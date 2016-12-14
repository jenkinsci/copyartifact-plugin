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

import java.util.logging.Level;
import java.util.logging.Logger;

import hudson.EnvVars;
import hudson.Extension;
import hudson.model.ParameterValue;
import hudson.model.Descriptor;
import hudson.model.Job;
import hudson.model.ParametersAction;
import hudson.model.Run;
import hudson.model.RunParameterValue;

import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Use a parameter to specify how the build is selected.
 * @see BuildSelectorParameter
 * @author Alan Harder
 */
public class ParameterizedBuildSelector extends BuildSelector {
    private String parameterName;
    private static final Logger LOG = Logger.getLogger(ParameterizedBuildSelector.class.getName());

    @DataBoundConstructor
    public ParameterizedBuildSelector(String parameterName) {
        this.parameterName = parameterName;
    }

    public String getParameterName() {
        return parameterName;
    }

    @Override
    public Run<?,?> getBuild(Job<?,?> job, EnvVars env, BuildFilter filter, Run<?,?> parent) {
        Run<?,?> run = checkForRunParameter(parent);
        if (run != null) {
            return run;
        }

        String xml = resolveParameter(env);
        if (xml == null) {
            return null;
        }
        BuildSelector selector = null;
        try {
            selector = BuildSelectorParameter.getSelectorFromXml(xml);
        } catch (Exception e) {
            LOG.log(Level.SEVERE, String.format("Failed to resolve selector: %s", xml), e);
            return null;
        }
        return selector.getBuild(job, env, filter, parent);
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
     * @param env
     * @return xstream expression.
     */
    private String resolveParameter(EnvVars env) {
        if (StringUtils.isBlank(getParameterName())) {
            LOG.log(Level.WARNING, "Parameter name is not specified");
            return null;
        }
        if (getParameterName().contains("<")) {
            LOG.log(Level.FINEST, "{0} is considered a xstream expression", getParameterName());
            return getParameterName();
        }
        if (getParameterName().contains("$")) {
            LOG.log(Level.FINEST, "{0} is considered a variable expression", getParameterName());
            return env.expand(getParameterName());
        }
        String xml = env.get(getParameterName());
        if (xml == null) {
            LOG.log(Level.WARNING, "{0} is not defined", getParameterName());
        }
        return xml;
    }
    
    public Run<?,?> checkForRunParameter(Run<?,?> parent) {
    	ParametersAction parameters = parent.getAction(ParametersAction.class);
        if (parameters == null) {
            return null;
        }

    	ParameterValue parameterValue = parameters.getParameter(parameterName);
    	
    	if (parameterValue instanceof RunParameterValue) {
    		return ((RunParameterValue) parameterValue).getRun();
    	} else {
    		return null;
    	}
    }

    @Extension(ordinal=-20)
    public static final Descriptor<BuildSelector> DESCRIPTOR =
            new SimpleBuildSelectorDescriptor(
                ParameterizedBuildSelector.class, Messages._ParameterizedBuildSelector_DisplayName());
}
