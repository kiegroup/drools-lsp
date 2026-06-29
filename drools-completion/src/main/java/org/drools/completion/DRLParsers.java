package org.drools.completion;

import java.util.concurrent.atomic.AtomicLong;

import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.drools.drl.parser.antlr4.DRL10Parser;
import org.drools.drl.parser.antlr4.DRLParserHelper;

/**
 * Shared ANTLR DRL parser construction for editor features.
 *
 * <p>Editor documents are routinely partial or mid-edit, so syntax errors are
 * the norm rather than the exception. {@link #silent(String)} builds a parser
 * whose lexer and parser error listeners are both replaced with a no-op, so
 * feature code (hover, completion, go-to-definition, outline, declared-type
 * extraction) can parse best-effort without the default listeners printing to
 * stderr. Features that need to surface parse errors — diagnostics — attach
 * their own collecting listener instead and don't use this.
 */
final class DRLParsers {

    /** A parse-error listener that discards every error. */
    private static final BaseErrorListener SILENT = new BaseErrorListener() {
        @Override
        public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol,
                                int line, int charPositionInLine, String msg,
                                RecognitionException e) {
        }
    };

    /**
     * Counts parser constructions. Lets tests (and ad-hoc profiling) assert that
     * a single editor operation parses the current document once rather than
     * re-parsing it in each helper.
     */
    private static final AtomicLong PARSE_COUNT = new AtomicLong();

    private DRLParsers() {
    }

    /**
     * Creates a DRL parser over {@code text} with the lexer's and parser's
     * error listeners silenced. Callers typically follow with
     * {@code parser.compilationUnit()}.
     */
    static DRL10Parser silent(String text) {
        PARSE_COUNT.incrementAndGet();
        DRL10Parser parser = DRLParserHelper.createDrlParser(text);
        Lexer lexer = (Lexer) parser.getTokenStream().getTokenSource();
        lexer.removeErrorListeners();
        lexer.addErrorListener(SILENT);
        parser.removeErrorListeners();
        parser.addErrorListener(SILENT);
        return parser;
    }

    /** Number of parsers built since the last {@link #resetParseCount()}. */
    static long parseCount() {
        return PARSE_COUNT.get();
    }

    /** Resets the parser-construction counter; call before a measured operation. */
    static void resetParseCount() {
        PARSE_COUNT.set(0);
    }
}
