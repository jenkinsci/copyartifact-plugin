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
package hudson.plugins.copyartifact.selector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.model.AbstractBuild;
import hudson.model.Cause;
import hudson.model.Cause.UpstreamCause;
import hudson.model.Job;
import hudson.model.Run;
import hudson.plugins.copyartifact.BuildSelector;
import hudson.plugins.copyartifact.BuildSelectorDescriptor;
import hudson.plugins.copyartifact.CopyArtifactPickContext;
import hudson.plugins.copyartifact.TriggeredBuildSelector;
import net.sf.json.JSONObject;

import org.jvnet.localizer.Localizable;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

/**
 * Copy artifacts from the build that triggered this build.
 * @author Alan Harder
 * @since 2.0
 */
public class TriggeringBuildSelector extends BuildSelector {
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
        UseGlobalSetting(false, Messages._TriggeringBuildSelector_UpstreamFilterStrategy_UseGlobalSetting()),
        /**
         * Use the oldest build.
         * 
         * The default value for the global configuration.
         */
        UseOldest(true,  Messages._TriggeringBuildSelector_UpstreamFilterStrategy_UseOldest()),
        /**
         * Use the newest build.
         */
        UseNewest(true, Messages._TriggeringBuildSelector_UpstreamFilterStrategy_UseNewest()),
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
    
    /**
     * An extension for {@link CopyArtifactPickContext}
     * that holds enumeration status.
     */
    private static class ContextExtension {
        /**
         * enumerated builds.
         */
        public Iterator<Run<?, ?>> nextBuild;
    };
    
    private final UpstreamFilterStrategy upstreamFilterStrategy;
    private boolean allowUpstreamDependencies;

    @DataBoundConstructor
    public TriggeringBuildSelector(UpstreamFilterStrategy upstreamFilterStrategy, boolean allowUpstreamDependencies) {
        this.upstreamFilterStrategy = upstreamFilterStrategy;
        this.allowUpstreamDependencies = allowUpstreamDependencies;
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
    @CheckForNull
    public Run<?, ?> getNextBuild(@Nonnull Job<?, ?> job, @Nonnull CopyArtifactPickContext context) {
        ContextExtension ext = context.getExtension(ContextExtension.class);
        if (ext == null) {
            // first time to be called.
            ext = new ContextExtension();
            List<Run<?, ?>> result = new ArrayList<Run<?, ?>>(
                    getAllUpstreamBuilds(job, context, context.getCopierBuild())
            );
            // sort builds by the strategy.
            Collections.sort(
                    result,
                    new Comparator<Run<?, ?>>() {
                        @Override
                        public int compare(Run<?, ?> o1, Run<?, ?> o2) {
                            return isUseNewest()
                                    ?o2.getNumber() - o1.getNumber()
                                    :o1.getNumber() - o2.getNumber();
                        }
                    }
            );
            
            ext.nextBuild = result.iterator();
            context.addExtension(ext);
        }
        if (!ext.nextBuild.hasNext()) {
            // no matching build.
            context.removeExtension(ext);
            return null;
        }
        
        return ext.nextBuild.next();
    }
    
    @Nonnull
    private HashSet<Run<?, ?>> getAllUpstreamBuilds(@Nonnull Job<?, ?> job, @Nonnull CopyArtifactPickContext context, @Nonnull Run<?, ?> parent) {
        HashSet<Run<?, ?>> result = new HashSet<Run<?, ?>>();
        
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
            if (jobNames.contains(upstreamBuild.getParent().getFullName())) {
                // Use the 'job' parameter instead of directly the 'upstreamBuild', because of Matrix jobs.
                result.add(job.getBuildByNumber(upstreamBuild.getNumber()));
            } else {
                // Figure out the parent job and do a recursive call to getBuild
                result.addAll(getAllUpstreamBuilds(job, context, upstreamBuild));
            }
        }
        
        return result;
    }
    
    @Extension
    public static class DescriptorImpl extends BuildSelectorDescriptor {
        private UpstreamFilterStrategy globalUpstreamFilterStrategy;
        
        @SuppressWarnings("deprecation")
        public DescriptorImpl() {
            globalUpstreamFilterStrategy = TriggeredBuildSelector.DESCRIPTOR
                .getGlobalUpstreamFilterStrategy().getOrigin();
            load();
        }
        
        @Override
        public String getDisplayName() {
            return Messages.TriggeringBuildSelector_DisplayName();
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
