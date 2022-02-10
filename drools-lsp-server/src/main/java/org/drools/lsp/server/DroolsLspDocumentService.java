package org.drools.lsp.server;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.drools.completion.DRLCompletionHelper;
import org.drools.drl.ast.descr.PackageDescr;
import org.eclipse.lsp4j.CompletionItem;
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

import static org.drools.parser.DRLParserHelper.parse;

public class DroolsLspDocumentService implements TextDocumentService {

    private final Map<String, String> sourcesMap = new ConcurrentHashMap<>();

    private final DroolsLspServer server;

    public DroolsLspDocumentService(DroolsLspServer server) {
        this.server = server;
    }

    @Override
    public void didOpen(DidOpenTextDocumentParams params) {
        sourcesMap.put(params.getTextDocument().getUri(), params.getTextDocument().getText());
        CompletableFuture.runAsync(() ->
                server.getClient().publishDiagnostics(
                        new PublishDiagnosticsParams(params.getTextDocument().getUri(), validate())
                )
        );
    }

    private List<Diagnostic> validate() {
        return Collections.emptyList();
    }

    @Override
    public void didChange(DidChangeTextDocumentParams params) {
        sourcesMap.put(params.getTextDocument().getUri(), params.getContentChanges().get(0).getText());
        // modify internal state
//        this.documentVersions.put(params.getTextDocument().getUri(), params.getTextDocument().getVersion() + 1);
        // send notification
        CompletableFuture.runAsync(() ->
                server.getClient().publishDiagnostics(
                        new PublishDiagnosticsParams(params.getTextDocument().getUri(), validate())
                )
        );
    }

    public String getRuleName(CompletionParams completionParams) {
        String text = sourcesMap.get( completionParams.getTextDocument().getUri() );
        PackageDescr packageDescr = parse(text);
        return packageDescr.getRules().get(0).getName();
    }

    @Override
    public CompletableFuture<Either<List<CompletionItem>, CompletionList>> completion(CompletionParams completionParams) {
        return CompletableFuture.supplyAsync( () -> Either.forLeft( attempt( () -> getCompletionItems(completionParams) ) ) );
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

        String text = sourcesMap.get( completionParams.getTextDocument().getUri() );

        Position caretPosition = completionParams.getPosition();

        List<CompletionItem> completionItems = DRLCompletionHelper.getCompletionItems(text, caretPosition, server.getClient());

        server.getClient().showMessage(new MessageParams(MessageType.Info, "Position=" + caretPosition));
        server.getClient().showMessage(new MessageParams(MessageType.Info, "completionItems = " + completionItems));
//        server.getClient().showMessage(new MessageParams(MessageType.Info, "Node = " + node));
//
//        Token stop = node instanceof TerminalNode ? ((TerminalNode)node).getSymbol() : ((ParserRuleContext)node).getStop();
//        server.getClient().showMessage(new MessageParams(MessageType.Info, "row=" + stop.getLine()));
//        server.getClient().showMessage(new MessageParams(MessageType.Info, "col=" + stop.getCharPositionInLine()));

//        server.getClient().showMessage(new MessageParams(MessageType.Info, "completionItem=" + completionItem.getLabel()));

        return completionItems;
    }

    @Override
    public void didClose(DidCloseTextDocumentParams params) {

    }

    @Override
    public void didSave(DidSaveTextDocumentParams params) {

    }
}
