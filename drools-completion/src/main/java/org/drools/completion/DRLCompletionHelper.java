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

import com.vmware.antlr4c3.CodeCompletionCore;
import org.drools.parser.DRLParser;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.services.LanguageClient;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.drools.parser.DRLParserHelper.computeTokenIndex;
import static org.drools.parser.DRLParserHelper.createDrlParser;

public class DRLCompletionHelper {

    private DRLCompletionHelper() {
    }

    public static List<CompletionItem> getCompletionItems(String text, Position caretPosition, LanguageClient client) {
        DRLParser drlParser = createDrlParser(text);

        int row = caretPosition == null ? -1 : caretPosition.getLine() + 1; // caret line position is zero based
        int col = caretPosition == null ? -1 : caretPosition.getCharacter();

        drlParser.compilationUnit();
        Integer nodeIndex = computeTokenIndex(drlParser, row, col);

        return getCompletionItems(drlParser, nodeIndex);
    }

    static List<CompletionItem> getCompletionItems(DRLParser drlParser, int nodeIndex) {
        CodeCompletionCore core = new CodeCompletionCore(drlParser, null, null);
        CodeCompletionCore.CandidatesCollection candidates = core.collectCandidates(nodeIndex, null);

        return candidates.tokens.keySet().stream().filter(Objects::nonNull)
                .filter(integer -> !Tokens.IGNORED.contains(integer))
                .map(integer -> drlParser.getVocabulary().getDisplayName(integer).replace("'", ""))
                .map(String::toLowerCase)
                .map(k -> createCompletionItem(k, CompletionItemKind.Keyword))
                .collect(Collectors.toList());
    }

    static CompletionItem createCompletionItem(String label, CompletionItemKind itemKind) {
        CompletionItem completionItem;
        completionItem = new CompletionItem();
        completionItem.setInsertText(label);
        completionItem.setLabel(label);
        completionItem.setKind(itemKind);
        return completionItem;
    }
}
