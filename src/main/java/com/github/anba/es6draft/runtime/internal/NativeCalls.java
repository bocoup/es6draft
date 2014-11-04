/**
 * Copyright (c) 2012-2014 André Bargull
 * Alle Rechte vorbehalten / All Rights Reserved.  Use is subject to license terms.
 *
 * <https://github.com/anba/es6draft>
 */
package com.github.anba.es6draft.runtime.internal;

import static com.github.anba.es6draft.runtime.internal.Errors.newInternalError;
import static com.github.anba.es6draft.runtime.types.Undefined.UNDEFINED;

import java.io.IOException;
import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.Path;
import java.util.Objects;

import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;

import com.github.anba.es6draft.Script;
import com.github.anba.es6draft.Scripts;
import com.github.anba.es6draft.compiler.CompilationException;
import com.github.anba.es6draft.parser.ParserException;
import com.github.anba.es6draft.runtime.AbstractOperations;
import com.github.anba.es6draft.runtime.ExecutionContext;
import com.github.anba.es6draft.runtime.Realm;
import com.github.anba.es6draft.runtime.objects.GlobalObject;
import com.github.anba.es6draft.runtime.objects.binary.ArrayBufferConstructor;
import com.github.anba.es6draft.runtime.objects.binary.ArrayBufferObject;
import com.github.anba.es6draft.runtime.objects.collection.MapObject;
import com.github.anba.es6draft.runtime.objects.collection.SetObject;
import com.github.anba.es6draft.runtime.objects.collection.WeakMapObject;
import com.github.anba.es6draft.runtime.objects.collection.WeakSetObject;
import com.github.anba.es6draft.runtime.objects.iteration.GeneratorObject;
import com.github.anba.es6draft.runtime.objects.text.RegExpObject;
import com.github.anba.es6draft.runtime.objects.text.RegExpPrototype;
import com.github.anba.es6draft.runtime.types.Callable;
import com.github.anba.es6draft.runtime.types.Intrinsics;
import com.github.anba.es6draft.runtime.types.ScriptObject;
import com.github.anba.es6draft.runtime.types.Symbol;
import com.github.anba.es6draft.runtime.types.Type;
import com.github.anba.es6draft.runtime.types.Undefined;
import com.github.anba.es6draft.runtime.types.builtins.FunctionObject;
import com.github.anba.es6draft.runtime.types.builtins.OrdinaryObject;

/**
 * Support class for native function calls.
 */
public final class NativeCalls {
    private NativeCalls() {
    }

    private static final class Types {
        static final org.objectweb.asm.Type Object = org.objectweb.asm.Type.getType(Object.class);
        static final org.objectweb.asm.Type Object_ = org.objectweb.asm.Type
                .getType(Object[].class);
        static final org.objectweb.asm.Type ExecutionContext = org.objectweb.asm.Type
                .getType(ExecutionContext.class);
    }

    /**
     * Returns the native call name.
     * 
     * @param name
     *            the native call function name
     * @return the native call name
     */
    public static String getNativeCallName(String name) {
        return "native:" + name;
    }

    private static final Handle BOOTSTRAP;
    static {
        MethodType mt = MethodType.methodType(CallSite.class, MethodHandles.Lookup.class,
                String.class, MethodType.class);
        BOOTSTRAP = new Handle(Opcodes.H_INVOKESTATIC,
                org.objectweb.asm.Type.getInternalName(NativeCalls.class), "bootstrapDynamic",
                mt.toMethodDescriptorString());
    }

    /**
     * Returns the native call bootstrap handle.
     * 
     * @return the bootstrap handle
     */
    public static Handle getNativeCallBootstrap() {
        return BOOTSTRAP;
    }

    private static final String OP_NATIVE_CALL = org.objectweb.asm.Type.getMethodDescriptor(
            Types.Object, Types.Object_, Types.ExecutionContext);

    /**
     * Returns the native call method descriptor.
     * 
     * @return the method descriptor
     */
    public static String getNativeCallMethodDescriptor() {
        return OP_NATIVE_CALL;
    }

    /**
     * Returns the native call {@code CallSite} object.
     * 
     * @param caller
     *            the caller lookup object
     * @param name
     *            the native call name
     * @param type
     *            the native call type
     * @return the native call {@code CallSite} object
     */
    public static CallSite bootstrapDynamic(MethodHandles.Lookup caller, String name,
            MethodType type) {
        MethodHandle target;
        switch (name) {
        case "native:Intrinsic":
            target = callIntrinsicMH;
            break;
        case "native:SetIntrinsic":
            target = callSetIntrinsicMH;
            break;
        case "native:GlobalObject":
            target = callGlobalObjectMH;
            break;
        case "native:GlobalThis":
            target = callGlobalThisMH;
            break;
        case "native:CallFunction":
            target = callCallFunctionMH;
            break;
        case "native:IsGenerator":
            target = callIsGeneratorMH;
            break;
        case "native:IsUninitializedMap":
            target = callIsUninitializedMapMH;
            break;
        case "native:IsUninitializedSet":
            target = callIsUninitializedSetMH;
            break;
        case "native:IsUninitializedWeakMap":
            target = callIsUninitializedWeakMapMH;
            break;
        case "native:IsUninitializedWeakSet":
            target = callIsUninitializedWeakSetMH;
            break;
        case "native:RegExpReplace":
            target = callRegExpReplaceMH;
            break;
        case "native:RegExpTest":
            target = callRegExpTestMH;
            break;
        case "native:IsFunctionExpression":
            target = callIsFunctionExpressionMH;
            break;
        case "native:SymbolDescription":
            target = callSymbolDescriptionMH;
            break;
        case "native:Include":
            target = callIncludeMH;
            break;
        case "native:IsArrayBuffer":
            target = callIsArrayBufferMH;
            break;
        case "native:IsDetachedBuffer":
            target = callIsDetachedBufferMH;
            break;
        case "native:ToPropertyKey":
            target = callToPropertyKeyMH;
            break;
        default:
            target = MethodHandles.insertArguments(invalidNativeCallMH, 0, name);
            target = MethodHandles.dropArguments(target, 0, Object[].class);
            break;
        }
        return new ConstantCallSite(target);
    }

    private static final MethodHandle callIntrinsicMH, callSetIntrinsicMH, callGlobalObjectMH,
            callGlobalThisMH, callCallFunctionMH, callIsGeneratorMH, callIsUninitializedMapMH,
            callIsUninitializedSetMH, callIsUninitializedWeakMapMH, callIsUninitializedWeakSetMH,
            callRegExpReplaceMH, callRegExpTestMH, callIsFunctionExpressionMH,
            callSymbolDescriptionMH, callIncludeMH, callIsArrayBufferMH, callIsDetachedBufferMH,
            callToPropertyKeyMH, invalidNativeCallMH;
    static {
        MethodLookup lookup = new MethodLookup(MethodHandles.lookup());
        MethodType callType = MethodType.methodType(Object.class, Object[].class,
                ExecutionContext.class);
        callIntrinsicMH = lookup.findStatic("call_Intrinsic", callType);
        callSetIntrinsicMH = lookup.findStatic("call_SetIntrinsic", callType);
        callGlobalObjectMH = lookup.findStatic("call_GlobalObject", callType);
        callGlobalThisMH = lookup.findStatic("call_GlobalThis", callType);
        callCallFunctionMH = lookup.findStatic("call_CallFunction", callType);
        callIsGeneratorMH = lookup.findStatic("call_IsGenerator", callType);
        callIsUninitializedMapMH = lookup.findStatic("call_IsUninitializedMap", callType);
        callIsUninitializedSetMH = lookup.findStatic("call_IsUninitializedSet", callType);
        callIsUninitializedWeakMapMH = lookup.findStatic("call_IsUninitializedWeakMap", callType);
        callIsUninitializedWeakSetMH = lookup.findStatic("call_IsUninitializedWeakSet", callType);
        callRegExpReplaceMH = lookup.findStatic("call_RegExpReplace", callType);
        callRegExpTestMH = lookup.findStatic("call_RegExpTest", callType);
        callIsFunctionExpressionMH = lookup.findStatic("call_IsFunctionExpression", callType);
        callSymbolDescriptionMH = lookup.findStatic("call_SymbolDescription", callType);
        callIncludeMH = lookup.findStatic("call_Include", callType);
        callIsArrayBufferMH = lookup.findStatic("call_IsArrayBuffer", callType);
        callIsDetachedBufferMH = lookup.findStatic("call_IsDetachedBuffer", callType);
        callToPropertyKeyMH = lookup.findStatic("call_ToPropertyKey", callType);
        invalidNativeCallMH = lookup.findStatic("invalidNativeCall",
                MethodType.methodType(Object.class, String.class, ExecutionContext.class));
    }

    @SuppressWarnings("unused")
    private static Object call_Intrinsic(Object[] args, ExecutionContext cx) {
        if (args.length == 1 && Type.isString(args[0])) {
            String intrinsicName = Type.stringValue(args[0]).toString();
            return Intrinsic(cx, intrinsicName);
        }
        return invalidNativeCallArguments(cx);
    }

    @SuppressWarnings("unused")
    private static Object call_SetIntrinsic(Object[] args, ExecutionContext cx) {
        if (args.length == 2 && Type.isString(args[0]) && args[1] instanceof OrdinaryObject) {
            String intrinsicName = Type.stringValue(args[0]).toString();
            OrdinaryObject intrinsicValue = (OrdinaryObject) args[1];
            return SetIntrinsic(cx, intrinsicName, intrinsicValue);
        }
        return invalidNativeCallArguments(cx);
    }

    @SuppressWarnings("unused")
    private static Object call_GlobalObject(Object[] args, ExecutionContext cx) {
        if (args.length == 0) {
            return GlobalObject(cx);
        }
        return invalidNativeCallArguments(cx);
    }

    @SuppressWarnings("unused")
    private static Object call_GlobalThis(Object[] args, ExecutionContext cx) {
        if (args.length == 0) {
            return GlobalThis(cx);
        }
        return invalidNativeCallArguments(cx);
    }

    @SuppressWarnings("unused")
    private static Object call_CallFunction(Object[] args, ExecutionContext cx) {
        if (args.length >= 2 && args[0] instanceof Callable) {
            Object[] arguments;
            if (args.length > 2) {
                arguments = new Object[args.length - 2];
                System.arraycopy(args, 2, arguments, 0, arguments.length);
            } else {
                arguments = ScriptRuntime.EMPTY_ARRAY;
            }
            return CallFunction(cx, (Callable) args[0], args[1], arguments);
        }
        return invalidNativeCallArguments(cx);
    }

    @SuppressWarnings("unused")
    private static Object call_IsGenerator(Object[] args, ExecutionContext cx) {
        if (args.length == 1) {
            return IsGenerator(args[0]);
        }
        return invalidNativeCallArguments(cx);
    }

    @SuppressWarnings("unused")
    private static Object call_IsUninitializedMap(Object[] args, ExecutionContext cx) {
        if (args.length == 1) {
            return IsUninitializedMap(args[0]);
        }
        return invalidNativeCallArguments(cx);
    }

    @SuppressWarnings("unused")
    private static Object call_IsUninitializedSet(Object[] args, ExecutionContext cx) {
        if (args.length == 1) {
            return IsUninitializedSet(args[0]);
        }
        return invalidNativeCallArguments(cx);
    }

    @SuppressWarnings("unused")
    private static Object call_IsUninitializedWeakMap(Object[] args, ExecutionContext cx) {
        if (args.length == 1) {
            return IsUninitializedWeakMap(args[0]);
        }
        return invalidNativeCallArguments(cx);
    }

    @SuppressWarnings("unused")
    private static Object call_IsUninitializedWeakSet(Object[] args, ExecutionContext cx) {
        if (args.length == 1) {
            return IsUninitializedWeakSet(args[0]);
        }
        return invalidNativeCallArguments(cx);
    }

    @SuppressWarnings("unused")
    private static Object call_RegExpReplace(Object[] args, ExecutionContext cx) {
        if (args.length == 3 && args[0] instanceof RegExpObject && Type.isString(args[1])
                && Type.isString(args[2])) {
            return RegExpReplace(cx, (RegExpObject) args[0], Type.stringValue(args[1]),
                    Type.stringValue(args[2]));
        }
        return invalidNativeCallArguments(cx);
    }

    @SuppressWarnings("unused")
    private static Object call_RegExpTest(Object[] args, ExecutionContext cx) {
        if (args.length == 2 && args[0] instanceof RegExpObject && Type.isString(args[1])) {
            return RegExpTest(cx, (RegExpObject) args[0], Type.stringValue(args[1]));
        }
        return invalidNativeCallArguments(cx);
    }

    @SuppressWarnings("unused")
    private static Object call_IsFunctionExpression(Object[] args, ExecutionContext cx) {
        if (args.length == 1 && args[0] instanceof Callable) {
            return IsFunctionExpression((Callable) args[0]);
        }
        return invalidNativeCallArguments(cx);
    }

    @SuppressWarnings("unused")
    private static Object call_SymbolDescription(Object[] args, ExecutionContext cx) {
        if (args.length == 1 && args[0] instanceof Symbol) {
            return SymbolDescription((Symbol) args[0]);
        }
        return invalidNativeCallArguments(cx);
    }

    @SuppressWarnings("unused")
    private static Object call_Include(Object[] args, ExecutionContext cx) {
        if (args.length == 1 && Type.isString(args[0])) {
            return Include(cx, Type.stringValue(args[0]).toString());
        }
        return invalidNativeCallArguments(cx);
    }

    @SuppressWarnings("unused")
    private static Object call_IsArrayBuffer(Object[] args, ExecutionContext cx) {
        if (args.length == 1) {
            return IsArrayBuffer(args[0]);
        }
        return invalidNativeCallArguments(cx);
    }

    @SuppressWarnings("unused")
    private static Object call_IsDetachedBuffer(Object[] args, ExecutionContext cx) {
        if (args.length == 1 && args[0] instanceof ArrayBufferObject) {
            return IsDetachedBuffer((ArrayBufferObject) args[0]);
        }
        return invalidNativeCallArguments(cx);
    }

    @SuppressWarnings("unused")
    private static Object call_ToPropertyKey(Object[] args, ExecutionContext cx) {
        if (args.length == 1) {
            return ToPropertyKey(cx, args[0]);
        }
        return invalidNativeCallArguments(cx);
    }

    @SuppressWarnings("unused")
    private static Object invalidNativeCall(String name, ExecutionContext cx) {
        throw newInternalError(cx, Messages.Key.InternalError, "Invalid native call " + name);
    }

    private static Object invalidNativeCallArguments(ExecutionContext cx) {
        throw newInternalError(cx, Messages.Key.InternalError, "Invalid native call arguments");
    }

    private static Intrinsics getIntrinsicByName(ExecutionContext cx, String name) {
        try {
            return Intrinsics.valueOf(name);
        } catch (IllegalArgumentException e) {
            throw newInternalError(cx, Messages.Key.InternalError, "Invalid intrinsic: " + name);
        }
    }

    /**
     * Native function: {@code %Intrinsic(<name>)}.
     * <p>
     * Returns the intrinsic by name.
     * 
     * @param cx
     *            the execution context
     * @param name
     *            the intrinsic name
     * @return the intrinsic
     */
    public static OrdinaryObject Intrinsic(ExecutionContext cx, String name) {
        Intrinsics id = getIntrinsicByName(cx, name);
        return cx.getRealm().getIntrinsic(id);
    }

    /**
     * Native function: {@code %SetIntrinsic(<name>, <realm>)}.
     * <p>
     * Sets the intrinsic to a new value.
     * 
     * @param cx
     *            the execution context
     * @param name
     *            the intrinsic name
     * @param intrinsic
     *            the new intrinsic object
     * @return the intrinsic
     */
    public static OrdinaryObject SetIntrinsic(ExecutionContext cx, String name,
            OrdinaryObject intrinsic) {
        Intrinsics id = getIntrinsicByName(cx, name);
        cx.getRealm().setIntrinsic(id, intrinsic);
        return intrinsic;
    }

    /**
     * Native function: {@code %GlobalObject()}.
     * <p>
     * Returns the global object.
     * 
     * @param cx
     *            the execution context
     * @return the global object
     */
    public static GlobalObject GlobalObject(ExecutionContext cx) {
        return cx.getRealm().getGlobalObject();
    }

    /**
     * Native function: {@code %GlobalThis()}.
     * <p>
     * Returns the global this.
     * 
     * @param cx
     *            the execution context
     * @return the global this
     */
    public static ScriptObject GlobalThis(ExecutionContext cx) {
        return cx.getRealm().getGlobalThis();
    }

    /**
     * Native function: {@code %CallFunction(<function>, <thisValue>, ...<arguments>)}.
     * <p>
     * Calls the function object.
     * 
     * @param cx
     *            the execution context
     * @param fn
     *            the function
     * @param thisValue
     *            the this-value
     * @param args
     *            the function arguments
     * @return the function return value
     */
    public static Object CallFunction(ExecutionContext cx, Callable fn, Object thisValue,
            Object... args) {
        return fn.call(cx, thisValue, args);
    }

    /**
     * Native function: {@code %IsGenerator(<value>)}.
     * <p>
     * Tests whether the input argument is a generator object.
     * 
     * @param value
     *            the input argument
     * @return {@code true} if the object is a generator
     */
    public static boolean IsGenerator(Object value) {
        return value instanceof GeneratorObject;
    }

    /**
     * Native function: {@code %IsUninitializedMap(<value>)}.
     * <p>
     * Tests whether the input argument is an uninitialized map object.
     * 
     * @param value
     *            the input argument
     * @return {@code true} if the object is an uninitialized map object
     */
    public static boolean IsUninitializedMap(Object value) {
        return value instanceof MapObject && !((MapObject) value).isInitialized();
    }

    /**
     * Native function: {@code %IsUninitializedSet(<value>)}.
     * <p>
     * Tests whether the input argument is an uninitialized set object.
     * 
     * @param value
     *            the input argument
     * @return {@code true} if the object is an uninitialized set object
     */
    public static boolean IsUninitializedSet(Object value) {
        return value instanceof SetObject && !((SetObject) value).isInitialized();
    }

    /**
     * Native function: {@code %IsUninitializedWeakMap(<value>)}.
     * <p>
     * Tests whether the input argument is an uninitialized weak map object.
     * 
     * @param value
     *            the input argument
     * @return {@code true} if the object is an uninitialized weak map object
     */
    public static boolean IsUninitializedWeakMap(Object value) {
        return value instanceof WeakMapObject && !((WeakMapObject) value).isInitialized();
    }

    /**
     * Native function: {@code %IsUninitializedWeakSet(<value>)}.
     * <p>
     * Tests whether the input argument is an uninitialized weak set object.
     * 
     * @param value
     *            the input argument
     * @return {@code true} if the object is an uninitialized weak set object
     */
    public static boolean IsUninitializedWeakSet(Object value) {
        return value instanceof WeakSetObject && !((WeakSetObject) value).isInitialized();
    }

    /**
     * Native function: {@code %RegExpReplace(<regexp>, <string>, <replacement>)}.
     * <p>
     * Replaces every occurrence of <var>regexp</var> in <var>string</var> with
     * <var>replacement</var>.
     * 
     * @param cx
     *            the execution context
     * @param regexp
     *            the regular expression object
     * @param string
     *            the input string
     * @param replacement
     *            the replacement string
     * @return the result string
     */
    public static String RegExpReplace(ExecutionContext cx, RegExpObject regexp,
            CharSequence string, CharSequence replacement) {
        return RegExpPrototype.RegExpReplace(cx, regexp, string.toString(), replacement.toString());
    }

    /**
     * Native function: {@code %RegExpTest(<regexp>, <string>)}.
     * <p>
     * Returns {@code true} if <var>string</var> matches <var>regexp</var>.
     * 
     * @param cx
     *            the execution context
     * @param regexp
     *            the regular expression object
     * @param string
     *            the input string
     * @return {@code true} if <var>string</var> matches <var>regexp</var>
     */
    public static boolean RegExpTest(ExecutionContext cx, RegExpObject regexp, CharSequence string) {
        return RegExpPrototype.RegExpTest(cx, regexp, string.toString());
    }

    /**
     * Native function: {@code %IsFunctionExpression(<function>)}.
     * <p>
     * Returns {@code true} if <var>function</var> is a function expression.
     * 
     * @param function
     *            the function object
     * @return {@code true} if <var>function</var> is a function expression
     */
    public static boolean IsFunctionExpression(Callable function) {
        if (!(function instanceof FunctionObject)) {
            return false;
        }
        FunctionObject funObj = (FunctionObject) function;
        RuntimeInfo.Function code = funObj.getCode();
        if (code == null) {
            return false;
        }
        int flags = code.functionFlags();
        return RuntimeInfo.FunctionFlags.Expression.isSet(flags)
                && !RuntimeInfo.FunctionFlags.Arrow.isSet(flags);
    }

    /**
     * Native function: {@code %SymbolDescription(<symbol>)}.
     * <p>
     * Returns the symbol's description or {@link Undefined#UNDEFINED}.
     * 
     * @param symbol
     *            the symbol object
     * @return the symbol's description or {@link Undefined#UNDEFINED}
     */
    public static Object SymbolDescription(Symbol symbol) {
        return symbol.getDescription() != null ? symbol.getDescription() : UNDEFINED;
    }

    /**
     * Native function: {@code %Include(<file>)}.
     * <p>
     * Loads and evaluates the script file.
     * 
     * @param cx
     *            the execution context
     * @param file
     *            the file path
     * @return the script evaluation result
     */
    public static Object Include(ExecutionContext cx, String file) {
        Realm realm = cx.getRealm();
        Source base = realm.sourceInfo(cx);
        if (base == null || base.getFile() == null) {
            throw newInternalError(cx, Messages.Key.InternalError,
                    "No source: " + Objects.toString(base));
        }
        Path path = base.getFile().getParent().resolve(file);
        Source source = new Source(path, path.getFileName().toString(), 1);
        Script script;
        try {
            script = realm.getScriptLoader().script(source, path);
        } catch (ParserException | CompilationException e) {
            throw e.toScriptException(cx);
        } catch (IOException e) {
            throw newInternalError(cx, Messages.Key.InternalError, e.toString());
        }
        return Scripts.ScriptEvaluation(script, realm);
    }

    /**
     * Native function: {@code %IsArrayBuffer(<value>)}.
     * <p>
     * Tests whether the input argument is an ArrayBuffer object.
     * 
     * @param value
     *            the input argument
     * @return {@code true} if the object is an ArrayBuffer
     */
    public static boolean IsArrayBuffer(Object value) {
        return value instanceof ArrayBufferObject;
    }

    /**
     * Native function: {@code %IsDetachedBuffer(<arrayBuffer>)}.
     * <p>
     * Tests whether or not the array buffer is detached.
     * 
     * @param arrayBuffer
     *            the array buffer object
     * @return {@code true} if the array buffer is detached
     */
    public static boolean IsDetachedBuffer(ArrayBufferObject arrayBuffer) {
        return ArrayBufferConstructor.IsDetachedBuffer(arrayBuffer);
    }

    /**
     * Native function: {@code %ToPropertyKey(<value>)}.
     * <p>
     * Converts the input argument to a property key.
     * 
     * @param cx
     *            the execution context
     * @param value
     *            the input argument
     * @return the property key
     */
    public static Object ToPropertyKey(ExecutionContext cx, Object value) {
        return AbstractOperations.ToPropertyKey(cx, value);
    }
}
