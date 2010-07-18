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

import com.thoughtworks.xstream.converters.UnmarshallingContext;
import hudson.DescriptorExtensionList;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.diagnosis.OldDataMonitor;
import hudson.matrix.MatrixProject;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.model.Job;
import hudson.model.Item;
import hudson.model.Project;
import hudson.model.Run;
import hudson.model.listeners.ItemListener;
import hudson.security.AccessControlled;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.DescribableList;
import hudson.util.FormValidation;
import hudson.util.XStream2;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

/**
 * Build step to copy artifacts from another project.
 * @author Alan Harder
 */
public class CopyArtifact extends Builder {

    private String projectName;
    private final String filter, target;
    private /*almost final*/ BuildSelector selector;
    @Deprecated private transient Boolean stable;
    private final Boolean flatten, optional;

    @DataBoundConstructor
    public CopyArtifact(String projectName, BuildSelector selector, String filter, String target,
                        boolean flatten, boolean optional) {
        this.projectName = projectName;
        this.selector = selector;
        this.filter = Util.fixNull(filter).trim();
        this.target = Util.fixNull(target).trim();
        this.flatten = flatten ? Boolean.TRUE : null;
        this.optional = optional ? Boolean.TRUE : null;
    }

    // Upgrade data from old format
    public static class ConverterImpl extends XStream2.PassthruConverter<CopyArtifact> {
        public ConverterImpl(XStream2 xstream) { super(xstream); }
        @Override protected void callback(CopyArtifact obj, UnmarshallingContext context) {
            if (obj.selector == null) {
                obj.selector = new StatusBuildSelector(obj.stable != null && obj.stable.booleanValue());
                OldDataMonitor.report(context, "1.355"); // Hudson version# when CopyArtifact 1.2 released
            }
        }
    }

    public String getProjectName() {
        return projectName;
    }

    public BuildSelector getBuildSelector() {
        return selector;
    }

    public String getFilter() {
        return filter;
    }

    public String getTarget() {
        return target;
    }

    public boolean isFlatten() {
        return flatten != null && flatten.booleanValue();
    }

    public boolean isOptional() {
        return optional != null && optional.booleanValue();
    }

    @Override
    public boolean perform(AbstractBuild<?,?> build, Launcher launcher, BuildListener listener)
            throws InterruptedException {
        PrintStream console = listener.getLogger();
        String expandedProject = projectName, expandedFilter = filter;
        try {
            EnvVars env = build.getEnvironment(listener);
            env.overrideAll(build.getBuildVariables()); // Add in matrix axes..
            expandedProject = env.expand(projectName);
            Job job = Hudson.getInstance().getItemByFullName(expandedProject, Job.class);
            if (job == null) {
                console.println(Messages.CopyArtifact_MissingProject(expandedProject));
                return false;
            }
            Run run = selector.getBuild(job, env);
            if (run == null) {
                console.println(Messages.CopyArtifact_MissingBuild(expandedProject));
                return isOptional();  // Fail build unless copy is optional
            }
            File srcDir = run.getArtifactsDir();
            FilePath targetDir = build.getWorkspace();
            if (targetDir == null) {
                console.println(Messages.CopyArtifact_MissingWorkspace()); // (see HUDSON-3330)
                return isOptional();  // Fail build unless copy is optional
            }

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

            if (target.length() > 0) targetDir = new FilePath(targetDir, env.expand(target));
            expandedFilter = env.expand(filter);
            if (expandedFilter.trim().length() == 0) expandedFilter = "**";
            int cnt;
            if (!isFlatten())
                cnt = new FilePath(srcDir).copyRecursiveTo(expandedFilter, targetDir);
            else {
                targetDir.mkdirs();  // Create target if needed
                FilePath[] list = new FilePath(srcDir).list(expandedFilter);
                for (FilePath file : list)
                    file.copyTo(new FilePath(targetDir, file.getName()));
                cnt = list.length;
            }
            listener.getLogger().println(
                    Messages.CopyArtifact_Copied(cnt, run.getFullDisplayName()));
            // Fail build if 0 files copied unless copy is optional
            return cnt > 0 || isOptional();
        }
        catch (IOException ex) {
            Util.displayIOException(ex, listener);
            ex.printStackTrace(listener.error(
                    Messages.CopyArtifact_FailedToCopy(expandedProject, expandedFilter)));
            return false;
        }
    }

    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {

        public FormValidation doCheckProjectName(
                @AncestorInPath AccessControlled anc, @QueryParameter String value) {
            // Require CONFIGURE permission on this project
            if (!anc.hasPermission(Item.CONFIGURE)) return FormValidation.ok();
            if (Hudson.getInstance().getItemByFullName(value, Job.class) != null)
                return FormValidation.ok();
            if (value.indexOf('$') >= 0)
                return FormValidation.warning(Messages.CopyArtifact_ParameterizedName());
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

        public DescriptorExtensionList<BuildSelector,Descriptor<BuildSelector>> getBuildSelectors() {
            return Hudson.getInstance().getDescriptorList(BuildSelector.class);
        }
    }

    // Listen for project renames and update property here if needed.
    @Extension
    public static final class ListenerImpl extends ItemListener {
        @Override
        public void onRenamed(Item item, String oldName, String newName) {
            for (AbstractProject<?,?> project
                    : Hudson.getInstance().getAllItems(AbstractProject.class)) {
                for (CopyArtifact ca : getCopiers(project)) try {
                    if (ca.getProjectName().equals(oldName))
                        ca.projectName = newName;
                    else if (ca.getProjectName().startsWith(oldName + '/'))
                        // Support rename for "MatrixJobName/AxisName=value" type of name
                        ca.projectName = newName + ca.projectName.substring(oldName.length());
                    else continue;
                    project.save();
                } catch (IOException ex) {
                    Logger.getLogger(ListenerImpl.class.getName()).log(Level.WARNING,
                            "Failed to resave project " + project.getName()
                            + " for project rename in CopyArtifact build step ("
                            + oldName + " =>" + newName + ")", ex);
                }
            }
        }

        private static List<CopyArtifact> getCopiers(AbstractProject project) {
            DescribableList<Builder,Descriptor<Builder>> list =
                    project instanceof Project ? ((Project<?,?>)project).getBuildersList()
                      : (project instanceof MatrixProject ?
                          ((MatrixProject)project).getBuildersList() : null);
            if (list == null) return Collections.emptyList();
            return (List<CopyArtifact>)list.getAll(CopyArtifact.class);
        }
    }
}
