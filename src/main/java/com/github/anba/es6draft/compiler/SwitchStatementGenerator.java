/**
 * Copyright (c) 2012-2013 André Bargull
 * Alle Rechte vorbehalten / All Rights Reserved.  Use is subject to license terms.
 *
 * <https://github.com/anba/es6draft>
 */
package com.github.anba.es6draft.compiler;

import static com.github.anba.es6draft.semantics.StaticSemantics.LexicalDeclarations;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.objectweb.asm.Label;
import org.objectweb.asm.Type;

import com.github.anba.es6draft.ast.AbruptNode.Abrupt;
import com.github.anba.es6draft.ast.Declaration;
import com.github.anba.es6draft.ast.Expression;
import com.github.anba.es6draft.ast.Node;
import com.github.anba.es6draft.ast.NumericLiteral;
import com.github.anba.es6draft.ast.StatementListItem;
import com.github.anba.es6draft.ast.StringLiteral;
import com.github.anba.es6draft.ast.SwitchClause;
import com.github.anba.es6draft.ast.SwitchStatement;
import com.github.anba.es6draft.compiler.InstructionVisitor.MethodDesc;
import com.github.anba.es6draft.compiler.InstructionVisitor.MethodType;

/**
 *
 */
class SwitchStatementGenerator extends DefaultCodeGenerator<Void, StatementVisitor> {
    private static class Methods {
        // class: CharSequence
        static final MethodDesc CharSequence_charAt = MethodDesc.create(MethodType.Interface,
                Types.CharSequence, "charAt", Type.getMethodType(Type.CHAR_TYPE, Type.INT_TYPE));
        static final MethodDesc CharSequence_length = MethodDesc.create(MethodType.Interface,
                Types.CharSequence, "length", Type.getMethodType(Type.INT_TYPE));
        static final MethodDesc CharSequence_toString = MethodDesc.create(MethodType.Interface,
                Types.CharSequence, "toString", Type.getMethodType(Types.String));

        // class: Number
        static final MethodDesc Number_doubleValue = MethodDesc.create(MethodType.Virtual,
                Types.Number, "doubleValue", Type.getMethodType(Type.DOUBLE_TYPE));

        // class: String
        static final MethodDesc String_equals = MethodDesc.create(MethodType.Virtual, Types.String,
                "equals", Type.getMethodType(Type.BOOLEAN_TYPE, Types.Object));
        static final MethodDesc String_hashCode = MethodDesc.create(MethodType.Virtual,
                Types.String, "hashCode", Type.getMethodType(Type.INT_TYPE));

        // class: ScriptRuntime
        static final MethodDesc ScriptRuntime_strictEqualityComparison = MethodDesc.create(
                MethodType.Static, Types.ScriptRuntime, "strictEqualityComparison",
                Type.getMethodType(Type.BOOLEAN_TYPE, Types.Object, Types.Object));
    }

    public SwitchStatementGenerator(CodeGenerator codegen) {
        super(codegen);
    }

    @Override
    protected Void visit(Node node, StatementVisitor mv) {
        throw new IllegalStateException(String.format("node-class: %s", node.getClass()));
    }

    @Override
    protected Void visit(StatementListItem node, StatementVisitor mv) {
        codegen.statement(node, mv);
        return null;
    }

    @Override
    public Void visit(SwitchClause node, StatementVisitor mv) {
        // see SwitchStatement
        throw new IllegalStateException();
    }

    private enum SwitchType {
        Int, Char, String, Generic;

        private static boolean isIntSwitch(SwitchStatement node) {
            for (SwitchClause switchClause : node.getClauses()) {
                Expression expr = switchClause.getExpression();
                if (expr != null) {
                    if (!(expr instanceof NumericLiteral)) {
                        return false;
                    }
                    double value = ((NumericLiteral) expr).getValue();
                    if (value != (int) value) {
                        return false;
                    }
                }
            }
            return true;
        }

        private static boolean isCharSwitch(SwitchStatement node) {
            for (SwitchClause switchClause : node.getClauses()) {
                Expression expr = switchClause.getExpression();
                if (expr != null) {
                    if (!(expr instanceof StringLiteral)) {
                        return false;
                    }
                    java.lang.String value = ((StringLiteral) expr).getValue();
                    if (value.length() != 1) {
                        return false;
                    }
                }
            }
            return true;
        }

        private static boolean isStringSwitch(SwitchStatement node) {
            for (SwitchClause switchClause : node.getClauses()) {
                Expression expr = switchClause.getExpression();
                if (expr != null && !(expr instanceof StringLiteral)) {
                    return false;
                }
            }
            return true;
        }

        static SwitchType of(SwitchStatement node) {
            List<SwitchClause> clauses = node.getClauses();
            if (clauses.size() == 0 || clauses.size() == 1
                    && clauses.get(0).getExpression() == null) {
                // empty or only default clause -> use generic switch
                return Generic;
            }
            Expression testExpr = clauses.get(0).getExpression();
            if (testExpr == null) {
                testExpr = clauses.get(1).getExpression();
            }
            if (testExpr instanceof NumericLiteral) {
                double value = ((NumericLiteral) testExpr).getValue();
                if (value == (int) value && isIntSwitch(node)) {
                    return Int;
                }
            } else if (testExpr instanceof StringLiteral) {
                java.lang.String value = ((StringLiteral) testExpr).getValue();
                if (value.length() == 1 && isCharSwitch(node)) {
                    return Char;
                }
                if (isStringSwitch(node)) {
                    return String;
                }
            }
            return Generic;
        }
    }

    @Override
    public Void visit(SwitchStatement node, StatementVisitor mv) {
        int savedEnv = saveEnvironment(node, mv);

        // stack -> switchValue
        ValType expressionValueType = expressionValue(node.getExpression(), mv);
        mv.toBoxed(expressionValueType);

        int switchValue = mv.newVariable(Types.Object);
        mv.store(switchValue, Types.Object);

        mv.enterScope(node);
        Collection<Declaration> declarations = LexicalDeclarations(node);
        if (!declarations.isEmpty()) {
            newDeclarativeEnvironment(mv);
            new BlockDeclarationInstantiationGenerator(codegen).generate(declarations, mv);
            pushLexicalEnvironment(mv);
        }

        Label lblBreak = new Label();
        Label[] labels;
        SwitchType type = SwitchType.of(node);
        if (type == SwitchType.Int) {
            labels = emitIntSwitch(node.getClauses(), lblBreak, switchValue, mv);
        } else if (type == SwitchType.Char) {
            labels = emitCharSwitch(node.getClauses(), lblBreak, switchValue, mv);
        } else if (type == SwitchType.String) {
            labels = emitStringSwitch(node.getClauses(), lblBreak, switchValue, mv);
        } else {
            labels = emitGenericSwitch(node.getClauses(), lblBreak, switchValue, mv);
        }

        mv.enterBreakable(node, lblBreak);
        int index = 0;
        for (SwitchClause switchClause : node.getClauses()) {
            mv.mark(labels[index++]);
            for (StatementListItem stmt : switchClause.getStatements()) {
                stmt.accept(this, mv);
            }
        }
        mv.exitBreakable(node);

        if (!declarations.isEmpty()) {
            popLexicalEnvironment(mv);
        }
        mv.exitScope();

        mv.mark(lblBreak);
        restoreEnvironment(node, Abrupt.Break, savedEnv, mv);
        freeVariable(savedEnv, mv);

        return null;
    }

    /**
     * <h3>Generic-switch</h3>
     * 
     * <pre>
     * switch (v) {
     * case key1: ...
     * case key2: ...
     * }
     * 
     * var $v = v;
     * if (strictEquals($v, key1)) goto L1
     * if (strictEquals($v, key2)) goto L2
     * L1: ...
     * L2: ...
     * </pre>
     */
    private Label[] emitGenericSwitch(List<SwitchClause> clauses, Label lblBreak, int switchValue,
            StatementVisitor mv) {
        Label defaultClause = null;
        int index = 0;
        Label[] labels = new Label[clauses.size()];
        for (SwitchClause switchClause : clauses) {
            Label stmtLabel = labels[index++] = new Label();
            Expression expr = switchClause.getExpression();
            if (expr == null) {
                assert defaultClause == null;
                defaultClause = stmtLabel;
            } else {
                mv.load(switchValue, Types.Object);
                ValType type = expressionValue(expr, mv);
                mv.toBoxed(type);
                mv.invoke(Methods.ScriptRuntime_strictEqualityComparison);
                mv.ifne(stmtLabel);
            }
        }

        mv.freeVariable(switchValue);

        if (defaultClause != null) {
            mv.goTo(defaultClause);
        } else {
            mv.goTo(lblBreak);
        }

        return labels;
    }

    /**
     * <h3>String-switch</h3>
     * 
     * <pre>
     * switch (v) {
     * case "key1": ...
     * case "key2": ...
     * }
     * 
     * var $v = v;
     * if (typeof $v == 'string') {
     *   lookupswitch(hashCode($v)) {
     *     hashCode("key1"): goto L1
     *     hashCode("key2"): goto L2
     *   }
     *   L1: if (equals($v, "key1")) ...
     *   L2: if (equals($v, "key2")) ...
     * }
     * </pre>
     */
    private Label[] emitStringSwitch(List<SwitchClause> clauses, Label lblBreak, int switchValue,
            StatementVisitor mv) {
        Label defaultClause = null;
        long[] values = new long[clauses.size()];
        Label[] labels = new Label[clauses.size()];
        for (int i = 0, j = 0, size = clauses.size(); i < size; ++i) {
            Label stmtLabel = labels[i] = new Label();
            SwitchClause switchClause = clauses.get(i);
            Expression expr = switchClause.getExpression();
            if (expr == null) {
                assert defaultClause == null;
                defaultClause = stmtLabel;
            } else {
                int value = ((StringLiteral) expr).getValue().hashCode();
                values[j++] = ((long) value) << 32 | i;
            }
        }

        int valuesLength = values.length - (defaultClause != null ? 1 : 0);
        Label switchDefault = defaultClause != null ? defaultClause : lblBreak;

        // test for string-ness: type is java.lang.CharSequence
        mv.load(switchValue, Types.Object);
        mv.instanceOf(Types.CharSequence);
        mv.ifeq(switchDefault);

        int switchValueString = mv.newVariable(Types.String);
        mv.load(switchValue, Types.Object);
        mv.checkcast(Types.CharSequence);
        mv.invoke(Methods.CharSequence_toString);
        mv.dup();
        mv.store(switchValueString, Types.String);
        mv.invoke(Methods.String_hashCode);

        mv.freeVariable(switchValue);

        int distinctValues = distinctValues(values, valuesLength);
        Label[] switchLabels = new Label[distinctValues];
        int[] switchKeys = new int[distinctValues];
        for (int i = 0, j = 0, lastValue = 0; i < valuesLength; ++i) {
            int value = (int) (values[i] >> 32);
            if (i == 0 || value != lastValue) {
                switchLabels[j] = new Label();
                switchKeys[j] = value;
                j += 1;
            }
            lastValue = value;
        }

        // emit lookupswitch
        mv.lookupswitch(switchDefault, switchKeys, switchLabels);

        // add String.equals() calls
        for (int i = 0, j = 0, lastValue = 0; i < valuesLength; ++i) {
            int value = (int) (values[i] >> 32);
            int index = (int) (values[i]);
            if (i == 0 || value != lastValue) {
                if (i != 0) {
                    mv.goTo(switchDefault);
                }
                mv.mark(switchLabels[j++]);
            }
            String string = ((StringLiteral) clauses.get(index).getExpression()).getValue();
            mv.load(switchValueString, Types.String);
            mv.aconst(string);
            mv.invoke(Methods.String_equals);
            mv.ifne(labels[index]);
            lastValue = value;
        }
        mv.goTo(switchDefault);

        mv.freeVariable(switchValueString);

        return labels;
    }

    /**
     * <h3>char-switch</h3>
     * 
     * <pre>
     * switch (v) {
     * case "a": ...
     * case "b": ...
     * }
     * 
     * var $v = v;
     * if (typeof $v == 'string' && length($v) == 1) {
     *   tableswitch|lookupswitch(charCodeAt($v, 0)) {
     *     charCodeAt("a", 0): goto L1
     *     charCodeAt("b", 0): goto L2
     *   }
     *   L1: ...
     *   L2: ...
     * }
     * </pre>
     */
    private Label[] emitCharSwitch(List<SwitchClause> clauses, Label lblBreak, int switchValue,
            StatementVisitor mv) {
        Label defaultClause = null;
        long[] values = new long[clauses.size()];
        Label[] labels = new Label[clauses.size()];
        for (int i = 0, j = 0, size = clauses.size(); i < size; ++i) {
            Label stmtLabel = labels[i] = new Label();
            SwitchClause switchClause = clauses.get(i);
            Expression expr = switchClause.getExpression();
            if (expr == null) {
                assert defaultClause == null;
                defaultClause = stmtLabel;
            } else {
                int value = ((StringLiteral) expr).getValue().charAt(0);
                values[j++] = ((long) value) << 32 | i;
            }
        }

        int valuesLength = values.length - (defaultClause != null ? 1 : 0);
        Label switchDefault = defaultClause != null ? defaultClause : lblBreak;

        // test for char-ness: type is java.lang.CharSequence
        mv.load(switchValue, Types.Object);
        mv.instanceOf(Types.CharSequence);
        mv.ifeq(switchDefault);

        // test for char-ness: value is char (string with only one character)
        int switchValueChar = mv.newVariable(Types.CharSequence);
        mv.load(switchValue, Types.Object);
        mv.checkcast(Types.CharSequence);
        mv.dup();
        mv.store(switchValueChar, Types.CharSequence);
        mv.invoke(Methods.CharSequence_length);
        mv.iconst(1);
        mv.ificmpne(switchDefault);

        mv.load(switchValueChar, Types.CharSequence);
        mv.iconst(0);
        mv.invoke(Methods.CharSequence_charAt);
        mv.cast(Type.CHAR_TYPE, Type.INT_TYPE);

        mv.freeVariable(switchValueChar);
        mv.freeVariable(switchValue);

        // emit tableswitch or lookupswitch
        switchInstruction(switchDefault, labels, values, valuesLength, mv);

        return labels;
    }

    /**
     * <h3>int-switch</h3>
     * 
     * <pre>
     * switch (v) {
     * case 0: ...
     * case 1: ...
     * }
     * 
     * var $v = v;
     * if (typeof $v == 'number' && isInt($v)) {
     *   tableswitch|lookupswitch(int($v)) {
     *     int(0): goto L1
     *     int(1): goto L2
     *   }
     *   L1: ...
     *   L2: ...
     * }
     * </pre>
     */
    private Label[] emitIntSwitch(List<SwitchClause> clauses, Label lblBreak, int switchValue,
            StatementVisitor mv) {
        Label defaultClause = null;
        long[] values = new long[clauses.size()];
        Label[] labels = new Label[clauses.size()];
        for (int i = 0, j = 0, size = clauses.size(); i < size; ++i) {
            Label stmtLabel = labels[i] = new Label();
            SwitchClause switchClause = clauses.get(i);
            Expression expr = switchClause.getExpression();
            if (expr == null) {
                assert defaultClause == null;
                defaultClause = stmtLabel;
            } else {
                int value = ((NumericLiteral) expr).getValue().intValue();
                values[j++] = ((long) value) << 32 | i;
            }
        }

        int valuesLength = values.length - (defaultClause != null ? 1 : 0);
        Label switchDefault = defaultClause != null ? defaultClause : lblBreak;

        // test for int-ness: type is java.lang.Number
        mv.load(switchValue, Types.Object);
        mv.instanceOf(Types.Number);
        mv.ifeq(switchDefault);

        // test for int-ness: value is integer
        int switchValueNum = mv.newVariable(Type.DOUBLE_TYPE);
        mv.load(switchValue, Types.Object);
        mv.checkcast(Types.Number);
        mv.invoke(Methods.Number_doubleValue);
        mv.dup2();
        mv.dup2();
        mv.store(switchValueNum, Type.DOUBLE_TYPE);
        mv.cast(Type.DOUBLE_TYPE, Type.INT_TYPE);
        mv.cast(Type.INT_TYPE, Type.DOUBLE_TYPE);
        mv.cmpl(Type.DOUBLE_TYPE);
        mv.ifne(switchDefault);

        mv.load(switchValueNum, Type.DOUBLE_TYPE);
        mv.cast(Type.DOUBLE_TYPE, Type.INT_TYPE);

        mv.freeVariable(switchValueNum);
        mv.freeVariable(switchValue);

        // emit tableswitch or lookupswitch
        switchInstruction(switchDefault, labels, values, valuesLength, mv);

        return labels;
    }

    /**
     * Shared implementation for int- and char-switches
     */
    private void switchInstruction(Label switchDefault, Label[] labels, long[] values,
            int valuesLength, StatementVisitor mv) {
        int distinctValues = distinctValues(values, valuesLength);
        int minValue = (int) (values[0] >> 32);
        int maxValue = (int) (values[valuesLength - 1] >> 32);
        int range = maxValue - minValue + 1;
        float density = (float) distinctValues / range;
        if (density >= 0.5f) {
            // System.out.printf("tableswitch [%d: %d - %d]\n", valuesLength, minValue, maxValue);
            Label[] switchLabels = new Label[range];
            Arrays.fill(switchLabels, switchDefault);
            for (int i = 0, lastValue = 0; i < valuesLength; ++i) {
                int value = (int) (values[i] >> 32);
                int index = (int) (values[i]);
                if (i == 0 || value != lastValue) {
                    switchLabels[value - minValue] = labels[index];
                }
                lastValue = value;
            }
            mv.tableswitch(minValue, maxValue, switchDefault, switchLabels);
        } else {
            // System.out.printf("lookupswitch [%d: %d - %d]\n", valuesLength, minValue, maxValue);
            Label[] switchLabels = new Label[distinctValues];
            int[] switchKeys = new int[distinctValues];
            for (int i = 0, j = 0, lastValue = 0; i < valuesLength; ++i) {
                int value = (int) (values[i] >> 32);
                int index = (int) (values[i]);
                if (i == 0 || value != lastValue) {
                    switchLabels[j] = labels[index];
                    switchKeys[j] = value;
                    j += 1;
                }
                lastValue = value;
            }
            mv.lookupswitch(switchDefault, switchKeys, switchLabels);
        }
    }

    private int distinctValues(long[] values, int length) {
        // sort values in ascending order
        Arrays.sort(values, 0, length);

        int distinctValues = 0;
        for (int i = 0, lastValue = 0; i < length; ++i) {
            int value = (int) (values[i] >> 32);
            if (i == 0 || value != lastValue) {
                distinctValues += 1;
            }
            lastValue = value;
        }
        return distinctValues;
    }
}
