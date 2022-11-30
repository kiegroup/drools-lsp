package org.drools.lsp.server;

import java.util.List;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.drools.lsp.server.TestHelperMethods.getDroolsLspDocumentService;

class DroolsLspDocumentServiceTest {

    @Test
    void testCompletion() {
        DroolsLspDocumentService droolsLspDocumentService = getDroolsLspDocumentService("");

        CompletionParams completionParams = new CompletionParams();
        completionParams.setTextDocument(new TextDocumentIdentifier("myDocument"));
        Position caretPosition = new Position();
        caretPosition.setCharacter(0);
        caretPosition.setLine(0);
        completionParams.setPosition(caretPosition);

        List<CompletionItem> result = droolsLspDocumentService.getCompletionItems(completionParams);
        assertThat(result.stream().map(CompletionItem::getInsertText).anyMatch("package"::equals)).isTrue();
    }

    @Test
    void testReadRuleName() {
        String drl = "rule MyRule when Dog(name == \"Bart\") then end";

        DroolsLspDocumentService droolsLspDocumentService = getDroolsLspDocumentService(drl);

        CompletionParams completionParams = new CompletionParams();
        completionParams.setTextDocument(new TextDocumentIdentifier("myDocument"));

        String ruleName = droolsLspDocumentService.getRuleName(completionParams);
        assertThat(ruleName).isEqualTo("MyRule");
    }

    @Test
    void testFindLHSandRHS() {
        String drl =
                "package org.test;\n" +
                        "import org.test.model.Person;\n" +
                        "rule TestRule when\n" +
                        "  $p:Person() \n" +
                        "then\n" +
                        "  System.out.println($p.getName()); \n" +
                        "end";

        DroolsLspDocumentService droolsLspDocumentService = getDroolsLspDocumentService(drl);

        CompletionParams completionParams = new CompletionParams();
        completionParams.setTextDocument(new TextDocumentIdentifier("myDocument"));

        completionParams.setPosition(new Position(1, 0));
        List<CompletionItem> result = droolsLspDocumentService.getCompletionItems(completionParams);
        assertThat(hasItem(result, "import")).isTrue();
        assertThat(hasItem(result, "rule")).isTrue();

        completionParams.setPosition(new Position(3, 14));
        result = droolsLspDocumentService.getCompletionItems(completionParams);
        assertThat(hasItem(result, "then")).isTrue();  // LHS

        completionParams.setPosition(new Position(5, 36));
        result = droolsLspDocumentService.getCompletionItems(completionParams);
        assertThat(hasItem(result, "end")).isTrue(); // RHS
    }

    private boolean hasItem(List<CompletionItem> result, String text) {
        return result.stream().map(CompletionItem::getInsertText).anyMatch(text::equals);
    }


}