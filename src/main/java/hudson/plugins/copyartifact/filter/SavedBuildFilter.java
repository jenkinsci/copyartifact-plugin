/*
 * The MIT License
 *
 * Copyright (c) 2004-2010, Sun Microsystems, Inc., Alan Harder
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

import hudson.Extension;
import hudson.model.Run;
import hudson.plugins.copyartifact.BuildFilter;
import hudson.plugins.copyartifact.BuildFilterDescriptor;
import hudson.plugins.copyartifact.CopyArtifactPickContext;

import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Copy artifacts from the saved build (marked "keep forever").
 * @author Alan Harder
 * @since 2.0
 */
public class SavedBuildFilter extends BuildFilter {
    @DataBoundConstructor
    public SavedBuildFilter() { }

    /**
     * @param run
     * @param context
     * @return
     * @see hudson.plugins.copyartifact.BuildFilter#isSelectable(hudson.model.Run, hudson.plugins.copyartifact.CopyArtifactPickContext)
     */
    @Override
    public boolean isSelectable(Run<?, ?> run, CopyArtifactPickContext context) {
        return run.isKeepLog();
    }

    /**
     * the descriptor for {@link SavedBuildFilter}
     */
    @Extension
    public static class DescriptorImpl extends BuildFilterDescriptor {
        /**
         * @return
         * @see hudson.model.Descriptor#getDisplayName()
         */
        @Override
        public String getDisplayName() {
            return Messages.SavedBuildFilter_DisplayName();
        }
    }
}
