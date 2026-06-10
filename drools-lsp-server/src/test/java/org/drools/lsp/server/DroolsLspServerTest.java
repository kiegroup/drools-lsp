package org.drools.lsp.server;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import org.drools.completion.ClassIndex;
import org.eclipse.lsp4j.DidChangeWatchedFilesParams;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class DroolsLspServerTest {

    @TempDir
    Path tempDir;

    @Test
    void classpathEntriesEmptyBeforeInitialize() {
        DroolsLspServer server = new DroolsLspServer();
        assertThat(server.getClasspathEntries()).isEmpty();
    }

    @Test
    void rebuildClassIndexUpdatesDocumentService() throws IOException {
        DroolsLspServer server = TestHelperMethods.getDroolsLspServerForDocument("");

        Path classDir = createClassDir("com/example/Foo.class");
        server.setClasspathEntriesForTest(Set.of(classDir));

        server.rebuildClassIndex();

        ClassIndex index = server.getTextDocumentService().getClassIndexForTest();
        assertThat(index.getMatching("Foo")).contains("com.example.Foo");
    }

    @Test
    void didChangeWatchedFilesTriggersRebuild() throws Exception {
        DroolsLspServer server = TestHelperMethods.getDroolsLspServerForDocument("");

        Path classDir = createClassDir("com/example/Bar.class");
        server.setClasspathEntriesForTest(Set.of(classDir));

        DroolsLspWorkspaceService workspaceService = (DroolsLspWorkspaceService) server.getWorkspaceService();
        workspaceService.didChangeWatchedFiles(new DidChangeWatchedFilesParams());

        Thread.sleep(DroolsLspWorkspaceService.DEBOUNCE_DELAY_MS + 500);

        ClassIndex index = server.getTextDocumentService().getClassIndexForTest();
        assertThat(index.getMatching("Bar")).contains("com.example.Bar");
    }

    @Test
    void rapidFileChangesCoalesceIntoOneRebuild() throws Exception {
        DroolsLspServer server = TestHelperMethods.getDroolsLspServerForDocument("");

        Path classDir = createClassDir("com/example/Baz.class");
        server.setClasspathEntriesForTest(Set.of(classDir));

        DroolsLspWorkspaceService workspaceService = (DroolsLspWorkspaceService) server.getWorkspaceService();
        for (int i = 0; i < 100; i++) {
            workspaceService.didChangeWatchedFiles(new DidChangeWatchedFilesParams());
        }

        // Index should not be rebuilt yet (still within debounce window)
        ClassIndex indexBefore = server.getTextDocumentService().getClassIndexForTest();
        assertThat(indexBefore.getMatching("Baz")).isEmpty();

        Thread.sleep(DroolsLspWorkspaceService.DEBOUNCE_DELAY_MS + 500);

        ClassIndex indexAfter = server.getTextDocumentService().getClassIndexForTest();
        assertThat(indexAfter).isNotNull();
        assertThat(indexAfter.getMatching("Baz")).contains("com.example.Baz");
    }

    // Verifies the cached JAR index is used on rebuild, not that JARs are
    // literally not re-read — the latter would require a spy on ClassIndex.build.
    @Test
    void rebuildPreservesJarClassesFromCachedIndex() throws Exception {
        DroolsLspServer server = TestHelperMethods.getDroolsLspServerForDocument("");

        Path classDir = createClassDir("com/example/Foo.class");

        Path jarPath = tempDir.resolve("dep.jar");
        try (java.util.jar.JarOutputStream jos = new java.util.jar.JarOutputStream(
                Files.newOutputStream(jarPath))) {
            jos.putNextEntry(new java.util.jar.JarEntry("com/acme/Order.class"));
            jos.closeEntry();
        }

        server.setClasspathEntriesForTest(Set.of(classDir, jarPath));
        server.rebuildClassIndex();

        ClassIndex index = server.getTextDocumentService().getClassIndexForTest();
        assertThat(index.getMatching("Foo")).contains("com.example.Foo");
        assertThat(index.getMatching("Or")).contains("com.acme.Order");

        // Delete the JAR — rebuild should still have JAR classes from cached index
        Files.delete(jarPath);
        server.rebuildClassIndex();

        ClassIndex indexAfter = server.getTextDocumentService().getClassIndexForTest();
        assertThat(indexAfter.getMatching("Foo")).contains("com.example.Foo");
        assertThat(indexAfter.getMatching("Or")).contains("com.acme.Order");
    }

    private Path createClassDir(String classFilePath) throws IOException {
        Path classFile = tempDir.resolve(classFilePath);
        Files.createDirectories(classFile.getParent());
        Files.createFile(classFile);
        return tempDir;
    }
}
