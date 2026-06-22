package org.drools.completion;

import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DRLDocFormatterTest {

    @Test
    void inlineCodeBecomesBackticks() {
        assertThat(DRLDocFormatter.format("uses {@code getX()} here", null))
                .isEqualTo("uses `getX()` here");
    }

    @Test
    void literalContentIsMarkdownEscaped() {
        assertThat(DRLDocFormatter.format("{@literal *not bold*}", null))
                .isEqualTo("\\*not bold\\*");
    }

    @Test
    void linkWithTargetRendersMarkdownLink() {
        Map<String, String> targets = Map.of("Person", "file:///types.drl");
        assertThat(DRLDocFormatter.format("see {@link Person}", targets))
                .isEqualTo("see [Person](file:///types.drl)");
    }

    @Test
    void linkLabelIsUsedWhenPresent() {
        Map<String, String> targets = Map.of("Person", "file:///types.drl");
        assertThat(DRLDocFormatter.format("see {@link Person the patient}", targets))
                .isEqualTo("see [the patient](file:///types.drl)");
    }

    @Test
    void memberReferenceLooksUpTheTypePart() {
        Map<String, String> targets = Map.of("Person", "file:///types.drl");
        assertThat(DRLDocFormatter.format("{@link Person#name}", targets))
                .isEqualTo("[Person#name](file:///types.drl)");
    }

    @Test
    void unresolvedLinkFallsBackToCode() {
        assertThat(DRLDocFormatter.format("see {@link Person}", null))
                .isEqualTo("see `Person`");
    }

    @Test
    void unresolvedLinkplainFallsBackToPlainText() {
        assertThat(DRLDocFormatter.format("see {@linkplain Person}", null))
                .isEqualTo("see Person");
    }

    @Test
    void unsupportedTagsAreLeftUntouched() {
        assertThat(DRLDocFormatter.format("{@value Config#MAX}", null))
                .isEqualTo("{@value Config#MAX}");
    }

    @Test
    void nullAndEmptyPassThrough() {
        assertThat(DRLDocFormatter.format(null, null)).isNull();
        assertThat(DRLDocFormatter.format("", null)).isEmpty();
    }
}
