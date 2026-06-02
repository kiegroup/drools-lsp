package org.drools.lsp.server;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import org.drools.completion.ClassIndex;
import org.drools.completion.DRLCompletionHelper;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ClassCompletionIntegrationTest {

    private static ClassIndex classIndex;

    @BeforeAll
    static void resolveClasspath() {
        Path projectRoot = Path.of(System.getProperty("user.dir")).getParent();
        assertThat(Files.exists(projectRoot.resolve("pom.xml")))
            .as("drools-lsp project root must be available")
            .isTrue();

        Set<Path> entries = MavenClasspathResolver.resolve(projectRoot);
        classIndex = ClassIndex.build(entries);
        assertThat(classIndex.size()).isGreaterThan(0);
    }

    @Test
    void dependencyJarClassSuggestedInPatternPosition() {
        String text = """
                package org.example;

                rule TestRule
                  when
                    $t : \s
                  then
                end
                """;

        Position caretPosition = new Position(4, 8);
        List<CompletionItem> result = DRLCompletionHelper.getCompletionItems(
            text, caretPosition, null, classIndex);

        List<CompletionItem> classItems = result.stream()
            .filter(item -> item.getKind() == CompletionItemKind.Class)
            .toList();

        assertThat(classItems).isNotEmpty();

        // antlr4-runtime JAR class should appear (via detail = FQCN)
        assertThat(classItems.stream().map(CompletionItem::getDetail).toList())
            .contains("org.antlr.v4.runtime.ANTLRFileStream");

        CompletionItem item = classItems.stream()
            .filter(i -> "org.antlr.v4.runtime.ANTLRFileStream".equals(i.getDetail()))
            .findFirst().orElseThrow();
        assertThat(item.getLabel()).isEqualTo("ANTLRFileStream");
        // Not imported, so sortText should start with "1_"
        assertThat(item.getSortText()).startsWith("1_");
    }

    @Test
    void importedDependencyClassRankedFirst() {
        String text = """
                package org.example;

                import org.antlr.v4.runtime.ANTLRFileStream;

                rule TestRule
                  when
                    $t : \s
                  then
                end
                """;

        Position caretPosition = new Position(6, 8);
        List<CompletionItem> result = DRLCompletionHelper.getCompletionItems(
            text, caretPosition, null, classIndex);

        CompletionItem item = result.stream()
            .filter(i -> i.getKind() == CompletionItemKind.Class)
            .filter(i -> "org.antlr.v4.runtime.ANTLRFileStream".equals(i.getDetail()))
            .findFirst().orElseThrow();

        // Imported, so sortText should start with "0_"
        assertThat(item.getSortText()).startsWith("0_");
    }
}
