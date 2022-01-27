package org.drools.lsp.server;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.antlr.v4.runtime.tree.ParseTree;
import org.drools.drl.ast.descr.PackageDescr;
import org.drools.parser.DRLParser;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.TextDocumentService;

import static org.drools.parser.DRLParserHelper.createParseTree;
import static org.drools.parser.DRLParserHelper.findNodeAtPosition;
import static org.drools.parser.DRLParserHelper.hasParentOfType;
import static org.drools.parser.DRLParserHelper.isAfterSymbol;
import static org.drools.parser.DRLParserHelper.parse;

public class DroolsLspDocumentService implements TextDocumentService {

    private final Map<String, String> sourcesMap = new ConcurrentHashMap<>();

    private final DroolsLspServer server;

    public DroolsLspDocumentService(DroolsLspServer server) {
        this.server = server;
    }

    @Override
    public void didOpen(DidOpenTextDocumentParams params) {
        sourcesMap.put(params.getTextDocument().getUri(), params.getTextDocument().getText());
        CompletableFuture.runAsync(() ->
                server.getClient().publishDiagnostics(
                        new PublishDiagnosticsParams(params.getTextDocument().getUri(), validate())
                )
        );
    }

    private List<Diagnostic> validate() {
        return Collections.emptyList();
    }

    @Override
    public void didChange(DidChangeTextDocumentParams params) {
        sourcesMap.put(params.getTextDocument().getUri(), params.getContentChanges().get(0).getText());
        // modify internal state
//        this.documentVersions.put(params.getTextDocument().getUri(), params.getTextDocument().getVersion() + 1);
        // send notification
        CompletableFuture.runAsync(() ->
                server.getClient().publishDiagnostics(
                        new PublishDiagnosticsParams(params.getTextDocument().getUri(), validate())
                )
        );
    }

    public String getRuleName(CompletionParams completionParams) {
        String text = sourcesMap.get( completionParams.getTextDocument().getUri() );
        PackageDescr packageDescr = parse(text);
        return packageDescr.getRules().get(0).getName();
    }

    @Override
    public CompletableFuture<Either<List<CompletionItem>, CompletionList>> completion(CompletionParams completionParams) {
        return CompletableFuture.supplyAsync( () -> Either.forLeft( attempt( () -> getCompletionItems(completionParams) ) ) );
    }

    private <T> T attempt(Supplier<T> supplier) {
        try {
            return supplier.get();
        } catch (Exception e) {
            server.getClient().showMessage(new MessageParams(MessageType.Error, e.toString()));
        }
        return null;
    }

    private List<CompletionItem> getCompletionItems(CompletionParams completionParams) {
        String text = sourcesMap.get( completionParams.getTextDocument().getUri() );
        ParseTree parseTree = createParseTree(text);

        Position caretPosition = completionParams.getPosition();
        int row = caretPosition == null ? -1 : caretPosition.getLine()+1; // caret line position is zero based
        int col = caretPosition == null ? -1 : caretPosition.getCharacter();

        ParseTree node = caretPosition == null ? null : findNodeAtPosition(parseTree, row, col);

//        server.getClient().showMessage(new MessageParams(MessageType.Info, "Position=" + caretPosition));
//        server.getClient().showMessage(new MessageParams(MessageType.Info, "Node = " + node));
//
//        Token stop = node instanceof TerminalNode ? ((TerminalNode)node).getSymbol() : ((ParserRuleContext)node).getStop();
//        server.getClient().showMessage(new MessageParams(MessageType.Info, "row=" + stop.getLine()));
//        server.getClient().showMessage(new MessageParams(MessageType.Info, "col=" + stop.getCharPositionInLine()));

        CompletionItem completionItem;

        if (hasParentOfType(node, DRLParser.RULE_lhs) || isAfterSymbol(node, DRLParser.WHEN, row, col)) {
            completionItem = new CompletionItem();
            completionItem.setInsertText("LHS");
            completionItem.setLabel("LHS");
            completionItem.setKind(CompletionItemKind.Snippet);
        } else if (hasParentOfType(node, DRLParser.RULE_rhs) || isAfterSymbol(node, DRLParser.THEN, row, col)) {
            completionItem = new CompletionItem();
            completionItem.setInsertText("RHS");
            completionItem.setLabel("RHS");
            completionItem.setKind(CompletionItemKind.Snippet);
        } else {
            completionItem = createDuplicateTextDummyItem(text);
        }

        List<CompletionItem> completionItems = new ArrayList<>();
        completionItems.add(completionItem);

//        server.getClient().showMessage(new MessageParams(MessageType.Info, "completionItem=" + completionItem.getLabel()));

        return completionItems;
    }

    private CompletionItem createDuplicateTextDummyItem(String text) {
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

    @Override
    public void didClose(DidCloseTextDocumentParams params) {

    }

    @Override
    public void didSave(DidSaveTextDocumentParams params) {

    }
}
