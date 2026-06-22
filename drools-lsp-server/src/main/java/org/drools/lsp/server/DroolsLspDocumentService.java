package org.drools.lsp.server;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.drools.completion.ClassIndex;
import org.drools.completion.ClassMemberIndex;
import org.drools.completion.DRLCompletionHelper;
import org.drools.completion.DRLDefinitionHelper;
import org.drools.completion.DRLReferencesHelper;
import org.drools.completion.DRLDiagnosticHelper;
import org.drools.completion.DRLDocumentSymbolHelper;
import org.drools.completion.DRLFoldingRangeHelper;
import org.drools.completion.DRLHoverHelper;
import org.drools.completion.DRLInlayHintHelper;
import org.drools.completion.DRLLintHelper;
import org.drools.completion.DRLTypeHierarchyHelper;
import org.drools.drl.parser.antlr4.DRLParserHelper;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.DefinitionParams;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.lsp4j.DocumentDiagnosticParams;
import org.eclipse.lsp4j.DocumentDiagnosticReport;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.DocumentSymbolParams;
import org.eclipse.lsp4j.FoldingRange;
import org.eclipse.lsp4j.FoldingRangeRequestParams;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.HoverParams;
import org.eclipse.lsp4j.InlayHint;
import org.eclipse.lsp4j.InlayHintParams;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.ReferenceParams;
import org.eclipse.lsp4j.RelatedFullDocumentDiagnosticReport;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.TypeHierarchyItem;
import org.eclipse.lsp4j.TypeHierarchyPrepareParams;
import org.eclipse.lsp4j.TypeHierarchySubtypesParams;
import org.eclipse.lsp4j.TypeHierarchySupertypesParams;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.TextDocumentService;

public class DroolsLspDocumentService implements TextDocumentService {

    private static final Logger logger = Logger.getLogger(DroolsLspDocumentService.class.getName());

    private final Map<String, String> sourcesMap = new ConcurrentHashMap<>();
    private volatile ClassIndex classIndex = ClassIndex.empty();
    private volatile ClassMemberIndex classMemberIndex = ClassMemberIndex.empty();

    private final DroolsLspServer server;

    public DroolsLspDocumentService(DroolsLspServer server) {
        this.server = server;
    }

    public void setClassIndex(ClassIndex classIndex) {
        this.classIndex = classIndex;
    }

    public void setClassMemberIndex(ClassMemberIndex classMemberIndex) {
        this.classMemberIndex = classMemberIndex;
    }

    ClassIndex getClassIndexForTest() {
        return classIndex;
    }

    @Override
    public void didOpen(DidOpenTextDocumentParams params) {
        sourcesMap.put(params.getTextDocument().getUri(), params.getTextDocument().getText());
    }

    /**
     * Runs syntax validation and the structural lint passes over the
     * current text of {@code uri} and returns the combined diagnostics
     * (empty when the document is unknown or clean).
     */
    List<Diagnostic> validate(String uri) {
        String text = sourcesMap.get(uri);
        List<Diagnostic> diagnostics = new ArrayList<>(DRLDiagnosticHelper.validate(text));
        try {
            diagnostics.addAll(DRLLintHelper.lint(text));
        } catch (Exception e) {
            logger.log(Level.WARNING, "Lint pass failed for " + uri, e);
        }
        return diagnostics;
    }

    @Override
    public void didChange(DidChangeTextDocumentParams params) {
        sourcesMap.put(params.getTextDocument().getUri(), params.getContentChanges().get(0).getText());
    }

    public String getRuleName(CompletionParams completionParams) {
        String text = sourcesMap.get(completionParams.getTextDocument().getUri());
        return DRLParserHelper.getFirstRuleName(text);
    }

    @Override
    public CompletableFuture<Either<List<CompletionItem>, CompletionList>> completion(CompletionParams completionParams) {
        return CompletableFuture.supplyAsync(() -> {
            List<CompletionItem> items = attempt(() -> getCompletionItems(completionParams));
            if (items == null) {
                items = List.of();
            }
            boolean isIncomplete = items.stream().anyMatch(item -> item.getKind() == CompletionItemKind.Class);
            return Either.forRight(new CompletionList(isIncomplete, items));
        });
    }

    private <T> T attempt(Supplier<T> supplier) {
        try {
            return supplier.get();
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error during completion/definition/code action", e);
            server.getClient().showMessage(new MessageParams(MessageType.Error, e.toString()));
        }
        return null;
    }

    public List<CompletionItem> getCompletionItems(CompletionParams completionParams) {
        String uri = completionParams.getTextDocument().getUri();
        String text = sourcesMap.get(uri);

        Position caretPosition = completionParams.getPosition();
        Path documentPath = toPath(uri);
        List<CompletionItem> completionItems = DRLCompletionHelper.getCompletionItems(text, caretPosition, server.getClient(), classIndex, classMemberIndex, documentPath, openSiblings(documentPath));

        logger.fine("Position=" + caretPosition);
        logger.fine("completionItems = " + completionItems);

        return completionItems;
    }

    /** Converts a document URI to a filesystem path, or null for non-file URIs. */
    private static Path toPath(String uri) {
        try {
            return Paths.get(URI.create(uri));
        } catch (Exception e) {
            return null;
        }
    }

    /** Current text for {@code uri}: the open buffer if present, else read from disk, else null. */
    private String textForUri(String uri) {
        String open = sourcesMap.get(uri);
        if (open != null) {
            return open;
        }
        Path p = toPath(uri);
        if (p == null) {
            return null;
        }
        try {
            return Files.readString(p);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Open, possibly unsaved, sibling {@code .drl} buffers (same directory as
     * {@code documentPath}, excluding the document itself), keyed by normalized
     * absolute path. Lets cross-file resolution reflect edits not yet saved to
     * disk. Returns an empty map for non-file documents.
     */
    private Map<Path, String> openSiblings(Path documentPath) {
        Map<Path, String> open = new HashMap<>();
        if (documentPath == null) {
            return open;
        }
        Path docNorm = documentPath.toAbsolutePath().normalize();
        Path dir = docNorm.getParent();
        if (dir == null) {
            return open;
        }
        for (Map.Entry<String, String> e : sourcesMap.entrySet()) {
            Path p = toPath(e.getKey());
            if (p == null) {
                continue;
            }
            Path np = p.toAbsolutePath().normalize();
            if (np.equals(docNorm) || !np.toString().endsWith(".drl")) {
                continue;
            }
            if (dir.equals(np.getParent())) {
                open.put(np, e.getValue());
            }
        }
        return open;
    }

    @Override
    public CompletableFuture<Hover> hover(HoverParams params) {
        return CompletableFuture.supplyAsync(() -> {
            String uri = params.getTextDocument().getUri();
            String text = sourcesMap.get(uri);
            Path documentPath = toPath(uri);
            return attempt(() -> DRLHoverHelper.hover(
                    text, params.getPosition(), classIndex, classMemberIndex, documentPath,
                    openSiblings(documentPath)));
        });
    }

    @Override
    public CompletableFuture<List<InlayHint>> inlayHint(InlayHintParams params) {
        return CompletableFuture.supplyAsync(() -> {
            if (!inlayHintsEnabled() || params == null || params.getTextDocument() == null) {
                return Collections.<InlayHint>emptyList();
            }
            String uri = params.getTextDocument().getUri();
            String text = sourcesMap.get(uri);
            if (text == null) {
                return Collections.<InlayHint>emptyList();
            }
            Path documentPath = toPath(uri);
            // getHints is best-effort and never throws (returns [] on failure).
            return DRLInlayHintHelper.getHints(
                    text, params.getRange(), documentPath, openSiblings(documentPath));
        });
    }

    /**
     * Whether DRL inlay hints are produced, from the
     * {@code drools.lsp.inlayHints.enabled} system property (set via
     * {@code -Ddrools.lsp.inlayHints.enabled} by the extension). Defaults to
     * enabled; only an explicit {@code false} turns it off. VSCode's global
     * {@code editor.inlayHints.enabled} gates display independently.
     */
    private static boolean inlayHintsEnabled() {
        return !"false".equalsIgnoreCase(
                System.getProperty("drools.lsp.inlayHints.enabled", "true").trim());
    }

    @Override
    @SuppressWarnings("deprecation")  // Either<SymbolInformation, …> is the lsp4j signature; we return the DocumentSymbol side.
    public CompletableFuture<List<Either<SymbolInformation, DocumentSymbol>>> documentSymbol(DocumentSymbolParams params) {
        return CompletableFuture.supplyAsync(() -> {
            if (params == null || params.getTextDocument() == null) {
                return Collections.<Either<SymbolInformation, DocumentSymbol>>emptyList();
            }
            String text = sourcesMap.get(params.getTextDocument().getUri());
            List<DocumentSymbol> symbols = DRLDocumentSymbolHelper.symbols(text);
            List<Either<SymbolInformation, DocumentSymbol>> result = new ArrayList<>(symbols.size());
            for (DocumentSymbol symbol : symbols) {
                result.add(Either.forRight(symbol));
            }
            return result;
        });
    }

    @Override
    public CompletableFuture<DocumentDiagnosticReport> diagnostic(DocumentDiagnosticParams params) {
        return CompletableFuture.supplyAsync(() -> {
            List<Diagnostic> items =
                    (params != null && params.getTextDocument() != null
                            && sourcesMap.containsKey(params.getTextDocument().getUri()))
                            ? validate(params.getTextDocument().getUri())
                            : Collections.emptyList();
            return new DocumentDiagnosticReport(new RelatedFullDocumentDiagnosticReport(items));
        });
    }

    @Override
    public CompletableFuture<List<FoldingRange>> foldingRange(FoldingRangeRequestParams params) {
        return CompletableFuture.supplyAsync(() -> {
            if (params == null || params.getTextDocument() == null) {
                return Collections.<FoldingRange>emptyList();
            }
            String text = sourcesMap.get(params.getTextDocument().getUri());
            return DRLFoldingRangeHelper.foldingRanges(text);
        });
    }

    @Override
    public CompletableFuture<List<TypeHierarchyItem>> prepareTypeHierarchy(TypeHierarchyPrepareParams params) {
        return CompletableFuture.supplyAsync(() -> {
            if (params == null || params.getTextDocument() == null) {
                return Collections.<TypeHierarchyItem>emptyList();
            }
            String uri = params.getTextDocument().getUri();
            Path documentPath = toPath(uri);
            return DRLTypeHierarchyHelper.prepare(sourcesMap.get(uri), params.getPosition(), uri,
                    openSiblings(documentPath), classIndex, server.getBuildOutputDirs());
        });
    }

    @Override
    public CompletableFuture<List<TypeHierarchyItem>> typeHierarchySupertypes(TypeHierarchySupertypesParams params) {
        return CompletableFuture.supplyAsync(() -> {
            if (params == null || params.getItem() == null) {
                return Collections.<TypeHierarchyItem>emptyList();
            }
            TypeHierarchyItem item = params.getItem();
            return DRLTypeHierarchyHelper.supertypes(item, textForUri(item.getUri()),
                    openSiblings(toPath(item.getUri())), classIndex, classMemberIndex,
                    server.getBuildOutputDirs());
        });
    }

    @Override
    public CompletableFuture<List<TypeHierarchyItem>> typeHierarchySubtypes(TypeHierarchySubtypesParams params) {
        return CompletableFuture.supplyAsync(() -> {
            if (params == null || params.getItem() == null) {
                return Collections.<TypeHierarchyItem>emptyList();
            }
            TypeHierarchyItem item = params.getItem();
            return DRLTypeHierarchyHelper.subtypes(item, textForUri(item.getUri()),
                    openSiblings(toPath(item.getUri())));
        });
    }

    @Override
    public CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>> definition(DefinitionParams params) {
        return CompletableFuture.supplyAsync(() -> {
            String uri = params.getTextDocument().getUri();
            String text = sourcesMap.get(uri);
            Path documentPath = toPath(uri);
            List<Location> definitions = attempt(() -> DRLDefinitionHelper.findDefinitions(
                    uri, text, params.getPosition(), classIndex, server.getBuildOutputDirs(),
                    openSiblings(documentPath)));
            return Either.forLeft(definitions == null ? List.of() : definitions);
        });
    }

    @Override
    public CompletableFuture<List<? extends Location>> references(ReferenceParams params) {
        return CompletableFuture.supplyAsync(() -> {
            if (params == null || params.getTextDocument() == null) {
                return Collections.<Location>emptyList();
            }
            String uri = params.getTextDocument().getUri();
            String text = sourcesMap.get(uri);
            Path documentPath = toPath(uri);
            boolean includeDeclaration =
                    params.getContext() != null && params.getContext().isIncludeDeclaration();
            return DRLReferencesHelper.references(uri, text, params.getPosition(),
                    openSiblings(documentPath), classIndex, server.getBuildOutputDirs(),
                    includeDeclaration);
        });
    }

    @Override
    public CompletableFuture<List<Either<Command, CodeAction>>> codeAction(CodeActionParams params) {
        if (params == null || params.getTextDocument() == null || params.getRange() == null) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }
        // No quick-fixes when the MVEL lint pass is disabled — diagnostics won't be
        // shown, so offering fixes for them would be confusing.
        String mvelProp = System.getProperty("drools.lsp.lint.mvelPropertyAccess", "off")
                                 .trim().toLowerCase();
        if ("off".equals(mvelProp)) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }
        String uri = params.getTextDocument().getUri();
        String text = sourcesMap.get(uri);
        if (text == null) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }
        Range requested = params.getRange();
        return CompletableFuture.supplyAsync(
                () -> buildPropertyAccessActions(uri, text, requested));
    }

    /**
     * Builds MVEL property-access quick-fixes for the requested range: one
     * per-call fix for every flagged getter that overlaps the cursor range,
     * plus a "convert all" fix when the file has more than one finding.
     * Package-private and static so it can be unit-tested without a live server.
     */
    static List<Either<Command, CodeAction>> buildPropertyAccessActions(
            String uri, String text, Range requested) {
        List<TextEdit> edits;
        try {
            edits = DRLLintHelper.mvelPropertyAccessEdits(text);
        } catch (Exception e) {
            return Collections.emptyList();
        }
        if (edits.isEmpty()) {
            return Collections.emptyList();
        }

        List<Either<Command, CodeAction>> actions = new ArrayList<>();
        for (TextEdit edit : edits) {
            if (!rangesOverlap(edit.getRange(), requested)) {
                continue;
            }
            CodeAction ca = new CodeAction(
                    "Use MVEL property access '" + edit.getNewText() + "'");
            ca.setKind(CodeActionKind.QuickFix);
            ca.setEdit(workspaceEdit(uri, Collections.singletonList(edit)));
            actions.add(Either.forRight(ca));
        }
        if (edits.size() > 1) {
            CodeAction all = new CodeAction(
                    "Convert all getter calls in this file to MVEL property access");
            all.setKind(CodeActionKind.QuickFix);
            all.setEdit(workspaceEdit(uri, new ArrayList<>(edits)));
            actions.add(Either.forRight(all));
        }
        return actions;
    }

    private static WorkspaceEdit workspaceEdit(String uri, List<TextEdit> edits) {
        WorkspaceEdit we = new WorkspaceEdit();
        Map<String, List<TextEdit>> changes = new LinkedHashMap<>();
        changes.put(uri, edits);
        we.setChanges(changes);
        return we;
    }

    private static boolean rangesOverlap(Range a, Range b) {
        if (a == null || b == null) {
            return false;
        }
        return !isStrictlyBefore(a.getEnd(), b.getStart())
                && !isStrictlyBefore(b.getEnd(), a.getStart());
    }

    private static boolean isStrictlyBefore(Position p, Position q) {
        if (p == null || q == null) {
            return false;
        }
        if (p.getLine() != q.getLine()) {
            return p.getLine() < q.getLine();
        }
        return p.getCharacter() < q.getCharacter();
    }

    @Override
    public void didClose(DidCloseTextDocumentParams params) {
        sourcesMap.remove(params.getTextDocument().getUri());
    }

    @Override
    public void didSave(DidSaveTextDocumentParams params) {
    }
}
