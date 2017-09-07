/*
 * The MIT License
 * 
 * Copyright (c) 2015 IKEDA Yasuyuki
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

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

import jenkins.model.Jenkins;

import org.kohsuke.stapler.DataBoundConstructor;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.model.Job;
import hudson.model.Run;
import hudson.plugins.copyartifact.BuildFilter;
import hudson.plugins.copyartifact.BuildFilterDescriptor;
import hudson.plugins.copyartifact.BuildSelector;
import hudson.plugins.copyartifact.BuildSelectorDescriptor;
import hudson.plugins.copyartifact.CopyArtifactPickContext;
import hudson.plugins.copyartifact.filter.AndBuildFilter;
import hudson.plugins.copyartifact.filter.NoBuildFilter;

/**
 * Tries multiple selectors consequently.
 * 
 * @since 2.0
 */
public class FallbackBuildSelector extends BuildSelector {
    /**
     * An entry for {@link FallbackBuildSelector}
     * @since 2.0
     */
    public static class Entry extends AbstractDescribableImpl<Entry>{
        @Nonnull
        private final BuildSelector buildSelector;
        
        @Nonnull
        private final BuildFilter buildFilter;
        
        /**
         * @param buildSelector build selector
         * @param buildFilter   build filter used with the build selector
         */
        @DataBoundConstructor
        public Entry(@Nonnull BuildSelector buildSelector, @Nonnull BuildFilter buildFilter) {
            this.buildSelector = buildSelector;
            this.buildFilter = buildFilter;
        }
        
        /**
         * @param buildSelector build selector
         */
        public Entry(@Nonnull BuildSelector buildSelector) {
            this(buildSelector, new NoBuildFilter());
        }
        
        /**
         * @return build selector
         */
        public BuildSelector getBuildSelector() {
            return buildSelector;
        }
        
        /**
         * @return build filter
         */
        public BuildFilter getBuildFilter() {
            return buildFilter;
        }
        
        /**
         * the descriptor for {@link FallbackBuildSelector}
         */
        @Extension
        public static class DescriptorImpl extends Descriptor<Entry> {
            /**
             * {@inheritDoc}
             */
            @Override
            public String getDisplayName() {
                return Messages.FallbackBuildSelector_Entry_DisplayName();
            }
            
            /**
             * @return descriptors of all {@link BuildSelector} except {@link FallbackBuildSelector}
             */
            public Iterable<? extends Descriptor<? extends BuildSelector>> getBuildSelectorDescriptorList() {
                Jenkins jenkins = Jenkins.getInstance();
                if (jenkins == null) {
                    return Collections.emptyList();
                }
                // remove FallbackBuildSelector itself.
                return Iterables.filter(
                        jenkins.getDescriptorList(BuildSelector.class),
                        new Predicate<Descriptor<? extends BuildSelector>>() {
                            @Override
                            public boolean apply(Descriptor<? extends BuildSelector> d) {
                                return !FallbackBuildSelector.class.isAssignableFrom(d.clazz);
                            }
                        }
                );
            }
            
            /**
             * @return descriptors for all {@link BuildFilter}s.
             */
            public List<BuildFilterDescriptor> getBuildFilterDescriptorList() {
                return BuildFilter.allWithNoBuildFilter();
            }
        }
        
    }
    
    @Nonnull
    private final List<Entry> entryList;
    
    /**
     * @param entryList build selectors to try
     */
    @DataBoundConstructor
    public FallbackBuildSelector(@Nonnull List<Entry> entryList) {
        this.entryList = entryList;
    }

    /**
     * Convenient constructor.
     * 
     * @param buildSelectors build selectors to try
     */
    public FallbackBuildSelector(@Nonnull BuildSelector... buildSelectors) {
        this(Lists.transform(
                Arrays.asList(buildSelectors),
                new Function<BuildSelector, Entry>() {
                    @Override
                    public Entry apply(BuildSelector buildSelector) {
                        return new Entry(buildSelector);
                    }
                }
        ));
    }

    /**
     * @return build selectors to try
     */
    public List<Entry> getEntryList() {
        return entryList;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @CheckForNull
    public Run<?, ?> pickBuildToCopyFrom(@Nonnull Job<?, ?> job, @Nonnull CopyArtifactPickContext context)
            throws IOException, InterruptedException
    {
        for (Entry entry : getEntryList()) {
            CopyArtifactPickContext childContext = context.clone();
            if (entry.getBuildFilter() instanceof NoBuildFilter) {
                // nothing to do.
            } else if (context.getBuildFilter() instanceof NoBuildFilter) {
                childContext.setBuildFilter(entry.getBuildFilter());
            } else {
                // BuildFilters are provided both in context and this selector.
                // Merge them.
                childContext.setBuildFilter(new AndBuildFilter(Arrays.asList(
                        childContext.getBuildFilter()
                        , entry.getBuildFilter()
                )));
            }
            // Ensure this is the first match.
            childContext.setLastMatchBuild(null);
            
            context.logDebug("Try {0}", entry.getBuildSelector().getDisplayName());
            Run<?, ?> candidate = entry.getBuildSelector().pickBuildToCopyFrom(job, childContext);
            if (candidate != null) {
                return candidate;
            }
        }
        return null;
    }
    
    @Extension(ordinal=-100)    // bottom most
    public static class DescriptorImpl extends BuildSelectorDescriptor {
        @Override
        public String getDisplayName() {
            return Messages.FallbackBuildSelector_DisplayName();
        }
    }
}
