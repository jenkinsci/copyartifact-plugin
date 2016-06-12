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

import javax.annotation.Nonnull;

import org.kohsuke.stapler.DataBoundConstructor;

import hudson.Extension;
import hudson.model.Run;
import hudson.plugins.copyartifact.BuildFilter;
import hudson.plugins.copyartifact.BuildFilterDescriptor;
import hudson.plugins.copyartifact.CopyArtifactPickContext;

/**
 * Accepts a build when the underlying filter doesn't accept it.
 * @since 2.0
 */
public class NotBuildFilter extends BuildFilter {
    @Nonnull
    private final BuildFilter buildFilter;
    
    /**
     * @param buildFilter build filter to invert
     */
    @DataBoundConstructor
    public NotBuildFilter(@Nonnull BuildFilter buildFilter) {
        this.buildFilter = buildFilter;
    }
    
    /**
     * @return build filter to invert
     */
    @Nonnull
    public BuildFilter getBuildFilter() {
        return buildFilter;
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isSelectable(Run<?, ?> candidate, CopyArtifactPickContext context) {
        boolean result = getBuildFilter().isSelectable(candidate, context);
        context.logDebug(
                "{0}: filter result by {1} is reverted: {2} -> {3}",
                candidate.getFullDisplayName(),
                getBuildFilter().getDisplayName(),
                result,
                !result
        );
        return !result;
    }
    
    /**
     * the descriptor for {@link NotBuildFilter}
     */
    @Extension(ordinal=-102)    // bottom most
    public static class DescriptorImpl extends BuildFilterDescriptor {
        /**
         * {@inheritDoc}
         */
        @Override
        public String getDisplayName() {
            return Messages.NotBuildFilter_DisplayName();
        }
    }
}
