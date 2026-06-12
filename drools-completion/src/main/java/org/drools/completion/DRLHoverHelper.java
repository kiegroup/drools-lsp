package org.drools.completion;

import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;

import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.drools.drl.parser.antlr4.DRL10Parser;
import org.drools.drl.parser.antlr4.DRLParserHelper;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.MarkupKind;
import org.eclipse.lsp4j.Position;

/**
 * Hover content for DRL documents: type structure for pattern type names and
 * type information for fields inside constraints.
 *
 * <p>Type names resolve like completion and go-to-definition do — DRL
 * {@code declare} blocks first (current document, then sibling files from
 * the active {@link WorkspaceSiblingResolver}), then classpath types through
 * imports and the class index. Declared types render as their declare block
 * with the doc comment above it, classpath types as their member list.
 */
public final class DRLHoverHelper {

    private static final Logger logger = Logger.getLogger(DRLHoverHelper.class.getName());

    /** Swallows ANTLR parse errors — incomplete documents are normal. */
    private static final BaseErrorListener SILENT = new BaseErrorListener() {
        @Override
        public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol,
                                int line, int charPositionInLine, String msg,
                                RecognitionException e) {
        }
    };

    private DRLHoverHelper() {
    }

    /**
     * Returns hover content for the identifier at {@code position}, or
     * {@code null} when there is nothing useful to show.
     *
     * @param documentPath filesystem location of the document, used to find
     *                     sibling DRL files; {@code null} for non-file
     *                     documents
     */
    public static Hover hover(String text, Position position, ClassIndex classIndex,
                              ClassMemberIndex memberIndex, Path documentPath) {
        if (text == null || position == null) {
            return null;
        }
        String word = DRLDefinitionHelper.wordAt(text, position);
        if (word.isEmpty() || !Character.isJavaIdentifierStart(word.charAt(0))) {
            return null;
        }

        // 1. DRL-declared types: current document, then siblings.
        DeclaredType declared = findDeclaredType(word, text, documentPath);
        if (declared != null) {
            return markdown(renderDeclared(declared));
        }

        DRL10Parser parser;
        DRL10Parser.CompilationUnitContext compilationUnit;
        Integer nodeIndex;
        try {
            parser = silentParser(text);
            compilationUnit = parser.compilationUnit();
            nodeIndex = DRLParserHelper.computeTokenIndex(
                    parser, position.getLine() + 1, position.getCharacter());
        } catch (Exception e) {
            logger.fine(() -> "Hover parse failed: " + e.getMessage());
            return null;
        }

        // 2. Field of the pattern enclosing the caret.
        if (nodeIndex != null) {
            String patternType = DRLCompletionHelper.findEnclosingPatternTypeName(
                    compilationUnit, nodeIndex);
            if (patternType != null && !patternType.equals(word)) {
                Field field = findField(patternType, word, text, documentPath,
                                        compilationUnit, classIndex, memberIndex);
                if (field != null) {
                    String owner = patternType.substring(patternType.lastIndexOf('.') + 1);
                    return markdown("**" + field.name + "** : `" + field.type
                            + "`\n\nField of `" + owner + "`");
                }
            }
        }

        // 3. Classpath type.
        String fqcn = DRLCompletionHelper.resolveFqcn(word, word, compilationUnit, classIndex);
        if (fqcn != null) {
            List<Field> members = memberIndex.membersOf(fqcn);
            if (!members.isEmpty()) {
                return markdown(renderJavaType(word, fqcn, members));
            }
        }
        return null;
    }

    private static DeclaredType findDeclaredType(String name, String text, Path documentPath) {
        for (DeclaredType dt : DRLDeclaredTypeParser.parseDeclaredTypes(text)) {
            if (name.equals(dt.name)) {
                return dt;
            }
        }
        if (documentPath != null) {
            for (Path sibling : WorkspaceSiblingResolvers.active().resolveSiblings(documentPath)) {
                for (DeclaredType dt : DRLDeclaredTypeParser.parseDeclaredTypesCached(sibling)) {
                    if (name.equals(dt.name)) {
                        return dt;
                    }
                }
            }
        }
        return null;
    }

    private static Field findField(String patternType, String fieldName, String text,
                                   Path documentPath, DRL10Parser.CompilationUnitContext compilationUnit,
                                   ClassIndex classIndex, ClassMemberIndex memberIndex) {
        String simpleName = patternType.substring(patternType.lastIndexOf('.') + 1);
        DeclaredType declared = findDeclaredType(simpleName, text, documentPath);
        List<Field> fields;
        if (declared != null) {
            fields = declared.fields;
        } else {
            String fqcn = DRLCompletionHelper.resolveFqcn(patternType, simpleName,
                                                          compilationUnit, classIndex);
            fields = fqcn == null ? List.of() : memberIndex.membersOf(fqcn);
        }
        for (Field field : fields) {
            if (fieldName.equals(field.name)) {
                return field;
            }
        }
        return null;
    }

    private static String renderDeclared(DeclaredType dt) {
        StringBuilder sb = new StringBuilder();
        if (dt.doc != null) {
            sb.append(dt.doc).append("\n\n");
        }
        sb.append("```\n");
        sb.append("declare ").append(dt.isEnum ? "enum " : "").append(dt.name);
        if (dt.extendsName != null) {
            sb.append(" extends ").append(dt.extendsName);
        }
        sb.append('\n');
        for (Field field : dt.fields) {
            if (dt.isEnum && dt.name.equals(field.type)) {
                sb.append("  ").append(field.name);
                if (field.args != null) {
                    sb.append('(').append(field.args).append(')');
                }
                sb.append('\n');
            } else {
                sb.append("  ").append(field.name).append(" : ").append(field.type).append('\n');
            }
        }
        sb.append("end\n```");
        return sb.toString();
    }

    private static String renderJavaType(String simpleName, String fqcn, List<Field> members) {
        StringBuilder sb = new StringBuilder();
        sb.append("**").append(simpleName).append("** — `").append(fqcn).append("`\n");
        for (Field member : members) {
            sb.append("\n- ").append(member.name).append(" : ").append(member.type);
        }
        return sb.toString();
    }

    private static Hover markdown(String content) {
        return new Hover(new MarkupContent(MarkupKind.MARKDOWN, content));
    }

    private static DRL10Parser silentParser(String text) {
        DRL10Parser parser = DRLParserHelper.createDrlParser(text);
        Lexer lexer = (Lexer) parser.getTokenStream().getTokenSource();
        lexer.removeErrorListeners();
        lexer.addErrorListener(SILENT);
        parser.removeErrorListeners();
        parser.addErrorListener(SILENT);
        return parser;
    }
}
