package org.drools.completion.fixtures;

/** Reflection fixture for ClassMemberIndex tests. */
public class Pet {

    static {
        InitProbe.petInitialized = true;
    }

    public int legs;
    private String name;
    private boolean friendly;

    public String getName() {
        return name;
    }

    public boolean isFriendly() {
        return friendly;
    }

    public String ignoredBecauseItTakesArgs(String arg) {
        return arg;
    }
}
