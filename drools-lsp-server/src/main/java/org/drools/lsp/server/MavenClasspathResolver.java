package org.drools.lsp.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MavenClasspathResolver {

    private static final Logger logger = Logger.getLogger(MavenClasspathResolver.class.getName());

    public static Set<Path> resolve(Path rootDir) {
        Set<Path> classpathEntries = new LinkedHashSet<>();
        List<Path> pomFiles = findPomFiles(rootDir);

        for (Path pom : pomFiles) {
            Path moduleDir = pom.getParent();
            try {
                Set<Path> moduleCp = resolveModule(moduleDir);
                classpathEntries.addAll(moduleCp);
            } catch (Exception e) {
                logger.log(Level.WARNING, "Failed to resolve classpath for " + pom + ": " + e.getMessage());
            }
        }

        return classpathEntries;
    }

    /**
     * Returns only the modules' compiled-output directories (target/classes and
     * the like), located via filesystem conventions without invoking {@code mvn}.
     *
     * <p>This is the fast half of {@link #resolve(Path)}: it lets the server index
     * the project's own classes immediately, before the slower dependency-JAR
     * resolution (which shells out to {@code mvn}) has completed.
     */
    public static Set<Path> resolveBuildOutputDirs(Path rootDir) {
        Set<Path> dirs = new LinkedHashSet<>();
        for (Path pom : findPomFiles(rootDir)) {
            dirs.addAll(BuildOutputLocator.findClassDirs(pom.getParent()));
        }
        return dirs;
    }

    static List<Path> findPomFiles(Path rootDir) {
        List<Path> result = new ArrayList<>();
        try {
            Files.walkFileTree(rootDir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    String dirName = dir.getFileName().toString();
                    if (dirName.equals("target") || dirName.startsWith(".")) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (file.getFileName().toString().equals("pom.xml")) {
                        result.add(file);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to walk directory tree: " + rootDir, e);
        }
        return result;
    }

    private static Set<Path> resolveModule(Path moduleDir) {
        Set<Path> entries = new LinkedHashSet<>();

        // The module's own compiled output -- located via filesystem conventions,
        // so this works even when mvn is unavailable and copes with non-standard
        // build layouts.
        entries.addAll(BuildOutputLocator.findClassDirs(moduleDir));

        // Dependency JARs -- best-effort via mvn; skipped gracefully when mvn is
        // absent, offline, or otherwise fails.
        resolveDependencyClasspath(moduleDir, entries);

        return entries;
    }

    private static void resolveDependencyClasspath(Path moduleDir, Set<Path> entries) {
        Path cpFile;
        try {
            cpFile = Files.createTempFile("drools-lsp-cp-", ".txt");
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to create temp file for classpath resolution", e);
            return;
        }
        try {
            String mvnCommand = System.getProperty("os.name").toLowerCase().contains("win") ? "mvn.cmd" : "mvn";
            ProcessBuilder pb = new ProcessBuilder(
                mvnCommand, "-f", moduleDir.resolve("pom.xml").toString(),
                "dependency:build-classpath",
                "-Dmdep.outputFile=" + cpFile.toAbsolutePath()
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                while (reader.readLine() != null) {
                    // drain
                }
            }

            boolean finished = process.waitFor(60, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                logger.warning("mvn dependency:build-classpath timed out for " + moduleDir);
                return;
            }
            if (process.exitValue() != 0) {
                logger.warning("mvn dependency:build-classpath failed for " + moduleDir + " with exit code " + process.exitValue());
                return;
            }

            String cpContent = Files.readString(cpFile).trim();
            if (!cpContent.isEmpty()) {
                Arrays.stream(cpContent.split(File.pathSeparator))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(Path::of)
                    .forEach(entries::add);
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to resolve dependency classpath for " + moduleDir + ": " + e.getMessage());
        } finally {
            try { Files.deleteIfExists(cpFile); } catch (IOException ignored) {}
        }
    }
}
