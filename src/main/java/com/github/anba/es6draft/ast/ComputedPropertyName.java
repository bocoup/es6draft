/**
 * Copyright (c) 2012-2013 André Bargull
 * Alle Rechte vorbehalten / All Rights Reserved.  Use is subject to license terms.
 *
 * <https://github.com/anba/es6draft>
 */
package com.github.anba.es6draft.ast;

/**
 * <h1>11 Expressions</h1><br>
 * <h2>11.1 Primary Expressions</h2>
 * <ul>
 * <li>11.1.5 Object Initialiser
 * </ul>
 */
public class ComputedPropertyName extends AstNode implements PropertyName {
    private Expression expression;

    public ComputedPropertyName(Expression expression) {
        this.expression = expression;
    }

    public Expression getExpression() {
        return expression;
    }

    @Override
    public String getName() {
        return null;
    }

    @Override
    public <R, V> R accept(NodeVisitor<R, V> visitor, V value) {
        return visitor.visit(this, value);
    }
}