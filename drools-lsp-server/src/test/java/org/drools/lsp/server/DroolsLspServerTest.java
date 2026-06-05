package org.drools.lsp.server;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import org.drools.completion.ClassIndex;
import org.eclipse.lsp4j.DidChangeWatchedFilesParams;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DroolsLspServerTest {

    @Test
    void classpathEntriesRetainedAfterInitialize() {
        DroolsLspServer server = new DroolsLspServer();
        assertThat(server.getClasspathEntries()).isEmpty();
    }

    @Test
    void rebuildClassIndexUpdatesDocumentService() {
        DroolsLspServer server = TestHelperMethods.getDroolsLspServerForDocument("");

        Path tempDir = createTempClassDir("com/example/Foo.class");
        server.setClasspathEntriesForTest(Set.of(tempDir));

        server.rebuildClassIndex();

        ClassIndex index = server.getTextDocumentService().getClassIndexForTest();
        assertThat(index.getMatching("Foo")).contains("com.example.Foo");
    }

    @Test
    void didChangeWatchedFilesTriggersRebuild() {
        DroolsLspServer server = TestHelperMethods.getDroolsLspServerForDocument("");

        Path tempDir = createTempClassDir("com/example/Bar.class");
        server.setClasspathEntriesForTest(Set.of(tempDir));

        DroolsLspWorkspaceService workspaceService = (DroolsLspWorkspaceService) server.getWorkspaceService();
        workspaceService.didChangeWatchedFiles(new DidChangeWatchedFilesParams());

        ClassIndex index = server.getTextDocumentService().getClassIndexForTest();
        assertThat(index.getMatching("Bar")).contains("com.example.Bar");
    }

    private Path createTempClassDir(String classFilePath) {
        try {
            Path tempDir = Files.createTempDirectory("classindex-test");
            Path classFile = tempDir.resolve(classFilePath);
            Files.createDirectories(classFile.getParent());
            Files.createFile(classFile);
            return tempDir;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
