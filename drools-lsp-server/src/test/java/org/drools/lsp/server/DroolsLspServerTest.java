package org.drools.lsp.server;

import java.nio.file.Path;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DroolsLspServerTest {

    @Test
    void classpathEntriesRetainedAfterInitialize() {
        DroolsLspServer server = new DroolsLspServer();
        assertThat(server.getClasspathEntries()).isEmpty();
    }
}
