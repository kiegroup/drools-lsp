package org.drools.completion;

/** A field, bean property, or enum constant of a {@link DeclaredType} or Java class. */
public class Field {

    public final String name;
    public final String type;
    /**
     * For enum constants declared with constructor arguments
     * (e.g. {@code LOW(1)}), the raw argument list as written in source —
     * without the surrounding parentheses. {@code null} for ordinary fields
     * and for enum constants without arguments.
     */
    public final String args;

    Field(String name, String type) {
        this(name, type, null);
    }

    Field(String name, String type, String args) {
        this.name = name;
        this.type = type;
        this.args = args;
    }
}
