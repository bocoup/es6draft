/**
 * Copyright (c) 2012-2014 André Bargull
 * Alle Rechte vorbehalten / All Rights Reserved.  Use is subject to license terms.
 *
 * <https://github.com/anba/es6draft>
 */
package com.github.anba.es6draft.runtime.objects.promise;

import static com.github.anba.es6draft.runtime.AbstractOperations.*;
import static com.github.anba.es6draft.runtime.internal.Errors.newTypeError;
import static com.github.anba.es6draft.runtime.types.Undefined.UNDEFINED;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import com.github.anba.es6draft.runtime.ExecutionContext;
import com.github.anba.es6draft.runtime.Realm;
import com.github.anba.es6draft.runtime.Task;
import com.github.anba.es6draft.runtime.internal.Messages;
import com.github.anba.es6draft.runtime.internal.ScriptException;
import com.github.anba.es6draft.runtime.types.BuiltinSymbol;
import com.github.anba.es6draft.runtime.types.Callable;
import com.github.anba.es6draft.runtime.types.Constructor;
import com.github.anba.es6draft.runtime.types.Intrinsics;
import com.github.anba.es6draft.runtime.types.ScriptObject;
import com.github.anba.es6draft.runtime.types.Type;
import com.github.anba.es6draft.runtime.types.Undefined;
import com.github.anba.es6draft.runtime.types.builtins.BuiltinFunction;

/**
 * <h1>25 Control Abstraction Objects</h1><br>
 * <h2>25.4 Promise Objects</h2>
 * <ul>
 * <li>25.4.1 Promise Abstract Operations
 * <li>25.4.2 Promise Tasks
 * </ul>
 */
public final class PromiseAbstractOperations {
    private PromiseAbstractOperations() {
    }

    public static final class ResolvingFunctions {
        /** [[Resolve]] */
        private final PromiseResolveFunction resolve;

        /** [[Reject]] */
        private final PromiseRejectFunction reject;

        ResolvingFunctions(PromiseResolveFunction resolve, PromiseRejectFunction reject) {
            this.resolve = resolve;
            this.reject = reject;
        }

        /**
         * [[Resolve]]
         *
         * @return the resolve function
         */
        public PromiseResolveFunction getResolve() {
            return resolve;
        }

        /**
         * [[Reject]]
         *
         * @return the reject function
         */
        public PromiseRejectFunction getReject() {
            return reject;
        }
    }

    /**
     * <h2>25.4.1 Promise Abstract Operations</h2>
     * <p>
     * 25.4.1.3 CreateResolvingFunctions ( promise )
     * 
     * @param cx
     *            the execution context
     * @param promise
     *            the promise object
     * @return the resolving functions tuple
     */
    public static ResolvingFunctions CreateResolvingFunctions(ExecutionContext cx,
            PromiseObject promise) {
        /* step 1 */
        AtomicBoolean alreadyResolved = new AtomicBoolean(false);
        /* steps 2-4 */
        PromiseResolveFunction resolve = new PromiseResolveFunction(cx.getRealm(), promise,
                alreadyResolved);
        /* steps 5-7 */
        PromiseRejectFunction reject = new PromiseRejectFunction(cx.getRealm(), promise,
                alreadyResolved);
        /* step 8 */
        return new ResolvingFunctions(resolve, reject);
    }

    /**
     * 25.4.1.3.1 Promise Reject Functions
     */
    public static final class PromiseRejectFunction extends BuiltinFunction {
        /** [[Promise]] */
        private final PromiseObject promise;
        /** [[AlreadyResolved]] */
        private final AtomicBoolean alreadyResolved;

        public PromiseRejectFunction(Realm realm, PromiseObject promise,
                AtomicBoolean alreadyResolved) {
            this(realm, promise, alreadyResolved, null);
            createDefaultFunctionProperties(ANONYMOUS, 1);
        }

        private PromiseRejectFunction(Realm realm, PromiseObject promise,
                AtomicBoolean alreadyResolved, Void ignore) {
            super(realm, ANONYMOUS);
            this.promise = promise;
            this.alreadyResolved = alreadyResolved;
        }

        @Override
        public PromiseRejectFunction clone() {
            return new PromiseRejectFunction(getRealm(), promise, alreadyResolved, null);
        }

        @Override
        public Undefined call(ExecutionContext callerContext, Object thisValue, Object... args) {
            ExecutionContext calleeContext = calleeContext();
            Object reason = args.length > 0 ? args[0] : UNDEFINED;
            /* step 1 (not applicable) */
            /* step 2 */
            PromiseObject promise = this.promise;
            /* steps 3-5 */
            if (!alreadyResolved.compareAndSet(false, true)) {
                return UNDEFINED;
            }
            /* step 6 */
            RejectPromise(calleeContext, promise, reason);
            return UNDEFINED;
        }
    }

    /**
     * 25.4.1.3.2 Promise Resolve Functions
     */
    public static final class PromiseResolveFunction extends BuiltinFunction {
        /** [[Promise]] */
        private final PromiseObject promise;
        /** [[AlreadyResolved]] */
        private final AtomicBoolean alreadyResolved;

        public PromiseResolveFunction(Realm realm, PromiseObject promise,
                AtomicBoolean alreadyResolved) {
            this(realm, promise, alreadyResolved, null);
            createDefaultFunctionProperties(ANONYMOUS, 1);
        }

        private PromiseResolveFunction(Realm realm, PromiseObject promise,
                AtomicBoolean alreadyResolved, Void ignore) {
            super(realm, ANONYMOUS);
            this.promise = promise;
            this.alreadyResolved = alreadyResolved;
        }

        @Override
        public PromiseResolveFunction clone() {
            return new PromiseResolveFunction(getRealm(), promise, alreadyResolved, null);
        }

        @Override
        public Undefined call(ExecutionContext callerContext, Object thisValue, Object... args) {
            ExecutionContext calleeContext = calleeContext();
            Object resolution = args.length > 0 ? args[0] : UNDEFINED;
            /* step 1 (not applicable) */
            /* step 2 */
            PromiseObject promise = this.promise;
            /* steps 3-5 */
            if (!alreadyResolved.compareAndSet(false, true)) {
                return UNDEFINED;
            }
            /* step 6 */
            if (SameValue(resolution, promise)) {
                ScriptException selfResolutionError = newTypeError(calleeContext,
                        Messages.Key.PromiseSelfResolution);
                RejectPromise(calleeContext, promise, selfResolutionError.getValue());
                return UNDEFINED;
            }
            /* step 7 */
            if (!Type.isObject(resolution)) {
                FulfillPromise(calleeContext, promise, resolution);
                return UNDEFINED;
            }
            /* steps 8-10 */
            Object then;
            try {
                then = Get(calleeContext, Type.objectValue(resolution), "then");
            } catch (ScriptException e) {
                /* step 9 */
                RejectPromise(calleeContext, promise, e.getValue());
                return UNDEFINED;
            }
            /* step 11 */
            if (!IsCallable(then)) {
                FulfillPromise(calleeContext, promise, resolution);
                return UNDEFINED;
            }
            /* step 12 */
            ResolvingFunctions resolvingFunctions = CreateResolvingFunctions(calleeContext, promise);
            /* steps 13-14 */
            try {
                ((Callable) then).call(calleeContext, resolution, resolvingFunctions.getResolve(),
                        resolvingFunctions.getReject());
            } catch (ScriptException e) {
                return resolvingFunctions.getReject().call(calleeContext, UNDEFINED, e.getValue());
            }
            /* step 15 */
            return UNDEFINED;
        }
    }

    /**
     * <h2>25.4.1 Promise Abstract Operations</h2>
     * <p>
     * 25.4.1.4 FulfillPromise (promise, value)
     * 
     * @param cx
     *            the execution context
     * @param promise
     *            the promise object
     * @param value
     *            the resolve value
     */
    public static void FulfillPromise(ExecutionContext cx, PromiseObject promise, Object value) {
        List<PromiseReaction> reactions = promise.resolve(value);
        TriggerPromiseReactions(cx, reactions, value);
    }

    /**
     * <h2>25.4.1 Promise Abstract Operations</h2>
     * <p>
     * 25.4.1.5 NewPromiseCapability ( C )
     * 
     * @param cx
     *            the execution context
     * @param c
     *            the promise constructor function
     * @return the new promise capability record
     */
    public static PromiseCapability<ScriptObject> NewPromiseCapability(ExecutionContext cx, Object c) {
        /* step 1 */
        if (!IsConstructor(c)) {
            throw newTypeError(cx, Messages.Key.NotConstructor);
        }
        Constructor constructor = (Constructor) c;
        /* step 2 (not applicable) */
        /* steps 3-4 */
        ScriptObject promise = CreateFromConstructor(cx, constructor);
        // FIXME: spec bug -> throw TypeError if Type(promise)=Undefined
        if (promise == null) {
            throw newTypeError(cx, Messages.Key.NotObjectType);
        }
        /* step 5 */
        return CreatePromiseCapabilityRecord(cx, promise, constructor);
    }

    /**
     * 25.4.1.5.1 CreatePromiseCapabilityRecord( promise, constructor )
     * 
     * @param <PROMISE>
     *            the promise type
     * @param cx
     *            the execution context
     * @param promise
     *            the promise object
     * @param constructor
     *            the promise constructor function
     * @return the new promise capability record
     */
    public static <PROMISE extends ScriptObject> PromiseCapability<PROMISE> CreatePromiseCapabilityRecord(
            ExecutionContext cx, PROMISE promise, Constructor constructor) {
        /* step 1 (not applicable) */
        /* steps 2-3 */
        GetCapabilitiesExecutor executor = new GetCapabilitiesExecutor(cx.getRealm());
        /* steps 4-5 */
        Object constructorResult = constructor.call(cx, promise, executor);
        /* step 6 */
        Object resolve = executor.resolve;
        if (!IsCallable(resolve)) {
            throw newTypeError(cx, Messages.Key.NotCallable);
        }
        /* step 7 */
        Object reject = executor.reject;
        if (!IsCallable(reject)) {
            throw newTypeError(cx, Messages.Key.NotCallable);
        }
        /* step 8 */
        if (Type.isObject(constructorResult) && !SameValue(promise, constructorResult)) {
            throw newTypeError(cx, Messages.Key.IncompatibleObject);
        }
        /* steps 1, 9 */
        return new PromiseCapability<>(promise, (Callable) resolve, (Callable) reject);
    }

    /**
     * 25.4.1.5.2 GetCapabilitiesExecutor Functions
     */
    public static final class GetCapabilitiesExecutor extends BuiltinFunction {
        /** [[Resolve]] */
        private Object resolve = UNDEFINED;

        /** [[Reject]] */
        private Object reject = UNDEFINED;

        public GetCapabilitiesExecutor(Realm realm) {
            super(realm, ANONYMOUS);
            createDefaultFunctionProperties(ANONYMOUS, 2);
        }

        private GetCapabilitiesExecutor(Realm realm, Void ignore) {
            super(realm, ANONYMOUS);
        }

        @Override
        public GetCapabilitiesExecutor clone() {
            return new GetCapabilitiesExecutor(getRealm(), null);
        }

        @Override
        public Undefined call(ExecutionContext callerContext, Object thisValue, Object... args) {
            ExecutionContext calleeContext = calleeContext();
            Object resolve = args.length > 0 ? args[0] : UNDEFINED;
            Object reject = args.length > 1 ? args[1] : UNDEFINED;
            /* step 1 (not applicable) */
            /* step 2 (omitted) */
            /* step 3 */
            if (!Type.isUndefined(this.resolve)) {
                throw newTypeError(calleeContext, Messages.Key.NotUndefined);
            }
            /* step 4 */
            if (!Type.isUndefined(this.reject)) {
                throw newTypeError(calleeContext, Messages.Key.NotUndefined);
            }
            /* step 5 */
            this.resolve = resolve;
            /* step 6 */
            this.reject = reject;
            /* step 7 */
            return UNDEFINED;
        }
    }

    /**
     * <h2>25.4.1 Promise Abstract Operations</h2>
     * <p>
     * 25.4.1.6 IsPromise ( x )
     * 
     * @param x
     *            the object
     * @return {@code true} if <var>x</var> is an initialized promise object
     */
    public static boolean IsPromise(Object x) {
        /* steps 1-2 */
        if (!(x instanceof PromiseObject)) {
            return false;
        }
        /* steps 3-4 */
        return ((PromiseObject) x).getState() != null;
    }

    /**
     * <h2>25.4.1 Promise Abstract Operations</h2>
     * <p>
     * 25.4.1.7 RejectPromise (promise, reason)
     * 
     * @param cx
     *            the execution context
     * @param promise
     *            the promise object
     * @param reason
     *            the rejection reason
     */
    public static void RejectPromise(ExecutionContext cx, PromiseObject promise, Object reason) {
        List<PromiseReaction> reactions = promise.reject(reason);
        TriggerPromiseReactions(cx, reactions, reason);
    }

    /**
     * <h2>25.4.1 Promise Abstract Operations</h2>
     * <p>
     * 25.4.1.8 TriggerPromiseReactions ( reactions, argument )
     * 
     * @param cx
     *            the execution context
     * @param reactions
     *            the list of promise reactions
     * @param argument
     *            the reaction task argument
     */
    public static void TriggerPromiseReactions(ExecutionContext cx,
            List<PromiseReaction> reactions, Object argument) {
        Realm realm = cx.getRealm();
        for (PromiseReaction reaction : reactions) {
            realm.enqueuePromiseTask(new PromiseReactionTask(realm, reaction, argument));
        }
    }

    /**
     * <h2>25.4.2 Promise Tasks</h2>
     * <p>
     * 25.4.2.1 PromiseReactionTask( reaction, argument )
     */
    public static final class PromiseReactionTask implements Task {
        private final Realm realm;
        private final PromiseReaction reaction;
        private final Object argument;

        public PromiseReactionTask(Realm realm, PromiseReaction reaction, Object argument) {
            this.realm = realm;
            this.reaction = reaction;
            this.argument = argument;
        }

        @Override
        public void execute() {
            ExecutionContext cx = realm.defaultContext();
            /* step 1 (not applicable) */
            /* step 2 */
            PromiseCapability<?> promiseCapability = reaction.getCapabilities();
            /* step 3 */
            Callable handler = reaction.getHandler();
            /* steps 4-6 */
            Object handlerResult;
            try {
                handlerResult = handler.call(cx, UNDEFINED, argument);
            } catch (ScriptException e) {
                /* step 5 */
                promiseCapability.getReject().call(cx, UNDEFINED, e.getValue());
                return;
            }
            /* steps 7-8 */
            promiseCapability.getResolve().call(cx, UNDEFINED, handlerResult);
        }
    }

    /* ***************************************************************************************** */

    /**
     * <h2>Modules</h2>
     * <p>
     * PromiseThen ( promise, onFulfilled )
     * 
     * @param cx
     *            the execution context
     * @param promise
     *            the promise object
     * @param onFulfilled
     *            the fulfillment handler
     * @return the new promise object
     */
    public static ScriptObject PromiseThen(ExecutionContext cx, ScriptObject promise,
            Callable onFulfilled) {
        // TODO: make safe
        Object p = PromisePrototype.Properties.newThen(cx, promise, onFulfilled, UNDEFINED);
        assert p instanceof ScriptObject;
        return (ScriptObject) p;
    }

    /**
     * <h2>Modules</h2>
     * <p>
     * PromiseThen ( promise, onFulfilled, onRejected )
     * 
     * @param cx
     *            the execution context
     * @param promise
     *            the promise object
     * @param onFulfilled
     *            the fulfillment handler
     * @param onRejected
     *            the rejection handler
     * @return the new promise object
     */
    public static ScriptObject PromiseThen(ExecutionContext cx, ScriptObject promise,
            Callable onFulfilled, Callable onRejected) {
        // TODO: make safe
        Object p = PromisePrototype.Properties.newThen(cx, promise, onFulfilled, onRejected);
        assert p instanceof ScriptObject;
        return (ScriptObject) p;
    }

    /**
     * <h2>Modules</h2>
     * <p>
     * PromiseCatch ( promise, onRejected )
     * 
     * @param cx
     *            the execution context
     * @param promise
     *            the promise object
     * @param onRejected
     *            the rejection handler
     * @return the new promise object
     */
    public static ScriptObject PromiseCatch(ExecutionContext cx, ScriptObject promise,
            Callable onRejected) {
        // TODO: make safe
        Object p = PromisePrototype.Properties._catch(cx, promise, onRejected);
        assert p instanceof ScriptObject;
        return (ScriptObject) p;
    }

    /**
     * <h2>Modules</h2>
     * <p>
     * PromiseAll ( x )
     * 
     * @param cx
     *            the execution context
     * @param list
     *            the list of promise objects
     * @return the new promise object
     */
    public static ScriptObject PromiseAll(ExecutionContext cx, List<ScriptObject> list) {
        // TODO: make safe
        ScriptObject promiseConstructor = cx.getIntrinsic(Intrinsics.Promise);
        ScriptObject iterator = CreateListIterator(cx, list);
        // Promote Iterator to Iterable
        CreateDataProperty(cx, iterator, BuiltinSymbol.iterator.get(),
                new ConstantFunction<>(cx.getRealm(), iterator));
        Object p = PromiseConstructor.Properties.all(cx, promiseConstructor, iterator);
        assert p instanceof ScriptObject;
        return (ScriptObject) p;
    }

    private static final class ConstantFunction<VALUE> extends BuiltinFunction {
        /** [[ConstantValue]] */
        private final VALUE constantValue;

        public ConstantFunction(Realm realm, VALUE constantValue) {
            this(realm, constantValue, null);
            createDefaultFunctionProperties(ANONYMOUS, 0);
        }

        private ConstantFunction(Realm realm, VALUE constantValue, Void ignore) {
            super(realm, ANONYMOUS);
            this.constantValue = constantValue;
        }

        @Override
        public ConstantFunction<VALUE> clone() {
            return new ConstantFunction<>(getRealm(), constantValue, null);
        }

        @Override
        public VALUE call(ExecutionContext callerContext, Object thisValue, Object... args) {
            return constantValue;
        }
    }
}
