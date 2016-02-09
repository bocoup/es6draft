/**
 * Copyright (c) 2012-2015 André Bargull
 * Alle Rechte vorbehalten / All Rights Reserved.  Use is subject to license terms.
 *
 * <https://github.com/anba/es6draft>
 */
package com.github.anba.es6draft.runtime.internal;

import java.util.EnumSet;
import java.util.Set;

/**
 * <h1>Annex B - Additional ECMAScript Features for Web Browsers</h1>
 * <ul>
 * <li>B.1 Additional Syntax
 * <li>B.2 Additional Built-in Properties
 * <li>B.3 Other Additional Features
 * </ul>
 */
public enum CompatibilityOption {
    /**
     * B.1.1 Numeric Literals
     */
    LegacyOctalIntegerLiteral,

    /**
     * B.1.2 String Literals
     */
    OctalEscapeSequence,

    /**
     * B.1.3 HTML-like Comments
     */
    HTMLComments,

    /**
     * B.1.4 Regular Expressions Patterns
     */
    WebRegularExpressions,

    /**
     * B.2.1 Additional Properties of the Global Object
     */
    GlobalObject,

    /**
     * B.2.2 Additional Properties of the Object.prototype Object
     */
    ObjectPrototype,

    /**
     * B.2.3 Additional Properties of the String.prototype Object
     */
    StringPrototype,

    /**
     * B.2.4 Additional Properties of the Date.prototype Object
     */
    DatePrototype,

    /**
     * B.2.5 Additional Properties of the RegExp.prototype Object
     */
    RegExpPrototype,

    /**
     * B.3.1 __proto___ Property Names in Object Initializers
     */
    ProtoInitializer,

    /**
     * B.3.2 Labelled Function Declarations
     */
    LabelledFunctionDeclaration,

    /**
     * B.3.3 Block-Level Function Declarations Web Legacy Compatibility Semantics
     */
    BlockFunctionDeclaration,

    /**
     * B.3.4 FunctionDeclarations in IfStatement Statement Clauses
     */
    IfStatementFunctionDeclaration,

    /**
     * B.3.5 VariableStatements in Catch blocks
     */
    CatchVarStatement,

    /**
     * Web-Extension: RegExp statics
     */
    RegExpStatics,

    /**
     * Web-Extension: Function.prototype.arguments
     */
    FunctionArguments,

    /**
     * Web-Extension: Function.prototype.caller
     */
    FunctionCaller,

    /**
     * Web-Extension: arguments.caller (not implemented)
     */
    ArgumentsCaller,

    /**
     * Moz-Extension: for-each statement
     */
    ForEachStatement,

    /**
     * Moz-Extension: guarded catch
     */
    GuardedCatch,

    /**
     * Moz-Extension: expression closure
     */
    ExpressionClosure,

    /**
     * Moz-Extension: legacy (star-less) generators
     */
    LegacyGenerator,

    /**
     * Moz-Extension: Reflect.parse() function
     */
    ReflectParse,

    /**
     * Moz-Extension: Extended precision for toFixed, toExponential, toPrecision
     */
    ExtendedPrecision,

    /**
     * Moz-Extension: Implicit strict functions include {@code "use strict;"} directive in source
     */
    ImplicitStrictDirective,

    /**
     * Moz-Extension: Don't call [[Enumerate]] on Proxy objects in the prototype chain
     */
    ProxyProtoSkipEnumerate,

    /**
     * Moz-Extension: Allow initializer expression in for-in variable declarations.
     */
    ForInVarInitializer,

    /**
     * Moz-Extension: legacy comprehension forms (disabled)
     */
    LegacyComprehension,

    /**
     * Moz-Extension: let expression (disabled)
     */
    LetExpression,

    /**
     * Moz-Extension: let statement (disabled)
     */
    LetStatement,

    /**
     * Exponentiation operator {@code **} (Stage 3 proposal)
     */
    Exponentiation,

    /**
     * Array.prototype.includes (Stage 4 proposal)
     */
    ArrayIncludes,

    /**
     * {@code function.sent} meta property (Stage 2 proposal)
     */
    FunctionSent,

    /**
     * Async Function Definitions (Stage 3 proposal)
     */
    AsyncFunction,

    /**
     * {@code export from} additions (Stage 1 proposal)
     */
    ExportFrom,

    /**
     * ArrayBuffer.transfer (Stage 1 proposal)
     */
    ArrayBufferTransfer,

    /**
     * Decorators (Stage 1 proposal)
     */
    Decorator,

    /**
     * Object Rest Destructuring (Stage 2 proposal)
     */
    ObjectRestDestructuring,

    /**
     * Object Spread Initializer (Stage 2 proposal)
     */
    ObjectSpreadInitializer,

    /**
     * Trailing comma in function calls (Stage 3 proposal)
     */
    FunctionCallTrailingComma,

    /**
     * String.prototype.trimLeft and trimRight (Stage 1 proposal)
     */
    StringTrim,

    /**
     * Class Property Declarations (static properties) (Stage 1 proposal)
     */
    StaticClassProperties,

    /**
     * Object.values and Object.entries functions (Stage 3 proposal)
     */
    ObjectValuesEntries,

    /**
     * String.prototype.padStart and padEnd (Stage 3 proposal)
     */
    StringPad,

    /**
     * String.prototype.matchAll (Stage 1 proposal)
     */
    StringMatchAll,

    /**
     * Type annotations (limited parser support only).
     */
    TypeAnnotation,

    /**
     * Function.prototype.toMethod (deferred extension)
     */
    FunctionToMethod,

    /**
     * Array and Generator Comprehension (deferred extension)
     */
    Comprehension,

    /**
     * new super() (deferred extension)
     */
    NewSuper,

    /**
     * Realm Objects (deferred extension)
     */
    Realm,

    /**
     * Loader Objects (deferred extension)
     */
    Loader,

    /**
     * System Object (deferred extension)
     */
    System,

    /**
     * Track unhandled rejected promise objects
     */
    PromiseRejection,

    /**
     * ArrayBuffer: Missing length parameter in constructor call
     */
    ArrayBufferMissingLength,

    ;

    /**
     * Returns a set of all options for strict-compatibility.
     * 
     * @return the options set for strict-compatibility
     */
    public static final Set<CompatibilityOption> StrictCompatibility() {
        return EnumSet.noneOf(CompatibilityOption.class);
    }

    /**
     * Returns a set of all options for web-compatibility.
     * 
     * @return the options set for web-compatibility
     */
    public static final Set<CompatibilityOption> WebCompatibility() {
        return addAll(AnnexB(), EnumSet.range(RegExpStatics, FunctionCaller));
    }

    /**
     * Returns a set of all options for mozilla-compatibility.
     * 
     * @return the options set for mozilla-compatibility
     */
    public static final Set<CompatibilityOption> MozCompatibility() {
        return addAll(WebCompatibility(), MozExtensions(), EnumSet.of(Comprehension));
    }

    /**
     * Returns a set of all mozilla extensions.
     * 
     * @return the options set for mozilla extensions
     */
    public static final Set<CompatibilityOption> MozExtensions() {
        return EnumSet.range(ForEachStatement, ForInVarInitializer);
    }

    /**
     * Returns a set of all options for Annex B features.
     * 
     * @return the options set for Annex B features
     */
    public static final Set<CompatibilityOption> AnnexB() {
        return EnumSet.range(LegacyOctalIntegerLiteral, CatchVarStatement);
    }

    /**
     * Staging level of ECMAScript proposals.
     * 
     * @see <a href="https://tc39.github.io/process-document/">The TC39 Process</a>
     */
    public enum Stage {
        /**
         * Stage 0 (Strawman)
         */
        Strawman(0),

        /**
         * Stage 1 (Proposal)
         */
        Proposal(1),

        /**
         * Stage 2 (Draft)
         */
        Draft(2),

        /**
         * Stage 3 (Candidate)
         */
        Candidate(3),

        /**
         * Stage 4 (Finished)
         */
        Finished(4)

        ;

        private final int level;

        private Stage(int level) {
            this.level = level;
        }

        /**
         * Returns the staging level.
         * 
         * @return the staging level
         */
        public int getLevel() {
            return level;
        }
    }

    /**
     * Returns a set of all options for proposed stage extensions.
     * 
     * @param stage
     *            the requested staging level
     * @return the options set for proposed stage extensions
     */
    public static final Set<CompatibilityOption> of(Stage stage) {
        switch (stage) {
        case Strawman:
            return EnumSet.noneOf(CompatibilityOption.class);
        case Proposal:
            return EnumSet.of(ArrayBufferTransfer, ExportFrom, Decorator, StringTrim, StringMatchAll,
                    StaticClassProperties);
        case Draft:
            return EnumSet.of(FunctionSent, ObjectRestDestructuring, ObjectSpreadInitializer);
        case Candidate:
            return EnumSet.of(AsyncFunction, Exponentiation, FunctionCallTrailingComma, ObjectValuesEntries, StringPad);
        case Finished:
            return EnumSet.of(ArrayIncludes);
        default:
            throw new AssertionError();
        }
    }

    /**
     * Returns a set of all options for proposed stage extensions.
     * 
     * @param stage
     *            the required staging level
     * @return the options set for proposed stage extensions
     */
    // TODO: Rename?
    public static final Set<CompatibilityOption> required(Stage stage) {
        EnumSet<CompatibilityOption> options = EnumSet.noneOf(CompatibilityOption.class);
        switch (stage) {
        case Strawman:
            options.addAll(of(Stage.Strawman));
        case Proposal:
            options.addAll(of(Stage.Proposal));
        case Draft:
            options.addAll(of(Stage.Draft));
        case Candidate:
            options.addAll(of(Stage.Candidate));
        case Finished:
            options.addAll(of(Stage.Finished));
        default:
            return options;
        }
    }

    /**
     * Returns a set of all experimental options.
     * 
     * @return the options set for experimental features
     */
    public static final Set<CompatibilityOption> Experimental() {
        return EnumSet.range(TypeAnnotation, System);
    }

    @SafeVarargs
    private static <E extends Enum<E>> Set<E> addAll(Set<E> set, Set<E>... sets) {
        for (Set<E> set2 : sets) {
            set.addAll(set2);
        }
        return set;
    }
}
