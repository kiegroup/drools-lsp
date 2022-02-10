package org.drools.lsp.server;/*
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

import java.util.List;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.junit.Ignore;
import org.junit.Test;

import static org.drools.lsp.server.TestHelperMethods.getDroolsLspDocumentService;
import static org.drools.lsp.server.TestHelperMethods.getDroolsLspServerForDocument;
import static org.junit.Assert.*;

public class DroolsLspDocumentServiceTest {

    @Ignore
    @Test
    public void testCompletion() throws Exception {
        // TODO
        DroolsLspDocumentService droolsLspDocumentService = getDroolsLspDocumentService("suggestion");

        CompletionParams completionParams = new CompletionParams();
        completionParams.setTextDocument(new TextDocumentIdentifier("myDocument"));
        Position caretPosition = new Position();
        caretPosition.setCharacter(0);
        caretPosition.setLine(-1); // -1 needed because of  int row = caretPosition == null ? -1 : caretPosition.getLine()+1; // caret line position is zero based
        completionParams.setPosition(caretPosition);

        List<CompletionItem> result = droolsLspDocumentService.getCompletionItems(completionParams);
        CompletionItem completionItem = result.get(0);
        assertEquals("suggestion", completionItem.getInsertText());
    }

    @Test
    public void testReadRuleName() throws Exception {
        String drl = "rule MyRule when Dog(name == \"Bart\") then end";

        DroolsLspDocumentService droolsLspDocumentService = getDroolsLspDocumentService(drl);

        CompletionParams completionParams = new CompletionParams();
        completionParams.setTextDocument(new TextDocumentIdentifier("myDocument"));

        String ruleName = droolsLspDocumentService.getRuleName(completionParams);
        assertEquals("MyRule", ruleName);
    }


    @Test
    public void testFindLHSandRHS() throws Exception {
        String drl =
                "package org.test;\n" +
                        "import org.test.model.Person;\n" +
                        "rule TestRule when\n" +
                        "  $p:Person() \n" +
                        "then\n" +
                        "  System.out.println($p.getName()); \n" +
                        "end";

        DroolsLspDocumentService droolsLspDocumentService = getDroolsLspDocumentService(drl);

        CompletionParams completionParams = new CompletionParams();
        completionParams.setTextDocument(new TextDocumentIdentifier("myDocument"));

        completionParams.setPosition(new Position(1, 0));
        List<CompletionItem> result = droolsLspDocumentService.getCompletionItems(completionParams);
        assertTrue( hasItem(result, "import") );
        assertTrue( hasItem(result, "rule") );

        completionParams.setPosition(new Position(3, 14));
        result = droolsLspDocumentService.getCompletionItems(completionParams);
        assertTrue( hasItem(result, "LHS") );
//        assertTrue( hasItem(result, "then") );

        completionParams.setPosition(new Position(5, 36));
        result = droolsLspDocumentService.getCompletionItems(completionParams);
        assertTrue( hasItem(result, "RHS") );
//        assertTrue( hasItem(result, "end") );
    }

    private boolean hasItem(List<CompletionItem> result, String text) {
        return result.stream().map(CompletionItem::getInsertText).anyMatch(text::equals);
    }


}