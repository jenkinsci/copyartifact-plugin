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

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

import jenkins.model.Jenkins;
import hudson.plugins.copyartifact.selector.TriggeringBuildSelector;
import hudson.plugins.copyartifact.selector.Version1BuildSelector;
import hudson.plugins.copyartifact.selector.FallbackBuildSelector;
import net.sf.json.JSONObject;

import org.kohsuke.stapler.StaplerRequest;

/**
 * Copy artifacts from the build that triggered this build.
 * @author Alan Harder
 * @deprecated use {@link TriggeredBuildSelector} instead.
 */
@Deprecated
public class TriggeredBuildSelector extends Version1BuildSelector {
    /**
     * Which build should be used if triggered by multiple upstream builds.
     * 
     * Specified in buildstep configurations and the global configuration.
     * @deprecated use {@link TriggeringBuildSelector.UpstreamFilterStrategy} instead.
     */
    @Deprecated
    public enum UpstreamFilterStrategy {
        /**
         * Use global configuration.
         * 
         * The default value for buildstep configurations.
         * Should not be specified in the global configuration.
         * 
         */
        UseGlobalSetting(TriggeringBuildSelector.UpstreamFilterStrategy.UseGlobalSetting),
        /**
         * Use the oldest build.
         * 
         * The default value for the global configuration.
         */
        UseOldest(TriggeringBuildSelector.UpstreamFilterStrategy.UseOldest),
        /**
         * Use the newest build.
         */
        UseNewest(TriggeringBuildSelector.UpstreamFilterStrategy.UseNewest),
        ;
        
        private final TriggeringBuildSelector.UpstreamFilterStrategy origin;
        
        UpstreamFilterStrategy(TriggeringBuildSelector.UpstreamFilterStrategy origin) {
            this.origin = origin;
        }
        
        public String getDisplayName() {
            return origin.getDisplayName();
        }
        
        public boolean isForGlobalSetting() {
            return origin.isForGlobalSetting();
        }
        
        /**
         * @return
         * @since 2.0
         */
        @Nonnull
        public TriggeringBuildSelector.UpstreamFilterStrategy getOrigin() {
            return origin;
        }
        
        @Nonnull
        public static TriggeringBuildSelector.UpstreamFilterStrategy getOriginFor(@CheckForNull UpstreamFilterStrategy value) {
            return (value != null)?value.getOrigin():UseGlobalSetting.getOrigin();
        }
    };
    private Boolean fallbackToLastSuccessful;
    private final UpstreamFilterStrategy upstreamFilterStrategy;
    private boolean allowUpstreamDependencies;

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
    public MigratedConfiguration migrateToVersion2() {
        return new MigratedConfiguration(isFallbackToLastSuccessful()
                ?new FallbackBuildSelector(
                        new TriggeringBuildSelector(
                                UpstreamFilterStrategy.getOriginFor(getUpstreamFilterStrategy()),
                                isAllowUpstreamDependencies()
                        ),
                        new StatusBuildSelector(
                                StatusBuildSelector.BuildStatus.Stable
                        )
                )
                :new TriggeringBuildSelector(
                        UpstreamFilterStrategy.getOriginFor(getUpstreamFilterStrategy()),
                        isAllowUpstreamDependencies()
                )
        );
    }
    
    /**
     * @deprecated use {@link TriggeringBuildSelector.DescriptorImpl} instead.
     */
    @Deprecated
    public static class DescriptorImpl extends SimpleBuildSelectorDescriptor {
        private UpstreamFilterStrategy globalUpstreamFilterStrategy;
        
        public DescriptorImpl() {
            super(TriggeredBuildSelector.class, Messages._TriggeredBuildSelector_DisplayName());
            globalUpstreamFilterStrategy = UpstreamFilterStrategy.UseOldest;
            load();
        }
        
        public void setGlobalUpstreamFilterStrategy(UpstreamFilterStrategy globalUpstreamFilterStrategy) {
            this.globalUpstreamFilterStrategy = globalUpstreamFilterStrategy;
            Jenkins jenkins = Jenkins.getInstance();
            if (jenkins != null) {
                ((TriggeringBuildSelector.DescriptorImpl)jenkins.getDescriptor(TriggeringBuildSelector.class))
                    .setGlobalUpstreamFilterStrategy(UpstreamFilterStrategy.getOriginFor(globalUpstreamFilterStrategy));
            }
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
    
    public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();
    
    @Override
    public DescriptorImpl getDescriptor() {
        return DESCRIPTOR;
    }
}
