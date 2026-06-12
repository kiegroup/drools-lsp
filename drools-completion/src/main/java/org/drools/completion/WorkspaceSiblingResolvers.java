package org.drools.completion;

import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Process-wide registry for the active {@link WorkspaceSiblingResolver}.
 * Defaults to directory grouping: all other {@code .drl} files in the same
 * directory as the current document, in stable (sorted) order.
 */
public final class WorkspaceSiblingResolvers {

    private static final WorkspaceSiblingResolver SAME_DIRECTORY =
            WorkspaceSiblingResolvers::sameDirectorySiblings;

    private static volatile WorkspaceSiblingResolver active = SAME_DIRECTORY;

    private WorkspaceSiblingResolvers() {
    }

    public static WorkspaceSiblingResolver active() {
        return active;
    }

    /**
     * Installs {@code resolver}, or restores the same-directory default when
     * {@code null}.
     */
    public static void setActive(WorkspaceSiblingResolver resolver) {
        active = (resolver == null) ? SAME_DIRECTORY : resolver;
    }

    private static List<Path> sameDirectorySiblings(Path currentFile) {
        if (currentFile == null) {
            return Collections.emptyList();
        }
        Path dir = currentFile.toAbsolutePath().getParent();
        if (dir == null || !Files.isDirectory(dir)) {
            return Collections.emptyList();
        }
        Path normalizedCurrent = currentFile.toAbsolutePath().normalize();
        List<Path> siblings = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.drl")) {
            for (Path candidate : stream) {
                if (!candidate.toAbsolutePath().normalize().equals(normalizedCurrent)) {
                    siblings.add(candidate);
                }
            }
        } catch (Exception e) {
            return Collections.emptyList();
        }
        siblings.sort(Path::compareTo);
        return siblings;
    }
}
