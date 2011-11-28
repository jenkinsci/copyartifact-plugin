/*
 * The MIT License
 *
 * Copyright (c) 2011, Alan Harder
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
import hudson.Extension;
import hudson.matrix.MatrixConfiguration;
import hudson.matrix.MatrixRun;
import hudson.model.Result;
import hudson.model.Descriptor;
import hudson.model.Cause;
import hudson.model.Cause.UpstreamCause;
import hudson.model.Job;
import hudson.model.Run;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Copy artifacts from the build that triggered this build.
 * @author Alan Harder
 */
public class TriggeredBuildSelector extends BuildSelector {
    private Boolean fallbackToLastSuccessful;

    @DataBoundConstructor
    public TriggeredBuildSelector(boolean fallback) {
        this.fallbackToLastSuccessful = fallback ? Boolean.TRUE : null;
    }

    public boolean isFallbackToLastSuccessful() {
        return fallbackToLastSuccessful != null && fallbackToLastSuccessful.booleanValue();
    }
    
    @Override
    public Run<?,?> getBuild(Job<?,?> job, EnvVars env, BuildFilter filter, Run<?,?> parent) {
        // Upstream job for matrix will be parent project, not individual configuration:
        String jobName = job instanceof MatrixConfiguration
            ? job.getParent().getFullName() : job.getFullName();
        // Matrix run is triggered by its parent project, so check causes of parent build:
        for (Cause cause : parent instanceof MatrixRun
                ? ((MatrixRun)parent).getParentBuild().getCauses() : parent.getCauses()) {
            if (cause instanceof UpstreamCause
                    && jobName.equals(((UpstreamCause)cause).getUpstreamProject())) {
                Run<?,?> run = job.getBuildByNumber(((UpstreamCause)cause).getUpstreamBuild());
                return (run != null && filter.isSelectable(run, env)) ? run : null;
            }
        }
        if (isFallbackToLastSuccessful()) {
            //TODO: Write to console, that fallback is used.
            return super.getBuild(job, env, filter, parent);
        }
        return null;
    }
    
    @Override
    protected boolean isSelectable(Run<?,?> run, EnvVars env) {
        return isFallbackToLastSuccessful() && run.getResult().isBetterOrEqualTo(Result.SUCCESS);
    }

    @Extension(ordinal=25)
    public static final Descriptor<BuildSelector> DESCRIPTOR =
            new SimpleBuildSelectorDescriptor(
                TriggeredBuildSelector.class, Messages._TriggeredBuildSelector_DisplayName());
}
