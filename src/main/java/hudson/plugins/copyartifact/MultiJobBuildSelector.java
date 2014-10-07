package hudson.plugins.copyartifact;

import java.util.logging.Logger;

import jenkins.model.Jenkins;

import org.kohsuke.stapler.DataBoundConstructor;

import hudson.EnvVars;
import hudson.Extension;
import hudson.model.Cause;
import hudson.model.Cause.UpstreamCause;
import hudson.model.Descriptor;
import hudson.model.Job;
import hudson.model.Run;

import com.tikal.jenkins.plugins.multijob.MultiJobBuild;

/**
 * Copy artifacts from the build that was part of this MultiJob build.
 * @author Ray Sennewald
 */
public class MultiJobBuildSelector extends BuildSelector {
    private static final Logger LOGGER = Logger.getLogger(MultiJobBuildSelector.class.getName());

    @DataBoundConstructor
    public MultiJobBuildSelector() { }

    @Override
    public Run<?, ?> getBuild(Job<?, ?> job, EnvVars env, BuildFilter filter, Run<?, ?> parent) {
        MultiJobBuild multiJobBuild = null;
        // Are we in the MultiJob itself and trying to get an artifact from a Phase Build?
        if (parent instanceof MultiJobBuild) {
            multiJobBuild = (MultiJobBuild)parent;
        }
        // Nope, look for Upstream MultiJob that triggered this run (Are we in a Phase job?)
        else {
            for (Cause cause : parent.getCauses()) {
                UpstreamCause upstreamCause = (UpstreamCause)cause;
                Job upstreamJob = Jenkins.getInstance().getItemByFullName(upstreamCause.getUpstreamProject(), Job.class);
                Run upstreamRun = upstreamJob.getBuildByNumber(upstreamCause.getUpstreamBuild());

                if (upstreamRun != null && upstreamRun instanceof MultiJobBuild) {
                    multiJobBuild = (MultiJobBuild)upstreamRun;
                }
            }
        }
        if (multiJobBuild == null) {
            LOGGER.warning(String.format("'%s' is not found to be part of a MultiJob Project.", parent.getFullDisplayName()));
            return null;
        }
        // Get the run for our source Job in the current MultiJob Project's Build
        for (MultiJobBuild.SubBuild subBuild : multiJobBuild.getSubBuilds()) {
            // Find Job's specific build we want
            if (subBuild.getJobName().equals(job.getFullDisplayName())) {
                return job.getBuildByNumber(subBuild.getBuildNumber());
            }
        }
        return null;
    }

    @Override
    public boolean isSelectable(Run<?,?> run, EnvVars env) {
        return true;
    }

    @Extension(ordinal=20)
    public static final Descriptor<BuildSelector> DESCRIPTOR =
            new SimpleBuildSelectorDescriptor(
                    MultiJobBuildSelector.class, Messages._MultiJobBuildSelector_DisplayName());
}
