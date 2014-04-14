/**
 * Copyright (c) 2012-2014 André Bargull
 * Alle Rechte vorbehalten / All Rights Reserved.  Use is subject to license terms.
 *
 * <https://github.com/anba/es6draft>
 */
package com.github.anba.es6draft.ast;

/**
 * <h1>12 ECMAScript Language: Expressions</h1><br>
 * <h2>12.13 Assignment Operators</h2>
 * <ul>
 * <li>12.13.1 Destructuring Assignment
 * </ul>
 */
public final class AssignmentElement extends AstNode implements AssignmentElementItem {
    private final LeftHandSideExpression target;
    private final Expression initializer;

    public AssignmentElement(long beginPosition, long endPosition, LeftHandSideExpression target,
            Expression initializer) {
        super(beginPosition, endPosition);
        this.target = target;
        this.initializer = initializer;
    }

    public LeftHandSideExpression getTarget() {
        return target;
    }

    public Expression getInitializer() {
        return initializer;
    }

    @Override
    public <R, V> R accept(NodeVisitor<R, V> visitor, V value) {
        return visitor.visit(this, value);
    }
}
