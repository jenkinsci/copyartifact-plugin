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

import com.tikal.jenkins.plugins.multijob.MultiJobBuild;
import hudson.EnvVars;
import hudson.Extension;
import hudson.matrix.MatrixConfiguration;
import hudson.matrix.MatrixRun;
import hudson.model.*;
import hudson.model.Cause.UpstreamCause;
import org.kohsuke.stapler.DataBoundConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Copy artifacts from the build that triggered this build.
 *
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
    public Run<?, ?> getBuild(Job<?, ?> job, EnvVars env, BuildFilter filter, Run<?, ?> parent) {
        // Upstream job for matrix will be parent project, not individual configuration:
        String jobName = job instanceof MatrixConfiguration
                ? job.getParent().getFullName() : job.getFullName();
        List<Cause> causes = new ArrayList<Cause>();

        if (parent instanceof MatrixRun) {
            causes.addAll(((MatrixRun) parent).getParentBuild().getCauses());
        } else {
            causes.addAll(parent.getCauses());
        }

        for (Cause cause : causes) {
            if (cause instanceof UpstreamCause) {

                UpstreamCause upstreamCause = (UpstreamCause) cause;
                Job upstreamJob = Hudson.getInstance().getItemByFullName(upstreamCause.getUpstreamProject(), Job.class);

                Run upstreamRun = upstreamJob.getBuildByNumber(upstreamCause.getUpstreamBuild());

                Run matchingUpstreamProjectInPipeline = findMatchingUpstreamProjectInPipeline(upstreamRun, jobName);

                if (matchingUpstreamProjectInPipeline != null && filter.isSelectable(matchingUpstreamProjectInPipeline, env)) {
                    return matchingUpstreamProjectInPipeline;
                }
            }
        }

        if (isFallbackToLastSuccessful()) {
            return super.getBuild(job, env, filter, parent);
        }
        return null;
    }

    private Run findMatchingUpstreamProjectInPipeline(Run upstreamRun, String jobName) {
        if (upstreamRun==null) {
            return null;
        }

        if (upstreamRun instanceof MultiJobBuild) {

            MultiJobBuild mjb = (MultiJobBuild) upstreamRun;
            List<MultiJobBuild.SubBuild> subBuilds = mjb.getSubBuilds();
            List<AbstractProject> downstreamProjects = mjb.getProject().getDownstreamProjects();

            for (AbstractProject downstreamProject : downstreamProjects) {
                for (MultiJobBuild.SubBuild subBuild : subBuilds) {
                        if (subBuild.getJobName().equalsIgnoreCase(downstreamProject.getName())) {
                            Run currentRun = findMatchingUpstreamProjectInPipeline(downstreamProject.getBuildByNumber(subBuild.getBuildNumber()), jobName);
                            if (currentRun!=null) {
                                return currentRun;
                            }
                        }
                }
            }
        } else if (upstreamRun.getParent().getName().equalsIgnoreCase(jobName) && upstreamRun.getResult()!=null) {
            return upstreamRun;
        }

        return null;
    }

    @Override
    protected boolean isSelectable(Run<?, ?> run, EnvVars env) {
        return isFallbackToLastSuccessful() && run.getResult().isBetterOrEqualTo(Result.SUCCESS);
    }

    @Extension(ordinal = 25)
    public static final Descriptor<BuildSelector> DESCRIPTOR =
            new SimpleBuildSelectorDescriptor(
                    TriggeredBuildSelector.class, Messages._TriggeredBuildSelector_DisplayName());
}
