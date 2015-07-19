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

import java.util.logging.Level;
import java.util.logging.Logger;

import jenkins.model.Jenkins;

import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import hudson.EnvVars;
import hudson.Extension;
import hudson.model.AutoCompletionCandidates;
import hudson.model.Item;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Descriptor;
import hudson.model.Job;
import hudson.model.Run;
import hudson.util.FormValidation;

/**
 * Select a build which is a downstream of a specified build.
 */
public class DownstreamBuildSelector extends BuildSelector {
    private static final Logger LOGGER = Logger.getLogger(DownstreamBuildSelector.class.getName());
    private static final String COPIER_PROJECT_KEY = "___COPIER_PROJECT_KEY___";
    private final String upstreamProjectName;
    private final String upstreamBuildNumber;
    
    /**
     * @param upstreamProjectName
     * @param upstreamBuildNumber
     */
    @DataBoundConstructor
    public DownstreamBuildSelector(String upstreamProjectName, String upstreamBuildNumber) {
        this.upstreamProjectName = StringUtils.trim(upstreamProjectName);
        this.upstreamBuildNumber = StringUtils.trim(upstreamBuildNumber);
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
    public Run<?, ?> getBuild(Job<?, ?> job, EnvVars env, BuildFilter filter, Run<?, ?> parent) {
        EnvVars extendedEnv = new EnvVars(env);
        // Workaround to pass who is copier to isSelectable().
        extendedEnv.put(COPIER_PROJECT_KEY, parent.getParent().getFullName());
        return super.getBuild(job, extendedEnv, filter, parent);
    }
    
    @Override
    protected boolean isSelectable(Run<?, ?> run, EnvVars env) {
        if (!(run instanceof AbstractBuild<?,?>)) {
            LOGGER.warning(String.format("Only applicable to AbstractBuild: but is %s.", run.getClass().getName()));
            return false;
        }
        Jenkins jenkins = Jenkins.getInstance();
        if (jenkins == null) {
            // to suppress findbugs warnings.
            LOGGER.log(
                    Level.SEVERE,
                    "Jenkins instance isn't available and cannot perform copyartifact from",
                    run.getDisplayName()
            );
            return false;
        }
        
        // Workaround to retrieve who is copying.
        Job<?,?> copier = jenkins.getItemByFullName(env.get(COPIER_PROJECT_KEY), Job.class);
        if (copier != null && (copier instanceof AbstractProject<?,?>)) {
            copier = ((AbstractProject<?,?>)copier).getRootProject();
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
        
        AbstractProject<?,?> upstreamProject = jenkins.getItem(
                projectName,
                copier,
                AbstractProject.class
        );
        if (upstreamProject == null || !upstreamProject.hasPermission(Item.READ)) {
            LOGGER.warning(String.format("Upstream project '%s' is not found.", projectName));
            return false;
        }
        AbstractBuild<?,?> upstreamBuild = ((AbstractBuild<?,?>)run).getUpstreamRelationshipBuild(upstreamProject);
        if (upstreamBuild == null || !upstreamBuild.hasPermission(Item.READ)) {
            LOGGER.fine(String.format("No upstream build of project '%s' is found for build %s-%s.", upstreamProject.getFullName(), run.getParent().getFullName(), run.getDisplayName()));
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
        
        if (buildNumber.equals(upstreamBuild.getId()) || buildNumber.equals(upstreamBuild.getDisplayName())) {
            // id or display name matches.
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
        
        /**
         * @param str
         * @return whether a value contains variable expressions.
         */
        protected boolean containsVariable(String str) {
            return !StringUtils.isBlank(str) && str.indexOf('$') >= 0;
        }
        
        /**
         * Validates a form input to "Upstream Project Name"
         * 
         * @return
         */
        public FormValidation doCheckUpstreamProjectName(
                @AncestorInPath AbstractProject<?,?> project,
                @QueryParameter String upstreamProjectName
        ) {
            upstreamProjectName = StringUtils.trim(upstreamProjectName);
            if (StringUtils.isBlank(upstreamProjectName)) {
                return FormValidation.error(Messages.DownstreamBuildSelector_UpstreamProjectName_Required());
            }
            
            if (containsVariable(upstreamProjectName)) {
                return FormValidation.ok();
            }
            
            Jenkins jenkins = Jenkins.getInstance();
            if (jenkins == null) {
                // Jenkins is unavailable and validation is useless.
                return FormValidation.ok();
            }
            
            AbstractProject<?,?> upstreamProject = jenkins.getItem(
                    upstreamProjectName, project.getRootProject(), AbstractProject.class
            );
            if (upstreamProject == null || !upstreamProject.hasPermission(Item.READ)) {
                return FormValidation.error(Messages.DownstreamBuildSelector_UpstreamProjectName_NotFound());
            }
            return FormValidation.ok();
        }
        
        /**
         * Validates a form input to "Upstream Build Number"
         * 
         * @return
         */
        public FormValidation doCheckUpstreamBuildNumber(
                @AncestorInPath AbstractProject<?,?> project,
                @QueryParameter String upstreamProjectName,
                @QueryParameter String upstreamBuildNumber
        ) {
            // This is useless in almost all cases as this is usually specified with variables.
            
            upstreamProjectName = StringUtils.trim(upstreamProjectName);
            upstreamBuildNumber = StringUtils.trim(upstreamBuildNumber);
            
            if (StringUtils.isBlank(upstreamProjectName) || containsVariable(upstreamProjectName)) {
                // skip validation
                return FormValidation.ok();
            }
            
            if (StringUtils.isBlank(upstreamBuildNumber)) {
                return FormValidation.error(Messages.DownstreamBuildSelector_UpstreamBuildNumber_Required());
            }
            
            if (containsVariable(upstreamBuildNumber)) {
                return FormValidation.ok();
            }
            
            Jenkins jenkins = Jenkins.getInstance();
            if (jenkins == null) {
                // Jenkins is unavailable and validation is useless.
                return FormValidation.ok();
            }
            
            AbstractProject<?,?> upstreamProject = jenkins.getItem(
                    upstreamProjectName, project.getRootProject(), AbstractProject.class
            );
            if (upstreamProject == null || !upstreamProject.hasPermission(Item.READ)) {
                return FormValidation.ok();
            }
            
            try {
                int number = Integer.parseInt(upstreamBuildNumber);
                AbstractBuild<?,?> upstreamBuild = upstreamProject.getBuildByNumber(number);
                if (upstreamBuild != null && upstreamBuild.hasPermission(Item.READ)) {
                    // build number matches.
                    return FormValidation.ok();
                }
            } catch (NumberFormatException e) {
                // Ignore. Nothing to do.
            }
            
            {
                AbstractBuild<?,?> upstreamBuild = upstreamProject.getBuild(upstreamBuildNumber);
                if (upstreamBuild != null && upstreamBuild.hasPermission(Item.READ)) {
                    // build id matches.
                    return FormValidation.ok();
                }
            }
            
            {
                for(
                        AbstractBuild<?,?> upstreamBuild = upstreamProject.getLastCompletedBuild();
                        upstreamBuild != null;
                        upstreamBuild = upstreamBuild.getPreviousCompletedBuild()
                ) {
                    if (upstreamBuild.getDisplayName().equals(upstreamBuildNumber)) {
                        // display name matches.
                        return FormValidation.ok();
                    }
                }
            }
            
            return FormValidation.error(Messages.DownstreamBuildSelector_UpstreamBuildNumber_NotFound());
        }
        
        /**
         * Fill the project name automatically.
         * 
         * @param value
         * @param project
         * @return
         */
        public AutoCompletionCandidates doAutoCompleteUpstreamProjectName(
                @QueryParameter String value,
                @AncestorInPath AbstractProject<?,?> project
        ) {
            // Specified Item to allow to autocomplete folders (maybe confusing...).
            return AutoCompletionCandidates.ofJobNames(Item.class, value, project, project.getParent());
        }
    }
}
