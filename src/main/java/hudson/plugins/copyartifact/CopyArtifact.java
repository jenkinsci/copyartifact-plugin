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

import com.thoughtworks.xstream.converters.UnmarshallingContext;
import hudson.DescriptorExtensionList;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.console.HyperlinkNote;
import hudson.diagnosis.OldDataMonitor;
import hudson.matrix.MatrixBuild;
import hudson.matrix.MatrixProject;
import hudson.maven.MavenModule;
import hudson.maven.MavenModuleSet;
import hudson.maven.MavenModuleSetBuild;
import hudson.model.*;
import hudson.model.listeners.ItemListener;
import hudson.model.listeners.RunListener;
import hudson.security.SecurityRealm;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.DescribableList;
import hudson.util.FormValidation;
import hudson.util.XStream2;

import java.io.IOException;
import java.io.PrintStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import jenkins.model.Jenkins;
import org.acegisecurity.GrantedAuthority;
import org.acegisecurity.providers.UsernamePasswordAuthenticationToken;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;

/**
 * Build step to copy artifacts from another project.
 * @author Alan Harder
 */
public class CopyArtifact extends Builder {

    @Deprecated private String projectName;
    private String project;
    private String parameters;
    private final String filter, target;
    private /*almost final*/ BuildSelector selector;
    @Deprecated private transient Boolean stable;
    private final Boolean flatten, optional;

    @DataBoundConstructor
    public CopyArtifact(String projectName, String parameters, BuildSelector selector, String filter, String target,
                        boolean flatten, boolean optional) {
        // check the permissions only if we can
        StaplerRequest req = Stapler.getCurrentRequest();
        if (req!=null) {
            ItemGroup context = req.findAncestorObject(ItemGroup.class);
            if (context == null) context = Jenkins.getInstance();

            // Prevents both invalid values and access to artifacts of projects which this user cannot see.
            // If value is parameterized, it will be checked when build runs.
            if (projectName.indexOf('$') < 0 && Jenkins.getInstance().getItem(projectName, context, Job.class) == null)
                projectName = ""; // Ignore/clear bad value to avoid ugly 500 page
        }

        this.project = projectName;
        this.parameters = Util.fixEmptyAndTrim(parameters);
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
                obj.selector = new StatusBuildSelector(obj.stable != null && obj.stable);
                OldDataMonitor.report(context, "1.355"); // Core version# when CopyArtifact 1.2 released
            }
        }
    }

    /**
     * Returns projectName.
     * 
     * when not configured, return a deprecated field for backword compatibility.
     * 
     * @return project name
     */
    public String getProjectName() {
        return (project != null)?project:projectName;
    }
    
    /**
     * Returns whether configured in the version < 1.26, and needed to be migrated.
     * 
     * @return whether need migration from version < 1.26
     */
    public boolean isNeedMigrationFrom0125() {
        return (project == null && projectName != null && projectName.indexOf('/') >= 0);
    }
    
    public String getParameters() {
        return parameters;
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
        return flatten != null && flatten;
    }

    public boolean isOptional() {
        return optional != null && optional;
    }

    private void upgradeIfNecessary(AbstractProject<?,?> job) throws IOException {
        if (projectName != null) {
            int i = projectName.lastIndexOf('/');
            if (i != -1 && projectName.indexOf('=', i) != -1 && /* not matrix */Jenkins.getInstance().getItem(projectName, job.getParent(), Job.class) == null) {
                project = projectName.substring(0, i);
                parameters = projectName.substring(i + 1);
            } else {
                project = projectName;
                parameters = null;
            }
            Logger.getLogger(CopyArtifact.class.getName()).log(Level.INFO, "Split {0} into {1} with parameters {2}", new Object[] {projectName, project, parameters});
            projectName = null;
            job.save();
        }
    }

    @Override
    public boolean perform(AbstractBuild<?,?> build, Launcher launcher, BuildListener listener)
            throws InterruptedException, IOException {
        upgradeIfNecessary(build.getProject());
        PrintStream console = listener.getLogger();
        String expandedProject = project, expandedFilter = filter;
        try {
            EnvVars env = build.getEnvironment(listener);
            env.overrideAll(build.getBuildVariables()); // Add in matrix axes..
            expandedProject = env.expand(project);
            Job job = Jenkins.getInstance().getItem(expandedProject, build.getProject().getParent(), Job.class);
            if (job != null && !expandedProject.equals(project)
                // If projectName is parameterized, need to do permission check on source project.
                // Would like to check if user who started build has permission, but unable to get
                // Authentication object for arbitrary user.. instead, only allow use of parameters
                // to select jobs which are accessible to all authenticated users.
                && !job.getACL().hasPermission(
                        new UsernamePasswordAuthenticationToken("authenticated", "",
                                new GrantedAuthority[]{ SecurityRealm.AUTHENTICATED_AUTHORITY }),
                        Item.READ)) {
                job = null; // Disallow access
            }
            if (job == null) {
                console.println(Messages.CopyArtifact_MissingProject(expandedProject));
                return false;
            }
            Run src = selector.getBuild(job, env, parameters != null ? new ParametersBuildFilter(env.expand(parameters)) : new BuildFilter(), build);
            if (src == null) {
                console.println(Messages.CopyArtifact_MissingBuild(expandedProject));
                return isOptional();  // Fail build unless copy is optional
            }
            FilePath targetDir = build.getWorkspace(), baseTargetDir = targetDir;
            if (targetDir == null || !targetDir.exists()) {
                console.println(Messages.CopyArtifact_MissingWorkspace()); // (see JENKINS-3330)
                return isOptional();  // Fail build unless copy is optional
            }
            // Add info about the selected build into the environment
            EnvAction envData = build.getAction(EnvAction.class);
            if (envData != null) {
                envData.add(getItemGroup(build), expandedProject, src.getNumber());
            }
            if (target.length() > 0) targetDir = new FilePath(targetDir, env.expand(target));
            expandedFilter = env.expand(filter);
            if (expandedFilter.trim().length() == 0) expandedFilter = "**";

            // for backward compatibility, look up the copier as CopyMethod
            Copier copier = Jenkins.getInstance().getExtensionList(Copier.class).get(0).clone();

            if (src instanceof MavenModuleSetBuild) {
                // Copy artifacts from the build (ArchiveArtifacts build step)
                boolean ok = perform(src, build, expandedFilter, targetDir, baseTargetDir, copier, console);
                // Copy artifacts from all modules of this Maven build (automatic archiving)
                for (Run r : ((MavenModuleSetBuild)src).getModuleLastBuilds().values())
                    ok |= perform(r, build, expandedFilter, targetDir, baseTargetDir, copier, console);
                return ok;
            } else if (src instanceof MatrixBuild) {
                boolean ok = false;
                // Copy artifacts from all configurations of this matrix build
                // Use MatrixBuild.getExactRuns if available
                for (Run r : ((MatrixBuild) src).getExactRuns())
                    // Use subdir of targetDir with configuration name (like "jdk=java6u20")
                    ok |= perform(r, build, expandedFilter, targetDir.child(r.getParent().getName()),
                                  baseTargetDir, copier, console);
                return ok;
            } else {
                return perform(src, build, expandedFilter, targetDir, baseTargetDir, copier, console);
            }
        }
        catch (IOException ex) {
            Util.displayIOException(ex, listener);
            ex.printStackTrace(listener.error(
                    Messages.CopyArtifact_FailedToCopy(expandedProject, expandedFilter)));
            return false;
        }
    }

    // retrieve the "folder" (jenkins root if no folder used) for this build
    private ItemGroup getItemGroup(AbstractBuild<?, ?> build) {
        ItemGroup group = build.getProject().getParent();
        if (group instanceof Job) {
            // MatrixProject, MavenModuleSet, IvyModuleSet or comparable
            return ((Job) group).getParent();
        }
        return group;

    }


    private boolean perform(Run src, AbstractBuild<?,?> dst, String expandedFilter, FilePath targetDir,
            FilePath baseTargetDir, Copier copier, PrintStream console)
            throws IOException, InterruptedException {
        FilePath srcDir = selector.getSourceDirectory(src, console);
        if (srcDir == null) {
            return isOptional();  // Fail build unless copy is optional
        }

        copier.init(src,dst,srcDir,baseTargetDir);
        try {
            int cnt;
            if (!isFlatten())
                cnt = copier.copyAll(srcDir, expandedFilter, targetDir);
            else {
                targetDir.mkdirs();  // Create target if needed
                FilePath[] list = srcDir.list(expandedFilter);
                for (FilePath file : list)
                    copier.copyOne(file, new FilePath(targetDir, file.getName()));
                cnt = list.length;
            }

            console.println(Messages.CopyArtifact_Copied(cnt, HyperlinkNote.encodeTo('/'+ src.getParent().getUrl(), src.getParent().getFullDisplayName()),
                    HyperlinkNote.encodeTo('/'+src.getUrl(), Integer.toString(src.getNumber()))));
            // Fail build if 0 files copied unless copy is optional
            return cnt > 0 || isOptional();
        } finally {
            copier.end();
        }
    }

    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {

        public FormValidation doCheckProjectName(
                @AncestorInPath AbstractItem anc, @QueryParameter String value) {
            // Require CONFIGURE permission on this project
            if (!anc.hasPermission(Item.CONFIGURE)) return FormValidation.ok();
            FormValidation result;
            Item item = Jenkins.getInstance().getItem(value, anc.getParent());
            if (item != null)
                result = item instanceof MavenModuleSet
                       ? FormValidation.warning(Messages.CopyArtifact_MavenProject())
                       : (item instanceof MatrixProject
                          ? FormValidation.warning(Messages.CopyArtifact_MatrixProject())
                          : FormValidation.ok());
            else if (value.indexOf('$') >= 0)
                result = FormValidation.warning(Messages.CopyArtifact_ParameterizedName());
            else
                result = FormValidation.error(
                    hudson.tasks.Messages.BuildTrigger_NoSuchProject(
                        value, AbstractProject.findNearest(value).getName()));
            return result;
        }

        public boolean isApplicable(Class<? extends AbstractProject> clazz) {
            return true;
        }

        public String getDisplayName() {
            return Messages.CopyArtifact_DisplayName();
        }

        public DescriptorExtensionList<BuildSelector,Descriptor<BuildSelector>> getBuildSelectors() {
            return Hudson.getInstance().<BuildSelector,Descriptor<BuildSelector>>getDescriptorList(BuildSelector.class);
        }
        
        /**
         * @return a short HTML message prompting to migrate settings from version < 1.26
         */
        public String getMessageForMigrationFrom0125() {
            String helpUrl = String.format("%s%s", Jenkins.getInstance().getRootUrl(), getHelpFile("migrationFrom0125"));
            return FormValidation.warningWithMarkup(Messages.CopyArtifact_MessageForMigrationFrom0125(helpUrl)).renderHtml();
        }
    }

    // Listen for project renames and update property here if needed.
    @Extension
    public static final class ListenerImpl extends ItemListener {
        @Override
        public void onRenamed(Item item, String oldName, String newName) {
            String oldFullName = Items.getCanonicalName(item.getParent(), oldName);
            String newFullName = Items.getCanonicalName(item.getParent(), newName);
            for (AbstractProject<?,?> project
                    : Hudson.getInstance().getAllItems(AbstractProject.class)) {
                try {
                for (CopyArtifact ca : getCopiers(project)) {
                    String projectName = ca.getProjectName();

                    String suffix = "";
                    // Support rename for "MatrixJobName/AxisName=value" type of name
                    int i = projectName.indexOf('=');
                    if (i > 0) {
                        int end = projectName.substring(0,i).lastIndexOf('/');
                        suffix = projectName.substring(end);
                        projectName = projectName.substring(0, end);
                    }

                    ItemGroup context = project.getParent();
                    String newProjectName = Items.computeRelativeNamesAfterRenaming(oldFullName, newFullName, projectName, context);
                    if (!projectName.equals(newProjectName)) {
                        ca.project = newProjectName + suffix;
                        project.save();
                    }
                }
                } catch (IOException ex) {
                    Logger.getLogger(ListenerImpl.class.getName()).log(Level.WARNING,
                            "Failed to resave project " + project.getName()
                            + " for project rename in CopyArtifact build step ("
                            + oldName + " =>" + newName + ")", ex);
                }
            }
        }

        private static List<CopyArtifact> getCopiers(AbstractProject<?,?> project) throws IOException {
            DescribableList<Builder,Descriptor<Builder>> list =
                    project instanceof Project ? ((Project<?,?>)project).getBuildersList()
                      : (project instanceof MatrixProject ?
                          ((MatrixProject)project).getBuildersList() : null);
            if (list == null) return Collections.emptyList();
            List<CopyArtifact> copiers = list.getAll(CopyArtifact.class);
            for (CopyArtifact copier : copiers) {
                copier.upgradeIfNecessary(project);
            }
            return copiers;
        }
    }

    // Listen for new builds and add EnvAction in any that use CopyArtifact build step
    @Extension
    public static final class CopyArtifactRunListener extends RunListener<Build> {
        public CopyArtifactRunListener() {
            super(Build.class);
        }

        @Override
        public void onStarted(Build r, TaskListener listener) {
            if (((Build<?,?>)r).getProject().getBuildersList().get(CopyArtifact.class) != null)
                r.addAction(new EnvAction());
        }
    }
    
    private static class EnvAction implements EnvironmentContributingAction {
        // Decided not to record this data in build.xml, so marked transient:
        private transient Map<String,String> data = new HashMap<String,String>();

        private void add(ItemGroup ctx, String projectName, int buildNumber) {
            if (data==null) return;
            Item item = getProject(ctx, projectName);
            // Use full name if configured with absolute path
            // and relative otherwise
            projectName = projectName.startsWith("/") ? item.getFullName() : item.getRelativeNameFrom(ctx);
            data.put("COPYARTIFACT_BUILD_NUMBER_"
                       + projectName.toUpperCase().replaceAll("[^A-Z]+", "_"), // Only use letters and _
                     Integer.toString(buildNumber));
        }

        private Job getProject(ItemGroup ctx, String projectName) {
            String[] parts = projectName.split("/");
            if (projectName.startsWith("/")) ctx = Jenkins.getInstance();
            for (String part : parts) {
                if (part.length() == 0) continue;
                if (part.equals("..")) {
                    ctx = ((Item) ctx).getParent();
                    continue;
                }
                Item i = ctx.getItem(part);
                if (i instanceof Job) return (Job) i;
                ctx = (ItemGroup) i;
            }
            return null;
        }

        public void buildEnvVars(AbstractBuild<?,?> build, EnvVars env) {
            if (data!=null) env.putAll(data);
        }

        public String getIconFileName() { return null; }
        public String getDisplayName() { return null; }
        public String getUrlName() { return null; }
    }
}
