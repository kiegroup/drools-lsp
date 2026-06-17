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

    // 'is' prefix but non-boolean return — not a JavaBeans property.
    public String isNamedAfter() {
        return name;
    }

    public String ignoredBecauseItTakesArgs(String arg) {
        return arg;
    }
}
