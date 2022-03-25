package org.drools.completion;

import org.antlr.v4.runtime.Token;
import org.drools.parser.DRLLexer;

import java.util.Set;

public class Tokens {

    public static Set<Integer> IGNORED = Set.of(
            Token.EPSILON, Token.EOF, Token.INVALID_TYPE,

            DRLLexer.TIME_INTERVAL, DRLLexer.HASH, DRLLexer.UNIFY, DRLLexer.NULL_SAFE_DOT, DRLLexer.QUESTION_DIV, DRLLexer.MISC,

            DRLLexer.DECIMAL_LITERAL, DRLLexer.HEX_LITERAL,
            DRLLexer.OCT_LITERAL, DRLLexer.BINARY_LITERAL, DRLLexer.FLOAT_LITERAL, DRLLexer.HEX_FLOAT_LITERAL,
            DRLLexer.BOOL_LITERAL, DRLLexer.CHAR_LITERAL, DRLLexer.STRING_LITERAL, DRLLexer.TEXT_BLOCK,
            DRLLexer.NULL_LITERAL, DRLLexer.LPAREN, DRLLexer.RPAREN, DRLLexer.LBRACE, DRLLexer.RBRACE, DRLLexer.LBRACK,
            DRLLexer.RBRACK, DRLLexer.SEMI, DRLLexer.COMMA, DRLLexer.DOT, DRLLexer.ASSIGN, DRLLexer.GT, DRLLexer.LT,
            DRLLexer.BANG, DRLLexer.TILDE, DRLLexer.QUESTION, DRLLexer.COLON, DRLLexer.EQUAL, DRLLexer.LE, DRLLexer.GE,
            DRLLexer.NOTEQUAL, DRLLexer.AND, DRLLexer.OR, DRLLexer.INC, DRLLexer.DEC, DRLLexer.ADD, DRLLexer.SUB, DRLLexer.MUL,
            DRLLexer.DIV, DRLLexer.BITAND, DRLLexer.BITOR, DRLLexer.CARET, DRLLexer.MOD, DRLLexer.ADD_ASSIGN, DRLLexer.SUB_ASSIGN,
            DRLLexer.MUL_ASSIGN, DRLLexer.DIV_ASSIGN, DRLLexer.AND_ASSIGN, DRLLexer.OR_ASSIGN, DRLLexer.XOR_ASSIGN,
            DRLLexer.MOD_ASSIGN, DRLLexer.LSHIFT_ASSIGN, DRLLexer.RSHIFT_ASSIGN, DRLLexer.URSHIFT_ASSIGN,
            DRLLexer.ARROW, DRLLexer.COLONCOLON, DRLLexer.AT, DRLLexer.ELLIPSIS, DRLLexer.WS, DRLLexer.COMMENT,
            DRLLexer.LINE_COMMENT, DRLLexer.IDENTIFIER, DRLLexer.TEXT
    );
}
