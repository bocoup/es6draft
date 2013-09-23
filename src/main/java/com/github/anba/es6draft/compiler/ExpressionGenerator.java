/**
 * Copyright (c) 2012-2013 André Bargull
 * Alle Rechte vorbehalten / All Rights Reserved.  Use is subject to license terms.
 *
 * <https://github.com/anba/es6draft>
 */
package com.github.anba.es6draft.compiler;

import static com.github.anba.es6draft.semantics.StaticSemantics.BoundNames;
import static com.github.anba.es6draft.semantics.StaticSemantics.Substitutions;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.objectweb.asm.Label;
import org.objectweb.asm.Type;

import com.github.anba.es6draft.ast.*;
import com.github.anba.es6draft.ast.synthetic.ElementAccessorValue;
import com.github.anba.es6draft.ast.synthetic.ExpressionMethod;
import com.github.anba.es6draft.ast.synthetic.IdentifierValue;
import com.github.anba.es6draft.ast.synthetic.PropertyAccessorValue;
import com.github.anba.es6draft.ast.synthetic.SpreadArrayLiteral;
import com.github.anba.es6draft.ast.synthetic.SpreadElementMethod;
import com.github.anba.es6draft.ast.synthetic.SuperExpressionValue;
import com.github.anba.es6draft.compiler.CodeGenerator.FunctionName;
import com.github.anba.es6draft.compiler.DefaultCodeGenerator.ValType;
import com.github.anba.es6draft.compiler.InstructionVisitor.FieldDesc;
import com.github.anba.es6draft.compiler.InstructionVisitor.FieldType;
import com.github.anba.es6draft.compiler.InstructionVisitor.MethodDesc;
import com.github.anba.es6draft.compiler.InstructionVisitor.MethodType;
import com.github.anba.es6draft.compiler.InstructionVisitor.Variable;
import com.github.anba.es6draft.runtime.internal.SimpleBootstrap;
import com.github.anba.es6draft.runtime.types.ScriptObject;
import com.github.anba.es6draft.runtime.types.builtins.ExoticArray;

/**
 *
 */
class ExpressionGenerator extends DefaultCodeGenerator<ValType, ExpressionVisitor> {
    private static class Fields {
        static final FieldDesc Intrinsics_ObjectPrototype = FieldDesc.create(FieldType.Static,
                Types.Intrinsics, "ObjectPrototype", Types.Intrinsics);

        static final FieldDesc ScriptRuntime_EMPTY_ARRAY = FieldDesc.create(FieldType.Static,
                Types.ScriptRuntime, "EMPTY_ARRAY", Types.Object_);
    }

    private static class Methods {
        // class: AbstractOperations
        static final MethodDesc AbstractOperations_Put = MethodDesc.create(MethodType.Static,
                Types.AbstractOperations, "Put", Type.getMethodType(Type.VOID_TYPE,
                        Types.ExecutionContext, Types.ScriptObject, Types.String, Types.Object,
                        Type.BOOLEAN_TYPE));

        // class: Callable
        static final MethodDesc Callable_call = MethodDesc.create(MethodType.Interface,
                Types.Callable, "call", Type.getMethodType(Types.Object, Types.ExecutionContext,
                        Types.Object, Types.Object_));

        // class: Eval
        static final MethodDesc Eval_directEval = MethodDesc.create(MethodType.Static, Types.Eval,
                "directEval", Type.getMethodType(Types.Object, Types.Object,
                        Types.ExecutionContext, Type.BOOLEAN_TYPE, Type.BOOLEAN_TYPE));

        // class: EnvironmentRecord
        static final MethodDesc EnvironmentRecord_createMutableBinding = MethodDesc.create(
                MethodType.Interface, Types.EnvironmentRecord, "createMutableBinding",
                Type.getMethodType(Type.VOID_TYPE, Types.String, Type.BOOLEAN_TYPE));

        // class: ExecutionContext
        static final MethodDesc ExecutionContext_getRealm = MethodDesc.create(MethodType.Virtual,
                Types.ExecutionContext, "getRealm", Type.getMethodType(Types.Realm));

        static final MethodDesc ExecutionContext_thisResolution = MethodDesc.create(
                MethodType.Virtual, Types.ExecutionContext, "thisResolution",
                Type.getMethodType(Types.Object));

        // class: ExoticArray
        static final MethodDesc ExoticArray_ArrayCreate = MethodDesc.create(MethodType.Static,
                Types.ExoticArray, "ArrayCreate",
                Type.getMethodType(Types.ExoticArray, Types.ExecutionContext, Type.LONG_TYPE));

        // class: LexicalEnvironment
        static final MethodDesc LexicalEnvironment_getEnvRec = MethodDesc.create(
                MethodType.Virtual, Types.LexicalEnvironment, "getEnvRec",
                Type.getMethodType(Types.EnvironmentRecord));

        // class: OrdinaryObject
        static final MethodDesc OrdinaryObject_ObjectCreate = MethodDesc.create(MethodType.Static,
                Types.OrdinaryObject, "ObjectCreate",
                Type.getMethodType(Types.OrdinaryObject, Types.ExecutionContext, Types.Intrinsics));

        // class: Realm
        static final MethodDesc Realm_getBuiltinEval = MethodDesc.create(MethodType.Virtual,
                Types.Realm, "getBuiltinEval", Type.getMethodType(Types.Callable));

        // class: Reference
        static final MethodDesc Reference_GetMethodCallThisValue = MethodDesc.create(
                MethodType.Virtual, Types.Reference, "GetMethodCallThisValue",
                Type.getMethodType(Types.ScriptObject, Types.ExecutionContext));

        static final MethodDesc Reference_GetThisValue = MethodDesc.create(MethodType.Virtual,
                Types.Reference, "GetThisValue",
                Type.getMethodType(Types.Object, Types.ExecutionContext));

        static final MethodDesc Reference_GetValue = MethodDesc.create(MethodType.Virtual,
                Types.Reference, "GetValue",
                Type.getMethodType(Types.Object, Types.ExecutionContext));

        static final MethodDesc Reference_PutValue = MethodDesc.create(MethodType.Virtual,
                Types.Reference, "PutValue",
                Type.getMethodType(Type.VOID_TYPE, Types.Object, Types.ExecutionContext));

        // class: ScriptRuntime
        static final MethodDesc ScriptRuntime_add = MethodDesc.create(MethodType.Static,
                Types.ScriptRuntime, "add", Type.getMethodType(Types.Object, Types.Object,
                        Types.Object, Types.ExecutionContext));

        static final MethodDesc ScriptRuntime_add_str = MethodDesc.create(MethodType.Static,
                Types.ScriptRuntime, "add", Type.getMethodType(Types.CharSequence,
                        Types.CharSequence, Types.CharSequence, Types.ExecutionContext));

        static final MethodDesc ScriptRuntime_delete = MethodDesc.create(MethodType.Static,
                Types.ScriptRuntime, "delete",
                Type.getMethodType(Type.BOOLEAN_TYPE, Types.Object, Types.ExecutionContext));

        static final MethodDesc ScriptRuntime_in = MethodDesc.create(MethodType.Static,
                Types.ScriptRuntime, "in", Type.getMethodType(Type.BOOLEAN_TYPE, Types.Object,
                        Types.Object, Types.ExecutionContext));

        static final MethodDesc ScriptRuntime_typeof = MethodDesc.create(MethodType.Static,
                Types.ScriptRuntime, "typeof",
                Type.getMethodType(Types.String, Types.Object, Types.ExecutionContext));

        static final MethodDesc ScriptRuntime_yield = MethodDesc.create(MethodType.Static,
                Types.ScriptRuntime, "yield",
                Type.getMethodType(Types.Object, Types.Object, Types.ExecutionContext));

        static final MethodDesc ScriptRuntime_delegatedYield = MethodDesc.create(MethodType.Static,
                Types.ScriptRuntime, "delegatedYield",
                Type.getMethodType(Types.Object, Types.Object, Types.ExecutionContext));

        static final MethodDesc ScriptRuntime_instanceOfOperator = MethodDesc.create(
                MethodType.Static, Types.ScriptRuntime, "instanceOfOperator", Type.getMethodType(
                        Type.BOOLEAN_TYPE, Types.Object, Types.Object, Types.ExecutionContext));

        static final MethodDesc ScriptRuntime_ArrayAccumulationSpreadElement = MethodDesc.create(
                MethodType.Static, Types.ScriptRuntime, "ArrayAccumulationSpreadElement", Type
                        .getMethodType(Type.INT_TYPE, Types.ScriptObject, Type.INT_TYPE,
                                Types.Object, Types.ExecutionContext));

        static final MethodDesc ScriptRuntime_CheckCallable = MethodDesc.create(MethodType.Static,
                Types.ScriptRuntime, "CheckCallable",
                Type.getMethodType(Types.Callable, Types.Object, Types.ExecutionContext));

        static final MethodDesc ScriptRuntime_defineProperty__int = MethodDesc.create(
                MethodType.Static, Types.ScriptRuntime, "defineProperty", Type.getMethodType(
                        Type.VOID_TYPE, Types.ScriptObject, Type.INT_TYPE, Types.Object,
                        Types.ExecutionContext));

        static final MethodDesc ScriptRuntime_ensureObject = MethodDesc.create(MethodType.Static,
                Types.ScriptRuntime, "ensureObject",
                Type.getMethodType(Types.ScriptObject, Types.Object, Types.ExecutionContext));

        static final MethodDesc ScriptRuntime_EvaluateArrowFunction = MethodDesc.create(
                MethodType.Static, Types.ScriptRuntime, "EvaluateArrowFunction", Type
                        .getMethodType(Types.OrdinaryFunction, Types.RuntimeInfo$Function,
                                Types.ExecutionContext));

        static final MethodDesc ScriptRuntime_EvaluateConstructorCall = MethodDesc.create(
                MethodType.Static, Types.ScriptRuntime, "EvaluateConstructorCall", Type
                        .getMethodType(Types.Object, Types.Object, Types.Object_,
                                Types.ExecutionContext));

        static final MethodDesc ScriptRuntime_EvaluateFunctionExpression = MethodDesc.create(
                MethodType.Static, Types.ScriptRuntime, "EvaluateFunctionExpression", Type
                        .getMethodType(Types.OrdinaryFunction, Types.RuntimeInfo$Function,
                                Types.ExecutionContext));

        static final MethodDesc ScriptRuntime_EvaluateGeneratorComprehension = MethodDesc.create(
                MethodType.Static, Types.ScriptRuntime, "EvaluateGeneratorComprehension",
                Type.getMethodType(Types.ScriptObject, Types.MethodHandle, Types.ExecutionContext));

        static final MethodDesc ScriptRuntime_EvaluateGeneratorExpression = MethodDesc.create(
                MethodType.Static, Types.ScriptRuntime, "EvaluateGeneratorExpression", Type
                        .getMethodType(Types.OrdinaryGenerator, Types.RuntimeInfo$Function,
                                Types.ExecutionContext));

        static final MethodDesc ScriptRuntime_EvaluateMethodCall = MethodDesc.create(
                MethodType.Static, Types.ScriptRuntime, "EvaluateMethodCall", Type.getMethodType(
                        Types.Object, Types.Object_, Types.Reference, Types.ScriptObject,
                        Types.ExecutionContext));

        static final MethodDesc ScriptRuntime_EvaluateMethodCallWithBaseObject = MethodDesc.create(
                MethodType.Static, Types.ScriptRuntime, "EvaluateMethodCallWithBaseObject", Type
                        .getMethodType(Types.Object, Types.Object_, Types.Reference,
                                Types.ScriptObject, Types.ExecutionContext));

        static final MethodDesc ScriptRuntime_getElement = MethodDesc.create(MethodType.Static,
                Types.ScriptRuntime, "getElement", Type.getMethodType(Types.Reference,
                        Types.Object, Types.Object, Types.ExecutionContext, Type.BOOLEAN_TYPE));

        static final MethodDesc ScriptRuntime_getElementValue = MethodDesc.create(
                MethodType.Static, Types.ScriptRuntime, "getElementValue", Type.getMethodType(
                        Types.Object, Types.Object, Types.Object, Types.ExecutionContext,
                        Type.BOOLEAN_TYPE));

        static final MethodDesc ScriptRuntime_getProperty = MethodDesc.create(MethodType.Static,
                Types.ScriptRuntime, "getProperty", Type.getMethodType(Types.Reference,
                        Types.Object, Types.String, Types.ExecutionContext, Type.BOOLEAN_TYPE));

        static final MethodDesc ScriptRuntime_getPropertyValue = MethodDesc.create(
                MethodType.Static, Types.ScriptRuntime, "getPropertyValue", Type.getMethodType(
                        Types.Object, Types.Object, Types.String, Types.ExecutionContext,
                        Type.BOOLEAN_TYPE));

        static final MethodDesc ScriptRuntime_MakeSuperReference = MethodDesc.create(
                MethodType.Static, Types.ScriptRuntime, "MakeSuperReference", Type.getMethodType(
                        Types.Reference, Types.ExecutionContext, Types.Object, Type.BOOLEAN_TYPE));

        static final MethodDesc ScriptRuntime_MakeStringSuperReference = MethodDesc.create(
                MethodType.Static, Types.ScriptRuntime, "MakeSuperReference", Type.getMethodType(
                        Types.Reference, Types.ExecutionContext, Types.String, Type.BOOLEAN_TYPE));

        static final MethodDesc ScriptRuntime_OrdinaryInvokeGet = MethodDesc.create(
                MethodType.Static, Types.ScriptRuntime, "OrdinaryInvokeGet", Type.getMethodType(
                        Types.Object, Types.ScriptObject, Types.Reference, Types.Object,
                        Types.ExecutionContext));

        static final MethodDesc ScriptRuntime_PrepareForTailCall = MethodDesc.create(
                MethodType.Static, Types.ScriptRuntime, "PrepareForTailCall",
                Type.getMethodType(Types.Object_, Types.Object_, Types.Object, Types.Callable));

        static final MethodDesc ScriptRuntime_RegExp = MethodDesc.create(MethodType.Static,
                Types.ScriptRuntime, "RegExp", Type.getMethodType(Types.ScriptObject,
                        Types.ExecutionContext, Types.String, Types.String));

        static final MethodDesc ScriptRuntime_SpreadArray = MethodDesc.create(MethodType.Static,
                Types.ScriptRuntime, "SpreadArray",
                Type.getMethodType(Types.Object_, Types.Object, Types.ExecutionContext));

        static final MethodDesc ScriptRuntime_toFlatArray = MethodDesc.create(MethodType.Static,
                Types.ScriptRuntime, "toFlatArray",
                Type.getMethodType(Types.Object_, Types.Object_, Types.ExecutionContext));

        // class: StringBuilder
        static final MethodDesc StringBuilder_append_Charsequence = MethodDesc.create(
                MethodType.Virtual, Types.StringBuilder, "append",
                Type.getMethodType(Types.StringBuilder, Types.CharSequence));

        static final MethodDesc StringBuilder_append_String = MethodDesc.create(MethodType.Virtual,
                Types.StringBuilder, "append",
                Type.getMethodType(Types.StringBuilder, Types.String));

        static final MethodDesc StringBuilder_init = MethodDesc.create(MethodType.Special,
                Types.StringBuilder, "<init>", Type.getMethodType(Type.VOID_TYPE));

        static final MethodDesc StringBuilder_toString = MethodDesc.create(MethodType.Virtual,
                Types.StringBuilder, "toString", Type.getMethodType(Types.String));
    }

    private final IdentifierResolution identifierResolution;

    public ExpressionGenerator(CodeGenerator codegen) {
        super(codegen);
        this.identifierResolution = new IdentifierResolution();
    }

    private static final Object[] EMPTY_BSM_ARGS = new Object[] {};

    private void invokeDynamicOperator(BinaryExpression.Operator operator, ExpressionVisitor mv) {
        mv.invokedynamic(SimpleBootstrap.getName(operator),
                SimpleBootstrap.getMethodDescriptor(operator),
                SimpleBootstrap.getBootstrap(operator), EMPTY_BSM_ARGS);
    }

    /**
     * ref = `eval` {@code node}<br>
     * GetValue(ref)<br>
     */
    private ValType evalAndGetValue(Expression node, ExpressionVisitor mv) {
        Expression valueNode = node.asValue();
        ValType type = valueNode.accept(this, mv);
        GetValue(valueNode, type, mv);
        return (type != ValType.Reference ? type : ValType.Any);
    }

    private void GetValue(Expression node, ValType type, ExpressionVisitor mv) {
        if (type == ValType.Reference) {
            mv.loadExecutionContext();
            mv.invoke(Methods.Reference_GetValue);
        }
    }

    private void PutValue(Expression node, ValType type, ExpressionVisitor mv) {
        assert type == ValType.Reference : "lhs is not reference: " + type;

        mv.loadExecutionContext();
        mv.invoke(Methods.Reference_PutValue);
    }

    /**
     * stack: [envRec] -> [envRec]
     */
    private void createMutableBinding(String name, boolean deletable, ExpressionVisitor mv) {
        mv.dup();
        mv.aconst(name);
        mv.iconst(deletable);
        mv.invoke(Methods.EnvironmentRecord_createMutableBinding);
    }

    private static boolean isPropertyReference(Expression base, ValType type) {
        return type == ValType.Reference && !(base instanceof Identifier);
    }

    private static boolean isEnclosedByWithStatement(ExpressionVisitor mv) {
        for (Scope scope = mv.getScope();;) {
            Scope nextScope;
            if (scope instanceof BlockScope) {
                BlockScope blockScope = (BlockScope) scope;
                if (blockScope.isDynamic()) {
                    return true;
                }
                nextScope = scope.getParent();
            } else if (scope instanceof FunctionScope) {
                FunctionScope funScope = (FunctionScope) scope;
                assert funScope.getParent() == null;
                nextScope = funScope.getEnclosingScope();
            } else {
                assert false : "unknown scope class: " + scope.getClass().getName();
                return false;
            }
            if (nextScope == null) {
                ScopedNode node = scope.getNode();
                if (node instanceof Script) {
                    // FIXME: need to record with-statement information instead of simply returning
                    // 'true' for all eval-scripts
                    return ((Script) node).isEvalScript();
                }
                return false;
            }
            scope = nextScope;
        }
    }

    /**
     * [11.2.3 EvaluateCall Abstract Operation]
     */
    private void EvaluateCall(Expression call, Expression base, ValType type,
            List<Expression> arguments, boolean directEval, ExpressionVisitor mv) {
        if (type == ValType.Reference) {
            if (isPropertyReference(base, type)) {
                EvaluateMethodCall(call, base, type, arguments, mv);
            } else if (isEnclosedByWithStatement(mv)) {
                EvaluateCallWithIdentRef(call, base, type, arguments, directEval, mv);
            } else {
                EvaluateCallIdentRef(call, base, type, arguments, directEval, mv);
            }
        } else {
            EvaluateCallWithValue(call, base, type, arguments, mv);
        }
    }

    /**
     * [11.2.3 EvaluateMethodCall Abstract Operation]
     */
    private void EvaluateMethodCall(Expression call, Expression base, ValType type,
            List<Expression> arguments, ExpressionVisitor mv) {
        // only called for the property reference case (`obj.method(...)` or `obj[method](...)`)
        assert isPropertyReference(base, type);

        Label afterCall = new Label(), notMethodCall = new Label();

        // FIXME: https://bugs.ecmascript.org/show_bug.cgi?id=1593

        // stack: [ref] -> [args, ref]
        ArgumentListEvaluation(arguments, mv);
        mv.swap();

        mv.lineInfo(call);

        // stack: [args, ref] -> [args, ref, base]
        mv.dup();
        mv.loadExecutionContext();
        mv.invoke(Methods.Reference_GetMethodCallThisValue);

        mv.dup();
        mv.instanceOf(Types.OrdinaryObject);
        mv.ifne(notMethodCall);
        {
            // stack: [args, ref, base] -> [result]
            mv.loadExecutionContext();
            mv.invoke(Methods.ScriptRuntime_EvaluateMethodCall);
            mv.goTo(afterCall);
        }
        mv.mark(notMethodCall);

        // this is an EvaluateMethodCall operation with an ordinary object as the base object,
        // revert to the standard EvaluateCall operation to:
        // (1) support tail calls (implementation specific)

        // If thisValue is a primitive value, base is the corresponding primitive wrapper, e.g.
        // if thisValue is a string, then base is String.prototype; regardless we don't need to
        // perform any additional actions for this case (cf.
        // Reference.PropertyNameReference.GetValuePrimitive), because primitives don't have own
        // function valued properties, so property look-up takes place on the primitive wrapper
        // in all cases.

        // stack: [args, ref, base] -> [args, base, ref]
        mv.swap();

        // stack: [args, base, ref] -> [args, base, ref, thisValue]
        mv.dup();
        mv.loadExecutionContext();
        mv.invoke(Methods.Reference_GetThisValue);

        // stack: [args, base, ref, thisValue] -> [args, thisValue, func]
        mv.dupX2();
        mv.loadExecutionContext();
        mv.invoke(Methods.ScriptRuntime_OrdinaryInvokeGet);

        // stack: [args, thisValue, func] -> [args, thisValue, func(Callable)]
        mv.loadExecutionContext();
        mv.invoke(Methods.ScriptRuntime_CheckCallable);

        // stack: [args, thisValue, func(Callable)] -> [result]
        stdCall(call, mv);

        mv.mark(afterCall);
    }

    /**
     * [11.2.3 EvaluateCall Abstract Operation]
     */
    private void EvaluateCallWithValue(Expression call, Expression base, ValType type,
            List<Expression> arguments, ExpressionVisitor mv) {
        assert type != ValType.Reference;

        /* step 1 (not applicable) */

        /* step 2 (see below) */
        // thisValue = undefined;

        /* steps 3-5 (no-op) */
        // GetValue(...)

        /* steps 6-7 */
        // stack: [func] -> [args, func]
        ArgumentListEvaluation(arguments, mv);
        mv.swap();

        // stack: [args, func]
        mv.lineInfo(call);

        /* steps 8-9 */
        // stack: [args, func] -> [args, func(Callable)]
        mv.loadExecutionContext();
        mv.invoke(Methods.ScriptRuntime_CheckCallable);

        /* step 2 */
        // stack: [args, func(Callable)] -> [args, thisValue, func(Callable)]
        mv.loadUndefined();
        mv.swap();

        /* steps 10-14 */
        // stack: [args, thisValue, func(Callable)] -> result
        stdCall(call, mv);
    }

    /**
     * [11.2.3 EvaluateCall Abstract Operation]
     */
    private void EvaluateCallIdentRef(Expression call, Expression base, ValType type,
            List<Expression> arguments, boolean directEval, ExpressionVisitor mv) {
        assert type == ValType.Reference && base instanceof Identifier;

        Label afterCall = new Label();

        /* step 1 */
        // thisValue = undefined;

        /* step 2 (not applicable) */

        /* steps 3-5 */
        // stack: [ref] -> [func]
        GetValue(base, type, mv);

        /* steps 6-7 */
        // stack: [func] -> [args, func]
        boolean hasSpread = ArgumentListEvaluation(arguments, mv);
        mv.swap();

        // stack: [args, func]
        mv.lineInfo(call);

        /* steps 8-9 */
        // stack: [args, func] -> [args, func(Callable)]
        mv.loadExecutionContext();
        mv.invoke(Methods.ScriptRuntime_CheckCallable);

        /* step 1 */
        // stack: [args, func(Callable)] -> [args, thisValue, func(Callable)]
        mv.loadUndefined();
        mv.swap();

        if (directEval) {
            directEvalCall(call, base, type, arguments, hasSpread, afterCall, mv);
        }

        /* steps 10-14 */
        // stack: [args, thisValue, func(Callable)] -> result
        stdCall(call, mv);

        mv.mark(afterCall);
    }

    /**
     * [11.2.3 EvaluateCall Abstract Operation]
     */
    private void EvaluateCallWithIdentRef(Expression call, Expression base, ValType type,
            List<Expression> arguments, boolean directEval, ExpressionVisitor mv) {
        assert type == ValType.Reference && base instanceof Identifier;

        Variable<ScriptObject> methodCallObject = mv.newVariable("methodCallObject",
                ScriptObject.class);
        Label argListEval = new Label();
        Label afterCall = new Label();
        Label stdCall = new Label(), stdCallCheck = new Label();

        /* step 1 */
        // 'getMethodCallThisValue' is a combination of step 1.b.i and step 4, cf. it retrieves
        // the withBaseObject or throws if it is an unresolved reference
        // stack: [ref] -> [ref, base]
        mv.dup();
        mv.loadExecutionContext();
        mv.invoke(Methods.Reference_GetMethodCallThisValue);

        // stack: [ref, base] -> [ref, base]
        mv.dup();
        mv.store(methodCallObject);

        // Below this point we're now in a combined EvaluateCall + EvaluateMethodCall operation,
        // 'methodCallObject' acts as selector to decide which operation to use.

        /* EvaluateCall: step 2 (not applicable) */
        /* EvaluateMethodCall: steps 1-3 (not applicable) */

        // stack: [ref, base] -> [ref|func]
        mv.ifnonnull(argListEval);
        {
            /* EvaluateCall: steps 3-5 */
            // stack: [ref] -> [func]
            GetValue(base, type, mv);
        }
        mv.mark(argListEval);

        // FIXME: https://bugs.ecmascript.org/show_bug.cgi?id=1593

        /* EvaluateCall: steps 6-7 */
        /* EvaluateMethodCall: steps 4-5 */
        // stack: [ref|func] -> [args, ref|func]
        boolean hasSpread = ArgumentListEvaluation(arguments, mv);
        mv.swap();

        // stack: [args, ref|func]
        mv.lineInfo(call);

        mv.load(methodCallObject);
        mv.ifnull(stdCall);
        {
            // EvaluateMethodCall: steps 6-13
            Label notMethodCall = new Label();

            // stack: [args, ref] -> [args, ref(Reference)]
            mv.checkcast(Types.Reference);

            /* EvaluateMethodCall: step 6 */
            // stack: [args, ref(Reference)] -> [args, ref(Reference), base]
            mv.load(methodCallObject);

            mv.dup();
            mv.instanceOf(Types.OrdinaryObject);
            mv.ifne(notMethodCall);
            {
                /* EvaluateMethodCall: steps 7-13 */
                // stack: [args, ref(Reference), base] -> [result]
                mv.loadExecutionContext();
                mv.invoke(Methods.ScriptRuntime_EvaluateMethodCallWithBaseObject);
                mv.goTo(afterCall);
            }
            mv.mark(notMethodCall);

            // this is an EvaluateMethodCall operation with an ordinary object as the base object,
            // revert to the standard EvaluateCall operation to:
            // (1) support direct-eval calls (specification issue)
            // (2) support tail calls (implementation specific)

            // Note: base == thisValue (!)
            // stack: [args, ref(Ref), base] -> [args, thisValue, base, ref(Ref), thisValue]
            mv.dupX1();
            mv.dupX1();

            // stack: [args, thisValue, base, ref(Ref), thisValue] -> [args, thisValue, func]
            mv.loadExecutionContext();
            mv.invoke(Methods.ScriptRuntime_OrdinaryInvokeGet);

            mv.goTo(stdCallCheck);

            mv.freeVariable(methodCallObject);
        }
        mv.mark(stdCall);

        // stack: [args, func] -> [args, thisValue, func]
        mv.loadUndefined();
        mv.swap();

        mv.mark(stdCallCheck);

        // stack: [args, thisValue, func] -> [args, thisValue, func(Callable)]
        mv.loadExecutionContext();
        mv.invoke(Methods.ScriptRuntime_CheckCallable);

        if (directEval) {
            directEvalCall(call, base, type, arguments, hasSpread, afterCall, mv);
        }

        stdCall(call, mv);

        mv.mark(afterCall);
    }

    /**
     * [15.1.2.1.1] Direct Call to Eval
     */
    private void directEvalCall(Expression call, Expression base, ValType type,
            List<Expression> arguments, boolean hasSpread, Label afterCall, ExpressionVisitor mv) {
        assert type == ValType.Reference && base instanceof Identifier;

        // test for possible direct-eval call
        Label notEval = new Label();

        // stack: [args, thisValue, func(Callable)] -> [args, thisValue, func(Callable)]
        mv.dup();
        mv.loadExecutionContext();
        mv.invoke(Methods.ExecutionContext_getRealm);
        mv.invoke(Methods.Realm_getBuiltinEval);
        mv.ifacmpne(notEval);

        // stack: [args, thisValue, func(Callable)] -> [args]
        mv.pop2();

        // stack: [args] -> [arg0]
        if (hasSpread) {
            Label isEmpty = new Label(), after = new Label();
            mv.dup();
            mv.arraylength();
            mv.ifeq(isEmpty);
            mv.iconst(0);
            mv.aload(Types.Object);
            mv.goTo(after);
            mv.mark(isEmpty);
            mv.pop();
            mv.loadUndefined();
            mv.mark(after);
        } else if (arguments.isEmpty()) {
            mv.pop();
            mv.loadUndefined();
        } else {
            mv.iconst(0);
            mv.aload(Types.Object);
        }

        // stack: [args0] -> [result]
        mv.loadExecutionContext();
        mv.iconst(mv.isStrict());
        mv.iconst(mv.isGlobalCode());
        mv.invoke(Methods.Eval_directEval);

        mv.goTo(afterCall);
        mv.mark(notEval);
    }

    /**
     * [11.2.3 EvaluateCall Abstract Operation]
     */
    private void stdCall(Expression call, ExpressionVisitor mv) {
        // stack: [args, thisValue, func(Callable)]

        /* steps 10, 12-13 */
        // TODO: tail-call for SuperExpression(call) or TemplateCallExpression?
        if (call instanceof CallExpression && mv.isTailCall((CallExpression) call)) {
            Label noTailCall = new Label();
            mv.dup();
            mv.instanceOf(Types.OrdinaryFunction);
            mv.ifeq(noTailCall);

            // stack: [args, thisValue, func(Callable)] -> [<func(Callable), thisValue, args>]
            mv.invoke(Methods.ScriptRuntime_PrepareForTailCall);
            mv.areturn(Types.Object_);

            mv.mark(noTailCall);
        }

        // stack: [args, thisValue, func(Callable)] -> [func(Callable), cx, thisValue, args]
        mv.loadExecutionContext();
        mv.dup2X2();
        mv.pop2();
        mv.swap();

        /* steps 11, 14 */
        // stack: [func(Callable), cx, thisValue, args] -> [result]
        mv.invoke(Methods.Callable_call);
    }

    /**
     * [11.2.5.1 ArgumentListEvaluation]
     */
    private boolean ArgumentListEvaluation(List<Expression> arguments, ExpressionVisitor mv) {
        boolean hasSpread = false;
        if (arguments.isEmpty()) {
            mv.get(Fields.ScriptRuntime_EMPTY_ARRAY);
        } else {
            mv.newarray(arguments.size(), Types.Object);
            for (int i = 0, size = arguments.size(); i < size; ++i) {
                mv.dup();
                mv.iconst(i);
                /* [11.2.5 Argument Lists] ArgumentListEvaluation */
                Expression argument = arguments.get(i);
                hasSpread |= (argument instanceof CallSpreadElement);
                ValType argtype = evalAndGetValue(argument, mv);
                mv.toBoxed(argtype);
                mv.astore(Types.Object);
            }
            if (hasSpread) {
                mv.loadExecutionContext();
                mv.invoke(Methods.ScriptRuntime_toFlatArray);
            }
        }
        return hasSpread;
    }

    /* ----------------------------------------------------------------------------------------- */

    @Override
    protected ValType visit(Node node, ExpressionVisitor mv) {
        throw new IllegalStateException(String.format("node-class: %s", node.getClass()));
    }

    @Override
    public ValType visit(ArrayComprehension node, ExpressionVisitor mv) {
        node.accept(new ArrayComprehensionGenerator(codegen), mv);

        return ValType.Object;
    }

    @Override
    public ValType visit(ArrayLiteral node, ExpressionVisitor mv) {
        boolean hasSpread = false;
        for (Expression element : node.getElements()) {
            if (element instanceof SpreadElement) {
                hasSpread = true;
                break;
            }
        }

        mv.loadExecutionContext();
        mv.lconst(0);
        mv.invoke(Methods.ExoticArray_ArrayCreate);
        if (!hasSpread) {
            int nextIndex = 0;
            for (Expression element : node.getElements()) {
                if (element instanceof Elision) {
                    // Elision
                } else {
                    mv.dup();
                    mv.iconst(nextIndex);
                    ValType type = evalAndGetValue(element, mv);
                    mv.toBoxed(type);
                    mv.loadExecutionContext();
                    mv.invoke(Methods.ScriptRuntime_defineProperty__int);
                }
                nextIndex += 1;
            }
            mv.dup();
            mv.loadExecutionContext();
            mv.swap();
            mv.aconst("length");
            mv.iconst(nextIndex);
            mv.toBoxed(Type.INT_TYPE);
            mv.iconst(false);
            mv.invoke(Methods.AbstractOperations_Put);
        } else {
            // stack: [array, nextIndex]
            mv.iconst(0); // nextIndex

            arrayLiteralWithSpread(node, mv);

            // stack: [array, nextIndex] -> [array, 'length', (nextIndex), false, cx]
            mv.toBoxed(Type.INT_TYPE);
            mv.swap();
            mv.dup();
            mv.loadExecutionContext();
            mv.dup2X2();
            mv.pop2();
            mv.swap();
            // stack: [array, cx, array, (nextIndex)]
            mv.aconst("length");
            mv.swap();
            mv.iconst(false);
            mv.invoke(Methods.AbstractOperations_Put);
        }

        return ValType.Object;
    }

    @Override
    public ValType visit(SpreadArrayLiteral node, ExpressionVisitor mv) {
        // stack: [] -> [array, nextIndex]
        mv.loadParameter(1, ExoticArray.class);
        mv.loadParameter(2, int.class);

        arrayLiteralWithSpread(node, mv);

        // stack: [array, nextIndex] -> [nextIndex]
        mv.swap();
        mv.pop();

        return ValType.Any;
    }

    private void arrayLiteralWithSpread(ArrayLiteral node, ExpressionVisitor mv) {
        // stack: [array, nextIndex]
        int elisionWidth = 0;
        for (Expression element : node.getElements()) {
            if (element instanceof Elision) {
                // Elision
                elisionWidth += 1;
                continue;
            }
            if (elisionWidth != 0) {
                mv.iconst(elisionWidth);
                mv.add(Type.INT_TYPE);
                elisionWidth = 0;
            }
            if (element instanceof SpreadElement) {
                element.accept(this, mv);
            } else {
                // stack: [array, nextIndex] -> [array, nextIndex, array, nextIndex]
                mv.dup2();
                ValType type = evalAndGetValue(element, mv);
                mv.toBoxed(type);
                mv.loadExecutionContext();
                // stack: [array, nextIndex, array, nextIndex, obj] -> [array, nextIndex]
                mv.invoke(Methods.ScriptRuntime_defineProperty__int);
                elisionWidth += 1;
            }
        }
        if (elisionWidth != 0) {
            mv.iconst(elisionWidth);
            mv.add(Type.INT_TYPE);
            elisionWidth = 0;
        }
    }

    @Override
    public ValType visit(SpreadElement node, ExpressionVisitor mv) {
        // stack: [array, nextIndex] -> [array, array, nextIndex]
        mv.swap();
        mv.dupX1();
        mv.swap();

        // stack: [array, array, nextIndex] -> [array, array, nextIndex, obj]
        Expression spread = node.getExpression();
        ValType type = evalAndGetValue(spread, mv);
        mv.toBoxed(type);

        mv.loadExecutionContext();

        // stack: [array, array, nextIndex, obj, cx] -> [array, nextIndex']
        mv.invoke(Methods.ScriptRuntime_ArrayAccumulationSpreadElement);

        return ValType.Any;
    }

    @Override
    public ValType visit(SpreadElementMethod node, ExpressionVisitor mv) {
        codegen.compile(node, mv);

        // stack: [array, nextIndex] -> [array, array, nextIndex]
        mv.swap();
        mv.dupX1();
        mv.swap();

        // stack: [array, array, nextIndex] -> [array, cx, array, nextIndex]
        mv.loadExecutionContext();
        mv.dupX2();
        mv.pop();

        // stack: [array, cx, array, nextIndex] -> [array, nextIndex']
        String desc = Type.getMethodDescriptor(Type.INT_TYPE, Types.ExecutionContext,
                Types.ExoticArray, Type.INT_TYPE);
        mv.invokestatic(codegen.getClassName(), codegen.methodName(node), desc);

        return ValType.Any;
    }

    @Override
    public ValType visit(ArrowFunction node, ExpressionVisitor mv) {
        codegen.compile(node);

        // Runtime Semantics: Evaluation -> ArrowFunction
        mv.invokestatic(codegen.getClassName(), codegen.methodName(node, FunctionName.RTI),
                Type.getMethodDescriptor(Types.RuntimeInfo$Function));
        mv.loadExecutionContext();
        mv.invoke(Methods.ScriptRuntime_EvaluateArrowFunction);

        return ValType.Object;
    }

    @Override
    public ValType visit(AssignmentExpression node, ExpressionVisitor mv) {
        LeftHandSideExpression left = node.getLeft();
        Expression right = node.getRight();
        if (node.getOperator() == AssignmentExpression.Operator.ASSIGN) {
            if (left instanceof AssignmentPattern) {
                ValType rtype = evalAndGetValue(right, mv);

                if (rtype != ValType.Object) {
                    mv.toBoxed(rtype);
                    mv.loadExecutionContext();
                    mv.invoke(Methods.ScriptRuntime_ensureObject);
                }

                mv.dup();
                DestructuringAssignment((AssignmentPattern) left, mv);

                return ValType.Object;
            } else {
                ValType ltype = left.accept(this, mv);
                assert ltype == ValType.Reference : "invalid lhs for assignment";
                ValType rtype = evalAndGetValue(right, mv);

                // lref rval
                mv.dupX(ltype, rtype);
                mv.toBoxed(rtype);
                PutValue(left, ltype, mv);

                return rtype;
            }
        } else {
            switch (node.getOperator()) {
            case ASSIGN_MUL: {
                // 11.5 Multiplicative Operators
                ValType ltype = left.accept(this, mv);
                assert ltype == ValType.Reference : "invalid lhs for assignment";
                mv.dup();
                GetValue(left, ltype, mv);
                ValType rtype = evalAndGetValue(right, mv);
                // lref lval rval
                mv.swap(ltype, rtype);
                ToNumber(ltype, mv);
                mv.swap(rtype, ValType.Number);
                ToNumber(rtype, mv);
                mv.mul(Type.DOUBLE_TYPE);
                // r lref r
                mv.dupX(ltype, ValType.Number);
                mv.toBoxed(ValType.Number);
                PutValue(left, ltype, mv);
                return ValType.Number;
            }
            case ASSIGN_DIV: {
                // 11.5 Multiplicative Operators
                ValType ltype = left.accept(this, mv);
                assert ltype == ValType.Reference : "invalid lhs for assignment";
                mv.dup();
                GetValue(left, ltype, mv);
                ValType rtype = evalAndGetValue(right, mv);
                // lref lval rval
                mv.swap(ltype, rtype);
                ToNumber(ltype, mv);
                mv.swap(rtype, ValType.Number);
                ToNumber(rtype, mv);
                mv.div(Type.DOUBLE_TYPE);
                // r lref r
                mv.dupX(ltype, ValType.Number);
                mv.toBoxed(ValType.Number);
                PutValue(left, ltype, mv);
                return ValType.Number;
            }
            case ASSIGN_MOD: {
                // 11.5 Multiplicative Operators
                ValType ltype = left.accept(this, mv);
                assert ltype == ValType.Reference : "invalid lhs for assignment";
                mv.dup();
                GetValue(left, ltype, mv);
                ValType rtype = evalAndGetValue(right, mv);
                // lref lval rval
                mv.swap(ltype, rtype);
                ToNumber(ltype, mv);
                mv.swap(rtype, ValType.Number);
                ToNumber(rtype, mv);
                mv.rem(Type.DOUBLE_TYPE);
                // r lref r
                mv.dupX(ltype, ValType.Number);
                mv.toBoxed(ValType.Number);
                PutValue(left, ltype, mv);
                return ValType.Number;
            }
            case ASSIGN_ADD: {
                // 11.6.1 The Addition operator ( + )
                if (right instanceof StringLiteral) {
                    // x += ""
                    ValType ltype = left.accept(this, mv);
                    assert ltype == ValType.Reference : "invalid lhs for assignment";
                    mv.dup();
                    GetValue(left, ltype, mv);
                    ToPrimitive(ltype, mv);
                    ToString(ltype, mv);
                    if (!((StringLiteral) right).getValue().isEmpty()) {
                        right.accept(this, mv);
                        mv.loadExecutionContext();
                        mv.invoke(Methods.ScriptRuntime_add_str);
                    }
                    // r lref r
                    mv.dupX1();
                    PutValue(left, ltype, mv);
                    return ValType.String;
                }

                ValType ltype = left.accept(this, mv);
                assert ltype == ValType.Reference : "invalid lhs for assignment";
                mv.dup();
                GetValue(left, ltype, mv);
                ValType rtype = evalAndGetValue(right, mv);
                mv.toBoxed(rtype);
                // lref lval rval
                mv.loadExecutionContext();
                mv.invoke(Methods.ScriptRuntime_add);
                // r lref r
                mv.dupX1();
                PutValue(left, ltype, mv);
                return ValType.Any;
            }
            case ASSIGN_SUB: {
                // 1.6.2 The Subtraction Operator ( - )
                ValType ltype = left.accept(this, mv);
                assert ltype == ValType.Reference : "invalid lhs for assignment";
                mv.dup();
                GetValue(left, ltype, mv);
                ValType rtype = evalAndGetValue(right, mv);
                // lref lval rval
                mv.swap(ltype, rtype);
                ToNumber(ltype, mv);
                mv.swap(rtype, ValType.Number);
                ToNumber(rtype, mv);
                mv.sub(Type.DOUBLE_TYPE);
                // r lref r
                mv.dupX(ltype, ValType.Number);
                mv.toBoxed(ValType.Number);
                PutValue(left, ltype, mv);
                return ValType.Number;
            }
            case ASSIGN_SHL: {
                // 11.7.1 The Left Shift Operator ( << )
                ValType ltype = left.accept(this, mv);
                assert ltype == ValType.Reference : "invalid lhs for assignment";
                mv.dup();
                GetValue(left, ltype, mv);
                ValType rtype = evalAndGetValue(right, mv);
                // lref lval rval
                mv.swap(ltype, rtype);
                ToInt32(ltype, mv);
                mv.swap(rtype, ValType.Number_int);
                ToInt32(rtype, mv); // ToUint32()
                mv.iconst(0x1F);
                mv.and(Type.INT_TYPE);
                mv.shl(Type.INT_TYPE);
                // r lref r
                mv.dupX(ltype, ValType.Number_int);
                mv.toBoxed(ValType.Number_int);
                PutValue(left, ltype, mv);
                return ValType.Number_int;
            }
            case ASSIGN_SHR: {
                // 11.7.2 The Signed Right Shift Operator ( >> )
                ValType ltype = left.accept(this, mv);
                assert ltype == ValType.Reference : "invalid lhs for assignment";
                mv.dup();
                GetValue(left, ltype, mv);
                ValType rtype = evalAndGetValue(right, mv);
                // lref lval rval
                mv.swap(ltype, rtype);
                ToInt32(ltype, mv);
                mv.swap(rtype, ValType.Number_int);
                ToInt32(rtype, mv); // ToUint32()
                mv.iconst(0x1F);
                mv.and(Type.INT_TYPE);
                mv.shr(Type.INT_TYPE);
                // r lref r
                mv.dupX(ltype, ValType.Number_int);
                mv.toBoxed(ValType.Number_int);
                PutValue(left, ltype, mv);
                return ValType.Number_int;
            }
            case ASSIGN_USHR: {
                // 11.7.3 The Unsigned Right Shift Operator ( >>> )
                ValType ltype = left.accept(this, mv);
                assert ltype == ValType.Reference : "invalid lhs for assignment";
                mv.dup();
                GetValue(left, ltype, mv);
                ValType rtype = evalAndGetValue(right, mv);
                // lref lval rval
                mv.swap(ltype, rtype);
                ToUint32(ltype, mv);
                mv.swap(rtype, ValType.Number_uint);
                ToInt32(rtype, mv); // ToUint32()
                mv.iconst(0x1F);
                mv.and(Type.INT_TYPE);
                mv.ushr(Type.LONG_TYPE);
                // r lref r
                mv.dupX(ltype, ValType.Number_uint);
                mv.toBoxed(ValType.Number_uint);
                PutValue(left, ltype, mv);
                return ValType.Number_uint;
            }
            case ASSIGN_BITAND: {
                // 11.10 Binary Bitwise Operators ( & )
                ValType ltype = left.accept(this, mv);
                assert ltype == ValType.Reference : "invalid lhs for assignment";
                mv.dup();
                GetValue(left, ltype, mv);
                ValType rtype = evalAndGetValue(right, mv);
                // lref lval rval
                mv.swap(ltype, rtype);
                ToInt32(ltype, mv);
                mv.swap(rtype, ValType.Number_int);
                ToInt32(rtype, mv);
                mv.and(Type.INT_TYPE);
                // r lref r
                mv.dupX(ltype, ValType.Number_int);
                mv.toBoxed(ValType.Number_int);
                PutValue(left, ltype, mv);
                return ValType.Number_int;
            }
            case ASSIGN_BITXOR: {
                // 11.10 Binary Bitwise Operators ( ^ )
                ValType ltype = left.accept(this, mv);
                assert ltype == ValType.Reference : "invalid lhs for assignment";
                mv.dup();
                GetValue(left, ltype, mv);
                ValType rtype = evalAndGetValue(right, mv);
                // lref lval rval
                mv.swap(ltype, rtype);
                ToInt32(ltype, mv);
                mv.swap(rtype, ValType.Number_int);
                ToInt32(rtype, mv);
                mv.xor(Type.INT_TYPE);
                // r lref r
                mv.dupX(ltype, ValType.Number_int);
                mv.toBoxed(ValType.Number_int);
                PutValue(left, ltype, mv);
                return ValType.Number_int;
            }
            case ASSIGN_BITOR: {
                // 11.10 Binary Bitwise Operators ( | )
                ValType ltype = left.accept(this, mv);
                assert ltype == ValType.Reference : "invalid lhs for assignment";
                mv.dup();
                GetValue(left, ltype, mv);
                ValType rtype = evalAndGetValue(right, mv);
                // lref lval rval
                mv.swap(ltype, rtype);
                ToInt32(ltype, mv);
                mv.swap(rtype, ValType.Number_int);
                ToInt32(rtype, mv);
                mv.or(Type.INT_TYPE);
                // r lref r
                mv.dupX(ltype, ValType.Number_int);
                mv.toBoxed(ValType.Number_int);
                PutValue(left, ltype, mv);
                return ValType.Number_int;
            }
            case ASSIGN:
            default:
                throw new IllegalStateException(Objects.toString(node.getOperator(), "<null>"));
            }
        }
    }

    @Override
    public ValType visit(BinaryExpression node, ExpressionVisitor mv) {
        Expression left = node.getLeft();
        Expression right = node.getRight();

        switch (node.getOperator()) {
        case MUL: {
            // 11.5 Multiplicative Operators
            ValType ltype = evalAndGetValue(left, mv);
            if (ltype.isPrimitive() || right instanceof ValueLiteral) {
                ToNumber(ltype, mv);
            }
            ValType rtype = evalAndGetValue(right, mv);
            if (!(ltype.isPrimitive() || right instanceof ValueLiteral)) {
                mv.swap(ltype, rtype);
                ToNumber(ltype, mv);
                mv.swap(rtype, ValType.Number);
            }
            ToNumber(rtype, mv);
            mv.mul(Type.DOUBLE_TYPE);
            return ValType.Number;
        }
        case DIV: {
            // 11.5 Multiplicative Operators
            ValType ltype = evalAndGetValue(left, mv);
            if (ltype.isPrimitive() || right instanceof ValueLiteral) {
                ToNumber(ltype, mv);
            }
            ValType rtype = evalAndGetValue(right, mv);
            if (!(ltype.isPrimitive() || right instanceof ValueLiteral)) {
                mv.swap(ltype, rtype);
                ToNumber(ltype, mv);
                mv.swap(rtype, ValType.Number);
            }
            ToNumber(rtype, mv);
            mv.div(Type.DOUBLE_TYPE);
            return ValType.Number;
        }
        case MOD: {
            // 11.5 Multiplicative Operators
            ValType ltype = evalAndGetValue(left, mv);
            if (ltype.isPrimitive() || right instanceof ValueLiteral) {
                ToNumber(ltype, mv);
            }
            ValType rtype = evalAndGetValue(right, mv);
            if (!(ltype.isPrimitive() || right instanceof ValueLiteral)) {
                mv.swap(ltype, rtype);
                ToNumber(ltype, mv);
                mv.swap(rtype, ValType.Number);
            }
            ToNumber(rtype, mv);
            mv.rem(Type.DOUBLE_TYPE);
            return ValType.Number;
        }
        case ADD: {
            // 11.6.1 The Addition operator ( + )
            if (left instanceof StringLiteral) {
                if (((StringLiteral) left).getValue().isEmpty()) {
                    // "" + x
                    ValType rtype = evalAndGetValue(right, mv);
                    rtype = ToPrimitive(rtype, mv);
                    ToString(rtype, mv);
                } else {
                    left.accept(this, mv);
                    ValType rtype = evalAndGetValue(right, mv);
                    rtype = ToPrimitive(rtype, mv);
                    ToString(rtype, mv);
                    mv.loadExecutionContext();
                    mv.invoke(Methods.ScriptRuntime_add_str);
                }
                return ValType.String;
            } else if (right instanceof StringLiteral) {
                if (((StringLiteral) right).getValue().isEmpty()) {
                    // x + ""
                    ValType ltype = evalAndGetValue(left, mv);
                    ltype = ToPrimitive(ltype, mv);
                    ToString(ltype, mv);
                } else {
                    ValType ltype = evalAndGetValue(left, mv);
                    ltype = ToPrimitive(ltype, mv);
                    ToString(ltype, mv);
                    right.accept(this, mv);
                    mv.loadExecutionContext();
                    mv.invoke(Methods.ScriptRuntime_add_str);
                }
                return ValType.String;
            }

            ValType ltype = evalAndGetValue(left, mv);
            mv.toBoxed(ltype);
            ValType rtype = evalAndGetValue(right, mv);
            mv.toBoxed(rtype);

            mv.loadExecutionContext();
            invokeDynamicOperator(node.getOperator(), mv);

            return ValType.Any;
        }
        case SUB: {
            // 1.6.2 The Subtraction Operator ( - )
            ValType ltype = evalAndGetValue(left, mv);
            if (ltype.isPrimitive() || right instanceof ValueLiteral) {
                ToNumber(ltype, mv);
            }
            ValType rtype = evalAndGetValue(right, mv);
            if (!(ltype.isPrimitive() || right instanceof ValueLiteral)) {
                mv.swap(ltype, rtype);
                ToNumber(ltype, mv);
                mv.swap(rtype, ValType.Number);
            }
            ToNumber(rtype, mv);
            mv.sub(Type.DOUBLE_TYPE);
            return ValType.Number;
        }
        case SHL: {
            // 11.7.1 The Left Shift Operator ( << )
            ValType ltype = evalAndGetValue(left, mv);
            if (ltype.isPrimitive() || right instanceof ValueLiteral) {
                ToInt32(ltype, mv);
            }
            ValType rtype = evalAndGetValue(right, mv);
            if (!(ltype.isPrimitive() || right instanceof ValueLiteral)) {
                mv.swap(ltype, rtype);
                ToInt32(ltype, mv);
                mv.swap(rtype, ValType.Number_int);
            }
            ToInt32(rtype, mv); // ToUint32()
            mv.iconst(0x1F);
            mv.and(Type.INT_TYPE);
            mv.shl(Type.INT_TYPE);
            return ValType.Number_int;
        }
        case SHR: {
            // 11.7.2 The Signed Right Shift Operator ( >> )
            ValType ltype = evalAndGetValue(left, mv);
            if (ltype.isPrimitive() || right instanceof ValueLiteral) {
                ToInt32(ltype, mv);
            }
            ValType rtype = evalAndGetValue(right, mv);
            if (!(ltype.isPrimitive() || right instanceof ValueLiteral)) {
                mv.swap(ltype, rtype);
                ToInt32(ltype, mv);
                mv.swap(rtype, ValType.Number_int);
            }
            ToInt32(rtype, mv); // ToUint32()
            mv.iconst(0x1F);
            mv.and(Type.INT_TYPE);
            mv.shr(Type.INT_TYPE);
            return ValType.Number_int;
        }
        case USHR: {
            // 11.7.3 The Unsigned Right Shift Operator ( >>> )
            ValType ltype = evalAndGetValue(left, mv);
            if (ltype.isPrimitive() || right instanceof ValueLiteral) {
                ToUint32(ltype, mv);
            }
            ValType rtype = evalAndGetValue(right, mv);
            if (!(ltype.isPrimitive() || right instanceof ValueLiteral)) {
                mv.swap(ltype, rtype);
                ToUint32(ltype, mv);
                mv.swap(rtype, ValType.Number_uint);
            }
            ToInt32(rtype, mv); // ToUint32()
            mv.iconst(0x1F);
            mv.and(Type.INT_TYPE);
            mv.ushr(Type.LONG_TYPE);
            return ValType.Number_uint;
        }
        case LT: {
            // 11.8 Relational Operators ( < )
            ValType ltype = evalAndGetValue(left, mv);
            mv.toBoxed(ltype);
            ValType rtype = evalAndGetValue(right, mv);
            mv.toBoxed(rtype);

            mv.loadExecutionContext();
            invokeDynamicOperator(node.getOperator(), mv);

            {
                Label lbl1 = new Label(), lbl2 = new Label();
                mv.ifgt(lbl1);
                mv.iconst(false);
                mv.goTo(lbl2);
                mv.mark(lbl1);
                mv.iconst(true);
                mv.mark(lbl2);
            }

            return ValType.Boolean;
        }
        case GT: {
            // 11.8 Relational Operators ( > )
            ValType ltype = evalAndGetValue(left, mv);
            mv.toBoxed(ltype);
            ValType rtype = evalAndGetValue(right, mv);
            mv.toBoxed(rtype);
            mv.swap();

            mv.loadExecutionContext();
            invokeDynamicOperator(node.getOperator(), mv);

            {
                Label lbl1 = new Label(), lbl2 = new Label();
                mv.ifgt(lbl1);
                mv.iconst(false);
                mv.goTo(lbl2);
                mv.mark(lbl1);
                mv.iconst(true);
                mv.mark(lbl2);
            }
            return ValType.Boolean;
        }
        case LE: {
            // 11.8 Relational Operators ( <= )
            ValType ltype = evalAndGetValue(left, mv);
            mv.toBoxed(ltype);
            ValType rtype = evalAndGetValue(right, mv);
            mv.toBoxed(rtype);
            mv.swap();

            mv.loadExecutionContext();
            invokeDynamicOperator(node.getOperator(), mv);

            {
                Label lbl1 = new Label(), lbl2 = new Label();
                mv.ifeq(lbl1);
                mv.iconst(false);
                mv.goTo(lbl2);
                mv.mark(lbl1);
                mv.iconst(true);
                mv.mark(lbl2);
            }
            return ValType.Boolean;
        }
        case GE: {
            // 11.8 Relational Operators ( >= )
            ValType ltype = evalAndGetValue(left, mv);
            mv.toBoxed(ltype);
            ValType rtype = evalAndGetValue(right, mv);
            mv.toBoxed(rtype);

            mv.loadExecutionContext();
            invokeDynamicOperator(node.getOperator(), mv);

            {
                Label lbl1 = new Label(), lbl2 = new Label();
                mv.ifeq(lbl1);
                mv.iconst(false);
                mv.goTo(lbl2);
                mv.mark(lbl1);
                mv.iconst(true);
                mv.mark(lbl2);
            }
            return ValType.Boolean;
        }
        case INSTANCEOF: {
            // 11.8 Relational Operators ( instanceof )
            ValType ltype = evalAndGetValue(left, mv);
            mv.toBoxed(ltype);
            ValType rtype = evalAndGetValue(right, mv);
            mv.toBoxed(rtype);

            mv.loadExecutionContext();
            mv.invoke(Methods.ScriptRuntime_instanceOfOperator);
            return ValType.Boolean;
        }
        case IN: {
            // 11.8 Relational Operators ( in )
            ValType ltype = evalAndGetValue(left, mv);
            mv.toBoxed(ltype);

            ValType rtype = evalAndGetValue(right, mv);
            mv.toBoxed(rtype);

            mv.loadExecutionContext();
            mv.invoke(Methods.ScriptRuntime_in);
            return ValType.Boolean;
        }
        case EQ: {
            // 11.9 Equality Operators ( == )
            ValType ltype = evalAndGetValue(left, mv);
            mv.toBoxed(ltype);

            ValType rtype = evalAndGetValue(right, mv);
            mv.toBoxed(rtype);

            mv.loadExecutionContext();
            invokeDynamicOperator(node.getOperator(), mv);

            return ValType.Boolean;
        }
        case NE: {
            // 11.9 Equality Operators ( != )
            ValType ltype = evalAndGetValue(left, mv);
            mv.toBoxed(ltype);

            ValType rtype = evalAndGetValue(right, mv);
            mv.toBoxed(rtype);

            mv.loadExecutionContext();
            invokeDynamicOperator(node.getOperator(), mv);

            mv.not();
            return ValType.Boolean;
        }
        case SHEQ: {
            // 11.9 Equality Operators ( === )
            ValType ltype = evalAndGetValue(left, mv);
            mv.toBoxed(ltype);

            ValType rtype = evalAndGetValue(right, mv);
            mv.toBoxed(rtype);

            invokeDynamicOperator(node.getOperator(), mv);

            return ValType.Boolean;
        }
        case SHNE: {
            // 11.9 Equality Operators ( !== )
            ValType ltype = evalAndGetValue(left, mv);
            mv.toBoxed(ltype);

            ValType rtype = evalAndGetValue(right, mv);
            mv.toBoxed(rtype);

            invokeDynamicOperator(node.getOperator(), mv);

            mv.not();
            return ValType.Boolean;
        }
        case BITAND: {
            // 11.10 Binary Bitwise Operators ( & )
            ValType ltype = evalAndGetValue(left, mv);
            if (ltype.isPrimitive() || right instanceof ValueLiteral) {
                ToInt32(ltype, mv);
            }
            ValType rtype = evalAndGetValue(right, mv);
            if (!(ltype.isPrimitive() || right instanceof ValueLiteral)) {
                mv.swap(ltype, rtype);
                ToInt32(ltype, mv);
                mv.swap(rtype, ValType.Number_int);
            }
            ToInt32(rtype, mv);
            mv.and(Type.INT_TYPE);
            return ValType.Number_int;
        }
        case BITXOR: {
            // 11.10 Binary Bitwise Operators ( ^ )
            ValType ltype = evalAndGetValue(left, mv);
            if (ltype.isPrimitive() || right instanceof ValueLiteral) {
                ToInt32(ltype, mv);
            }
            ValType rtype = evalAndGetValue(right, mv);
            if (!(ltype.isPrimitive() || right instanceof ValueLiteral)) {
                mv.swap(ltype, rtype);
                ToInt32(ltype, mv);
                mv.swap(rtype, ValType.Number_int);
            }
            ToInt32(rtype, mv);
            mv.xor(Type.INT_TYPE);
            return ValType.Number_int;
        }
        case BITOR: {
            // 11.10 Binary Bitwise Operators ( | )
            ValType ltype = evalAndGetValue(left, mv);
            if (ltype.isPrimitive() || right instanceof ValueLiteral) {
                ToInt32(ltype, mv);
            }
            ValType rtype = evalAndGetValue(right, mv);
            if (!(ltype.isPrimitive() || right instanceof ValueLiteral)) {
                mv.swap(ltype, rtype);
                ToInt32(ltype, mv);
                mv.swap(rtype, ValType.Number_int);
            }
            ToInt32(rtype, mv);
            mv.or(Type.INT_TYPE);
            return ValType.Number_int;
        }

        case AND:
        case OR: {
            // 11.11 Binary Logical Operators
            Label after = new Label();

            ValType ltype = evalAndGetValue(left, mv);
            mv.toBoxed(ltype);
            mv.dup();
            ToBoolean(ValType.Any, mv);
            if (node.getOperator() == BinaryExpression.Operator.AND) {
                mv.ifeq(after);
            } else {
                mv.ifne(after);
            }
            mv.pop();
            ValType rtype = evalAndGetValue(right, mv);
            mv.toBoxed(rtype);
            mv.mark(after);

            return ValType.Any;
        }
        default:
            throw new IllegalStateException(Objects.toString(node.getOperator(), "<null>"));
        }
    }

    @Override
    public ValType visit(BooleanLiteral node, ExpressionVisitor mv) {
        mv.iconst(node.getValue());

        return ValType.Boolean;
    }

    @Override
    public ValType visit(CallExpression node, ExpressionVisitor mv) {
        ValType type = node.getBase().accept(this, mv);
        mv.toBoxed(type);

        // direct call to eval?
        boolean directEval = (node.getBase() instanceof Identifier && "eval"
                .equals(((Identifier) node.getBase()).getName()));
        EvaluateCall(node, node.getBase(), type, node.getArguments(), directEval, mv);

        return ValType.Any;
    }

    @Override
    public ValType visit(CallSpreadElement node, ExpressionVisitor mv) {
        ValType type = evalAndGetValue(node.getExpression(), mv);
        mv.toBoxed(type);
        mv.loadExecutionContext();
        mv.invoke(Methods.ScriptRuntime_SpreadArray);

        return ValType.Any; // actually Object[]
    }

    @Override
    public ValType visit(ClassExpression node, ExpressionVisitor mv) {
        String className = (node.getName() != null ? node.getName().getName() : null);
        ClassDefinitionEvaluation(node, className, mv);

        return ValType.Object;
    }

    @Override
    public ValType visit(CommaExpression node, ExpressionVisitor mv) {
        ValType type = null;
        List<Expression> list = node.getOperands();
        for (Expression e : list) {
            if (type != null) {
                mv.pop(type);
            }
            type = evalAndGetValue(e, mv);
        }
        assert type != null;
        return type;
    }

    @Override
    public ValType visit(ConditionalExpression node, ExpressionVisitor mv) {
        Label l0 = new Label(), l1 = new Label();

        ValType typeTest = evalAndGetValue(node.getTest(), mv);
        ToBoolean(typeTest, mv);
        mv.ifeq(l0);
        ValType typeThen = evalAndGetValue(node.getThen(), mv);
        mv.toBoxed(typeThen);
        mv.goTo(l1);
        mv.mark(l0);
        ValType typeOtherwise = evalAndGetValue(node.getOtherwise(), mv);
        mv.toBoxed(typeOtherwise);
        mv.mark(l1);

        return ValType.Any;
    }

    @Override
    public ValType visit(ElementAccessor node, ExpressionVisitor mv) {
        ValType baseType = evalAndGetValue(node.getBase(), mv);
        mv.toBoxed(baseType);
        ValType elementType = evalAndGetValue(node.getElement(), mv);
        mv.toBoxed(elementType);
        mv.loadExecutionContext();
        mv.iconst(mv.isStrict());
        mv.invoke(Methods.ScriptRuntime_getElement);

        return ValType.Reference;
    }

    @Override
    public ValType visit(ElementAccessorValue node, ExpressionVisitor mv) {
        ValType baseType = evalAndGetValue(node.getBase(), mv);
        mv.toBoxed(baseType);
        ValType elementType = evalAndGetValue(node.getElement(), mv);
        mv.toBoxed(elementType);
        mv.loadExecutionContext();
        mv.iconst(mv.isStrict());
        mv.invoke(Methods.ScriptRuntime_getElementValue);

        return ValType.Any;
    }

    @Override
    public ValType visit(ExpressionMethod node, ExpressionVisitor mv) {
        codegen.compile(node, mv);

        String desc = Type.getMethodDescriptor(Types.Object, Types.ExecutionContext);
        mv.loadExecutionContext();
        mv.invokestatic(codegen.getClassName(), codegen.methodName(node), desc);

        return ValType.Any;
    }

    @Override
    public ValType visit(FunctionExpression node, ExpressionVisitor mv) {
        codegen.compile(node);

        // Runtime Semantics: Evaluation -> FunctionExpression
        mv.invokestatic(codegen.getClassName(), codegen.methodName(node, FunctionName.RTI),
                Type.getMethodDescriptor(Types.RuntimeInfo$Function));
        mv.loadExecutionContext();
        mv.invoke(Methods.ScriptRuntime_EvaluateFunctionExpression);

        return ValType.Object;
    }

    @Override
    public ValType visit(GeneratorComprehension node, ExpressionVisitor mv) {
        codegen.compile(node, mv);

        String desc = Type.getMethodDescriptor(Types.Object, Types.ExecutionContext);
        mv.invokeStaticMH(codegen.getClassName(), codegen.methodName(node), desc);
        mv.loadExecutionContext();
        mv.invoke(Methods.ScriptRuntime_EvaluateGeneratorComprehension);

        return ValType.Object;
    }

    @Override
    public ValType visit(GeneratorExpression node, ExpressionVisitor mv) {
        codegen.compile(node);

        // Runtime Semantics: Evaluation -> FunctionExpression
        mv.invokestatic(codegen.getClassName(), codegen.methodName(node, FunctionName.RTI),
                Type.getMethodDescriptor(Types.RuntimeInfo$Function));
        mv.loadExecutionContext();
        mv.invoke(Methods.ScriptRuntime_EvaluateGeneratorExpression);

        return ValType.Object;
    }

    @Override
    public ValType visit(Identifier node, ExpressionVisitor mv) {
        return identifierResolution.resolve(node, mv);
    }

    @Override
    public ValType visit(IdentifierValue node, ExpressionVisitor mv) {
        return identifierResolution.resolveValue(node, mv);
    }

    @Override
    public ValType visit(LetExpression node, ExpressionVisitor mv) {
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

        ValType type = evalAndGetValue(node.getExpression(), mv);

        // restore previous lexical environment
        popLexicalEnvironment(mv);
        mv.exitScope();

        return type;
    }

    @Override
    public ValType visit(NewExpression node, ExpressionVisitor mv) {
        ValType type = evalAndGetValue(node.getExpression(), mv);
        mv.toBoxed(type);
        ArgumentListEvaluation(node.getArguments(), mv);
        mv.lineInfo(node);
        mv.loadExecutionContext();
        mv.invoke(Methods.ScriptRuntime_EvaluateConstructorCall);

        return ValType.Any;
    }

    @Override
    public ValType visit(NullLiteral node, ExpressionVisitor mv) {
        mv.loadNull();

        return ValType.Null;
    }

    @Override
    public ValType visit(NumericLiteral node, ExpressionVisitor mv) {
        double v = node.getValue();
        if ((int) v == v) {
            mv.iconst((int) v);
            return ValType.Number_int;
        } else {
            mv.dconst(v);
            return ValType.Number;
        }
    }

    @Override
    public ValType visit(ObjectLiteral node, ExpressionVisitor mv) {
        mv.loadExecutionContext();
        mv.get(Fields.Intrinsics_ObjectPrototype);
        mv.invoke(Methods.OrdinaryObject_ObjectCreate);
        for (PropertyDefinition property : node.getProperties()) {
            mv.dup();
            codegen.propertyDefinition(property, mv);
        }

        return ValType.Object;
    }

    @Override
    public ValType visit(PropertyAccessor node, ExpressionVisitor mv) {
        ValType type = evalAndGetValue(node.getBase(), mv);
        mv.toBoxed(type);
        mv.aconst(node.getName());
        mv.loadExecutionContext();
        mv.iconst(mv.isStrict());
        mv.invoke(Methods.ScriptRuntime_getProperty);

        return ValType.Reference;
    }

    @Override
    public ValType visit(PropertyAccessorValue node, ExpressionVisitor mv) {
        ValType type = evalAndGetValue(node.getBase(), mv);
        mv.toBoxed(type);
        mv.aconst(node.getName());
        mv.loadExecutionContext();
        mv.iconst(mv.isStrict());
        mv.invoke(Methods.ScriptRuntime_getPropertyValue);

        return ValType.Any;
    }

    @Override
    public ValType visit(RegularExpressionLiteral node, ExpressionVisitor mv) {
        mv.loadExecutionContext();
        mv.aconst(node.getRegexp());
        mv.aconst(node.getFlags());
        mv.invoke(Methods.ScriptRuntime_RegExp);

        return ValType.Object;
    }

    @Override
    public ValType visit(StringLiteral node, ExpressionVisitor mv) {
        mv.aconst(node.getValue());

        return ValType.String;
    }

    @Override
    public ValType visit(SuperExpression node, ExpressionVisitor mv) {
        if (node.getName() != null) {
            mv.loadExecutionContext();
            mv.aconst(node.getName());
            mv.iconst(mv.isStrict());
            mv.invoke(Methods.ScriptRuntime_MakeStringSuperReference);

            return ValType.Reference;
        } else if (node.getExpression() != null) {
            mv.loadExecutionContext();
            ValType type = evalAndGetValue(node.getExpression(), mv);
            ToPropertyKey(type, mv);
            mv.iconst(mv.isStrict());
            mv.invoke(Methods.ScriptRuntime_MakeSuperReference);

            return ValType.Reference;
        } else if (node.getArguments() != null) {
            mv.loadExecutionContext();
            mv.aconst(null);
            mv.iconst(mv.isStrict());
            mv.invoke(Methods.ScriptRuntime_MakeSuperReference);

            EvaluateCall(node, node, ValType.Reference, node.getArguments(), false, mv);

            return ValType.Any;
        } else {
            mv.loadExecutionContext();
            mv.aconst(null);
            mv.iconst(mv.isStrict());
            mv.invoke(Methods.ScriptRuntime_MakeSuperReference);

            return ValType.Reference;
        }
    }

    @Override
    public ValType visit(SuperExpressionValue node, ExpressionVisitor mv) {
        if (node.getName() != null) {
            mv.loadExecutionContext();
            mv.aconst(node.getName());
            mv.iconst(mv.isStrict());
            mv.invoke(Methods.ScriptRuntime_MakeStringSuperReference);

            GetValue(node, ValType.Reference, mv);

            return ValType.Any;
        } else if (node.getExpression() != null) {
            mv.loadExecutionContext();
            ValType type = evalAndGetValue(node.getExpression(), mv);
            ToPropertyKey(type, mv);
            mv.iconst(mv.isStrict());
            mv.invoke(Methods.ScriptRuntime_MakeSuperReference);

            GetValue(node, ValType.Reference, mv);

            return ValType.Any;
        } else if (node.getArguments() != null) {
            mv.loadExecutionContext();
            mv.aconst(null);
            mv.iconst(mv.isStrict());
            mv.invoke(Methods.ScriptRuntime_MakeSuperReference);

            EvaluateCall(node, node, ValType.Reference, node.getArguments(), false, mv);

            return ValType.Any;
        } else {
            mv.loadExecutionContext();
            mv.aconst(null);
            mv.iconst(mv.isStrict());
            mv.invoke(Methods.ScriptRuntime_MakeSuperReference);

            GetValue(node, ValType.Reference, mv);

            return ValType.Any;
        }
    }

    @Override
    public ValType visit(TemplateCallExpression node, ExpressionVisitor mv) {
        codegen.compile(node.getTemplate());

        TemplateLiteral template = node.getTemplate();
        List<Expression> substitutions = Substitutions(template);
        List<Expression> arguments = new ArrayList<>(substitutions.size() + 1);
        arguments.add(template);
        arguments.addAll(substitutions);

        ValType type = node.getBase().accept(this, mv);
        mv.toBoxed(type);
        EvaluateCall(node, node.getBase(), type, arguments, false, mv);

        return ValType.Any;
    }

    @Override
    public ValType visit(TemplateLiteral node, ExpressionVisitor mv) {
        if (node.isTagged()) {
            codegen.GetTemplateCallSite(node, mv);
            return ValType.Object;
        }

        mv.anew(Types.StringBuilder);
        mv.dup();
        mv.invoke(Methods.StringBuilder_init);

        boolean chars = true;
        for (Expression expr : node.getElements()) {
            assert chars == (expr instanceof TemplateCharacters);
            if (chars) {
                mv.aconst(((TemplateCharacters) expr).getValue());
                mv.invoke(Methods.StringBuilder_append_String);
            } else {
                ValType type = evalAndGetValue(expr, mv);
                ToString(type, mv);
                mv.invoke(Methods.StringBuilder_append_Charsequence);
            }
            chars = !chars;
        }

        mv.invoke(Methods.StringBuilder_toString);

        return ValType.String;
    }

    @Override
    public ValType visit(ThisExpression node, ExpressionVisitor mv) {
        mv.loadExecutionContext();
        mv.invoke(Methods.ExecutionContext_thisResolution);

        return ValType.Any;
    }

    @Override
    public ValType visit(UnaryExpression node, ExpressionVisitor mv) {
        switch (node.getOperator()) {
        case POST_INC: {
            // 11.3.1 Postfix Increment Operator
            Expression expr = node.getOperand();
            ValType type = expr.accept(this, mv);
            assert type == ValType.Reference : "reference lhs for inc/dec";
            mv.dup();
            GetValue(expr, type, mv);
            ToNumber(type, mv);
            mv.dupX(type, ValType.Number);
            mv.dconst(1d);
            mv.add(Type.DOUBLE_TYPE);
            mv.toBoxed(ValType.Number);
            PutValue(expr, type, mv);
            return ValType.Number;
        }
        case POST_DEC: {
            // 11.3.2 Postfix Decrement Operator
            Expression expr = node.getOperand();
            ValType type = expr.accept(this, mv);
            assert type == ValType.Reference : "reference lhs for inc/dec";
            mv.dup();
            GetValue(expr, type, mv);
            ToNumber(type, mv);
            mv.dupX(type, ValType.Number);
            mv.dconst(1d);
            mv.sub(Type.DOUBLE_TYPE);
            mv.toBoxed(ValType.Number);
            PutValue(expr, type, mv);
            return ValType.Number;
        }
        case DELETE: {
            // 11.4.1 The delete Operator
            Expression expr = node.getOperand();
            ValType type = expr.accept(this, mv);
            mv.toBoxed(type);
            mv.loadExecutionContext();
            mv.invoke(Methods.ScriptRuntime_delete);
            return ValType.Boolean;
        }
        case VOID: {
            // 11.4.2 The void Operator
            Expression expr = node.getOperand();
            ValType type = evalAndGetValue(expr, mv);
            mv.pop(type);
            mv.loadUndefined();
            return ValType.Undefined;
        }
        case TYPEOF: {
            // 11.4.3 The typeof Operator
            Expression expr = node.getOperand();
            ValType type = expr.accept(this, mv);
            mv.toBoxed(type);
            mv.loadExecutionContext();
            mv.invoke(Methods.ScriptRuntime_typeof);
            return ValType.String;
        }
        case PRE_INC: {
            // 11.4.4 Prefix Increment Operator
            Expression expr = node.getOperand();
            ValType type = expr.accept(this, mv);
            assert type == ValType.Reference : "reference lhs for inc/dec";
            mv.dup();
            GetValue(expr, type, mv);
            ToNumber(type, mv);
            mv.dconst(1d);
            mv.add(Type.DOUBLE_TYPE);
            mv.dupX(type, ValType.Number);
            mv.toBoxed(ValType.Number);
            PutValue(expr, type, mv);
            return ValType.Number;
        }
        case PRE_DEC: {
            // 11.4.5 Prefix Decrement Operator
            Expression expr = node.getOperand();
            ValType type = expr.accept(this, mv);
            assert type == ValType.Reference : "reference lhs for inc/dec";
            mv.dup();
            GetValue(expr, type, mv);
            ToNumber(type, mv);
            mv.dconst(1d);
            mv.sub(Type.DOUBLE_TYPE);
            mv.dupX(type, ValType.Number);
            mv.toBoxed(ValType.Number);
            PutValue(expr, type, mv);
            return ValType.Number;
        }
        case POS: {
            // 11.4.6 Unary + Operator
            Expression expr = node.getOperand();
            ValType type = evalAndGetValue(expr, mv);
            ToNumber(type, mv);
            return ValType.Number;
        }
        case NEG: {
            // 11.4.7 Unary - Operator
            Expression expr = node.getOperand();
            ValType type = evalAndGetValue(expr, mv);
            ToNumber(type, mv);
            mv.neg(Type.DOUBLE_TYPE);
            return ValType.Number;
        }
        case BITNOT: {
            // 11.4.8 Bitwise NOT Operator ( ~ )
            Expression expr = node.getOperand();
            ValType type = evalAndGetValue(expr, mv);
            ToInt32(type, mv);
            mv.bitnot();
            return ValType.Number_int;
        }
        case NOT: {
            // 11.4.9 Logical NOT Operator ( ! )
            Expression expr = node.getOperand();
            ValType type = evalAndGetValue(expr, mv);
            ToBoolean(type, mv);
            mv.not();
            return ValType.Boolean;
        }
        default:
            throw new IllegalStateException(Objects.toString(node.getOperator(), "<null>"));
        }
    }

    @Override
    public ValType visit(YieldExpression node, ExpressionVisitor mv) {
        Expression expr = node.getExpression();
        if (expr != null) {
            ValType type = evalAndGetValue(expr, mv);
            mv.toBoxed(type);
        } else {
            mv.loadUndefined();
        }

        mv.loadExecutionContext();
        if (node.isDelegatedYield()) {
            mv.lineInfo(node);
            mv.invoke(Methods.ScriptRuntime_delegatedYield);
        } else {
            mv.lineInfo(node);
            mv.invoke(Methods.ScriptRuntime_yield);
        }

        return ValType.Any;
    }
}
