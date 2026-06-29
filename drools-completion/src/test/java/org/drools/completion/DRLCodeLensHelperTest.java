package org.drools.completion;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.lsp4j.CodeLens;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DRLCodeLensHelperTest {

    private static final String DRL = """
            package demo;
            declare Person
              name : String
            end
            declare Unused
              x : int
            end
            rule R
              when
                Person( )
              then
                insert(new Person());
            end
            """;

    @Test
    void oneLensPerDeclaredTypeWithUseCount() {
        List<CodeLens> lenses = DRLCodeLensHelper.codeLenses(
                "myDocument", DRL, Map.of(), ClassIndex.empty(), Set.of());

        assertThat(lenses).hasSize(2);

        CodeLens person = lensAtLine(lenses, 1);
        assertThat(person.getCommand().getTitle()).isEqualTo("2 references"); // pattern + RHS new
        assertThat(person.getCommand().getCommand()).isEqualTo("drools.peekReferences");
        assertThat(person.getRange().getStart().getCharacter()).isEqualTo(8); // "declare Person"

        CodeLens unused = lensAtLine(lenses, 4);
        assertThat(unused.getCommand().getTitle()).isEqualTo("0 references");
    }

    @Test
    void singleReferenceIsSingular() {
        String drl = """
                package demo;
                declare Person
                  name : String
                end
                rule R
                  when
                    Person( )
                  then
                end
                """;
        List<CodeLens> lenses = DRLCodeLensHelper.codeLenses(
                "myDocument", drl, Map.of(), ClassIndex.empty(), Set.of());

        assertThat(lenses).hasSize(1);
        assertThat(lenses.get(0).getCommand().getTitle()).isEqualTo("1 reference");
    }

    @Test
    void lensCommandCarriesUriPositionAndLocations() {
        List<CodeLens> lenses = DRLCodeLensHelper.codeLenses(
                "myDocument", DRL, Map.of(), ClassIndex.empty(), Set.of());

        List<Object> args = lensAtLine(lenses, 1).getCommand().getArguments();
        assertThat(args).hasSize(3);
        assertThat(args.get(0)).isEqualTo("myDocument");
        assertThat(args.get(1)).isInstanceOfSatisfying(Position.class,
                p -> assertThat(p.getLine()).isEqualTo(1));
        assertThat(args.get(2)).asInstanceOf(
                org.assertj.core.api.InstanceOfAssertFactories.list(Location.class)).hasSize(2);
    }

    @Test
    void includesEnumDeclarations() {
        String drl = "package demo;\ndeclare enum Color\n  RED, GREEN\nend\n";
        List<CodeLens> lenses = DRLCodeLensHelper.codeLenses(
                "myDocument", drl, Map.of(), ClassIndex.empty(), Set.of());

        assertThat(lenses).hasSize(1);
        assertThat(lenses.get(0).getCommand().getTitle()).isEqualTo("0 references");
    }

    @Test
    void noLensesForNullText() {
        assertThat(DRLCodeLensHelper.codeLenses(
                "myDocument", null, Map.of(), ClassIndex.empty(), Set.of()))
                .isEmpty();
    }

    private static CodeLens lensAtLine(List<CodeLens> lenses, int line) {
        return lenses.stream()
                .filter(l -> l.getRange().getStart().getLine() == line)
                .findFirst().orElseThrow();
    }
}
