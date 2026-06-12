package org.drools.completion.fixtures;

/** Records whether {@link Pet}'s static initializer ran. */
public final class InitProbe {

    public static volatile boolean petInitialized = false;

    private InitProbe() {
    }
}
