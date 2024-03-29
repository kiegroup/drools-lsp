package org.drools.parser;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.drools.drl.ast.descr.PackageDescr;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.drools.parser.DRLParserHelper.compilationUnitContext2PackageDescr;

/**
 * Wrapper for DRLParser. Somewhat duplicated from DRLParserHelper, but this class is instantiated and holds errors.
 */
public class DRLParserWrapper {

    private static final Logger LOGGER = LoggerFactory.getLogger(DRLParserWrapper.class);

    private final List<DRLParserError> errors = new ArrayList<>();

    /**
     * Main entry point for parsing DRL
     */
    public PackageDescr parse(String drl) {
        DRLParser drlParser = DRLParserHelper.createDrlParser(drl);
        return parse(drlParser);
    }

    /**
     * Main entry point for parsing DRL
     */
    public PackageDescr parse(InputStream is) {
        DRLParser drlParser = DRLParserHelper.createDrlParser(is);
        return parse(drlParser);
    }

    private PackageDescr parse(DRLParser drlParser) {
        DRLErrorListener errorListener = new DRLErrorListener();
        drlParser.addErrorListener(errorListener);

        DRLParser.CompilationUnitContext cxt = drlParser.compilationUnit();

        errors.addAll(errorListener.getErrors());

        try {
            return compilationUnitContext2PackageDescr(cxt, drlParser.getTokenStream());
        } catch (Exception e) {
            LOGGER.error("Exception while creating PackageDescr", e);
            errors.add(new DRLParserError(e));
            return null;
        }
    }

    public List<DRLParserError> getErrors() {
        return errors;
    }

    public List<String> getErrorMessages() {
        return errors.stream().map(DRLParserError::getMessage).collect(Collectors.toList());
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }
}
