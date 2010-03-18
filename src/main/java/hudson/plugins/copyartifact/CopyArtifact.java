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
package hudson.plugins.copyartifact;

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Hudson;
import hudson.model.Job;
import hudson.model.Item;
import hudson.model.Project;
import hudson.model.Run;
import hudson.model.listeners.ItemListener;
import hudson.security.AccessControlled;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URL;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

/**
 * Build step to copy artifacts from another project.
 * @author Alan.Harder@sun.com
 */
public class CopyArtifact extends Builder {

    private String projectName;
    private final String filter, target;
    private final Boolean stable;

    @DataBoundConstructor
    public CopyArtifact(String projectName, String filter, String target, boolean stable) {
        this.projectName = projectName;
        this.filter = Util.fixNull(filter).trim();
        this.target = Util.fixNull(target).trim();
        this.stable = stable ? Boolean.TRUE : null;
    }

    public String getProjectName() {
        return projectName;
    }

    public String getFilter() {
        return filter;
    }

    public String getTarget() {
        return target;
    }

    public boolean isStable() {
        return stable != null ? stable.booleanValue() : false;
    }

    @Override
    public boolean perform(AbstractBuild<?,?> build, Launcher launcher, BuildListener listener)
            throws InterruptedException {
        PrintStream console = listener.getLogger();
        Job job = Hudson.getInstance().getItemByFullName(projectName, Job.class);
        if (job == null) {
            console.println(Messages.CopyArtifact_MissingProject(projectName));
            return false;
        }
        Run run = stable != null && stable.booleanValue() ? job.getLastStableBuild()
                                                          : job.getLastSuccessfulBuild();
        if (run == null) {
            console.println(Messages.CopyArtifact_MissingBuild(projectName));
            return false;
        }
        File srcDir = run.getArtifactsDir();
        FilePath targetDir = build.getWorkspace();
        if (targetDir == null) {
            console.println(Messages.CopyArtifact_MissingWorkspace()); // (see HUDSON-3330)
            return false;
        }
        String expandedFilter = filter;
        try {
            // Workaround for HUDSON-5977.. this block can be removed whenever
            // copyartifact plugin raises its minimum Hudson version to whatever
            // release fixes #5977.
            // Make a call to copy a small file, to get all class-loading to happen.
            // When we copy the real stuff there won't be any classloader requests
            // coming the other direction, which due to full-buffer-deadlock problem
            // can cause slave to hang.
            URL base = Hudson.getInstance().getPluginManager()
                             .getPlugin("copyartifact").baseResourceURL;
            if (base!=null && "file".equals(base.getProtocol())) {
                FilePath tmp = targetDir.createTempDir("copyartifact", ".dir");
                new FilePath(new File(base.getPath())).copyRecursiveTo("HUDSON-5977/**", tmp);
                tmp.deleteRecursive();
            }
            // End workaround

            EnvVars env = build.getEnvironment(listener);
            if (target.length() > 0) targetDir = new FilePath(targetDir, env.expand(target));
            expandedFilter = build.getEnvironment(listener).expand(filter);
            if (expandedFilter.trim().length() == 0) expandedFilter = "**";
            int cnt = new FilePath(srcDir).copyRecursiveTo(expandedFilter, targetDir);
            listener.getLogger().println(Messages.CopyArtifact_Copied(cnt, projectName));
        }
        catch (IOException ex) {
            Util.displayIOException(ex, listener);
            ex.printStackTrace(listener.error(
                    Messages.CopyArtifact_FailedToCopy(projectName, expandedFilter)));
            return false;
        }
        return true;
    }

    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {

        public FormValidation doCheckProjectName(
                @AncestorInPath AccessControlled anc, @QueryParameter String value) {
            // Require CONFIGURE permission on this project
            if (!anc.hasPermission(Item.CONFIGURE)) return FormValidation.ok();
            if (Hudson.getInstance().getItemByFullName(value, Job.class) != null)
                return FormValidation.ok();
            return FormValidation.error(
                    hudson.tasks.Messages.BuildTrigger_NoSuchProject(
                        value, AbstractProject.findNearest(value).getName()));
        }

        public boolean isApplicable(Class<? extends AbstractProject> clazz) {
            return true;
        }

        public String getDisplayName() {
            return Messages.CopyArtifact_DisplayName();
        }
    }

    // Listen for project renames and update property here if needed.
    @Extension
    public static final class ListenerImpl extends ItemListener {
        @Override
        public void onRenamed(Item item, String oldName, String newName) {
            for (Project<?,?> project : Hudson.getInstance().getAllItems(Project.class)) {
                for (CopyArtifact ca : (List<CopyArtifact>)
                        project.getBuildersList().getAll(CopyArtifact.class)) {
                    if (ca.getProjectName().equals(oldName)) try {
                        ca.projectName = newName;
                        project.save();
                    } catch (IOException ex) {
                        Logger.getLogger(ListenerImpl.class.getName()).log(Level.WARNING,
                                "Failed to resave project " + project.getName()
                                + " for project rename in CopyArtifact build step ("
                                + oldName + " =>" + newName + ")", ex);
                    }
                }
            }
        }
    }
}
