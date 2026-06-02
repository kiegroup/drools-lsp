package org.drools.completion;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class ClassIndexTest {

    @TempDir
    Path tempDir;

    @Test
    void scanDirectory() throws IOException {
        Path classesDir = tempDir.resolve("target/classes");
        Path personClass = classesDir.resolve("org/example/Person.class");
        Files.createDirectories(personClass.getParent());
        Files.createFile(personClass);

        Path addressClass = classesDir.resolve("org/example/Address.class");
        Files.createFile(addressClass);

        // Inner class should be skipped
        Path innerClass = classesDir.resolve("org/example/Person$Builder.class");
        Files.createFile(innerClass);

        // module-info should be skipped
        Path moduleInfo = classesDir.resolve("module-info.class");
        Files.createFile(moduleInfo);

        ClassIndex index = ClassIndex.build(Set.of(classesDir));

        assertThat(index.getMatching("Per")).containsExactly("org.example.Person");
        assertThat(index.getMatching("Addr")).containsExactly("org.example.Address");
        assertThat(index.getMatching("Person$")).isEmpty();
        assertThat(index.getMatching("module")).isEmpty();
    }

    @Test
    void scanJar() throws IOException {
        Path jarPath = tempDir.resolve("test.jar");
        try (java.util.jar.JarOutputStream jos = new java.util.jar.JarOutputStream(
                Files.newOutputStream(jarPath))) {
            jos.putNextEntry(new java.util.jar.JarEntry("com/acme/Order.class"));
            jos.closeEntry();
            jos.putNextEntry(new java.util.jar.JarEntry("com/acme/Order$Item.class"));
            jos.closeEntry();
            jos.putNextEntry(new java.util.jar.JarEntry("com/acme/Product.class"));
            jos.closeEntry();
        }

        ClassIndex index = ClassIndex.build(Set.of(jarPath));

        assertThat(index.getMatching("Or")).containsExactly("com.acme.Order");
        assertThat(index.getMatching("Prod")).containsExactly("com.acme.Product");
        assertThat(index.getMatching("Order$")).isEmpty();
    }

    @Test
    void mixedSourcesAndPrefixMatching() throws IOException {
        Path classesDir = tempDir.resolve("classes");
        Path personClass = classesDir.resolve("org/example/Person.class");
        Files.createDirectories(personClass.getParent());
        Files.createFile(personClass);

        Path jarPath = tempDir.resolve("security.jar");
        try (java.util.jar.JarOutputStream jos = new java.util.jar.JarOutputStream(
                Files.newOutputStream(jarPath))) {
            jos.putNextEntry(new java.util.jar.JarEntry("java/security/Permission.class"));
            jos.closeEntry();
        }

        ClassIndex index = ClassIndex.build(Set.of(classesDir, jarPath));

        assertThat(index.getMatching("Per"))
            .containsExactlyInAnyOrder("org.example.Person", "java.security.Permission");
        assertThat(index.size()).isEqualTo(2);
        assertThat(index.getMatching("Xyz")).isEmpty();
    }

    @Test
    void emptyIndex() {
        ClassIndex index = ClassIndex.empty();
        assertThat(index.getMatching("Any")).isEmpty();
        assertThat(index.size()).isEqualTo(0);
    }
}
