/*
 * Copyright 2022 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.drools.completion;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.vmware.antlr4c3.CodeCompletionCore;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.drools.drl.parser.antlr4.DRL10Lexer;
import org.drools.drl.parser.antlr4.DRL10Parser;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.services.LanguageClient;

import static org.drools.drl.parser.antlr4.DRLParserHelper.computeTokenIndex;
import static org.drools.drl.parser.antlr4.DRLParserHelper.createDrlParser;

public class DRLCompletionHelper {

    // PREFERRED_RULES is used to filter out the rules that consist of unwanted tokens
    // additionally, it can be used to customize getCompletionItems behavior
    private static final Set<Integer> PREFERRED_RULES = Set.of(
            DRL10Parser.RULE_drlIdentifier,
            DRL10Parser.RULE_drlQualifiedName,
            DRL10Parser.RULE_stringId,
            DRL10Parser.RULE_consequenceBody
    );

    private DRLCompletionHelper() {
    }

    public static List<CompletionItem> getCompletionItems(String text, Position caretPosition, LanguageClient client) {
        return getCompletionItems(text, caretPosition, client, ClassIndex.empty());
    }

    public static List<CompletionItem> getCompletionItems(String text, Position caretPosition, LanguageClient client, ClassIndex classIndex) {
        return getCompletionItems(text, caretPosition, client, classIndex, ClassMemberIndex.empty());
    }

    public static List<CompletionItem> getCompletionItems(String text, Position caretPosition, LanguageClient client, ClassIndex classIndex, ClassMemberIndex memberIndex) {
        return getCompletionItems(text, caretPosition, client, classIndex, memberIndex, null);
    }

    /**
     * @param documentPath filesystem location of the document, used to find
     *                     sibling DRL files; {@code null} for non-file
     *                     documents (sibling declares are then unavailable)
     */
    public static List<CompletionItem> getCompletionItems(String text, Position caretPosition, LanguageClient client, ClassIndex classIndex, ClassMemberIndex memberIndex, Path documentPath) {
        DRL10Parser drlParser = createDrlParser(text);

        int row = caretPosition == null ? -1 : caretPosition.getLine() + 1; // caret line position is zero based
        int col = caretPosition == null ? -1 : caretPosition.getCharacter();

        DRL10Parser.CompilationUnitContext compilationUnit = drlParser.compilationUnit();
        Integer nodeIndex = computeTokenIndex(drlParser, row, col);
        String prefix = extractPrefix(drlParser, nodeIndex);

        return getCompletionItems(drlParser, nodeIndex, compilationUnit, classIndex, prefix, memberIndex, documentPath);
    }

    static List<CompletionItem> getCompletionItems(DRL10Parser drlParser, int nodeIndex) {
        return getCompletionItems(drlParser, nodeIndex, null, ClassIndex.empty(), "", ClassMemberIndex.empty(), null);
    }

    static List<CompletionItem> getCompletionItems(DRL10Parser drlParser, int nodeIndex, DRL10Parser.CompilationUnitContext compilationUnit, ClassIndex classIndex, String prefix, ClassMemberIndex memberIndex, Path documentPath) {
        CodeCompletionCore core = new CodeCompletionCore(drlParser, PREFERRED_RULES, Tokens.IGNORED);
        CodeCompletionCore.CandidatesCollection candidates = core.collectCandidates(nodeIndex, null);

        if (candidates.rules.containsKey(DRL10Parser.RULE_consequenceBody)) {
            // in RHS consequence, parser cannot suggest DRL_RHS_END because of island mode approach, so we add it manually
            candidates.tokens.put(DRL10Lexer.DRL_RHS_END, List.of());
        }

        List<CompletionItem> items = candidates.tokens.keySet().stream().filter(Objects::nonNull)
                .map(integer -> drlParser.getVocabulary().getDisplayName(integer).replace("'", ""))
                .map(String::toLowerCase)
                .map(k -> createCompletionItem(k, CompletionItemKind.Keyword))
                .collect(Collectors.toList());

        if (compilationUnit != null && classIndex.size() > 0 && isPatternPosition(candidates)) {
            items.addAll(getClassCompletionItems(compilationUnit, classIndex, prefix));
        }

        if (compilationUnit != null && isConstraintPosition(candidates)) {
            items.addAll(getFieldCompletionItems(compilationUnit, nodeIndex, classIndex, memberIndex, documentPath));
        }

        return items;
    }

    private static boolean isConstraintPosition(CodeCompletionCore.CandidatesCollection candidates) {
        List<Integer> path = candidates.rules.get(DRL10Parser.RULE_drlIdentifier);
        return path != null && path.contains(DRL10Parser.RULE_constraint);
    }

    /**
     * Completion items for the fields of the pattern enclosing the caret:
     * fields of a DRL-declared type (current document first, then sibling
     * files from the active {@link WorkspaceSiblingResolver}), or bean
     * properties/fields of a classpath type resolved through imports and the
     * class index.
     */
    private static List<CompletionItem> getFieldCompletionItems(DRL10Parser.CompilationUnitContext compilationUnit,
                                                                int nodeIndex, ClassIndex classIndex,
                                                                ClassMemberIndex memberIndex, Path documentPath) {
        String patternType = findEnclosingPatternTypeName(compilationUnit, nodeIndex);
        if (patternType == null || patternType.isEmpty()) {
            return List.of();
        }
        String simpleName = patternType.substring(patternType.lastIndexOf('.') + 1);

        // DRL-declared types win over classpath types.
        for (DeclaredType declared : DRLDeclaredTypeParser.extractFromCompilationUnit(compilationUnit)) {
            if (simpleName.equals(declared.name)) {
                return fieldItems(declared.fields);
            }
        }
        if (documentPath != null) {
            for (Path sibling : WorkspaceSiblingResolvers.active().resolveSiblings(documentPath)) {
                for (DeclaredType declared : DRLDeclaredTypeParser.parseDeclaredTypesCached(sibling)) {
                    if (simpleName.equals(declared.name)) {
                        return fieldItems(declared.fields);
                    }
                }
            }
        }

        String fqcn = resolveFqcn(patternType, simpleName, compilationUnit, classIndex);
        if (fqcn == null) {
            return List.of();
        }
        return fieldItems(memberIndex.membersOf(fqcn));
    }

    private static List<CompletionItem> fieldItems(List<Field> fields) {
        List<CompletionItem> items = new ArrayList<>(fields.size());
        for (Field field : fields) {
            CompletionItem item = new CompletionItem();
            item.setLabel(field.name);
            item.setInsertText(field.name);
            item.setDetail(field.type);
            item.setKind(CompletionItemKind.Field);
            items.add(item);
        }
        return items;
    }

    /**
     * Resolves a pattern's type name to a fully qualified class name: an
     * already-qualified name is used as-is, otherwise the imports and then
     * the class index are consulted for the simple name.
     */
    private static String resolveFqcn(String patternType, String simpleName,
                                      DRL10Parser.CompilationUnitContext compilationUnit,
                                      ClassIndex classIndex) {
        if (patternType.indexOf('.') >= 0) {
            return patternType;
        }
        for (String imported : extractImports(compilationUnit)) {
            if (imported.endsWith("." + simpleName)) {
                return imported;
            }
        }
        for (String fqcn : classIndex.getMatching(simpleName)) {
            if (fqcn.endsWith("." + simpleName) || fqcn.equals(simpleName)) {
                return fqcn;
            }
        }
        return null;
    }

    /**
     * Returns the type name of the deepest {@code lhsPattern} whose token
     * span contains {@code tokenIndex}, or {@code null} when the caret is
     * not inside a pattern.
     */
    private static String findEnclosingPatternTypeName(ParseTree node, int tokenIndex) {
        if (!(node instanceof ParserRuleContext ctx)) {
            return null;
        }
        Token start = ctx.getStart();
        Token stop = ctx.getStop();
        if (start == null || start.getTokenIndex() > tokenIndex
                || (stop != null && stop.getTokenIndex() < tokenIndex)) {
            return null;
        }
        String best = null;
        if (ctx instanceof DRL10Parser.LhsPatternContext pattern && pattern.objectType != null) {
            best = pattern.objectType.getText();
        }
        for (int i = 0; i < ctx.getChildCount(); i++) {
            String deeper = findEnclosingPatternTypeName(ctx.getChild(i), tokenIndex);
            if (deeper != null) {
                best = deeper;
            }
        }
        return best;
    }

    private static boolean isPatternPosition(CodeCompletionCore.CandidatesCollection candidates) {
        List<Integer> path = candidates.rules.get(DRL10Parser.RULE_drlQualifiedName);
        if (path == null) {
            return false;
        }
        return path.contains(DRL10Parser.RULE_lhsPattern)
            || path.contains(DRL10Parser.RULE_lhsPatternBind);
    }

    private static List<CompletionItem> getClassCompletionItems(DRL10Parser.CompilationUnitContext compilationUnit, ClassIndex classIndex, String prefix) {
        Set<String> importedFqcns = extractImports(compilationUnit);
        List<String> matchingFqcns = classIndex.getMatching(prefix);
        List<CompletionItem> items = new ArrayList<>();

        for (String fqcn : matchingFqcns) {
            String simpleName = fqcn.substring(fqcn.lastIndexOf('.') + 1);
            CompletionItem item = new CompletionItem();
            item.setLabel(simpleName);
            item.setDetail(fqcn);
            item.setKind(CompletionItemKind.Class);
            item.setInsertText(simpleName);

            if (importedFqcns.contains(fqcn)) {
                item.setSortText("0_" + simpleName + "_" + fqcn);
            } else {
                item.setSortText("1_" + simpleName + "_" + fqcn);
            }

            items.add(item);
        }

        return items;
    }

    private static Set<String> extractImports(DRL10Parser.CompilationUnitContext compilationUnit) {
        Set<String> imports = new HashSet<>();
        for (DRL10Parser.DrlStatementdefContext stmt : compilationUnit.drlStatementdef()) {
            if (stmt.importdef() instanceof DRL10Parser.ImportStandardDefContext importDef) {
                if (importDef.DRL_FUNCTION() == null && importDef.STATIC() == null) {
                    imports.add(importDef.drlQualifiedName().getText());
                }
            }
        }
        return imports;
    }

    private static String extractPrefix(DRL10Parser drlParser, Integer nodeIndex) {
        if (nodeIndex == null || nodeIndex < 0 || nodeIndex >= drlParser.getInputStream().size()) {
            return "";
        }
        Token token = drlParser.getInputStream().get(nodeIndex);
        String text = token.getText();
        if (text != null && !text.isEmpty() && Character.isJavaIdentifierStart(text.charAt(0))) {
            return text;
        }
        return "";
    }

    static CompletionItem createCompletionItem(String label, CompletionItemKind itemKind) {
        CompletionItem completionItem;
        completionItem = new CompletionItem();
        if (label.startsWith("drl_rhs_")) {
            // when Lexer uses "DRL_RHS_" keywords in multiple modes with type(),
            // drlParser.getVocabulary().getDisplayName() returns the keyword name as-is (symbolicNames), so remove the prefix.
            label = label.substring("drl_rhs_".length());
        }
        completionItem.setInsertText(label);
        completionItem.setLabel(label);
        completionItem.setKind(itemKind);
        return completionItem;
    }
}
