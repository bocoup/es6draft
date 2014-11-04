/**
 * Copyright (c) 2012-2014 André Bargull
 * Alle Rechte vorbehalten / All Rights Reserved.  Use is subject to license terms.
 *
 * <https://github.com/anba/es6draft>
 */
package com.github.anba.es6draft.runtime;

import static com.github.anba.es6draft.runtime.internal.Errors.newInternalError;
import static com.github.anba.es6draft.runtime.internal.Errors.newRangeError;
import static com.github.anba.es6draft.runtime.internal.Errors.newTypeError;
import static com.github.anba.es6draft.runtime.internal.ScriptRuntime.InstanceofOperator;
import static com.github.anba.es6draft.runtime.objects.BooleanObject.BooleanCreate;
import static com.github.anba.es6draft.runtime.objects.SymbolObject.SymbolCreate;
import static com.github.anba.es6draft.runtime.objects.number.NumberObject.NumberCreate;
import static com.github.anba.es6draft.runtime.objects.promise.PromiseAbstractOperations.AllocatePromise;
import static com.github.anba.es6draft.runtime.objects.promise.PromiseAbstractOperations.CreatePromiseCapabilityRecord;
import static com.github.anba.es6draft.runtime.objects.promise.PromiseConstructor.InitializePromise;
import static com.github.anba.es6draft.runtime.types.Undefined.UNDEFINED;
import static com.github.anba.es6draft.runtime.types.builtins.ArrayObject.ArrayCreate;
import static com.github.anba.es6draft.runtime.types.builtins.OrdinaryObject.ObjectCreate;
import static com.github.anba.es6draft.runtime.types.builtins.OrdinaryObject.OrdinaryCreateFromConstructor;
import static com.github.anba.es6draft.runtime.types.builtins.StringObject.StringCreate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;

import org.mozilla.javascript.ConsString;
import org.mozilla.javascript.DToA;
import org.mozilla.javascript.v8dtoa.FastDtoa;

import com.github.anba.es6draft.parser.NumberParser;
import com.github.anba.es6draft.runtime.internal.Messages;
import com.github.anba.es6draft.runtime.internal.ScriptException;
import com.github.anba.es6draft.runtime.internal.ScriptRuntime;
import com.github.anba.es6draft.runtime.internal.Strings;
import com.github.anba.es6draft.runtime.internal.TailCallInvocation;
import com.github.anba.es6draft.runtime.objects.FunctionPrototype;
import com.github.anba.es6draft.runtime.objects.internal.CompoundIterator;
import com.github.anba.es6draft.runtime.objects.internal.ListIterator;
import com.github.anba.es6draft.runtime.objects.promise.PromiseCapability;
import com.github.anba.es6draft.runtime.objects.promise.PromiseObject;
import com.github.anba.es6draft.runtime.types.*;
import com.github.anba.es6draft.runtime.types.builtins.ArrayObject;
import com.github.anba.es6draft.runtime.types.builtins.BoundFunctionObject;
import com.github.anba.es6draft.runtime.types.builtins.OrdinaryObject;
import com.google.doubleconversion.DoubleConversion;

/**
 * <h1>7 Abstract Operations</h1>
 * <ul>
 * <li>7.1 Type Conversion and Testing
 * <li>7.2 Testing and Comparison Operations
 * <li>7.3 Operations on Objects
 * <li>7.4 Operations on Iterator Objects
 * <li>7.5 Operations on Promise Objects
 * </ul>
 */
public final class AbstractOperations {
    private AbstractOperations() {
    }

    /**
     * Hint string for
     * {@link AbstractOperations#ToPrimitive(ExecutionContext, Object, ToPrimitiveHint)}
     */
    public enum ToPrimitiveHint {
        Default, String, Number;

        @Override
        public String toString() {
            switch (this) {
            case String:
                return "string";
            case Number:
                return "number";
            case Default:
            default:
                return "default";
            }
        }
    }

    /**
     * 7.1.1 ToPrimitive ( input [, PreferredType] )
     * 
     * @param cx
     *            the execution context
     * @param argument
     *            the argument value
     * @return the primitive value
     */
    public static Object ToPrimitive(ExecutionContext cx, Object argument) {
        if (!Type.isObject(argument)) {
            return argument;
        }
        return ToPrimitive(cx, Type.objectValue(argument), ToPrimitiveHint.Default);
    }

    /**
     * 7.1.1 ToPrimitive ( input [, PreferredType] )
     * 
     * @param cx
     *            the execution context
     * @param argument
     *            the argument value
     * @param preferredType
     *            the preferred primitive type
     * @return the primitive value
     */
    public static Object ToPrimitive(ExecutionContext cx, Object argument,
            ToPrimitiveHint preferredType) {
        if (!Type.isObject(argument)) {
            return argument;
        }
        return ToPrimitive(cx, Type.objectValue(argument), preferredType);
    }

    /**
     * 7.1.1 ToPrimitive ( input [, PreferredType] )
     * <p>
     * ToPrimitive for the Object type
     * 
     * @param cx
     *            the execution context
     * @param argument
     *            the argument value
     * @param preferredType
     *            the preferred primitive type
     * @return the primitive value
     */
    private static Object ToPrimitive(ExecutionContext cx, ScriptObject argument,
            ToPrimitiveHint preferredType) {
        /* steps 1-3 */
        String hint = preferredType.toString();
        /* steps 4-5 */
        Callable exoticToPrim = GetMethod(cx, argument, BuiltinSymbol.toPrimitive.get());
        /* step 6 */
        if (exoticToPrim != null) {
            Object result = exoticToPrim.call(cx, argument, hint);
            if (!Type.isObject(result)) {
                return result;
            }
            throw newTypeError(cx, Messages.Key.NotPrimitiveType);
        }
        /* step 7 */
        if (preferredType == ToPrimitiveHint.Default) {
            preferredType = ToPrimitiveHint.Number;
        }
        /* step 8 */
        return OrdinaryToPrimitive(cx, argument, preferredType);
    }

    /**
     * 7.1.1 ToPrimitive ( input [, PreferredType] )
     * <p>
     * OrdinaryToPrimitive
     * 
     * @param cx
     *            the execution context
     * @param object
     *            the argument object
     * @param hint
     *            the preferred primitive type
     * @return the primitive value
     */
    public static Object OrdinaryToPrimitive(ExecutionContext cx, ScriptObject object,
            ToPrimitiveHint hint) {
        /* steps 1-2 */
        assert hint == ToPrimitiveHint.String || hint == ToPrimitiveHint.Number;
        /* steps 3-4 */
        String tryFirst, trySecond;
        if (hint == ToPrimitiveHint.String) {
            tryFirst = "toString";
            trySecond = "valueOf";
        } else {
            tryFirst = "valueOf";
            trySecond = "toString";
        }
        /* step 5 (first try) */
        Object first = Get(cx, object, tryFirst);
        if (IsCallable(first)) {
            Object result = ((Callable) first).call(cx, object);
            if (!Type.isObject(result)) {
                return result;
            }
        }
        /* step 5 (second try) */
        Object second = Get(cx, object, trySecond);
        if (IsCallable(second)) {
            Object result = ((Callable) second).call(cx, object);
            if (!Type.isObject(result)) {
                return result;
            }
        }
        /* step 6 */
        throw newTypeError(cx, Messages.Key.NoPrimitiveRepresentation);
    }

    /**
     * 7.1.2 ToBoolean ( argument )
     * 
     * @param value
     *            the argument value
     * @return the boolean result
     */
    public static boolean ToBoolean(Object value) {
        switch (Type.of(value)) {
        case Undefined:
            return false;
        case Null:
            return false;
        case Boolean:
            return Type.booleanValue(value);
        case Number:
            double d = Type.numberValue(value);
            return !(d == 0 || Double.isNaN(d));
        case String:
            return Type.stringValue(value).length() != 0;
        case Symbol:
            return true;
        case Object:
        default:
            return true;
        }
    }

    /**
     * 7.1.2 ToBoolean ( argument )
     * 
     * @param value
     *            the argument value
     * @return the boolean result
     */
    public static boolean ToBoolean(double value) {
        return !(value == 0 || Double.isNaN(value));
    }

    /**
     * 7.1.3 ToNumber ( argument )
     * 
     * @param cx
     *            the execution context
     * @param value
     *            the argument value
     * @return the number result
     */
    public static double ToNumber(ExecutionContext cx, Object value) {
        switch (Type.of(value)) {
        case Undefined:
            return Double.NaN;
        case Null:
            return +0;
        case Boolean:
            return Type.booleanValue(value) ? 1 : +0;
        case Number:
            return Type.numberValue(value);
        case String:
            return ToNumber(Type.stringValue(value));
        case Symbol:
            throw newTypeError(cx, Messages.Key.SymbolNumber);
        case Object:
        default:
            Object primValue = ToPrimitive(cx, value, ToPrimitiveHint.Number);
            return ToNumber(cx, primValue);
        }
    }

    /**
     * 7.1.3.1 ToNumber Applied to the String Type
     * 
     * @param string
     *            the argument value
     * @return the number result
     */
    public static double ToNumber(CharSequence string) {
        return ToNumberParser.parse(string.toString());
    }

    /**
     * 7.1.4 ToInteger ( argument )
     * 
     * @param cx
     *            the execution context
     * @param value
     *            the argument value
     * @return the integer result
     */
    public static double ToInteger(ExecutionContext cx, Object value) {
        /* steps 1-2 */
        double number = ToNumber(cx, value);
        /* step 3 */
        if (Double.isNaN(number))
            return +0.0;
        /* step 4 */
        if (number == 0.0 || Double.isInfinite(number))
            return number;
        /* step 5 */
        return Math.signum(number) * Math.floor(Math.abs(number));
    }

    /**
     * 7.1.4 ToInteger ( argument )
     * 
     * @param number
     *            the argument value
     * @return the integer result
     */
    public static double ToInteger(double number) {
        /* steps 1-2 (not applicable) */
        /* step 3 */
        if (Double.isNaN(number))
            return +0.0;
        /* step 4 */
        if (number == 0.0 || Double.isInfinite(number))
            return number;
        /* step 5 */
        return Math.signum(number) * Math.floor(Math.abs(number));
    }

    /**
     * 7.1.5 ToInt32 ( argument ) — Signed 32 Bit Integer
     * 
     * @param cx
     *            the execution context
     * @param value
     *            the argument value
     * @return the integer result
     */
    public static int ToInt32(ExecutionContext cx, Object value) {
        /* steps 1-2 */
        double number = ToNumber(cx, value);
        /* steps 3-6 */
        return DoubleConversion.doubleToInt32(number);
    }

    /**
     * 7.1.5 ToInt32 ( argument ) — Signed 32 Bit Integer
     * 
     * @param number
     *            the argument value
     * @return the integer result
     */
    public static int ToInt32(double number) {
        /* steps 1-2 (not applicable) */
        /* steps 3-6 */
        return DoubleConversion.doubleToInt32(number);
    }

    /**
     * 7.1.6 ToUint32 ( argument ) — Unsigned 32 Bit Integer
     * 
     * @param cx
     *            the execution context
     * @param value
     *            the argument value
     * @return the integer result
     */
    public static long ToUint32(ExecutionContext cx, Object value) {
        /* steps 1-2 */
        double number = ToNumber(cx, value);
        /* steps 3-6 */
        return DoubleConversion.doubleToInt32(number) & 0xffffffffL;
    }

    /**
     * 7.1.6 ToUint32 ( argument ) — Unsigned 32 Bit Integer
     * 
     * @param number
     *            the argument value
     * @return the integer result
     */
    public static long ToUint32(double number) {
        /* steps 1-2 (not applicable) */
        /* steps 3-6 */
        return DoubleConversion.doubleToInt32(number) & 0xffffffffL;
    }

    /**
     * 7.1.7 ToInt16 ( argument ) — Signed 16 Bit Integer
     * 
     * @param cx
     *            the execution context
     * @param value
     *            the argument value
     * @return the integer result
     */
    public static short ToInt16(ExecutionContext cx, Object value) {
        /* steps 1-2 */
        double number = ToNumber(cx, value);
        /* steps 3-6 */
        return (short) DoubleConversion.doubleToInt32(number);
    }

    /**
     * 7.1.7 ToInt16 ( argument ) — Signed 16 Bit Integer
     * 
     * @param number
     *            the argument value
     * @return the integer result
     */
    public static short ToInt16(double number) {
        /* steps 1-2 (not applicable) */
        /* steps 3-6 */
        return (short) DoubleConversion.doubleToInt32(number);
    }

    /**
     * 7.1.8 ToUint16 ( argument ) — Unsigned 16 Bit Integer
     * 
     * @param cx
     *            the execution context
     * @param value
     *            the argument value
     * @return the integer result
     */
    public static char ToUint16(ExecutionContext cx, Object value) {
        /* steps 1-2 */
        double number = ToNumber(cx, value);
        /* steps 3-6 */
        return (char) DoubleConversion.doubleToInt32(number);
    }

    /**
     * 7.1.8 ToUint16 ( argument ) — Unsigned 16 Bit Integer
     * 
     * @param number
     *            the argument value
     * @return the integer result
     */
    public static char ToUint16(double number) {
        /* steps 1-2 (not applicable) */
        /* steps 3-6 */
        return (char) DoubleConversion.doubleToInt32(number);
    }

    /**
     * 7.1.9 ToInt8 ( argument ) — Signed 8 Bit Integer
     * 
     * @param cx
     *            the execution context
     * @param value
     *            the argument value
     * @return the integer result
     */
    public static byte ToInt8(ExecutionContext cx, Object value) {
        /* steps 1-2 */
        double number = ToNumber(cx, value);
        /* steps 3-6 */
        return (byte) DoubleConversion.doubleToInt32(number);
    }

    /**
     * 7.1.9 ToInt8 ( argument ) — Signed 8 Bit Integer
     * 
     * @param number
     *            the argument value
     * @return the integer result
     */
    public static byte ToInt8(double number) {
        /* steps 1-2 (not applicable) */
        /* steps 3-6 */
        return (byte) DoubleConversion.doubleToInt32(number);
    }

    /**
     * 7.1.10 ToUint8 ( argument ) — Unsigned 8 Bit Integer
     * 
     * @param cx
     *            the execution context
     * @param value
     *            the argument value
     * @return the integer result
     */
    public static int ToUint8(ExecutionContext cx, Object value) {
        /* steps 1-2 */
        double number = ToNumber(cx, value);
        /* steps 3-6 */
        return DoubleConversion.doubleToInt32(number) & 0xFF;
    }

    /**
     * 7.1.10 ToUint8 ( argument ) — Unsigned 8 Bit Integer
     * 
     * @param number
     *            the argument value
     * @return the integer result
     */
    public static int ToUint8(double number) {
        /* steps 1-2 (not applicable) */
        /* steps 3-6 */
        return DoubleConversion.doubleToInt32(number) & 0xFF;
    }

    /**
     * 7.1.11 ToUint8Clamp ( argument ) — Unsigned 8 Bit Integer, Clamped
     * 
     * @param cx
     *            the execution context
     * @param value
     *            the argument value
     * @return the integer result
     */
    public static int ToUint8Clamp(ExecutionContext cx, Object value) {
        /* steps 1-2 */
        double number = ToNumber(cx, value);
        /* steps 3-8 */
        return number <= 0 ? +0 : number > 255 ? 255 : (int) Math.rint(number);
    }

    /**
     * 7.1.11 ToUint8Clamp ( argument ) — Unsigned 8 Bit Integer, Clamped
     * 
     * @param number
     *            the argument value
     * @return the integer result
     */
    public static int ToUint8Clamp(double number) {
        /* steps 1-2 (not applicable) */
        /* steps 3-8 */
        return number <= 0 ? +0 : number > 255 ? 255 : (int) Math.rint(number);
    }

    /**
     * 7.1.12 ToString ( argument )
     * 
     * @param cx
     *            the execution context
     * @param value
     *            the argument value
     * @return the string result
     */
    public static String ToFlatString(ExecutionContext cx, Object value) {
        return ToString(cx, value).toString();
    }

    /**
     * 7.1.12 ToString ( argument )
     * 
     * @param cx
     *            the execution context
     * @param value
     *            the argument value
     * @return the string result
     */
    public static CharSequence ToString(ExecutionContext cx, Object value) {
        switch (Type.of(value)) {
        case Undefined:
            return "undefined";
        case Null:
            return "null";
        case Boolean:
            return Type.booleanValue(value) ? "true" : "false";
        case Number:
            return ToString(Type.numberValue(value));
        case String:
            return Type.stringValue(value);
        case Symbol:
            throw newTypeError(cx, Messages.Key.SymbolString);
        case Object:
        default:
            Object primValue = ToPrimitive(cx, value, ToPrimitiveHint.String);
            return ToString(cx, primValue);
        }
    }

    /**
     * 7.1.12.1 ToString Applied to the Number Type
     * 
     * @param value
     *            the argument value
     * @return the string result
     */
    public static String ToString(int value) {
        return Integer.toString(value);
    }

    /**
     * 7.1.12.1 ToString Applied to the Number Type
     * 
     * @param value
     *            the argument value
     * @return the string result
     */
    public static String ToString(long value) {
        if ((int) value == value) {
            return Integer.toString((int) value);
        } else if (-0x1F_FFFF_FFFF_FFFFL <= value && value <= 0x1F_FFFF_FFFF_FFFFL) {
            return Long.toString(value);
        }
        return ToString((double) value);
    }

    /**
     * 7.1.12.1 ToString Applied to the Number Type
     * 
     * @param value
     *            the argument value
     * @return the string result
     */
    public static String ToString(double value) {
        /* steps 1-4 (+ shortcut for integer values) */
        if ((int) value == value) {
            return Integer.toString((int) value);
        } else if (value != value) {
            return "NaN";
        } else if (value == Double.POSITIVE_INFINITY) {
            return "Infinity";
        } else if (value == Double.NEGATIVE_INFINITY) {
            return "-Infinity";
        } else if (value == 0.0) {
            return "0";
        }

        // call DToA for general number-to-string
        String result = FastDtoa.numberToString(value);
        if (result != null) {
            return result;
        }
        StringBuilder buffer = new StringBuilder();
        DToA.JS_dtostr(buffer, DToA.DTOSTR_STANDARD, 0, value);
        return buffer.toString();
    }

    /**
     * 7.1.13 ToObject ( argument )
     * 
     * @param cx
     *            the execution context
     * @param value
     *            the argument value
     * @return the object result
     */
    public static ScriptObject ToObject(ExecutionContext cx, Object value) {
        switch (Type.of(value)) {
        case Undefined:
        case Null:
            throw newTypeError(cx, Messages.Key.UndefinedOrNull);
        case Boolean:
            return BooleanCreate(cx, Type.booleanValue(value));
        case Number:
            return NumberCreate(cx, Type.numberValue(value));
        case String:
            return StringCreate(cx, Type.stringValue(value));
        case Symbol:
            return SymbolCreate(cx, Type.symbolValue(value));
        case Object:
        default:
            return Type.objectValue(value);
        }
    }

    /**
     * 7.1.14 ToPropertyKey ( argument )
     * 
     * @param cx
     *            the execution context
     * @param value
     *            the argument value
     * @return the property key
     */
    public static Object ToPropertyKey(ExecutionContext cx, Object value) {
        /* steps 1-2 */
        Object key = ToPrimitive(cx, value, ToPrimitiveHint.String);
        /* step 3 */
        if (key instanceof Symbol) {
            return key;
        }
        /* step 4 */
        return ToFlatString(cx, key);
    }

    /**
     * 7.1.15 ToLength ( argument )
     * 
     * @param cx
     *            the execution context
     * @param value
     *            the argument value
     * @return the length value
     */
    public static long ToLength(ExecutionContext cx, Object value) {
        /* steps 1-2 */
        double len = ToInteger(cx, value);
        /* step 3 */
        if (len <= 0) {
            return 0;
        }
        /* step 4 */
        return (long) Math.min(len, 0x1F_FFFF_FFFF_FFFFL);
    }

    /**
     * 7.1.15 ToLength ( argument )
     * 
     * @param value
     *            the argument value
     * @return the length value
     */
    public static long ToLength(double value) {
        /* steps 1-2 (not applicable) */
        /* step 3 */
        if (value <= 0) {
            return 0;
        }
        /* step 4 */
        return (long) Math.min(value, 0x1F_FFFF_FFFF_FFFFL);
    }

    /**
     * 7.1.16 CanonicalNumericIndexString ( argument )
     * 
     * @param value
     *            the argument value
     * @return the canonical number or -1 if not canonical
     */
    public static long CanonicalNumericIndexString(String value) {
        /* step 1 (not applicable) */
        /* step 2 */
        if ("-0".equals(value)) {
            return Long.MAX_VALUE;
        }
        /* step 3 */
        double n = ToNumber(value);
        // FIXME: spec issue https://bugs.ecmascript.org/show_bug.cgi?id=2049
        // FIXME: not resolved in rev26 - reopen bug report!
        /* step 4 */
        if (!value.equals(ToString(n))) {
            return -1;
        }
        // Directly perform IsInteger() check and encode negative and non-integer indices as OOB.
        if (n < 0 || !IsInteger(n)) {
            return Long.MAX_VALUE;
        }
        /* step 5 */
        return (long) n;
    }

    /**
     * 7.2.1 RequireObjectCoercible
     * 
     * @param cx
     *            the execution context
     * @param value
     *            the argument value
     * @return the input argument unless it is either <code>undefined</code> or <code>null</code>
     */
    public static Object RequireObjectCoercible(ExecutionContext cx, Object value) {
        if (Type.isUndefinedOrNull(value)) {
            throw newTypeError(cx, Messages.Key.UndefinedOrNull);
        }
        return value;
    }

    /**
     * 7.2.2 IsCallable
     * 
     * @param value
     *            the argument value
     * @return {@code true} if the value is a callable object
     */
    public static boolean IsCallable(Object value) {
        return value instanceof Callable;
    }

    /**
     * 7.2.3 SameValue(x, y)
     * 
     * @param x
     *            the first operand
     * @param y
     *            the second operand
     * @return {@code true} if both operands have the same value
     */
    public static boolean SameValue(Object x, Object y) {
        /* same reference shortcuts */
        if (x == y) {
            return true;
        } else if (x == null || y == null) {
            return false;
        }
        Type tx = Type.of(x);
        Type ty = Type.of(y);
        /* steps 1-2 (not applicable) */
        /* step 3 */
        if (tx != ty) {
            return false;
        }
        /* step 4 */
        if (tx == Type.Undefined) {
            return true;
        }
        /* step 5 */
        if (tx == Type.Null) {
            return true;
        }
        /* step 6 */
        if (tx == Type.Number) {
            double dx = Type.numberValue(x);
            double dy = Type.numberValue(y);
            return Double.compare(dx, dy) == 0;
        }
        /* step 7 */
        if (tx == Type.String) {
            CharSequence sx = Type.stringValue(x);
            CharSequence sy = Type.stringValue(y);
            return sx.length() == sy.length() && sx.toString().equals(sy.toString());
        }
        /* step 8 */
        if (tx == Type.Boolean) {
            return Type.booleanValue(x) == Type.booleanValue(y);
        }
        /* steps 9-10 */
        assert tx == Type.Object || tx == Type.Symbol;
        return (x == y);
    }

    /**
     * 7.2.3 SameValue(x, y)
     * 
     * @param x
     *            the first operand
     * @param y
     *            the second operand
     * @return {@code true} if both operands have the same value
     */
    public static boolean SameValue(double x, double y) {
        /* steps 1-5, 7-10 (not applicable) */
        /* step 6 */
        return Double.compare(x, y) == 0;
    }

    /**
     * 7.2.3 SameValue(x, y)
     * 
     * @param x
     *            the first operand
     * @param y
     *            the second operand
     * @return {@code true} if both operands have the same value
     */
    public static boolean SameValue(ScriptObject x, ScriptObject y) {
        /* steps 1-9 (not applicable) */
        /* step 10 */
        return (x == y);
    }

    /**
     * 7.2.4 SameValueZero(x, y)
     * 
     * @param x
     *            the first operand
     * @param y
     *            the second operand
     * @return {@code true} if both operands have the same value
     */
    public static boolean SameValueZero(Object x, Object y) {
        /* same reference shortcuts */
        if (x == y) {
            return true;
        } else if (x == null || y == null) {
            return false;
        }
        Type tx = Type.of(x);
        Type ty = Type.of(y);
        /* steps 1-2 (not applicable) */
        /* step 3 */
        if (tx != ty) {
            return false;
        }
        /* step 4 */
        if (tx == Type.Undefined) {
            return true;
        }
        /* step 5 */
        if (tx == Type.Null) {
            return true;
        }
        /* step 6 */
        if (tx == Type.Number) {
            double dx = Type.numberValue(x);
            double dy = Type.numberValue(y);
            if (dx == 0) {
                return (dy == 0);
            }
            return Double.compare(dx, dy) == 0;
        }
        /* step 7 */
        if (tx == Type.String) {
            CharSequence sx = Type.stringValue(x);
            CharSequence sy = Type.stringValue(y);
            return sx.length() == sy.length() && sx.toString().equals(sy.toString());
        }
        /* step 8 */
        if (tx == Type.Boolean) {
            return Type.booleanValue(x) == Type.booleanValue(y);
        }
        /* steps 9-10 */
        assert tx == Type.Object || tx == Type.Symbol;
        return (x == y);
    }

    /**
     * 7.2.4 SameValueZero(x, y)
     * 
     * @param x
     *            the first operand
     * @param y
     *            the second operand
     * @return {@code true} if both operands have the same value
     */
    public static boolean SameValueZero(double x, double y) {
        /* steps 1-5 (not applicable) */
        /* steps 7-10 (not applicable) */
        /* step 6 */
        double dx = Type.numberValue(x);
        double dy = Type.numberValue(y);
        if (dx == 0) {
            return (dy == 0);
        }
        return Double.compare(dx, dy) == 0;
    }

    /**
     * 7.2.5 IsConstructor
     * 
     * @param value
     *            the argument value
     * @return {@code true} if the value is a constructor object
     */
    public static boolean IsConstructor(Object value) {
        /* steps 1-4 */
        return value instanceof Constructor;
    }

    /**
     * 7.2.6 IsPropertyKey
     * 
     * @param value
     *            the argument value
     * @return {@code true} if the value is a property key
     */
    public static boolean IsPropertyKey(Object value) {
        /* steps 1-4 */
        return value instanceof String || value instanceof Symbol;
    }

    /**
     * 7.2.7 IsExtensible (O)
     * 
     * @param cx
     *            the execution context
     * @param object
     *            the script object
     * @return {@code true} if the object is extensible
     */
    public static boolean IsExtensible(ExecutionContext cx, ScriptObject object) {
        /* steps 1-2 */
        return object.isExtensible(cx);
    }

    /**
     * 7.2.8 IsInteger
     * 
     * @param value
     *            the argument value
     * @return {@code true} if the value is a finite integer
     */
    public static boolean IsInteger(Object value) {
        /* steps 1-2 */
        if (!Type.isNumber(value)) {
            return false;
        }
        double d = Type.numberValue(value);
        /* step 2 */
        if (Double.isNaN(d) || Double.isInfinite(d)) {
            return false;
        }
        /* step 3 */
        if (Math.floor(Math.abs(d)) != Math.abs(d)) {
            return false;
        }
        /* step 4 */
        return true;
    }

    /**
     * 7.2.8 IsInteger
     * 
     * @param value
     *            the argument value
     * @return {@code true} if the value is a finite integer
     */
    public static boolean IsInteger(double value) {
        double d = value;
        /* step 2 */
        if (Double.isNaN(d) || Double.isInfinite(d)) {
            return false;
        }
        /* step 3 */
        if (Math.floor(Math.abs(d)) != Math.abs(d)) {
            return false;
        }
        /* step 4 */
        return true;
    }

    /**
     * 7.2.9 Abstract Relational Comparison
     * 
     * @param cx
     *            the execution context
     * @param x
     *            the first operand
     * @param y
     *            the second operand
     * @param leftFirst
     *            the operation order flag
     * @return the comparison result
     */
    public static int RelationalComparison(ExecutionContext cx, Object x, Object y,
            boolean leftFirst) {
        // true -> 1
        // false -> 0
        // undefined -> -1
        /* steps 1-2 (not applicable) */
        /* steps 3-4 */
        Object px, py;
        if (leftFirst) {
            px = ToPrimitive(cx, x, ToPrimitiveHint.Number);
            py = ToPrimitive(cx, y, ToPrimitiveHint.Number);
        } else {
            py = ToPrimitive(cx, y, ToPrimitiveHint.Number);
            px = ToPrimitive(cx, x, ToPrimitiveHint.Number);
        }
        /* step 5 */
        if (Type.isString(px) && Type.isString(py)) {
            int c = Type.stringValue(px).toString().compareTo(Type.stringValue(py).toString());
            return c < 0 ? 1 : 0;
        }
        /* step 6 */
        double nx = ToNumber(cx, px);
        double ny = ToNumber(cx, py);
        if (Double.isNaN(nx) || Double.isNaN(ny)) {
            return -1;
        }
        if (nx == ny) {
            return 0;
        }
        if (nx == Double.POSITIVE_INFINITY) {
            return 0;
        }
        if (ny == Double.POSITIVE_INFINITY) {
            return 1;
        }
        if (ny == Double.NEGATIVE_INFINITY) {
            return 0;
        }
        if (nx == Double.NEGATIVE_INFINITY) {
            return 1;
        }
        return nx < ny ? 1 : 0;

    }

    /**
     * 7.2.10 Abstract Equality Comparison
     * 
     * @param cx
     *            the execution context
     * @param x
     *            the first operand
     * @param y
     *            the second operand
     * @return the comparison result
     */
    public static boolean EqualityComparison(ExecutionContext cx, Object x, Object y) {
        // fast path for same reference
        if (x == y) {
            if (x instanceof Double) {
                return !((Double) x).isNaN();
            }
            return true;
        }
        /* steps 1-2 (not applicable) */
        Type tx = Type.of(x);
        Type ty = Type.of(y);
        /* step 3 */
        if (tx == ty) {
            return StrictEqualityComparison(x, y);
        }
        /* step 4 */
        if (tx == Type.Null && ty == Type.Undefined) {
            return true;
        }
        /* step 5 */
        if (tx == Type.Undefined && ty == Type.Null) {
            return true;
        }
        /* step 6 */
        if (tx == Type.Number && ty == Type.String) {
            // return EqualityComparison(cx, x, ToNumber(cx, y));
            return Type.numberValue(x) == ToNumber(cx, y);
        }
        /* step 7 */
        if (tx == Type.String && ty == Type.Number) {
            // return EqualityComparison(cx, ToNumber(cx, x), y);
            return ToNumber(cx, x) == Type.numberValue(y);
        }
        /* step 8 */
        if (tx == Type.Boolean) {
            return EqualityComparison(cx, ToNumber(cx, x), y);
        }
        /* step 9 */
        if (ty == Type.Boolean) {
            return EqualityComparison(cx, x, ToNumber(cx, y));
        }
        /* step 10 */
        if ((tx == Type.String || tx == Type.Number || tx == Type.Symbol) && ty == Type.Object) {
            return EqualityComparison(cx, x, ToPrimitive(cx, y));
        }
        /* step 11 */
        if (tx == Type.Object && (ty == Type.String || ty == Type.Number || ty == Type.Symbol)) {
            return EqualityComparison(cx, ToPrimitive(cx, x), y);
        }
        /* step 12 */
        return false;
    }

    /**
     * 7.2.11 Strict Equality Comparison
     * 
     * @param x
     *            the first operand
     * @param y
     *            the second operand
     * @return the comparison result
     */
    public static boolean StrictEqualityComparison(Object x, Object y) {
        // fast path for same reference
        if (x == y) {
            if (x instanceof Double) {
                return !((Double) x).isNaN();
            }
            return true;
        }
        Type tx = Type.of(x);
        Type ty = Type.of(y);
        /* step 1 */
        if (tx != ty) {
            return false;
        }
        /* step 2 */
        if (tx == Type.Undefined) {
            return true;
        }
        /* step 3 */
        if (tx == Type.Null) {
            return true;
        }
        /* step 4 */
        if (tx == Type.Number) {
            return Type.numberValue(x) == Type.numberValue(y);
        }
        /* step 5 */
        if (tx == Type.String) {
            CharSequence sx = Type.stringValue(x);
            CharSequence sy = Type.stringValue(y);
            return sx.length() == sy.length() && sx.toString().equals(sy.toString());
        }
        /* step 6 */
        if (tx == Type.Boolean) {
            return Type.booleanValue(x) == Type.booleanValue(y);
        }
        assert tx == Type.Object || tx == Type.Symbol;
        /* steps 7-9 */
        return (x == y);
    }

    /**
     * 7.3.1 Get (O, P)
     * 
     * @param cx
     *            the execution context
     * @param object
     *            the script object
     * @param propertyKey
     *            the property key
     * @return the property value
     */
    public static Object Get(ExecutionContext cx, ScriptObject object, Object propertyKey) {
        /* steps 1-3 */
        if (propertyKey instanceof String) {
            return Get(cx, object, (String) propertyKey);
        } else {
            return Get(cx, object, (Symbol) propertyKey);
        }
    }

    /**
     * 7.3.1 Get (O, P)
     * 
     * @param cx
     *            the execution context
     * @param object
     *            the script object
     * @param propertyKey
     *            the property key
     * @return the property value
     */
    public static Object Get(ExecutionContext cx, ScriptObject object, long propertyKey) {
        /* steps 1-3 */
        return object.get(cx, propertyKey, object);
    }

    /**
     * 7.3.1 Get (O, P)
     * 
     * @param cx
     *            the execution context
     * @param object
     *            the script object
     * @param propertyKey
     *            the property key
     * @return the property value
     */
    public static Object Get(ExecutionContext cx, ScriptObject object, String propertyKey) {
        /* steps 1-3 */
        return object.get(cx, propertyKey, object);
    }

    /**
     * 7.3.1 Get (O, P)
     * 
     * @param cx
     *            the execution context
     * @param object
     *            the script object
     * @param propertyKey
     *            the property key
     * @return the property value
     */
    public static Object Get(ExecutionContext cx, ScriptObject object, Symbol propertyKey) {
        /* steps 1-3 */
        return object.get(cx, propertyKey, object);
    }

    /**
     * 7.3.2 Put (O, P, V, Throw)
     * 
     * @param cx
     *            the execution context
     * @param object
     *            the script object
     * @param propertyKey
     *            the property key
     * @param value
     *            the new property value
     * @param _throw
     *            the throw flag
     */
    public static void Put(ExecutionContext cx, ScriptObject object, Object propertyKey,
            Object value, boolean _throw) {
        /* steps 1-7 */
        if (propertyKey instanceof String) {
            Put(cx, object, (String) propertyKey, value, _throw);
        } else {
            Put(cx, object, (Symbol) propertyKey, value, _throw);
        }
    }

    /**
     * 7.3.2 Put (O, P, V, Throw)
     * 
     * @param cx
     *            the execution context
     * @param object
     *            the script object
     * @param propertyKey
     *            the property key
     * @param value
     *            the new property value
     * @param _throw
     *            the throw flag
     */
    public static void Put(ExecutionContext cx, ScriptObject object, long propertyKey,
            Object value, boolean _throw) {
        /* steps 1-5 */
        boolean success = object.set(cx, propertyKey, value, object);
        /* step 6 */
        if (!success && _throw) {
            throw newTypeError(cx, Messages.Key.PropertyNotModifiable, ToString(propertyKey));
        }
        /* step 7 (not applicable) */
    }

    /**
     * 7.3.2 Put (O, P, V, Throw)
     * 
     * @param cx
     *            the execution context
     * @param object
     *            the script object
     * @param propertyKey
     *            the property key
     * @param value
     *            the new property value
     * @param _throw
     *            the throw flag
     */
    public static void Put(ExecutionContext cx, ScriptObject object, String propertyKey,
            Object value, boolean _throw) {
        /* steps 1-5 */
        boolean success = object.set(cx, propertyKey, value, object);
        /* step 6 */
        if (!success && _throw) {
            throw newTypeError(cx, Messages.Key.PropertyNotModifiable, propertyKey);
        }
        /* step 7 (not applicable) */
    }

    /**
     * 7.3.2 Put (O, P, V, Throw)
     * 
     * @param cx
     *            the execution context
     * @param object
     *            the script object
     * @param propertyKey
     *            the property key
     * @param value
     *            the new property value
     * @param _throw
     *            the throw flag
     */
    public static void Put(ExecutionContext cx, ScriptObject object, Symbol propertyKey,
            Object value, boolean _throw) {
        /* steps 1-5 */
        boolean success = object.set(cx, propertyKey, value, object);
        /* step 6 */
        if (!success && _throw) {
            throw newTypeError(cx, Messages.Key.PropertyNotModifiable, propertyKey.toString());
        }
        /* step 7 (not applicable) */
    }

    /**
     * 7.3.3 CreateDataProperty (O, P, V)
     * 
     * @param cx
     *            the execution context
     * @param object
     *            the script object
     * @param propertyKey
     *            the property key
     * @param value
     *            the new property value
     * @return {@code true} on success
     */
    public static boolean CreateDataProperty(ExecutionContext cx, ScriptObject object,
            Object propertyKey, Object value) {
        if (propertyKey instanceof String) {
            return CreateDataProperty(cx, object, (String) propertyKey, value);
        } else {
            return CreateDataProperty(cx, object, (Symbol) propertyKey, value);
        }
    }

    /**
     * 7.3.3 CreateDataProperty (O, P, V)
     * 
     * @param cx
     *            the execution context
     * @param object
     *            the script object
     * @param propertyKey
     *            the property key
     * @param value
     *            the new property value
     * @return {@code true} on success
     */
    public static boolean CreateDataProperty(ExecutionContext cx, ScriptObject object,
            long propertyKey, Object value) {
        /* steps 1-2 (not applicable) */
        /* step 3 */
        PropertyDescriptor newDesc = new PropertyDescriptor(value, true, true, true);
        /* step 4 */
        return object.defineOwnProperty(cx, propertyKey, newDesc);
    }

    /**
     * 7.3.3 CreateDataProperty (O, P, V)
     * 
     * @param cx
     *            the execution context
     * @param object
     *            the script object
     * @param propertyKey
     *            the property key
     * @param value
     *            the new property value
     * @return {@code true} on success
     */
    public static boolean CreateDataProperty(ExecutionContext cx, ScriptObject object,
            String propertyKey, Object value) {
        /* steps 1-2 (not applicable) */
        /* step 3 */
        PropertyDescriptor newDesc = new PropertyDescriptor(value, true, true, true);
        /* step 4 */
        return object.defineOwnProperty(cx, propertyKey, newDesc);
    }

    /**
     * 7.3.3 CreateDataProperty (O, P, V)
     * 
     * @param cx
     *            the execution context
     * @param object
     *            the script object
     * @param propertyKey
     *            the property key
     * @param value
     *            the new property value
     * @return {@code true} on success
     */
    public static boolean CreateDataProperty(ExecutionContext cx, ScriptObject object,
            Symbol propertyKey, Object value) {
        /* steps 1-2 (not applicable) */
        /* step 3 */
        PropertyDescriptor newDesc = new PropertyDescriptor(value, true, true, true);
        /* step 4 */
        return object.defineOwnProperty(cx, propertyKey, newDesc);
    }

    /**
     * 7.3.4 CreateDataPropertyOrThrow (O, P, V)
     * 
     * @param cx
     *            the execution context
     * @param object
     *            the script object
     * @param propertyKey
     *            the property key
     * @param value
     *            the new property value
     */
    public static void CreateDataPropertyOrThrow(ExecutionContext cx, ScriptObject object,
            Object propertyKey, Object value) {
        if (propertyKey instanceof String) {
            CreateDataPropertyOrThrow(cx, object, (String) propertyKey, value);
        } else {
            CreateDataPropertyOrThrow(cx, object, (Symbol) propertyKey, value);
        }
    }

    /**
     * 7.3.4 CreateDataPropertyOrThrow (O, P, V)
     * 
     * @param cx
     *            the execution context
     * @param object
     *            the script object
     * @param propertyKey
     *            the property key
     * @param value
     *            the new property value
     */
    public static void CreateDataPropertyOrThrow(ExecutionContext cx, ScriptObject object,
            long propertyKey, Object value) {
        /* steps 1-2 (not applicable) */
        /* steps 3-4 */
        boolean success = CreateDataProperty(cx, object, propertyKey, value);
        /* step 5 */
        if (!success) {
            throw newTypeError(cx, Messages.Key.PropertyNotCreatable, ToString(propertyKey));
        }
        /* step 6 */
    }

    /**
     * 7.3.4 CreateDataPropertyOrThrow (O, P, V)
     * 
     * @param cx
     *            the execution context
     * @param object
     *            the script object
     * @param propertyKey
     *            the property key
     * @param value
     *            the new property value
     */
    public static void CreateDataPropertyOrThrow(ExecutionContext cx, ScriptObject object,
            String propertyKey, Object value) {
        /* steps 1-2 (not applicable) */
        /* steps 3-4 */
        boolean success = CreateDataProperty(cx, object, propertyKey, value);
        /* step 5 */
        if (!success) {
            throw newTypeError(cx, Messages.Key.PropertyNotCreatable, propertyKey);
        }
        /* step 6 */
    }

    /**
     * 7.3.4 CreateDataPropertyOrThrow (O, P, V)
     * 
     * @param cx
     *            the execution context
     * @param object
     *            the script object
     * @param propertyKey
     *            the property key
     * @param value
     *            the new property value
     */
    public static void CreateDataPropertyOrThrow(ExecutionContext cx, ScriptObject object,
            Symbol propertyKey, Object value) {
        /* steps 1-2 (not applicable) */
        /* steps 3-4 */
        boolean success = CreateDataProperty(cx, object, propertyKey, value);
        /* step 5 */
        if (!success) {
            throw newTypeError(cx, Messages.Key.PropertyNotCreatable, propertyKey.toString());
        }
        /* step 6 */
    }

    /**
     * 7.3.5 DefinePropertyOrThrow (O, P, desc)
     * 
     * @param cx
     *            the execution context
     * @param object
     *            the script object
     * @param propertyKey
     *            the property key
     * @param desc
     *            the property descriptor
     */
    public static void DefinePropertyOrThrow(ExecutionContext cx, ScriptObject object,
            Object propertyKey, PropertyDescriptor desc) {
        if (propertyKey instanceof String) {
            DefinePropertyOrThrow(cx, object, (String) propertyKey, desc);
        } else {
            DefinePropertyOrThrow(cx, object, (Symbol) propertyKey, desc);
        }
    }

    /**
     * 7.3.5 DefinePropertyOrThrow (O, P, desc)
     * 
     * @param cx
     *            the execution context
     * @param object
     *            the script object
     * @param propertyKey
     *            the property key
     * @param desc
     *            the property descriptor
     */
    public static void DefinePropertyOrThrow(ExecutionContext cx, ScriptObject object,
            long propertyKey, PropertyDescriptor desc) {
        /* steps 1-4 */
        boolean success = object.defineOwnProperty(cx, propertyKey, desc);
        /* step 5 */
        if (!success) {
            throw newTypeError(cx, Messages.Key.PropertyNotCreatable, ToString(propertyKey));
        }
        /* step 6 (not applicable) */
    }

    /**
     * 7.3.5 DefinePropertyOrThrow (O, P, desc)
     * 
     * @param cx
     *            the execution context
     * @param object
     *            the script object
     * @param propertyKey
     *            the property key
     * @param desc
     *            the property descriptor
     */
    public static void DefinePropertyOrThrow(ExecutionContext cx, ScriptObject object,
            String propertyKey, PropertyDescriptor desc) {
        /* steps 1-4 */
        boolean success = object.defineOwnProperty(cx, propertyKey, desc);
        /* step 5 */
        if (!success) {
            throw newTypeError(cx, Messages.Key.PropertyNotCreatable, propertyKey);
        }
        /* step 6 (not applicable) */
    }

    /**
     * 7.3.5 DefinePropertyOrThrow (O, P, desc)
     * 
     * @param cx
     *            the execution context
     * @param object
     *            the script object
     * @param propertyKey
     *            the property key
     * @param desc
     *            the property descriptor
     */
    public static void DefinePropertyOrThrow(ExecutionContext cx, ScriptObject object,
            Symbol propertyKey, PropertyDescriptor desc) {
        /* steps 1-4 */
        boolean success = object.defineOwnProperty(cx, propertyKey, desc);
        /* step 5 */
        if (!success) {
            throw newTypeError(cx, Messages.Key.PropertyNotCreatable, propertyKey.toString());
        }
        /* step 6 (not applicable) */
    }

    /**
     * 7.3.6 DeletePropertyOrThrow (O, P)
     * 
     * @param cx
     *            the execution context
     * @param object
     *            the script object
     * @param propertyKey
     *            the property key
     */
    public static void DeletePropertyOrThrow(ExecutionContext cx, ScriptObject object,
            Object propertyKey) {
        if (propertyKey instanceof String) {
            DeletePropertyOrThrow(cx, object, (String) propertyKey);
        } else {
            DeletePropertyOrThrow(cx, object, (Symbol) propertyKey);
        }
    }

    /**
     * 7.3.6 DeletePropertyOrThrow (O, P)
     * 
     * @param cx
     *            the execution context
     * @param object
     *            the script object
     * @param propertyKey
     *            the property key
     */
    public static void DeletePropertyOrThrow(ExecutionContext cx, ScriptObject object,
            long propertyKey) {
        /* steps 1-4 */
        boolean success = object.delete(cx, propertyKey);
        /* step 5 */
        if (!success) {
            throw newTypeError(cx, Messages.Key.PropertyNotDeletable, ToString(propertyKey));
        }
        /* step 6 (not applicable) */
    }

    /**
     * 7.3.6 DeletePropertyOrThrow (O, P)
     * 
     * @param cx
     *            the execution context
     * @param object
     *            the script object
     * @param propertyKey
     *            the property key
     */
    public static void DeletePropertyOrThrow(ExecutionContext cx, ScriptObject object,
            String propertyKey) {
        /* steps 1-4 */
        boolean success = object.delete(cx, propertyKey);
        /* step 5 */
        if (!success) {
            throw newTypeError(cx, Messages.Key.PropertyNotDeletable, propertyKey);
        }
        /* step 6 (not applicable) */
    }

    /**
     * 7.3.6 DeletePropertyOrThrow (O, P)
     * 
     * @param cx
     *            the execution context
     * @param object
     *            the script object
     * @param propertyKey
     *            the property key
     */
    public static void DeletePropertyOrThrow(ExecutionContext cx, ScriptObject object,
            Symbol propertyKey) {
        /* steps 1-4 */
        boolean success = object.delete(cx, propertyKey);
        /* step 5 */
        if (!success) {
            throw newTypeError(cx, Messages.Key.PropertyNotDeletable, propertyKey.toString());
        }
        /* step 6 (not applicable) */
    }

    /**
     * 7.3.7 HasProperty (O, P)
     * 
     * @param cx
     *            the execution context
     * @param object
     *            the script object
     * @param propertyKey
     *            the property key
     * @return {@code true} if the property is present
     */
    public static boolean HasProperty(ExecutionContext cx, ScriptObject object, Object propertyKey) {
        if (propertyKey instanceof String) {
            return HasProperty(cx, object, (String) propertyKey);
        } else {
            return HasProperty(cx, object, (Symbol) propertyKey);
        }
    }

    /**
     * 7.3.7 HasProperty (O, P)
     * 
     * @param cx
     *            the execution context
     * @param object
     *            the script object
     * @param propertyKey
     *            the property key
     * @return {@code true} if the property is present
     */
    public static boolean HasProperty(ExecutionContext cx, ScriptObject object, long propertyKey) {
        /* steps 1-3 */
        return object.hasProperty(cx, propertyKey);
    }

    /**
     * 7.3.7 HasProperty (O, P)
     * 
     * @param cx
     *            the execution context
     * @param object
     *            the script object
     * @param propertyKey
     *            the property key
     * @return {@code true} if the property is present
     */
    public static boolean HasProperty(ExecutionContext cx, ScriptObject object, String propertyKey) {
        /* steps 1-3 */
        return object.hasProperty(cx, propertyKey);
    }

    /**
     * 7.3.7 HasProperty (O, P)
     * 
     * @param cx
     *            the execution context
     * @param object
     *            the script object
     * @param propertyKey
     *            the property key
     * @return {@code true} if the property is present
     */
    public static boolean HasProperty(ExecutionContext cx, ScriptObject object, Symbol propertyKey) {
        /* steps 1-3 */
        return object.hasProperty(cx, propertyKey);
    }

    /**
     * 7.3.8 HasOwnProperty (O, P)
     * 
     * @param cx
     *            the execution context
     * @param object
     *            the script object
     * @param propertyKey
     *            the property key
     * @return {@code true} if the property is present
     */
    public static boolean HasOwnProperty(ExecutionContext cx, ScriptObject object,
            Object propertyKey) {
        if (propertyKey instanceof String) {
            return HasOwnProperty(cx, object, (String) propertyKey);
        } else {
            return HasOwnProperty(cx, object, (Symbol) propertyKey);
        }
    }

    /**
     * 7.3.8 HasOwnProperty (O, P)
     * 
     * @param cx
     *            the execution context
     * @param object
     *            the script object
     * @param propertyKey
     *            the property key
     * @return {@code true} if the property is present
     */
    public static boolean HasOwnProperty(ExecutionContext cx, ScriptObject object, long propertyKey) {
        /* steps 1-2 (not applicable) */
        /* steps 3-4 */
        Property desc = object.getOwnProperty(cx, propertyKey);
        /* steps 5-6 */
        return desc != null;
    }

    /**
     * 7.3.8 HasOwnProperty (O, P)
     * 
     * @param cx
     *            the execution context
     * @param object
     *            the script object
     * @param propertyKey
     *            the property key
     * @return {@code true} if the property is present
     */
    public static boolean HasOwnProperty(ExecutionContext cx, ScriptObject object,
            String propertyKey) {
        /* steps 1-2 (not applicable) */
        /* steps 3-4 */
        Property desc = object.getOwnProperty(cx, propertyKey);
        /* steps 5-6 */
        return desc != null;
    }

    /**
     * 7.3.8 HasOwnProperty (O, P)
     * 
     * @param cx
     *            the execution context
     * @param object
     *            the script object
     * @param propertyKey
     *            the property key
     * @return {@code true} if the property is present
     */
    public static boolean HasOwnProperty(ExecutionContext cx, ScriptObject object,
            Symbol propertyKey) {
        /* steps 1-2 (not applicable) */
        /* steps 3-4 */
        Property desc = object.getOwnProperty(cx, propertyKey);
        /* steps 5-6 */
        return desc != null;
    }

    /**
     * 7.3.9 GetMethod (O, P)
     * 
     * @param cx
     *            the execution context
     * @param object
     *            the script object
     * @param propertyKey
     *            the property key
     * @return the method or {@code null} if not present
     */
    public static Callable GetMethod(ExecutionContext cx, ScriptObject object, Object propertyKey) {
        if (propertyKey instanceof String) {
            return GetMethod(cx, object, (String) propertyKey);
        } else {
            return GetMethod(cx, object, (Symbol) propertyKey);
        }
    }

    /**
     * 7.3.9 GetMethod (O, P)
     * 
     * @param cx
     *            the execution context
     * @param object
     *            the script object
     * @param propertyKey
     *            the property key
     * @return the method or {@code null} if not present
     */
    public static Callable GetMethod(ExecutionContext cx, ScriptObject object, String propertyKey) {
        /* steps 1-4 */
        Object func = object.get(cx, propertyKey, object);
        /* step 5 */
        if (Type.isUndefinedOrNull(func)) {
            return null;
        }
        /* step 6 */
        if (!IsCallable(func)) {
            throw newTypeError(cx, Messages.Key.PropertyNotCallable, propertyKey);
        }
        /* step 7 */
        return (Callable) func;
    }

    /**
     * 7.3.9 GetMethod (O, P)
     * 
     * @param cx
     *            the execution context
     * @param object
     *            the script object
     * @param propertyKey
     *            the property key
     * @return the method or {@code null} if not present
     */
    public static Callable GetMethod(ExecutionContext cx, ScriptObject object, Symbol propertyKey) {
        /* steps 1-4 */
        Object func = object.get(cx, propertyKey, object);
        /* step 5 */
        if (Type.isUndefinedOrNull(func)) {
            return null;
        }
        /* step 6 */
        if (!IsCallable(func)) {
            throw newTypeError(cx, Messages.Key.PropertyNotCallable, propertyKey.toString());
        }
        /* step 7 */
        return (Callable) func;
    }

    /**
     * 7.3.10 Invoke(O,P [,args])
     * 
     * @param cx
     *            the execution context
     * @param object
     *            the script object
     * @param propertyKey
     *            the property key
     * @param args
     *            the method call arguments
     * @return the method return value
     */
    public static Object Invoke(ExecutionContext cx, Object object, Object propertyKey,
            Object... args) {
        if (propertyKey instanceof String) {
            return Invoke(cx, object, (String) propertyKey, args);
        } else {
            return Invoke(cx, object, (Symbol) propertyKey, args);
        }
    }

    /**
     * 7.3.10 Invoke(O,P [,args])
     * 
     * @param cx
     *            the execution context
     * @param object
     *            the script object
     * @param propertyKey
     *            the property key
     * @param args
     *            the method call arguments
     * @return the method return value
     */
    public static Object Invoke(ExecutionContext cx, Object object, String propertyKey,
            Object... args) {
        /* steps 1-2 (not applicable) */
        /* steps 3-4 */
        ScriptObject base = ToObject(cx, object);
        /* steps 5-6 */
        Object func = base.get(cx, propertyKey, object);
        /* step 7 */
        if (!IsCallable(func)) {
            throw newTypeError(cx, Messages.Key.PropertyNotCallable, propertyKey);
        }
        /* step 8 */
        return ((Callable) func).call(cx, object, args);
    }

    /**
     * 7.3.10 Invoke(O,P [,args])
     * 
     * @param cx
     *            the execution context
     * @param object
     *            the script object
     * @param propertyKey
     *            the property key
     * @param args
     *            the method call arguments
     * @return the method return value
     */
    public static Object Invoke(ExecutionContext cx, ScriptObject object, String propertyKey,
            Object... args) {
        /* steps 1-4 (not applicable) */
        /* steps 5-6 */
        Object func = object.get(cx, propertyKey, object);
        /* step 7 */
        if (!IsCallable(func)) {
            throw newTypeError(cx, Messages.Key.PropertyNotCallable, propertyKey);
        }
        /* step 8 */
        return ((Callable) func).call(cx, object, args);
    }

    /**
     * 7.3.10 Invoke(O,P [,args])
     * 
     * @param cx
     *            the execution context
     * @param object
     *            the script object
     * @param propertyKey
     *            the property key
     * @param args
     *            the method call arguments
     * @return the method return value
     */
    public static Object Invoke(ExecutionContext cx, Object object, Symbol propertyKey,
            Object... args) {
        /* steps 1-2 (not applicable) */
        /* steps 3-4 */
        ScriptObject base = ToObject(cx, object);
        /* steps 5-6 */
        Object func = base.get(cx, propertyKey, object);
        /* step 7 */
        if (!IsCallable(func)) {
            throw newTypeError(cx, Messages.Key.PropertyNotCallable, propertyKey.toString());
        }
        /* step 8 */
        return ((Callable) func).call(cx, object, args);
    }

    /**
     * 7.3.10 Invoke(O,P [,args])
     * 
     * @param cx
     *            the execution context
     * @param object
     *            the script object
     * @param propertyKey
     *            the property key
     * @param args
     *            the method call arguments
     * @return the method return value
     */
    public static Object Invoke(ExecutionContext cx, ScriptObject object, Symbol propertyKey,
            Object... args) {
        /* steps 1-4 (not applicable) */
        /* steps 5-6 */
        Object func = object.get(cx, propertyKey, object);
        /* step 7 */
        if (!IsCallable(func)) {
            throw newTypeError(cx, Messages.Key.PropertyNotCallable, propertyKey.toString());
        }
        /* step 8 */
        return ((Callable) func).call(cx, object, args);
    }

    /**
     * 7.3.11 SetIntegrityLevel (O, level)
     * 
     * @param cx
     *            the execution context
     * @param object
     *            the script object
     * @param level
     *            the new integrity level
     * @return {@code true} on success
     */
    public static boolean SetIntegrityLevel(ExecutionContext cx, ScriptObject object,
            IntegrityLevel level) {
        /* steps 1-2 */
        assert level == IntegrityLevel.Sealed || level == IntegrityLevel.Frozen;
        /* steps 3-5 */
        if (!object.preventExtensions(cx)) {
            return false;
        }
        /* steps 6-7 */
        List<?> keys = object.ownPropertyKeys(cx);
        /* step 8 */
        ScriptException pendingException = null;
        if (level == IntegrityLevel.Sealed) {
            /* step 9 */
            PropertyDescriptor nonConfigurable = new PropertyDescriptor();
            nonConfigurable.setConfigurable(false);
            for (Object key : keys) {
                try {
                    if (key instanceof String) {
                        DefinePropertyOrThrow(cx, object, (String) key, nonConfigurable);
                    } else {
                        assert key instanceof Symbol;
                        DefinePropertyOrThrow(cx, object, (Symbol) key, nonConfigurable);
                    }
                } catch (ScriptException e) {
                    if (pendingException == null) {
                        pendingException = e;
                    }
                }
            }
        } else {
            /* step 10 */
            PropertyDescriptor nonConfigurable = new PropertyDescriptor();
            nonConfigurable.setConfigurable(false);
            PropertyDescriptor nonConfigurableWritable = new PropertyDescriptor();
            nonConfigurableWritable.setConfigurable(false);
            nonConfigurableWritable.setWritable(false);
            for (Object key : keys) {
                try {
                    Property currentDesc;
                    if (key instanceof String) {
                        currentDesc = object.getOwnProperty(cx, (String) key);
                    } else {
                        assert key instanceof Symbol;
                        currentDesc = object.getOwnProperty(cx, (Symbol) key);
                    }
                    if (currentDesc != null) {
                        PropertyDescriptor desc;
                        if (currentDesc.isAccessorDescriptor()) {
                            desc = nonConfigurable;
                        } else {
                            desc = nonConfigurableWritable;
                        }
                        if (key instanceof String) {
                            DefinePropertyOrThrow(cx, object, (String) key, desc);
                        } else {
                            assert key instanceof Symbol;
                            DefinePropertyOrThrow(cx, object, (Symbol) key, desc);
                        }
                    }
                } catch (ScriptException e) {
                    if (pendingException == null) {
                        pendingException = e;
                    }
                }
            }
        }
        /* step 11 */
        if (pendingException != null) {
            throw pendingException;
        }
        /* step 12 */
        return true;
    }

    /**
     * 7.3.12 TestIntegrityLevel (O, level)
     * 
     * @param cx
     *            the execution context
     * @param object
     *            the script object
     * @param level
     *            the integrity level to test
     * @return {@code true} if the object conforms to the integrity level
     */
    public static boolean TestIntegrityLevel(ExecutionContext cx, ScriptObject object,
            IntegrityLevel level) {
        /* steps 1-2 */
        assert level == IntegrityLevel.Sealed || level == IntegrityLevel.Frozen;
        /* steps 3-4 */
        boolean status = IsExtensible(cx, object);
        /* steps 5-6 */
        if (status) {
            return false;
        }
        /* steps 7-8 */
        List<?> keys = object.ownPropertyKeys(cx);
        /* step 9 */
        ScriptException pendingException = null;
        /* step 10 */
        boolean configurable = false;
        /* step 11 */
        boolean writable = false;
        /* step 12 */
        for (Object key : keys) {
            try {
                Property currentDesc;
                if (key instanceof String) {
                    currentDesc = object.getOwnProperty(cx, (String) key);
                } else {
                    assert key instanceof Symbol;
                    currentDesc = object.getOwnProperty(cx, (Symbol) key);
                }
                if (currentDesc != null) {
                    configurable |= currentDesc.isConfigurable();
                    if (currentDesc.isDataDescriptor()) {
                        writable |= currentDesc.isWritable();
                    }
                }
            } catch (ScriptException e) {
                if (pendingException == null) {
                    pendingException = e;
                }
            }
        }
        /* step 13 */
        if (pendingException != null) {
            throw pendingException;
        }
        /* step 14 */
        if (level == IntegrityLevel.Frozen && writable) {
            return false;
        }
        /* step 15 */
        if (configurable) {
            return false;
        }
        /* step 16 */
        return true;
    }

    /**
     * 7.3.13 CreateArrayFromList (elements)
     * 
     * @param <T>
     *            the element type
     * @param cx
     *            the execution context
     * @param elements
     *            the array elements
     * @return the array object
     */
    public static <T> ArrayObject CreateArrayFromList(ExecutionContext cx, List<T> elements) {
        /* step 1 (not applicable) */
        /* step 2 */
        ArrayObject array = ArrayCreate(cx, 0);
        /* step 3 */
        int n = 0;
        /* step 4 */
        for (Object e : elements) {
            boolean status = CreateDataProperty(cx, array, n, e);
            assert status;
            n += 1;
        }
        /* step 5 */
        return array;
    }

    /**
     * 7.3.14 CreateListFromArrayLike (obj)
     * 
     * @param cx
     *            the execution context
     * @param obj
     *            the array-like object
     * @return the array elements
     */
    public static Object[] CreateListFromArrayLike(ExecutionContext cx, Object obj) {
        if (obj instanceof ArrayObject) {
            // Fast-path for dense arrays
            ArrayObject array = (ArrayObject) obj;
            if (array.isDenseArray()) {
                long len = array.getLength();
                if (len == 0) {
                    return ScriptRuntime.EMPTY_ARRAY;
                }
                // CreateListFromArrayLike() is (currently) only used for argument arrays
                if (len > FunctionPrototype.getMaxArguments()) {
                    throw newRangeError(cx, Messages.Key.FunctionTooManyArguments);
                }
                return array.toArray();
            }
        }
        /* steps 1-2 (not applicable) */
        /* step 3 */
        if (!Type.isObject(obj)) {
            throw newTypeError(cx, Messages.Key.NotObjectType);
        }
        ScriptObject object = Type.objectValue(obj);
        /* step 4 */
        Object len = Get(cx, object, "length");
        /* steps 5-6 */
        long n = ToLength(cx, len);
        // CreateListFromArrayLike() is (currently) only used for argument arrays
        if (n > FunctionPrototype.getMaxArguments()) {
            throw newRangeError(cx, Messages.Key.FunctionTooManyArguments);
        }
        int length = (int) n;
        /* step 7 */
        Object[] list = new Object[length];
        /* steps 8-9 */
        for (int index = 0; index < length; ++index) {
            int indexName = index;
            Object next = Get(cx, object, indexName);
            list[index] = next;
        }
        /* step 10 */
        return list;
    }

    /**
     * 7.3.14 CreateListFromArrayLike (obj)
     * 
     * @param cx
     *            the execution context
     * @param obj
     *            the array-like object
     * @param elementTypes
     *            the set of allowed element types
     * @return the array elements
     */
    public static List<Object> CreateListFromArrayLike(ExecutionContext cx, Object obj,
            EnumSet<Type> elementTypes) {
        assert elementTypes.size() == 2 && elementTypes.contains(Type.String)
                && elementTypes.contains(Type.Symbol) : elementTypes;
        if (obj instanceof ArrayObject) {
            // Fast-path for dense arrays
            ArrayObject array = (ArrayObject) obj;
            if (array.isDenseArray()) {
                long len = array.getLength();
                if (len == 0) {
                    return Collections.emptyList();
                }
                Object[] list = array.toArray();
                for (int index = 0, length = list.length; index < length; ++index) {
                    Object next = list[index];
                    if (next instanceof String || next instanceof Symbol) {
                        // list[index] = next;
                    } else if (next instanceof ConsString) {
                        // enforce flat string
                        list[index] = ((ConsString) next).toString();
                    } else {
                        throw newTypeError(cx, Messages.Key.ProxyPropertyKey, Type.of(next)
                                .toString());
                    }
                }
                return Arrays.asList(list);
            }
        }
        /* steps 1-2 (not applicable) */
        /* step 3 */
        if (!Type.isObject(obj)) {
            throw newTypeError(cx, Messages.Key.NotObjectType);
        }
        ScriptObject object = Type.objectValue(obj);
        /* step 4 */
        Object len = Get(cx, object, "length");
        /* steps 5-6 */
        long n = ToLength(cx, len);
        if (n > Integer.MAX_VALUE) {
            throw newInternalError(cx, Messages.Key.OutOfMemory);
        }
        int length = (int) n;
        /* step 7 */
        Object[] list = new Object[length];
        /* steps 8-9 */
        for (int index = 0; index < length; ++index) {
            int indexName = index;
            Object next = Get(cx, object, indexName);
            if (next instanceof String || next instanceof Symbol) {
                list[index] = next;
            } else if (next instanceof ConsString) {
                // enforce flat string
                list[index] = ((ConsString) next).toString();
            } else {
                throw newTypeError(cx, Messages.Key.ProxyPropertyKey, Type.of(next).toString());
            }
        }
        /* step 10 */
        return Arrays.asList(list);
    }

    /**
     * 7.3.15 OrdinaryHasInstance (C, O)
     * 
     * @param cx
     *            the execution context
     * @param c
     *            the constructor object
     * @param o
     *            the instance object
     * @return {@code true} on success
     */
    public static boolean OrdinaryHasInstance(ExecutionContext cx, Object c, Object o) {
        /* step 1 */
        if (!IsCallable(c)) {
            return false;
        }
        /* step 2 */
        if (c instanceof BoundFunctionObject) {
            Callable bc = ((BoundFunctionObject) c).getBoundTargetFunction();
            return InstanceofOperator(o, bc, cx);
        }
        /* step 3 */
        if (!Type.isObject(o)) {
            return false;
        }
        /* steps 4-5 */
        Object p = Get(cx, (ScriptObject) c, "prototype");
        if (!Type.isObject(p)) {
            throw newTypeError(cx, Messages.Key.NotObjectType);
        }
        /* step 7 */
        for (ScriptObject obj = Type.objectValue(o), proto = Type.objectValue(p);;) {
            obj = obj.getPrototypeOf(cx);
            if (obj == null) {
                return false;
            }
            if (SameValue(proto, obj)) {
                return true;
            }
        }
    }

    /**
     * 7.3.16 GetPrototypeFromConstructor ( constructor, intrinsicDefaultProto )
     * 
     * @param cx
     *            the execution context
     * @param constructor
     *            the constructor object
     * @param intrinsicDefaultProto
     *            the default prototype
     * @return the prototype object
     */
    public static ScriptObject GetPrototypeFromConstructor(ExecutionContext cx, Object constructor,
            Intrinsics intrinsicDefaultProto) {
        /* step 1 (not applicable) */
        /* step 2 */
        if (!IsConstructor(constructor)) {
            throw newTypeError(cx, Messages.Key.NotConstructor);
        }
        /* steps 3-4 */
        Object proto = Get(cx, Type.objectValue(constructor), "prototype");
        /* step 5 */
        if (!Type.isObject(proto)) {
            Realm realm = GetFunctionRealm(cx, (Constructor) constructor);
            proto = realm.getIntrinsic(intrinsicDefaultProto);
        }
        /* step 6 */
        return Type.objectValue(proto);
    }

    /**
     * 7.3.16 GetPrototypeFromConstructor ( constructor, intrinsicDefaultProto )
     * 
     * @param cx
     *            the execution context
     * @param constructor
     *            the constructor object
     * @param intrinsicDefaultProto
     *            the default prototype
     * @return the prototype object
     */
    public static ScriptObject GetPrototypeFromConstructor(ExecutionContext cx,
            Constructor constructor, Intrinsics intrinsicDefaultProto) {
        /* step 1 (not applicable) */
        /* step 2 (not applicable) */
        /* steps 3-4 */
        Object proto = Get(cx, constructor, "prototype");
        /* step 5 */
        if (!Type.isObject(proto)) {
            Realm realm = GetFunctionRealm(cx, constructor);
            proto = realm.getIntrinsic(intrinsicDefaultProto);
        }
        /* step 6 */
        return Type.objectValue(proto);
    }

    /**
     * 7.3.17 CreateFromConstructor (F)
     * 
     * @param cx
     *            the execution context
     * @param f
     *            the constructor function
     * @param args
     *            the arguments array
     * @return the new allocated object
     */
    public static ScriptObject CreateFromConstructor(ExecutionContext cx, Constructor f,
            Object... args) {
        /* step 1 (not applicable) */
        /* step 2 */
        if (f instanceof Creatable) {
            CreateAction<?> createAction = ((Creatable<?>) f).createAction();
            if (createAction != null) {
                ScriptObject obj = createAction.create(cx, f, args);
                assert obj != null;
                return obj;
            }
        }
        /* step 3 */
        return null;
    }

    /**
     * 7.3.18 Construct (F, argumentsList)
     * 
     * @param cx
     *            the execution context
     * @param f
     *            the constructor function
     * @param args
     *            the constructor function arguments
     * @return the new allocated and initialized object
     */
    public static ScriptObject Construct(ExecutionContext cx, Constructor f, Object... args) {
        /* step 1 (not applicable) */
        /* steps 2-3 */
        ScriptObject obj = CreateFromConstructor(cx, f, args);
        /* step 4 */
        if (obj == null) {
            obj = OrdinaryCreateFromConstructor(cx, f, Intrinsics.ObjectPrototype);
        }
        /* steps 5-6 */
        Object result = f.call(cx, obj, args);
        /* step 7 */
        if (Type.isObject(result)) {
            return Type.objectValue(result);
        }
        /* step 8 */
        return obj;
    }

    /**
     * 7.3.18 Construct (F, argumentsList)
     * 
     * @param cx
     *            the execution context
     * @param f
     *            the constructor function
     * @param args
     *            the constructor function arguments
     * @return the new allocated and initialized object or a tail-call invocation object
     * @throws Throwable
     *             if the underlying method throws an error
     */
    public static Object ConstructTailCall(ExecutionContext cx, Constructor f, Object... args)
            throws Throwable {
        /* step 1 (not applicable) */
        /* steps 2-3 */
        ScriptObject obj = CreateFromConstructor(cx, f, args);
        /* step 4 */
        if (obj == null) {
            obj = OrdinaryCreateFromConstructor(cx, f, Intrinsics.ObjectPrototype);
        }
        /* steps 5-6 */
        // Invoke 'tailCall()' instead of 'call()' to get TailCallInvocation objects
        Object result = f.tailCall(cx, obj, args);
        /* steps 7-8 (tail-call) */
        if (result instanceof TailCallInvocation) {
            // Don't unwind tail-call yet, instead store reference to 'obj'
            return ((TailCallInvocation) result).toConstructTailCall(obj);
        }
        /* step 7 */
        if (Type.isObject(result)) {
            return Type.objectValue(result);
        }
        /* step 8 */
        return obj;
    }

    /**
     * 7.3.19 GetOption (options, P)
     * 
     * @param cx
     *            the execution context
     * @param options
     *            the options object
     * @param propertyKey
     *            the property key
     * @return the option value
     */
    public static Object GetOption(ExecutionContext cx, Object options, Object propertyKey) {
        if (propertyKey instanceof String) {
            return GetOption(cx, options, (String) propertyKey);
        } else {
            return GetOption(cx, options, (Symbol) propertyKey);
        }
    }

    /**
     * 7.3.19 GetOption (options, P)
     * 
     * @param cx
     *            the execution context
     * @param options
     *            the options object
     * @param propertyKey
     *            the property key
     * @return the option value
     */
    public static Object GetOption(ExecutionContext cx, Object options, String propertyKey) {
        /* step 1 (not applicable) */
        /* step 2 */
        if (Type.isUndefined(options)) {
            return UNDEFINED;
        }
        /* step 3 */
        if (!Type.isObject(options)) {
            throw newTypeError(cx, Messages.Key.NotObjectType);
        }
        /* step 4 */
        return Type.objectValue(options).get(cx, propertyKey, options);
    }

    /**
     * 7.3.19 GetOption (options, P)
     * 
     * @param cx
     *            the execution context
     * @param options
     *            the options object
     * @param propertyKey
     *            the property key
     * @return the option value
     */
    public static Object GetOption(ExecutionContext cx, Object options, Symbol propertyKey) {
        /* step 1 (not applicable) */
        /* step 2 */
        if (Type.isUndefined(options)) {
            return UNDEFINED;
        }
        /* step 3 */
        if (!Type.isObject(options)) {
            throw newTypeError(cx, Messages.Key.NotObjectType);
        }
        /* step 4 */
        return Type.objectValue(options).get(cx, propertyKey, options);
    }

    /**
     * 7.3.20 EnumerableOwnNames (O)
     * 
     * @param cx
     *            the execution context
     * @param object
     *            the script object
     * @return <var>object</var>'s own enumerable string-valued property keys
     */
    public static List<String> EnumerableOwnNames(ExecutionContext cx, ScriptObject object) {
        /* step 1 (not applicable) */
        /* steps 2-3 */
        List<?> ownKeys = object.ownPropertyKeys(cx);
        /* step 4 */
        ArrayList<String> names = new ArrayList<>();
        /* step 5 */
        for (Object key : ownKeys) {
            if (key instanceof String) {
                String skey = (String) key;
                Property desc = object.getOwnProperty(cx, skey);
                if (desc != null && desc.isEnumerable()) {
                    names.add(skey);
                }
            }
        }
        /* step 6 (sort keys - not applicable) */
        /* step 7 */
        return names;
    }

    /**
     * 7.3.21 GetFunctionRealm ( obj ) Abstract Operation
     * 
     * @param cx
     *            the execution context
     * @param function
     *            the callable object
     * @return the function's realm
     */
    public static Realm GetFunctionRealm(ExecutionContext cx, Callable function) {
        /* steps 1-5 */
        return function.getRealm(cx);
    }

    /**
     * 7.4.1 CheckIterable ( obj )
     * 
     * @param cx
     *            the execution context
     * @param obj
     *            the script object
     * @return the iterator method
     */
    public static Object CheckIterable(ExecutionContext cx, Object obj) {
        /* step 1 */
        if (!Type.isObject(obj)) {
            // Directly throw a TypeError instead of returning `undefined` to get a more useful
            // error message. The only callers of this method are Promise.all and Promise.race.
            // return UNDEFINED;
            throw newTypeError(cx, Messages.Key.NotObjectType);
        }
        /* step 2 */
        return Get(cx, Type.objectValue(obj), BuiltinSymbol.iterator.get());
    }

    /**
     * 7.4.1 CheckIterable ( obj )
     * 
     * @param cx
     *            the execution context
     * @param obj
     *            the script object
     * @return the iterator method
     */
    public static Object CheckIterable(ExecutionContext cx, ScriptObject obj) {
        /* step 1 (not applicable) */
        /* step 2 */
        return Get(cx, Type.objectValue(obj), BuiltinSymbol.iterator.get());
    }

    /**
     * 7.4.2 GetIterator ( obj )
     * 
     * @param cx
     *            the execution context
     * @param obj
     *            the script object
     * @return the script iterator object
     */
    public static ScriptObject GetIterator(ExecutionContext cx, Object obj) {
        /* step 1 (not applicable) */
        /* step 2 */
        Object method = CheckIterable(cx, obj);
        /* steps 3-7 */
        return GetIterator(cx, obj, method);
    }

    /**
     * 7.4.2 GetIterator ( obj )
     * 
     * @param cx
     *            the execution context
     * @param obj
     *            the script object
     * @return the script iterator object
     */
    public static ScriptObject GetIterator(ExecutionContext cx, ScriptObject obj) {
        /* step 1 (not applicable) */
        /* step 2 */
        Object method = CheckIterable(cx, obj);
        /* steps 3-7 */
        return GetIterator(cx, obj, method);
    }

    /**
     * 7.4.2 GetIterator ( obj )
     * 
     * @param cx
     *            the execution context
     * @param obj
     *            the script object
     * @param method
     *            the iterator method
     * @return the script iterator object
     */
    public static ScriptObject GetIterator(ExecutionContext cx, Object obj, Object method) {
        /* steps 1-2 (not applicable) */
        /* step 3 */
        if (!IsCallable(method)) {
            throw newTypeError(cx, Messages.Key.PropertyNotCallable,
                    BuiltinSymbol.iterator.toString());
        }
        /* steps 4-5 */
        Object iterator = ((Callable) method).call(cx, obj);
        /* step 6 */
        if (!Type.isObject(iterator)) {
            throw newTypeError(cx, Messages.Key.NotObjectType);
        }
        /* step 7 */
        return Type.objectValue(iterator);
    }

    /**
     * 7.4.2 GetIterator ( obj )
     * 
     * @param cx
     *            the execution context
     * @param obj
     *            the script object
     * @param method
     *            the iterator method
     * @return the script iterator object
     */
    public static ScriptObject GetIterator(ExecutionContext cx, ScriptObject obj, Object method) {
        /* steps 1-2 (not applicable) */
        /* step 3 */
        if (!IsCallable(method)) {
            throw newTypeError(cx, Messages.Key.PropertyNotCallable,
                    BuiltinSymbol.iterator.toString());
        }
        /* steps 4-5 */
        Object iterator = ((Callable) method).call(cx, obj);
        /* step 6 */
        if (!Type.isObject(iterator)) {
            throw newTypeError(cx, Messages.Key.NotObjectType);
        }
        /* step 7 */
        return Type.objectValue(iterator);
    }

    /**
     * 7.4.3 IteratorNext ( iterator, value )
     * 
     * @param cx
     *            the execution context
     * @param iterator
     *            the script iterator object
     * @return the next value from the iterator
     */
    public static ScriptObject IteratorNext(ExecutionContext cx, ScriptObject iterator) {
        /* steps 1-3 */
        Object result = Invoke(cx, iterator, "next");
        /* step 4 */
        if (!Type.isObject(result)) {
            throw newTypeError(cx, Messages.Key.NotObjectType);
        }
        /* step 5 */
        return Type.objectValue(result);
    }

    /**
     * 7.4.3 IteratorNext ( iterator, value )
     * 
     * @param cx
     *            the execution context
     * @param iterator
     *            the script iterator object
     * @param value
     *            the value to pass to the next() function
     * @return the next value from the iterator
     */
    public static ScriptObject IteratorNext(ExecutionContext cx, ScriptObject iterator, Object value) {
        /* steps 1-3 */
        Object result = Invoke(cx, iterator, "next", value);
        /* step 4 */
        if (!Type.isObject(result)) {
            throw newTypeError(cx, Messages.Key.NotObjectType);
        }
        /* step 5 */
        return Type.objectValue(result);
    }

    /**
     * 7.4.? IteratorReturn ( iterator, value )
     * 
     * @param cx
     *            the execution context
     * @param iterator
     *            the script iterator object
     * @param value
     *            the value to pass to the throw() function
     * @return the next value from the iterator
     */
    public static Object IteratorReturn(ExecutionContext cx, ScriptObject iterator, Object value) {
        Object result = Invoke(cx, iterator, "return", value);
        if (!Type.isObject(result)) {
            throw newTypeError(cx, Messages.Key.NotObjectType);
        }
        return IteratorValue(cx, Type.objectValue(result));
    }

    /**
     * 7.4.? IteratorThrow ( iterator, value )
     * 
     * @param cx
     *            the execution context
     * @param iterator
     *            the script iterator object
     * @param value
     *            the value to pass to the throw() function
     */
    public static void IteratorThrow(ExecutionContext cx, ScriptObject iterator, Object value) {
        Invoke(cx, iterator, "throw", value);
    }

    /**
     * 7.4.4 IteratorComplete (iterResult)
     * 
     * @param cx
     *            the execution context
     * @param iterResult
     *            the iterator result object
     * @return {@code true} if the iterator is completed
     */
    public static boolean IteratorComplete(ExecutionContext cx, ScriptObject iterResult) {
        /* step 1 (not applicable) */
        /* step 2 */
        Object done = Get(cx, iterResult, "done");
        /* step 3 */
        return ToBoolean(done);
    }

    /**
     * 7.4.5 IteratorValue (iterResult)
     * 
     * @param cx
     *            the execution context
     * @param iterResult
     *            the iterator result object
     * @return the iterator result value
     */
    public static Object IteratorValue(ExecutionContext cx, ScriptObject iterResult) {
        /* step 1 (not applicable) */
        /* step 2 */
        return Get(cx, iterResult, "value");
    }

    /**
     * 7.4.6 IteratorStep ( iterator )
     * 
     * @param cx
     *            the execution context
     * @param iterator
     *            the script iterator object
     * @return the next value from the iterator or {@code null}
     */
    public static ScriptObject IteratorStep(ExecutionContext cx, ScriptObject iterator) {
        /* steps 1-2 */
        ScriptObject result = IteratorNext(cx, iterator);
        /* steps 3-4 */
        boolean done = IteratorComplete(cx, result);
        /* step 5 */
        if (done) {
            return null;
        }
        /* step 6 */
        return result;
    }

    /**
     * 7.4.7 IteratorClose( iterator, completion )
     * 
     * @param cx
     *            the execution context
     * @param iterator
     *            the script iterator object
     * @param isThrowCompletion
     *            {@code true} if the completion is Throw
     */
    public static void IteratorClose(ExecutionContext cx, ScriptObject iterator,
            boolean isThrowCompletion) {
        /* step 1 (not applicable) */
        /* step 2 (not applicable) */
        /* steps 3-4 */
        // FIXME: spec issue - change to GetMethod to avoid Has+Get?
        boolean hasReturn = HasProperty(cx, iterator, "return");
        /* step 5 */
        if (hasReturn) {
            if (isThrowCompletion) {
                try {
                    Invoke(cx, iterator, "return");
                } catch (ScriptException e) {
                    // ignore
                }
            } else {
                Invoke(cx, iterator, "return");
            }
        }
    }

    /**
     * 7.4.8 CreateIterResultObject (value, done)
     * 
     * @param cx
     *            the execution context
     * @param value
     *            the iterator result value
     * @param done
     *            the iterator result state
     * @return the new iterator result object
     */
    public static OrdinaryObject CreateIterResultObject(ExecutionContext cx, Object value,
            boolean done) {
        /* step 1 (not applicable) */
        /* step 2 */
        OrdinaryObject obj = ObjectCreate(cx, Intrinsics.ObjectPrototype);
        /* step 3 */
        CreateDataProperty(cx, obj, "value", value);
        /* step 4 */
        CreateDataProperty(cx, obj, "done", done);
        /* step 5 */
        return obj;
    }

    /**
     * 7.4.9 CreateListIterator (list)
     * 
     * @param <T>
     *            the element type
     * @param cx
     *            the execution context
     * @param list
     *            the source iterable
     * @return a new script object iterator
     */
    public static <T> ScriptObject CreateListIterator(ExecutionContext cx, Iterable<T> list) {
        return ListIterator.CreateListIterator(cx, list.iterator());
    }

    /**
     * 7.4.9 CreateListIterator (list)
     * 
     * @param <T>
     *            the element type
     * @param cx
     *            the execution context
     * @param iterator
     *            the source iterator
     * @return a new script object iterator
     */
    public static <T> ScriptObject CreateListIterator(ExecutionContext cx, Iterator<T> iterator) {
        return ListIterator.CreateListIterator(cx, iterator);
    }

    /**
     * 7.4.10 CreateCompoundIterator ( iterator1, iterator2 )
     * 
     * @param <T>
     *            the element type
     * @param cx
     *            the execution context
     * @param iterator1
     *            the first source iterator
     * @param iterator2
     *            the second source iterator
     * @return a new script object iterator
     */
    public static <T> ScriptObject CreateCompoundIterator(ExecutionContext cx,
            Iterator<T> iterator1, Iterator<T> iterator2) {
        return CompoundIterator.CreateCompoundIterator(cx, iterator1, iterator2);
    }

    /**
     * 7.5.1 PromiseNew ( executor ) Abstract Operation
     * 
     * @param cx
     *            the execution context
     * @param executor
     *            the executor function
     * @return the new promise object
     */
    public static PromiseObject PromiseNew(ExecutionContext cx, Callable executor) {
        /* step 1 */
        PromiseObject promise = AllocatePromise(cx,
                (Constructor) cx.getIntrinsic(Intrinsics.Promise));
        /* step 2 */
        return InitializePromise(cx, promise, executor);
    }

    /**
     * 7.5.2 PromiseBuiltinCapability () Abstract Operation
     * 
     * @param cx
     *            the execution context
     * @return the promise capability record
     */
    public static PromiseCapability<PromiseObject> PromiseBuiltinCapability(ExecutionContext cx) {
        /* step 1 */
        PromiseObject promise = AllocatePromise(cx,
                (Constructor) cx.getIntrinsic(Intrinsics.Promise));
        /* step 2 */
        return CreatePromiseCapabilityRecord(cx, promise,
                (Constructor) cx.getIntrinsic(Intrinsics.Promise));
    }

    /**
     * 7.5.3 PromiseOf (value) Abstract Operation
     * 
     * @param cx
     *            the execution context
     * @param value
     *            the resolved value
     * @return the new promise object
     */
    public static PromiseObject PromiseOf(ExecutionContext cx, Object value) {
        /* steps 1-2 */
        PromiseCapability<PromiseObject> capability = PromiseBuiltinCapability(cx);
        /* steps 3-4 */
        capability.getResolve().call(cx, UNDEFINED, value);
        /* step 5 */
        return capability.getPromise();
    }

    /**
     * Returns a list of all string-valued [[OwnPropertyKeys]] of {@code obj}.
     * 
     * @param cx
     *            the execution context
     * @param obj
     *            the script object
     * @return <var>obj</var>'s own string-valued property keys
     */
    public static List<String> GetOwnPropertyNames(ExecutionContext cx, ScriptObject obj) {
        // FIXME: spec clean-up (Bug 1142)
        ArrayList<String> nameList = new ArrayList<>();
        for (Object key : obj.ownPropertyKeys(cx)) {
            if (key instanceof String) {
                nameList.add((String) key);
            }
        }
        return nameList;
    }

    /**
     * Returns a list of all enumerable [[OwnPropertyKeys]] of {@code obj}.
     * 
     * @param cx
     *            the execution context
     * @param obj
     *            the script object
     * @return <var>obj</var>'s own enumerable property keys
     */
    public static List<Object> GetOwnEnumerablePropertyKeys(ExecutionContext cx, ScriptObject obj) {
        // FIXME: spec clean-up (Bug 1142)
        ArrayList<Object> nameList = new ArrayList<>();
        for (Object key : obj.ownPropertyKeys(cx)) {
            Property desc;
            if (key instanceof String) {
                desc = obj.getOwnProperty(cx, (String) key);
            } else {
                desc = obj.getOwnProperty(cx, (Symbol) key);
            }
            if (desc != null && desc.isEnumerable()) {
                nameList.add(key);
            }
        }
        return nameList;
    }

    /**
     * 7.1.3.1 ToNumber Applied to the String Type
     */
    private static final class ToNumberParser {
        private ToNumberParser() {
        }

        static double parse(String input) {
            String s = Strings.trim(input);
            int len = s.length();
            if (len == 0) {
                return 0;
            }
            if (s.charAt(0) == '0' && len > 2) {
                char c = s.charAt(1);
                if (c == 'x' || c == 'X') {
                    return readHexIntegerLiteral(s);
                }
                if (c == 'b' || c == 'B') {
                    return readBinaryIntegerLiteral(s);
                }
                if (c == 'o' || c == 'O') {
                    return readOctalIntegerLiteral(s);
                }
            }
            return readDecimalLiteral(s);
        }

        private static double readHexIntegerLiteral(String s) {
            assert s.length() > 2;
            final int start = 2; // "0x" prefix
            for (int index = start, end = s.length(); index < end; ++index) {
                char c = s.charAt(index);
                if (!(('0' <= c && c <= '9') || ('A' <= c && c <= 'F') || ('a' <= c && c <= 'f'))) {
                    return Double.NaN;
                }
            }
            return NumberParser.parseHex(s);
        }

        private static double readBinaryIntegerLiteral(String s) {
            assert s.length() > 2;
            final int start = 2; // "0b" prefix
            for (int index = start, end = s.length(); index < end; ++index) {
                char c = s.charAt(index);
                if (!(c == '0' || c == '1')) {
                    return Double.NaN;
                }
            }
            return NumberParser.parseBinary(s);
        }

        private static double readOctalIntegerLiteral(String s) {
            assert s.length() > 2;
            final int start = 2; // "0o" prefix
            for (int index = start, end = s.length(); index < end; ++index) {
                char c = s.charAt(index);
                if (!('0' <= c && c <= '7')) {
                    return Double.NaN;
                }
            }
            return NumberParser.parseOctal(s);
        }

        static double readDecimalLiteral(String s) {
            final int Infinity_length = "Infinity".length();

            outOfBounds: invalidChar: {
                final int end = s.length();
                int index = 0;
                int c = s.charAt(index++);
                boolean isPos = true;
                if (c == '+' || c == '-') {
                    if (index >= end)
                        break outOfBounds;
                    isPos = (c == '+');
                    c = s.charAt(index++);
                }
                if (c == 'I') {
                    // Infinity
                    if (index - 1 + Infinity_length == end
                            && s.regionMatches(index - 1, "Infinity", 0, Infinity_length)) {
                        return isPos ? Double.POSITIVE_INFINITY : Double.NEGATIVE_INFINITY;
                    }
                    break invalidChar;
                }
                boolean hasDot = (c == '.'), hasExp = false;
                if (hasDot) {
                    if (index >= end)
                        break outOfBounds;
                    c = s.charAt(index);
                }
                if (!('0' <= c && c <= '9')) {
                    break invalidChar;
                }
                if (!hasDot) {
                    while (index < end) {
                        c = s.charAt(index++);
                        if ('0' <= c && c <= '9') {
                            continue;
                        }
                        if (c == '.') {
                            hasDot = true;
                            break;
                        }
                        if (c == 'e' || c == 'E') {
                            hasExp = true;
                            break;
                        }
                        break invalidChar;
                    }
                }
                if (hasDot) {
                    while (index < end) {
                        c = s.charAt(index++);
                        if ('0' <= c && c <= '9') {
                            continue;
                        }
                        if (c == 'e' || c == 'E') {
                            hasExp = true;
                            break;
                        }
                        break invalidChar;
                    }
                }
                if (hasExp) {
                    if (index >= end)
                        break outOfBounds;
                    c = s.charAt(index++);
                    if (c == '+' || c == '-') {
                        if (index >= end)
                            break outOfBounds;
                        c = s.charAt(index++);
                    }
                    if (!('0' <= c && c <= '9')) {
                        break invalidChar;
                    }
                    while (index < end) {
                        c = s.charAt(index++);
                        if ('0' <= c && c <= '9') {
                            continue;
                        }
                        break invalidChar;
                    }
                }
                if (index != end) {
                    break invalidChar;
                }
                if (!(hasDot || hasExp)) {
                    return NumberParser.parseInteger(s);
                }
                return NumberParser.parseDecimal(s);
            }
            return Double.NaN;
        }
    }
}
