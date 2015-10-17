/*
 * The MIT License
 *
 * Copyright (c) 2004-2011, Sun Microsystems, Inc., Alan Harder
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

import java.io.IOException;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

import hudson.Extension;
import hudson.model.Descriptor;
import hudson.model.Job;
import hudson.model.Run;
import hudson.plugins.copyartifact.filter.AndBuildFilter;
import hudson.plugins.copyartifact.filter.NoBuildFilter;
import hudson.plugins.copyartifact.selector.Version1BuildSelector;

import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Use a parameter to specify how the build is selected.
 * @see BuildSelectorParameter
 * @author Alan Harder
 */
public class ParameterizedBuildSelector extends BuildSelector {
    private String parameterName;

    @DataBoundConstructor
    public ParameterizedBuildSelector(String parameterName) {
        this.parameterName = parameterName;
    }

    public String getParameterName() {
        return parameterName;
    }

    @CheckForNull
    private BuildSelector getSelector(@Nonnull CopyArtifactPickContext context) {
        String xml = resolveParameter(context);
        if (xml == null) {
            return null;
        }
        try {
            return BuildSelectorParameter.getSelectorFromXml(xml);
        } catch (Exception e) {
            context.logException(String.format("Failed to resolve selector: %s", xml), e);
            return null;
        }
    }
    
    @SuppressWarnings("deprecation")
    @Override
    public Run<?, ?> pickBuildToCopyFrom(Job<?, ?> job, CopyArtifactPickContext context)
            throws IOException, InterruptedException
    {
        BuildSelector selector = getSelector(context);
        if (selector == null) {
            context.logInfo("No selector was resolved.");
            return null;
        }
        if (selector instanceof Version1BuildSelector) {
            Version1BuildSelector.MigratedConfiguration conf = 
                    ((Version1BuildSelector)selector).migrateToVersion2();
            if (conf.buildFilter != null && !(conf.buildFilter instanceof NoBuildFilter)) {
                context = context.clone();
                if (context.getBuildFilter() instanceof NoBuildFilter) {
                    context.setBuildFilter(conf.buildFilter);
                } else {
                    context.setBuildFilter(new AndBuildFilter(
                            conf.buildFilter,
                            context.getBuildFilter()
                    ));
                }
            }
            if (conf.copyArtifactOperation != null) {
                context.logInfo(
                        "{0} specified {1} as copy operation, but doesn't supported by {2}."
                        + "It may not work as you expect.",
                        selector.getDisplayName(),
                        conf.copyArtifactOperation.getDescriptor().getDisplayName(),
                        getDisplayName()
                );
            }
            selector = conf.buildSelector;
        }
        return selector.pickBuildToCopyFrom(job, context);
    }
    
    /**
     * Expand the parameter and resolve it to a xstream expression.
     * <ol>
     *   <li>Considers an immediate value if contains '&lt;'.
     *       This is expected to be used in especially in workflow jobs.</li>
     *   <li>Otherwise, considers a variable expression if contains '$'.
     *       This is to keep the compatibility of usage between workflow jobs and non-workflow jobs.</li>
     *   <li>Otherwise, considers a variable name.</li>
     * </ol>
     * 
     * @param context
     * @return xstream expression.
     */
    @CheckForNull
    private String resolveParameter(@Nonnull CopyArtifactPickContext context) {
        if (StringUtils.isBlank(getParameterName())) {
            context.logInfo("Parameter name is not specified");
            return null;
        }
        if (getParameterName().contains("<")) {
            context.logDebug("{0} is considered a xstream expression", getParameterName());
            return getParameterName();
        }
        if (getParameterName().contains("$")) {
            context.logDebug("{0} is considered a variable expression", getParameterName());
            return context.getEnvVars().expand(getParameterName());
        }
        String xml = context.getEnvVars().get(getParameterName());
        if (xml == null) {
            context.logInfo("{0} is not defined", getParameterName());
        }
        return xml;
    }

    @Extension(ordinal=-20)
    public static final Descriptor<BuildSelector> DESCRIPTOR =
            new SimpleBuildSelectorDescriptor(
                ParameterizedBuildSelector.class, Messages._ParameterizedBuildSelector_DisplayName());
}
