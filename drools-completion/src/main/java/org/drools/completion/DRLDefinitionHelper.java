package org.drools.completion;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.drools.drl.parser.antlr4.DRL10Parser;
import org.drools.drl.parser.antlr4.DRLParserHelper;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;

/**
 * Go-to-definition for type names in DRL documents.
 *
 * <p>Resolution order — DRL first, then Java:
 * <ol>
 *   <li>{@code declare} blocks in the current document;</li>
 *   <li>{@code declare} blocks in sibling DRL files from the active
 *       {@link WorkspaceSiblingResolver} (same-directory by default;
 *       non-file documents have no siblings);</li>
 *   <li>project Java sources located by Maven convention: when the resolved
 *       classpath contains {@code <module>/target/classes/pkg/Type.class},
 *       the definition is {@code <module>/src/main/java/pkg/Type.java} if
 *       that file exists. JAR classes have no navigable source.</li>
 * </ol>
 */
public final class DRLDefinitionHelper {

    private static final Logger logger =
            Logger.getLogger(DRLDefinitionHelper.class.getName());

    /** Swallows ANTLR parse errors — incomplete documents are normal. */
    private static final BaseErrorListener SILENT = new BaseErrorListener() {
        @Override
        public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol,
                                int line, int charPositionInLine, String msg,
                                RecognitionException e) {
        }
    };

    private DRLDefinitionHelper() {
    }

    /**
     * Returns the definition locations for the identifier at
     * {@code position}, or an empty list when it doesn't name a resolvable
     * type.
     *
     * @param buildOutputDirs build-output directories from the resolved
     *                        classpath, used for the Maven source-mapping
     */
    public static List<Location> findDefinitions(String uri, String text, Position position,
                                                 ClassIndex classIndex, Set<Path> buildOutputDirs) {
        return findDefinitions(uri, text, position, classIndex, buildOutputDirs, Map.of());
    }

    /**
     * @param openFiles open unsaved sibling buffers keyed by path, so a
     *                  definition in an unsaved sibling resolves to its current
     *                  (edited) location; may be empty
     */
    public static List<Location> findDefinitions(String uri, String text, Position position,
                                                 ClassIndex classIndex, Set<Path> buildOutputDirs,
                                                 Map<Path, String> openFiles) {
        if (text == null || position == null) {
            return List.of();
        }
        String word = wordAt(text, position);
        if (word.isEmpty() || !Character.isJavaIdentifierStart(word.charAt(0))) {
            return List.of();
        }

        // 1. declare blocks in this document.
        for (DeclaredType declared : DRLDeclaredTypeParser.parseDeclaredTypes(text)) {
            if (word.equals(declared.name)) {
                return List.of(new Location(uri, nameRange(declared, word)));
            }
        }

        // 2. declare blocks in sibling DRL files (open buffers shadow disk).
        Location sibling = findSiblingDefinition(word, toPath(uri), openFiles);
        if (sibling != null) {
            return List.of(sibling);
        }

        // 3. Java sources by Maven convention.
        String fqcn = resolveFqcn(text, word, classIndex);
        if (fqcn == null) {
            return List.of();
        }
        Location javaSource = javaSourceLocation(fqcn, buildOutputDirs);
        return javaSource == null ? List.of() : List.of(javaSource);
    }

    /**
     * Finds {@code word}'s declaration among sibling files via the shared
     * {@link DRLWorkspaceTypeIndex} walk (open unsaved buffers shadow disk),
     * returning its {@link Location} or {@code null}. First match wins.
     */
    private static Location findSiblingDefinition(String word, Path documentPath,
                                                  Map<Path, String> openFiles) {
        if (documentPath == null) {
            return null;
        }
        Location[] hit = {null};
        DRLWorkspaceTypeIndex.forEachSiblingType(documentPath, openFiles, (declared, fileUri) -> {
            if (hit[0] == null && word.equals(declared.name)) {
                hit[0] = new Location(fileUri, nameRange(declared, word));
            }
        });
        return hit[0];
    }

    private static Range nameRange(DeclaredType declared, String word) {
        return new Range(new Position(declared.nameLine, declared.nameCol),
                         new Position(declared.nameLine, declared.nameCol + word.length()));
    }

    /** Converts a document URI to a filesystem path, or null for non-file URIs. */
    private static Path toPath(String uri) {
        try {
            return Path.of(java.net.URI.create(uri));
        } catch (Exception e) {
            return null;
        }
    }

    private static String resolveFqcn(String text, String word, ClassIndex classIndex) {
        try {
            DRL10Parser parser = DRLParserHelper.createDrlParser(text);
            Lexer lexer = (Lexer) parser.getTokenStream().getTokenSource();
            lexer.removeErrorListeners();
            lexer.addErrorListener(SILENT);
            parser.removeErrorListeners();
            parser.addErrorListener(SILENT);
            return DRLCompletionHelper.resolveFqcn(word, word, parser.compilationUnit(), classIndex);
        } catch (Exception e) {
            logger.fine(() -> "FQCN resolution failed for " + word + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Maps an FQCN whose {@code .class} file sits in a build-output
     * directory to its conventional Maven source file, anchored at the type
     * declaration when it can be found in the source text.
     */
    private static Location javaSourceLocation(String fqcn, Set<Path> buildOutputDirs) {
        if (buildOutputDirs == null || buildOutputDirs.isEmpty()) {
            return null;
        }
        String relClass = fqcn.replace('.', '/') + ".class";
        String relJava = fqcn.replace('.', '/') + ".java";
        String simpleName = fqcn.substring(fqcn.lastIndexOf('.') + 1);

        for (Path outputDir : buildOutputDirs) {
            if (!Files.isRegularFile(outputDir.resolve(relClass))) {
                continue;
            }
            // <module>/target/classes → <module>
            Path target = outputDir.getParent();
            Path module = target == null ? null : target.getParent();
            if (module == null) {
                continue;
            }
            Path javaFile = module.resolve("src/main/java").resolve(relJava);
            if (!Files.isRegularFile(javaFile)) {
                continue;
            }
            return new Location(javaFile.toUri().toString(),
                                declarationRange(javaFile, simpleName));
        }
        return null;
    }

    /** Finds the {@code class/interface/enum/record <Simple>} declaration line. */
    private static Range declarationRange(Path javaFile, String simpleName) {
        try {
            Pattern decl = Pattern.compile(
                    "\\b(?:class|interface|enum|record)\\s+(" + Pattern.quote(simpleName) + ")\\b");
            List<String> lines = Files.readAllLines(javaFile);
            for (int i = 0; i < lines.size(); i++) {
                Matcher m = decl.matcher(lines.get(i));
                if (m.find()) {
                    return new Range(new Position(i, m.start(1)),
                                     new Position(i, m.start(1) + simpleName.length()));
                }
            }
        } catch (Exception e) {
            logger.fine(() -> "Could not locate declaration in " + javaFile + ": " + e.getMessage());
        }
        return new Range(new Position(0, 0), new Position(0, 0));
    }

    /**
     * Expands the identifier ({@code [A-Za-z0-9_$]+}) around the caret.
     * Shared with {@link DRLHoverHelper}.
     */
    static String wordAt(String text, Position position) {
        String[] lines = text.split("\r?\n", -1);
        if (position.getLine() < 0 || position.getLine() >= lines.length) {
            return "";
        }
        String line = lines[position.getLine()];
        int col = Math.min(Math.max(position.getCharacter(), 0), line.length());

        int start = col;
        while (start > 0 && isIdentifierChar(line.charAt(start - 1))) {
            start--;
        }
        int end = col;
        while (end < line.length() && isIdentifierChar(line.charAt(end))) {
            end++;
        }
        return start < end ? line.substring(start, end) : "";
    }

    private static boolean isIdentifierChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '$';
    }
}
