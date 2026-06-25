package org.drools.completion;

import java.util.List;

import org.eclipse.lsp4j.FoldingRange;
import org.eclipse.lsp4j.FoldingRangeKind;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class DRLFoldingRangeHelperTest {

    @Test
    void emptyOrNullYieldsNoRanges() {
        assertThat(DRLFoldingRangeHelper.foldingRanges(null)).isEmpty();
        assertThat(DRLFoldingRangeHelper.foldingRanges("")).isEmpty();
    }

    @Test
    void foldsConstructsAndBlockComments() {
        String drl =
            "/*\n"                           // 0
            + " * banner\n"                  // 1
            + " */\n"                        // 2
            + "global java.util.List log\n"  // 3 (single line -> no fold)
            + "declare Person\n"             // 4
            + "  name : String\n"            // 5
            + "end\n"                        // 6
            + "rule \"R\"\n"                 // 7
            + "  when\n"                     // 8
            + "  then\n"                     // 9
            + "end\n";                       // 10

        List<FoldingRange> ranges = DRLFoldingRangeHelper.foldingRanges(drl);

        assertThat(ranges)
                .extracting(FoldingRange::getStartLine, FoldingRange::getEndLine, FoldingRange::getKind)
                .containsExactlyInAnyOrder(
                        tuple(4, 6, FoldingRangeKind.Region),    // declare Person ... end
                        tuple(7, 10, FoldingRangeKind.Region),   // rule "R" ... end
                        tuple(0, 2, FoldingRangeKind.Comment));  // /* ... */
    }

    @Test
    void singleLineConstructsProduceNoFold() {
        // A one-line declare and a global — nothing multi-line to fold.
        String drl = "global Foo bar\n";

        assertThat(DRLFoldingRangeHelper.foldingRanges(drl)).isEmpty();
    }
}
