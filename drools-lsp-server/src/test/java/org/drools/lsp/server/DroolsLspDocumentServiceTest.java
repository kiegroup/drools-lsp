package org.drools.lsp.server;

import java.util.List;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.DefinitionParams;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.drools.lsp.server.TestHelperMethods.getDroolsLspDocumentService;

class DroolsLspDocumentServiceTest {

    @Test
    void getCompletionItems() {
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
    void getRuleName() {
        String drl = "rule MyRule when Dog(name == \"Bart\") then end";

        DroolsLspDocumentService droolsLspDocumentService = getDroolsLspDocumentService(drl);

        CompletionParams completionParams = new CompletionParams();
        completionParams.setTextDocument(new TextDocumentIdentifier("myDocument"));

        String ruleName = droolsLspDocumentService.getRuleName(completionParams);
        assertThat(ruleName).isEqualTo("MyRule");
    }

    @Test
    void getCompletionItems_findLHSandRHS() {
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

    @Test
    void definitionJumpsToDeclareBlock() throws Exception {
        String drl = """
                package demo;

                declare Person
                  name : String
                end

                rule R
                  when
                    Person( name == "x" )
                  then
                end
                """;
        DroolsLspDocumentService service = getDroolsLspDocumentService(drl);

        DefinitionParams params = new DefinitionParams(
                new TextDocumentIdentifier("myDocument"), new Position(8, 6));
        List<? extends Location> locations = service.definition(params).get().getLeft();

        assertThat(locations).hasSize(1);
        assertThat(locations.get(0).getUri()).isEqualTo("myDocument");
        assertThat(locations.get(0).getRange().getStart().getLine()).isEqualTo(2);
    }

    @Test
    void brokenDocumentProducesSyntaxDiagnostics() {
        String brokenDrl = "rule R when Person( then end";
        DroolsLspDocumentService service = getDroolsLspDocumentService(brokenDrl);

        List<Diagnostic> diags = service.validate("myDocument");
        assertThat(diags).isNotEmpty();
        assertThat(diags)
                 .anySatisfy(d -> assertThat(d.getSource()).isEqualTo("drools-parser"));
    }

    @Test
    void structuralLintComplementsSyntaxDiagnostics() {
        // Missing 'end': the lint pass anchors a friendly warning at the rule
        // header, alongside whatever the parser reports near EOF.
        String drl = "rule \"A\"\n  when\n  then\n";
        DroolsLspDocumentService service = getDroolsLspDocumentService(drl);

        List<Diagnostic> diags = service.validate("myDocument");
        assertThat(diags)
                .anySatisfy(d -> assertThat(d.getSource()).isEqualTo("drools-lint"));
    }

    @Test
    void cleanDocumentProducesNoDiagnostics() {
        String drl = "package demo;\n"
                + "rule \"R\"\n"
                + "  when\n"
                + "  then\n"
                + "end\n";
        DroolsLspDocumentService service = getDroolsLspDocumentService(drl);

        assertThat(service.validate("myDocument")).isEmpty();
    }
}