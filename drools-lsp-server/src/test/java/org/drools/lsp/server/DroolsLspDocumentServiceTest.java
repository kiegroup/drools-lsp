package org.drools.lsp.server;

import java.util.List;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.DefinitionParams;
import org.eclipse.lsp4j.DocumentDiagnosticParams;
import org.eclipse.lsp4j.DocumentDiagnosticReport;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.HoverParams;
import org.eclipse.lsp4j.InlayHint;
import org.eclipse.lsp4j.InlayHintParams;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
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
    void pullDiagnosticReportsErrors() throws Exception {
        String brokenDrl = "rule R when Person( then end";
        DroolsLspDocumentService service = getDroolsLspDocumentService(brokenDrl);

        DocumentDiagnosticReport report = service.diagnostic(
                new DocumentDiagnosticParams(new TextDocumentIdentifier("myDocument"))).get();

        assertThat(report.getRelatedFullDocumentDiagnosticReport().getItems())
                .isNotEmpty()
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

    @Test
    void hoverShowsDeclaredTypeStructure() throws Exception {
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

        HoverParams params = new HoverParams(
                new TextDocumentIdentifier("myDocument"), new Position(8, 6));
        Hover hover = service.hover(params).get();

        assertThat(hover).isNotNull();
        assertThat(hover.getContents().getRight().getValue())
                .contains("declare Person")
                .contains("name : String");
    }

    @Test
    void hoverShowsBoundVariableType() throws Exception {
        String drl = """
                package demo;

                declare Person
                  name : String
                end

                rule R
                  when
                    $p : Person( name == "x" )
                  then
                end
                """;
        DroolsLspDocumentService service = getDroolsLspDocumentService(drl);

        // Caret on "$p" at line 8, character 4.
        HoverParams params = new HoverParams(
                new TextDocumentIdentifier("myDocument"), new Position(8, 4));
        Hover hover = service.hover(params).get();

        assertThat(hover).isNotNull();
        assertThat(hover.getContents().getRight().getValue())
                .contains("declare Person")
                .contains("name : String");
    }

    @Test
    void hoverShowsJavaLangTypeWithoutExplicitImport() throws Exception {
        // java.lang.* is implicitly available in DRL — no import needed.
        String drl = """
                package demo;

                rule R
                  when
                    Object()
                  then
                end
                """;
        DroolsLspDocumentService service = getDroolsLspDocumentService(drl);

        // Caret on "Object" at line 4, character 4.
        HoverParams params = new HoverParams(
                new TextDocumentIdentifier("myDocument"), new Position(4, 4));
        Hover hover = service.hover(params).get();

        assertThat(hover).isNotNull();
        assertThat(hover.getContents().getRight().getValue())
                .contains("java.lang.Object");
    }

    @Test
    void inlayHintShowsBindingTypeAtRhsUsage() throws Exception {
        String drl = "rule \"r\" when\n"
                + "  $p : Patient(age > 18)\n"
                + "then\n"
                + "  update($p);\n"
                + "end\n";
        DroolsLspDocumentService service = getDroolsLspDocumentService(drl);

        InlayHintParams params = new InlayHintParams(
                new TextDocumentIdentifier("myDocument"),
                new Range(new Position(0, 0), new Position(99, 0)));
        List<InlayHint> hints = service.inlayHint(params).get();

        assertThat(hints).isNotEmpty();
        assertThat(hints).anySatisfy(h ->
                assertThat(h.getLabel().getLeft()).isEqualTo(": Patient"));
    }

    @Test
    void inlayHintReturnsEmptyWhenDisabledBySetting() throws Exception {
        String previous = System.getProperty("drools.lsp.inlayHints.enabled");
        System.setProperty("drools.lsp.inlayHints.enabled", "false");
        try {
            String drl = "rule \"r\" when\n"
                    + "  $p : Patient(age > 18)\n"
                    + "then\n"
                    + "  update($p);\n"
                    + "end\n";
            DroolsLspDocumentService service = getDroolsLspDocumentService(drl);

            InlayHintParams params = new InlayHintParams(
                    new TextDocumentIdentifier("myDocument"),
                    new Range(new Position(0, 0), new Position(99, 0)));

            assertThat(service.inlayHint(params).get()).isEmpty();
        } finally {
            if (previous == null) {
                System.clearProperty("drools.lsp.inlayHints.enabled");
            } else {
                System.setProperty("drools.lsp.inlayHints.enabled", previous);
            }
        }
    }
}