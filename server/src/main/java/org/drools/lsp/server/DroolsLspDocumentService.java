package org.drools.lsp.server;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import org.drools.compiler.compiler.DrlParser;
import org.drools.compiler.compiler.DroolsParserException;
import org.drools.compiler.lang.descr.PackageDescr;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.TextDocumentService;

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
        try {
            DrlParser drlParser = new DrlParser();
            PackageDescr packageDescr = drlParser.parse(null, text);
            if (drlParser.hasErrors()) {
                throw new RuntimeException("" + drlParser.getErrors());
            }
            return packageDescr.getRules().get(0).getName();
        } catch (DroolsParserException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public CompletableFuture<Either<List<CompletionItem>, CompletionList>> completion(CompletionParams completionParams) {
        // Provide completion item.
        return CompletableFuture.supplyAsync(() -> {

            List<CompletionItem> completionItems = new ArrayList<>();
            try {

                // Sample Completion item for text duplication
                CompletionItem completionItem = new CompletionItem();

                // Define the text to be inserted in to the file if the completion item is selected.
                String text = sourcesMap.get( completionParams.getTextDocument().getUri() );
                completionItem.setInsertText(text == null ? "" : text);

                // Set the label that shows when the completion drop down appears in the Editor.
                completionItem.setLabel("duplicate text");

                // Set the completion kind. This is a snippet.
                // That means it replace character which trigger the completion and
                // replace it with what defined in inserted text.
                completionItem.setKind(CompletionItemKind.Snippet);

                // This will set the details for the snippet code which will help user to
                // understand what this completion item is.
                completionItem.setDetail("this will duplicate current text");

                // Add the sample completion item to the list.
                completionItems.add(completionItem);
            } catch (Exception e) {
                e.printStackTrace();
            }

            // Return the list of completion items.
            return Either.forLeft(completionItems);
        });
    }

    @Override
    public void didClose(DidCloseTextDocumentParams params) {

    }

    @Override
    public void didSave(DidSaveTextDocumentParams params) {

    }
}
