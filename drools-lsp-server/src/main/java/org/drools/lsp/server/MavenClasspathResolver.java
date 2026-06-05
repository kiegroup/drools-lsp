package org.drools.lsp.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
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

    private static Set<Path> resolveModule(Path moduleDir) throws IOException, InterruptedException {
        Set<Path> entries = new LinkedHashSet<>();

        Path cpFile = Files.createTempFile("drools-lsp-cp-", ".txt");
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

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                logger.warning("mvn dependency:build-classpath failed for " + moduleDir + " with exit code " + exitCode);
                return entries;
            }

            String cpContent = Files.readString(cpFile).trim();
            if (!cpContent.isEmpty()) {
                String separator = System.getProperty("os.name").toLowerCase().contains("win") ? ";" : ":";
                Arrays.stream(cpContent.split(separator))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(Path::of)
                    .forEach(entries::add);
            }
        } finally {
            Files.deleteIfExists(cpFile);
        }

        Path targetClasses = moduleDir.resolve("target/classes");
        if (Files.isDirectory(targetClasses)) {
            entries.add(targetClasses);
        }

        return entries;
    }
}
