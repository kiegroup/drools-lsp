package org.drools.completion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.Token;
import org.drools.drl.parser.antlr4.DRL10Parser;
import org.drools.drl.parser.antlr4.DRLParserHelper;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;

/**
 * Validates DRL text with the ANTLR parser and converts lexer and parser
 * errors into LSP {@link Diagnostic}s, so editors can surface syntax
 * problems as the user types.
 */
public final class DRLDiagnosticHelper {

    private DRLDiagnosticHelper() {
    }

    /**
     * Parses {@code text} and returns one {@link Diagnostic} per syntax
     * error, in document order. Returns an empty list for {@code null},
     * empty, or syntactically clean input.
     */
    public static List<Diagnostic> validate(String text) {
        if (text == null || text.isEmpty()) {
            return Collections.emptyList();
        }
        DRL10Parser parser = DRLParserHelper.createDrlParser(text);
        List<Diagnostic> diagnostics = new ArrayList<>();
        CollectingErrorListener listener = new CollectingErrorListener(diagnostics);

        // Safe today because DRLParserHelper builds an unfilled
        // CommonTokenStream directly over the lexer; lexer errors emitted
        // during lazy tokenization therefore reach the listener attached
        // below. If the helper ever pre-fills the stream, lexer errors would
        // be lost — attach the listener before tokenization in that case.
        Lexer lexer = (Lexer) parser.getTokenStream().getTokenSource();
        lexer.removeErrorListeners();
        lexer.addErrorListener(listener);

        parser.removeErrorListeners();
        parser.addErrorListener(listener);

        parser.compilationUnit();
        return diagnostics;
    }

    private static class CollectingErrorListener extends BaseErrorListener {

        private final List<Diagnostic> diagnostics;

        CollectingErrorListener(List<Diagnostic> diagnostics) {
            this.diagnostics = diagnostics;
        }

        @Override
        public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol,
                                int line, int charPositionInLine, String msg,
                                RecognitionException e) {
            int startCol = charPositionInLine;
            int endCol = charPositionInLine + 1;
            if (offendingSymbol instanceof Token) {
                Token token = (Token) offendingSymbol;
                if (token.getType() == Token.EOF) {
                    // EOF has no extent and its position is one past the last
                    // char; getText() is the 5-char placeholder "<EOF>".
                    // Anchor the range on the last real character (zero-width
                    // at column 0) so it never points past the text.
                    startCol = Math.max(0, charPositionInLine - 1);
                    endCol = charPositionInLine;
                } else {
                    // Widen the range to the offending token, but only when
                    // its text is usable: getText() may be null for detached
                    // tokens, and a token spanning lines would yield an end
                    // column computed on the wrong line — those fall back to
                    // the 1-char caret.
                    String tokenText = token.getText();
                    if (tokenText != null && !tokenText.isEmpty()
                            && tokenText.indexOf('\n') < 0 && tokenText.indexOf('\r') < 0) {
                        endCol = charPositionInLine + tokenText.length();
                    }
                }
            }
            Diagnostic d = new Diagnostic();
            d.setRange(new Range(new Position(line - 1, startCol),
                                 new Position(line - 1, endCol)));
            d.setSeverity(DiagnosticSeverity.Error);
            d.setSource("drools-parser");
            d.setMessage(msg);
            diagnostics.add(d);
        }
    }
}
