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

package hudson.plugins.copyartifact.filter;

import java.util.Arrays;
import java.util.List;

import javax.annotation.Nonnull;

import org.kohsuke.stapler.DataBoundConstructor;

import hudson.Extension;
import hudson.model.Run;
import hudson.plugins.copyartifact.BuildFilter;
import hudson.plugins.copyartifact.BuildFilterDescriptor;
import hudson.plugins.copyartifact.CopyArtifactPickContext;

/**
 * Accepts a build when any of underlying filters accepts it.
 * @since 2.0
 */
public class OrBuildFilter extends BuildFilter {
    @Nonnull
    private final List<BuildFilter> buildFilterList;
    
    /**
     * @param buildFilterList build filters to disjunct
     */
    @DataBoundConstructor
    public OrBuildFilter(@Nonnull List<BuildFilter> buildFilterList) {
        this.buildFilterList = buildFilterList;
    }
    
    /**
     * Convenient constructor.
     * 
     * @param buildFilters build filters to disjunct
     */
    public OrBuildFilter(@Nonnull BuildFilter... buildFilters) {
        this(Arrays.asList(buildFilters));
    }
    
    /**
     * @return build filters to disjunct
     */
    @Nonnull
    public List<BuildFilter> getBuildFilterList() {
        return buildFilterList;
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isSelectable(Run<?, ?> candidate, CopyArtifactPickContext context) {
        for (BuildFilter filter: getBuildFilterList()) {
            if (filter.isSelectable(candidate, context)) {
                context.logDebug(
                        "{0}: accepted by the filter {1} in {2}",
                        candidate.getFullDisplayName(),
                        filter.getDisplayName(),
                        getDisplayName()
                );
                return true;
            }
        }
        return false;
    }
    
    /**
     * the descriptor for {@link OrBuildFilter}
     */
    @Extension(ordinal=-101)    // bottom most
    public static class DescriptorImpl extends BuildFilterDescriptor {
        /**
         * {@inheritDoc}
         */
        @Override
        public String getDisplayName() {
            return Messages.OrBuildFilter_DisplayName();
        }
    }
}
