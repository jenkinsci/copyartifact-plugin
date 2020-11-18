/*
 * The MIT License
 *
 * Copyright (c) 2004-2011, Sun Microsystems, Inc., Alan Harder
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package hudson.plugins.copyartifact;

import hudson.EnvVars;
import hudson.ExtensionPoint;
import hudson.FilePath;
import hudson.Util;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Result;
import hudson.model.Job;
import hudson.model.Run;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Serializable;
import javax.annotation.CheckForNull;
import jenkins.util.VirtualFile;

/**
 * Extension point for selecting the build to copy artifacts from.
 * In a subclass override just isSelectable() for a standard loop through completed
 * builds, starting with the most recent.  Otherwise override getBuild() to provide
 * different build selection logic.
 * @author Alan Harder
 */
public abstract class BuildSelector extends AbstractDescribableImpl<BuildSelector> implements ExtensionPoint, Serializable {

    /**
     * Find a build to copy artifacts from.
     * @param job Source project
     * @param env Environment for build that is copying artifacts
     * @param filter Additional filter; returned result should return true (return null otherwise)
     * @param parent Build to which artifacts are being copied
     * @return Build to use, or null if no appropriate build was found
     */
    public Run<?,?> getBuild(Job<?,?> job, EnvVars env, BuildFilter filter, Run<?,?> parent) {
        // Backward compatibility:
        if (Util.isOverridden(BuildSelector.class, getClass(), "getBuild",
                              Job.class, EnvVars.class, BuildFilter.class)) {
            Run<?,?> run = getBuild(job, env, filter);
            return (run != null && filter.isSelectable(run, env)) ? run : null;
        }

        for (Run<?,?> run = job.getLastCompletedBuild(); run != null; run = run.getPreviousCompletedBuild())
            if (isSelectable(run, env) && filter.isSelectable(run, env))
                return run;

        return null;
    }

    /**
     * Find a build to copy artifacts from. Older and deprecated version of API.
     * @param job Source project
     * @param env Environment for build that is copying artifacts
     * @param filter Additional filter; returned result should return true (return null otherwise)
     * @return Build to use, or null if no appropriate build was found
     */
    @Deprecated
    public Run<?,?> getBuild(Job<?,?> job, EnvVars env, BuildFilter filter) {
        return getBuild(job, env, filter, null);
    }

    /**
     * Should this build be selected?  Override just this method to use a standard
     * loop through completed builds, starting with the most recent.
     * @param run Build to check
     * @param env Environment for build that is copying artifacts
     * @return True to select this build
     */
    protected boolean isSelectable(Run<?,?> run, EnvVars env) {
        return false;
    }

    /**
     * Wrapper for {@link Result#isBetterOrEqualTo(Result)} with null checks.
     * 
     * Returns <code>false</code> if <code>run</code> is <code>null</code>
     * or <code>run</code> is still running.
     * 
     * @param run Build to test.
     * @param resultToTest Result to test.
     * @return <code>false</code> if <code>run</code> is <code>null</code>
     * or <code>run</code> is still running.
     * @see Result#isBetterOrEqualTo(Result)
     */
    protected static boolean isBuildResultBetterOrEqualTo(Run<?,?> run, Result resultToTest) {
        if (run == null) {
            return false;
        }
        Result buildResult = run.getResult();
        if (buildResult == null) {
            return false;
        }
        return buildResult.isBetterOrEqualTo(resultToTest);
    }

    /**
     * Load artifacts from a given build.
     * @param sourceBuild a build which may have associated artifacts
     * @param console a way to print messages
     * @return a {@linkplain VirtualFile#isDirectory directory} of artifacts, or null if missing
     */
    protected @CheckForNull VirtualFile getArtifacts(Run<?, ?> sourceBuild, PrintStream console) throws IOException, InterruptedException {
        if (Util.isOverridden(BuildSelector.class, getClass(), "getSourceDirectory", Run.class, PrintStream.class)) {
            FilePath old = getSourceDirectory(sourceBuild, console);
            return old != null ? old.toVirtualFile() : null;
        } else {
            VirtualFile root = sourceBuild.getArtifactManager().root();
            return root.isDirectory() ? root : null;
        }
    }

    /**
     * @deprecated rather override {@link #getArtifacts}
     */
    @Deprecated
    protected FilePath getSourceDirectory(Run<?,?> src, PrintStream console) throws IOException, InterruptedException {
        FilePath srcDir = new FilePath(src.getArtifactsDir());
        if (srcDir.exists()) {
            return srcDir;
        } else {
            console.println(Messages.CopyArtifact_MissingSrcArtifacts(srcDir));
            return null;
        }
    }

}
