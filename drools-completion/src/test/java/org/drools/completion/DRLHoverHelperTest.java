package org.drools.completion;

import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class DRLHoverHelperTest {

    private static final String DECLARE_DRL = """
            package demo;

            /** A person known to the rules. */
            declare Person
              name : String
              age : int
            end

            rule R
              when
                Person( name == "x" )
              then
            end
            """;

    private static String content(Hover hover) {
        assertThat(hover).isNotNull();
        return hover.getContents().getRight().getValue();
    }

    @Test
    void hoverOnDeclaredTypeShowsFieldsAndDoc() {
        // Caret on "Person" in the pattern.
        Hover hover = DRLHoverHelper.hover(DECLARE_DRL, new Position(10, 6),
                ClassIndex.empty(), ClassMemberIndex.empty(), null);

        String md = content(hover);
        assertThat(md).contains("declare Person");
        assertThat(md).contains("name : String");
        assertThat(md).contains("age : int");
        assertThat(md).contains("A person known to the rules.");
    }

    @Test
    void hoverOnFieldShowsItsTypeAndOwner() {
        // Caret on "name" inside the constraint.
        Hover hover = DRLHoverHelper.hover(DECLARE_DRL, new Position(10, 13),
                ClassIndex.empty(), ClassMemberIndex.empty(), null);

        String md = content(hover);
        assertThat(md).contains("name");
        assertThat(md).contains("String");
        assertThat(md).contains("Person");
    }

    @Test
    void hoverOnSiblingDeclaredType(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("Types.drl"),
                "package demo;\ndeclare Address\n  code : String\nend\n");
        Path current = dir.resolve("rules.drl");
        String drl = "package demo;\nrule R\n  when\n    Address( )\n  then\nend\n";
        Files.writeString(current, drl);

        Hover hover = DRLHoverHelper.hover(drl, new Position(3, 6),
                ClassIndex.empty(), ClassMemberIndex.empty(), current);

        assertThat(content(hover)).contains("declare Address").contains("code : String");
    }

    @Test
    void hoverOnClasspathTypeViaImport() {
        String drl = """
                package demo;

                import org.drools.completion.fixtures.Pet;

                rule R
                  when
                    Pet( )
                  then
                end
                """;
        ClassMemberIndex memberIndex = new ClassMemberIndex(getClass().getClassLoader());

        Hover hover = DRLHoverHelper.hover(drl, new Position(6, 5),
                ClassIndex.empty(), memberIndex, null);

        String md = content(hover);
        assertThat(md).contains("org.drools.completion.fixtures.Pet");
        assertThat(md).contains("name").contains("friendly").contains("legs");
    }

    @Test
    void hoverOnUnknownSymbolReturnsNull() {
        assertThat(DRLHoverHelper.hover(DECLARE_DRL, new Position(7, 3),
                ClassIndex.empty(), ClassMemberIndex.empty(), null)).isNull();
    }

    @Test
    void hoverOnNullTextReturnsNull() {
        assertThat(DRLHoverHelper.hover(null, new Position(0, 0),
                ClassIndex.empty(), ClassMemberIndex.empty(), null)).isNull();
    }
}
