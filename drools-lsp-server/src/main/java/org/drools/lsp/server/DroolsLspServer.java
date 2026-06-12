package org.drools.lsp.server;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.drools.completion.ClassIndex;
import org.drools.completion.ClassMemberIndex;
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
    private volatile Set<Path> buildOutputDirs = Set.of();
    private volatile ClassIndex jarClassIndex = ClassIndex.empty();

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
        Set<Path> dirs = buildOutputDirs;
        if (dirs.isEmpty() && jarClassIndex.size() == 0) {
            return;
        }
        try {
            ClassIndex outputIndex = ClassIndex.build(dirs);
            textService.setClassIndex(ClassIndex.merge(jarClassIndex, outputIndex));
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to rebuild class index", e);
        }
    }

    private void setResolvedClasspath(Set<Path> entries) {
        this.classpathEntries = entries;
        this.buildOutputDirs = filterDirectories(entries);
        Set<Path> jars = filterJars(entries);
        this.jarClassIndex = jars.isEmpty() ? ClassIndex.empty() : ClassIndex.build(jars);
        // Member lookup reflects over the full classpath (jars + class dirs)
        // lazily — building the index itself loads no classes.
        textService.setClassMemberIndex(ClassMemberIndex.of(entries));
    }

    void setClasspathEntriesForTest(Set<Path> entries) {
        setResolvedClasspath(entries);
    }

    private static Set<Path> filterDirectories(Set<Path> entries) {
        Set<Path> dirs = new LinkedHashSet<>();
        for (Path entry : entries) {
            if (Files.isDirectory(entry)) {
                dirs.add(entry);
            }
        }
        return dirs;
    }

    private static Set<Path> filterJars(Set<Path> entries) {
        Set<Path> jars = new LinkedHashSet<>();
        for (Path entry : entries) {
            if (!Files.isDirectory(entry)) {
                jars.add(entry);
            }
        }
        return jars;
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
                try {
                    Path rootPath = Paths.get(URI.create(rootUri));
                    Set<Path> resolved = MavenClasspathResolver.resolve(rootPath);
                    setResolvedClasspath(resolved);
                    ClassIndex outputIndex = ClassIndex.build(buildOutputDirs);
                    textService.setClassIndex(ClassIndex.merge(jarClassIndex, outputIndex));
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Failed to build class index at startup", e);
                }
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
