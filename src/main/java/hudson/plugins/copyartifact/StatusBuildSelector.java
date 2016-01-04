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

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

import hudson.Extension;
import hudson.model.Descriptor;
import hudson.model.Job;
import hudson.model.Result;
import hudson.model.Run;

import org.jvnet.localizer.Localizable;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Copy artifacts from the specific status build.
 * @author Alan Harder
 */
public class StatusBuildSelector extends BuildSelector {
    public static enum BuildStatus {
        /** Stable builds.*/
        Stable(Messages._StatusBuildSelector_BuildStatus_Stable()),
        
        /** Stable or Unstable builds.*/
        Successful(Messages._StatusBuildSelector_BuildStatus_Successful()),
        
        /** Unstable builds. */
        Unstable(Messages._StatusBuildSelector_BuildStatus_Unstable()),
        
        /** Failed builds. */
        Failed(Messages._StatusBuildSelector_BuildStatus_Failed()),
        
        /** Completed builds with any build results.*/
        Completed(Messages._StatusBuildSelector_BuildStatus_Completed()),
        
        /** Any builds including incomplete (running) ones.*/
        Any(Messages._StatusBuildSelector_BuildStatus_Any()),
        
        ;
        private final Localizable displayName;
        private BuildStatus(Localizable displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName.toString();
        }
    };


    @Nonnull
    private final BuildStatus buildStatus;

    @Deprecated
    private transient Boolean stable;

    /**
     * @param stable
     * @deprecated use {@link #StatusBuildSelector(Result)} instead.
     */
    @Deprecated
    public StatusBuildSelector(boolean stable) {
        this(stable?BuildStatus.Stable:BuildStatus.Successful);
    }

    /**
     * @since 2.0
     */
    public StatusBuildSelector() {
        this(null);
    }

    /**
     * @param buildStatus
     * @since 2.0
     */
    @DataBoundConstructor
    public StatusBuildSelector(@CheckForNull BuildStatus buildStatus) {
        this.buildStatus = (buildStatus != null)?buildStatus:BuildStatus.Stable;
    }

    @SuppressWarnings("unused")
    private StatusBuildSelector readResolve() {
        if (buildStatus == null) {
            return new StatusBuildSelector(stable != null ? stable.booleanValue() : true);
        }
        return this;
    }

    /**
     * @return
     * @since 2.0
     */
    public BuildStatus getBuildStatus() {
        return buildStatus;
    }

    @Deprecated
    public boolean isStable() {
        return BuildStatus.Stable.equals(getBuildStatus());
    }

    /**
     * @param job
     * @param context
     * @return
     * @see hudson.plugins.copyartifact.BuildSelector#getNextBuild(hudson.model.Job, hudson.plugins.copyartifact.CopyArtifactPickContext)
     */
    @Override
    public Run<?, ?> getNextBuild(Job<?, ?> job, CopyArtifactPickContext context) {
        Run<?, ?> previousBuild = context.getLastMatchBuild();
        if (previousBuild == null) {
            // the first time
            switch(getBuildStatus()) {
            case Stable:
                return job.getLastStableBuild();
            case Unstable:
                return job.getLastUnstableBuild();
            case Failed:
                return job.getLastFailedBuild();
            case Successful:
                // really confusing, but in this case,
                // "successful" means marked as SUCCESS or UNSTABLE.
                return job.getLastSuccessfulBuild();
            case Completed:
                return job.getLastCompletedBuild();
            case Any:
                return job.getLastBuild();
            }
        } else {
            // the second or later time
            switch(getBuildStatus()) {
            case Stable:
                // really confusing, but in this case,
                // "successful" means marked as SUCCESS.
                return previousBuild.getPreviousSuccessfulBuild();
            case Unstable:
                for (
                        previousBuild = previousBuild.getPreviousBuild();
                        previousBuild != null;
                        previousBuild = previousBuild.getPreviousBuild()
                ) {
                    Result r = previousBuild.getResult();
                    if (Result.UNSTABLE.equals(r)) {
                        return previousBuild;
                    }
                }
                break;
            case Failed:
                return previousBuild.getPreviousFailedBuild();
            case Successful:
                for (
                        previousBuild = previousBuild.getPreviousBuild();
                        previousBuild != null;
                        previousBuild = previousBuild.getPreviousBuild()
                ) {
                    Result r = previousBuild.getResult();
                    if (r != null && r.isBetterOrEqualTo(Result.UNSTABLE)) {
                        return previousBuild;
                    }
                }
                break;
            case Completed:
                return previousBuild.getPreviousCompletedBuild();
            case Any:
                return previousBuild.getPreviousBuild();
            }
        }
        return null;
    }
    
    /**
     * @return
     * @see hudson.plugins.copyartifact.BuildSelector#getDisplayName()
     */
    @Override
    public String getDisplayName() {
        return String.format(
                "%s (%s)",
                super.getDisplayName(),
                getBuildStatus().toString()
        );
    }
    
    @Extension(ordinal=100)
    public static final Descriptor<BuildSelector> DESCRIPTOR =
            new SimpleBuildSelectorDescriptor(
                StatusBuildSelector.class, Messages._StatusBuildSelector_DisplayName());
}
