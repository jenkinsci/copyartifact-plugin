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

package hudson.plugins.copyartifact;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

import jenkins.model.Jenkins;
import net.sf.json.JSONObject;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;

import hudson.Extension;
import hudson.model.Descriptor;
import hudson.model.ParameterValue;
import hudson.model.SimpleParameterDefinition;
import hudson.model.StringParameterValue;
import hudson.plugins.copyartifact.filter.NoBuildFilter;
import hudson.plugins.copyartifact.filter.ParameterizedBuildFilter;

/**
 * Build parameter used with {@link ParameterizedBuildFilter}
 * @since 2.0
 */
public class BuildFilterParameter extends SimpleParameterDefinition {
    @Nonnull
    private final BuildFilter defaultFilter;
    
    /**
     * @param name
     * @param description
     * @param defaultFilter
     */
    @DataBoundConstructor
    public BuildFilterParameter(String name, String description, @CheckForNull BuildFilter defaultFilter) {
        super(name, description);
        this.defaultFilter = (defaultFilter != null)?defaultFilter:new NoBuildFilter();
    }

    /**
     * @return
     */
    public BuildFilter getDefaultFilter() {
        return defaultFilter;
    }
    
    /**
     * @param value
     * @return
     * @see hudson.model.SimpleParameterDefinition#createValue(java.lang.String)
     */
    @Override
    public ParameterValue createValue(String value) {
        return new StringParameterValue(getName(), value, getDescription());
    }

    /**
     * @param req
     * @param jo
     * @return
     * @see hudson.model.ParameterDefinition#createValue(org.kohsuke.stapler.StaplerRequest, net.sf.json.JSONObject)
     */
    @Override
    public ParameterValue createValue(StaplerRequest req, JSONObject jo) {
        BuildFilter filter = req.bindJSON(BuildFilter.class, jo);
        return createValue(ParameterizedBuildFilter.encodeToXml(filter));
    }
    
    /**
     * the descriptor for {@link BuildFilterParameter}
     */
    @Extension
    public static class DescriptorImpl extends ParameterDescriptor {
        /**
         * @return
         * @see hudson.model.ParameterDefinition.ParameterDescriptor#getDisplayName()
         */
        @Override
        public String getDisplayName() {
            return Messages.BuildFilterParameter_DisplayName();
        }
        
        /**
         * @param fieldName
         * @return
         * @see hudson.model.Descriptor#getHelpFile(java.lang.String)
         */
        @Override
        public String getHelpFile(String fieldName) {
            if ("defaultFilter".equals(fieldName) || "parameter".equals(fieldName)) {
                // Display the help file of `Copyartifact#getFilter` ("Build filter" field)
                // for `defaultFilter` ("Default Filter" field) in project configuration pages
                // and the value of build parameter ("Build filter for Copy Artifact" field)
                // in "This build requires parameters" pages.
                Jenkins jenkins = Jenkins.getInstance();
                Descriptor<?> d = (jenkins == null)?null:jenkins.getDescriptor(CopyArtifact.class);
                if (d != null) {
                    return d.getHelpFile("buildFilter");
                }
            }
            return super.getHelpFile(fieldName);
        }
        
        /**
         * @return descriptors of all {@link BuildFilter}s except {@link BuildFilterParameter}
         */
        public Iterable<BuildFilterDescriptor> getBuildFilterDescriptors() {
            return Iterables.filter(
                    BuildFilter.allWithNoBuildFilter(),
                    new Predicate<BuildFilterDescriptor>() {
                        @Override
                        public boolean apply(BuildFilterDescriptor d) {
                            return !d.clazz.equals(ParameterizedBuildFilter.class);
                        }
                    }
            );
        }
    }
}
