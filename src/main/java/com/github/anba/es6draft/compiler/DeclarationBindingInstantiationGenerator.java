/**
 * Copyright (c) 2012-2015 André Bargull
 * Alle Rechte vorbehalten / All Rights Reserved.  Use is subject to license terms.
 *
 * <https://github.com/anba/es6draft>
 */
package com.github.anba.es6draft.compiler;

import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

import com.github.anba.es6draft.ast.AsyncFunctionDeclaration;
import com.github.anba.es6draft.ast.Declaration;
import com.github.anba.es6draft.ast.FunctionDeclaration;
import com.github.anba.es6draft.ast.GeneratorDeclaration;
import com.github.anba.es6draft.ast.HoistableDeclaration;
import com.github.anba.es6draft.ast.LegacyGeneratorDeclaration;
import com.github.anba.es6draft.ast.Node;
import com.github.anba.es6draft.ast.VariableDeclaration;
import com.github.anba.es6draft.ast.scope.Name;
import com.github.anba.es6draft.compiler.CodeGenerator.FunctionName;
import com.github.anba.es6draft.compiler.assembler.MethodName;
import com.github.anba.es6draft.compiler.assembler.Type;
import com.github.anba.es6draft.compiler.assembler.Variable;
import com.github.anba.es6draft.runtime.EnvironmentRecord;
import com.github.anba.es6draft.runtime.ExecutionContext;
import com.github.anba.es6draft.runtime.GlobalEnvironmentRecord;
import com.github.anba.es6draft.runtime.LexicalEnvironment;
import com.github.anba.es6draft.runtime.internal.RuntimeInfo;
import com.github.anba.es6draft.runtime.internal.ScriptRuntime;

/**
 * Base class for Binding Instantiation generators
 */
abstract class DeclarationBindingInstantiationGenerator {
    private static final class Methods {
        // class: ExecutionContext
        static final MethodName ExecutionContext_getLexicalEnvironment = MethodName.findVirtual(
                Types.ExecutionContext, "getLexicalEnvironment",
                Type.methodType(Types.LexicalEnvironment));

        static final MethodName ExecutionContext_getVariableEnvironment = MethodName.findVirtual(
                Types.ExecutionContext, "getVariableEnvironment",
                Type.methodType(Types.LexicalEnvironment));

        // class: EnvironmentRecord
        static final MethodName EnvironmentRecord_hasBinding = MethodName.findInterface(
                Types.EnvironmentRecord, "hasBinding",
                Type.methodType(Type.BOOLEAN_TYPE, Types.String));

        static final MethodName EnvironmentRecord_createMutableBinding = MethodName.findInterface(
                Types.EnvironmentRecord, "createMutableBinding",
                Type.methodType(Type.VOID_TYPE, Types.String, Type.BOOLEAN_TYPE));

        static final MethodName EnvironmentRecord_createImmutableBinding = MethodName
                .findInterface(Types.EnvironmentRecord, "createImmutableBinding",
                        Type.methodType(Type.VOID_TYPE, Types.String, Type.BOOLEAN_TYPE));

        static final MethodName EnvironmentRecord_initializeBinding = MethodName.findInterface(
                Types.EnvironmentRecord, "initializeBinding",
                Type.methodType(Type.VOID_TYPE, Types.String, Types.Object));

        static final MethodName EnvironmentRecord_setMutableBinding = MethodName.findInterface(
                Types.EnvironmentRecord, "setMutableBinding",
                Type.methodType(Type.VOID_TYPE, Types.String, Types.Object, Type.BOOLEAN_TYPE));

        static final MethodName EnvironmentRecord_getBindingValue = MethodName.findInterface(
                Types.EnvironmentRecord, "getBindingValue",
                Type.methodType(Types.Object, Types.String, Type.BOOLEAN_TYPE));

        // class: GlobalEnvironmentRecord
        static final MethodName GlobalEnvironmentRecord_createGlobalVarBinding = MethodName
                .findVirtual(Types.GlobalEnvironmentRecord, "createGlobalVarBinding",
                        Type.methodType(Type.VOID_TYPE, Types.String, Type.BOOLEAN_TYPE));

        static final MethodName GlobalEnvironmentRecord_createGlobalFunctionBinding = MethodName
                .findVirtual(Types.GlobalEnvironmentRecord, "createGlobalFunctionBinding", Type
                        .methodType(Type.VOID_TYPE, Types.String, Types.Object, Type.BOOLEAN_TYPE));

        // class: LexicalEnvironment
        static final MethodName LexicalEnvironment_getEnvRec = MethodName.findVirtual(
                Types.LexicalEnvironment, "getEnvRec", Type.methodType(Types.EnvironmentRecord));

        // class: ScriptRuntime
        static final MethodName ScriptRuntime_canDeclareGlobalFunctionOrThrow = MethodName
                .findStatic(Types.ScriptRuntime, "canDeclareGlobalFunctionOrThrow", Type
                        .methodType(Type.VOID_TYPE, Types.ExecutionContext,
                                Types.GlobalEnvironmentRecord, Types.String));

        static final MethodName ScriptRuntime_canDeclareGlobalVarOrThrow = MethodName.findStatic(
                Types.ScriptRuntime, "canDeclareGlobalVarOrThrow", Type.methodType(Type.VOID_TYPE,
                        Types.ExecutionContext, Types.GlobalEnvironmentRecord, Types.String));

        static final MethodName ScriptRuntime_canDeclareLexicalScopedOrThrow = MethodName
                .findStatic(Types.ScriptRuntime, "canDeclareLexicalScopedOrThrow", Type.methodType(
                        Type.VOID_TYPE, Types.ExecutionContext, Types.GlobalEnvironmentRecord,
                        Types.String));

        static final MethodName ScriptRuntime_canDeclareVarScopedOrThrow = MethodName.findStatic(
                Types.ScriptRuntime, "canDeclareVarScopedOrThrow", Type.methodType(Type.VOID_TYPE,
                        Types.ExecutionContext, Types.GlobalEnvironmentRecord, Types.String));

        static final MethodName ScriptRuntime_InstantiateAsyncFunctionObject = MethodName
                .findStatic(Types.ScriptRuntime, "InstantiateAsyncFunctionObject", Type.methodType(
                        Types.OrdinaryAsyncFunction, Types.LexicalEnvironment,
                        Types.ExecutionContext, Types.RuntimeInfo$Function));

        static final MethodName ScriptRuntime_InstantiateFunctionObject = MethodName.findStatic(
                Types.ScriptRuntime, "InstantiateFunctionObject", Type.methodType(
                        Types.OrdinaryConstructorFunction, Types.LexicalEnvironment,
                        Types.ExecutionContext, Types.RuntimeInfo$Function));

        static final MethodName ScriptRuntime_InstantiateGeneratorObject = MethodName.findStatic(
                Types.ScriptRuntime, "InstantiateGeneratorObject", Type.methodType(
                        Types.OrdinaryGenerator, Types.LexicalEnvironment, Types.ExecutionContext,
                        Types.RuntimeInfo$Function));

        static final MethodName ScriptRuntime_InstantiateLegacyGeneratorObject = MethodName
                .findStatic(Types.ScriptRuntime, "InstantiateLegacyGeneratorObject", Type
                        .methodType(Types.OrdinaryGenerator, Types.LexicalEnvironment,
                                Types.ExecutionContext, Types.RuntimeInfo$Function));
    }

    protected final CodeGenerator codegen;

    protected DeclarationBindingInstantiationGenerator(CodeGenerator codegen) {
        this.codegen = codegen;
    }

    /**
     * Emit function call for: {@link EnvironmentRecord#hasBinding(String)}
     * <p>
     * stack: [] {@literal ->} [boolean]
     * 
     * @param envRec
     *            the variable which holds the environment record
     * @param name
     *            the binding name
     * @param mv
     *            the instruction visitor
     */
    protected final void hasBinding(Variable<? extends EnvironmentRecord> envRec, Name name,
            InstructionVisitor mv) {
        mv.load(envRec);
        mv.aconst(name.getIdentifier());
        mv.invoke(Methods.EnvironmentRecord_hasBinding);
    }

    /**
     * Emit function call for: {@link EnvironmentRecord#createMutableBinding(String, boolean)}
     * <p>
     * stack: [] {@literal ->} []
     * 
     * @param envRec
     *            the variable which holds the environment record
     * @param node
     *            the declaration node
     * @param name
     *            the binding name
     * @param deletable
     *            the deletable flag
     * @param mv
     *            the instruction visitor
     */
    protected final void createMutableBinding(Variable<? extends EnvironmentRecord> envRec,
            Node node, Name name, boolean deletable, InstructionVisitor mv) {
        mv.load(envRec);
        mv.aconst(name.getIdentifier());
        mv.iconst(deletable);
        mv.lineInfo(node);
        mv.invoke(Methods.EnvironmentRecord_createMutableBinding);
    }

    /**
     * Emit function call for: {@link EnvironmentRecord#createMutableBinding(String, boolean)}
     * <p>
     * stack: [] {@literal ->} []
     * 
     * @param envRec
     *            the variable which holds the environment record
     * @param name
     *            the binding name
     * @param deletable
     *            the deletable flag
     * @param mv
     *            the instruction visitor
     */
    protected final void createMutableBinding(Variable<? extends EnvironmentRecord> envRec,
            Name name, boolean deletable, InstructionVisitor mv) {
        mv.load(envRec);
        mv.aconst(name.getIdentifier());
        mv.iconst(deletable);
        mv.invoke(Methods.EnvironmentRecord_createMutableBinding);
    }

    /**
     * Emit function call for: {@link EnvironmentRecord#createImmutableBinding(String, boolean)}
     * <p>
     * stack: [] {@literal ->} []
     * 
     * @param envRec
     *            the variable which holds the environment record
     * @param node
     *            the declaration node
     * @param name
     *            the binding name
     * @param mv
     *            the instruction visitor
     */
    protected final void createImmutableBinding(Variable<? extends EnvironmentRecord> envRec,
            Node node, Name name, boolean strict, InstructionVisitor mv) {
        mv.load(envRec);
        mv.aconst(name.getIdentifier());
        mv.iconst(strict);
        mv.lineInfo(node);
        mv.invoke(Methods.EnvironmentRecord_createImmutableBinding);
    }

    /**
     * Emit function call for: {@link EnvironmentRecord#createImmutableBinding(String, boolean)}
     * <p>
     * stack: [] {@literal ->} []
     * 
     * @param envRec
     *            the variable which holds the environment record
     * @param name
     *            the binding name
     * @param mv
     *            the instruction visitor
     */
    protected final void createImmutableBinding(Variable<? extends EnvironmentRecord> envRec,
            Name name, boolean strict, InstructionVisitor mv) {
        mv.load(envRec);
        mv.aconst(name.getIdentifier());
        mv.iconst(strict);
        mv.invoke(Methods.EnvironmentRecord_createImmutableBinding);
    }

    /**
     * Emit function call for: {@link EnvironmentRecord#initializeBinding(String, Object)}
     * <p>
     * stack: [] {@literal ->} []
     * 
     * @param envRec
     *            the variable which holds the environment record
     * @param name
     *            the binding name
     * @param value
     *            the variable which holds the binding value
     * @param mv
     *            the instruction visitor
     */
    protected final void initializeBinding(Variable<? extends EnvironmentRecord> envRec, Name name,
            Variable<?> value, InstructionVisitor mv) {
        mv.load(envRec);
        mv.aconst(name.getIdentifier());
        mv.load(value);
        mv.invoke(Methods.EnvironmentRecord_initializeBinding);
    }

    /**
     * Emit function call for: {@link EnvironmentRecord#initializeBinding(String, Object)}
     * <p>
     * stack: [envRec, name, obj] {@literal ->} []
     * 
     * @param mv
     *            the instruction visitor
     */
    protected final void initializeBinding(InstructionVisitor mv) {
        mv.invoke(Methods.EnvironmentRecord_initializeBinding);
    }

    /**
     * Emit fused function call for: {@link EnvironmentRecord#getBindingValue(String, boolean)} and
     * {@link EnvironmentRecord#initializeBinding(String, Object)}
     * <p>
     * stack: [] {@literal ->} []
     * 
     * @param targetEnvRec
     *            the variable which holds the target environment record
     * @param sourceEnvRec
     *            the variable which holds the source environment record
     * @param name
     *            the binding name
     * @param strict
     *            the strict-mode flag
     * @param mv
     *            the instruction visitor
     */
    protected final void initializeBindingFrom(Variable<? extends EnvironmentRecord> targetEnvRec,
            Variable<? extends EnvironmentRecord> sourceEnvRec, Name name, boolean strict,
            InstructionVisitor mv) {
        mv.load(targetEnvRec);
        mv.aconst(name.getIdentifier());
        {
            mv.load(sourceEnvRec);
            mv.aconst(name.getIdentifier());
            mv.iconst(strict);
            mv.invoke(Methods.EnvironmentRecord_getBindingValue);
        }
        mv.invoke(Methods.EnvironmentRecord_initializeBinding);
    }

    /**
     * Emit function call for: {@link EnvironmentRecord#setMutableBinding(String, Object, boolean)}
     * <p>
     * stack: [obj] {@literal ->} []
     * 
     * @param envRec
     *            the variable which holds the environment record
     * @param name
     *            the binding name
     * @param value
     *            the value
     * @param strict
     *            the strict-mode flag
     * @param mv
     *            the instruction visitor
     */
    protected final void setMutableBinding(Variable<? extends EnvironmentRecord> envRec, Name name,
            Variable<?> value, boolean strict, InstructionVisitor mv) {
        mv.load(envRec);
        mv.aconst(name.getIdentifier());
        mv.load(value);
        mv.iconst(strict);
        mv.invoke(Methods.EnvironmentRecord_setMutableBinding);
    }

    /**
     * Emit function call for: {@link ExecutionContext#getLexicalEnvironment()}
     * <p>
     * stack: [] {@literal ->} [env]
     * 
     * @param context
     *            the variable which holds the execution context
     * @param env
     *            the variable which holds the lexical environment
     * @param mv
     *            the instruction visitor
     */
    protected final void getLexicalEnvironment(Variable<ExecutionContext> context,
            Variable<? extends LexicalEnvironment<?>> env, InstructionVisitor mv) {
        // stack: [] -> []
        mv.load(context);
        mv.invoke(Methods.ExecutionContext_getLexicalEnvironment);
        mv.store(env);
    }

    /**
     * Emit function call for: {@link ExecutionContext#getVariableEnvironment()}
     * <p>
     * stack: [] {@literal ->} [env]
     * 
     * @param context
     *            the variable which holds the execution context
     * @param env
     *            the variable which holds the lexical environment
     * @param mv
     *            the instruction visitor
     */
    protected final void getVariableEnvironment(Variable<ExecutionContext> context,
            Variable<? extends LexicalEnvironment<?>> env, InstructionVisitor mv) {
        // stack: [] -> []
        mv.load(context);
        mv.invoke(Methods.ExecutionContext_getVariableEnvironment);
        mv.store(env);
    }

    /**
     * Emit function call for: {@link LexicalEnvironment#getEnvRec()}
     * <p>
     * stack: [] {@literal ->} []
     * 
     * @param env
     *            the variable which holds the lexical environment
     * @param envRec
     *            the variable which holds the environment record
     * @param mv
     *            the instruction visitor
     */
    protected final <R extends EnvironmentRecord, R2 extends R> void getEnvironmentRecord(
            Variable<? extends LexicalEnvironment<? extends R2>> env, Variable<? extends R> envRec,
            InstructionVisitor mv) {
        mv.load(env);
        mv.invoke(Methods.LexicalEnvironment_getEnvRec);
        if (envRec.getType() != Types.EnvironmentRecord) {
            mv.checkcast(envRec.getType());
        }
        mv.store(envRec);
    }

    /**
     * <code>
     * ScriptRuntime.canDeclareLexicalScopedOrThrow(cx, envRec, name)
     * </code>
     * 
     * @param context
     *            the variable which holds the execution context
     * @param envRec
     *            the variable which holds the environment record
     * @param node
     *            the declaration node
     * @param name
     *            the binding name
     * @param mv
     *            the instruction visitor
     */
    protected final void canDeclareLexicalScopedOrThrow(Variable<ExecutionContext> context,
            Variable<GlobalEnvironmentRecord> envRec, Node node, Name name, InstructionVisitor mv) {
        mv.load(context);
        mv.load(envRec);
        mv.aconst(name.getIdentifier());
        mv.lineInfo(node);
        mv.invoke(Methods.ScriptRuntime_canDeclareLexicalScopedOrThrow);
    }

    /**
     * <code>
     * ScriptRuntime.canDeclareVarScopedOrThrow(cx, envRec, name)
     * </code>
     * 
     * @param context
     *            the variable which holds the execution context
     * @param envRec
     *            the variable which holds the environment record
     * @param node
     *            the declaration node
     * @param name
     *            the binding name
     * @param mv
     *            the instruction visitor
     */
    protected final void canDeclareVarScopedOrThrow(Variable<ExecutionContext> context,
            Variable<GlobalEnvironmentRecord> envRec, Node node, Name name, InstructionVisitor mv) {
        mv.load(context);
        mv.load(envRec);
        mv.aconst(name.getIdentifier());
        mv.lineInfo(node);
        mv.invoke(Methods.ScriptRuntime_canDeclareVarScopedOrThrow);
    }

    /**
     * <code>
     * ScriptRuntime.canDeclareGlobalFunctionOrThrow(cx, envRec, name)
     * </code>
     * 
     * @param context
     *            the variable which holds the execution context
     * @param envRec
     *            the variable which holds the environment record
     * @param node
     *            the function node
     * @param name
     *            the binding name
     * @param mv
     *            the instruction visitor
     */
    protected final void canDeclareGlobalFunctionOrThrow(Variable<ExecutionContext> context,
            Variable<GlobalEnvironmentRecord> envRec, HoistableDeclaration node, Name name,
            InstructionVisitor mv) {
        mv.load(context);
        mv.load(envRec);
        mv.aconst(name.getIdentifier());
        mv.lineInfo(node);
        mv.invoke(Methods.ScriptRuntime_canDeclareGlobalFunctionOrThrow);
    }

    /**
     * <code>
     * ScriptRuntime.canDeclareGlobalVarOrThrow(cx, envRec, name)
     * </code>
     * 
     * @param context
     *            the variable which holds the execution context
     * @param envRec
     *            the variable which holds the environment record
     * @param node
     *            the variable declaration node
     * @param name
     *            the binding name
     * @param mv
     *            the instruction visitor
     */
    protected final void canDeclareGlobalVarOrThrow(Variable<ExecutionContext> context,
            Variable<GlobalEnvironmentRecord> envRec, VariableDeclaration node, Name name,
            InstructionVisitor mv) {
        mv.load(context);
        mv.load(envRec);
        mv.aconst(name.getIdentifier());
        mv.lineInfo(node);
        mv.invoke(Methods.ScriptRuntime_canDeclareGlobalVarOrThrow);
    }

    /**
     * <code>
     * envRec.createGlobalVarBinding(name, deletableBindings)
     * </code>
     * 
     * @param envRec
     *            the variable which holds the environment record
     * @param node
     *            the variable declaration node
     * @param name
     *            the binding name
     * @param deletableBindings
     *            the variable which holds the deletable flag
     * @param mv
     *            the instruction visitor
     */
    protected final void createGlobalVarBinding(Variable<GlobalEnvironmentRecord> envRec,
            VariableDeclaration node, Name name, boolean deletableBindings, InstructionVisitor mv) {
        mv.load(envRec);
        mv.aconst(name.getIdentifier());
        mv.iconst(deletableBindings);
        mv.lineInfo(node);
        mv.invoke(Methods.GlobalEnvironmentRecord_createGlobalVarBinding);
    }

    /**
     * <code>
     * envRec.createGlobalFunctionBinding(name, functionObject, deletableBindings)
     * </code>
     * <p>
     * stack: [fo] {@literal ->} []
     * 
     * @param envRec
     *            the variable which holds the environment record
     * @param node
     *            the function node
     * @param name
     *            the binding name
     * @param deletableBindings
     *            the variable which holds the deletable flag
     * @param mv
     *            the instruction visitor
     */
    protected final void createGlobalFunctionBinding(Variable<GlobalEnvironmentRecord> envRec,
            HoistableDeclaration node, Name name, boolean deletableBindings, InstructionVisitor mv) {
        // stack: [fo] -> []
        mv.load(envRec);
        mv.swap();
        mv.aconst(name.getIdentifier());
        mv.swap();
        mv.iconst(deletableBindings);
        mv.lineInfo(node);
        mv.invoke(Methods.GlobalEnvironmentRecord_createGlobalFunctionBinding);
    }

    /**
     * Emit runtime call to initialize the function object.
     * <p>
     * stack: [] {@literal ->} [fo]
     * 
     * @param context
     *            the variable which holds the execution context
     * @param env
     *            the variable which holds the lexical environment
     * @param f
     *            the function declaration to instantiate
     * @param mv
     *            the instruction visitor
     */
    protected final void InstantiateFunctionObject(Variable<ExecutionContext> context,
            Variable<? extends LexicalEnvironment<?>> env, Declaration f, InstructionVisitor mv) {
        if (f instanceof FunctionDeclaration) {
            InstantiateFunctionObject(context, env, (FunctionDeclaration) f, mv);
        } else if (f instanceof GeneratorDeclaration) {
            InstantiateGeneratorObject(context, env, (GeneratorDeclaration) f, mv);
        } else {
            InstantiateAsyncFunctionObject(context, env, (AsyncFunctionDeclaration) f, mv);
        }
    }

    /**
     * Emit function call for:
     * {@link ScriptRuntime#InstantiateAsyncFunctionObject(LexicalEnvironment, ExecutionContext, RuntimeInfo.Function)}
     * <p>
     * stack: [] {@literal ->} [fo]
     * 
     * @param context
     *            the variable which holds the execution context
     * @param env
     *            the variable which holds the lexical environment
     * @param f
     *            the function declaration to instantiate
     * @param mv
     *            the instruction visitor
     */
    private void InstantiateAsyncFunctionObject(Variable<ExecutionContext> context,
            Variable<? extends LexicalEnvironment<?>> env, AsyncFunctionDeclaration f,
            InstructionVisitor mv) {
        codegen.compile(f);

        mv.load(env);
        mv.load(context);
        mv.invoke(codegen.methodDesc(f, FunctionName.RTI));
        mv.invoke(Methods.ScriptRuntime_InstantiateAsyncFunctionObject);
    }

    /**
     * Emit function call for:
     * {@link ScriptRuntime#InstantiateFunctionObject(LexicalEnvironment, ExecutionContext, RuntimeInfo.Function)}
     * <p>
     * stack: [] {@literal ->} [fo]
     * 
     * @param context
     *            the variable which holds the execution context
     * @param env
     *            the variable which holds the lexical environment
     * @param f
     *            the function declaration to instantiate
     * @param mv
     *            the instruction visitor
     */
    private void InstantiateFunctionObject(Variable<ExecutionContext> context,
            Variable<? extends LexicalEnvironment<?>> env, FunctionDeclaration f,
            InstructionVisitor mv) {
        codegen.compile(f);

        mv.load(env);
        mv.load(context);
        mv.invoke(codegen.methodDesc(f, FunctionName.RTI));
        mv.invoke(Methods.ScriptRuntime_InstantiateFunctionObject);
    }

    /**
     * Emit function call for:
     * {@link ScriptRuntime#InstantiateGeneratorObject(LexicalEnvironment, ExecutionContext, RuntimeInfo.Function)}
     * <p>
     * stack: [] {@literal ->} [fo]
     * 
     * @param context
     *            the variable which holds the execution context
     * @param env
     *            the variable which holds the lexical environment
     * @param f
     *            the generator declaration to instantiate
     * @param mv
     *            the instruction visitor
     */
    private void InstantiateGeneratorObject(Variable<ExecutionContext> context,
            Variable<? extends LexicalEnvironment<?>> env, GeneratorDeclaration f,
            InstructionVisitor mv) {
        codegen.compile(f);

        mv.load(env);
        mv.load(context);
        mv.invoke(codegen.methodDesc(f, FunctionName.RTI));
        if (!(f instanceof LegacyGeneratorDeclaration)) {
            mv.invoke(Methods.ScriptRuntime_InstantiateGeneratorObject);
        } else {
            mv.invoke(Methods.ScriptRuntime_InstantiateLegacyGeneratorObject);
        }
    }

    protected static final <T> Iterable<T> reverse(final List<T> list) {
        return new Iterable<T>() {
            @Override
            public Iterator<T> iterator() {
                return new Iterator<T>() {
                    ListIterator<T> iter = list.listIterator(list.size());

                    @Override
                    public boolean hasNext() {
                        return iter.hasPrevious();
                    }

                    @Override
                    public T next() {
                        return iter.previous();
                    }

                    @Override
                    public void remove() {
                        throw new UnsupportedOperationException();
                    }
                };
            }
        };
    }
}
