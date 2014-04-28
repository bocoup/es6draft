/**
 * Copyright (c) 2012-2014 André Bargull
 * Alle Rechte vorbehalten / All Rights Reserved.  Use is subject to license terms.
 *
 * <https://github.com/anba/es6draft>
 */
package com.github.anba.es6draft.runtime.types.builtins;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;

import com.github.anba.es6draft.runtime.ExecutionContext;
import com.github.anba.es6draft.runtime.Realm;
import com.github.anba.es6draft.runtime.internal.TailCallInvocation;

/**
 * <h1>9 Ordinary and Exotic Objects Behaviours</h1>
 * <ul>
 * <li>9.3 Built-in Function Objects
 * </ul>
 */
public final class NativeTailCallFunction extends BuiltinFunction {
    // (ExecutionContext, Object, Object[]) -> Object
    private final MethodHandle mh;

    // (Object, Object[]) -> Object
    private final MethodHandle tmh;

    public NativeTailCallFunction(Realm realm, String name, int arity, MethodHandle mh) {
        this(realm, name, tailCallAdapter(mh), mh);
        createDefaultFunctionProperties(name, arity);
    }

    private NativeTailCallFunction(Realm realm, String name, MethodHandle mh, MethodHandle tmh) {
        super(realm, name);
        this.mh = mh;
        this.tmh = tmh;
    }

    private static MethodHandle tailCallAdapter(MethodHandle mh) {
        mh = MethodHandles.dropArguments(mh, 0, ExecutionContext.class);

        MethodHandle result = TailCallInvocation.getTailCallHandler();
        result = MethodHandles.dropArguments(result, 2, Object.class, Object[].class);
        result = MethodHandles.foldArguments(result, mh);
        return result;
    }

    /**
     * Returns `(ExecutionContext, Object, Object[]) {@literal ->} Object` method-handle
     * 
     * @return the call method handle
     */
    public MethodHandle getCallMethod() {
        return mh;
    }

    /**
     * 9.3.1 [[Call]] (thisArgument, argumentsList)
     */
    @Override
    public Object call(ExecutionContext callerContext, Object thisValue, Object... args) {
        try {
            return mh.invokeExact(callerContext, thisValue, args);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 9.3.1 [[Call]] (thisArgument, argumentsList)
     */
    @Override
    public Object tailCall(ExecutionContext callerContext, Object thisValue, Object... args)
            throws Throwable {
        return tmh.invokeExact(thisValue, args);
    }

    @Override
    public NativeTailCallFunction clone() {
        return new NativeTailCallFunction(getRealm(), getName(), mh, tmh);
    }
}
