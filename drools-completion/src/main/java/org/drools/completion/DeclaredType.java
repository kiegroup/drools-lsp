package org.drools.completion;

import java.util.List;

/** A type declared in DRL via a {@code declare} block. */
public class DeclaredType {

    public final String name;
    public final List<Field> fields;
    public final boolean isEnum;
    /** 0-based line of the type name token (for future go-to-definition). */
    public final int nameLine;
    /** 0-based column of the type name token (for future go-to-definition). */
    public final int nameCol;
    /**
     * Simple name of the parent type from {@code extends}, or {@code null}
     * when no inheritance is declared.
     */
    public final String extendsName;

    DeclaredType(String name, List<Field> fields, boolean isEnum,
                 int nameLine, int nameCol) {
        this(name, fields, isEnum, nameLine, nameCol, null);
    }

    DeclaredType(String name, List<Field> fields, boolean isEnum,
                 int nameLine, int nameCol, String extendsName) {
        this.name = name;
        this.fields = fields;
        this.isEnum = isEnum;
        this.nameLine = nameLine;
        this.nameCol = nameCol;
        this.extendsName = extendsName;
    }
}
