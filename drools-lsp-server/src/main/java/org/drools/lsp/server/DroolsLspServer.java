package org.drools.lsp.server;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.drools.completion.ClassIndex;
import org.eclipse.lsp4j.CompletionOptions;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.TextDocumentSyncKind;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageClientAware;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.lsp4j.services.WorkspaceService;

public class DroolsLspServer implements LanguageServer, LanguageClientAware {

    private static final Logger logger = Logger.getLogger(DroolsLspServer.class.getName());

    private final DroolsLspDocumentService textService;
    private final WorkspaceService workspaceService;

    private LanguageClient client;
    private volatile Set<Path> classpathEntries = Set.of();

    public DroolsLspServer() {
        textService = new DroolsLspDocumentService(this);
        workspaceService = new DroolsLspWorkspaceService(this);
    }


    @Override
    public void connect(LanguageClient client) {
        this.client = client;
    }

    public LanguageClient getClient() {
        return client;
    }

    public Set<Path> getClasspathEntries() {
        return classpathEntries;
    }

    public void rebuildClassIndex() {
        Set<Path> entries = classpathEntries;
        if (entries.isEmpty()) {
            return;
        }
        try {
            ClassIndex classIndex = ClassIndex.build(entries);
            textService.setClassIndex(classIndex);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to rebuild class index", e);
        }
    }

    void setClasspathEntriesForTest(Set<Path> entries) {
        this.classpathEntries = entries;
    }

    @Override
    public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
        final InitializeResult initializeResult = new InitializeResult(new ServerCapabilities());

        initializeResult.getCapabilities().setTextDocumentSync(TextDocumentSyncKind.Full);
        CompletionOptions completionOptions = new CompletionOptions();
        initializeResult.getCapabilities().setCompletionProvider(completionOptions);

        String rootUri = params.getRootUri();
        if (rootUri != null) {
            CompletableFuture.runAsync(() -> {
                Path rootPath = Paths.get(URI.create(rootUri));
                Set<Path> resolved = MavenClasspathResolver.resolve(rootPath);
                classpathEntries = resolved;
                ClassIndex classIndex = ClassIndex.build(resolved);
                textService.setClassIndex(classIndex);
            });
        }

        return CompletableFuture.supplyAsync(() -> initializeResult);
    }

    @Override
    public CompletableFuture<Object> shutdown() {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void exit() {
        System.exit(0);
    }

    @Override
    public DroolsLspDocumentService getTextDocumentService() {
        return textService;
    }

    @Override
    public WorkspaceService getWorkspaceService() {
        return workspaceService;
    }
}
