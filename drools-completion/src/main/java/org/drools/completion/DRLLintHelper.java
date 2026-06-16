package org.drools.completion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;

/**
 * Heuristic, parser-free lint passes for DRL source files, complementing the
 * ANTLR-based syntax diagnostics from {@link DRLDiagnosticHelper} with
 * friendlier messages anchored at the offending construct (the parser often
 * reports structural mistakes far from their cause, e.g. a missing
 * {@code end} surfaces as a confusing error at end-of-file).
 *
 * <p>Each pass is configurable through a system property, because every
 * heuristic has false-positive potential and teams differ on how loudly they
 * want to be warned:
 *
 * <pre>
 *   drools.lsp.lint.missingEnd         = off | hint | info | warning | error
 *   drools.lsp.lint.missingSeparator   = off | hint | info | warning | error
 *   drools.lsp.lint.missingSemicolon   = off | hint | info | warning | error
 *   drools.lsp.lint.unbalancedParens   = off | hint | info | warning | error
 *   drools.lsp.lint.mvelPropertyAccess = off | hint | info | warning | error
 * </pre>
 *
 * The structural passes default to {@code warning}. The MVEL property-access
 * pass is purely stylistic (both forms are valid DRL), so it defaults to
 * {@code off} and only runs for teams that opt in.
 *
 * <p>All methods are stateless and safe to call from multiple threads. The
 * passes operate on a sanitized copy of the text in which comments and
 * string-literal contents are blanked out (preserving line/column positions),
 * so quoted parentheses, {@code when}/{@code then} inside comments, and
 * trailing comments cannot confuse the heuristics.
 */
public final class DRLLintHelper {

    private static final String PROP_MISSING_END          = "drools.lsp.lint.missingEnd";
    private static final String PROP_MISSING_SEPARATOR    = "drools.lsp.lint.missingSeparator";
    private static final String PROP_MISSING_SEMICOLON    = "drools.lsp.lint.missingSemicolon";
    private static final String PROP_UNBALANCED_PARENS    = "drools.lsp.lint.unbalancedParens";
    private static final String PROP_MVEL_PROPERTY_ACCESS = "drools.lsp.lint.mvelPropertyAccess";

    private static final int MAX_DIAGNOSTICS_PER_PASS = 20;

    private static final Pattern RULE_KEYWORD =
            Pattern.compile("^\\s*(rule|query)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern DECLARE_KEYWORD =
            Pattern.compile("^\\s*declare\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern END_AT_START =
            Pattern.compile("^end\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern END_AT_END =
            Pattern.compile("(?:^|\\s)end$", Pattern.CASE_INSENSITIVE);
    private static final Pattern WHEN_KEYWORD =
            Pattern.compile("^\\s*when\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern THEN_KEYWORD =
            Pattern.compile("^\\s*then\\b", Pattern.CASE_INSENSITIVE);
    /** A bare pattern header opening its paren at end of line: {@code Person(}. */
    private static final Pattern PATTERN_HEADER_LINE = Pattern.compile(
            "^\\s*(?:(?:not|exists|forall)\\s+)?\\$?[A-Za-z_][\\w$]*\\s*\\(\\s*$",
            Pattern.CASE_INSENSITIVE);
    /** A line that starts like a field constraint: {@code name == ...}. */
    private static final Pattern CONSTRAINT_START = Pattern.compile(
            "^\\s*\\$?[A-Za-z_][\\w$]*(?:\\.[A-Za-z_][\\w$]*)*\\s*"
                    + "(?:[=!<>]|contains\\b|matches\\b|memberOf\\b|in\\b).*");

    private DRLLintHelper() {
    }

    /**
     * Runs all enabled lint passes over {@code text} and returns their
     * combined diagnostics. Returns an empty list for null/empty input or
     * when every pass is disabled.
     */
    public static List<Diagnostic> lint(String text) {
        if (text == null || text.isEmpty()) {
            return Collections.emptyList();
        }
        String sanitized = sanitize(text);
        List<Diagnostic> out = new ArrayList<>();

        DiagnosticSeverity endSeverity = severityFor(PROP_MISSING_END);
        if (endSeverity != null) {
            out.addAll(lintMissingRuleEnds(sanitized, endSeverity));
        }
        DiagnosticSeverity separatorSeverity = severityFor(PROP_MISSING_SEPARATOR);
        if (separatorSeverity != null) {
            out.addAll(lintMissingConstraintSeparators(sanitized, separatorSeverity));
        }
        DiagnosticSeverity semicolonSeverity = severityFor(PROP_MISSING_SEMICOLON);
        if (semicolonSeverity != null) {
            out.addAll(lintMissingThenSemicolons(sanitized, text, semicolonSeverity));
        }
        DiagnosticSeverity parenSeverity = severityFor(PROP_UNBALANCED_PARENS);
        if (parenSeverity != null) {
            out.addAll(lintUnbalancedParens(sanitized, parenSeverity));
        }
        DiagnosticSeverity mvelSeverity = severityFor(PROP_MVEL_PROPERTY_ACCESS, "off");
        if (mvelSeverity != null) {
            out.addAll(lintMvelPropertyAccess(sanitized, mvelSeverity));
        }
        return out;
    }

    // ── unbalanced parentheses in the LHS ────────────────────────────────

    /**
     * Reports unbalanced parentheses inside rule LHS (when-sections), rule
     * consequences (then-sections), and query bodies — the regions where a
     * missing {@code )} is both easy to type and hard to spot from the
     * parser's far-away recovery error. An unclosed {@code (} is anchored
     * at the paren itself; a stray {@code )} at its own position. Runs on
     * sanitized text, so parens in strings and comments don't count.
     */
    private static List<Diagnostic> lintUnbalancedParens(String sanitized,
                                                         DiagnosticSeverity severity) {
        String[] lines = sanitized.split("\r?\n", -1);
        List<Diagnostic> out = new ArrayList<>();
        List<int[]> openParens = new ArrayList<>(); // {line, col}
        boolean inLhs = false;
        boolean inThen = false;

        for (int i = 0; i < lines.length && out.size() < MAX_DIAGNOSTICS_PER_PASS; i++) {
            String raw = lines[i];
            String line = raw.trim();

            java.util.regex.Matcher ruleStart = RULE_KEYWORD.matcher(line);
            if (ruleStart.find()) {
                // A new rule/query while parens are still open: flush what the
                // previous section left unclosed, then track the new block
                // (query bodies are pattern regions from the start; rules from `when`).
                reportUnclosed(openParens, lines, out, severity);
                inLhs = "query".equalsIgnoreCase(ruleStart.group(1));
                inThen = false;
                continue;
            }
            if (WHEN_KEYWORD.matcher(line).find()) {
                inLhs = true;
                inThen = false;
                openParens.clear();
                continue;
            }
            if (inLhs && THEN_KEYWORD.matcher(line).find()) {
                reportUnclosed(openParens, lines, out, severity);
                inLhs = false;
                inThen = true;
                continue;
            }
            if (END_AT_START.matcher(line).find() && (inLhs || inThen)) {
                reportUnclosed(openParens, lines, out, severity);
                inLhs = false;
                inThen = false;
                continue;
            }
            if (!inLhs && !inThen) {
                continue;
            }

            for (int col = 0; col < raw.length(); col++) {
                char c = raw.charAt(col);
                if (c == '(') {
                    openParens.add(new int[] {i, col});
                } else if (c == ')') {
                    if (openParens.isEmpty()) {
                        Diagnostic d = new Diagnostic();
                        d.setSeverity(severity);
                        d.setSource("drools-lint");
                        d.setMessage("Unmatched ')' — no corresponding '('");
                        d.setRange(new Range(new Position(i, col),
                                             new Position(i, col + 1)));
                        out.add(d);
                        if (out.size() >= MAX_DIAGNOSTICS_PER_PASS) {
                            return out;
                        }
                    } else {
                        openParens.remove(openParens.size() - 1);
                    }
                }
            }
        }
        reportUnclosed(openParens, lines, out, severity);
        return out;
    }

    private static void reportUnclosed(List<int[]> openParens, String[] lines,
                                       List<Diagnostic> out, DiagnosticSeverity severity) {
        for (int[] open : openParens) {
            if (out.size() >= MAX_DIAGNOSTICS_PER_PASS) {
                break;
            }
            Diagnostic d = new Diagnostic();
            d.setSeverity(severity);
            d.setSource("drools-lint");
            d.setMessage("Unclosed '(' — missing matching ')'");
            d.setRange(new Range(new Position(open[0], open[1]),
                                 new Position(open[0], open[1] + 1)));
            out.add(d);
        }
        openParens.clear();
    }

    // ── MVEL property access in constraints ──────────────────────────────

    /**
     * A no-arg JavaBean accessor call with an instance receiver:
     * {@code .getCode()} / {@code .isActive()}. group(1) is the accessor
     * name without the leading dot.
     */
    private static final Pattern ACCESSOR_CALL = Pattern.compile(
            "\\.(get[A-Z][A-Za-z0-9_]*|is[A-Z][A-Za-z0-9_]*)\\(\\s*\\)");

    /**
     * Flags JavaBean getter calls in LHS constraints and suggests the MVEL
     * property-access form ({@code address.getCode()} → {@code address.code}),
     * which is the canonical Drools constraint style. Scoped to the WHEN
     * section only — the THEN consequence is real Java, where getter calls
     * are correct and the property sugar is unavailable. Skips
     * {@code getClass()} (its property form {@code .class} is a reserved
     * MVEL construct) and {@code Type.getX()} static calls.
     */
    private static List<Diagnostic> lintMvelPropertyAccess(String sanitized,
                                                           DiagnosticSeverity severity) {
        String[] lines = sanitized.split("\r?\n", -1);
        List<Diagnostic> out = new ArrayList<>();
        boolean inWhen = false;

        for (int i = 0; i < lines.length; i++) {
            String raw = lines[i];
            String line = raw.trim();

            if (RULE_KEYWORD.matcher(line).find()) {
                inWhen = false;
            }
            if (WHEN_KEYWORD.matcher(line).find()) {
                inWhen = true;
                continue;
            }
            if (inWhen && THEN_KEYWORD.matcher(line).find()) {
                inWhen = false;
                continue;
            }
            if (!inWhen) {
                continue;
            }

            java.util.regex.Matcher m = ACCESSOR_CALL.matcher(raw);
            while (m.find()) {
                String accessor = m.group(1);
                if ("getClass".equals(accessor)) {
                    continue;
                }
                if (receiverLooksLikeType(raw, m.start())) {
                    continue;
                }
                String property = decapitalize(
                        accessor.startsWith("get") ? accessor.substring(3) : accessor.substring(2));
                Diagnostic d = new Diagnostic();
                d.setSeverity(severity);
                d.setSource("drools-lint");
                d.setCode("mvel-property-access");
                d.setMessage("Prefer MVEL property access '" + property
                        + "' over '" + accessor + "()' in constraints");
                d.setRange(new Range(new Position(i, m.start(1)),
                                     new Position(i, m.end())));
                out.add(d);
                if (out.size() >= MAX_DIAGNOSTICS_PER_PASS) {
                    return out;
                }
            }
        }
        return out;
    }

    /**
     * Returns true when the identifier immediately before the dot at
     * {@code dotIndex} starts with an uppercase letter — a {@code Type.getX()}
     * static call rather than a bean property access on an instance.
     */
    private static boolean receiverLooksLikeType(String line, int dotIndex) {
        int end = dotIndex;
        int start = end;
        while (start > 0) {
            char c = line.charAt(start - 1);
            if (Character.isLetterOrDigit(c) || c == '_' || c == '$') {
                start--;
            } else {
                break;
            }
        }
        return start < end && Character.isUpperCase(line.charAt(start));
    }

    /**
     * JavaBeans {@code Introspector.decapitalize} rule, inlined to avoid a
     * {@code java.desktop} dependency: lowercase the first character unless
     * the first two are both uppercase ({@code URL} stays {@code URL}) —
     * matching how Drools resolves pattern properties back to accessors.
     */
    private static String decapitalize(String name) {
        if (name.isEmpty()) {
            return name;
        }
        if (name.length() > 1 && Character.isUpperCase(name.charAt(0))
                && Character.isUpperCase(name.charAt(1))) {
            return name;
        }
        char[] chars = name.toCharArray();
        chars[0] = Character.toLowerCase(chars[0]);
        return new String(chars);
    }

    // ── missing ';' in THEN ──────────────────────────────────────────────

    /** Matches a {@code dialect "mvel"} attribute (checked on original text). */
    private static final Pattern MVEL_DIALECT =
            Pattern.compile("(?i)\\bdialect\\s*\"mvel\"");
    private static final Pattern CONTROL_FLOW_START = Pattern.compile(
            "^(if|for|while|else|try|catch|finally|switch|do)\\b.*",
            Pattern.CASE_INSENSITIVE);

    /**
     * Reports Java statements in the {@code then} consequence that end
     * without a semicolon. Exemptions for the legal semicolon-free cases:
     * MVEL-dialect rules (file- or rule-level attribute), statement bodies
     * inside braces (e.g. {@code modify(...) { ... }}), fluent-chain
     * continuations (next line starts with {@code .}), multi-line call
     * arguments (open paren depth), and control-flow/brace lines.
     *
     * @param sanitized comment/string-blanked text the heuristics run on
     * @param original  unmodified text, needed for dialect detection (the
     *                  sanitizer blanks the {@code "mvel"} literal)
     */
    private static List<Diagnostic> lintMissingThenSemicolons(String sanitized,
                                                              String original,
                                                              DiagnosticSeverity severity) {
        String[] lines = sanitized.split("\r?\n", -1);
        String[] originalLines = original.split("\r?\n", -1);
        List<Diagnostic> out = new ArrayList<>();

        boolean inRuleHeader = false;
        boolean inThen = false;
        boolean fileMvel = false;
        boolean ruleMvel = false;
        int parenDepth = 0;
        int braceDepth = 0;

        for (int i = 0; i < lines.length; i++) {
            String raw = lines[i];
            String line = raw.trim();
            String originalLine = i < originalLines.length ? originalLines[i] : "";

            // dialect "mvel" before the first rule applies to the whole file.
            if (!inRuleHeader && !inThen && MVEL_DIALECT.matcher(originalLine).find()) {
                if (!containsRuleBefore(lines, i)) {
                    fileMvel = true;
                } else {
                    ruleMvel = true;
                }
            }

            if (RULE_KEYWORD.matcher(line).find()) {
                inRuleHeader = true;
                inThen = false;
                ruleMvel = false;
                parenDepth = 0;
                braceDepth = 0;
            }
            if (inRuleHeader && MVEL_DIALECT.matcher(originalLine).find()) {
                ruleMvel = true;
            }
            if (WHEN_KEYWORD.matcher(line).find()) {
                inRuleHeader = false;
            }
            if (THEN_KEYWORD.matcher(line).find()) {
                inRuleHeader = false;
                inThen = true;
                parenDepth = 0;
                braceDepth = 0;
                continue;
            }
            if (!inThen) {
                continue;
            }
            if (END_AT_START.matcher(line).find()) {
                inThen = false;
                continue;
            }
            if (line.isEmpty()) {
                continue;
            }
            if (fileMvel || ruleMvel) {
                continue;
            }

            int braceDelta = countChar(raw, '{') - countChar(raw, '}');
            boolean wasInsideBraces = braceDepth > 0;
            braceDepth = Math.max(0, braceDepth + braceDelta);
            parenDepth = Math.max(0, parenDepth + countChar(raw, '(') - countChar(raw, ')'));

            // Inside a braced body (modify blocks, if/for bodies) the
            // heuristic stands down — modify uses commas legally, and
            // distinguishing it from Java blocks isn't worth false positives.
            if (wasInsideBraces || braceDelta != 0
                    || line.endsWith("{") || line.endsWith("}")) {
                continue;
            }
            // A statement whose call parens are still open continues on the
            // next line.
            if (parenDepth > 0) {
                continue;
            }
            if (CONTROL_FLOW_START.matcher(line).matches()) {
                continue;
            }
            if (line.endsWith(";") || line.endsWith(",")) {
                continue;
            }
            // Fluent chain: the statement continues on a `.method(...)` line.
            String next = nextNonBlankLine(lines, i + 1);
            if (next != null && next.startsWith(".")) {
                continue;
            }

            String lower = line.toLowerCase();
            boolean looksLikeStatement = line.endsWith(")")
                    || lower.startsWith("insert") || lower.startsWith("update")
                    || lower.startsWith("retract") || lower.startsWith("delete")
                    || lower.startsWith("modify") || lower.startsWith("new ")
                    || line.contains("=");
            if (!looksLikeStatement) {
                continue;
            }

            int col = Math.max(0, lastNonWhitespaceCol(raw));
            Diagnostic d = new Diagnostic();
            d.setSeverity(severity);
            d.setSource("drools-lint");
            d.setMessage("Likely missing ';' at the end of this consequence statement");
            d.setRange(new Range(new Position(i, col),
                                 new Position(i, col + 1)));
            out.add(d);
            if (out.size() >= MAX_DIAGNOSTICS_PER_PASS) {
                break;
            }
        }
        return out;
    }

    private static boolean containsRuleBefore(String[] lines, int index) {
        for (int i = 0; i < index && i < lines.length; i++) {
            if (RULE_KEYWORD.matcher(lines[i].trim()).find()) {
                return true;
            }
        }
        return false;
    }

    // ── missing constraint separators ────────────────────────────────────

    /**
     * Reports adjacent constraint lines inside an open pattern paren where
     * the first line ends without a separator ({@code ,}, {@code &&},
     * {@code ||}, {@code and}, {@code or}) — a newline is not a constraint
     * separator in DRL. Operates on sanitized text, so parens inside string
     * literals and comment lines cannot corrupt the depth bookkeeping.
     */
    private static List<Diagnostic> lintMissingConstraintSeparators(String sanitized,
                                                                    DiagnosticSeverity severity) {
        String[] lines = sanitized.split("\r?\n", -1);
        List<Diagnostic> out = new ArrayList<>();

        boolean inWhen = false;
        int parenDepth = 0;

        for (int i = 0; i < lines.length; i++) {
            String raw = lines[i];
            String a = raw.trim();

            if (RULE_KEYWORD.matcher(a).find()) {
                inWhen = false;
                parenDepth = 0;
            }
            if (WHEN_KEYWORD.matcher(a).find()) {
                inWhen = true;
                parenDepth = 0;
                continue;
            }
            if (inWhen && THEN_KEYWORD.matcher(a).find()) {
                inWhen = false;
                parenDepth = 0;
                continue;
            }
            if (!inWhen) {
                continue;
            }

            // Depth bookkeeping happens for every line in the when-section —
            // skipping it for "uninteresting" lines is how comment lines used
            // to silence the pass for the rest of the rule.
            parenDepth += countChar(raw, '(') - countChar(raw, ')');

            if (a.isEmpty() || parenDepth <= 0) {
                continue;
            }
            if (PATTERN_HEADER_LINE.matcher(a).matches()) {
                continue;
            }
            if (!CONSTRAINT_START.matcher(a).matches()) {
                continue;
            }

            String lower = a.toLowerCase();
            boolean endsWithSeparator = a.endsWith(",") || a.endsWith("&&")
                    || a.endsWith("||") || a.endsWith("(")
                    || lower.endsWith(" and") || lower.endsWith(" or");
            if (endsWithSeparator) {
                continue;
            }

            String next = nextNonBlankLine(lines, i + 1);
            if (next == null || !CONSTRAINT_START.matcher(next).matches()) {
                continue;
            }

            int col = Math.max(0, lastNonWhitespaceCol(raw));
            Diagnostic d = new Diagnostic();
            d.setSeverity(severity);
            d.setSource("drools-lint");
            d.setMessage("Likely missing ',' between constraints (newline is not a separator)");
            d.setRange(new Range(new Position(i, col),
                                 new Position(i, col + 1)));
            out.add(d);
            if (out.size() >= MAX_DIAGNOSTICS_PER_PASS) {
                break;
            }
        }
        return out;
    }

    // ── missing 'end' ────────────────────────────────────────────────────

    /**
     * Reports {@code rule}/{@code query} blocks that are never closed with
     * {@code end}. {@code declare} blocks are tracked separately so their
     * {@code end} doesn't close a rule; a declare block left unclosed does
     * not suppress tracking of subsequent rules (a {@code rule} keyword
     * cannot legally appear inside {@code declare}, so it implicitly
     * terminates the tracking of one).
     */
    private static List<Diagnostic> lintMissingRuleEnds(String sanitized,
                                                        DiagnosticSeverity severity) {
        String[] lines = sanitized.split("\r?\n", -1);
        List<Diagnostic> out = new ArrayList<>();
        List<int[]> openRules = new ArrayList<>(); // {line, startCol}
        boolean inDeclare = false;

        for (int i = 0; i < lines.length; i++) {
            String raw = lines[i];
            String trimmed = raw.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            if (RULE_KEYWORD.matcher(trimmed).find()) {
                inDeclare = false;
                openRules.add(new int[] {i, firstNonWhitespaceCol(raw)});
            } else if (DECLARE_KEYWORD.matcher(trimmed).find()) {
                inDeclare = true;
            }

            boolean closes = END_AT_START.matcher(trimmed).find()
                    || END_AT_END.matcher(trimmed).find();
            if (closes) {
                if (inDeclare) {
                    inDeclare = false;
                } else if (!openRules.isEmpty()) {
                    openRules.remove(openRules.size() - 1);
                }
            }
        }

        for (int[] rs : openRules) {
            String raw = lines[rs[0]];
            int startCol = rs[1];
            int keywordLen = raw.trim().toLowerCase().startsWith("query") ? 5 : 4;
            int endCol = Math.min(raw.length(), startCol + keywordLen);
            if (endCol <= startCol) {
                endCol = Math.min(raw.length(), startCol + 1);
            }
            Diagnostic d = new Diagnostic();
            d.setSeverity(severity);
            d.setSource("drools-lint");
            d.setRange(new Range(new Position(rs[0], startCol),
                                 new Position(rs[0], endCol)));
            d.setMessage("Missing 'end' for rule/query starting at line " + (rs[0] + 1));
            out.add(d);
            if (out.size() >= MAX_DIAGNOSTICS_PER_PASS) {
                break;
            }
        }
        return out;
    }

    // ── shared helpers ───────────────────────────────────────────────────

    private static DiagnosticSeverity severityFor(String property) {
        return severityFor(property, "warning");
    }

    /**
     * Resolves the severity for a pass from its system property: {@code off}
     * returns null (pass disabled), unknown values fall back to
     * {@code defaultValue}.
     */
    private static DiagnosticSeverity severityFor(String property, String defaultValue) {
        String value = System.getProperty(property, defaultValue)
                .trim().toLowerCase();
        switch (value) {
            case "off":
                return null;
            case "hint":
                return DiagnosticSeverity.Hint;
            case "info":
                return DiagnosticSeverity.Information;
            case "error":
                return DiagnosticSeverity.Error;
            case "warning":
            default:
                return DiagnosticSeverity.Warning;
        }
    }

    /**
     * Returns a copy of {@code text} in which line comments ({@code //},
     * {@code #}), block comments and the contents of string literals are
     * replaced by spaces. Newlines are preserved (CR normalised to a space
     * within lines is avoided by keeping CR as-is only when followed by LF),
     * so every remaining character keeps its original line and column.
     */
    static String sanitize(String text) {
        char[] chars = text.toCharArray();
        boolean inString = false;
        boolean inLineComment = false;
        boolean inBlockComment = false;

        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];
            if (c == '\n' || c == '\r') {
                inLineComment = false;
                inString = false; // DRL string literals do not span lines
                continue;
            }
            if (inLineComment) {
                chars[i] = ' ';
                continue;
            }
            if (inBlockComment) {
                if (c == '*' && i + 1 < chars.length && chars[i + 1] == '/') {
                    chars[i] = ' ';
                    chars[i + 1] = ' ';
                    i++;
                    inBlockComment = false;
                } else {
                    chars[i] = ' ';
                }
                continue;
            }
            if (inString) {
                if (c == '\\' && i + 1 < chars.length) {
                    chars[i] = ' ';
                    chars[i + 1] = ' ';
                    i++;
                } else if (c == '"') {
                    inString = false;
                } else {
                    chars[i] = ' ';
                }
                continue;
            }
            if (c == '"') {
                inString = true;
            } else if (c == '/' && i + 1 < chars.length && chars[i + 1] == '/') {
                chars[i] = ' ';
                chars[i + 1] = ' ';
                i++;
                inLineComment = true;
            } else if (c == '#') {
                chars[i] = ' ';
                inLineComment = true;
            } else if (c == '/' && i + 1 < chars.length && chars[i + 1] == '*') {
                chars[i] = ' ';
                chars[i + 1] = ' ';
                i++;
                inBlockComment = true;
            }
        }
        return new String(chars);
    }

    private static int firstNonWhitespaceCol(String line) {
        for (int i = 0; i < line.length(); i++) {
            if (!Character.isWhitespace(line.charAt(i))) {
                return i;
            }
        }
        return 0;
    }

    private static int lastNonWhitespaceCol(String line) {
        for (int i = line.length() - 1; i >= 0; i--) {
            if (!Character.isWhitespace(line.charAt(i))) {
                return i;
            }
        }
        return 0;
    }

    private static String nextNonBlankLine(String[] lines, int from) {
        for (int i = from; i < lines.length; i++) {
            String trimmed = lines[i].trim();
            if (!trimmed.isEmpty()) {
                return trimmed;
            }
        }
        return null;
    }

    private static int countChar(String s, char c) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == c) {
                n++;
            }
        }
        return n;
    }
}
