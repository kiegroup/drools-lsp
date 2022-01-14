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
import org.eclipse.lsp4j.Position;
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
        DroolsLspServer ls = getDroolsLspServerForDocument("suggestion");

        CompletionParams completionParams = new CompletionParams();
        completionParams.setTextDocument(new TextDocumentIdentifier("myDocument"));

        CompletableFuture<Either<List<CompletionItem>, CompletionList>> result = ls.getTextDocumentService().completion(completionParams);
        CompletionItem completionItem = result.get().getLeft().get(0);
        assertEquals("suggestion", completionItem.getInsertText());
    }

    @Test
    public void testReadRuleName() throws Exception {
        String drl = "rule MyRule when Dog(name == \"Bart\") then end";

        DroolsLspServer ls = getDroolsLspServerForDocument(drl);

        CompletionParams completionParams = new CompletionParams();
        completionParams.setTextDocument(new TextDocumentIdentifier("myDocument"));

        String ruleName = ((DroolsLspDocumentService) ls.getTextDocumentService()).getRuleName(completionParams);
        assertEquals("MyRule", ruleName);
    }

    @Test
    public void testFindLHSandRHS() throws Exception {
        String drl =
                "package org.test;\n" +
                "import org.test.model.Person;\n" +
                "rule TestRule when\n" +
                "  $p:Person()\n" +
                "then\n" +
                "  System.out.println($p.getName());\n" +
                "end";

        DroolsLspServer ls = getDroolsLspServerForDocument(drl);

        CompletionParams completionParams = new CompletionParams();
        completionParams.setTextDocument(new TextDocumentIdentifier("myDocument"));

        completionParams.setPosition(new Position(3, 4));
        CompletableFuture<Either<List<CompletionItem>, CompletionList>> result = ls.getTextDocumentService().completion(completionParams);
        CompletionItem completionItem = result.get().getLeft().get(0);
        assertEquals("LHS", completionItem.getInsertText());

        completionParams.setPosition(new Position(5, 14));
        result = ls.getTextDocumentService().completion(completionParams);
        completionItem = result.get().getLeft().get(0);
        assertEquals("RHS", completionItem.getInsertText());
    }

    private DroolsLspServer getDroolsLspServerForDocument(String drl) {
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
        doc.setText(drl);
        ls.getTextDocumentService().didOpen(new DidOpenTextDocumentParams(doc));
        return ls;
    }
}