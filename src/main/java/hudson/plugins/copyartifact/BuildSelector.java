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
import hudson.Util;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Result;
import hudson.model.Job;
import hudson.model.Run;

import javax.annotation.Nonnull;

/**
 * Extension point for enumerating builds to copy artifacts from.
 * Subclasses should override {@link #getNextBuild(Job, CopyArtifactPickContext)}.
 * 
 * @author Alan Harder
 */
public abstract class BuildSelector extends AbstractDescribableImpl<BuildSelector> implements ExtensionPoint {
    /**
     * @param job       the job to pick a build from.
     * @param context   context for the current execution of copyartifact.
     * @return  the build matches this selector and conditions stored in the context.
     * 
     * @since 2.0
     */
    public Run<?, ?> pickBuildToCopyFrom(@Nonnull Job<?,?> job, @Nonnull final CopyArtifactPickContext context) {
        if (!Util.isOverridden(
                BuildSelector.class,
                getClass(),
                "getNextBuild",
                Job.class,
                CopyArtifactPickContext.class
        )) {
            // backward compatibility.
            context.logInfo("WARNING: {0} is desined for the older version of Copyartifact and might not fully finctions.", getDisplayName());
            return getBuild(
                    job,
                    context.getEnvVars(),
                    new BuildFilter() {
                        @Override
                        public boolean isSelectable(Run<?, ?> run, EnvVars env) {
                            return context.getBuildFilter().isSelectable(run, context);
                        }
                    },
                    context.getCopierBuild()
            );
        }
        
        context.setLastMatchBuild(null);
        while (true) {
            Run<?, ?> candidate = getNextBuild(job, context);
            context.setLastMatchBuild(candidate);
            if (candidate == null) {
                context.logDebug("{0}: No more matching builds.", getDisplayName());
                return null;
            }
            context.logDebug("{0}: {1} found", getDisplayName(), candidate.getDisplayName());
            BuildFilter filter = context.getBuildFilter();
            if (!filter.isSelectable(candidate, context)) {
                context.logDebug(
                        "{0}: {1} is declined by the filter {2}",
                        getDisplayName(),
                        candidate.getDisplayName(),
                        filter.getDisplayName()
                );
                continue;
            }
            context.logDebug("{0}: {1} satisfied conditions.", getDisplayName(), candidate.getDisplayName());
            return candidate;
        }
    }

    /**
     * Override this method to implement {@link BuildSelector}.
     * Use {@link CopyArtifactPickContext#getLastMatchBuild()} to
     * continue enumerating builds.
     * Or you can save the execution state
     * with {@link CopyArtifactPickContext#addExtension(Object)}
     * 
     * @param job       the job to pick a build from.
     * @param context   context for the current execution of copyartifact.
     * @return  the build matches this selector.
     * 
     * @since 2.0
     */
    public Run<?, ?> getNextBuild(@Nonnull Job<?, ?> job, @Nonnull CopyArtifactPickContext context) {
        // Though this can be protected,
        // Util#isOverridden is applicable only for public methods.
        return null;
    }

    /**
     * Returns the display name for this selector.
     * You can override this to output configurations of this selector
     * in verbose logs.
     * 
     * @return the display name for this selector.
     * 
     * @since 2.0
     */
    public String getDisplayName() {
        try {
            return getDescriptor().getDisplayName();
        } catch (AssertionError e) {
            // getDescriptor throws AssertionException
            // if there's no descriptor available
            // (e.g. selectors in unit tests)
            return getClass().getName();
        }
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

    //// For backward compatibility.
    /**
     * Find a build to copy artifacts from.
     * @param job Source project
     * @param env Environment for build that is copying artifacts
     * @param filter Additional filter; returned result should return true (return null otherwise)
     * @param parent Build to which artifacts are being copied
     * @return Build to use, or null if no appropriate build was found
     * 
     * @deprecated implement {@link #getNextBuild(Job, CopyArtifactPickContext)} instead.
     */
    @Deprecated
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
     * 
     * @deprecated implement {@link #getNextBuild(Job, CopyArtifactPickContext)} instead.
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
     * 
     * @deprecated implement {@link #getNextBuild(Job, CopyArtifactPickContext)} instead.
     */
    @Deprecated
    protected boolean isSelectable(Run<?,?> run, EnvVars env) {
        return false;
    }
}
