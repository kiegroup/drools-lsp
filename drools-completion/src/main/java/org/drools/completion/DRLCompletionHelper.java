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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.vmware.antlr4c3.CodeCompletionCore;
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
        DRL10Parser drlParser = createDrlParser(text);

        int row = caretPosition == null ? -1 : caretPosition.getLine() + 1; // caret line position is zero based
        int col = caretPosition == null ? -1 : caretPosition.getCharacter();

        DRL10Parser.CompilationUnitContext compilationUnit = drlParser.compilationUnit();
        Integer nodeIndex = computeTokenIndex(drlParser, row, col);

        return getCompletionItems(drlParser, nodeIndex, compilationUnit, classIndex);
    }

    static List<CompletionItem> getCompletionItems(DRL10Parser drlParser, int nodeIndex) {
        return getCompletionItems(drlParser, nodeIndex, null, ClassIndex.empty());
    }

    static List<CompletionItem> getCompletionItems(DRL10Parser drlParser, int nodeIndex, DRL10Parser.CompilationUnitContext compilationUnit, ClassIndex classIndex) {
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
            items.addAll(getClassCompletionItems(compilationUnit, classIndex));
        }

        return items;
    }

    private static boolean isPatternPosition(CodeCompletionCore.CandidatesCollection candidates) {
        List<Integer> path = candidates.rules.get(DRL10Parser.RULE_drlQualifiedName);
        if (path == null) {
            return false;
        }
        return path.contains(DRL10Parser.RULE_lhsPattern)
            || path.contains(DRL10Parser.RULE_lhsPatternBind);
    }

    private static List<CompletionItem> getClassCompletionItems(DRL10Parser.CompilationUnitContext compilationUnit, ClassIndex classIndex) {
        Set<String> importedFqcns = extractImports(compilationUnit);
        List<String> matchingFqcns = classIndex.getMatching("");
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
