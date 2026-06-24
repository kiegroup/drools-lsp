package org.drools.lsp.server;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class MavenClasspathResolverTest {

    @TempDir
    Path tempDir;

    @Test
    void findPomFiles() throws IOException {
        Files.writeString(tempDir.resolve("pom.xml"), "<project/>");

        Path submodule = tempDir.resolve("module-a");
        Files.createDirectories(submodule);
        Files.writeString(submodule.resolve("pom.xml"), "<project/>");

        // pom.xml inside target/ should be skipped
        Path targetDir = tempDir.resolve("module-a/target/generated");
        Files.createDirectories(targetDir);
        Files.writeString(targetDir.resolve("pom.xml"), "<project/>");

        // pom.xml inside hidden dir should be skipped
        Path hiddenDir = tempDir.resolve(".hidden");
        Files.createDirectories(hiddenDir);
        Files.writeString(hiddenDir.resolve("pom.xml"), "<project/>");

        List<Path> poms = MavenClasspathResolver.findPomFiles(tempDir);

        assertThat(poms).containsExactlyInAnyOrder(
            tempDir.resolve("pom.xml"),
            submodule.resolve("pom.xml")
        );
    }

    @Test
    void resolveBuildOutputDirsReturnsClassDirsWithoutInvokingMaven() throws IOException {
        // A minimal project with compiled output but no resolvable dependencies.
        // The build-output dirs must come back from the filesystem alone (no mvn),
        // so the server can index the project's own classes before the slower
        // dependency-JAR resolution runs.
        Files.writeString(tempDir.resolve("pom.xml"), "<project/>");
        Path classes = tempDir.resolve("target/classes");
        Files.createDirectories(classes);

        Set<Path> dirs = MavenClasspathResolver.resolveBuildOutputDirs(tempDir);

        assertThat(dirs).contains(classes);
    }

    @Test
    void resolveReturnsClasspathEntriesForRealProject() {
        Path projectRoot = Path.of(System.getProperty("user.dir")).getParent();
        if (!Files.exists(projectRoot.resolve("pom.xml"))) {
            return;
        }

        Set<Path> entries = MavenClasspathResolver.resolve(projectRoot);

        assertThat(entries).isNotEmpty();
        assertThat(entries.stream().anyMatch(p -> p.toString().endsWith(".jar"))).isTrue();
    }
}
