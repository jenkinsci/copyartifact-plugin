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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nonnull;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.google.common.collect.Lists;

import jenkins.model.Jenkins;
import hudson.EnvVars;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.model.Run;
import hudson.plugins.copyartifact.filter.NoBuildFilter;

/**
 * Additional filter used by BuildSelector.
 * Use {@link BuildFilterDescriptor} for its descriptor.
 * @author Alan Harder
 */
public class BuildFilter extends AbstractDescribableImpl<BuildFilter> {

    /**
     * @param run
     * @param env
     * @return
     * @deprecated implement {@link #isSelectable(Run, CopyArtifactPickContext)} instead.
     */
    @Deprecated
    public boolean isSelectable(Run<?,?> run, EnvVars env) {
        return true;
    }

    /**
     * @param candidate the build to check
     * @param context the context of current copyartifact execution.
     * @return whether this build can be selected.
     * 
     * @since 2.0
     */
    public boolean isSelectable(@Nonnull Run<?, ?> candidate, @Nonnull CopyArtifactPickContext context) {
        // for backward compatibility.
        return isSelectable(candidate, context.getEnvVars());
    }
    
    /**
     * @return
     * @see hudson.model.AbstractDescribableImpl#getDescriptor()
     */
    @Override
    public BuildFilterDescriptor getDescriptor() {
        return (BuildFilterDescriptor)super.getDescriptor();
    }
    
    /**
     * @return all descriptors of {@link BuildFilter} without {@link NoBuildFilter}
     */
    public static List<BuildFilterDescriptor> all() {
        Jenkins j = Jenkins.getInstance();
        if (j == null) {
            return Collections.emptyList();
        }
        return Lists.transform(
                j.getDescriptorList(BuildFilter.class),
                new Function<Descriptor<?>, BuildFilterDescriptor>() {
                    @Override
                    public BuildFilterDescriptor apply(Descriptor<?> arg0) {
                        return (BuildFilterDescriptor)arg0;
                    }
                }
        );
    }
    
    /**
     * @return all descriptors of {@link BuildFilter} including {@link NoBuildFilter}
     */
    public static List<BuildFilterDescriptor> allWithNoBuildFilter() {
        List<BuildFilterDescriptor> allFilters = new ArrayList<BuildFilterDescriptor>(all());
        allFilters.add(0, NoBuildFilter.DESCRIPTOR);
        
        return allFilters;
    }
}
