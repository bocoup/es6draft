/**
 * Copyright (c) 2012-2014 André Bargull
 * Alle Rechte vorbehalten / All Rights Reserved.  Use is subject to license terms.
 *
 * <https://github.com/anba/es6draft>
 */
package com.github.anba.es6draft.runtime.objects.collection;

import com.github.anba.es6draft.runtime.Realm;
import com.github.anba.es6draft.runtime.internal.LinkedMap;
import com.github.anba.es6draft.runtime.internal.LinkedMapImpl;
import com.github.anba.es6draft.runtime.types.builtins.OrdinaryObject;

/**
 * <h1>23 Keyed Collection</h1><br>
 * <h2>23.1 Map Objects</h2>
 * <ul>
 * <li>23.1.4 Properties of Map Instances
 * </ul>
 */
public final class MapObject extends OrdinaryObject {
    /** [[MapData]] */
    private LinkedMap<Object, Object> mapData = null;

    /**
     * Constructs a new Map object.
     * 
     * @param realm
     *            the realm object
     */
    public MapObject(Realm realm) {
        super(realm);
    }

    /**
     * [[MapData]]
     * <p>
     * Returns the underlying map data.
     * 
     * @return the underlying map data
     */
    public LinkedMap<Object, Object> getMapData() {
        return mapData;
    }

    /**
     * Initializes this Map instance.
     */
    public void initialize() {
        assert this.mapData == null : "Map already initialized";
        this.mapData = new LinkedMapImpl<Object>();
    }

    /**
     * Returns {@code true} if this Map instance is initialized.
     * 
     * @return {@code true} if this object is initialized
     */
    public boolean isInitialized() {
        return mapData != null;
    }
}
