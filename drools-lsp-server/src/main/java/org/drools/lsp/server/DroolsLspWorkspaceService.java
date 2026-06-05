package org.drools.lsp.server;

import org.eclipse.lsp4j.DidChangeConfigurationParams;
import org.eclipse.lsp4j.DidChangeWatchedFilesParams;
import org.eclipse.lsp4j.services.WorkspaceService;

public class DroolsLspWorkspaceService implements WorkspaceService {

    private final DroolsLspServer server;

    DroolsLspWorkspaceService(DroolsLspServer server) {
        this.server = server;
    }

    @Override
    public void didChangeConfiguration(DidChangeConfigurationParams params) {
    }

    @Override
    public void didChangeWatchedFiles(DidChangeWatchedFilesParams params) {
        server.rebuildClassIndex();
    }
}
