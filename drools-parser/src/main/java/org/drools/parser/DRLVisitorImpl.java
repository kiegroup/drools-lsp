package org.drools.parser;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.TokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.RuleNode;
import org.drools.drl.ast.descr.AccumulateDescr;
import org.drools.drl.ast.descr.AccumulateImportDescr;
import org.drools.drl.ast.descr.AndDescr;
import org.drools.drl.ast.descr.AnnotationDescr;
import org.drools.drl.ast.descr.AttributeDescr;
import org.drools.drl.ast.descr.BaseDescr;
import org.drools.drl.ast.descr.BehaviorDescr;
import org.drools.drl.ast.descr.CollectDescr;
import org.drools.drl.ast.descr.EntryPointDeclarationDescr;
import org.drools.drl.ast.descr.EntryPointDescr;
import org.drools.drl.ast.descr.EvalDescr;
import org.drools.drl.ast.descr.ExistsDescr;
import org.drools.drl.ast.descr.ExprConstraintDescr;
import org.drools.drl.ast.descr.ForallDescr;
import org.drools.drl.ast.descr.FromDescr;
import org.drools.drl.ast.descr.FunctionDescr;
import org.drools.drl.ast.descr.FunctionImportDescr;
import org.drools.drl.ast.descr.GlobalDescr;
import org.drools.drl.ast.descr.ImportDescr;
import org.drools.drl.ast.descr.MVELExprDescr;
import org.drools.drl.ast.descr.NotDescr;
import org.drools.drl.ast.descr.OrDescr;
import org.drools.drl.ast.descr.PackageDescr;
import org.drools.drl.ast.descr.PatternDescr;
import org.drools.drl.ast.descr.PatternSourceDescr;
import org.drools.drl.ast.descr.QueryDescr;
import org.drools.drl.ast.descr.RuleDescr;
import org.drools.drl.ast.descr.TypeDeclarationDescr;
import org.drools.drl.ast.descr.TypeFieldDescr;
import org.drools.drl.ast.descr.UnitDescr;
import org.drools.drl.ast.descr.WindowDeclarationDescr;

import static org.drools.parser.DRLParserHelper.getTextWithoutErrorNode;
import static org.drools.parser.ParserStringUtils.getTextPreservingWhitespace;
import static org.drools.parser.ParserStringUtils.getTokenTextPreservingWhitespace;
import static org.drools.parser.ParserStringUtils.safeStripStringDelimiters;
import static org.drools.parser.ParserStringUtils.trimThen;
import static org.drools.util.StringUtils.unescapeJava;

public class DRLVisitorImpl extends DRLParserBaseVisitor<Object> {

    private final TokenStream tokenStream;

    public DRLVisitorImpl(TokenStream tokenStream) {
        this.tokenStream = tokenStream;
    }

    @Override
    public PackageDescr visitCompilationUnit(DRLParser.CompilationUnitContext ctx) {
        PackageDescr packageDescr = new PackageDescr();
        if (ctx.packagedef() != null) {
            packageDescr.setName(getTextWithoutErrorNode(ctx.packagedef().name));
        }
        List<BaseDescr> descrList = visitDescrChildren(ctx);
        applyChildrenDescrs(packageDescr, descrList);
        return packageDescr;
    }

    private void applyChildrenDescrs(PackageDescr packageDescr, List<BaseDescr> descrList) {
        descrList.forEach(descr -> {
            if (descr instanceof UnitDescr) {
                packageDescr.setUnit((UnitDescr) descr);
            } else if (descr instanceof GlobalDescr) {
                packageDescr.addGlobal((GlobalDescr) descr);
            } else if (descr instanceof FunctionImportDescr) {
                packageDescr.addFunctionImport((FunctionImportDescr) descr);
            } else if (descr instanceof AccumulateImportDescr) {
                packageDescr.addAccumulateImport((AccumulateImportDescr) descr);
            } else if (descr instanceof ImportDescr) {
                packageDescr.addImport((ImportDescr) descr);
            } else if (descr instanceof FunctionDescr) {
                FunctionDescr functionDescr = (FunctionDescr) descr;
                functionDescr.setNamespace(packageDescr.getNamespace());
                AttributeDescr dialect = packageDescr.getAttribute("dialect");
                if (dialect != null) {
                    functionDescr.setDialect(dialect.getValue());
                }
                packageDescr.addFunction(functionDescr);
            } else if (descr instanceof TypeDeclarationDescr) {
                packageDescr.addTypeDeclaration((TypeDeclarationDescr) descr);
            } else if (descr instanceof EntryPointDeclarationDescr) {
                packageDescr.addEntryPointDeclaration((EntryPointDeclarationDescr) descr);
            } else if (descr instanceof WindowDeclarationDescr) {
                packageDescr.addWindowDeclaration((WindowDeclarationDescr) descr);
            } else if (descr instanceof AttributeDescr) {
                packageDescr.addAttribute((AttributeDescr) descr);
            } else if (descr instanceof RuleDescr) { // QueryDescr extends RuleDescr
                packageDescr.addRule((RuleDescr) descr);
                packageDescr.afterRuleAdded((RuleDescr) descr);
            }
        });
    }

    @Override
    public UnitDescr visitUnitdef(DRLParser.UnitdefContext ctx) {
        return new UnitDescr(ctx.name.getText());
    }

    @Override
    public GlobalDescr visitGlobaldef(DRLParser.GlobaldefContext ctx) {
        GlobalDescr globalDescr = new GlobalDescr(ctx.drlIdentifier().getText(), ctx.type().getText());
        populateStartEnd(globalDescr, ctx);
        return globalDescr;
    }

    @Override
    public ImportDescr visitImportStandardDef(DRLParser.ImportStandardDefContext ctx) {
        String target = ctx.drlQualifiedName().getText() + (ctx.MUL() != null ? ".*" : "");
        if (ctx.DRL_FUNCTION() != null || ctx.STATIC() != null) {
            FunctionImportDescr functionImportDescr = new FunctionImportDescr();
            functionImportDescr.setTarget(target);
            populateStartEnd(functionImportDescr, ctx);
            return functionImportDescr;
        } else {
            ImportDescr importDescr = new ImportDescr();
            importDescr.setTarget(target);
            populateStartEnd(importDescr, ctx);
            return importDescr;
        }
    }

    @Override
    public AccumulateImportDescr visitImportAccumulateDef(DRLParser.ImportAccumulateDefContext ctx) {
        AccumulateImportDescr accumulateImportDescr = new AccumulateImportDescr();
        accumulateImportDescr.setTarget(ctx.drlQualifiedName().getText());
        accumulateImportDescr.setFunctionName(ctx.IDENTIFIER().getText());
        return accumulateImportDescr;
    }

    @Override
    public FunctionDescr visitFunctiondef(DRLParser.FunctiondefContext ctx) {
        FunctionDescr functionDescr = new FunctionDescr();
        if (ctx.typeTypeOrVoid() != null) {
            functionDescr.setReturnType(ctx.typeTypeOrVoid().getText());
        } else {
            functionDescr.setReturnType("void");
        }
        functionDescr.setName(ctx.IDENTIFIER().getText());
        DRLParser.FormalParametersContext formalParametersContext = ctx.formalParameters();
        DRLParser.FormalParameterListContext formalParameterListContext = formalParametersContext.formalParameterList();
        if (formalParameterListContext != null) {
            List<DRLParser.FormalParameterContext> formalParameterContexts = formalParameterListContext.formalParameter();
            formalParameterContexts.forEach(formalParameterContext -> {
                DRLParser.TypeTypeContext typeTypeContext = formalParameterContext.typeType();
                DRLParser.VariableDeclaratorIdContext variableDeclaratorIdContext = formalParameterContext.variableDeclaratorId();
                functionDescr.addParameter(typeTypeContext.getText(), variableDeclaratorIdContext.getText());
            });
        }
        functionDescr.setBody(getTextPreservingWhitespace(ctx.block()));
        return functionDescr;
    }

    @Override
    public BaseDescr visitDeclaredef(DRLParser.DeclaredefContext ctx) {
        return visitDescrChildren(ctx).get(0);
    }

    @Override
    public TypeDeclarationDescr visitTypeDeclaration(DRLParser.TypeDeclarationContext ctx) {
        TypeDeclarationDescr typeDeclarationDescr = new TypeDeclarationDescr(ctx.name.getText());
        if (ctx.EXTENDS() != null) {
            typeDeclarationDescr.addSuperType(ctx.superType.getText());
        }
        ctx.drlAnnotation().stream()
                .map(this::visitDrlAnnotation)
                .forEach(typeDeclarationDescr::addAnnotation);
        ctx.field().stream()
                .map(this::visitField)
                .forEach(typeDeclarationDescr::addField);
        return typeDeclarationDescr;
    }

    @Override
    public EntryPointDeclarationDescr visitEntryPointDeclaration(DRLParser.EntryPointDeclarationContext ctx) {
        EntryPointDeclarationDescr entryPointDeclarationDescr = new EntryPointDeclarationDescr();
        entryPointDeclarationDescr.setEntryPointId(ctx.name.getText());
        ctx.drlAnnotation().stream()
                .map(this::visitDrlAnnotation)
                .forEach(entryPointDeclarationDescr::addAnnotation);
        return entryPointDeclarationDescr;
    }

    @Override
    public WindowDeclarationDescr visitWindowDeclaration(DRLParser.WindowDeclarationContext ctx) {
        WindowDeclarationDescr windowDeclarationDescr = new WindowDeclarationDescr();
        windowDeclarationDescr.setName(ctx.name.getText());
        ctx.drlAnnotation().stream()
                .map(this::visitDrlAnnotation)
                .forEach(windowDeclarationDescr::addAnnotation);
        windowDeclarationDescr.setPattern((PatternDescr) visitLhsPatternBind(ctx.lhsPatternBind()));
        return windowDeclarationDescr;
    }

    @Override
    public RuleDescr visitRuledef(DRLParser.RuledefContext ctx) {
        RuleDescr ruleDescr = new RuleDescr(safeStripStringDelimiters(ctx.name.getText()));

        if (ctx.EXTENDS() != null) {
            ruleDescr.setParentName(safeStripStringDelimiters(ctx.parentName.getText()));
        }

        ctx.drlAnnotation().stream().map(this::visitDrlAnnotation).forEach(ruleDescr::addAnnotation);

        if (ctx.attributes() != null) {
            List<BaseDescr> descrList = visitDescrChildren(ctx.attributes());
            descrList.stream()
                    .filter(AttributeDescr.class::isInstance)
                    .map(AttributeDescr.class::cast)
                    .forEach(ruleDescr::addAttribute);
        }

        if (ctx.lhs() != null) {
            List<BaseDescr> lhsDescrList = visitLhs(ctx.lhs());
            lhsDescrList.forEach(descr -> ruleDescr.getLhs().addDescr(descr));
            slimLhsRootDescr(ruleDescr.getLhs());
        }

        if (ctx.rhs() != null) {
            ruleDescr.setConsequenceLocation(ctx.rhs().getStart().getLine(), ctx.rhs().getStart().getCharPositionInLine()); // location of "then"
            ruleDescr.setConsequence(trimThen(getTextPreservingWhitespace(ctx.rhs())));
        }

        return ruleDescr;
    }

    private void slimLhsRootDescr(AndDescr root) {
        List<BaseDescr> descrList = new ArrayList<>(root.getDescrs());
        root.getDescrs().clear();
        descrList.forEach(root::addOrMerge); // This slims down nested AndDescr
    }

    @Override
    public QueryDescr visitQuerydef(DRLParser.QuerydefContext ctx) {
        QueryDescr queryDescr = new QueryDescr(safeStripStringDelimiters(ctx.name.getText()));

        DRLParser.FormalParametersContext formalParametersContext = ctx.formalParameters();
        if (formalParametersContext != null) {
            DRLParser.FormalParameterListContext formalParameterListContext = formalParametersContext.formalParameterList();
            List<DRLParser.FormalParameterContext> formalParameterContexts = formalParameterListContext.formalParameter();
            formalParameterContexts.forEach(formalParameterContext -> {
                DRLParser.TypeTypeContext typeTypeContext = formalParameterContext.typeType();
                DRLParser.VariableDeclaratorIdContext variableDeclaratorIdContext = formalParameterContext.variableDeclaratorId();
                queryDescr.addParameter(typeTypeContext.getText(), variableDeclaratorIdContext.getText());
            });
        }

        ctx.drlAnnotation().stream().map(this::visitDrlAnnotation).forEach(queryDescr::addAnnotation);

        ctx.lhsExpression().stream()
                           .flatMap(lhsExpressionContext -> visitDescrChildren(lhsExpressionContext).stream())
                           .forEach(descr -> queryDescr.getLhs().addDescr(descr));

        slimLhsRootDescr(queryDescr.getLhs());

        return queryDescr;
    }

    @Override
    public AnnotationDescr visitDrlAnnotation(DRLParser.DrlAnnotationContext ctx) {
        AnnotationDescr annotationDescr = new AnnotationDescr(ctx.name.getText());
        if (ctx.drlElementValue() != null) {
            annotationDescr.setValue(getTextPreservingWhitespace(ctx.drlElementValue())); // single value
        } else if (ctx.drlElementValuePairs() != null) {
            visitDrlElementValuePairs(ctx.drlElementValuePairs(), annotationDescr); // multiple values
        }
        return annotationDescr;
    }

    @Override
    public TypeFieldDescr visitField(DRLParser.FieldContext ctx) {
        TypeFieldDescr typeFieldDescr = new TypeFieldDescr();
        typeFieldDescr.setFieldName(ctx.label().IDENTIFIER().getText());
        typeFieldDescr.setPattern(new PatternDescr(ctx.type().getText()));
        if (ctx.ASSIGN() != null) {
            typeFieldDescr.setInitExpr(getTextPreservingWhitespace(ctx.initExpr));
        }
        ctx.drlAnnotation().stream()
                .map(this::visitDrlAnnotation)
                .forEach(typeFieldDescr::addAnnotation);
        return typeFieldDescr;
    }

    private void visitDrlElementValuePairs(DRLParser.DrlElementValuePairsContext ctx, AnnotationDescr annotationDescr) {
        ctx.drlElementValuePair().forEach(pairCtx -> {
            String key = pairCtx.key.getText();
            String value = getTextPreservingWhitespace(pairCtx.value);
            annotationDescr.setKeyValue(key, value);
        });
    }

    @Override
    public AttributeDescr visitAttribute(DRLParser.AttributeContext ctx) {
        AttributeDescr attributeDescr = new AttributeDescr(ctx.getChild(0).getText());
        if (ctx.getChildCount() > 1) {
            // TODO : will likely split visitAttribute methods using labels (e.g. #stringAttribute)
            String value = unescapeJava(safeStripStringDelimiters(ctx.getChild(1).getText()));
            attributeDescr.setValue(value);
        }
        return attributeDescr;
    }

    @Override
    public List<BaseDescr> visitLhs(DRLParser.LhsContext ctx) {
        if (ctx.lhsExpression() != null) {
            return visitDescrChildren(ctx);
        } else {
            return new ArrayList<>();
        }
    }

    @Override
    public BaseDescr visitLhsPatternBind(DRLParser.LhsPatternBindContext ctx) {
        if (ctx.lhsPattern().size() == 1) {
            return getSinglePatternDescr(ctx);
        } else if (ctx.lhsPattern().size() > 1) {
            return getOrDescrWithMultiplePatternDescr(ctx);
        } else {
            throw new IllegalStateException("ctx.lhsPattern().size() == 0 : " + ctx.getText());
        }
    }

    private PatternDescr getSinglePatternDescr(DRLParser.LhsPatternBindContext ctx) {
        Optional<BaseDescr> optPatternDescr = visitFirstDescrChild(ctx);
        PatternDescr patternDescr = optPatternDescr.filter(PatternDescr.class::isInstance)
                .map(PatternDescr.class::cast)
                .orElseThrow(() -> new IllegalStateException("lhsPatternBind must have at least one lhsPattern : " + ctx.getText()));
        if (ctx.label() != null) {
            patternDescr.setIdentifier(ctx.label().IDENTIFIER().getText());
        } else if (ctx.unif() != null) {
            patternDescr.setIdentifier(ctx.unif().IDENTIFIER().getText());
            patternDescr.setUnification(true);
        }
        return patternDescr;
    }

    private OrDescr getOrDescrWithMultiplePatternDescr(DRLParser.LhsPatternBindContext ctx) {
        OrDescr orDescr = new OrDescr();
        List<BaseDescr> descrList = visitDescrChildren(ctx);
        descrList.stream()
                .filter(PatternDescr.class::isInstance)
                .map(PatternDescr.class::cast)
                .forEach(patternDescr -> {
                    if (ctx.label() != null) {
                        patternDescr.setIdentifier(ctx.label().IDENTIFIER().getText());
                    }
                    orDescr.addDescr(patternDescr);
                });

        return orDescr;
    }

    @Override
    public PatternDescr visitLhsPattern(DRLParser.LhsPatternContext ctx) {
        PatternDescr patternDescr = new PatternDescr(ctx.objectType.getText());
        if (ctx.QUESTION() != null) {
            patternDescr.setQuery(true);
        }
        if (ctx.patternFilter() != null) {
            patternDescr.addBehavior(visitPatternFilter(ctx.patternFilter()));
        }
        if (ctx.patternSource() != null) {
            PatternSourceDescr patternSourceDescr = (PatternSourceDescr) visitPatternSource(ctx.patternSource());
            patternSourceDescr.setResource(patternDescr.getResource());
            patternDescr.setSource(patternSourceDescr);
        }
        List<ExprConstraintDescr> constraintDescrList = visitConstraints(ctx.positionalConstraints(), ctx.constraints());
        constraintDescrList.forEach(descr -> addToPatternDescr(patternDescr, descr));
        return patternDescr;
    }

    private void addToPatternDescr(PatternDescr patternDescr, ExprConstraintDescr exprConstraintDescr) {
        exprConstraintDescr.setResource(patternDescr.getResource());
        patternDescr.addConstraint(exprConstraintDescr);
    }

    @Override
    public ForallDescr visitLhsForall(DRLParser.LhsForallContext ctx) {
        ForallDescr forallDescr = new ForallDescr();
        visitDescrChildren(ctx).forEach(forallDescr::addDescr);
        return forallDescr;
    }

    @Override
    public PatternDescr visitLhsAccumulate(DRLParser.LhsAccumulateContext ctx) {
        AccumulateDescr accumulateDescr = new AccumulateDescr();
        accumulateDescr.setInput(visitLhsAndDef(ctx.lhsAndDef()));

        // accumulate function
        for (DRLParser.AccumulateFunctionContext accumulateFunctionContext : ctx.accumulateFunction()) {
            accumulateDescr.addFunction(visitAccumulateFunction(accumulateFunctionContext));
        }

        PatternDescr patternDescr = new PatternDescr("Object");
        patternDescr.setSource(accumulateDescr);
        List<ExprConstraintDescr> constraintDescrList = visitConstraints(ctx.constraints());
        constraintDescrList.forEach(patternDescr::addConstraint);
        return patternDescr;
    }

    @Override
    public BehaviorDescr visitPatternFilter(DRLParser.PatternFilterContext ctx) {
        BehaviorDescr behaviorDescr = new BehaviorDescr();
        behaviorDescr.setType(ctx.DRL_WINDOW().getText());
        behaviorDescr.setSubType(ctx.IDENTIFIER().getText());
        List<DRLParser.DrlExpressionContext> drlExpressionContexts = ctx.expressionList().drlExpression();
        List<String> parameters = drlExpressionContexts.stream().map(ParserStringUtils::getTextPreservingWhitespace).collect(Collectors.toList());
        behaviorDescr.setParameters(parameters);
        return behaviorDescr;
    }

    @Override
    public FromDescr visitFromExpression(DRLParser.FromExpressionContext ctx) {
        FromDescr fromDescr = new FromDescr();
        fromDescr.setDataSource(new MVELExprDescr(ctx.getText()));
        return fromDescr;
    }

    @Override
    public CollectDescr visitFromCollect(DRLParser.FromCollectContext ctx) {
        CollectDescr collectDescr = new CollectDescr();
        collectDescr.setInputPattern((PatternDescr) visitLhsPatternBind(ctx.lhsPatternBind()));
        return collectDescr;
    }

    @Override
    public AccumulateDescr visitFromAccumulate(DRLParser.FromAccumulateContext ctx) {
        AccumulateDescr accumulateDescr = new AccumulateDescr();
        accumulateDescr.setInput(visitLhsAndDef(ctx.lhsAndDef()));
        if (ctx.DRL_INIT() != null) {
            // inline custom accumulate
            accumulateDescr.setInitCode(getTextPreservingWhitespace(ctx.initBlockStatements));
            accumulateDescr.setActionCode(getTextPreservingWhitespace(ctx.actionBlockStatements));
            if (ctx.DRL_REVERSE() != null) {
                accumulateDescr.setReverseCode(getTextPreservingWhitespace(ctx.reverseBlockStatements));
            }
            accumulateDescr.setResultCode(ctx.expression().getText());
        } else {
            // accumulate function
            accumulateDescr.addFunction(visitAccumulateFunction(ctx.accumulateFunction()));
        }
        return accumulateDescr;
    }

    @Override
    public AccumulateDescr.AccumulateFunctionCallDescr visitAccumulateFunction(DRLParser.AccumulateFunctionContext ctx) {
        String function = ctx.IDENTIFIER().getText();
        String bind = ctx.label() == null ? null : ctx.label().IDENTIFIER().getText();
        String[] params = new String[]{getTextPreservingWhitespace(ctx.drlExpression())};
        return new AccumulateDescr.AccumulateFunctionCallDescr(function, bind, false, params);
    }

    @Override
    public EntryPointDescr visitFromEntryPoint(DRLParser.FromEntryPointContext ctx) {
        return new EntryPointDescr(safeStripStringDelimiters(ctx.stringId().getText()));
    }

    @Override
    public List<ExprConstraintDescr> visitConstraints(DRLParser.ConstraintsContext ctx) {
        List<ExprConstraintDescr> exprConstraintDescrList = new ArrayList<>();
        populateExprConstraintDescrList(ctx, exprConstraintDescrList);
        return exprConstraintDescrList;
    }

    private List<ExprConstraintDescr> visitConstraints(DRLParser.PositionalConstraintsContext positionalCtx, DRLParser.ConstraintsContext ctx) {
        List<ExprConstraintDescr> exprConstraintDescrList = new ArrayList<>();
        populateExprConstraintDescrList(positionalCtx, exprConstraintDescrList);
        populateExprConstraintDescrList(ctx, exprConstraintDescrList);
        return exprConstraintDescrList;
    }

    private void populateExprConstraintDescrList(ParserRuleContext ctx, List<ExprConstraintDescr> exprConstraintDescrList) {
        if (ctx == null) {
            return;
        }
        List<BaseDescr> descrList = visitDescrChildren(ctx);
        for (BaseDescr descr : descrList) {
            if (descr instanceof ExprConstraintDescr) {
                ExprConstraintDescr exprConstraintDescr = (ExprConstraintDescr) descr;
                exprConstraintDescr.setType(ctx instanceof DRLParser.PositionalConstraintsContext ? ExprConstraintDescr.Type.POSITIONAL : ExprConstraintDescr.Type.NAMED);
                exprConstraintDescr.setPosition(exprConstraintDescrList.size());
                exprConstraintDescrList.add(exprConstraintDescr);
            }
        }
    }

    @Override
    public ExprConstraintDescr visitConstraint(DRLParser.ConstraintContext ctx) {
        String constraint = visitConstraintChildren(ctx);
        if (!constraint.isEmpty()) {
            ExprConstraintDescr constraintDescr = new ExprConstraintDescr(constraint);
            constraintDescr.setType(ExprConstraintDescr.Type.NAMED);
            return constraintDescr;
        }
        return null;
    }

    @Override
    public String visitDrlIdentifier(DRLParser.DrlIdentifierContext ctx) {
        return ctx.getText();
    }

    @Override
    public ExistsDescr visitLhsExists(DRLParser.LhsExistsContext ctx) {
        ExistsDescr existsDescr = new ExistsDescr();
        BaseDescr descr = visitLhsPatternBind(ctx.lhsPatternBind());
        existsDescr.addDescr(descr);
        return existsDescr;
    }

    @Override
    public NotDescr visitLhsNot(DRLParser.LhsNotContext ctx) {
        NotDescr notDescr = new NotDescr();
        BaseDescr descr = visitLhsPatternBind(ctx.lhsPatternBind());
        notDescr.addDescr(descr);
        return notDescr;
    }

    @Override
    public EvalDescr visitLhsEval(DRLParser.LhsEvalContext ctx) {
        return new EvalDescr(getTextPreservingWhitespace(ctx.conditionalOrExpression()));
    }

    @Override
    public BaseDescr visitLhsExpressionEnclosed(DRLParser.LhsExpressionEnclosedContext ctx) {
        return (BaseDescr) visit(ctx.lhsExpression());
    }

    @Override
    public BaseDescr visitLhsOr(DRLParser.LhsOrContext ctx) {
        OrDescr orDescr = new OrDescr();
        List<BaseDescr> descrList = visitDescrChildren(ctx);
        descrList.forEach(orDescr::addDescr);
        return orDescr;
    }

    @Override
    public BaseDescr visitLhsAnd(DRLParser.LhsAndContext ctx) {
        return createAndDescr(visitDescrChildren(ctx));
    }

    private AndDescr createAndDescr(List<BaseDescr> descrList) {
        AndDescr andDescr = new AndDescr();
        descrList.forEach(andDescr::addDescr);
        return andDescr;
    }

    @Override
    public BaseDescr visitLhsAndDef(DRLParser.LhsAndDefContext ctx) {
        return createAndDescr(visitDescrChildren(ctx));
    }

    @Override
    public BaseDescr visitLhsUnary(DRLParser.LhsUnaryContext ctx) {
        return visitDescrChildren(ctx).get(0);
    }

    private void populateStartEnd(BaseDescr descr, ParserRuleContext ctx) {
        descr.setStartCharacter(ctx.getStart().getStartIndex());
        // TODO: Current DRL6Parser adds +1 for EndCharacter but it doesn't look reasonable. At the moment, I don't add. Instead, I fix unit tests.
        //       I will revisit if this is the right approach.
        descr.setEndCharacter(ctx.getStop().getStopIndex());
    }

    private List<BaseDescr> visitDescrChildren(RuleNode node) {
        List<BaseDescr> aggregator = new ArrayList<>();
        int n = node.getChildCount();

        for (int i = 0; i < n && this.shouldVisitNextChild(node, aggregator); ++i) {
            ParseTree c = node.getChild(i);
            Object childResult = c.accept(this);
            if (childResult instanceof BaseDescr) {
                aggregator.add((BaseDescr) childResult);
            }
        }
        return aggregator;
    }

    // leaves of constraint concatenate return Strings
    private String visitConstraintChildren(ParserRuleContext ctx) {
        return getTokenTextPreservingWhitespace(ctx, tokenStream);
    }

    private Optional<BaseDescr> visitFirstDescrChild(RuleNode node) {
        int n = node.getChildCount();

        for (int i = 0; i < n && this.shouldVisitNextChild(node, null); ++i) {
            ParseTree c = node.getChild(i);
            Object childResult = c.accept(this);
            if (childResult instanceof BaseDescr) {
                return Optional.of((BaseDescr) childResult);
            }
        }
        return Optional.empty();
    }
}
