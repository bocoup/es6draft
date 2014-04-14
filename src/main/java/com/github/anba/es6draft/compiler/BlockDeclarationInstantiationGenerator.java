/**
 * Copyright (c) 2012-2014 André Bargull
 * Alle Rechte vorbehalten / All Rights Reserved.  Use is subject to license terms.
 *
 * <https://github.com/anba/es6draft>
 */
package com.github.anba.es6draft.compiler;

import static com.github.anba.es6draft.semantics.StaticSemantics.BoundNames;
import static com.github.anba.es6draft.semantics.StaticSemantics.IsConstantDeclaration;
import static com.github.anba.es6draft.semantics.StaticSemantics.LexicalDeclarations;
import static com.github.anba.es6draft.semantics.StaticSemantics.LexicallyDeclaredNames;

import java.util.ArrayList;
import java.util.List;

import com.github.anba.es6draft.ast.BlockStatement;
import com.github.anba.es6draft.ast.Declaration;
import com.github.anba.es6draft.ast.SwitchStatement;

/**
 * <h1>13 ECMAScript Language: Statements and Declarations</h1><br>
 * <h2>13.1 Block</h2>
 * <ul>
 * <li>13.1.11 Runtime Semantics: Block Declaration Instantiation
 * </ul>
 */
final class BlockDeclarationInstantiationGenerator extends DeclarationBindingInstantiationGenerator {
    private static final int INLINE_LIMIT = 1 << 5;

    BlockDeclarationInstantiationGenerator(CodeGenerator codegen) {
        super(codegen);
    }

    /**
     * stack: [env] {@literal ->} [env]
     * 
     * @param node
     *            the block statement
     * @param mv
     *            the statement visitor
     */
    void generate(BlockStatement node, StatementVisitor mv) {
        int declarations = LexicallyDeclaredNames(node.getScope()).size();
        if (declarations > INLINE_LIMIT) {
            codegen.compile(node, mv, this);

            mv.loadExecutionContext();
            mv.swap();
            mv.invoke(codegen.methodDesc(node));
        } else {
            generateInline(LexicalDeclarations(node), mv);
        }
    }

    /**
     * stack: [env] {@literal ->} [env]
     * 
     * @param node
     *            the switch statement
     * @param mv
     *            the statement visitor
     */
    void generate(SwitchStatement node, StatementVisitor mv) {
        int declarations = LexicallyDeclaredNames(node.getScope()).size();
        if (declarations > INLINE_LIMIT) {
            codegen.compile(node, mv, this);

            mv.loadExecutionContext();
            mv.swap();
            mv.invoke(codegen.methodDesc(node));
        } else {
            generateInline(LexicalDeclarations(node), mv);
        }
    }

    /**
     * stack: [env] {@literal ->} [env]
     * 
     * @param node
     *            the block statement
     * @param mv
     *            the expression visitor
     */
    void generateMethod(BlockStatement node, ExpressionVisitor mv) {
        // TODO: split into multiple methods if there are still too many declarations
        generateInline(LexicalDeclarations(node), mv);
    }

    /**
     * stack: [env] {@literal ->} [env]
     * 
     * @param node
     *            the switch statement
     * @param mv
     *            the expression visitor
     */
    void generateMethod(SwitchStatement node, ExpressionVisitor mv) {
        // TODO: split into multiple methods if there are still too many declarations
        generateInline(LexicalDeclarations(node), mv);
    }

    /**
     * stack: [env] {@literal ->} [env]
     * 
     * @param declarations
     *            the block scoped declarations
     * @param mv
     *            the expression visitor
     */
    private void generateInline(List<Declaration> declarations, ExpressionVisitor mv) {
        /* steps 1-2 */
        List<Declaration> functionsToInitialize = new ArrayList<>();

        // stack: [env] -> [env, envRec]
        mv.dup();
        getEnvironmentRecord(mv);

        /* step 3 */
        for (Declaration d : declarations) {
            for (String dn : BoundNames(d)) {
                mv.dup();
                if (IsConstantDeclaration(d)) {
                    // FIXME: spec bug (CreateImmutableBinding concrete method of `env`)
                    createImmutableBinding(dn, mv);
                } else {
                    // FIXME: spec bug (CreateMutableBinding concrete method of `env`)
                    createMutableBinding(dn, false, mv);
                }
            }
            if (isFunctionDeclaration(d)) {
                functionsToInitialize.add(d);
            }
        }

        if (!functionsToInitialize.isEmpty()) {
            // stack: [env, envRec] -> [envRec, env]
            mv.swap();

            /* step 4 */
            for (Declaration f : functionsToInitialize) {
                String fn = BoundName(f);

                // stack: [envRec, env] -> [envRec, env, envRec, env, cx]
                mv.dup2();
                mv.loadExecutionContext();

                // stack: [envRec, env, envRec, env, cx] -> [envRec, env, envRec, fo]
                InstantiateFunctionObject(f, mv);

                // stack: [envRec, env, envRec, fo] -> [envRec, env]
                initializeBinding(fn, mv);
            }

            // stack: [envRec, env] -> [env, envRec]
            mv.swap();
        }

        // stack: [env, envRec] -> [env]
        mv.pop();
    }
}
