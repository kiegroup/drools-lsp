package org.drools.completion;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.ShowMessageRequestParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.services.LanguageClient;
import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class DRLCompletionHelperTest {

    @Ignore
    @Test
    public void testGetCompletionItems() {
        // TODO
        String text = "rule MyRule when Dog(name == \"Bart\") then end";
        CompletionParams completionParams = new CompletionParams();
        completionParams.setTextDocument(new TextDocumentIdentifier("myDocument"));
        Position caretPosition = new Position();
        caretPosition.setCharacter(0);
        caretPosition.setLine(-1); // -1 needed because of  int row = caretPosition == null ? -1 : caretPosition
        // .getLine()+1; // caret line position is zero based
        completionParams.setPosition(caretPosition);

        List<CompletionItem> result = DRLCompletionHelper.getCompletionItems(text, caretPosition, getLanguageClient());
        CompletionItem completionItem = result.get(0);
        assertEquals("suggestion", completionItem.getInsertText());
    }

    @Test
    public void testTestGetCompletionItems() {
        // TODO
    }

    private LanguageClient getLanguageClient() {
        List<Diagnostic> diagnostics = new ArrayList<>();
       return  new LanguageClient() {
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
        };

    }
}