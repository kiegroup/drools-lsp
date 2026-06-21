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
        // Root pom declares one module; only that module's pom.xml is discovered.
        Files.writeString(tempDir.resolve("pom.xml"),
            "<project><modules><module>module-a</module></modules></project>");

        Path submodule = tempDir.resolve("module-a");
        Files.createDirectories(submodule);
        Files.writeString(submodule.resolve("pom.xml"), "<project/>");

        // pom.xml in a non-declared sibling directory must NOT be discovered —
        // the resolver never walks the tree, so adjacent projects cannot
        // contaminate the class index.
        Path siblingDir = tempDir.resolve("other-project");
        Files.createDirectories(siblingDir);
        Files.writeString(siblingDir.resolve("pom.xml"), "<project/>");

        List<Path> poms = MavenClasspathResolver.findPomFiles(tempDir);

        assertThat(poms).containsExactlyInAnyOrder(
            tempDir.resolve("pom.xml"),
            submodule.resolve("pom.xml")
        );
    }

    @Test
    void fallsBackToTreeWalkWhenNoRootPom() throws IOException {
        // No pom.xml at the root: scan the tree for poms so a pom-less / nested
        // layout still resolves a best-effort classpath (skipping target/).
        Path projA = tempDir.resolve("proj-a");
        Files.createDirectories(projA);
        Files.writeString(projA.resolve("pom.xml"), "<project/>");

        // pom.xml inside target/ must still be skipped by the fallback walk.
        Path generated = tempDir.resolve("proj-a/target/generated");
        Files.createDirectories(generated);
        Files.writeString(generated.resolve("pom.xml"), "<project/>");

        List<Path> poms = MavenClasspathResolver.findPomFiles(tempDir);

        assertThat(poms).containsExactly(projA.resolve("pom.xml"));
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
