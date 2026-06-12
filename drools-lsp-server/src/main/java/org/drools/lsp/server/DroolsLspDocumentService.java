package org.drools.lsp.server;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.drools.completion.ClassIndex;
import org.drools.completion.DRLCompletionHelper;
import org.drools.completion.DRLDiagnosticHelper;
import org.drools.completion.DRLLintHelper;
import org.drools.drl.parser.antlr4.DRLParserHelper;
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
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.TextDocumentService;

public class DroolsLspDocumentService implements TextDocumentService {

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
        diagnostics.addAll(DRLLintHelper.lint(text));
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
