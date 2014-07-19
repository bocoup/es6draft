/**
 * Copyright (c) 2012-2014 André Bargull
 * Alle Rechte vorbehalten / All Rights Reserved.  Use is subject to license terms.
 *
 * <https://github.com/anba/es6draft>
 */
package com.github.anba.es6draft.runtime.objects.text;

import static com.github.anba.es6draft.runtime.AbstractOperations.*;
import static com.github.anba.es6draft.runtime.internal.Errors.newTypeError;
import static com.github.anba.es6draft.runtime.internal.Properties.createProperties;
import static com.github.anba.es6draft.runtime.objects.text.RegExpConstructor.EscapeRegExpPattern;
import static com.github.anba.es6draft.runtime.objects.text.RegExpConstructor.RegExpInitialize;
import static com.github.anba.es6draft.runtime.types.Null.NULL;
import static com.github.anba.es6draft.runtime.types.Undefined.UNDEFINED;
import static com.github.anba.es6draft.runtime.types.builtins.ExoticArray.ArrayCreate;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.regex.MatchResult;

import com.github.anba.es6draft.regexp.IterableMatchResult;
import com.github.anba.es6draft.regexp.MatchState;
import com.github.anba.es6draft.regexp.RegExpMatcher;
import com.github.anba.es6draft.runtime.ExecutionContext;
import com.github.anba.es6draft.runtime.Realm;
import com.github.anba.es6draft.runtime.internal.CompatibilityOption;
import com.github.anba.es6draft.runtime.internal.Initializable;
import com.github.anba.es6draft.runtime.internal.Messages;
import com.github.anba.es6draft.runtime.internal.Properties.Accessor;
import com.github.anba.es6draft.runtime.internal.Properties.Attributes;
import com.github.anba.es6draft.runtime.internal.Properties.CompatibilityExtension;
import com.github.anba.es6draft.runtime.internal.Properties.Function;
import com.github.anba.es6draft.runtime.internal.Properties.Prototype;
import com.github.anba.es6draft.runtime.internal.Properties.Value;
import com.github.anba.es6draft.runtime.types.BuiltinSymbol;
import com.github.anba.es6draft.runtime.types.Callable;
import com.github.anba.es6draft.runtime.types.Intrinsics;
import com.github.anba.es6draft.runtime.types.ScriptObject;
import com.github.anba.es6draft.runtime.types.Type;
import com.github.anba.es6draft.runtime.types.builtins.ExoticArray;
import com.github.anba.es6draft.runtime.types.builtins.NativeFunction;
import com.github.anba.es6draft.runtime.types.builtins.NativeFunction.NativeFunctionId;
import com.github.anba.es6draft.runtime.types.builtins.OrdinaryObject;

/**
 * <h1>21 Text Processing</h1><br>
 * <h2>21.2 RegExp (Regular Expression) Objects</h2>
 * <ul>
 * <li>21.2.5 Properties of the RegExp Prototype Object
 * <li>21.2.6 Properties of RegExp Instances
 * </ul>
 */
public final class RegExpPrototype extends OrdinaryObject implements Initializable {
    /**
     * Constructs a new RegExp prototype object.
     * 
     * @param realm
     *            the realm object
     */
    public RegExpPrototype(Realm realm) {
        super(realm);
    }

    @Override
    public void initialize(ExecutionContext cx) {
        createProperties(cx, this, Properties.class);
        createProperties(cx, this, AdditionalProperties.class);
    }

    private static RegExpObject thisRegExpValue(ExecutionContext cx, Object object) {
        if (object instanceof RegExpObject) {
            RegExpObject obj = (RegExpObject) object;
            if (obj.isInitialized()) {
                return obj;
            }
            throw newTypeError(cx, Messages.Key.UninitializedObject);
        }
        throw newTypeError(cx, Messages.Key.IncompatibleObject);
    }

    /**
     * 21.2.5 Properties of the RegExp Prototype Object
     */
    public enum Properties {
        ;

        @Prototype
        public static final Intrinsics __proto__ = Intrinsics.ObjectPrototype;

        /**
         * 21.2.5.1 RegExp.prototype.constructor
         */
        @Value(name = "constructor")
        public static final Intrinsics constructor = Intrinsics.RegExp;

        /**
         * 21.2.5.2 RegExp.prototype.exec(string)
         * 
         * @param cx
         *            the execution context
         * @param thisValue
         *            the function this-value
         * @param string
         *            the string
         * @return the match result array
         */
        @Function(name = "exec", arity = 1, nativeId = NativeFunctionId.RegExpPrototypeExec)
        public static Object exec(ExecutionContext cx, Object thisValue, Object string) {
            /* steps 1-4 */
            RegExpObject r = thisRegExpValue(cx, thisValue);
            /* steps 5-6 */
            String s = ToFlatString(cx, string);
            /* step 7 */
            ExoticArray result = RegExpBuiltinExec(cx, r, s);
            return result != null ? result : NULL;
        }

        /**
         * 21.2.5.3 get RegExp.prototype.global
         * 
         * @param cx
         *            the execution context
         * @param thisValue
         *            the function this-value
         * @return the global flag
         */
        @Accessor(name = "global", type = Accessor.Type.Getter)
        public static Object global(ExecutionContext cx, Object thisValue) {
            /* steps 1-5 */
            RegExpObject r = thisRegExpValue(cx, thisValue);
            /* steps 6-7 */
            return r.getOriginalFlags().indexOf('g') != -1;
        }

        /**
         * 21.2.5.4 get RegExp.prototype.ignoreCase
         * 
         * @param cx
         *            the execution context
         * @param thisValue
         *            the function this-value
         * @return the ignoreCase flag
         */
        @Accessor(name = "ignoreCase", type = Accessor.Type.Getter)
        public static Object ignoreCase(ExecutionContext cx, Object thisValue) {
            /* steps 1-5 */
            RegExpObject r = thisRegExpValue(cx, thisValue);
            /* steps 6-7 */
            return r.getOriginalFlags().indexOf('i') != -1;
        }

        /**
         * 21.2.5.6 get RegExp.prototype.multiline
         * 
         * @param cx
         *            the execution context
         * @param thisValue
         *            the function this-value
         * @return the multiline flag
         */
        @Accessor(name = "multiline", type = Accessor.Type.Getter)
        public static Object multiline(ExecutionContext cx, Object thisValue) {
            /* steps 1-5 */
            RegExpObject r = thisRegExpValue(cx, thisValue);
            /* steps 6-7 */
            return r.getOriginalFlags().indexOf('m') != -1;
        }

        /**
         * 21.2.5.9 get RegExp.prototype.source
         * 
         * @param cx
         *            the execution context
         * @param thisValue
         *            the function this-value
         * @return the source property
         */
        @Accessor(name = "source", type = Accessor.Type.Getter)
        public static Object source(ExecutionContext cx, Object thisValue) {
            /* steps 1-7 */
            RegExpObject r = thisRegExpValue(cx, thisValue);
            /* step 8 */
            return EscapeRegExpPattern(r.getOriginalSource(), r.getOriginalFlags());
        }

        /**
         * 21.2.5.11 get RegExp.prototype.sticky
         * 
         * @param cx
         *            the execution context
         * @param thisValue
         *            the function this-value
         * @return the sticky flag
         */
        @Accessor(name = "sticky", type = Accessor.Type.Getter)
        public static Object sticky(ExecutionContext cx, Object thisValue) {
            /* steps 1-5 */
            RegExpObject r = thisRegExpValue(cx, thisValue);
            /* steps 6-7 */
            return r.getOriginalFlags().indexOf('y') != -1;
        }

        /**
         * 21.2.5.12 RegExp.prototype.test(string)
         * 
         * @param cx
         *            the execution context
         * @param thisValue
         *            the function this-value
         * @param string
         *            the string
         * @return {@code true} if the string matches the pattern
         */
        @Function(name = "test", arity = 1)
        public static Object test(ExecutionContext cx, Object thisValue, Object string) {
            /* step 1 */
            if (!Type.isObject(thisValue)) {
                throw newTypeError(cx, Messages.Key.NotObjectType);
            }
            /* step 2 */
            ScriptObject r = Type.objectValue(thisValue);
            /* steps 3-4 */
            String s = ToFlatString(cx, string);
            /* steps 5-6 */
            MatchResult match = getMatcherOrNull(cx, r, s, true);
            /* step 7 */
            return match != null;
        }

        /**
         * 21.2.5.14 get RegExp.prototype.unicode
         * 
         * @param cx
         *            the execution context
         * @param thisValue
         *            the function this-value
         * @return the unicode flag
         */
        @Accessor(name = "unicode", type = Accessor.Type.Getter)
        public static Object unicode(ExecutionContext cx, Object thisValue) {
            /* steps 1-5 */
            RegExpObject r = thisRegExpValue(cx, thisValue);
            /* steps 6-7 */
            return r.getOriginalFlags().indexOf('u') != -1;
        }

        /**
         * 21.2.5.13 RegExp.prototype.toString()
         * 
         * @param cx
         *            the execution context
         * @param thisValue
         *            the function this-value
         * @return the string representation
         */
        @Function(name = "toString", arity = 0)
        public static Object toString(ExecutionContext cx, Object thisValue) {
            /* steps 1-4 */
            RegExpObject r = thisRegExpValue(cx, thisValue);
            /* steps 5-6 */
            CharSequence source = ToString(cx, Get(cx, r, "source"));
            if (source.length() == 0) {
                source = "(?:)";
            }
            /* step 7 */
            StringBuilder result = new StringBuilder().append('/').append(source).append('/');
            /* steps 8-10 */
            if (ToBoolean(Get(cx, r, "global"))) {
                result.append('g');
            }
            /* steps 11-13 */
            if (ToBoolean(Get(cx, r, "ignoreCase"))) {
                result.append('i');
            }
            /* steps 14-16 */
            if (ToBoolean(Get(cx, r, "multiline"))) {
                result.append('m');
            }
            /* steps 17-19 */
            if (ToBoolean(Get(cx, r, "unicode"))) {
                result.append('u');
            }
            /* steps 20-22 */
            if (ToBoolean(Get(cx, r, "sticky"))) {
                result.append('y');
            }
            /* step 23 */
            return result.toString();
        }

        /**
         * 21.2.5.5 RegExp.prototype.match (string)
         * 
         * @param cx
         *            the execution context
         * @param thisValue
         *            the function this-value
         * @param string
         *            the string
         * @return the match result array
         */
        @Function(name = "match", arity = 1)
        public static Object match(ExecutionContext cx, Object thisValue, Object string) {
            /* step 2 */
            if (!Type.isObject(thisValue)) {
                throw newTypeError(cx, Messages.Key.NotObjectType);
            }
            /* step 1 */
            ScriptObject rx = Type.objectValue(thisValue);
            /* steps 3-4 */
            String s = ToFlatString(cx, string);
            /* steps 5-6 */
            boolean global = ToBoolean(Get(cx, rx, "global"));
            /* steps 7-8 */
            if (!global) {
                ScriptObject result = RegExpExec(cx, rx, s);
                return result != null ? result : NULL;
            } else {
                /* steps 8.a-8.b */
                Put(cx, rx, "lastIndex", 0, true);
                /* step 8.c */
                ExoticArray array = ArrayCreate(cx, 0);
                /* step 8.d */
                // double previousLastIndex = 0;
                /* steps 8.e-8.f */
                for (int n = 0;;) {
                    // ScriptObject result = RegExpExec(cx, rx, s);
                    MatchResult result = getMatcherOrNull(cx, rx, s, true);
                    if (result == null) {
                        return n == 0 ? NULL : array;
                    }
                    // FIXME: spec issue (bug 1467)
                    // double thisIndex = ToInteger(cx, Get(cx, rx, "lastIndex"));
                    // if (thisIndex == previousLastIndex) {
                    // Put(cx, rx, "lastIndex", thisIndex + 1, true);
                    // previousLastIndex = thisIndex + 1;
                    // } else {
                    // previousLastIndex = thisIndex;
                    // }
                    // Object matchStr = Get(cx, result, "0");
                    String matchStr = result.group(0);
                    if (matchStr.isEmpty()) {
                        double thisIndex = ToInteger(cx, Get(cx, rx, "lastIndex"));
                        Put(cx, rx, "lastIndex", thisIndex + 1, true);
                    }
                    CreateDataPropertyOrThrow(cx, array, n, matchStr);
                    n += 1;
                }
            }
        }

        /**
         * 21.2.5.7 RegExp.prototype.replace (S, replaceValue)
         * 
         * @param cx
         *            the execution context
         * @param thisValue
         *            the function this-value
         * @param string
         *            the string
         * @param replaceValue
         *            the replace string or replacer function
         * @return the new string
         */
        @Function(name = "replace", arity = 2)
        public static Object replace(ExecutionContext cx, Object thisValue, Object string,
                Object replaceValue) {
            /* step 2 */
            if (!Type.isObject(thisValue)) {
                throw newTypeError(cx, Messages.Key.NotObjectType);
            }
            /* step 1 */
            ScriptObject rx = Type.objectValue(thisValue);
            /* step 3 (FIXME: spec bug - invalid) (bug 2778) */
            /* steps 4-5 */
            String s = ToFlatString(cx, string);
            /* step 6 */
            int lengthS = s.length();
            /* step 7 */
            boolean functionalReplace = IsCallable(replaceValue);
            String replaceValueString = null;
            Callable replaceValueCallable = null;
            if (!functionalReplace) {
                replaceValueString = ToFlatString(cx, replaceValue);
            } else {
                replaceValueCallable = (Callable) replaceValue;
            }
            /* steps 8-9 */
            boolean global = ToBoolean(Get(cx, rx, "global"));
            /* step 10 */
            if (global) {
                Put(cx, rx, "lastIndex", 0, true);
            }
            /* step 11 */
            // double previousLastIndex = 0;
            /* step 12 */
            ArrayList<MatchResult> results = new ArrayList<>();
            /* step 13 */
            boolean done = false;
            /* step 14 */
            while (!done) {
                /* steps 14.a-14.b */
                MatchResult result = getMatcherOrNull(cx, rx, s, false);
                /* steps 14.c-14.d */
                if (result == null) {
                    /* step 14.c */
                    done = true;
                } else {
                    /* step 14.d */
                    if (!global) {
                        done = true;
                    } else {
                        // FIXME: spec issue (bug 1467)
                        // double thisIndex = ToInteger(cx, Get(cx, rx, "lastIndex"));
                        // if (thisIndex == previousLastIndex) {
                        // Put(cx, rx, "lastIndex", thisIndex + 1, true);
                        // previousLastIndex = thisIndex + 1;
                        // } else {
                        // previousLastIndex = thisIndex;
                        // }
                        String matchStr = result.group(0);
                        if (matchStr.isEmpty()) {
                            double thisIndex = ToInteger(cx, Get(cx, rx, "lastIndex"));
                            Put(cx, rx, "lastIndex", thisIndex + 1, true);
                        }
                    }
                }
                /* step 14.e */
                if (result != null) {
                    results.add(result);
                }
            }
            // fast-path if matches were found
            if (results.isEmpty()) {
                return s;
            }
            /* step 15 */
            StringBuilder accumulatedResult = new StringBuilder();
            /* step 16 */
            int nextSrcPosition = 0;
            /* step 17 (omitted) */
            /* step 18 */
            for (MatchResult result : results) {
                if (!(result instanceof ScriptObjectMatchResult)) {
                    RegExpConstructor.storeLastMatchResult(cx, s, result);
                }
                /* steps 18.a-18.b */
                String matched = result.group(0);
                /* step 18.c */
                int matchLength = matched.length();
                /* steps 18.d-18.f */
                int position = Math.max(Math.min(result.start(), lengthS), 0);
                /* steps 18.g-18.l */
                String replacement;
                if (functionalReplace) {
                    Object[] replacerArgs = GetReplacerArguments(result, s, matched, position);
                    Object replValue = replaceValueCallable.call(cx, UNDEFINED, replacerArgs);
                    replacement = ToFlatString(cx, replValue);
                } else {
                    String[] captures = groups(result);
                    replacement = GetReplaceSubstitution(matched, s, position, captures,
                            replaceValueString);
                }
                /* step 18.m */
                if (position >= nextSrcPosition) {
                    accumulatedResult.append(s, nextSrcPosition, position).append(replacement);
                    nextSrcPosition = position + matchLength;
                }
            }
            /* step 19 */
            if (nextSrcPosition >= s.length()) {
                return accumulatedResult.toString();
            }
            /* step 20 */
            return accumulatedResult.append(s, nextSrcPosition, s.length()).toString();
        }

        /**
         * 21.2.5.8 RegExp.prototype.search (string)
         * 
         * @param cx
         *            the execution context
         * @param thisValue
         *            the function this-value
         * @param string
         *            the string
         * @return the string index of the first match
         */
        @Function(name = "search", arity = 1)
        public static Object search(ExecutionContext cx, Object thisValue, Object string) {
            /* step 2 */
            if (!Type.isObject(thisValue)) {
                throw newTypeError(cx, Messages.Key.NotObjectType);
            }
            /* step 1 */
            ScriptObject rx = Type.objectValue(thisValue);
            /* steps 3-4 */
            String s = ToFlatString(cx, string);
            /* steps 5-6 */
            Object previousLastIndex = Get(cx, rx, "lastIndex");
            /* steps 7-8 */
            Put(cx, rx, "lastIndex", 0, true);
            /* steps 9-10 */
            MatchResult result = getMatcherOrNull(cx, rx, s, true);
            /* steps 11-12 */
            Put(cx, rx, "lastIndex", previousLastIndex, true);
            /* step 13 */
            if (result == null) {
                return -1;
            }
            /* step 14 */
            if (result instanceof ScriptObjectMatchResult) {
                // Extract wrapped script object to ensure no ToInteger conversion takes place
                ScriptObject object = ((ScriptObjectMatchResult) result).object;
                return Get(cx, object, "index");
            }
            return result.start();
        }

        /**
         * 21.2.5.10 RegExp.prototype.split (string, limit)
         * 
         * @param cx
         *            the execution context
         * @param thisValue
         *            the function this-value
         * @param string
         *            the string
         * @param limit
         *            the optional split array limit
         * @return the split array object
         */
        @Function(name = "split", arity = 2)
        public static Object split(ExecutionContext cx, Object thisValue, Object string,
                Object limit) {
            /* steps 1-4 */
            RegExpObject rx = thisRegExpValue(cx, thisValue);
            /* step 5 (moved after step 14) */
            /* steps 6-7 */
            String s = ToFlatString(cx, string);
            /* steps 8-9 */
            ExoticArray a = ArrayCreate(cx, 0);
            /* step 10 */
            int lengthA = 0;
            /* step 11 */
            long lim = Type.isUndefined(limit) ? 0x1F_FFFF_FFFF_FFFFL : ToLength(cx, limit);
            /* step 12 */
            int size = s.length();
            /* step 13 */
            int p = 0;
            /* step 14 */
            if (lim == 0) {
                return a;
            }
            /* step 5 */
            MatchState matcher = rx.getRegExpMatcher().matcher(s);
            /* step 15 */
            if (size == 0) {
                if (matcher.find()) {
                    RegExpConstructor.storeLastMatchResult(cx, s, matcher.toMatchResult());
                    return a;
                }
                CreateDataProperty(cx, a, 0, s);
                return a;
            }
            /* step 16 */
            int q = p;
            int lastStart = -1;
            while (q != size) {
                boolean match = matcher.find();
                if (!match) {
                    break;
                }
                RegExpConstructor.storeLastMatchResult(cx, s, matcher.toMatchResult());
                int e = matcher.end();
                if (e == p) {
                    q = q + 1;
                } else {
                    String t = s.substring(p, lastStart = matcher.start());
                    CreateDataProperty(cx, a, lengthA, t);
                    lengthA += 1;
                    if (lengthA == lim) {
                        return a;
                    }
                    p = e;
                    Iterator<String> iterator = groupIterator(matcher, matcher.groupCount());
                    while (iterator.hasNext()) {
                        String cap = iterator.next();
                        CreateDataProperty(cx, a, lengthA, cap != null ? cap : UNDEFINED);
                        lengthA += 1;
                        if (lengthA == lim) {
                            return a;
                        }
                    }
                    q = p;
                }
            }
            if (lastStart == size) {
                return a;
            }
            /* step 18 */
            String t = s.substring(p, size);
            /* steps 19-20 */
            CreateDataProperty(cx, a, lengthA, t);
            /* step 21 */
            return a;
        }

        /**
         * 21.2.5.15 RegExp.prototype.@@isRegExp
         */
        @Value(name = "[Symbol.isRegExp]", symbol = BuiltinSymbol.isRegExp,
                attributes = @Attributes(writable = false, enumerable = false, configurable = true))
        public static final boolean isRegExp = true;

    }

    /**
     * B.2.5 Additional Properties of the RegExp.prototype Object
     */
    @CompatibilityExtension(CompatibilityOption.RegExpPrototype)
    public enum AdditionalProperties {
        ;

        /**
         * B.2.5.1 RegExp.prototype.compile (pattern, flags )
         * 
         * @param cx
         *            the execution context
         * @param thisValue
         *            the function this-value
         * @param pattern
         *            the regular expression pattern
         * @param flags
         *            the regular expression flags
         * @return the regular expression object
         */
        @Function(name = "compile", arity = 2)
        public static Object compile(ExecutionContext cx, Object thisValue, Object pattern,
                Object flags) {
            /* step 1 (omitted) */
            /* step 2 */
            if (!(thisValue instanceof RegExpObject)) {
                throw newTypeError(cx, Messages.Key.IncompatibleObject);
            }
            RegExpObject r = (RegExpObject) thisValue;
            // FIXME: spec issue - delay extensible check after side effects?
            /*
             * re = /abc/;
             * re.compile({
             *   toString() {
             *     re.compile("def");
             *     Object.preventExtensions(re);
             *     return "ghi";
             *   }
             * });
             */
            /* step 3 */
            boolean extensible = IsExtensible(cx, r);
            /* step 4 */
            if (!extensible) {
                throw newTypeError(cx, Messages.Key.NotExtensible);
            }
            Object p, f;
            if (pattern instanceof RegExpObject) {
                /* step 5 */
                RegExpObject rx = (RegExpObject) pattern;
                if (!rx.isInitialized()) {
                    throw newTypeError(cx, Messages.Key.UninitializedObject);
                }
                if (!Type.isUndefined(flags)) {
                    throw newTypeError(cx, Messages.Key.NotUndefined);
                }
                p = rx.getOriginalSource();
                f = rx.getOriginalFlags();
            } else {
                /* step 6 */
                p = pattern;
                f = flags;
            }
            /* step 7 */
            return RegExpInitialize(cx, r, p, f);
        }
    }

    /**
     * 21.2.5.2.1 Runtime Semantics: RegExpExec ( R, S ) Abstract Operation
     * 
     * @param cx
     *            the execution context
     * @param r
     *            the regular expression object
     * @param s
     *            the string
     * @return the match result object or null
     */
    public static ScriptObject RegExpExec(ExecutionContext cx, ScriptObject r, String s) {
        /* steps 1-2 (not applicable) */
        /* steps 3-4 */
        Object exec = Get(cx, r, "exec");
        /* step 5 */
        // Don't take the slow path for built-in RegExp.prototype.exec
        if (IsCallable(exec) && !isBuiltinExec(cx, exec)) {
            return RegExpUserExec(cx, (Callable) exec, r, s);
        }
        /* steps 6-7 */
        RegExpObject rx = thisRegExpValue(cx, r);
        /* step 8 */
        return RegExpBuiltinExec(cx, rx, s);
    }

    /**
     * 21.2.5.2.1 Runtime Semantics: RegExpExec ( R, S ) Abstract Operation (1)
     * 
     * @param cx
     *            the execution context
     * @param r
     *            the regular expression object
     * @param s
     *            the string
     * @return the match result object or null
     */
    private static MatchResult getMatcherOrNull(ExecutionContext cx, ScriptObject r, String s,
            boolean storeResult) {
        /* steps 1-2 (not applicable) */
        /* steps 3-4 */
        Object exec = Get(cx, r, "exec");
        /* step 5 */
        // Don't take the slow path for built-in RegExp.prototype.exec
        if (IsCallable(exec) && !isBuiltinExec(cx, exec)) {
            ScriptObject o = RegExpUserExec(cx, (Callable) exec, r, s);
            return o != null ? new ScriptObjectMatchResult(cx, o) : null;
        }
        /* steps 6-7 */
        RegExpObject rx = thisRegExpValue(cx, r);
        /* step 8 */
        MatchResult m = getMatcherOrNull(cx, rx, s);
        if (m == null) {
            return null;
        }
        if (storeResult) {
            RegExpConstructor.storeLastMatchResult(cx, s, m);
        }
        return m;
    }

    private static boolean isBuiltinExec(ExecutionContext cx, Object exec) {
        return exec instanceof NativeFunction
                && ((NativeFunction) exec).getId() == NativeFunctionId.RegExpPrototypeExec
                && ((NativeFunction) exec).getRealm() == cx.getRealm();
    }

    private static ScriptObject RegExpUserExec(ExecutionContext cx, Callable exec, ScriptObject r,
            String s) {
        /* steps 5.a-5.b */
        Object result = ((Callable) exec).call(cx, r, s);
        /* step 5.c */
        if (!Type.isObjectOrNull(result)) {
            throw newTypeError(cx, Messages.Key.NotObjectOrNull);
        }
        /* step 5.d */
        return Type.objectValueOrNull(result);
    }

    /**
     * 21.2.5.2.2 Runtime Semantics: RegExpBuiltinExec ( R, S ) Abstract Operation
     * 
     * @param cx
     *            the execution context
     * @param r
     *            the regular expression object
     * @param s
     *            the string
     * @return the match result object or null
     */
    private static ExoticArray RegExpBuiltinExec(ExecutionContext cx, RegExpObject r, String s) {
        /* steps 1-19 */
        MatchResult m = getMatcherOrNull(cx, r, s);
        if (m == null) {
            return null;
        }
        RegExpConstructor.storeLastMatchResult(cx, s, m);
        /* steps 20-30 */
        return toMatchResult(cx, s, m);
    }

    /**
     * 21.2.5.2.2 Runtime Semantics: RegExpBuiltinExec ( R, S ) Abstract Operation (1)
     * 
     * @param cx
     *            the execution context
     * @param r
     *            the regular expression object
     * @param s
     *            the string
     * @return the match result or {@code null}
     */
    private static MatchResult getMatcherOrNull(ExecutionContext cx, RegExpObject r, String s) {
        /* step 1 */
        assert r.isInitialized();
        /* step 2 (not applicable) */
        /* step 3 */
        int length = s.length();
        /* step 4 */
        Object lastIndex = Get(cx, r, "lastIndex");
        /* steps 5-6 */
        double i = ToInteger(cx, lastIndex);
        /* steps 7-8 */
        boolean global = ToBoolean(Get(cx, r, "global"));
        /* steps 9-10 */
        boolean sticky = ToBoolean(Get(cx, r, "sticky"));
        /* step 11 */
        if (!global && !sticky) {
            i = 0;
        }
        /* step 16.a */
        if (i < 0 || i > length) {
            Put(cx, r, "lastIndex", 0, true);
            return null;
        }
        /* step 12 */
        RegExpMatcher matcher = r.getRegExpMatcher();
        /* steps 13-14 (not applicable) */
        /* steps 15-16 */
        MatchState m = matcher.matcher(s);
        boolean matchSucceeded;
        if (!sticky) {
            matchSucceeded = m.find((int) i);
        } else {
            matchSucceeded = m.matches((int) i);
        }
        /* step 16.a, 16.c */
        if (!matchSucceeded) {
            Put(cx, r, "lastIndex", 0, true);
            return null;
        }
        /* steps 17-18 */
        int e = m.end();
        /* step 19 */
        if (global || sticky) {
            Put(cx, r, "lastIndex", e, true);
        }
        return m.toMatchResult();
    }

    /**
     * 21.2.5.2.2 Runtime Semantics: RegExpBuiltinExec ( R, S ) Abstract Operation (2)
     * 
     * @param cx
     *            the execution context
     * @param s
     *            the string
     * @param m
     *            the match result
     * @return the match result script object
     */
    private static ExoticArray toMatchResult(ExecutionContext cx, String s, MatchResult m) {
        /* steps 17-18 */
        int e = m.end();
        /* step 20 */
        int n = m.groupCount();
        /* step 21 */
        ExoticArray array = ArrayCreate(cx, n + 1);
        /* step 22 (omitted) */
        /* step 23 */
        int matchIndex = m.start();
        /* steps 24-26 */
        CreateDataProperty(cx, array, "index", matchIndex);
        CreateDataProperty(cx, array, "input", s);
        /* step 27 */
        String matchedSubstr = s.substring(matchIndex, e);
        /* step 28 */
        CreateDataProperty(cx, array, 0, matchedSubstr);
        /* step 29 */
        Iterator<String> iterator = groupIterator(m, n);
        for (int i = 1; iterator.hasNext(); ++i) {
            String capture = iterator.next();
            CreateDataProperty(cx, array, i, (capture != null ? capture : UNDEFINED));
        }
        /* step 30 */
        return array;
    }

    private static Object[] GetReplacerArguments(MatchResult matchResult, String string,
            String matched, int position) {
        int groupCount = matchResult.groupCount();
        Object[] arguments = new Object[groupCount + 3];
        arguments[0] = matched;
        Iterator<String> iterator = groupIterator(matchResult, groupCount);
        for (int i = 1; iterator.hasNext(); ++i) {
            String group = iterator.next();
            arguments[i] = (group != null ? group : UNDEFINED);
        }
        arguments[groupCount + 1] = position;
        arguments[groupCount + 2] = string;
        return arguments;
    }

    /**
     * 21.1.3.14.1 Runtime Semantics: GetReplaceSubstitution Abstract Operation
     * 
     * @param matched
     *            the matched substring
     * @param string
     *            the input string
     * @param position
     *            the match position
     * @param captures
     *            the captured groups
     * @param replacement
     *            the replace string
     * @return the replacement string
     */
    private static String GetReplaceSubstitution(String matched, String string, int position,
            String[] captures, String replacement) {
        /* step 1 (not applicable) */
        /* step 2 */
        int matchLength = matched.length();
        /* step 3 (not applicable) */
        /* step 4 */
        int stringLength = string.length();
        /* step 5 */
        assert position >= 0;
        /* step 6 */
        assert position <= stringLength;
        /* steps 7-8 (not applicable) */
        /* step 9 */
        int tailPos = Math.min(position + matchLength, stringLength);
        /* step 10 */
        int m = captures.length;
        /* step 11 */
        StringBuilder result = new StringBuilder();
        for (int cursor = 0, len = replacement.length(); cursor < len;) {
            char c = replacement.charAt(cursor++);
            if (c == '$' && cursor < len) {
                c = replacement.charAt(cursor++);
                switch (c) {
                case '0':
                case '1':
                case '2':
                case '3':
                case '4':
                case '5':
                case '6':
                case '7':
                case '8':
                case '9': {
                    int n = c - '0';
                    if (cursor < len) {
                        char d = replacement.charAt(cursor);
                        if (d >= (n == 0 ? '1' : '0') && d <= '9') {
                            int nn = n * 10 + (d - '0');
                            if (nn <= m) {
                                cursor += 1;
                                n = nn;
                            }
                        }
                    }
                    if (n == 0 || n > m) {
                        assert n >= 0 && n <= 9;
                        result.append('$').append(c);
                    } else {
                        assert n >= 1 && n <= 99;
                        String capture = captures[n - 1];
                        if (capture != null) {
                            result.append(capture);
                        }
                    }
                    break;
                }
                case '&':
                    result.append(matched);
                    break;
                case '`':
                    result.append(string, 0, position);
                    break;
                case '\'':
                    result.append(string, tailPos, stringLength);
                    break;
                case '$':
                    result.append('$');
                    break;
                default:
                    result.append('$').append(c);
                    break;
                }
            } else {
                result.append(c);
            }
        }
        /* step 12 */
        return result.toString();
    }

    /**
     * Internal {@code RegExp.prototype.test()} function.
     * 
     * @param cx
     *            the execution context
     * @param rx
     *            the regular expression object
     * @param string
     *            the string
     * @return the result string
     */
    public static boolean RegExpTest(ExecutionContext cx, RegExpObject rx, String string) {
        if (!rx.isInitialized()) {
            throw newTypeError(cx, Messages.Key.UninitializedObject);
        }
        int lastIndex = 0;
        return getMatcherOrNull(rx, string, lastIndex) != null;
    }

    /**
     * Internal {@code RegExp.prototype.replace()} function.
     * 
     * @param cx
     *            the execution context
     * @param rx
     *            the regular expression object
     * @param string
     *            the string
     * @param replaceValue
     *            the replacement string
     * @return the result string
     */
    public static String RegExpReplace(ExecutionContext cx, RegExpObject rx, String string,
            String replaceValue) {
        if (!rx.isInitialized()) {
            throw newTypeError(cx, Messages.Key.UninitializedObject);
        }
        /* steps 1-5 (not applicable) */
        /* step 6 */
        int lengthS = string.length();
        /* steps 7-8 (not applicable) */
        /* steps 9-10 */
        boolean global = rx.getOriginalFlags().indexOf('g') != -1;
        /* step 11 */
        int lastIndex = 0;
        /* steps 12-14 (not applicable) */
        /* step 16 */
        StringBuilder accumulatedResult = new StringBuilder();
        /* step 17 */
        int nextSrcPosition = 0;
        /* step 18 (omitted) */
        /* steps 15, 19 */
        do {
            /* steps 15.a-15.b */
            MatchResult result = getMatcherOrNull(rx, string, lastIndex);
            /* step 15.c */
            if (result == null) {
                break;
            }
            /* step 15.d */
            String matched = result.group(0);
            int matchLength = matched.length();
            lastIndex = (matchLength > 0 ? result.end() : result.end() + 1);
            /* step 15.e (not applicable) */
            /* step 19 */
            int position = result.start();
            assert 0 <= position && position < lengthS;
            assert position >= nextSrcPosition;
            String[] captures = groups(result);
            String replacement = GetReplaceSubstitution(matched, string, position, captures,
                    replaceValue);
            accumulatedResult.append(string, nextSrcPosition, position).append(replacement);
            nextSrcPosition = position + matchLength;
        } while (global);
        /* step 20 (not applicable) */
        assert nextSrcPosition <= lengthS;
        /* step 21 */
        return accumulatedResult.append(string, nextSrcPosition, lengthS).toString();
    }

    private static MatchResult getMatcherOrNull(RegExpObject r, String s, int lastIndex) {
        /* step 1 */
        assert r.isInitialized();
        /* steps 2-6 */
        /* steps 7-8 */
        boolean global = r.getOriginalFlags().indexOf('g') != -1;
        /* steps 9-10 */
        boolean sticky = r.getOriginalFlags().indexOf('y') != -1;
        /* steps 11-16 */
        MatchState m = getMatcherOrNull(r, s, lastIndex, global, sticky);
        /* step 16.a, 16.c */
        if (m == null) {
            return null;
        }
        return m.toMatchResult();
    }

    private static MatchState getMatcherOrNull(RegExpObject r, String s, int lastIndex,
            boolean global, boolean sticky) {
        /* steps 11, 16.a */
        int i;
        if (global || sticky) {
            /* step 16.a */
            if (lastIndex < 0 || lastIndex > s.length()) {
                return null;
            }
            i = lastIndex;
        } else {
            /* step 11 */
            i = 0;
        }
        /* steps 12-14  */
        RegExpMatcher matcher = r.getRegExpMatcher();
        /* steps 15-16 */
        MatchState m = matcher.matcher(s);
        boolean matchSucceeded;
        if (!sticky) {
            matchSucceeded = m.find(i);
        } else {
            matchSucceeded = m.matches(i);
        }
        /* step 16.a, 16.c */
        if (!matchSucceeded) {
            return null;
        }
        return m;
    }

    /**
     * Returns the capturing groups of the {@link MatchResult} argument.
     * 
     * @param matchResult
     *            the match result
     * @return the match groups
     */
    public static String[] groups(MatchResult matchResult) {
        int groupCount = matchResult.groupCount();
        Iterator<String> iterator = groupIterator(matchResult, groupCount);
        String[] groups = new String[groupCount];
        for (int i = 0; iterator.hasNext(); ++i) {
            groups[i] = iterator.next();
        }
        return groups;
    }

    private static Iterator<String> groupIterator(MatchResult matchResult, int groupCount) {
        if (matchResult instanceof IterableMatchResult) {
            return ((IterableMatchResult) matchResult).iterator();
        }
        return new GroupIterator(matchResult, groupCount);
    }

    private static final class GroupIterator implements Iterator<String> {
        private final MatchResult result;
        private final int groupCount;
        private int group = 1;

        GroupIterator(MatchResult result, int groupCount) {
            this.result = result;
            this.groupCount = groupCount;
        }

        @Override
        public boolean hasNext() {
            return group <= groupCount;
        }

        @Override
        public String next() {
            int group = this.group++;
            return result.group(group);
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    private static final class ScriptObjectMatchResult implements MatchResult {
        private final ExecutionContext cx;
        private final ScriptObject object;

        ScriptObjectMatchResult(ExecutionContext cx, ScriptObject object) {
            this.cx = cx;
            this.object = object;
        }

        @Override
        public int start() {
            return start(0);
        }

        @Override
        public int start(int group) {
            if (group == 0) {
                return (int) ToInteger(cx, Get(cx, object, "index"));
            }
            throw new UnsupportedOperationException();
        }

        @Override
        public int end() {
            return end(0);
        }

        @Override
        public int end(int group) {
            return start(group) + group(group).length();
        }

        @Override
        public String group() {
            return group(0);
        }

        @Override
        public String group(int group) {
            Object captured = Get(cx, object, group);
            if (Type.isUndefined(captured)) {
                return null;
            }
            return ToFlatString(cx, captured);
        }

        @Override
        public int groupCount() {
            long len = ToLength(cx, Get(cx, object, "length"));
            return (int) Math.max(len - 1, 0);
        }
    }
}
