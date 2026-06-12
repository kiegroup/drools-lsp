package org.drools.completion;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.drools.drl.parser.antlr4.DRL10Parser;
import org.drools.drl.parser.antlr4.DRLParserHelper;

/**
 * Extracts {@link DeclaredType}s ({@code declare} blocks, including declared
 * enums) from DRL source using the ANTLR parser, so completion can offer the
 * fields of types that exist only in DRL and never on the compiled classpath.
 */
public final class DRLDeclaredTypeParser {

    private static final Logger logger =
            Logger.getLogger(DRLDeclaredTypeParser.class.getName());

    /** Swallows ANTLR parse errors — partial/incomplete DRL is normal here. */
    private static final BaseErrorListener SILENT = new BaseErrorListener() {
        @Override
        public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol,
                                int line, int charPositionInLine, String msg,
                                RecognitionException e) {
        }
    };

    private DRLDeclaredTypeParser() {
    }

    /**
     * Parses all {@code declare} blocks in {@code text}. Parser errors are
     * ignored so partial files still yield partial results.
     */
    public static List<DeclaredType> parseDeclaredTypes(String text) {
        List<DeclaredType> types = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return types;
        }
        try {
            DRL10Parser parser = DRLParserHelper.createDrlParser(text);
            Lexer lexer = (Lexer) parser.getTokenStream().getTokenSource();
            lexer.removeErrorListeners();
            lexer.addErrorListener(SILENT);
            parser.removeErrorListeners();
            parser.addErrorListener(SILENT);
            return extractFromCompilationUnit(parser.compilationUnit());
        } catch (Exception e) {
            logger.fine(() -> "Failed to parse DRL for declared types: " + e.getMessage());
        }
        return types;
    }

    /**
     * Extracts declared types from an already-parsed compilation unit. Use
     * this overload when the caller already produced the parse tree (e.g.
     * during completion) to avoid a redundant second parse.
     */
    static List<DeclaredType> extractFromCompilationUnit(DRL10Parser.CompilationUnitContext cu) {
        List<DeclaredType> types = new ArrayList<>();
        if (cu == null) {
            return types;
        }
        for (DRL10Parser.DrlStatementdefContext stmt : cu.drlStatementdef()) {
            DRL10Parser.DeclaredefContext decl = stmt.declaredef();
            if (decl == null) {
                continue;
            }
            try {
                if (decl.typeDeclaration() != null) {
                    DeclaredType dt = extractTypeDeclaration(decl.typeDeclaration());
                    if (dt != null) {
                        types.add(dt);
                    }
                } else if (decl.enumDeclaration() != null) {
                    DeclaredType dt = extractEnumDeclaration(decl.enumDeclaration());
                    if (dt != null) {
                        types.add(dt);
                    }
                }
                // entryPointDeclaration and windowDeclaration are not class types.
            } catch (Exception e) {
                logger.fine(() -> "Skipping malformed declare block: " + e.getMessage());
            }
        }
        return types;
    }

    private static DeclaredType extractTypeDeclaration(DRL10Parser.TypeDeclarationContext ctx) {
        if (ctx == null || ctx.name == null) {
            return null;
        }
        String name = ctx.name.getText();
        int nameLine = ctx.name.getStart() != null ? ctx.name.getStart().getLine() - 1 : 0;
        int nameCol = ctx.name.getStart() != null ? ctx.name.getStart().getCharPositionInLine() : 0;
        List<Field> fields = extractFields(ctx.field());
        String extendsName = null;
        if (ctx.superTypes != null && !ctx.superTypes.isEmpty()) {
            String raw = ctx.superTypes.get(0).getText();
            int dot = raw == null ? -1 : raw.lastIndexOf('.');
            extendsName = (raw != null && dot >= 0) ? raw.substring(dot + 1) : raw;
        }
        return new DeclaredType(name, fields, false, nameLine, nameCol, extendsName);
    }

    private static DeclaredType extractEnumDeclaration(DRL10Parser.EnumDeclarationContext ctx) {
        if (ctx == null || ctx.name == null) {
            return null;
        }
        String name = ctx.name.getText();
        int nameLine = ctx.name.getStart() != null ? ctx.name.getStart().getLine() - 1 : 0;
        int nameCol = ctx.name.getStart() != null ? ctx.name.getStart().getCharPositionInLine() : 0;
        List<Field> fields = new ArrayList<>();
        // Enum constants carry the enum type name as their type.
        if (ctx.enumeratives() != null) {
            for (DRL10Parser.EnumerativeContext enumerative : ctx.enumeratives().enumerative()) {
                if (enumerative.drlIdentifier() != null) {
                    fields.add(new Field(enumerative.drlIdentifier().getText(), name,
                                         extractEnumArgs(enumerative)));
                }
            }
        }
        fields.addAll(extractFields(ctx.field()));
        return new DeclaredType(name, fields, true, nameLine, nameCol);
    }

    /**
     * Returns the comma-separated constructor arguments of an enum constant
     * ({@code LOW(1, "x")} → {@code 1, "x"}), or {@code null} when the
     * constant has no argument list.
     */
    private static String extractEnumArgs(DRL10Parser.EnumerativeContext ctx) {
        if (ctx == null || ctx.expression() == null || ctx.expression().isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ctx.expression().size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(ctx.expression(i).getText());
        }
        return sb.toString();
    }

    private static List<Field> extractFields(List<DRL10Parser.FieldContext> fieldCtxs) {
        List<Field> fields = new ArrayList<>();
        if (fieldCtxs == null) {
            return fields;
        }
        for (DRL10Parser.FieldContext field : fieldCtxs) {
            try {
                if (field.label() == null || field.type() == null) {
                    continue;
                }
                // label().getText() returns "name:" — strip the trailing colon.
                String rawLabel = field.label().getText();
                String fieldName = rawLabel.endsWith(":")
                        ? rawLabel.substring(0, rawLabel.length() - 1).trim()
                        : rawLabel.trim();
                String fieldType = field.type().getText();
                if (!fieldName.isEmpty() && !fieldType.isEmpty()) {
                    fields.add(new Field(fieldName, fieldType));
                }
            } catch (Exception e) {
                logger.fine(() -> "Skipping malformed field: " + e.getMessage());
            }
        }
        return fields;
    }
}
