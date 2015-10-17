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

import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import jenkins.model.Jenkins;
import hudson.EnvVars;
import hudson.Extension;
import hudson.model.Result;
import hudson.model.AbstractProject;
import hudson.model.AbstractBuild;
import hudson.model.Cause;
import hudson.model.Cause.UpstreamCause;
import hudson.model.Job;
import hudson.model.Run;
import net.sf.json.JSONObject;

import org.jvnet.localizer.Localizable;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

/**
 * Copy artifacts from the build that triggered this build.
 * @author Alan Harder
 */
public class TriggeredBuildSelector extends BuildSelector {
    private static final Logger LOGGER = Logger.getLogger(TriggeredBuildSelector.class.getName());
    /**
     * Which build should be used if triggered by multiple upstream builds.
     * 
     * Specified in buildstep configurations and the global configuration.
     */
    public enum UpstreamFilterStrategy {
        /**
         * Use global configuration.
         * 
         * The default value for buildstep configurations.
         * Should not be specified in the global configuration.
         * 
         */
        UseGlobalSetting(false, Messages._TriggeredBuildSelector_UpstreamFilterStrategy_UseGlobalSetting()),
        /**
         * Use the oldest build.
         * 
         * The default value for the global configuration.
         */
        UseOldest(true,  Messages._TriggeredBuildSelector_UpstreamFilterStrategy_UseOldest()),
        /**
         * Use the newest build.
         */
        UseNewest(true, Messages._TriggeredBuildSelector_UpstreamFilterStrategy_UseNewest()),
        ;
        
        private final boolean forGlobalSetting;
        private final Localizable displayName;
        
        UpstreamFilterStrategy(boolean forGlobalSetting, Localizable displayName) {
            this.forGlobalSetting = forGlobalSetting;
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName.toString();
        }
        
        public boolean isForGlobalSetting() {
            return forGlobalSetting;
        }
    };
    private Boolean fallbackToLastSuccessful;
    private final UpstreamFilterStrategy upstreamFilterStrategy;
    private boolean allowUpstreamDependencies;

    @DataBoundConstructor
    public TriggeredBuildSelector(boolean fallbackToLastSuccessful, UpstreamFilterStrategy upstreamFilterStrategy, boolean allowUpstreamDependencies) {
        this.fallbackToLastSuccessful = fallbackToLastSuccessful ? Boolean.TRUE : null;
        this.upstreamFilterStrategy = upstreamFilterStrategy;
        this.allowUpstreamDependencies = allowUpstreamDependencies;
    }

    @Deprecated
    public TriggeredBuildSelector(boolean fallbackToLastSuccessful, UpstreamFilterStrategy upstreamFilterStrategy) {
        this(fallbackToLastSuccessful, upstreamFilterStrategy, false);
    }

    @Deprecated
    public TriggeredBuildSelector(boolean fallback) {
        this(fallback, UpstreamFilterStrategy.UseGlobalSetting, false);
    }
    
    public boolean isFallbackToLastSuccessful() {
        return fallbackToLastSuccessful != null && fallbackToLastSuccessful.booleanValue();
    }
    
    /**
     * @return Which build should be used if triggered by multiple upstream builds.
     */
    public UpstreamFilterStrategy getUpstreamFilterStrategy() {
        return upstreamFilterStrategy;
    }

    /**
     * @return whether to use the newest upstream or not (use the oldest) when there are multiple upstreams.
     */
    public boolean isUseNewest() {
        UpstreamFilterStrategy strategy = getUpstreamFilterStrategy();
        if(strategy == null || strategy == UpstreamFilterStrategy.UseGlobalSetting) {
            strategy = ((DescriptorImpl)getDescriptor()).getGlobalUpstreamFilterStrategy();
        }
        if(strategy == null){
            return false;
        }
        switch(strategy) {
        case UseOldest:
            return false;
        case UseNewest:
            return true;
        default:
            // default behavior
            return false;
        }
    }
    
    public boolean isAllowUpstreamDependencies() {
        return allowUpstreamDependencies;
    }
    
    @Override
    public Run<?,?> getBuild(Job<?,?> job, EnvVars env, BuildFilter filter, Run<?,?> parent) {
        Run<?,?> result = null;

        // Upstream job for matrix will be parent project, not only individual configuration:
        List<String> jobNames = new ArrayList<String>();
        jobNames.add(job.getFullName());
        if ((job instanceof AbstractProject<?,?>) && ((AbstractProject<?,?>)job).getRootProject() != job) {
            jobNames.add(((AbstractProject<?,?>)job).getRootProject().getFullName());
        }

        List<Run<?, ?>> upstreamBuilds = new ArrayList<Run<?, ?>>();

        for (Cause cause: parent.getCauses()) {
            if (cause instanceof UpstreamCause) {
                UpstreamCause upstream = (UpstreamCause) cause;
                Run<?, ?> upstreamRun = upstream.getUpstreamRun();
                if (upstreamRun != null) {
                    upstreamBuilds.add(upstreamRun);
                }
            }
        }

        if (isAllowUpstreamDependencies() && (parent instanceof AbstractBuild)) {
            AbstractBuild<?, ?> parentBuild = (AbstractBuild<?,?>)parent;
            
            Map<AbstractProject, Integer> parentUpstreamBuilds = parentBuild.getUpstreamBuilds();
            for (Map.Entry<AbstractProject, Integer> buildEntry : parentUpstreamBuilds.entrySet()) {
                upstreamBuilds.add(buildEntry.getKey().getBuildByNumber(buildEntry.getValue()));
            }

        }

        for (Run<?, ?> upstreamBuild : upstreamBuilds) {
            Run<?,?> run = null;

            if (jobNames.contains(upstreamBuild.getParent().getFullName())) {
                // Use the 'job' parameter instead of directly the 'upstreamBuild', because of Matrix jobs.
                run = job.getBuildByNumber(upstreamBuild.getNumber());
            } else {
                // Figure out the parent job and do a recursive call to getBuild
                run = getBuild(job, env, filter, upstreamBuild);
            }

            if (run != null && filter.isSelectable(run, env)){
                if (
                        (result == null)
                        || (isUseNewest() && result.getNumber() < run.getNumber())
                        || (!isUseNewest() && result.getNumber() > run.getNumber())
                ) {
                    result = run;
                }
            }
        }
        
        if (result == null && isFallbackToLastSuccessful()) {
            //TODO: Write to console, that fallback is used.
            result = super.getBuild(job, env, filter, parent);
        }
        return result;
    }
    
    @Override
    protected boolean isSelectable(Run<?,?> run, EnvVars env) {
        return isFallbackToLastSuccessful() && isBuildResultBetterOrEqualTo(run, Result.SUCCESS);
    }

    @Extension(ordinal=25)
    public static class DescriptorImpl extends SimpleBuildSelectorDescriptor {
        private UpstreamFilterStrategy globalUpstreamFilterStrategy;
        
        public DescriptorImpl() {
            super(TriggeredBuildSelector.class, Messages._TriggeredBuildSelector_DisplayName());
            globalUpstreamFilterStrategy = UpstreamFilterStrategy.UseOldest;
            load();
        }
        
        public void setGlobalUpstreamFilterStrategy(UpstreamFilterStrategy globalUpstreamFilterStrategy) {
            this.globalUpstreamFilterStrategy = globalUpstreamFilterStrategy;
        }
        
        public UpstreamFilterStrategy getGlobalUpstreamFilterStrategy() {
            return globalUpstreamFilterStrategy;
        }
        
        @Override
        public boolean configure(StaplerRequest req, JSONObject json)
                throws hudson.model.Descriptor.FormException {
            setGlobalUpstreamFilterStrategy(UpstreamFilterStrategy.valueOf(json.getString("globalUpstreamFilterStrategy")));
            save();
            return super.configure(req, json);
        }
    }
}
