package org.drools.completion;

import java.util.List;

import org.drools.completion.fixtures.InitProbe;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ClassMemberIndexTest {

    private final ClassMemberIndex index =
            new ClassMemberIndex(getClass().getClassLoader());

    @Test
    void exposesBeanPropertiesAndPublicFields() {
        List<Field> members = index.membersOf("org.drools.completion.fixtures.Pet");

        assertThat(members).extracting(f -> f.name)
                .contains("name", "friendly", "legs");
        assertThat(members).extracting(f -> f.name)
                .doesNotContain("class", "getClass", "ignoredBecauseItTakesArgs");
        assertThat(members)
                .anySatisfy(f -> {
                    assertThat(f.name).isEqualTo("name");
                    assertThat(f.type).isEqualTo("String");
                });
    }

    @Test
    void reflectionDoesNotRunStaticInitializers() {
        index.membersOf("org.drools.completion.fixtures.Pet");
        assertThat(InitProbe.petInitialized)
                .as("membersOf must not execute static initializers of user classes")
                .isFalse();
    }

    @Test
    void exposesEnumConstants() {
        List<Field> members = index.membersOf("org.drools.completion.fixtures.PetKind");

        assertThat(members).extracting(f -> f.name).contains("CAT", "DOG");
        assertThat(members)
                .anySatisfy(f -> {
                    assertThat(f.name).isEqualTo("CAT");
                    assertThat(f.type).isEqualTo("PetKind");
                });
    }

    @Test
    void unknownClassYieldsNoMembers() {
        assertThat(index.membersOf("does.not.Exist")).isEmpty();
        // Negative result is cached and stays consistent.
        assertThat(index.membersOf("does.not.Exist")).isEmpty();
    }

    @Test
    void emptyIndexYieldsNoMembers() {
        assertThat(ClassMemberIndex.empty().membersOf("java.lang.String")).isEmpty();
    }
}
