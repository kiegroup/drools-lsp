package org.drools.completion;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;

/**
 * Find-references ({@code textDocument/references}) for DRL.
 *
 * <p>Resolves the identifier at the caret to a symbol and collects its uses
 * across the workspace via {@link DRLReferenceScanner}:
 * <ul>
 *   <li><b>Bound variable</b> ({@code $x}) — uses within the enclosing rule
 *       only (rename-safe scoping); current document.</li>
 *   <li><b>Declared type</b> — uses across the current document and its sibling
 *       {@code .drl} files (open buffers shadow disk). The {@code declare} site
 *       is included only when {@code includeDeclaration} is set.</li>
 *   <li><b>Classpath type</b> — DRL uses across files that resolve the simple
 *       name to the <em>same</em> FQCN, so a different same-named type from
 *       another package isn't swept in. The Java source is not scanned, so
 *       these are read-only references (rename is blocked elsewhere).</li>
 * </ul>
 *
 * <p>All operations are best-effort — any failure yields the references found
 * so far (typically empty).
 */
public final class DRLReferencesHelper {

    private DRLReferencesHelper() {
    }

    /**
     * Returns the locations referencing the symbol at {@code position}, or an
     * empty list when the caret is not on a resolvable symbol.
     *
     * @param openFiles          open unsaved sibling buffers keyed by path, so
     *                           cross-file references reflect unsaved edits
     * @param includeDeclaration whether to include a declared type's
     *                           {@code declare} site (LSP {@code ReferenceContext})
     */
    public static List<Location> references(String uri, String text, Position position,
                                            Map<Path, String> openFiles, ClassIndex classIndex,
                                            Set<Path> buildOutputDirs, boolean includeDeclaration) {
        List<Location> out = new ArrayList<>();
        if (text == null || position == null) {
            return out;
        }
        String word = DRLDefinitionHelper.wordAt(text, position);
        if (word.isEmpty()) {
            return out;
        }
        if (DRLDefinitionHelper.caretInCommentOrString(text, position)) {
            return out;
        }
        Path docPath = toPath(uri);

        // Bound variable: rule-scoped, current document only.
        if (word.charAt(0) == '$') {
            for (var range : DRLReferenceScanner.bindingOccurrences(text, position, word)) {
                out.add(new Location(uri, range));
            }
            return out;
        }
        if (!Character.isJavaIdentifierStart(word.charAt(0))) {
            return out;
        }

        // Declared type anywhere in the workspace → simple-name match across files.
        Map<String, DeclaredType> typeIndex = DRLWorkspaceTypeIndex.build(text, docPath, openFiles);
        if (typeIndex.containsKey(word)) {
            addTypeRefs(uri, text, word, includeDeclaration, out);
            DRLWorkspaceTypeIndex.forEachSiblingFile(docPath, openFiles,
                    (fileUri, fileText) -> addTypeRefs(fileUri, fileText, word, includeDeclaration, out));
            return out;
        }

        // Classpath type → DRL uses in files resolving the name to the same FQCN.
        String fqcn = DRLDefinitionHelper.resolveFqcn(text, word, classIndex);
        if (fqcn == null) {
            return out;
        }
        addClasspathRefs(uri, text, word, fqcn, classIndex, out);
        DRLWorkspaceTypeIndex.forEachSiblingFile(docPath, openFiles,
                (fileUri, fileText) -> addClasspathRefs(fileUri, fileText, word, fqcn, classIndex, out));
        return out;
    }

    private static void addTypeRefs(String uri, String text, String simpleName,
                                    boolean includeDeclaration, List<Location> out) {
        for (DRLReferenceScanner.Occurrence occ : DRLReferenceScanner.typeOccurrences(text, simpleName)) {
            if (occ.declaration && !includeDeclaration) {
                continue;
            }
            out.add(new Location(uri, occ.range));
        }
    }

    private static void addClasspathRefs(String uri, String text, String simpleName, String fqcn,
                                         ClassIndex classIndex, List<Location> out) {
        if (!text.contains(simpleName)) {
            return;
        }
        if (!fqcn.equals(DRLDefinitionHelper.resolveFqcn(text, simpleName, classIndex))) {
            return;
        }
        for (DRLReferenceScanner.Occurrence occ : DRLReferenceScanner.typeOccurrences(text, simpleName)) {
            out.add(new Location(uri, occ.range));
        }
    }

    /** Converts a document URI to a filesystem path, or null for non-file URIs. */
    private static Path toPath(String uri) {
        try {
            return Path.of(java.net.URI.create(uri));
        } catch (Exception e) {
            return null;
        }
    }
}
