package org.drools.completion;

import java.nio.file.Path;
import java.util.List;

/**
 * Resolves the set of DRL files that should be considered "siblings" of a
 * given document — files in the same logical group whose declared types are
 * in scope for completion and navigation.
 *
 * <p>The default resolver (see {@link WorkspaceSiblingResolvers}) groups by
 * directory: every other {@code .drl} file beside the current one. Hosts
 * with an explicit grouping model (build configuration, rule sets, …) can
 * install their own implementation via
 * {@link WorkspaceSiblingResolvers#setActive}.
 */
public interface WorkspaceSiblingResolver {

    /**
     * Returns the absolute paths of the files grouped with
     * {@code currentFile} (excluding the file itself), or an empty list when
     * no grouping applies.
     */
    List<Path> resolveSiblings(Path currentFile);
}
