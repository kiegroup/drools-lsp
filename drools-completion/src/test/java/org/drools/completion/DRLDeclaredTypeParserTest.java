package org.drools.completion;

import java.util.List;

import org.drools.drl.parser.antlr4.DRL10Parser;
import org.drools.drl.parser.antlr4.DRLParserHelper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DRLDeclaredTypeParserTest {

    private static List<DeclaredType> parse(String drl) {
        DRL10Parser parser = DRLParserHelper.createDrlParser(drl);
        parser.removeErrorListeners(); // partial/malformed DRL is expected here
        return DRLDeclaredTypeParser.extractFromCompilationUnit(parser.compilationUnit());
    }

    @Test
    void parsesDeclaredTypeWithFields() {
        String drl = "package demo;\n"
                + "declare Person\n"
                + "  name : String\n"
                + "  age : int\n"
                + "end\n";
        List<DeclaredType> types = parse(drl);

        assertThat(types).hasSize(1);
        DeclaredType person = types.get(0);
        assertThat(person.name).isEqualTo("Person");
        assertThat(person.isEnum).isFalse();
        assertThat(person.fields).extracting(f -> f.name).containsExactly("name", "age");
        assertThat(person.fields).extracting(f -> f.type).containsExactly("String", "int");
    }

    @Test
    void parsesDeclaredEnumWithConstants() {
        String drl = "declare enum Severity\n"
                + "  LOW(1), HIGH(2);\n"
                + "  level : int\n"
                + "end\n";
        List<DeclaredType> types = parse(drl);

        assertThat(types).hasSize(1);
        DeclaredType severity = types.get(0);
        assertThat(severity.isEnum).isTrue();
        assertThat(severity.fields).extracting(f -> f.name)
                .contains("LOW", "HIGH", "level");
        assertThat(severity.fields.get(0).type).isEqualTo("Severity");
        assertThat(severity.fields.get(0).args).isEqualTo("1");
    }

    @Test
    void recordsExtendsParent() {
        String drl = "declare Employee extends Person\n"
                + "  salary : double\n"
                + "end\n";
        List<DeclaredType> types = parse(drl);

        assertThat(types).hasSize(1);
        assertThat(types.get(0).extendsName).isEqualTo("Person");
    }

    @Test
    void malformedSurroundingsStillYieldPartialResults() {
        // Parse errors after the declare must neither throw nor discard the
        // already-parsed type. (Recovery from errors *before* a declare may
        // legitimately consume it — partial results are best-effort.)
        String drl = "declare Alpha\n"
                + "  id : long\n"
                + "end\n"
                + "rule broken when Person( then\n";
        List<DeclaredType> types = parse(drl);

        assertThat(types).extracting(t -> t.name).contains("Alpha");
    }

    @Test
    void nullAndEmptyTextYieldNoTypes() {
        assertThat(DRLDeclaredTypeParser.extractFromCompilationUnit(null)).isEmpty();
        assertThat(parse("")).isEmpty();
    }
}
