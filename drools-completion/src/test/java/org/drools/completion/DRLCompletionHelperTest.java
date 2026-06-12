package org.drools.completion;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
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

    @Test
    void classCompletionInPatternPosition() throws IOException {
        Path tempDir = Files.createTempDirectory("drools-test-classes");
        try {
            Files.createDirectories(tempDir.resolve("org/example"));
            Files.createFile(tempDir.resolve("org/example/Person.class"));
            Files.createFile(tempDir.resolve("org/example/Pet.class"));
            Files.createFile(tempDir.resolve("org/example/Address.class"));

            ClassIndex classIndex = ClassIndex.build(Set.of(tempDir));

            String text = """
                    package org.example;

                    import org.example.Person;

                    rule MyRule
                      when
                        $p : \s
                      then
                    end
                    """;

            Position caretPosition = new Position(6, 8);
            List<CompletionItem> result = DRLCompletionHelper.getCompletionItems(text, caretPosition, getLanguageClient(), classIndex);

            List<String> labels = result.stream().map(CompletionItem::getLabel).toList();
            assertThat(labels).contains("Person", "Pet", "Address");

            List<CompletionItem> classItems = result.stream()
                .filter(item -> item.getKind() == CompletionItemKind.Class)
                .toList();
            assertThat(classItems).isNotEmpty();

            CompletionItem personItem = classItems.stream()
                .filter(item -> item.getLabel().equals("Person")).findFirst().orElseThrow();
            CompletionItem addressItem = classItems.stream()
                .filter(item -> item.getLabel().equals("Address")).findFirst().orElseThrow();

            // Person is imported, so should sort before Address
            assertThat(personItem.getSortText().compareTo(addressItem.getSortText())).isLessThan(0);
        } finally {
            try (var walk = Files.walk(tempDir)) {
                walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try { Files.delete(p); } catch (IOException ignored) {}
                });
            }
        }
    }

    @Test
    void classCompletionWithTypedPrefix() throws IOException {
        Path tempDir = Files.createTempDirectory("drools-test-classes");
        try {
            Files.createDirectories(tempDir.resolve("org/example"));
            Files.createFile(tempDir.resolve("org/example/Person.class"));
            Files.createFile(tempDir.resolve("org/example/Pet.class"));
            Files.createFile(tempDir.resolve("org/example/Address.class"));

            ClassIndex classIndex = ClassIndex.build(Set.of(tempDir));

            String text = """
                    package org.example;

                    rule MyRule
                      when
                        $p : Per
                      then
                    end
                    """;

            Position caretPosition = new Position(4, 12);
            List<CompletionItem> result = DRLCompletionHelper.getCompletionItems(text, caretPosition, getLanguageClient(), classIndex);

            List<String> classLabels = result.stream()
                .filter(item -> item.getKind() == CompletionItemKind.Class)
                .map(CompletionItem::getLabel)
                .toList();

            assertThat(classLabels).containsExactly("Person");
            assertThat(classLabels).doesNotContain("Pet", "Address");
        } finally {
            try (var walk = Files.walk(tempDir)) {
                walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try { Files.delete(p); } catch (IOException ignored) {}
                });
            }
        }
    }

    @Test
    void fieldCompletionFromDeclaredType() {
        String text = """
                package demo;

                declare Person
                  name : String
                  age : int
                end

                rule R
                  when
                    Person(  )
                  then
                end
                """;

        // Inside the pattern's parens.
        Position caretPosition = new Position(9, 12);
        List<CompletionItem> result = DRLCompletionHelper.getCompletionItems(
                text, caretPosition, getLanguageClient(), ClassIndex.empty());

        List<CompletionItem> fieldItems = result.stream()
                .filter(item -> item.getKind() == CompletionItemKind.Field)
                .toList();
        assertThat(fieldItems).extracting(CompletionItem::getLabel)
                .contains("name", "age");
        CompletionItem nameItem = fieldItems.stream()
                .filter(item -> item.getLabel().equals("name")).findFirst().orElseThrow();
        assertThat(nameItem.getDetail()).isEqualTo("String");
    }

    @Test
    void fieldCompletionFromClasspathTypeViaImport() {
        String text = """
                package demo;

                import org.drools.completion.fixtures.Pet;

                rule R
                  when
                    Pet(  )
                  then
                end
                """;

        ClassMemberIndex memberIndex = new ClassMemberIndex(getClass().getClassLoader());
        Position caretPosition = new Position(6, 9);
        List<CompletionItem> result = DRLCompletionHelper.getCompletionItems(
                text, caretPosition, getLanguageClient(), ClassIndex.empty(), memberIndex);

        List<String> fieldLabels = result.stream()
                .filter(item -> item.getKind() == CompletionItemKind.Field)
                .map(CompletionItem::getLabel)
                .toList();
        assertThat(fieldLabels).contains("name", "friendly", "legs");
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