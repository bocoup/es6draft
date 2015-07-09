/**
 * Copyright (c) 2012-2015 André Bargull
 * Alle Rechte vorbehalten / All Rights Reserved.  Use is subject to license terms.
 *
 * <https://github.com/anba/es6draft>
 */
package com.github.anba.es6draft.runtime.objects.collection;

import static com.github.anba.es6draft.runtime.AbstractOperations.Get;
import static com.github.anba.es6draft.runtime.AbstractOperations.GetScriptIterator;
import static com.github.anba.es6draft.runtime.AbstractOperations.IsCallable;
import static com.github.anba.es6draft.runtime.internal.Errors.newTypeError;
import static com.github.anba.es6draft.runtime.internal.Properties.createProperties;

import com.github.anba.es6draft.runtime.ExecutionContext;
import com.github.anba.es6draft.runtime.Realm;
import com.github.anba.es6draft.runtime.internal.Initializable;
import com.github.anba.es6draft.runtime.internal.Messages;
import com.github.anba.es6draft.runtime.internal.ObjectAllocator;
import com.github.anba.es6draft.runtime.internal.Properties.Accessor;
import com.github.anba.es6draft.runtime.internal.Properties.Attributes;
import com.github.anba.es6draft.runtime.internal.Properties.Prototype;
import com.github.anba.es6draft.runtime.internal.Properties.Value;
import com.github.anba.es6draft.runtime.internal.ScriptException;
import com.github.anba.es6draft.runtime.internal.ScriptIterator;
import com.github.anba.es6draft.runtime.types.BuiltinSymbol;
import com.github.anba.es6draft.runtime.types.Callable;
import com.github.anba.es6draft.runtime.types.Constructor;
import com.github.anba.es6draft.runtime.types.Intrinsics;
import com.github.anba.es6draft.runtime.types.ScriptObject;
import com.github.anba.es6draft.runtime.types.Type;
import com.github.anba.es6draft.runtime.types.builtins.BuiltinConstructor;

/**
 * <h1>23 Keyed Collection</h1><br>
 * <h2>23.1 Map Objects</h2>
 * <ul>
 * <li>23.1.1 The Map Constructor
 * <li>23.1.2 Properties of the Map Constructor
 * </ul>
 */
public final class MapConstructor extends BuiltinConstructor implements Initializable {
    /**
     * Constructs a new Map constructor function.
     * 
     * @param realm
     *            the realm object
     */
    public MapConstructor(Realm realm) {
        super(realm, "Map", 0);
    }

    @Override
    public void initialize(Realm realm) {
        createProperties(realm, this, Properties.class);
    }

    @Override
    public MapConstructor clone() {
        return new MapConstructor(getRealm());
    }

    /**
     * 23.1.1.1 Map ([ iterable ])
     */
    @Override
    public Object call(ExecutionContext callerContext, Object thisValue, Object... args) {
        ExecutionContext calleeContext = calleeContext();
        /* step 1 */
        throw newTypeError(calleeContext, Messages.Key.InvalidCall, "Map");
    }

    /**
     * 23.1.1.1 Map ([ iterable ])
     */
    @Override
    public MapObject construct(ExecutionContext callerContext, Constructor newTarget,
            Object... args) {
        ExecutionContext calleeContext = calleeContext();
        Object iterable = argument(args, 0);

        /* step 1 (not applicable) */
        /* steps 2-4 */
        MapObject map = OrdinaryCreateFromConstructor(calleeContext, newTarget,
                Intrinsics.MapPrototype, MapObjectAllocator.INSTANCE);

        /* steps 5-7 */
        ScriptIterator<?> iter;
        Callable adder = null;
        if (Type.isUndefinedOrNull(iterable)) {
            iter = null;
        } else {
            Object _adder = Get(calleeContext, map, "set");
            if (!IsCallable(_adder)) {
                throw newTypeError(calleeContext, Messages.Key.PropertyNotCallable, "set");
            }
            adder = (Callable) _adder;
            iter = GetScriptIterator(calleeContext, iterable);
        }
        /* step 8 */
        if (iter == null) {
            return map;
        }
        /* step 9 */
        try {
            while (iter.hasNext()) {
                Object nextItem = iter.next();
                if (!Type.isObject(nextItem)) {
                    throw newTypeError(calleeContext, Messages.Key.NotObjectType);
                }
                ScriptObject item = Type.objectValue(nextItem);
                Object k = Get(calleeContext, item, 0);
                Object v = Get(calleeContext, item, 1);
                adder.call(calleeContext, map, k, v);
            }
            return map;
        } catch (ScriptException e) {
            iter.close(e);
            throw e;
        }
    }

    private static final class MapObjectAllocator implements ObjectAllocator<MapObject> {
        static final ObjectAllocator<MapObject> INSTANCE = new MapObjectAllocator();

        @Override
        public MapObject newInstance(Realm realm) {
            return new MapObject(realm);
        }
    }

    /**
     * 23.1.2 Properties of the Map Constructor
     */
    public enum Properties {
        ;

        @Prototype
        public static final Intrinsics __proto__ = Intrinsics.FunctionPrototype;

        @Value(name = "length", attributes = @Attributes(writable = false, enumerable = false,
                configurable = true))
        public static final int length = 0;

        @Value(name = "name", attributes = @Attributes(writable = false, enumerable = false,
                configurable = true))
        public static final String name = "Map";

        /**
         * 23.1.2.1 Map.prototype
         */
        @Value(name = "prototype", attributes = @Attributes(writable = false, enumerable = false,
                configurable = false))
        public static final Intrinsics prototype = Intrinsics.MapPrototype;

        /**
         * 23.1.2.2 get Map [ @@species ]
         * 
         * @param cx
         *            the execution context
         * @param thisValue
         *            the function this-value
         * @return the species object
         */
        @Accessor(name = "get [Symbol.species]", symbol = BuiltinSymbol.species,
                type = Accessor.Type.Getter)
        public static Object species(ExecutionContext cx, Object thisValue) {
            /* step 1 */
            return thisValue;
        }
    }
}
