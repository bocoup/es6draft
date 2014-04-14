/**
 * Copyright (c) 2012-2014 André Bargull
 * Alle Rechte vorbehalten / All Rights Reserved.  Use is subject to license terms.
 *
 * <https://github.com/anba/es6draft>
 */
package com.github.anba.es6draft.util;

import static com.github.anba.es6draft.util.Functional.toStrings;
import static java.util.Collections.emptyList;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.configuration.Configuration;
import org.junit.rules.ExternalResource;

import com.github.anba.es6draft.Script;
import com.github.anba.es6draft.compiler.Compiler;
import com.github.anba.es6draft.repl.console.ShellConsole;
import com.github.anba.es6draft.repl.global.ShellGlobalObject;
import com.github.anba.es6draft.runtime.World;
import com.github.anba.es6draft.runtime.internal.CompatibilityOption;
import com.github.anba.es6draft.runtime.internal.ObjectAllocator;
import com.github.anba.es6draft.runtime.internal.ScriptCache;
import com.github.anba.es6draft.runtime.objects.GlobalObject;

/**
 * {@link ExternalResource} sub-class to facilitate creation of {@link GlobalObject} instances
 */
public abstract class TestGlobals<GLOBAL extends ShellGlobalObject, TEST extends TestInfo> extends
        ExternalResource {
    private final Configuration configuration;
    private Set<CompatibilityOption> options;
    private ScriptCache scriptCache;
    private List<Script> scripts;

    public TestGlobals(Configuration configuration) {
        this.configuration = configuration;
    }

    protected Set<CompatibilityOption> getOptions() {
        return options;
    }

    protected Set<Compiler.Option> getCompilerOptions() {
        return EnumSet.noneOf(Compiler.Option.class);
    }

    @Override
    protected void before() throws Throwable {
        // read options ...
        options = compatibilityOptions(configuration.getString("mode", ""));
        scriptCache = new ScriptCache(getOptions());

        // pre-compile initialisation scripts
        Path basedir = Resources.getTestSuitePath(configuration);
        List<?> scriptNames = configuration.getList("scripts", emptyList());
        List<Script> scripts = new ArrayList<>();
        for (String scriptName : toStrings(scriptNames)) {
            Script script = scriptCache.script(scriptName, 1,
                    Resources.resource(scriptName, basedir));
            scripts.add(script);
        }
        this.scripts = scripts;
    }

    public final GLOBAL newGlobal(ShellConsole console, TEST test) throws IOException,
            URISyntaxException {
        ObjectAllocator<GLOBAL> allocator = newAllocator(console, test, scriptCache);
        World<GLOBAL> world = new World<>(allocator, getOptions(), getCompilerOptions());
        GLOBAL global = world.newGlobal();
        global.initialize(global);
        // Evaluate additional initialisation scripts
        for (Script script : scripts) {
            global.eval(script);
        }
        return global;
    }

    protected abstract ObjectAllocator<GLOBAL> newAllocator(ShellConsole console, TEST test,
            ScriptCache scriptCache);

    private static Set<CompatibilityOption> compatibilityOptions(String mode) {
        switch (mode) {
        case "moz-compatibility":
            return CompatibilityOption.MozCompatibility();
        case "web-compatibility":
            return CompatibilityOption.WebCompatibility();
        case "strict-compatibility":
        default:
            return CompatibilityOption.StrictCompatibility();
        }
    }
}
