package org.drools.completion;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class WorkspaceSiblingResolversTest {

    @AfterEach
    void resetResolver() {
        WorkspaceSiblingResolvers.setActive(null);
    }

    @Test
    void defaultResolverReturnsSameDirectoryDrlFiles(@TempDir Path tmp) throws Exception {
        Path a = Files.createFile(tmp.resolve("A.drl"));
        Path b = Files.createFile(tmp.resolve("B.drl"));
        Files.createFile(tmp.resolve("notes.txt"));

        List<Path> siblings = WorkspaceSiblingResolvers.active().resolveSiblings(a);

        assertThat(siblings).containsExactly(b);
    }

    @Test
    void defaultResolverHandlesNullAndMissingPaths(@TempDir Path tmp) {
        assertThat(WorkspaceSiblingResolvers.active().resolveSiblings(null)).isEmpty();
        assertThat(WorkspaceSiblingResolvers.active()
                .resolveSiblings(tmp.resolve("missing/X.drl"))).isEmpty();
    }

    @Test
    void setActiveSwapsResolverAndNullRestoresDefault(@TempDir Path tmp) throws Exception {
        Path a = Files.createFile(tmp.resolve("A.drl"));
        Path b = Files.createFile(tmp.resolve("B.drl"));

        WorkspaceSiblingResolvers.setActive(file -> List.of());
        assertThat(WorkspaceSiblingResolvers.active().resolveSiblings(a)).isEmpty();

        WorkspaceSiblingResolvers.setActive(null);
        assertThat(WorkspaceSiblingResolvers.active().resolveSiblings(a)).containsExactly(b);
    }
}
