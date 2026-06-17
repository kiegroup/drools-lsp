package org.drools.lsp.server;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.drools.completion.ClassIndex;
import org.drools.completion.DRLCompletionHelper;
import org.drools.completion.DRLDiagnosticHelper;
import org.drools.completion.DRLLintHelper;
import org.drools.drl.parser.antlr4.DRLParserHelper;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.TextDocumentService;

public class DroolsLspDocumentService implements TextDocumentService {

    private static final Logger logger = Logger.getLogger(DroolsLspDocumentService.class.getName());

    private final Map<String, String> sourcesMap = new ConcurrentHashMap<>();
    private volatile ClassIndex classIndex = ClassIndex.empty();

    private final DroolsLspServer server;

    public DroolsLspDocumentService(DroolsLspServer server) {
        this.server = server;
    }

    public void setClassIndex(ClassIndex classIndex) {
        this.classIndex = classIndex;
    }

    ClassIndex getClassIndexForTest() {
        return classIndex;
    }

    @Override
    public void didOpen(DidOpenTextDocumentParams params) {
        String uri = params.getTextDocument().getUri();
        sourcesMap.put(uri, params.getTextDocument().getText());
        CompletableFuture.runAsync(() ->
                server.getClient().publishDiagnostics(
                        new PublishDiagnosticsParams(uri, validate(uri))
                )
        );
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
        String uri = params.getTextDocument().getUri();
        sourcesMap.put(uri, params.getContentChanges().get(0).getText());
        CompletableFuture.runAsync(() ->
                server.getClient().publishDiagnostics(
                        new PublishDiagnosticsParams(uri, validate(uri))
                )
        );
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
            server.getClient().showMessage(new MessageParams(MessageType.Error, e.toString()));
        }
        return null;
    }

    public List<CompletionItem> getCompletionItems(CompletionParams completionParams) {
        String text = sourcesMap.get(completionParams.getTextDocument().getUri());

        Position caretPosition = completionParams.getPosition();
        List<CompletionItem> completionItems = DRLCompletionHelper.getCompletionItems(text, caretPosition, server.getClient(), classIndex);

        server.getClient().showMessage(new MessageParams(MessageType.Info, "Position=" + caretPosition));
        server.getClient().showMessage(new MessageParams(MessageType.Info, "completionItems = " + completionItems));

        return completionItems;
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
        String uri = params.getTextDocument().getUri();
        sourcesMap.remove(uri);
        // Clear the document's diagnostics so stale squiggles don't survive
        // the editor closing the file.
        CompletableFuture.runAsync(() ->
                server.getClient().publishDiagnostics(
                        new PublishDiagnosticsParams(uri, Collections.emptyList())
                )
        );
    }

    @Override
    public void didSave(DidSaveTextDocumentParams params) {
    }
}
