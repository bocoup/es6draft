/**
 * Copyright (c) 2012-2013 André Bargull
 * Alle Rechte vorbehalten / All Rights Reserved.  Use is subject to license terms.
 *
 * <https://github.com/anba/es6draft>
 */
package com.github.anba.es6draft.runtime.types;

import static com.github.anba.es6draft.runtime.AbstractOperations.SameValue;
import static com.github.anba.es6draft.runtime.types.Undefined.UNDEFINED;

/**
 * <h1>8 Types</h1><br>
 * <h2>8.1 ECMAScript Language Types</h2>
 * <ul>
 * <li>8.1.6.1 Property Attributes
 * </ul>
 */
public final class Property {
    private enum Type {
        DataProperty, AccessorProperty
    }

    private Type type;
    private Object value;
    private Callable getter;
    private Callable setter;
    private boolean writable;
    private boolean enumerable;
    private boolean configurable;

    // package-private for PropertyDescriptor
    Property(PropertyDescriptor original) {
        type = original.isAccessorDescriptor() ? Type.AccessorProperty : Type.DataProperty;
        value = original.getValue();
        getter = original.getGetter();
        setter = original.getSetter();
        writable = original.isWritable();
        enumerable = original.isEnumerable();
        configurable = original.isConfigurable();
    }

    public void toDataProperty() {
        toProperty(Type.DataProperty);
    }

    public void toAccessorProperty() {
        toProperty(Type.AccessorProperty);
    }

    private void toProperty(Type newType) {
        assert type != newType;
        type = newType;
        // default attribute values per 8.1.6.1, table 7
        value = UNDEFINED;
        getter = null;
        setter = null;
        writable = false;
    }

    public void apply(PropertyDescriptor desc) {
        if (isDataDescriptor()) {
            if (desc.hasValue()) {
                value = desc.getValue();
            }
            if (desc.hasWritable()) {
                writable = desc.isWritable();
            }
        } else {
            if (desc.hasGetter()) {
                getter = desc.getGetter();
            }
            if (desc.hasSetter()) {
                setter = desc.getSetter();
            }
        }
        if (desc.hasEnumerable()) {
            enumerable = desc.isEnumerable();
        }
        if (desc.hasConfigurable()) {
            configurable = desc.isConfigurable();
        }
    }

    public PropertyDescriptor toPropertyDescriptor() {
        return new PropertyDescriptor(this);
    }

    /**
     * 8.2.5.1 IsAccessorDescriptor ( Desc )<br>
     * Returns {@code true} if this object is an accessor property descriptor
     */
    public final boolean isAccessorDescriptor() {
        return type == Type.AccessorProperty;
    }

    /**
     * 8.2.5.2 IsDataDescriptor ( Desc )<br>
     * Returns {@code true} if this object is a data property descriptor
     */
    public final boolean isDataDescriptor() {
        return type == Type.DataProperty;
    }

    /**
     * Returns {@code true} if every field of {@code desc} also occurs in this property descriptor
     * and every present field has the same value. That means {@code true} is returned iff
     * {@code desc} &#8838; {@code this} holds.
     */
    public final boolean isSubset(PropertyDescriptor desc) {
        if (isDataDescriptor()) {
            if (desc.hasValue() && !SameValue(desc.getValue(), value)) {
                return false;
            }
            if (desc.hasWritable() && desc.isWritable() != writable) {
                return false;
            }
            if (desc.isAccessorDescriptor()) {
                return false;
            }
        } else {
            if (desc.hasGetter() && !SameValue(desc.getGetter(), getter)) {
                return false;
            }
            if (desc.hasSetter() && !SameValue(desc.getSetter(), setter)) {
                return false;
            }
            if (desc.isDataDescriptor()) {
                return false;
            }
        }
        if (desc.hasEnumerable() && desc.isEnumerable() != enumerable) {
            return false;
        }
        if (desc.hasConfigurable() && desc.isConfigurable() != configurable) {
            return false;
        }
        return true;
    }

    /**
     * Returns the <tt>[[Value]]</tt> field
     */
    public final Object getValue() {
        return value;
    }

    /**
     * Returns the <tt>[[Get]]</tt> field
     */
    public final Callable getGetter() {
        return getter;
    }

    /**
     * Returns the <tt>[[Set]]</tt> field
     */
    public final Callable getSetter() {
        return setter;
    }

    /**
     * Returns the <tt>[[Writable]]</tt> field
     */
    public final boolean isWritable() {
        return writable;
    }

    /**
     * Returns the <tt>[[Enumerable]]</tt> field
     */
    public final boolean isEnumerable() {
        return enumerable;
    }

    /**
     * Returns the <tt>[[Configurable]]</tt> field
     */
    public final boolean isConfigurable() {
        return configurable;
    }
}
