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

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import com.vmware.antlr4c3.CodeCompletionCore;
import org.antlr.v4.runtime.tree.ParseTree;
import org.drools.parser.DRLParser;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.services.LanguageClient;

import static org.drools.parser.DRLParserHelper.createDrlParser;
import static org.drools.parser.DRLParserHelper.findNodeAtPosition;
import static org.drools.parser.DRLParserHelper.getNodeIndex;
import static org.drools.parser.DRLParserHelper.hasParentOfType;
import static org.drools.parser.DRLParserHelper.isAfterSymbol;

public class DRLCompletionHelper {

    private DRLCompletionHelper() {
    }

    public static List<CompletionItem> getCompletionItems(String text, Position caretPosition, LanguageClient client) {
        DRLParser drlParser = createDrlParser(text);

        int row = caretPosition == null ? -1 : caretPosition.getLine()+1; // caret line position is zero based
        int col = caretPosition == null ? -1 : caretPosition.getCharacter();

        ParseTree parseTree = drlParser.compilationunit();
        ParseTree node = caretPosition == null ? null : findNodeAtPosition(parseTree, row, col);
        // TODO Fix NPE if caretPosition/node are null
        client.showMessage(new MessageParams(MessageType.Info, "node = " + node));

        List<CompletionItem> completionItems = getCompletionItems(drlParser, node);

        CompletionItem completionItem;

        if (hasParentOfType(node, DRLParser.RULE_lhs) || isAfterSymbol(node, DRLParser.WHEN, row, col)) {
            completionItem = createCompletionItem("LHS", CompletionItemKind.Snippet);
        } else if (hasParentOfType(node, DRLParser.RULE_rhs) || isAfterSymbol(node, DRLParser.THEN, row, col)) {
            completionItem = createCompletionItem("RHS", CompletionItemKind.Snippet);
        } else {
            completionItem = createDuplicateTextDummyItem(text);
        }

        completionItems.add(completionItem);

        client.showMessage(new MessageParams(MessageType.Info, "completionItem=" + completionItem.getLabel()));

        return completionItems;
    }

    static List<CompletionItem> getCompletionItems(DRLParser drlParser, ParseTree node) {
        CodeCompletionCore core = new CodeCompletionCore(drlParser, null, null);
        // TODO Fix NPE if node is null
        CodeCompletionCore.CandidatesCollection candidates = core.collectCandidates(getNodeIndex(node), drlParser.getRuleContext());

        return candidates.tokens.keySet().stream().filter(Objects::nonNull )
                .filter( i -> i <= DRLParser.END ) // filter keywords only
                .map( drlParser.getVocabulary()::getSymbolicName )
                .map( String::toLowerCase )
                .map( k -> createCompletionItem(k, CompletionItemKind.Keyword))
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

    static CompletionItem createDuplicateTextDummyItem(String text) {
        // Sample Completion item for text duplication
        CompletionItem completionItem = new CompletionItem();

        // Define the text to be inserted in to the file if the completion item is selected.
        completionItem.setInsertText(text == null ? "" : text);

        // Set the label that shows when the completion drop down appears in the Editor.
        completionItem.setLabel("duplicate text");

        // Set the completion kind. This is a snippet.
        // That means it replace character which trigger the completion and
        // replace it with what defined in inserted text.
        completionItem.setKind(CompletionItemKind.Snippet);

        // This will set the details for the snippet code which will help user to
        // understand what this completion item is.
        completionItem.setDetail("this will duplicate current text");
        return completionItem;
    }

}
