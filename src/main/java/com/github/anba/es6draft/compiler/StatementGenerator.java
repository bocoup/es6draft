/**
 * Copyright (c) 2012-2013 André Bargull
 * Alle Rechte vorbehalten / All Rights Reserved.  Use is subject to license terms.
 *
 * <https://github.com/anba/es6draft>
 */
package com.github.anba.es6draft.compiler;

import static com.github.anba.es6draft.semantics.StaticSemantics.BoundNames;
import static com.github.anba.es6draft.semantics.StaticSemantics.IsConstantDeclaration;
import static com.github.anba.es6draft.semantics.StaticSemantics.LexicalDeclarations;
import static com.github.anba.es6draft.semantics.StaticSemantics.TailCallNodes;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import org.objectweb.asm.Label;
import org.objectweb.asm.Type;

import com.github.anba.es6draft.ast.AbruptNode.Abrupt;
import com.github.anba.es6draft.ast.*;
import com.github.anba.es6draft.ast.synthetic.StatementListMethod;
import com.github.anba.es6draft.compiler.InstructionVisitor.MethodDesc;
import com.github.anba.es6draft.compiler.InstructionVisitor.MethodType;
import com.github.anba.es6draft.compiler.InstructionVisitor.Variable;
import com.github.anba.es6draft.parser.Parser;
import com.github.anba.es6draft.runtime.LexicalEnvironment;
import com.github.anba.es6draft.runtime.internal.ScriptException;

/**
 *
 */
class StatementGenerator extends
        DefaultCodeGenerator<StatementGenerator.Completion, StatementVisitor> {
    enum Completion {
        Normal, Return, Throw, Break, Continue, Abrupt;

        boolean isAbrupt() {
            return this != Normal;
        }

        Completion then(Completion next) {
            return this != Normal ? this : next;
        }

        Completion select(Completion other) {
            return this == Normal || other == Normal ? Normal : this == other ? this : Abrupt;
        }

        Completion normal(boolean b) {
            return b ? Normal : this;
        }
    }

    private static class Methods {
        // class: EnvironmentRecord
        static final MethodDesc EnvironmentRecord_createMutableBinding = MethodDesc.create(
                MethodType.Interface, Types.EnvironmentRecord, "createMutableBinding",
                Type.getMethodType(Type.VOID_TYPE, Types.String, Type.BOOLEAN_TYPE));

        static final MethodDesc EnvironmentRecord_createImmutableBinding = MethodDesc.create(
                MethodType.Interface, Types.EnvironmentRecord, "createImmutableBinding",
                Type.getMethodType(Type.VOID_TYPE, Types.String));

        // class: Iterator
        static final MethodDesc Iterator_hasNext = MethodDesc.create(MethodType.Interface,
                Types.Iterator, "hasNext", Type.getMethodType(Type.BOOLEAN_TYPE));

        static final MethodDesc Iterator_next = MethodDesc.create(MethodType.Interface,
                Types.Iterator, "next", Type.getMethodType(Types.Object));

        // class: LexicalEnvironment
        static final MethodDesc LexicalEnvironment_getEnvRec = MethodDesc.create(
                MethodType.Virtual, Types.LexicalEnvironment, "getEnvRec",
                Type.getMethodType(Types.EnvironmentRecord));

        // class: Reference
        static final MethodDesc Reference_PutValue = MethodDesc.create(MethodType.Virtual,
                Types.Reference, "PutValue",
                Type.getMethodType(Type.VOID_TYPE, Types.Object, Types.ExecutionContext));

        // class: ScriptException
        static final MethodDesc ScriptException_getValue = MethodDesc.create(MethodType.Virtual,
                Types.ScriptException, "getValue", Type.getMethodType(Types.Object));

        // class: ScriptRuntime
        static final MethodDesc ScriptRuntime_debugger = MethodDesc.create(MethodType.Static,
                Types.ScriptRuntime, "debugger", Type.getMethodType(Type.VOID_TYPE));

        static final MethodDesc ScriptRuntime_ensureObject = MethodDesc.create(MethodType.Static,
                Types.ScriptRuntime, "ensureObject",
                Type.getMethodType(Types.ScriptObject, Types.Object, Types.ExecutionContext));

        static final MethodDesc ScriptRuntime_enumerate = MethodDesc.create(MethodType.Static,
                Types.ScriptRuntime, "enumerate",
                Type.getMethodType(Types.Iterator, Types.Object, Types.ExecutionContext));

        static final MethodDesc ScriptRuntime_enumerateValues = MethodDesc.create(
                MethodType.Static, Types.ScriptRuntime, "enumerateValues",
                Type.getMethodType(Types.Iterator, Types.Object, Types.ExecutionContext));

        static final MethodDesc ScriptRuntime_GetUnscopables = MethodDesc.create(MethodType.Static,
                Types.ScriptRuntime, "GetUnscopables",
                Type.getMethodType(Types.Set, Types.ScriptObject, Types.ExecutionContext));

        static final MethodDesc ScriptRuntime_iterate = MethodDesc.create(MethodType.Static,
                Types.ScriptRuntime, "iterate",
                Type.getMethodType(Types.Iterator, Types.Object, Types.ExecutionContext));

        static final MethodDesc ScriptRuntime_throw = MethodDesc.create(MethodType.Static,
                Types.ScriptRuntime, "_throw",
                Type.getMethodType(Types.ScriptException, Types.Object));

        static final MethodDesc ScriptRuntime_toInternalError = MethodDesc.create(
                MethodType.Static, Types.ScriptRuntime, "toInternalError", Type.getMethodType(
                        Types.ScriptException, Types.StackOverflowError, Types.ExecutionContext));
    }

    public StatementGenerator(CodeGenerator codegen) {
        super(codegen);
    }

    /* ----------------------------------------------------------------------------------------- */

    /**
     * stack: [Reference, Object] -> []
     */
    private void PutValue(Expression node, ValType type, StatementVisitor mv) {
        assert type == ValType.Reference : "lhs is not reference: " + type;

        mv.loadExecutionContext();
        mv.invoke(Methods.Reference_PutValue);
    }

    /**
     * stack: [envRec] -> [envRec]
     */
    private void createImmutableBinding(String name, StatementVisitor mv) {
        mv.dup();
        mv.aconst(name);
        mv.invoke(Methods.EnvironmentRecord_createImmutableBinding);
    }

    /**
     * stack: [envRec] -> [envRec]
     */
    private void createMutableBinding(String name, boolean deletable, StatementVisitor mv) {
        mv.dup();
        mv.aconst(name);
        mv.iconst(deletable);
        mv.invoke(Methods.EnvironmentRecord_createMutableBinding);
    }

    @Override
    protected Completion visit(Node node, StatementVisitor mv) {
        throw new IllegalStateException(String.format("node-class: %s", node.getClass()));
    }

    @Override
    public Completion visit(BlockStatement node, StatementVisitor mv) {
        if (node.getStatements().isEmpty()) {
            // Block : { }
            // -> Return NormalCompletion(empty)
            return Completion.Normal;
        }

        mv.enterScope(node);
        Collection<Declaration> declarations = LexicalDeclarations(node);
        if (!declarations.isEmpty()) {
            newDeclarativeEnvironment(mv);
            new BlockDeclarationInstantiationGenerator(codegen).generate(declarations, mv);
            pushLexicalEnvironment(mv);
        }

        Completion result = Completion.Normal;
        for (StatementListItem statement : node.getStatements()) {
            if ((result = result.then(statement.accept(this, mv))).isAbrupt()) {
                break;
            }
        }

        if (!declarations.isEmpty() && !result.isAbrupt()) {
            popLexicalEnvironment(mv);
        }
        mv.exitScope();

        return result;
    }

    @Override
    public Completion visit(BreakStatement node, StatementVisitor mv) {
        mv.goTo(mv.breakLabel(node));
        return Completion.Break;
    }

    @Override
    public Completion visit(ClassDeclaration node, StatementVisitor mv) {
        ClassDefinitionEvaluation(node, null, mv);

        // stack: [lexEnv, value] -> []
        getEnvironmentRecord(mv);
        mv.swap();
        BindingInitialisationWithEnvironment(node.getName(), mv);

        return Completion.Normal;
    }

    @Override
    public Completion visit(ContinueStatement node, StatementVisitor mv) {
        mv.goTo(mv.continueLabel(node));
        return Completion.Continue;
    }

    @Override
    public Completion visit(DebuggerStatement node, StatementVisitor mv) {
        mv.lineInfo(node);
        mv.invoke(Methods.ScriptRuntime_debugger);
        return Completion.Normal;
    }

    @Override
    public Completion visit(DoWhileStatement node, StatementVisitor mv) {
        Label lblNext = new Label();
        JumpLabel lblContinue = new JumpLabel(), lblBreak = new JumpLabel();

        // L1: <statement>
        // IFNE ToBoolean(<expr>) L1

        Variable<LexicalEnvironment> savedEnv = saveEnvironment(node, mv);

        Completion result;
        mv.mark(lblNext);
        {
            mv.enterIteration(node, lblBreak, lblContinue);
            result = node.getStatement().accept(this, mv);
            mv.exitIteration(node);
        }
        mv.mark(lblContinue);
        if (lblContinue.isUsed()) {
            restoreEnvironment(node, Abrupt.Continue, savedEnv, mv);
        }

        if (!result.isAbrupt() || lblContinue.isUsed()) {
            ValType type = expressionValue(node.getTest(), mv);
            ToBoolean(type, mv);
            mv.ifne(lblNext);
        }

        mv.mark(lblBreak);
        if (lblBreak.isUsed()) {
            restoreEnvironment(node, Abrupt.Break, savedEnv, mv);
        }

        freeVariable(savedEnv, mv);

        return result.normal(lblContinue.isUsed() || lblBreak.isUsed());
    }

    @Override
    public Completion visit(EmptyStatement node, StatementVisitor mv) {
        // nothing to do!
        return Completion.Normal;
    }

    @Override
    public Completion visit(ExpressionStatement node, StatementVisitor mv) {
        ValType type = expressionValue(node.getExpression(), mv);
        mv.storeCompletionValueForScript(type);
        return Completion.Normal;
    }

    private enum IterationKind {
        Enumerate, Iterate, EnumerateValues
    }

    @Override
    public Completion visit(ForEachStatement node, StatementVisitor mv) {
        return visitForInOfLoop(node, node.getExpression(), node.getHead(), node.getStatement(),
                IterationKind.EnumerateValues, mv);
    }

    @Override
    public Completion visit(ForInStatement node, StatementVisitor mv) {
        return visitForInOfLoop(node, node.getExpression(), node.getHead(), node.getStatement(),
                IterationKind.Enumerate, mv);
    }

    @Override
    public Completion visit(ForOfStatement node, StatementVisitor mv) {
        return visitForInOfLoop(node, node.getExpression(), node.getHead(), node.getStatement(),
                IterationKind.Iterate, mv);
    }

    private <FORSTATEMENT extends IterationStatement & ScopedNode> Completion visitForInOfLoop(
            FORSTATEMENT node, Expression expr, Node lhs, Statement stmt,
            IterationKind iterationKind, StatementVisitor mv) {
        JumpLabel lblContinue = new JumpLabel(), lblBreak = new JumpLabel();
        Label loopstart = new Label(), loopbody = new Label();

        Variable<LexicalEnvironment> savedEnv = saveEnvironment(node, mv);

        // Runtime Semantics: For In/Of Expression Evaluation Abstract Operation
        ValType type = expressionValue(expr, mv);
        mv.toBoxed(type);

        mv.dup();
        isUndefinedOrNull(mv);
        mv.ifeq(loopstart);
        mv.pop();
        mv.goTo(lblBreak);
        mv.mark(loopstart);

        if ((iterationKind == IterationKind.Enumerate || iterationKind == IterationKind.EnumerateValues)
                && codegen.isEnabled(Parser.Option.LegacyGenerator)) {
            // legacy generator mode, both, for-in and for-each, perform Iterate on generators
            Label l0 = new Label(), l1 = new Label();
            mv.dup();
            mv.instanceOf(Types.GeneratorObject);
            mv.ifeq(l0);
            mv.loadExecutionContext();
            mv.invoke(Methods.ScriptRuntime_iterate);
            mv.goTo(l1);
            mv.mark(l0);
            mv.loadExecutionContext();
            if (iterationKind == IterationKind.Enumerate) {
                mv.invoke(Methods.ScriptRuntime_enumerate);
            } else {
                mv.invoke(Methods.ScriptRuntime_enumerateValues);
            }
            mv.mark(l1);
        } else {
            mv.loadExecutionContext();
            if (iterationKind == IterationKind.Enumerate) {
                mv.invoke(Methods.ScriptRuntime_enumerate);
            } else if (iterationKind == IterationKind.EnumerateValues) {
                mv.invoke(Methods.ScriptRuntime_enumerateValues);
            } else {
                assert iterationKind == IterationKind.Iterate;
                mv.invoke(Methods.ScriptRuntime_iterate);
            }
        }

        @SuppressWarnings("rawtypes")
        Variable<Iterator> iter = mv.newVariable("iter", Iterator.class);
        mv.store(iter);

        mv.goTo(lblContinue);

        mv.mark(loopbody);
        mv.load(iter);
        mv.invoke(Methods.Iterator_next);

        if (lhs instanceof Expression) {
            assert lhs instanceof LeftHandSideExpression;
            if (lhs instanceof AssignmentPattern) {
                ToObject(ValType.Any, mv);
                DestructuringAssignment((AssignmentPattern) lhs, mv);
            } else {
                ValType lhsType = expression((Expression) lhs, mv);
                mv.swap();
                PutValue((Expression) lhs, lhsType, mv);
            }
        } else if (lhs instanceof VariableStatement) {
            VariableDeclaration varDecl = ((VariableStatement) lhs).getElements().get(0);
            Binding binding = varDecl.getBinding();
            // Binding Instantiation: ForBinding
            if (binding instanceof BindingPattern) {
                ToObject(ValType.Any, mv);
            }
            BindingInitialisation(binding, mv);
        } else {
            assert lhs instanceof LexicalDeclaration;
            LexicalDeclaration lexDecl = (LexicalDeclaration) lhs;
            assert lexDecl.getElements().size() == 1;
            LexicalBinding lexicalBinding = lexDecl.getElements().get(0);

            // create new declarative lexical environment
            // stack: [nextValue] -> [nextValue, iterEnv]
            mv.enterScope(node);
            newDeclarativeEnvironment(mv);
            {
                // Runtime Semantics: Binding Instantiation
                // ForDeclaration : LetOrConst ForBinding

                // stack: [nextValue, iterEnv] -> [iterEnv, nextValue, envRec]
                mv.dupX1();
                mv.invoke(Methods.LexicalEnvironment_getEnvRec);

                // stack: [iterEnv, nextValue, envRec] -> [iterEnv, envRec, nextValue]
                for (String name : BoundNames(lexicalBinding.getBinding())) {
                    if (IsConstantDeclaration(lexDecl)) {
                        // FIXME: spec bug (CreateImmutableBinding concrete method of `env`)
                        createImmutableBinding(name, mv);
                    } else {
                        // FIXME: spec bug (CreateMutableBinding concrete method of `env`)
                        createMutableBinding(name, false, mv);
                    }
                }
                mv.swap();

                // Binding Instantiation: ForBinding
                if (lexicalBinding.getBinding() instanceof BindingPattern) {
                    ToObject(ValType.Any, mv);
                }

                // stack: [iterEnv, envRec, nextValue] -> [iterEnv]
                BindingInitialisationWithEnvironment(lexicalBinding.getBinding(), mv);
            }
            // stack: [iterEnv] -> []
            pushLexicalEnvironment(mv);
        }

        Completion result;
        {
            mv.enterIteration(node, lblBreak, lblContinue);
            result = stmt.accept(this, mv);
            mv.exitIteration(node);
        }

        if (lhs instanceof LexicalDeclaration) {
            // restore previous lexical environment
            if (!result.isAbrupt()) {
                popLexicalEnvironment(mv);
            }
            mv.exitScope();
        }

        mv.mark(lblContinue);
        if (lblContinue.isUsed()) {
            restoreEnvironment(node, Abrupt.Continue, savedEnv, mv);
        }

        mv.load(iter);
        mv.invoke(Methods.Iterator_hasNext);
        mv.ifne(loopbody);

        mv.mark(lblBreak);
        if (lblBreak.isUsed()) {
            restoreEnvironment(node, Abrupt.Break, savedEnv, mv);
        }

        mv.freeVariable(iter);
        freeVariable(savedEnv, mv);

        return result.normal(lblContinue.isUsed() || lblBreak.isUsed()).select(Completion.Normal);
    }

    @Override
    public Completion visit(ForStatement node, StatementVisitor mv) {
        Node head = node.getHead();
        if (head == null) {
            // empty
        } else if (head instanceof Expression) {
            ValType type = expressionValue((Expression) head, mv);
            mv.pop(type);
        } else if (head instanceof VariableStatement) {
            head.accept(this, mv);
        } else {
            assert head instanceof LexicalDeclaration;
            LexicalDeclaration lexDecl = (LexicalDeclaration) head;

            mv.enterScope(node);
            newDeclarativeEnvironment(mv);
            {
                // stack: [loopEnv] -> [loopEnv, envRec]
                mv.dup();
                mv.invoke(Methods.LexicalEnvironment_getEnvRec);

                boolean isConst = IsConstantDeclaration(lexDecl);
                for (String dn : BoundNames(lexDecl)) {
                    if (isConst) {
                        // FIXME: spec bug (CreateImmutableBinding concrete method of `loopEnv`)
                        createImmutableBinding(dn, mv);
                    } else {
                        // FIXME: spec bug (CreateMutableBinding concrete method of `loopEnv`)
                        createMutableBinding(dn, false, mv);
                    }
                }
                mv.pop();
            }
            pushLexicalEnvironment(mv);

            lexDecl.accept(this, mv);
        }

        Variable<LexicalEnvironment> savedEnv = saveEnvironment(node, mv);

        Label lblTest = new Label(), lblStmt = new Label();
        JumpLabel lblContinue = new JumpLabel(), lblBreak = new JumpLabel();

        Completion result;
        mv.goTo(lblTest);
        mv.mark(lblStmt);
        {
            mv.enterIteration(node, lblBreak, lblContinue);
            result = node.getStatement().accept(this, mv);
            mv.exitIteration(node);
        }
        mv.mark(lblContinue);
        if (lblContinue.isUsed()) {
            restoreEnvironment(node, Abrupt.Continue, savedEnv, mv);
        }

        if (node.getStep() != null && (!result.isAbrupt() || lblContinue.isUsed())) {
            ValType type = expressionValue(node.getStep(), mv);
            mv.pop(type);
        }

        mv.mark(lblTest);
        if (node.getTest() != null) {
            ValType type = expressionValue(node.getTest(), mv);
            ToBoolean(type, mv);
            mv.ifne(lblStmt);
        } else {
            mv.goTo(lblStmt);
        }

        mv.mark(lblBreak);
        if (lblBreak.isUsed()) {
            restoreEnvironment(node, Abrupt.Break, savedEnv, mv);
        }

        freeVariable(savedEnv, mv);

        if (head instanceof LexicalDeclaration) {
            if (node.getTest() != null || lblBreak.isUsed()) {
                popLexicalEnvironment(mv);
            }
            mv.exitScope();
        }

        if (node.getTest() == null) {
            if (!result.isAbrupt() && !lblBreak.isUsed()) {
                return Completion.Abrupt; // infinite loop
            }
            return result.normal(lblBreak.isUsed());
        }
        return result.normal(lblContinue.isUsed() || lblBreak.isUsed()).select(Completion.Normal);
    }

    @Override
    public Completion visit(FunctionDeclaration node, StatementVisitor mv) {
        codegen.compile(node);

        // Runtime Semantics: Evaluation -> FunctionDeclaration
        /* return NormalCompletion(empty) */

        return Completion.Normal;
    }

    @Override
    public Completion visit(GeneratorDeclaration node, StatementVisitor mv) {
        codegen.compile(node);

        // Runtime Semantics: Evaluation -> GeneratorDeclaration
        /* return NormalCompletion(empty) */

        return Completion.Normal;
    }

    @Override
    public Completion visit(IfStatement node, StatementVisitor mv) {
        Label l0 = new Label(), l1 = new Label();

        ValType type = expressionValue(node.getTest(), mv);
        ToBoolean(type, mv);
        mv.ifeq(l0);
        Completion resultThen = node.getThen().accept(this, mv);
        if (node.getOtherwise() != null) {
            if (!resultThen.isAbrupt()) {
                mv.goTo(l1);
            }
            mv.mark(l0);
            Completion resultOtherwise = node.getOtherwise().accept(this, mv);
            mv.mark(l1);
            return resultThen.select(resultOtherwise);
        } else {
            mv.mark(l0);
            return resultThen.select(Completion.Normal);
        }
    }

    @Override
    public Completion visit(LabelledStatement node, StatementVisitor mv) {
        Variable<LexicalEnvironment> savedEnv = saveEnvironment(node, mv);

        JumpLabel label = new JumpLabel();
        mv.enterLabelled(node, label);
        Completion result = node.getStatement().accept(this, mv);
        mv.exitLabelled(node);
        mv.mark(label);

        if (label.isUsed()) {
            restoreEnvironment(node, Abrupt.Break, savedEnv, mv);
        }
        freeVariable(savedEnv, mv);

        return result.normal(label.isUsed());
    }

    @Override
    public Completion visit(LexicalDeclaration node, StatementVisitor mv) {
        for (LexicalBinding binding : node.getElements()) {
            binding.accept(this, mv);
        }

        return Completion.Normal;
    }

    @Override
    public Completion visit(LetStatement node, StatementVisitor mv) {
        // create new declarative lexical environment
        // stack: [] -> [env]
        mv.enterScope(node);
        newDeclarativeEnvironment(mv);
        {
            // stack: [env] -> [env, envRec]
            mv.dup();
            mv.invoke(Methods.LexicalEnvironment_getEnvRec);

            // stack: [env, envRec] -> [env]
            for (LexicalBinding binding : node.getBindings()) {
                // stack: [env, envRec] -> [env, envRec, envRec]
                mv.dup();

                // stack: [env, envRec, envRec] -> [env, envRec, envRec]
                for (String name : BoundNames(binding.getBinding())) {
                    createMutableBinding(name, false, mv);
                }

                Expression initialiser = binding.getInitialiser();
                if (initialiser != null) {
                    ValType type = expressionValue(initialiser, mv);
                    if (binding.getBinding() instanceof BindingPattern) {
                        ToObject(type, mv);
                    } else {
                        mv.toBoxed(type);
                    }
                } else {
                    assert binding.getBinding() instanceof BindingIdentifier;
                    mv.loadUndefined();
                }

                // stack: [env, envRec, envRec, value] -> [env, envRec]
                BindingInitialisationWithEnvironment(binding.getBinding(), mv);
            }
            mv.pop();
        }
        // stack: [env] -> []
        pushLexicalEnvironment(mv);

        Completion result = node.getStatement().accept(this, mv);

        // restore previous lexical environment
        if (!result.isAbrupt()) {
            popLexicalEnvironment(mv);
        }
        mv.exitScope();

        return result;
    }

    @Override
    public Completion visit(LexicalBinding node, StatementVisitor mv) {
        Binding binding = node.getBinding();
        Expression initialiser = node.getInitialiser();
        if (initialiser != null) {
            ValType type = expressionValue(initialiser, mv);
            mv.toBoxed(type);
            if (binding instanceof BindingPattern && type != ValType.Object) {
                mv.loadExecutionContext();
                mv.invoke(Methods.ScriptRuntime_ensureObject);
            }
        } else {
            assert binding instanceof BindingIdentifier;
            mv.loadUndefined();
        }

        getEnvironmentRecord(mv);
        mv.swap();
        BindingInitialisationWithEnvironment(binding, mv);

        return Completion.Normal;
    }

    @Override
    public Completion visit(ReturnStatement node, StatementVisitor mv) {
        Expression expr = node.getExpression();
        if (expr != null) {
            if (!mv.isWrapped()) {
                mv.setTailCall(TailCallNodes(expr));
            }
            ValType type = expressionValue(expr, mv);
            mv.toBoxed(type);
            if (!mv.isWrapped()) {
                mv.setTailCall(TailCallNodes(null));
            }
        } else {
            mv.loadUndefined();
        }
        mv.storeCompletionValue();
        mv.goTo(mv.returnLabel());

        return Completion.Return;
    }

    @Override
    public Completion visit(StatementListMethod node, StatementVisitor mv) {
        codegen.compile(node, mv);

        mv.lineInfo(0);
        mv.loadExecutionContext();
        mv.loadCompletionValue();

        String desc = Type.getMethodDescriptor(Types.Object, Types.ExecutionContext, Types.Object);
        mv.invokestatic(codegen.getClassName(), codegen.methodName(node), desc);

        if (mv.getCodeType() == StatementVisitor.CodeType.Function) {
            // TODO: only emit when `return` used in StmtListMethod
            Label noReturn = new Label();
            mv.dup();
            mv.ifnull(noReturn);
            mv.storeCompletionValue();
            mv.goTo(mv.returnLabel());
            mv.mark(noReturn);
            mv.pop();
        } else {
            mv.storeCompletionValueForScript(ValType.Any);
        }

        return Completion.Normal; // TODO: return correct result
    }

    @Override
    public Completion visit(SwitchStatement node, StatementVisitor mv) {
        return node.accept(new SwitchStatementGenerator(codegen), mv);
    }

    @Override
    public Completion visit(ThrowStatement node, StatementVisitor mv) {
        ValType type = expressionValue(node.getExpression(), mv);
        mv.toBoxed(type);
        mv.invoke(Methods.ScriptRuntime_throw);
        mv.athrow();

        return Completion.Throw;
    }

    @Override
    public Completion visit(TryStatement node, StatementVisitor mv) {
        // NB: nop() instruction are inserted to ensure no empty blocks will be generated

        BlockStatement tryBlock = node.getTryBlock();
        CatchNode catchNode = node.getCatchNode();
        List<GuardedCatchNode> guardedCatchNodes = node.getGuardedCatchNodes();
        BlockStatement finallyBlock = node.getFinallyBlock();

        if ((catchNode != null || !guardedCatchNodes.isEmpty()) && finallyBlock != null) {
            Label startCatchFinally = new Label();
            Label endCatch = new Label(), handlerCatch = new Label();
            Label endFinally = new Label(), handlerFinally = new Label();
            Label handlerCatchStackOverflow = new Label();
            Label handlerFinallyStackOverflow = new Label();
            Label noException = new Label();
            Label exceptionHandled = new Label();

            mv.enterFinallyScoped();

            Variable<LexicalEnvironment> savedEnv = saveEnvironment(mv);
            mv.mark(startCatchFinally);
            mv.enterWrapped();
            Completion tryResult = tryBlock.accept(this, mv);
            if (!tryResult.isAbrupt()) {
                mv.nop();
                mv.goTo(noException);
            }
            mv.exitWrapped();
            mv.mark(endCatch);

            // StackOverflowError -> ScriptException
            mv.mark(handlerCatchStackOverflow);
            mv.loadExecutionContext();
            mv.invoke(Methods.ScriptRuntime_toInternalError);

            Completion catchResult;
            mv.mark(handlerCatch);
            restoreEnvironment(mv, savedEnv);
            mv.enterWrapped();
            if (!guardedCatchNodes.isEmpty()) {
                mv.enterCatchWithGuarded(node, new Label());

                Variable<ScriptException> exception = mv.newVariable("exception",
                        ScriptException.class);
                mv.store(exception);
                Completion result = null;
                for (GuardedCatchNode guardedCatchNode : guardedCatchNodes) {
                    mv.load(exception);
                    Completion guardedResult = guardedCatchNode.accept(this, mv);
                    result = result != null ? result.select(guardedResult) : guardedResult;
                }
                assert result != null;
                if (catchNode != null) {
                    mv.load(exception);
                    catchResult = catchNode.accept(this, mv);
                } else {
                    mv.load(exception);
                    mv.athrow();
                    catchResult = Completion.Throw;
                }

                mv.freeVariable(exception);
                mv.mark(mv.catchWithGuardedLabel());
                mv.exitCatchWithGuarded(node);

                catchResult = catchResult.select(result);
            } else {
                catchResult = catchNode.accept(this, mv);
            }
            mv.exitWrapped();
            mv.mark(endFinally);

            // restore temp abrupt targets
            List<TempLabel> tempLabels = mv.exitFinallyScoped();

            // various finally blocks
            if (!catchResult.isAbrupt()) {
                mv.enterFinally();
                Completion finallyResult = finallyBlock.accept(this, mv);
                mv.exitFinally();
                if (!finallyResult.isAbrupt()) {
                    mv.goTo(exceptionHandled);
                }
            }

            mv.mark(handlerFinallyStackOverflow);
            mv.mark(handlerFinally);
            Variable<Throwable> throwable = mv.newVariable("throwable", Throwable.class);
            mv.store(throwable);
            restoreEnvironment(mv, savedEnv);
            mv.enterFinally();
            Completion finallyResult = finallyBlock.accept(this, mv);
            mv.exitFinally();
            if (!finallyResult.isAbrupt()) {
                mv.load(throwable);
                mv.athrow();
            }
            mv.freeVariable(throwable);

            if (!tryResult.isAbrupt()) {
                mv.mark(noException);
                mv.enterFinally();
                finallyBlock.accept(this, mv);
                mv.exitFinally();
                if (!finallyResult.isAbrupt()) {
                    mv.goTo(exceptionHandled);
                }
            }

            // abrupt completion (return, break, continue) finally blocks
            for (TempLabel temp : tempLabels) {
                mv.mark(temp);
                restoreEnvironment(mv, savedEnv);
                mv.enterFinally();
                finallyBlock.accept(this, mv);
                mv.exitFinally();
                if (!finallyResult.isAbrupt()) {
                    mv.goTo(temp.getActual().mark());
                }
            }

            mv.mark(exceptionHandled);

            mv.freeVariable(savedEnv);
            mv.visitTryCatchBlock(startCatchFinally, endCatch, handlerCatch,
                    Types.ScriptException.getInternalName());
            mv.visitTryCatchBlock(startCatchFinally, endCatch, handlerCatchStackOverflow,
                    Types.StackOverflowError.getInternalName());
            mv.visitTryCatchBlock(startCatchFinally, endFinally, handlerFinally,
                    Types.ScriptException.getInternalName());
            mv.visitTryCatchBlock(startCatchFinally, endFinally, handlerFinallyStackOverflow,
                    Types.StackOverflowError.getInternalName());

            return finallyResult.then(tryResult.select(catchResult));
        } else if (catchNode != null || !guardedCatchNodes.isEmpty()) {
            Label startCatch = new Label(), endCatch = new Label(), handlerCatch = new Label();
            Label handlerCatchStackOverflow = new Label();
            Label exceptionHandled = new Label();

            Variable<LexicalEnvironment> savedEnv = saveEnvironment(mv);
            mv.mark(startCatch);
            mv.enterWrapped();
            Completion tryResult = tryBlock.accept(this, mv);
            if (!tryResult.isAbrupt()) {
                mv.nop();
                mv.goTo(exceptionHandled);
            }
            mv.exitWrapped();
            mv.mark(endCatch);

            // StackOverflowError -> ScriptException
            mv.mark(handlerCatchStackOverflow);
            mv.loadExecutionContext();
            mv.invoke(Methods.ScriptRuntime_toInternalError);

            Completion catchResult;
            mv.mark(handlerCatch);
            restoreEnvironment(mv, savedEnv);
            if (!guardedCatchNodes.isEmpty()) {
                mv.enterCatchWithGuarded(node, new Label());

                Variable<ScriptException> exception = mv.newVariable("exception",
                        ScriptException.class);
                mv.store(exception);
                Completion result = null;
                for (GuardedCatchNode guardedCatchNode : guardedCatchNodes) {
                    mv.load(exception);
                    Completion guardedResult = guardedCatchNode.accept(this, mv);
                    result = result != null ? result.select(guardedResult) : guardedResult;
                }
                assert result != null;
                if (catchNode != null) {
                    mv.load(exception);
                    catchResult = catchNode.accept(this, mv);
                } else {
                    mv.load(exception);
                    mv.athrow();
                    catchResult = Completion.Throw;
                }

                mv.freeVariable(exception);
                mv.mark(mv.catchWithGuardedLabel());
                mv.exitCatchWithGuarded(node);

                catchResult = catchResult.select(result);
            } else {
                catchResult = catchNode.accept(this, mv);
            }
            mv.mark(exceptionHandled);

            mv.freeVariable(savedEnv);
            mv.visitTryCatchBlock(startCatch, endCatch, handlerCatch,
                    Types.ScriptException.getInternalName());
            mv.visitTryCatchBlock(startCatch, endCatch, handlerCatchStackOverflow,
                    Types.StackOverflowError.getInternalName());

            return tryResult.select(catchResult);
        } else {
            assert finallyBlock != null;
            Label startFinally = new Label(), endFinally = new Label(), handlerFinally = new Label();
            Label handlerFinallyStackOverflow = new Label();
            Label noException = new Label();
            Label exceptionHandled = new Label();

            mv.enterFinallyScoped();

            Variable<LexicalEnvironment> savedEnv = saveEnvironment(mv);
            mv.mark(startFinally);
            mv.enterWrapped();
            Completion tryResult = tryBlock.accept(this, mv);
            if (!tryResult.isAbrupt()) {
                mv.nop();
                mv.goTo(noException);
            }
            mv.exitWrapped();
            mv.mark(endFinally);

            // restore temp abrupt targets
            List<TempLabel> tempLabels = mv.exitFinallyScoped();

            mv.mark(handlerFinallyStackOverflow);
            mv.mark(handlerFinally);
            Variable<Throwable> throwable = mv.newVariable("throwable", Throwable.class);
            mv.store(throwable);
            restoreEnvironment(mv, savedEnv);
            mv.enterFinally();
            Completion finallyResult = finallyBlock.accept(this, mv);
            mv.exitFinally();
            if (!finallyResult.isAbrupt()) {
                mv.load(throwable);
                mv.athrow();
            }
            mv.freeVariable(throwable);

            if (!tryResult.isAbrupt()) {
                mv.mark(noException);
                mv.enterFinally();
                finallyBlock.accept(this, mv);
                mv.exitFinally();
                if (!finallyResult.isAbrupt()) {
                    mv.goTo(exceptionHandled);
                }
            }

            // abrupt completion (return, break, continue) finally blocks
            for (TempLabel temp : tempLabels) {
                mv.mark(temp);
                restoreEnvironment(mv, savedEnv);
                mv.enterFinally();
                finallyBlock.accept(this, mv);
                mv.exitFinally();
                if (!finallyResult.isAbrupt()) {
                    mv.goTo(temp.getActual().mark());
                }
            }

            mv.mark(exceptionHandled);

            mv.freeVariable(savedEnv);
            mv.visitTryCatchBlock(startFinally, endFinally, handlerFinally,
                    Types.ScriptException.getInternalName());
            mv.visitTryCatchBlock(startFinally, endFinally, handlerFinallyStackOverflow,
                    Types.StackOverflowError.getInternalName());

            return finallyResult.then(tryResult);
        }
    }

    @Override
    public Completion visit(CatchNode node, StatementVisitor mv) {
        Binding catchParameter = node.getCatchParameter();
        BlockStatement catchBlock = node.getCatchBlock();

        // stack: [e] -> [ex]
        mv.invoke(Methods.ScriptException_getValue);

        // create new declarative lexical environment
        // stack: [ex] -> [ex, catchEnv]
        mv.enterScope(node);
        newDeclarativeEnvironment(mv);
        {
            // stack: [ex, catchEnv] -> [catchEnv, ex, envRec]
            mv.dupX1();
            mv.invoke(Methods.LexicalEnvironment_getEnvRec);

            // FIXME: spec bug (CreateMutableBinding concrete method of `catchEnv`)
            // [catchEnv, ex, envRec] -> [catchEnv, envRec, ex]
            for (String name : BoundNames(catchParameter)) {
                createMutableBinding(name, false, mv);
            }
            mv.swap();

            if (catchParameter instanceof BindingPattern) {
                // ToObject(...)
                ToObject(ValType.Any, mv);
            }

            // stack: [catchEnv, envRec, ex] -> [catchEnv]
            BindingInitialisationWithEnvironment(catchParameter, mv);
        }
        // stack: [catchEnv] -> []
        pushLexicalEnvironment(mv);

        Completion result = catchBlock.accept(this, mv);

        // restore previous lexical environment
        if (!result.isAbrupt()) {
            popLexicalEnvironment(mv);
        }
        mv.exitScope();

        return result;
    }

    @Override
    public Completion visit(GuardedCatchNode node, StatementVisitor mv) {
        Binding catchParameter = node.getCatchParameter();
        BlockStatement catchBlock = node.getCatchBlock();

        // stack: [e] -> [ex]
        mv.invoke(Methods.ScriptException_getValue);

        // create new declarative lexical environment
        // stack: [ex] -> [ex, catchEnv]
        mv.enterScope(node);
        newDeclarativeEnvironment(mv);
        {
            // stack: [ex, catchEnv] -> [catchEnv, ex, envRec]
            mv.dupX1();
            mv.invoke(Methods.LexicalEnvironment_getEnvRec);

            // FIXME: spec bug (CreateMutableBinding concrete method of `catchEnv`)
            // [catchEnv, ex, envRec] -> [catchEnv, envRec, ex]
            for (String name : BoundNames(catchParameter)) {
                createMutableBinding(name, false, mv);
            }
            mv.swap();

            if (catchParameter instanceof BindingPattern) {
                // ToObject(...)
                ToObject(ValType.Any, mv);
            }

            // stack: [catchEnv, envRec, ex] -> [catchEnv]
            BindingInitialisationWithEnvironment(catchParameter, mv);
        }
        // stack: [catchEnv] -> []
        pushLexicalEnvironment(mv);

        Completion result;
        Label l0 = new Label();
        ToBoolean(expressionValue(node.getGuard(), mv), mv);
        mv.ifeq(l0);
        {
            result = catchBlock.accept(this, mv);

            // restore previous lexical environment and go to end of catch block
            if (!result.isAbrupt()) {
                popLexicalEnvironment(mv);
                mv.goTo(mv.catchWithGuardedLabel());
            }
        }
        mv.mark(l0);

        // restore previous lexical environment
        popLexicalEnvironment(mv);
        mv.exitScope();

        return result;
    }

    @Override
    public Completion visit(VariableDeclaration node, StatementVisitor mv) {
        Binding binding = node.getBinding();
        Expression initialiser = node.getInitialiser();
        if (initialiser != null) {
            ValType type = expressionValue(initialiser, mv);
            mv.toBoxed(type);
            if (binding instanceof BindingPattern && type != ValType.Object) {
                mv.loadExecutionContext();
                mv.invoke(Methods.ScriptRuntime_ensureObject);
            }
            BindingInitialisation(binding, mv);
        } else {
            assert binding instanceof BindingIdentifier;
        }
        return Completion.Normal;
    }

    @Override
    public Completion visit(VariableStatement node, StatementVisitor mv) {
        for (VariableDeclaration decl : node.getElements()) {
            decl.accept(this, mv);
        }
        return Completion.Normal;
    }

    @Override
    public Completion visit(WhileStatement node, StatementVisitor mv) {
        Label lblNext = new Label();
        JumpLabel lblContinue = new JumpLabel(), lblBreak = new JumpLabel();

        Variable<LexicalEnvironment> savedEnv = saveEnvironment(node, mv);

        Completion result;
        mv.goTo(lblContinue);
        mv.mark(lblNext);
        {
            mv.enterIteration(node, lblBreak, lblContinue);
            result = node.getStatement().accept(this, mv);
            mv.exitIteration(node);
        }
        mv.mark(lblContinue);
        if (lblContinue.isUsed()) {
            restoreEnvironment(node, Abrupt.Continue, savedEnv, mv);
        }

        ValType type = expressionValue(node.getTest(), mv);
        ToBoolean(type, mv);
        mv.ifne(lblNext);

        mv.mark(lblBreak);
        if (lblBreak.isUsed()) {
            restoreEnvironment(node, Abrupt.Break, savedEnv, mv);
        }

        freeVariable(savedEnv, mv);

        return result.normal(lblContinue.isUsed() || lblBreak.isUsed()).select(Completion.Normal);
    }

    @Override
    public Completion visit(WithStatement node, StatementVisitor mv) {
        // with(<Expression>)
        ValType type = expressionValue(node.getExpression(), mv);

        // ToObject(<Expression>)
        ToObject(type, mv);

        // retrieve object's @@unscopables list
        mv.dup();
        mv.loadExecutionContext();
        mv.invoke(Methods.ScriptRuntime_GetUnscopables);

        // create new object lexical environment (withEnvironment-flag = true)
        mv.enterScope(node);
        newObjectEnvironment(mv, true); // withEnvironment-flag = true
        pushLexicalEnvironment(mv);

        Completion result = node.getStatement().accept(this, mv);

        // restore previous lexical environment
        if (!result.isAbrupt()) {
            popLexicalEnvironment(mv);
        }
        mv.exitScope();

        return result;
    }
}
