/**
 * Copyright (c) 2012-2016 André Bargull
 * Alle Rechte vorbehalten / All Rights Reserved.  Use is subject to license terms.
 *
 * <https://github.com/anba/es6draft>
 */
package com.github.anba.es6draft.compiler;

import static com.github.anba.es6draft.compiler.ClassPropertyGenerator.ClassPropertyEvaluation;
import static com.github.anba.es6draft.semantics.StaticSemantics.ConstructorMethod;
import static com.github.anba.es6draft.semantics.StaticSemantics.HasDecorators;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import com.github.anba.es6draft.ast.*;
import com.github.anba.es6draft.ast.AbruptNode.Abrupt;
import com.github.anba.es6draft.ast.scope.BlockScope;
import com.github.anba.es6draft.ast.scope.ModuleScope;
import com.github.anba.es6draft.ast.scope.Name;
import com.github.anba.es6draft.ast.scope.Scope;
import com.github.anba.es6draft.ast.scope.ScriptScope;
import com.github.anba.es6draft.ast.scope.WithScope;
import com.github.anba.es6draft.compiler.assembler.FieldName;
import com.github.anba.es6draft.compiler.assembler.Jump;
import com.github.anba.es6draft.compiler.assembler.MethodName;
import com.github.anba.es6draft.compiler.assembler.TryCatchLabel;
import com.github.anba.es6draft.compiler.assembler.Type;
import com.github.anba.es6draft.compiler.assembler.Value;
import com.github.anba.es6draft.compiler.assembler.Variable;
import com.github.anba.es6draft.runtime.DeclarativeEnvironmentRecord;
import com.github.anba.es6draft.runtime.EnvironmentRecord;
import com.github.anba.es6draft.runtime.GlobalEnvironmentRecord;
import com.github.anba.es6draft.runtime.LexicalEnvironment;
import com.github.anba.es6draft.runtime.ModuleEnvironmentRecord;
import com.github.anba.es6draft.runtime.ObjectEnvironmentRecord;
import com.github.anba.es6draft.runtime.internal.Bootstrap;
import com.github.anba.es6draft.runtime.internal.ScriptException;
import com.github.anba.es6draft.runtime.types.Callable;
import com.github.anba.es6draft.runtime.types.Null;
import com.github.anba.es6draft.runtime.types.Reference;
import com.github.anba.es6draft.runtime.types.ScriptObject;
import com.github.anba.es6draft.runtime.types.Undefined;
import com.github.anba.es6draft.runtime.types.builtins.OrdinaryConstructorFunction;
import com.github.anba.es6draft.runtime.types.builtins.OrdinaryObject;

/**
 * Abstract base class for specialised generators
 */
abstract class DefaultCodeGenerator<RETURN> extends DefaultNodeVisitor<RETURN, CodeVisitor> {
    private static final class Fields {
        static final FieldName Double_NaN = FieldName.findStatic(Types.Double, "NaN", Type.DOUBLE_TYPE);

        static final FieldName ScriptRuntime_EMPTY_ARRAY = FieldName.findStatic(Types.ScriptRuntime, "EMPTY_ARRAY",
                Types.Object_);
    }

    private static final class Methods {
        // class: AbstractOperations
        static final MethodName AbstractOperations_CreateIterResultObject = MethodName.findStatic(
                Types.AbstractOperations, "CreateIterResultObject", Type.methodType(
                        Types.OrdinaryObject, Types.ExecutionContext, Types.Object,
                        Type.BOOLEAN_TYPE));

        static final MethodName AbstractOperations_HasOwnProperty = MethodName.findStatic(
                Types.AbstractOperations, "HasOwnProperty", Type.methodType(Type.BOOLEAN_TYPE,
                        Types.ExecutionContext, Types.ScriptObject, Types.String));

        static final MethodName AbstractOperations_GetIterator = MethodName.findStatic(
                Types.AbstractOperations, "GetIterator",
                Type.methodType(Types.ScriptObject, Types.ExecutionContext, Types.Object));

        static final MethodName AbstractOperations_GetMethod = MethodName.findStatic(Types.AbstractOperations,
                "GetMethod", Type.methodType(Types.Callable, Types.ExecutionContext, Types.ScriptObject, Types.String));

        static final MethodName AbstractOperations_IteratorComplete = MethodName.findStatic(
                Types.AbstractOperations, "IteratorComplete",
                Type.methodType(Type.BOOLEAN_TYPE, Types.ExecutionContext, Types.ScriptObject));

        static final MethodName AbstractOperations_IteratorNext = MethodName.findStatic(
                Types.AbstractOperations, "IteratorNext", Type.methodType(Types.ScriptObject,
                        Types.ExecutionContext, Types.ScriptObject));

        static final MethodName AbstractOperations_IteratorNext_Object = MethodName.findStatic(
                Types.AbstractOperations, "IteratorNext", Type.methodType(Types.ScriptObject,
                        Types.ExecutionContext, Types.ScriptObject, Types.Object));

        static final MethodName AbstractOperations_IteratorValue = MethodName.findStatic(
                Types.AbstractOperations, "IteratorValue",
                Type.methodType(Types.Object, Types.ExecutionContext, Types.ScriptObject));

        static final MethodName AbstractOperations_ToPrimitive = MethodName.findStatic(
                Types.AbstractOperations, "ToPrimitive",
                Type.methodType(Types.Object, Types.ExecutionContext, Types.Object));

        static final MethodName AbstractOperations_ToBoolean = MethodName.findStatic(
                Types.AbstractOperations, "ToBoolean",
                Type.methodType(Type.BOOLEAN_TYPE, Types.Object));

        static final MethodName AbstractOperations_ToBoolean_double = MethodName.findStatic(
                Types.AbstractOperations, "ToBoolean",
                Type.methodType(Type.BOOLEAN_TYPE, Type.DOUBLE_TYPE));

        static final MethodName AbstractOperations_ToFlatString = MethodName.findStatic(
                Types.AbstractOperations, "ToFlatString",
                Type.methodType(Types.String, Types.ExecutionContext, Types.Object));

        static final MethodName AbstractOperations_ToNumber = MethodName.findStatic(
                Types.AbstractOperations, "ToNumber",
                Type.methodType(Type.DOUBLE_TYPE, Types.ExecutionContext, Types.Object));

        static final MethodName AbstractOperations_ToNumber_CharSequence = MethodName.findStatic(
                Types.AbstractOperations, "ToNumber",
                Type.methodType(Type.DOUBLE_TYPE, Types.CharSequence));

        static final MethodName AbstractOperations_ToInt32 = MethodName.findStatic(
                Types.AbstractOperations, "ToInt32",
                Type.methodType(Type.INT_TYPE, Types.ExecutionContext, Types.Object));

        static final MethodName AbstractOperations_ToInt32_double = MethodName.findStatic(
                Types.AbstractOperations, "ToInt32",
                Type.methodType(Type.INT_TYPE, Type.DOUBLE_TYPE));

        static final MethodName AbstractOperations_ToUint32 = MethodName.findStatic(
                Types.AbstractOperations, "ToUint32",
                Type.methodType(Type.LONG_TYPE, Types.ExecutionContext, Types.Object));

        static final MethodName AbstractOperations_ToUint32_double = MethodName.findStatic(
                Types.AbstractOperations, "ToUint32",
                Type.methodType(Type.LONG_TYPE, Type.DOUBLE_TYPE));

        static final MethodName AbstractOperations_ToObject = MethodName.findStatic(
                Types.AbstractOperations, "ToObject",
                Type.methodType(Types.ScriptObject, Types.ExecutionContext, Types.Object));

        static final MethodName AbstractOperations_ToPropertyKey = MethodName.findStatic(
                Types.AbstractOperations, "ToPropertyKey",
                Type.methodType(Types.Object, Types.ExecutionContext, Types.Object));

        static final MethodName AbstractOperations_ToString = MethodName.findStatic(
                Types.AbstractOperations, "ToString",
                Type.methodType(Types.CharSequence, Types.ExecutionContext, Types.Object));

        static final MethodName AbstractOperations_ToString_int = MethodName.findStatic(
                Types.AbstractOperations, "ToString", Type.methodType(Types.String, Type.INT_TYPE));

        static final MethodName AbstractOperations_ToString_long = MethodName
                .findStatic(Types.AbstractOperations, "ToString",
                        Type.methodType(Types.String, Type.LONG_TYPE));

        static final MethodName AbstractOperations_ToString_double = MethodName.findStatic(
                Types.AbstractOperations, "ToString",
                Type.methodType(Types.String, Type.DOUBLE_TYPE));

        // class: AsyncAbstractOperations
        static final MethodName AsyncAbstractOperations_AsyncFunctionAwait = MethodName.findStatic(
                Types.AsyncAbstractOperations, "AsyncFunctionAwait",
                Type.methodType(Type.VOID_TYPE, Types.ExecutionContext, Types.Object));

        // class: Boolean
        static final MethodName Boolean_toString = MethodName.findStatic(Types.Boolean, "toString",
                Type.methodType(Types.String, Type.BOOLEAN_TYPE));

        // class: CharSequence
        static final MethodName CharSequence_length = MethodName.findInterface(Types.CharSequence,
                "length", Type.methodType(Type.INT_TYPE));
        static final MethodName CharSequence_toString = MethodName.findInterface(
                Types.CharSequence, "toString", Type.methodType(Types.String));

        // class: ExecutionContext
        static final MethodName ExecutionContext_getLexicalEnvironment = MethodName.findVirtual(
                Types.ExecutionContext, "getLexicalEnvironment",
                Type.methodType(Types.LexicalEnvironment));

        static final MethodName ExecutionContext_getLexicalEnvironmentRecord = MethodName
                .findVirtual(Types.ExecutionContext, "getLexicalEnvironmentRecord",
                        Type.methodType(Types.EnvironmentRecord));

        static final MethodName ExecutionContext_getVariableEnvironmentRecord = MethodName
                .findVirtual(Types.ExecutionContext, "getVariableEnvironmentRecord",
                        Type.methodType(Types.EnvironmentRecord));

        static final MethodName ExecutionContext_pushLexicalEnvironment = MethodName.findVirtual(
                Types.ExecutionContext, "pushLexicalEnvironment",
                Type.methodType(Type.VOID_TYPE, Types.LexicalEnvironment));

        static final MethodName ExecutionContext_popLexicalEnvironment = MethodName.findVirtual(
                Types.ExecutionContext, "popLexicalEnvironment", Type.methodType(Type.VOID_TYPE));

        static final MethodName ExecutionContext_replaceLexicalEnvironment = MethodName
                .findVirtual(Types.ExecutionContext, "replaceLexicalEnvironment",
                        Type.methodType(Type.VOID_TYPE, Types.LexicalEnvironment));

        static final MethodName ExecutionContext_restoreLexicalEnvironment = MethodName
                .findVirtual(Types.ExecutionContext, "restoreLexicalEnvironment",
                        Type.methodType(Type.VOID_TYPE, Types.LexicalEnvironment));

        // class: LexicalEnvironment
        static final MethodName LexicalEnvironment_getEnvRec = MethodName.findVirtual(
                Types.LexicalEnvironment, "getEnvRec", Type.methodType(Types.EnvironmentRecord));

        static final MethodName LexicalEnvironment_cloneDeclarativeEnvironment = MethodName
                .findStatic(Types.LexicalEnvironment, "cloneDeclarativeEnvironment",
                        Type.methodType(Types.LexicalEnvironment, Types.LexicalEnvironment));

        static final MethodName LexicalEnvironment_newDeclarativeEnvironment = MethodName
                .findStatic(Types.LexicalEnvironment, "newDeclarativeEnvironment",
                        Type.methodType(Types.LexicalEnvironment, Types.LexicalEnvironment));

        static final MethodName LexicalEnvironment_newCatchDeclarativeEnvironment = MethodName
                .findStatic(Types.LexicalEnvironment, "newCatchDeclarativeEnvironment",
                        Type.methodType(Types.LexicalEnvironment, Types.LexicalEnvironment));

        static final MethodName LexicalEnvironment_newObjectEnvironment = MethodName.findStatic(
                Types.LexicalEnvironment, "newObjectEnvironment", Type.methodType(
                        Types.LexicalEnvironment, Types.ScriptObject, Types.LexicalEnvironment,
                        Type.BOOLEAN_TYPE));

        // class: OrdinaryFunction
        static final MethodName OrdinaryFunction_SetFunctionName_String = MethodName.findStatic(
                Types.OrdinaryFunction, "SetFunctionName",
                Type.methodType(Type.VOID_TYPE, Types.OrdinaryObject, Types.String));

        static final MethodName OrdinaryFunction_SetFunctionName_Symbol = MethodName.findStatic(
                Types.OrdinaryFunction, "SetFunctionName",
                Type.methodType(Type.VOID_TYPE, Types.OrdinaryObject, Types.Symbol));

        // class: ReturnValue
        static final MethodName ReturnValue_getValue = MethodName.findVirtual(Types.ReturnValue,
                "getValue", Type.methodType(Types.Object));

        // class: ScriptException
        static final MethodName ScriptException_getValue = MethodName.findVirtual(Types.ScriptException,
                "getValue", Type.methodType(Types.Object));

        // class: ScriptRuntime
        static final MethodName ScriptRuntime_CheckCallable = MethodName.findStatic(
                Types.ScriptRuntime, "CheckCallable",
                Type.methodType(Types.Callable, Types.Object, Types.ExecutionContext));

        static final MethodName ScriptRuntime_EvaluateConstructorMethod = MethodName.findStatic(
                Types.ScriptRuntime, "EvaluateConstructorMethod", Type.methodType(
                        Types.OrdinaryConstructorFunction, Types.ScriptObject,
                        Types.OrdinaryObject, Types.RuntimeInfo$Function, Type.BOOLEAN_TYPE,
                        Types.ExecutionContext));

        static final MethodName ScriptRuntime_EvaluateClassDecorators = MethodName
                .findStatic(Types.ScriptRuntime, "EvaluateClassDecorators", Type.methodType(
                        Type.VOID_TYPE, Types.OrdinaryConstructorFunction, Types.ArrayList,
                        Types.ExecutionContext));

        static final MethodName ScriptRuntime_EvaluateClassMethodDecorators = MethodName
                .findStatic(Types.ScriptRuntime, "EvaluateClassMethodDecorators",
                        Type.methodType(Type.VOID_TYPE, Types.ArrayList, Types.ExecutionContext));

        static final MethodName ScriptRuntime_getClassProto = MethodName.findStatic(
                Types.ScriptRuntime, "getClassProto",
                Type.methodType(Types.ScriptObject_, Types.Object, Types.ExecutionContext));

        static final MethodName ScriptRuntime_getClassProto_Null = MethodName.findStatic(
                Types.ScriptRuntime, "getClassProto",
                Type.methodType(Types.ScriptObject_, Types.ExecutionContext));

        static final MethodName ScriptRuntime_getDefaultClassProto = MethodName.findStatic(
                Types.ScriptRuntime, "getDefaultClassProto",
                Type.methodType(Types.ScriptObject_, Types.ExecutionContext));

        static final MethodName ScriptRuntime_yieldThrowCompletion = MethodName.findStatic(
                Types.ScriptRuntime, "yieldThrowCompletion", Type.methodType(Types.ScriptObject,
                        Types.ExecutionContext, Types.ScriptObject, Types.ScriptException));

        static final MethodName ScriptRuntime_yieldReturnCompletion = MethodName.findStatic(
                Types.ScriptRuntime, "yieldReturnCompletion", Type.methodType(Types.ScriptObject,
                        Types.ExecutionContext, Types.ScriptObject, Types.ReturnValue));

        static final MethodName ScriptRuntime_reportPropertyNotCallable = MethodName.findStatic(Types.ScriptRuntime,
                "reportPropertyNotCallable",
                Type.methodType(Types.ScriptException, Types.String, Types.ExecutionContext));

        static final MethodName ScriptRuntime_requireObjectResult = MethodName.findStatic(Types.ScriptRuntime,
                "requireObjectResult",
                Type.methodType(Types.ScriptObject, Types.Object, Types.String, Types.ExecutionContext));

        // class: Type
        static final MethodName Type_isUndefinedOrNull = MethodName.findStatic(Types._Type,
                "isUndefinedOrNull", Type.methodType(Type.BOOLEAN_TYPE, Types.Object));

        // class: ArrayList
        static final MethodName ArrayList_init = MethodName.findConstructor(Types.ArrayList,
                Type.methodType(Type.VOID_TYPE));

        static final MethodName ArrayList_add = MethodName.findVirtual(Types.ArrayList, "add",
                Type.methodType(Type.BOOLEAN_TYPE, Types.Object));

        // class: Throwable
        static final MethodName Throwable_addSuppressed = MethodName.findVirtual(Types.Throwable, "addSuppressed",
                Type.methodType(Type.VOID_TYPE, Types.Throwable));
    }

    protected final CodeGenerator codegen;

    protected DefaultCodeGenerator(CodeGenerator codegen) {
        this.codegen = codegen;
    }

    /**
     * stack: [] {@literal ->} [value|reference]
     * 
     * @param node
     *            the expression node
     * @param mv
     *            the code visitor
     * @return the value type returned by the expression
     */
    protected final ValType expression(Expression node, CodeVisitor mv) {
        return codegen.expression(node, mv);
    }

    /**
     * stack: [] {@literal ->} [boxed(value)]
     * 
     * @param node
     *            the expression node
     * @param mv
     *            the code visitor
     * @return the value type returned by the expression
     */
    protected final ValType expressionBoxed(Expression node, CodeVisitor mv) {
        return codegen.expressionBoxed(node, mv);
    }

    /**
     * stack: [] {@literal ->} []
     * 
     * @param node
     *            the abrupt node
     * @param mv
     *            the code visitor
     * @return the variable holding the saved environment or {@code null}
     */
    protected final Variable<LexicalEnvironment<?>> saveEnvironment(AbruptNode node, CodeVisitor mv) {
        EnumSet<Abrupt> abrupt = node.getAbrupt();
        if (abrupt.contains(Abrupt.Break) || abrupt.contains(Abrupt.Continue)) {
            return saveEnvironment(mv);
        }
        return null;
    }

    /**
     * stack: [] {@literal ->} []
     * 
     * @param mv
     *            the code visitor
     * @return the variable holding the saved environment
     */
    protected final Variable<LexicalEnvironment<?>> saveEnvironment(CodeVisitor mv) {
        Variable<LexicalEnvironment<?>> savedEnv = mv.newVariable("savedEnv",
                LexicalEnvironment.class).uncheckedCast();
        getLexicalEnvironment(mv);
        mv.store(savedEnv);
        return savedEnv;
    }

    /**
     * stack: [] {@literal ->} []
     * 
     * @param savedEnv
     *            the variable which holds the saved environment
     * @param mv
     *            the code visitor
     */
    protected final void restoreEnvironment(Variable<LexicalEnvironment<?>> savedEnv, CodeVisitor mv) {
        mv.loadExecutionContext();
        mv.load(savedEnv);
        mv.invoke(Methods.ExecutionContext_restoreLexicalEnvironment);
    }

    /**
     * stack: [] {@literal ->} []
     * 
     * @param savedEnv
     *            the variable which holds the saved environment
     * @param mv
     *            the code visitor
     */
    protected final void replaceLexicalEnvironment(Variable<LexicalEnvironment<?>> savedEnv, CodeVisitor mv) {
        mv.loadExecutionContext();
        mv.load(savedEnv);
        mv.invoke(Methods.ExecutionContext_replaceLexicalEnvironment);
    }

    /**
     * stack: [] {@literal ->} [lexEnv]
     * 
     * @param mv
     *            the code visitor
     */
    protected final void getLexicalEnvironment(CodeVisitor mv) {
        mv.loadExecutionContext();
        mv.invoke(Methods.ExecutionContext_getLexicalEnvironment);
    }

    /**
     * stack: [] {@literal ->} []
     * 
     * @param envRec
     *            the variable which holds the lexical environment record
     * @param mv
     *            the code visitor
     */
    protected final <R extends EnvironmentRecord> void getLexicalEnvironmentRecord(Variable<? extends R> envRec,
            CodeVisitor mv) {
        mv.loadExecutionContext();
        mv.invoke(Methods.ExecutionContext_getLexicalEnvironmentRecord);
        if (envRec.getType() != Types.EnvironmentRecord) {
            mv.checkcast(envRec.getType());
        }
        mv.store(envRec);
    }

    /**
     * stack: [] {@literal ->} []
     * 
     * @param <R>
     *            the environment record type
     * @param type
     *            the environment record type
     * @param mv
     *            the code visitor
     */
    protected final <R extends EnvironmentRecord> Value<R> getLexicalEnvironmentRecord(Type type, CodeVisitor mv) {
        return asm -> {
            mv.loadExecutionContext();
            mv.invoke(Methods.ExecutionContext_getLexicalEnvironmentRecord);
            if (type != Types.EnvironmentRecord) {
                mv.checkcast(type);
            }
        };
    }

    /**
     * stack: [] {@literal ->} []
     * 
     * @param envRec
     *            the variable which holds the variable environment record
     * @param mv
     *            the code visitor
     */
    protected final <R extends EnvironmentRecord> void getVariableEnvironmentRecord(Variable<? extends R> envRec,
            CodeVisitor mv) {
        mv.loadExecutionContext();
        mv.invoke(Methods.ExecutionContext_getVariableEnvironmentRecord);
        if (envRec.getType() != Types.EnvironmentRecord) {
            mv.checkcast(envRec.getType());
        }
        mv.store(envRec);
    }

    /**
     * stack: [] {@literal ->} []
     * 
     * @param <R>
     *            the environment record type
     * @param type
     *            the environment record type
     * @param mv
     *            the code visitor
     */
    protected final <R extends EnvironmentRecord> Value<R> getVariableEnvironmentRecord(Type type, CodeVisitor mv) {
        return asm -> {
            mv.loadExecutionContext();
            mv.invoke(Methods.ExecutionContext_getVariableEnvironmentRecord);
            if (type != Types.EnvironmentRecord) {
                mv.checkcast(type);
            }
        };
    }

    /**
     * Returns the current environment record type.
     * 
     * @param mv
     *            the code visitor
     * @return the current environment record type
     */
    protected final Class<? extends EnvironmentRecord> getEnvironmentRecordClass(CodeVisitor mv) {
        Scope scope = mv.getScope();
        while (!scope.isPresent()) {
            scope = scope.getParent();
        }
        if (scope instanceof ScriptScope) {
            Script script = ((ScriptScope) scope).getNode();
            if (!(script.isEvalScript() || script.isScripting())) {
                return GlobalEnvironmentRecord.class;
            }
        } else if (scope instanceof ModuleScope) {
            return ModuleEnvironmentRecord.class;
        } else if (scope instanceof WithScope) {
            return ObjectEnvironmentRecord.class;
        }
        return DeclarativeEnvironmentRecord.class;
    }

    /**
     * Creates a new object environment.
     * <p>
     * stack: [obj] {@literal ->} [lexEnv]
     * 
     * @param mv
     *            the code visitor
     * @param withEnvironment
     *            the withEnvironment flag
     */
    protected final void newObjectEnvironment(CodeVisitor mv, boolean withEnvironment) {
        mv.loadExecutionContext();
        mv.invoke(Methods.ExecutionContext_getLexicalEnvironment);
        mv.iconst(withEnvironment);
        mv.invoke(Methods.LexicalEnvironment_newObjectEnvironment);
    }

    /**
     * Creates a new declarative environment.
     * <p>
     * stack: [] {@literal ->} [lexEnv]
     * 
     * @param mv
     *            the code visitor
     */
    protected final void newDeclarativeEnvironment(BlockScope scope, CodeVisitor mv) {
        mv.loadExecutionContext();
        mv.invoke(Methods.ExecutionContext_getLexicalEnvironment);
        mv.invoke(Methods.LexicalEnvironment_newDeclarativeEnvironment);
    }

    /**
     * Creates a new declarative environment for a {@code Catch} clause.
     * <p>
     * stack: [] {@literal ->} [lexEnv]
     * 
     * @param mv
     *            the code visitor
     */
    protected final void newCatchDeclarativeEnvironment(BlockScope scope, CodeVisitor mv) {
        mv.loadExecutionContext();
        mv.invoke(Methods.ExecutionContext_getLexicalEnvironment);
        mv.invoke(Methods.LexicalEnvironment_newCatchDeclarativeEnvironment);
    }

    /**
     * stack: [] {@literal ->} [lexEnv]
     * 
     * @param mv
     *            the code visitor
     */
    protected final void cloneDeclarativeEnvironment(CodeVisitor mv) {
        mv.loadExecutionContext();
        mv.invoke(Methods.ExecutionContext_getLexicalEnvironment);
        mv.invoke(Methods.LexicalEnvironment_cloneDeclarativeEnvironment);
    }

    /**
     * stack: [lexEnv] {@literal ->} []
     * 
     * @param mv
     *            the code visitor
     */
    protected final void pushLexicalEnvironment(CodeVisitor mv) {
        mv.loadExecutionContext();
        mv.swap();
        mv.invoke(Methods.ExecutionContext_pushLexicalEnvironment);
    }

    /**
     * Restores the previous lexical environment.
     * <p>
     * stack: [] {@literal ->} []
     * 
     * @param mv
     *            the code visitor
     */
    protected final void popLexicalEnvironment(CodeVisitor mv) {
        mv.loadExecutionContext();
        mv.invoke(Methods.ExecutionContext_popLexicalEnvironment);
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
    protected final <R extends EnvironmentRecord, R2 extends R> void getEnvRec(
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
     * Emit function call for: {@link LexicalEnvironment#getEnvRec()}
     * <p>
     * stack: [env] {@literal ->} [env]
     * 
     * @param env
     *            the variable which holds the lexical environment
     * @param envRec
     *            the variable which holds the environment record
     * @param mv
     *            the instruction visitor
     */
    protected final <R extends EnvironmentRecord> void getEnvRec(Variable<? extends R> envRec,
            InstructionVisitor mv) {
        mv.dup(); // TODO: Remove dup?!
        mv.invoke(Methods.LexicalEnvironment_getEnvRec);
        if (envRec.getType() != Types.EnvironmentRecord) {
            mv.checkcast(envRec.getType());
        }
        mv.store(envRec);
    }

    /**
     * stack: [object] {@literal ->} [boolean]
     * 
     * @param mv
     *            the code visitor
     */
    protected final void isUndefinedOrNull(CodeVisitor mv) {
        mv.invoke(Methods.Type_isUndefinedOrNull);
    }

    enum ValType {
        Undefined, Null, Boolean, Number, Number_int, Number_uint, String, Object, Reference, Any,
        Empty;

        public int size() {
            switch (this) {
            case Number:
            case Number_uint:
                return 2;
            case Number_int:
            case Undefined:
            case Null:
            case Boolean:
            case String:
            case Object:
            case Reference:
            case Any:
                return 1;
            case Empty:
            default:
                return 0;
            }
        }

        public boolean isNumeric() {
            switch (this) {
            case Number:
            case Number_int:
            case Number_uint:
                return true;
            case Undefined:
            case Null:
            case Boolean:
            case String:
            case Object:
            case Reference:
            case Any:
            case Empty:
            default:
                return false;
            }
        }

        public boolean isPrimitive() {
            switch (this) {
            case Undefined:
            case Null:
            case Boolean:
            case Number:
            case Number_int:
            case Number_uint:
            case String:
                return true;
            case Object:
            case Reference:
            case Any:
            case Empty:
            default:
                return false;
            }
        }

        public boolean isJavaPrimitive() {
            switch (this) {
            case Boolean:
            case Number:
            case Number_int:
            case Number_uint:
                return true;
            case Undefined:
            case Null:
            case String:
            case Object:
            case Reference:
            case Any:
            case Empty:
            default:
                return false;
            }
        }

        public Class<?> toClass() {
            switch (this) {
            case Boolean:
                return boolean.class;
            case String:
                return CharSequence.class;
            case Number:
                return double.class;
            case Number_int:
                return int.class;
            case Number_uint:
                return long.class;
            case Object:
                return ScriptObject.class;
            case Reference:
                return Reference.class;
            case Null:
                return Null.class;
            case Undefined:
                return Undefined.class;
            case Any:
                return Object.class;
            case Empty:
            default:
                throw new AssertionError();
            }
        }

        public Type toType() {
            switch (this) {
            case Boolean:
                return Type.BOOLEAN_TYPE;
            case String:
                return Types.CharSequence;
            case Number:
                return Type.DOUBLE_TYPE;
            case Number_int:
                return Type.INT_TYPE;
            case Number_uint:
                return Type.LONG_TYPE;
            case Object:
                return Types.ScriptObject;
            case Reference:
                return Types.Reference;
            case Null:
                return Types.Null;
            case Undefined:
                return Types.Undefined;
            case Any:
                return Types.Object;
            case Empty:
            default:
                throw new AssertionError();
            }
        }

        public Type toBoxedType() {
            switch (this) {
            case Boolean:
                return Types.Boolean;
            case String:
                return Types.CharSequence;
            case Number:
                return Types.Double;
            case Number_int:
                return Types.Integer;
            case Number_uint:
                return Types.Long;
            case Object:
                return Types.ScriptObject;
            case Reference:
                return Types.Reference;
            case Null:
                return Types.Null;
            case Undefined:
                return Types.Undefined;
            case Any:
                return Types.Object;
            case Empty:
            default:
                throw new AssertionError();
            }
        }

        public static ValType of(Type type) {
            if (type.isPrimitive()) {
                if (Type.BOOLEAN_TYPE.equals(type)) {
                    return ValType.Boolean;
                }
                if (Type.INT_TYPE.equals(type)) {
                    return ValType.Number_int;
                }
                if (Type.LONG_TYPE.equals(type)) {
                    return ValType.Number_uint;
                }
                if (Type.DOUBLE_TYPE.equals(type)) {
                    return ValType.Number;
                }
                return ValType.Any;
            }
            if (Types.Boolean.equals(type)) {
                return ValType.Boolean;
            }
            if (Types.Integer.equals(type)) {
                return ValType.Number_int;
            }
            if (Types.Long.equals(type)) {
                return ValType.Number_uint;
            }
            if (Types.Double.equals(type)) {
                return ValType.Number;
            }
            if (Types.Null.equals(type)) {
                return ValType.Null;
            }
            if (Types.Undefined.equals(type)) {
                return ValType.Undefined;
            }
            if (Types.String.equals(type) || Types.CharSequence.equals(type)) {
                return ValType.String;
            }
            if (Types.ScriptObject.equals(type)) {
                return ValType.Object;
            }
            if (Types.OrdinaryObject.equals(type)) {
                return ValType.Object;
            }
            return ValType.Any;
        }
    }

    /**
     * stack: [Object] {@literal ->} [boolean]
     * 
     * @param from
     *            the input value type
     * @param mv
     *            the code visitor
     * @return the returned value type
     */
    protected static final ValType ToPrimitive(ValType from, CodeVisitor mv) {
        switch (from) {
        case Number:
        case Number_int:
        case Number_uint:
        case Undefined:
        case Null:
        case Boolean:
        case String:
            return from;
        case Object:
        case Any:
            mv.loadExecutionContext();
            mv.swap();
            mv.invoke(Methods.AbstractOperations_ToPrimitive);
            return ValType.Any;
        case Empty:
        case Reference:
        default:
            throw new AssertionError();
        }
    }

    /**
     * stack: [Object] {@literal ->} [boolean]
     * 
     * @param from
     *            the input value type
     * @param mv
     *            the code visitor
     */
    protected static final void ToBoolean(ValType from, CodeVisitor mv) {
        switch (from) {
        case Number:
            mv.invoke(Methods.AbstractOperations_ToBoolean_double);
            return;
        case Number_int:
            mv.i2d();
            mv.invoke(Methods.AbstractOperations_ToBoolean_double);
            return;
        case Number_uint:
            mv.l2d();
            mv.invoke(Methods.AbstractOperations_ToBoolean_double);
            return;
        case Undefined:
        case Null:
            mv.pop();
            mv.iconst(false);
            return;
        case Boolean:
            return;
        case String: {
            Jump l0 = new Jump(), l1 = new Jump();
            mv.invoke(Methods.CharSequence_length);
            mv.ifeq(l0);
            mv.iconst(true);
            mv.goTo(l1);
            mv.mark(l0);
            mv.iconst(false);
            mv.mark(l1);
            return;
        }
        case Object:
            mv.pop();
            mv.iconst(true);
            return;
        case Any:
            mv.invoke(Methods.AbstractOperations_ToBoolean);
            return;
        case Empty:
        case Reference:
        default:
            throw new AssertionError();
        }
    }

    /**
     * stack: [Object] {@literal ->} [double]
     * 
     * @param from
     *            the input value type
     * @param mv
     *            the code visitor
     */
    protected static final void ToNumber(ValType from, CodeVisitor mv) {
        switch (from) {
        case Number:
            return;
        case Number_int:
            mv.i2d();
            return;
        case Number_uint:
            mv.l2d();
            return;
        case Undefined:
            mv.pop();
            mv.get(Fields.Double_NaN);
            return;
        case Null:
            mv.pop();
            mv.dconst(0);
            return;
        case Boolean:
            mv.i2d();
            return;
        case String:
            mv.invoke(Methods.AbstractOperations_ToNumber_CharSequence);
            return;
        case Object:
        case Any:
            mv.loadExecutionContext();
            mv.swap();
            mv.invoke(Methods.AbstractOperations_ToNumber);
            return;
        case Empty:
        case Reference:
        default:
            throw new AssertionError();
        }
    }

    /**
     * stack: [Object] {@literal ->} [int]
     * 
     * @param from
     *            the input value type
     * @param mv
     *            the code visitor
     */
    protected static final void ToInt32(ValType from, CodeVisitor mv) {
        switch (from) {
        case Number:
            mv.invoke(Methods.AbstractOperations_ToInt32_double);
            return;
        case Number_int:
            return;
        case Number_uint:
            mv.l2i();
            return;
        case Undefined:
        case Null:
            mv.pop();
            mv.iconst(0);
            return;
        case Boolean:
            return;
        case String:
            mv.invoke(Methods.AbstractOperations_ToNumber_CharSequence);
            mv.invoke(Methods.AbstractOperations_ToInt32_double);
            return;
        case Object:
        case Any:
            mv.loadExecutionContext();
            mv.swap();
            mv.invoke(Methods.AbstractOperations_ToInt32);
            return;
        case Empty:
        case Reference:
        default:
            throw new AssertionError();
        }
    }

    /**
     * stack: [Object] {@literal ->} [long]
     * 
     * @param from
     *            the input value type
     * @param mv
     *            the code visitor
     */
    protected static final void ToUint32(ValType from, CodeVisitor mv) {
        switch (from) {
        case Number:
            mv.invoke(Methods.AbstractOperations_ToUint32_double);
            return;
        case Number_int:
            mv.i2l();
            mv.lconst(0xffff_ffffL);
            mv.land();
            return;
        case Number_uint:
            return;
        case Undefined:
        case Null:
            mv.pop();
            mv.lconst(0);
            return;
        case Boolean:
            mv.i2l();
            return;
        case String:
            mv.invoke(Methods.AbstractOperations_ToNumber_CharSequence);
            mv.invoke(Methods.AbstractOperations_ToUint32_double);
            return;
        case Object:
        case Any:
            mv.loadExecutionContext();
            mv.swap();
            mv.invoke(Methods.AbstractOperations_ToUint32);
            return;
        case Empty:
        case Reference:
        default:
            throw new AssertionError();
        }
    }

    /**
     * stack: [Object] {@literal ->} [CharSequence]
     * 
     * @param from
     *            the input value type
     * @param mv
     *            the code visitor
     */
    protected static final void ToString(ValType from, CodeVisitor mv) {
        switch (from) {
        case Number:
            mv.invoke(Methods.AbstractOperations_ToString_double);
            return;
        case Number_int:
            mv.invoke(Methods.AbstractOperations_ToString_int);
            return;
        case Number_uint:
            mv.invoke(Methods.AbstractOperations_ToString_long);
            return;
        case Undefined:
            mv.pop();
            mv.aconst("undefined");
            return;
        case Null:
            mv.pop();
            mv.aconst("null");
            return;
        case Boolean:
            mv.invoke(Methods.Boolean_toString);
            return;
        case String:
            return;
        case Object:
        case Any:
            mv.loadExecutionContext();
            mv.swap();
            mv.invoke(Methods.AbstractOperations_ToString);
            return;
        case Empty:
        case Reference:
        default:
            throw new AssertionError();
        }
    }

    /**
     * stack: [Object] {@literal ->} [String]
     * 
     * @param from
     *            the input value type
     * @param mv
     *            the code visitor
     */
    protected static final void ToFlatString(ValType from, CodeVisitor mv) {
        switch (from) {
        case Number:
            mv.invoke(Methods.AbstractOperations_ToString_double);
            return;
        case Number_int:
            mv.invoke(Methods.AbstractOperations_ToString_int);
            return;
        case Number_uint:
            mv.invoke(Methods.AbstractOperations_ToString_long);
            return;
        case Undefined:
            mv.pop();
            mv.aconst("undefined");
            return;
        case Null:
            mv.pop();
            mv.aconst("null");
            return;
        case Boolean:
            mv.invoke(Methods.Boolean_toString);
            return;
        case String:
            mv.invoke(Methods.CharSequence_toString);
            return;
        case Object:
        case Any:
            mv.loadExecutionContext();
            mv.swap();
            mv.invoke(Methods.AbstractOperations_ToFlatString);
            return;
        case Empty:
        case Reference:
        default:
            throw new AssertionError();
        }
    }

    /**
     * stack: [Object] {@literal ->} [ScriptObject]
     * 
     * @param from
     *            the input value type
     * @param mv
     *            the code visitor
     */
    protected static final void ToObject(ValType from, CodeVisitor mv) {
        switch (from) {
        case Number:
        case Number_int:
        case Number_uint:
        case Boolean:
            mv.toBoxed(from);
            break;
        case Object:
            return;
        case Undefined:
        case Null:
        case String:
        case Any:
            break;
        case Empty:
        case Reference:
        default:
            throw new AssertionError();
        }

        mv.loadExecutionContext();
        mv.swap();
        mv.invoke(Methods.AbstractOperations_ToObject);
    }

    /**
     * stack: [Object] {@literal ->} [ScriptObject]
     * 
     * @param node
     *            the current node
     * @param from
     *            the input value type
     * @param mv
     *            the code visitor
     */
    protected static final void ToObject(Node node, ValType from, CodeVisitor mv) {
        switch (from) {
        case Number:
        case Number_int:
        case Number_uint:
        case Boolean:
            mv.toBoxed(from);
            break;
        case Object:
            return;
        case Undefined:
        case Null:
        case String:
        case Any:
            break;
        case Empty:
        case Reference:
        default:
            throw new AssertionError();
        }

        mv.lineInfo(node);
        mv.loadExecutionContext();
        mv.swap();
        mv.invoke(Methods.AbstractOperations_ToObject);
    }

    /**
     * stack: [Object] {@literal ->} [String|Symbol]
     * 
     * @param from
     *            the input value type
     * @param mv
     *            the code visitor
     */
    protected static final ValType ToPropertyKey(ValType from, CodeVisitor mv) {
        switch (from) {
        case Number:
            mv.invoke(Methods.AbstractOperations_ToString_double);
            return ValType.String;
        case Number_int:
            mv.invoke(Methods.AbstractOperations_ToString_int);
            return ValType.String;
        case Number_uint:
            mv.invoke(Methods.AbstractOperations_ToString_long);
            return ValType.String;
        case Undefined:
            mv.pop();
            mv.aconst("undefined");
            return ValType.String;
        case Null:
            mv.pop();
            mv.aconst("null");
            return ValType.String;
        case Boolean:
            mv.invoke(Methods.Boolean_toString);
            return ValType.String;
        case String:
            mv.invoke(Methods.CharSequence_toString);
            return ValType.String;
        case Object:
            mv.loadExecutionContext();
            mv.swap();
            mv.invoke(Methods.AbstractOperations_ToFlatString);
            return ValType.String;
        case Any:
            mv.loadExecutionContext();
            mv.swap();
            mv.invoke(Methods.AbstractOperations_ToPropertyKey);
            return ValType.Any;
        case Empty:
        case Reference:
        default:
            throw new AssertionError();
        }
    }

    /**
     * stack: [propertyKey, function] {@literal ->} [propertyKey, function]
     * 
     * @param node
     *            the function or class node
     * @param propertyKeyType
     *            the property key value type
     * @param mv
     *            the code visitor
     */
    protected static void SetFunctionName(Node node, ValType propertyKeyType, CodeVisitor mv) {
        Jump hasOwnName = null;
        switch (hasOwnNameProperty(node)) {
        case HasOwn:
            return;
        case HasComputed:
            emitHasOwnNameProperty(mv);

            hasOwnName = new Jump();
            mv.ifne(hasOwnName);
        default:
        }

        // stack: [propertyKey, function] -> [propertyKey, function, function, propertyKey]
        mv.dup2();
        mv.swap();

        if (propertyKeyType == ValType.String) {
            mv.invoke(Methods.OrdinaryFunction_SetFunctionName_String);
        } else {
            assert propertyKeyType == ValType.Any;
            Jump isString = new Jump(), afterSetFunctionName = new Jump();
            mv.dup();
            mv.instanceOf(Types.String);
            mv.ifeq(isString);
            {
                // stack: [propertyKey, function, function, propertyKey] -> [propertyKey, function]
                mv.checkcast(Types.String);
                mv.invoke(Methods.OrdinaryFunction_SetFunctionName_String);
                mv.goTo(afterSetFunctionName);
            }
            {
                mv.mark(isString);
                mv.checkcast(Types.Symbol);
                mv.invoke(Methods.OrdinaryFunction_SetFunctionName_Symbol);
            }
            mv.mark(afterSetFunctionName);
        }

        if (hasOwnName != null) {
            mv.mark(hasOwnName);
        }
    }

    /**
     * stack: [function] {@literal ->} [function]
     * 
     * @param node
     *            the function or class node
     * @param name
     *            the new function name
     * @param mv
     *            the code visitor
     */
    protected static void SetFunctionName(Node node, Name name, CodeVisitor mv) {
        SetFunctionName(node, name.getIdentifier(), mv);
    }

    /**
     * stack: [function] {@literal ->} [function]
     * 
     * @param node
     *            the function or class node
     * @param name
     *            the new function name
     * @param mv
     *            the code visitor
     */
    protected static void SetFunctionName(Node node, String name, CodeVisitor mv) {
        Jump hasOwnName = null;
        switch (hasOwnNameProperty(node)) {
        case HasOwn:
            return;
        case HasComputed:
            emitHasOwnNameProperty(mv);

            hasOwnName = new Jump();
            mv.ifne(hasOwnName);
        default:
        }

        // stack: [function] -> [function, function, name]
        mv.dup();
        mv.aconst(name);
        // stack: [function, function, name] -> [function]
        mv.invoke(Methods.OrdinaryFunction_SetFunctionName_String);

        if (hasOwnName != null) {
            mv.mark(hasOwnName);
        }
    }

    private static void emitHasOwnNameProperty(CodeVisitor mv) {
        // stack: [function] -> [function, cx, function, "name"]
        mv.dup();
        mv.loadExecutionContext();
        mv.swap();
        mv.aconst("name");
        // stack: [function, cx, function, "name"] -> [function, hasOwn]
        mv.invoke(Methods.AbstractOperations_HasOwnProperty);
    }

    private enum NameProperty {
        HasOwn, HasComputed, None
    }

    private static NameProperty hasOwnNameProperty(Node node) {
        if (node instanceof FunctionNode) {
            return NameProperty.None;
        }

        assert node instanceof ClassDefinition : node.getClass();
        for (PropertyDefinition property : ((ClassDefinition) node).getProperties()) {
            if (property instanceof MethodDefinition) {
                MethodDefinition methodDefinition = (MethodDefinition) property;
                if (methodDefinition.isStatic()) {
                    String methodName = methodDefinition.getPropertyName().getName();
                    if (methodName == null) {
                        return NameProperty.HasComputed;
                    }
                    if ("name".equals(methodName)) {
                        return NameProperty.HasOwn;
                    }
                }
                if (!methodDefinition.getDecorators().isEmpty()) {
                    // Decorator expressions are like computed names.
                    return NameProperty.HasComputed;
                }
            } else if (property instanceof PropertyValueDefinition) {
                // Only static class properties are supported.
                PropertyValueDefinition valueDefinition = (PropertyValueDefinition) property;
                String methodName = valueDefinition.getPropertyName().getName();
                if (methodName == null) {
                    return NameProperty.HasComputed;
                }
                if ("name".equals(methodName)) {
                    return NameProperty.HasOwn;
                }
            }
        }
        return NameProperty.None;
    }

    /**
     * 14.5.14 Runtime Semantics: ClassDefinitionEvaluation
     * 
     * @param def
     *            the class definition node
     * @param className
     *            the class name or {@code null} if not present
     * @param mv
     *            the code visitor
     */
    protected final void ClassDefinitionEvaluation(ClassDefinition def, Name className, CodeVisitor mv) {
        mv.enterVariableScope();
        Variable<ArrayList<Callable>> classDecorators = null;
        boolean hasClassDecorators = !def.getDecorators().isEmpty();
        if (hasClassDecorators) {
            classDecorators = newDecoratorVariable("classDecorators", mv);
            evaluateDecorators(classDecorators, def.getDecorators(), mv);
        }

        mv.enterClassDefinition();

        // step 1 (not applicable)
        // steps 2-4
        BlockScope scope = def.getScope();
        assert (scope != null && scope.isPresent()) == (className != null);
        Variable<DeclarativeEnvironmentRecord> classScopeEnvRec = null;
        if (className != null) {
            // stack: [] -> [classScope]
            newDeclarativeEnvironment(scope, mv);

            classScopeEnvRec = mv.newVariable("classScopeEnvRec",
                    DeclarativeEnvironmentRecord.class);
            getEnvRec(classScopeEnvRec, mv);

            // stack: [classScope] -> [classScope]
            Name innerName = scope.resolveName(className, false);
            BindingOp<DeclarativeEnvironmentRecord> op = BindingOp.of(classScopeEnvRec, innerName);
            op.createImmutableBinding(classScopeEnvRec, innerName, true, mv);

            // stack: [classScope] -> []
            pushLexicalEnvironment(mv);
            mv.enterScope(def);
        }

        // steps 5-7
        // stack: [] -> [<constructorParent,proto>]
        Expression classHeritage = def.getHeritage();
        if (classHeritage == null) {
            mv.loadExecutionContext();
            mv.invoke(Methods.ScriptRuntime_getDefaultClassProto);
        } else if (classHeritage instanceof NullLiteral) {
            mv.loadExecutionContext();
            mv.invoke(Methods.ScriptRuntime_getClassProto_Null);
        } else {
            expressionBoxed(classHeritage, mv);
            mv.loadExecutionContext();
            mv.lineInfo(def);
            mv.invoke(Methods.ScriptRuntime_getClassProto);
        }

        // stack: [<constructorParent,proto>] -> [<constructorParent,proto>]
        Variable<OrdinaryObject> proto = mv.newVariable("proto", OrdinaryObject.class);
        mv.dup();
        mv.aload(1, Types.ScriptObject);
        mv.checkcast(Types.OrdinaryObject);
        mv.store(proto);

        // stack: [<constructorParent,proto>] -> [constructorParent, proto]
        mv.aload(0, Types.ScriptObject);
        mv.load(proto);

        // steps 8-9
        // stack: [constructorParent, proto] -> [constructorParent, proto, <rti>]
        MethodDefinition constructor = ConstructorMethod(def);
        assert constructor != null;
        MethodName method = codegen.compile(def);
        // Runtime Semantics: Evaluation -> MethodDefinition
        mv.invoke(method);

        // step 10 (not applicable)
        // steps 11-18
        // stack: [constructorParent, proto, <rti>] -> [F]
        mv.iconst(classHeritage != null);
        mv.loadExecutionContext();
        mv.lineInfo(def);
        mv.invoke(Methods.ScriptRuntime_EvaluateConstructorMethod);

        // stack: [F] -> []
        Variable<OrdinaryConstructorFunction> F = mv.newVariable("F",
                OrdinaryConstructorFunction.class);
        mv.store(F);

        Variable<ArrayList<Object>> methodDecorators = null;
        boolean hasMethodDecorators = HasDecorators(def);
        if (hasMethodDecorators) {
            methodDecorators = newDecoratorVariable("methodDecorators", mv);
        }

        if (!constructor.getDecorators().isEmpty()) {
            addDecoratorObject(methodDecorators, proto, mv);
            evaluateDecorators(methodDecorators, constructor.getDecorators(), mv);
            addDecoratorKey(methodDecorators, "constructor", mv);
        }

        // steps 19-21
        ClassPropertyEvaluation(codegen, def.getProperties(), F, proto, methodDecorators, mv);

        if (hasClassDecorators) {
            mv.load(F);
            mv.load(classDecorators);
            mv.loadExecutionContext();
            mv.lineInfo(def);
            mv.invoke(Methods.ScriptRuntime_EvaluateClassDecorators);
        }

        if (hasMethodDecorators) {
            mv.load(methodDecorators);
            mv.loadExecutionContext();
            mv.lineInfo(def);
            mv.invoke(Methods.ScriptRuntime_EvaluateClassMethodDecorators);
        }

        // steps 22-23 (moved)
        if (className != null) {
            // stack: [] -> []
            Name innerName = scope.resolveName(className, false);
            BindingOp<DeclarativeEnvironmentRecord> op = BindingOp.of(classScopeEnvRec, innerName);
            op.initializeBinding(classScopeEnvRec, innerName, F, mv);

            mv.exitScope();
            popLexicalEnvironment(mv);
        }

        // stack: [] -> [F]
        mv.load(F);

        mv.exitVariableScope();

        // step 24 (return F)
        mv.exitClassDefinition();
    }

    protected final <T> Variable<ArrayList<T>> newDecoratorVariable(String name, CodeVisitor mv) {
        Variable<ArrayList<T>> var = mv.newVariable(name, ArrayList.class).uncheckedCast();
        mv.anew(Types.ArrayList, Methods.ArrayList_init);
        mv.store(var);
        return var;
    }

    protected final void addDecoratorObject(Variable<ArrayList<Object>> var, Variable<? extends ScriptObject> object,
            CodeVisitor mv) {
        mv.load(var);
        mv.load(object);
        mv.invoke(Methods.ArrayList_add);
        mv.pop();
    }

    protected final void addDecoratorKey(Variable<ArrayList<Object>> var, String propertyKey, CodeVisitor mv) {
        mv.load(var);
        mv.aconst(propertyKey);
        mv.invoke(Methods.ArrayList_add);
        mv.pop();
    }

    protected final void addDecoratorKey(Variable<ArrayList<Object>> var, ValType type, CodeVisitor mv) {
        mv.dup(type);
        mv.load(var);
        mv.swap(type.toType(), Types.ArrayList);
        mv.invoke(Methods.ArrayList_add);
        mv.pop();
    }

    protected final <T> void evaluateDecorators(Variable<ArrayList<T>> var, List<Expression> decorators,
            CodeVisitor mv) {
        for (Expression decorator : decorators) {
            mv.load(var);
            expressionBoxed(decorator, mv);
            mv.loadExecutionContext();
            mv.lineInfo(decorator);
            mv.invoke(Methods.ScriptRuntime_CheckCallable);
            mv.invoke(Methods.ArrayList_add);
            mv.pop();
        }
    }

    /**
     * 14.4 Generator Function Definitions
     * <p>
     * 14.4.14 Runtime Semantics: Evaluation
     * <ul>
     * <li>YieldExpression : yield * AssignmentExpression
     * </ul>
     * <p>
     * stack: [value] {@literal ->} [value']
     * 
     * @param node
     *            the expression node
     * @param mv
     *            the code visitor
     */
    protected final void delegatedYield(Expression node, CodeVisitor mv) {
        if (!mv.isAsync()) {
            delegatedYield(node, (iterator, received) -> {
                IteratorNext(node, iterator, received, mv);
            } , (iterator, received) -> {
                mv.loadExecutionContext();
                mv.load(iterator);
                mv.load(received);
                mv.checkcast(Types.ScriptException);
                mv.invoke(Methods.ScriptRuntime_yieldThrowCompletion);
            } , (iterator, received) -> {
                mv.loadExecutionContext();
                mv.load(iterator);
                mv.load(received);
                mv.checkcast(Types.ReturnValue);
                mv.invoke(Methods.ScriptRuntime_yieldReturnCompletion);
            } , mv);
        } else {
            delegatedYield(node, (iterator, received) -> {
                IteratorNext(node, iterator, received, mv);
                await(node, mv);
                // FIXME: spec bug - missing type check
                requireObjectResult(node, "next", mv);
            } , (iterator, received) -> {
                mv.enterVariableScope();
                Variable<Callable> throwMethod = mv.newVariable("throwMethod", Callable.class);

                GetMethod(node, iterator, "throw", mv);
                mv.store(throwMethod);

                Jump noThrow = new Jump(), nextYield = new Jump();
                mv.load(throwMethod);
                mv.ifnull(noThrow);
                {
                    InvokeMethod(node, mv, throwMethod, iterator, __ -> {
                        mv.load(received);
                        mv.checkcast(Types.ScriptException);
                        mv.invoke(Methods.ScriptException_getValue);
                    });
                    await(node, mv);
                    requireObjectResult(node, "throw", mv);
                    mv.goTo(nextYield);
                }
                mv.mark(noThrow);
                {
                    asyncIteratorClose(node, iterator, mv);

                    reportPropertyNotCallable(node, "throw", mv);
                    mv.athrow();
                }
                mv.mark(nextYield);

                mv.exitVariableScope();
            } , (iterator, received) -> {
                mv.enterVariableScope();
                Variable<Callable> returnMethod = mv.newVariable("returnMethod", Callable.class);

                GetMethod(node, iterator, "return", mv);
                mv.store(returnMethod);

                Jump noReturn = new Jump(), nextYield = new Jump();
                mv.load(returnMethod);
                mv.ifnull(noReturn);
                {
                    InvokeMethod(node, mv, returnMethod, iterator, __ -> {
                        mv.load(received);
                        mv.checkcast(Types.ReturnValue);
                        mv.invoke(Methods.ReturnValue_getValue);
                    });
                    await(node, mv);
                    requireObjectResult(node, "return", mv);
                    mv.goTo(nextYield);
                }
                mv.mark(noReturn);
                {
                    mv.anull();
                }
                mv.mark(nextYield);

                mv.exitVariableScope();
            } , mv);
        }
    }

    private void delegatedYield(Expression node, BiConsumer<Variable<ScriptObject>, Variable<Object>> iterNext,
            BiConsumer<Variable<ScriptObject>, Variable<Object>> iterThrow,
            BiConsumer<Variable<ScriptObject>, Variable<Object>> iterReturn, CodeVisitor mv) {
        Jump iteratorNext = new Jump();
        Jump generatorYield = new Jump();
        Jump generatorYieldOrReturn = new Jump();
        Jump done = new Jump();

        mv.lineInfo(node);
        mv.enterVariableScope();
        Variable<ScriptObject> iterator = mv.newVariable("iterator", ScriptObject.class);
        Variable<ScriptObject> innerResult = mv.newVariable("innerResult", ScriptObject.class);
        Variable<Object> received = mv.newVariable("received", Object.class);

        /* steps 3-4 */
        // stack: [value] -> []
        mv.loadExecutionContext();
        mv.swap();
        mv.invoke(Methods.AbstractOperations_GetIterator);
        mv.store(iterator);

        /* step 5 */
        // stack: [] -> []
        mv.loadUndefined();
        mv.store(received);

        /* step 6.a.i-6.a.ii */
        // stack: [] -> []
        mv.mark(iteratorNext);
        iterNext.accept(iterator, received);
        mv.store(innerResult);

        /* steps 6.a.iii-6.a.v */
        // stack: [] -> []
        IteratorComplete(node, innerResult, mv);
        mv.ifne(done);

        /* step 6.a.vi */
        // stack: [] -> [Object(innerResult)]
        // force stack top to Object-type
        mv.mark(generatorYield);
        mv.load(innerResult);
        mv.checkcast(Types.Object);
        mv.suspend();
        mv.store(received);

        /* step 6.b */
        Jump isException = new Jump();
        mv.load(received);
        mv.instanceOf(Types.ScriptException);
        mv.ifeq(isException);
        {
            /* steps 6.b.iii.1-4, 6.b.iv */
            iterThrow.accept(iterator, received);
            mv.store(innerResult);

            mv.goTo(generatorYieldOrReturn);
        }
        mv.mark(isException);

        /* step 6.c */
        mv.load(received);
        mv.instanceOf(Types.ReturnValue);
        mv.ifeq(iteratorNext);
        {
            /* steps 6.c.i-vii */
            iterReturn.accept(iterator, received);
            mv.store(innerResult);

            mv.load(innerResult);
            mv.ifnonnull(generatorYieldOrReturn);
            {
                /* step 6.c.iv */
                mv.popStack();
                mv.returnCompletion(__ -> {
                    mv.load(received);
                    mv.checkcast(Types.ReturnValue);
                    mv.invoke(Methods.ReturnValue_getValue);
                });
            }
        }

        mv.mark(generatorYieldOrReturn);

        /* steps 6.b.iii.5-6, 6.c.viii-ix */
        IteratorComplete(node, innerResult, mv);
        mv.ifeq(generatorYield);

        /* step 6.b.iii.7, 6.c.x */
        mv.popStack();
        mv.returnCompletion(__ -> {
            IteratorValue(node, innerResult, mv);
        });

        /* step 6.a.v */
        mv.mark(done);
        IteratorValue(node, innerResult, mv);

        mv.exitVariableScope();
    }

    /**
     * 14.4 Generator Function Definitions
     * <p>
     * 14.4.14 Runtime Semantics: Evaluation
     * <ul>
     * <li>YieldExpression : yield
     * <li>YieldExpression : yield AssignmentExpression
     * </ul>
     * <p>
     * stack: [value] {@literal ->} [value']
     * 
     * @param node
     *            the expression node
     * @param mv
     *            the code visitor
     */
    protected final void yield(Expression node, CodeVisitor mv) {
        mv.lineInfo(node);
        mv.loadExecutionContext();
        mv.swap();
        mv.iconst(false);
        mv.invoke(Methods.AbstractOperations_CreateIterResultObject);

        // force stack top to Object-type
        mv.checkcast(Types.Object);
        mv.suspend();

        // check for exception
        throwAfterResume(mv);

        // check for return value
        returnAfterResume(mv);
    }

    /**
     * Extension: Async Function Definitions
     * <p>
     * stack: [value] {@literal ->} [value']
     * 
     * @param node
     *            the expression node
     * @param mv
     *            the code visitor
     */
    protected final void await(Node node, CodeVisitor mv) {
        // stack: [value] -> [value']
        mv.loadExecutionContext();
        mv.swap();
        mv.lineInfo(node);
        mv.invoke(Methods.AsyncAbstractOperations_AsyncFunctionAwait);

        // Reserve stack space for await return value.
        mv.anull();
        mv.suspend();

        // check for exception
        throwAfterResume(mv);
    }

    private void throwAfterResume(CodeVisitor mv) {
        Jump isException = new Jump();
        mv.dup();
        mv.instanceOf(Types.ScriptException);
        mv.ifeq(isException);
        {
            mv.checkcast(Types.ScriptException);
            mv.athrow();
        }
        mv.mark(isException);
    }

    private void returnAfterResume(CodeVisitor mv) {
        Jump isReturn = new Jump();
        mv.dup();
        mv.instanceOf(Types.ReturnValue);
        mv.ifeq(isReturn);
        {
            mv.checkcast(Types.ReturnValue);
            mv.invoke(Methods.ReturnValue_getValue);
            if (mv.getStackSize() == 1) {
                mv.returnCompletion();
            } else {
                mv.enterVariableScope();
                Variable<Object> returnValue = mv.newVariable("returnValue", Object.class);
                mv.store(returnValue);
                mv.popStack();
                mv.returnCompletion(returnValue);
                mv.exitVariableScope();
            }
        }
        mv.mark(isReturn);
    }

    /**
     * IteratorNext ( iterator, value )
     * 
     * @param node
     *            the ast node
     * @param iterator
     *            the script iterator object
     * @param mv
     *            the code visitor
     */
    protected final void IteratorNext(Node node, Variable<ScriptObject> iterator, CodeVisitor mv) {
        mv.loadExecutionContext();
        mv.load(iterator);
        mv.lineInfo(node);
        mv.invoke(Methods.AbstractOperations_IteratorNext);
    }

    /**
     * IteratorNext ( iterator, value )
     * 
     * @param node
     *            the ast node
     * @param iterator
     *            the script iterator object
     * @param value
     *            the value to pass to the next() function
     * @param mv
     *            the code visitor
     */
    protected final void IteratorNext(Node node, Variable<ScriptObject> iterator, Value<Object> value, CodeVisitor mv) {
        mv.loadExecutionContext();
        mv.load(iterator);
        mv.load(value);
        mv.lineInfo(node);
        mv.invoke(Methods.AbstractOperations_IteratorNext_Object);
    }

    /**
     * IteratorComplete (iterResult)
     * 
     * @param node
     *            the ast node
     * @param iterResult
     *            the iterator result object
     * @param mv
     *            the code visitor
     */
    protected final void IteratorComplete(Node node, Variable<ScriptObject> iterResult, CodeVisitor mv) {
        mv.loadExecutionContext();
        mv.load(iterResult);
        mv.lineInfo(node);
        mv.invoke(Methods.AbstractOperations_IteratorComplete);
    }

    /**
     * IteratorValue (iterResult)
     * 
     * @param node
     *            the ast node
     * @param iterResult
     *            the iterator result object
     * @param mv
     *            the code visitor
     */
    protected final void IteratorValue(Node node, Variable<ScriptObject> iterResult, CodeVisitor mv) {
        mv.loadExecutionContext();
        mv.load(iterResult);
        mv.lineInfo(node);
        mv.invoke(Methods.AbstractOperations_IteratorValue);
    }

    /**
     * GetMethod (O, P)
     * 
     * @param node
     *            the ast node
     * @param object
     *            the script object
     * @param methodName
     *            the method name
     * @param mv
     *            the code visitor
     */
    final void GetMethod(Node node, Variable<ScriptObject> object, String methodName, CodeVisitor mv) {
        mv.loadExecutionContext();
        mv.load(object);
        mv.aconst(methodName);
        mv.lineInfo(node);
        mv.invoke(Methods.AbstractOperations_GetMethod);
    }

    /**
     * Emit: {@code method.call(cx, thisValue, arguments)}
     * 
     * @param node
     *            the ast node
     * @param mv
     *            the code visitor
     * @param method
     *            the callable object
     * @param thisValue
     *            the call this-value
     * @param arguments
     *            the method call arguments
     */
    final void InvokeMethod(Node node, CodeVisitor mv, Value<Callable> method, Value<?> thisValue,
            Value<?>... arguments) {
        mv.load(method);
        mv.loadExecutionContext();
        mv.load(thisValue);
        if (arguments.length == 0) {
            mv.get(Fields.ScriptRuntime_EMPTY_ARRAY);
        } else {
            mv.anewarray(Types.Object, arguments);
        }
        mv.lineInfo(node);
        mv.invokedynamic(Bootstrap.getCallName(), Bootstrap.getCallMethodDescriptor(), Bootstrap.getCallBootstrap());
    }

    /**
     * Emit:
     * 
     * <pre>
     * Callable returnMethod = GetMethod(cx, iterator, "return");
     * if (returnMethod != null) {
     *   Object innerResult = returnMethod.call(cx, iterator);
     *   await;
     *   if (!(innerResult instanceof ScriptObject)) {
     *     throw newTypeError(cx, Messages.Key.NotObjectTypeReturned, "return");
     *   }
     * }
     * </pre>
     * 
     * @param node
     *            the ast node
     * @param iterator
     *            the script iterator object
     * @param mv
     *            the code visitor
     */
    final void asyncIteratorClose(Node node, Variable<ScriptObject> iterator, CodeVisitor mv) {
        IteratorClose(node, iterator, returnMethod -> {
            InvokeMethod(node, mv, returnMethod, iterator);
            await(node, mv);
            requireObjectResult(node, "return", mv);
            mv.pop();
        } , mv);
    }

    /**
     * Emit:
     * 
     * <pre>
     * Callable returnMethod = GetMethod(cx, iterator, "return");
     * if (returnMethod != null) {
     *   try {
     *     returnMethod.call(cx, iterator);
     *     await;
     *   } catch (ScriptException e) {
     *     if (throwable != e) {
     *       throwable.addSuppressed(e);
     *     }
     *   }
     * }
     * </pre>
     * 
     * @param node
     *            the ast node
     * @param iterator
     *            the script iterator object
     * @param mv
     *            the code visitor
     */
    final void asyncIteratorClose(Node node, Variable<ScriptObject> iterator, Variable<? extends Throwable> throwable,
            CodeVisitor mv) {
        IteratorClose(node, iterator, returnMethod -> {
            TryCatchLabel startCatch = new TryCatchLabel();
            TryCatchLabel endCatch = new TryCatchLabel(), handlerCatch = new TryCatchLabel();
            Jump noException = new Jump();

            mv.mark(startCatch);
            {
                InvokeMethod(node, mv, returnMethod, iterator);
                await(node, mv);
                mv.pop();

                mv.goTo(noException);
            }
            mv.mark(endCatch);

            mv.catchHandler(handlerCatch, Types.ScriptException);
            {
                mv.enterVariableScope();
                Variable<ScriptException> exception = mv.newVariable("exception", ScriptException.class);
                mv.store(exception);

                mv.load(throwable);
                mv.load(exception);
                mv.ifacmpeq(noException);
                {
                    mv.load(throwable);
                    mv.load(exception);
                    mv.invoke(Methods.Throwable_addSuppressed);
                }

                mv.exitVariableScope();
            }
            mv.tryCatch(startCatch, endCatch, handlerCatch, Types.ScriptException);
            mv.mark(noException);
        } , mv);
    }

    /**
     * <pre>
     * Callable returnMethod = GetMethod(cx, iterator, "return");
     * if (returnMethod != null) {
     *   &lt;invoke return&gt;
     * }
     * </pre>
     * 
     * @param node
     *            the ast node
     * @param iterator
     *            the script iterator object
     * @param invokeReturn
     *            the code snippet to invoke return()
     * @param mv
     *            the code visitor
     */
    final void IteratorClose(Node node, Variable<ScriptObject> iterator, Consumer<Variable<Callable>> invokeReturn,
            CodeVisitor mv) {
        mv.enterVariableScope();
        Variable<Callable> returnMethod = mv.newVariable("returnMethod", Callable.class);

        GetMethod(node, iterator, "return", mv);
        mv.store(returnMethod);

        Jump done = new Jump();
        mv.load(returnMethod);
        mv.ifnull(done);
        {
            invokeReturn.accept(returnMethod);
        }
        mv.mark(done);

        mv.exitVariableScope();
    }

    /**
     * Extension: Async Generator Function Definitions
     * <p>
     * stack: [value] {@literal ->} []
     * 
     * @param node
     *            the ast node
     * @param methodName
     *            the method name
     * @param mv
     *            the code visitor
     */
    protected final void requireObjectResult(Node node, String methodName, CodeVisitor mv) {
        mv.aconst(methodName);
        mv.loadExecutionContext();
        mv.lineInfo(node);
        mv.invoke(Methods.ScriptRuntime_requireObjectResult);
    }

    private void reportPropertyNotCallable(Node node, String methodName, CodeVisitor mv) {
        mv.aconst(methodName);
        mv.loadExecutionContext();
        mv.lineInfo(node);
        mv.invoke(Methods.ScriptRuntime_reportPropertyNotCallable);
    }
}
