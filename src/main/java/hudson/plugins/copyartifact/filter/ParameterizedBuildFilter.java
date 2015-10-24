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

import java.util.logging.Logger;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

import jenkins.model.Jenkins;

import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;

import hudson.Extension;
import hudson.Util;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.model.Run;
import hudson.plugins.copyartifact.BuildFilter;
import hudson.plugins.copyartifact.BuildFilterDescriptor;
import hudson.plugins.copyartifact.CopyArtifactPickContext;
import hudson.util.XStream2;

/**
 * Specifies {@link BuildFilter} to use with a build parameter.
 * 
 * @since 2.0
 */
public class ParameterizedBuildFilter extends BuildFilter {
    private static final Logger LOGGER = Logger.getLogger(ParameterizedBuildFilter.class.getName());
    private static final XStream2 XSTREAM = new XStream2();

    @Nonnull
    private final String parameter;
    
    @DataBoundConstructor
    public ParameterizedBuildFilter(@CheckForNull String parameter) {
        this.parameter = Util.fixNull(parameter);
    }
    
    @Nonnull
    public String getParameter() {
        return parameter;
    }
    
    @Override
    public boolean isSelectable(Run<?, ?> candidate, CopyArtifactPickContext context) {
        String xml = context.getEnvVars().expand(getParameter());
        context.logDebug("{0}: Expanded build filter: {1}", getDisplayName(), xml);
        BuildFilter filter = getFilterFromXml(xml);
        if (filter == null) {
            context.logDebug("{0}: No filter is specified", getDisplayName());
            return true;
        }
        return filter.isSelectable(candidate, context);
    }
    
    @CheckForNull
    public static BuildFilter getFilterFromXml(@CheckForNull String xml) {
        if (StringUtils.isBlank(xml)) {
            return null;
        }
        return (BuildFilter)XSTREAM.fromXML(xml);
    }
    
    @CheckForNull
    public static String encodeToXml(@CheckForNull BuildFilter filter) {
        if (filter == null) {
            return null;
        }
        return XSTREAM.toXML(filter).replaceAll("[\n\r]+", "");
    }
    
    
    @Initializer(after=InitMilestone.PLUGINS_STARTED)
    public static void initAliases() {
        Jenkins jenkins = Jenkins.getInstance();
        if (jenkins == null) {
            LOGGER.severe("Called for initialization but Jenkins instance no longer available.");
            return;
        }
        for (BuildFilterDescriptor d : BuildFilter.all()) {
            XSTREAM.alias(d.clazz.getSimpleName(), d.clazz);
        }
    }
    
    @Extension
    public static class DescriptorImpl extends BuildFilterDescriptor {
        @Override
        public String getDisplayName() {
            return Messages.ParameterizedBuildFilter_DisplayName();
        }
    }
}
