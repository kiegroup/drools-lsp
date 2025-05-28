package org.drools.completion;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.ShowMessageRequestParams;
import org.eclipse.lsp4j.services.LanguageClient;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DRLCompletionHelperTest {

    @Test
    void getCompletionItems() {
        String text = """
                package org.example;
                
                import org.example.model.Dog;
                
                global java.util.List list;
                
                rule MyRule
                  when
                    $dog : Dog(name == "Bart")
                  then
                    list.add($dog.getName());
                end
                """;

        Position caretPosition = new Position();
        List<CompletionItem> result;

        // at the beginning of the file
        caretPosition.setCharacter(0); // caret character(column) position is zero-based
        caretPosition.setLine(0); // caret line position is zero-based
        result = DRLCompletionHelper.getCompletionItems(text, caretPosition, getLanguageClient());
        assertThat(completionItemStrings(result)).contains("package", "unit"); // once in a file
        assertThat(completionItemStrings(result)).contains("import", "global", "rule"); // top level statements

        // 'package '
        caretPosition.setCharacter(8);
        result = DRLCompletionHelper.getCompletionItems(text, caretPosition, getLanguageClient());
        assertThat(result).isEmpty(); // no completion for identifier

        // 'package org.example;\n\n'
        caretPosition.setLine(2);
        caretPosition.setCharacter(0);
        result = DRLCompletionHelper.getCompletionItems(text, caretPosition, getLanguageClient());
        assertThat(completionItemStrings(result)).doesNotContain("package");  // once in a file (already used)
        assertThat(completionItemStrings(result)).contains("unit"); // once in a file (not yet used)
        assertThat(completionItemStrings(result)).contains("import", "global", "rule"); // top level statements

        // 'import '
        caretPosition.setLine(2);
        caretPosition.setCharacter(7);
        result = DRLCompletionHelper.getCompletionItems(text, caretPosition, getLanguageClient());
        assertThat(completionItemStrings(result)).contains("static", "function", "accumulate", "acc"); // 'import static' etc.

        // 'import org.example.model.Dog;\n\n'
        caretPosition.setLine(4);
        caretPosition.setCharacter(0);
        result = DRLCompletionHelper.getCompletionItems(text, caretPosition, getLanguageClient());
        assertThat(completionItemStrings(result)).doesNotContain("package", "unit");  // should be placed before import
        assertThat(completionItemStrings(result)).contains("import", "global", "rule"); // top level statements

        // 'global '
        caretPosition.setLine(4);
        caretPosition.setCharacter(7);
        result = DRLCompletionHelper.getCompletionItems(text, caretPosition, getLanguageClient());
        assertThat(completionItemStrings(result)).contains("boolean", "int"); // types (primitives are not useful, but valid)

        // `rule My`
        caretPosition.setLine(6);
        caretPosition.setCharacter(7);
        result = DRLCompletionHelper.getCompletionItems(text, caretPosition, getLanguageClient());
        assertThat(result).isEmpty(); // no completion for identifier

        // `  wh`
        caretPosition.setLine(7);
        caretPosition.setCharacter(3);
        result = DRLCompletionHelper.getCompletionItems(text, caretPosition, getLanguageClient());
        assertThat(completionItemStrings(result)).contains("when", "salience"); // inside rule

        // `    $d`
        caretPosition.setLine(8);
        caretPosition.setCharacter(6);
        result = DRLCompletionHelper.getCompletionItems(text, caretPosition, getLanguageClient());
        assertThat(completionItemStrings(result)).contains("exists", "not"); // inside LHS

        // `    $dog : Dog(`
        caretPosition.setLine(8);
        caretPosition.setCharacter(15);
        result = DRLCompletionHelper.getCompletionItems(text, caretPosition, getLanguageClient());
        assertThat(result).isEmpty(); // no completion for identifier

        // `  th`
        caretPosition.setLine(9);
        caretPosition.setCharacter(3);
        result = DRLCompletionHelper.getCompletionItems(text, caretPosition, getLanguageClient());
        assertThat(completionItemStrings(result)).doesNotContain("salience"); // attribute is not allowed
        assertThat(completionItemStrings(result)).contains("then", "exists"); // inside LHS

        // `    li`
        caretPosition.setLine(10);
        caretPosition.setCharacter(6);
        result = DRLCompletionHelper.getCompletionItems(text, caretPosition, getLanguageClient());
        assertThat(completionItemStrings(result)).contains("end"); // inside RHS

        // at the end of the file
        caretPosition.setLine(12);
        caretPosition.setCharacter(0);
        result = DRLCompletionHelper.getCompletionItems(text, caretPosition, getLanguageClient());
        assertThat(completionItemStrings(result)).contains("import", "global", "rule"); // top level statements
    }

    private List<String> completionItemStrings(List<CompletionItem> result) {
        return result.stream().map(CompletionItem::getInsertText).toList();
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