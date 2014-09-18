/*
 * The MIT License
 * 
 * Copyright (c) 2014 IKEDA Yasuyuki
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

import java.util.logging.Logger;

import jenkins.model.Jenkins;

import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;

import hudson.EnvVars;
import hudson.Extension;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Descriptor;
import hudson.model.Run;

/**
 * Select a build which is a downstream of a specified build.
 */
public class DownstreamBuildSelector extends BuildSelector {
    private static final Logger LOGGER = Logger.getLogger(DownstreamBuildSelector.class.getName());
    private final String upstreamProjectName;
    private final String upstreamBuildNumber;
    
    /**
     * @param upstreamProjectName
     * @param upstreamBuildNumber
     */
    @DataBoundConstructor
    public DownstreamBuildSelector(String upstreamProjectName, String upstreamBuildNumber) {
        this.upstreamProjectName = upstreamProjectName;
        this.upstreamBuildNumber = upstreamBuildNumber;
    }
    
    /**
     * @return upstream project name. May include variable expression.
     */
    public String getUpstreamProjectName() {
        return upstreamProjectName;
    }
    
    /**
     * @return upstream build number. May include variable expression.
     */
    public String getUpstreamBuildNumber() {
        return upstreamBuildNumber;
    }
    
    @Override
    protected boolean isSelectable(Run<?, ?> run, EnvVars env) {
        if (!(run instanceof AbstractBuild<?,?>)) {
            LOGGER.warning(String.format("Only applicable to AbstractBuild: but is %s.", run.getClass().getName()));
            return false;
        }
        
        String projectName = env.expand(getUpstreamProjectName());
        String buildNumber = env.expand(getUpstreamBuildNumber());
        
        if (StringUtils.isBlank(projectName)) {
            LOGGER.warning("Upstream project name gets empty.");
            return false;
        }
        
        if (StringUtils.isBlank(buildNumber)) {
            LOGGER.warning("Upstream build number gets empty.");
            return false;
        }
        
        AbstractProject<?,?> upstreamProject = Jenkins.getInstance().getItem(
                projectName,
                run.getParent(),
                AbstractProject.class
        );
        if (upstreamProject == null) {
            LOGGER.warning(String.format("Upstream project '%s' is not found.", upstreamProject));
            return false;
        }
        AbstractBuild<?,?> upstreamBuild = ((AbstractBuild<?,?>)run).getUpstreamRelationshipBuild(upstreamProject);
        if (upstreamBuild == null) {
            LOGGER.fine(String.format("No upstream build of project '%s' is found for build %s-%s.", upstreamProject, run.getParent().getFullName(), run.getDisplayName()));
            return false;
        }
        
        try {
            int number = Integer.parseInt(buildNumber);
            if (number == upstreamBuild.getNumber()) {
                // build number matches.
                return true;
            }
        } catch (NumberFormatException e) {
            // Ignore. Nothing to do.
        }
        
        if (buildNumber.equals(upstreamBuild.getDisplayName())) {
            // build name matches.
            return true;
        }
        
        LOGGER.fine(String.format("build %s-%s doesn't match %s.", run.getParent().getFullName(), run.getDisplayName(), buildNumber));
        return false;
    }
    
    @Extension
    public static final class DescriptorImpl extends Descriptor<BuildSelector> {
        @Override
        public String getDisplayName() {
            return Messages.DownstreamBuildSelector_DisplayName();
        }
    }
}
