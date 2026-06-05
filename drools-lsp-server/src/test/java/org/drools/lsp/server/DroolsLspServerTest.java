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
    void didChangeWatchedFilesTriggersRebuild() throws IOException {
        DroolsLspServer server = TestHelperMethods.getDroolsLspServerForDocument("");

        Path classDir = createClassDir("com/example/Bar.class");
        server.setClasspathEntriesForTest(Set.of(classDir));

        DroolsLspWorkspaceService workspaceService = (DroolsLspWorkspaceService) server.getWorkspaceService();
        workspaceService.didChangeWatchedFiles(new DidChangeWatchedFilesParams());

        ClassIndex index = server.getTextDocumentService().getClassIndexForTest();
        assertThat(index.getMatching("Bar")).contains("com.example.Bar");
    }

    private Path createClassDir(String classFilePath) throws IOException {
        Path classFile = tempDir.resolve(classFilePath);
        Files.createDirectories(classFile.getParent());
        Files.createFile(classFile);
        return tempDir;
    }
}
