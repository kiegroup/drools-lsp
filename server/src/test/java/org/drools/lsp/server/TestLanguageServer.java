package org.drools.lsp.server;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.drools.lsp.server.DroolsLspServer;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.ShowMessageRequestParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.LanguageClient;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TestLanguageServer {

    @Test
    public void testCompletion() throws Exception {
        DroolsLspServer ls = new DroolsLspServer();
        List<Diagnostic> diagnostics = new ArrayList<>();
        ls.connect(new LanguageClient() {
            @Override
            public void telemetryEvent(Object object) {
            }

            @Override
            public CompletableFuture<MessageActionItem> showMessageRequest(ShowMessageRequestParams requestParams) {
                return null;
            }

            @Override
            public void showMessage(MessageParams messageParams) {
            }

            @Override
            public void publishDiagnostics(PublishDiagnosticsParams d) {
                diagnostics.clear();
                diagnostics.addAll(d.getDiagnostics());
            }

            @Override
            public void logMessage(MessageParams message) {
            }
        });

        TextDocumentItem doc = new TextDocumentItem();
        doc.setUri("myDocument");
        doc.setText("suggestion");
        ls.getTextDocumentService().didOpen(new DidOpenTextDocumentParams(doc));
        Thread.sleep(1000);

        CompletionParams completionParams = new CompletionParams();
        completionParams.setTextDocument(new TextDocumentIdentifier("myDocument"));

        CompletableFuture<Either<List<CompletionItem>, CompletionList>> result = ls.getTextDocumentService().completion(completionParams);
        assertEquals("suggestion", result.get().getLeft().get(0).getInsertText());
    }

    private static final String DRL = "rule MyRule when Dog(name == \"Bart\") then end";

    @Test
    public void testReadRuleName() throws Exception {
        DroolsLspServer ls = new DroolsLspServer();
        List<Diagnostic> diagnostics = new ArrayList<>();
        ls.connect(new LanguageClient() {
            @Override
            public void telemetryEvent(Object object) {
            }

            @Override
            public CompletableFuture<MessageActionItem> showMessageRequest(ShowMessageRequestParams requestParams) {
                return null;
            }

            @Override
            public void showMessage(MessageParams messageParams) {
            }

            @Override
            public void publishDiagnostics(PublishDiagnosticsParams d) {
                diagnostics.clear();
                diagnostics.addAll(d.getDiagnostics());
            }

            @Override
            public void logMessage(MessageParams message) {
            }
        });

        TextDocumentItem doc = new TextDocumentItem();
        doc.setUri("myDocument");
        doc.setText(DRL);
        ls.getTextDocumentService().didOpen(new DidOpenTextDocumentParams(doc));
        Thread.sleep(1000);

        CompletionParams completionParams = new CompletionParams();
        completionParams.setTextDocument(new TextDocumentIdentifier("myDocument"));

        String ruleName = ((DroolsLspDocumentService) ls.getTextDocumentService()).getRuleName(completionParams);
        assertEquals("MyRule", ruleName);
    }
}