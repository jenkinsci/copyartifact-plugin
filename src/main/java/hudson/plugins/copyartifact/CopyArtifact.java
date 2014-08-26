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
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.matrix.MatrixBuild;
import hudson.matrix.MatrixProject;
import hudson.maven.MavenBuild;
import hudson.maven.MavenModuleSet;
import hudson.maven.MavenModuleSetBuild;
import hudson.model.*;
import hudson.model.listeners.ItemListener;
import hudson.model.listeners.RunListener;
import hudson.security.ACL;
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
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import jenkins.model.Jenkins;

import jenkins.tasks.SimpleBuildStep;
import org.acegisecurity.Authentication;
import org.acegisecurity.GrantedAuthority;
import org.acegisecurity.providers.UsernamePasswordAuthenticationToken;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;

import javax.annotation.Nonnull;

/**
 * Build step to copy artifacts from another project.
 * @author Alan Harder
 */
public class CopyArtifact extends Builder implements SimpleBuildStep {

    // specifies upgradeCopyArtifact is needed to work.
    private static boolean upgradeNeeded = false;
    private static Logger LOGGER = Logger.getLogger(CopyArtifact.class.getName());

    @Deprecated private String projectName;
    private String project;
    private String parameters;
    private final String filter, target;
    private final String excludes;
    private /*almost final*/ BuildSelector selector;
    @Deprecated private transient Boolean stable;
    private final Boolean flatten, optional;
    private final boolean doNotFingerprintArtifacts;

    @Deprecated
    public CopyArtifact(String projectName, String parameters, BuildSelector selector, String filter, String target,
            boolean flatten, boolean optional) {
        this(projectName, parameters, selector, filter, target, flatten, optional, true);
    }

    @Deprecated
    public CopyArtifact(String projectName, String parameters, BuildSelector selector, String filter, String target,
            boolean flatten, boolean optional, boolean fingerprintArtifacts) {
        this(projectName, parameters, selector, filter, null, target, flatten, optional, fingerprintArtifacts);
    }

    @DataBoundConstructor
    public CopyArtifact(String projectName, String parameters, BuildSelector selector, String filter, String excludes, String target,
                        boolean flatten, boolean optional, boolean fingerprintArtifacts) {
        // check the permissions only if we can
        StaplerRequest req = Stapler.getCurrentRequest();
        if (req!=null) {
            AbstractProject<?,?> p = req.findAncestorObject(AbstractProject.class);
            if (p != null) {
                ItemGroup<?> context = p.getParent();

                // Prevents both invalid values and access to artifacts of projects which this user cannot see.
                // If value is parameterized, it will be checked when build runs.
                if (projectName.indexOf('$') < 0 && Jenkins.getInstance().getItem(projectName, context, Job.class) == null)
                    projectName = ""; // Ignore/clear bad value to avoid ugly 500 page
            }
        }

        this.project = projectName;
        this.parameters = Util.fixEmptyAndTrim(parameters);
        this.selector = selector;
        this.filter = Util.fixNull(filter).trim();
        this.excludes = Util.fixNull(excludes).trim();
        this.target = Util.fixNull(target).trim();
        this.flatten = flatten ? Boolean.TRUE : null;
        this.optional = optional ? Boolean.TRUE : null;
        this.doNotFingerprintArtifacts = !fingerprintArtifacts;
    }

    // Upgrade data from old format
    public static class ConverterImpl extends XStream2.PassthruConverter<CopyArtifact> {
        public ConverterImpl(XStream2 xstream) { super(xstream); }
        @Override protected void callback(CopyArtifact obj, UnmarshallingContext context) {
            if (obj.selector == null) {
                obj.selector = new StatusBuildSelector(obj.stable != null && obj.stable);
                OldDataMonitor.report(context, "1.355"); // Core version# when CopyArtifact 1.2 released
            }
            if (obj.isUpgradeNeeded()) {
                // A Copy Artifact to be upgraded.
                // For information of the containing project is needed, 
                // The upgrade will be performed by upgradeCopyArtifact.
                setUpgradeNeeded();
            }
        }
    }

    private static synchronized void setUpgradeNeeded() {
        if (!upgradeNeeded) {
            LOGGER.info("Upgrade for Copy Artifact is scheduled.");
            upgradeNeeded = true;
        }
    }

    // get all CopyArtifacts configured to AbstractProject. This works both for Project and MatrixProject.
    private static List<CopyArtifact> getCopyArtifactsInProject(AbstractProject<?,?> project) throws IOException {
        DescribableList<Builder,Descriptor<Builder>> list =
                project instanceof Project ? ((Project<?,?>)project).getBuildersList()
                  : (project instanceof MatrixProject ?
                      ((MatrixProject)project).getBuildersList() : null);
        if (list == null) return Collections.emptyList();
        return list.getAll(CopyArtifact.class);
    }

    @Initializer(after=InitMilestone.JOB_LOADED)
    public static void upgradeCopyArtifact() {
        if (!upgradeNeeded) {
            return;
        }
        upgradeNeeded = false;
        
        boolean isUpgraded = false;
        for (AbstractProject<?,?> project: Jenkins.getInstance().getAllItems(AbstractProject.class)) {
            try {
                for (CopyArtifact target: getCopyArtifactsInProject(project)) {
                    try {
                        if (target.upgradeIfNecessary(project)) {
                            isUpgraded = true;
                        }
                    } catch(IOException e) {
                        LOGGER.log(Level.SEVERE, String.format("Failed to upgrade CopyArtifact in %s", project.getFullName()), e);
                    }
                }
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, String.format("Failed to upgrade CopyArtifact in %s", project.getFullName()), e);
            }
        }
        
        if (!isUpgraded) {
            // No CopyArtifact is upgraded.
            LOGGER.warning("Update of CopyArtifact is scheduled, but no CopyArtifact to upgrade was found!");
        }
    }

    public String getProjectName() {
        return project;
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

    public String getExcludes() {
        return excludes;
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

    private boolean upgradeIfNecessary(AbstractProject<?,?> job) throws IOException {
        if (isUpgradeNeeded()) {
            int i = projectName.lastIndexOf('/');
            if (i != -1 && projectName.indexOf('=', i) != -1 && /* not matrix */Jenkins.getInstance().getItem(projectName, job.getParent(), Job.class) == null) {
                project = projectName.substring(0, i);
                parameters = projectName.substring(i + 1);
            } else {
                project = projectName;
                parameters = null;
            }
            LOGGER.log(Level.INFO, "Split {0} into {1} with parameters {2}", new Object[] {projectName, project, parameters});
            projectName = null;
            job.save();
            return true;
        } else {
            return false;
        }
    }

    private boolean isUpgradeNeeded() {
        return (projectName != null);
    }

    public boolean isFingerprintArtifacts() {
        return !doNotFingerprintArtifacts;
    }

    @Override
    public boolean perform(AbstractBuild<?,?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        upgradeIfNecessary(build.getProject());
        return _perform(build, build.getWorkspace(), listener, selector);
    }

    @Override
    public void perform(@Nonnull Run<?, ?> run, @Nonnull FilePath workspace, @Nonnull Launcher launcher, @Nonnull TaskListener listener) throws InterruptedException, IOException {
        if (selector != null) {
            _perform(run, workspace, listener, selector);
        } else {
            // TODO: what selector to use?
            _perform(run, workspace, listener, new WorkspaceSelector());
        }
    }

    private boolean _perform(@Nonnull Run<?, ?> run, @Nonnull FilePath workspace, @Nonnull TaskListener listener, BuildSelector buildSelector) throws InterruptedException, IOException {
        CopyParams copyParams = new CopyParams(run, workspace, listener, buildSelector);

        try {
            if (!copyParams.expand()) {
                // Something went wrong expanding the copy parameters.
                return isOptional();
            }

            // Copy...
            return perform(copyParams, listener);
        }
        catch (IOException ex) {
            Util.displayIOException(ex, listener);
            ex.printStackTrace(listener.error(
                    Messages.CopyArtifact_FailedToCopy(copyParams.expandedProject, copyParams.expandedFilter)));
            return false;
        }
    }

    private boolean perform(CopyParams copyParams, TaskListener listener) throws IOException, InterruptedException {
        Copier copier = Jenkins.getInstance().getExtensionList(Copier.class).get(0).clone();
        PrintStream console = listener.getLogger();

        if (Hudson.getInstance().getPlugin("maven-plugin") != null && (copyParams.src instanceof MavenModuleSetBuild) ) {
            // use classes in the "maven-plugin" plugin as might not be installed
            // Copy artifacts from the build (ArchiveArtifacts build step)
            boolean ok = perform(copyParams.src, copyParams.run, copyParams.expandedFilter, copyParams.expandedExcludes, copyParams.targetDir, copyParams.baseTargetDir, copier, console, copyParams.buildSelector);
            // Copy artifacts from all modules of this Maven build (automatic archiving)
            for (Iterator<MavenBuild> it = ((MavenModuleSetBuild)copyParams.src).getModuleLastBuilds().values().iterator(); it.hasNext(); ) {
                // for(Run r: ....values()) causes upcasting and loading MavenBuild compiled with jdk 1.6.
                // SEE https://wiki.jenkins-ci.org/display/JENKINS/Tips+for+optional+dependencies for details.
                Run<?,?> r = it.next();
                ok |= perform(r, copyParams.run, copyParams.expandedFilter, copyParams.expandedExcludes, copyParams.targetDir, copyParams.baseTargetDir, copier, console, copyParams.buildSelector);
            }
            return ok;
        } else if (copyParams.src instanceof MatrixBuild) {
            boolean ok = false;
            // Copy artifacts from all configurations of this matrix build
            // Use MatrixBuild.getExactRuns if available
            for (Run r : ((MatrixBuild) copyParams.src).getExactRuns())
                // Use subdir of targetDir with configuration name (like "jdk=java6u20")
                ok |= perform(r, copyParams.run, copyParams.expandedFilter, copyParams.expandedExcludes, copyParams.targetDir.child(r.getParent().getName()),
                        copyParams.baseTargetDir, copier, console, copyParams.buildSelector);
            return ok;
        } else {
            return perform(copyParams.src, copyParams.run, copyParams.expandedFilter, copyParams.expandedExcludes, copyParams.targetDir, copyParams.baseTargetDir, copier, console, copyParams.buildSelector);
        }
    }

    private class CopyParams {
        private final Run<?, ?> run;
        private final TaskListener listener;
        private final FilePath workspace;
        private final BuildSelector buildSelector;
        private String expandedProject = project;
        private String expandedFilter = filter;
        private String expandedExcludes = getExcludes();
        private Run src;
        private FilePath targetDir;
        private FilePath baseTargetDir;

        private CopyParams(Run<?, ?> run, FilePath workspace, TaskListener listener, BuildSelector buildSelector) {
            this.run = run;
            this.workspace = workspace;
            this.listener = listener;
            this.buildSelector = buildSelector;
        }

        private boolean expand() throws IOException, InterruptedException {
            PrintStream console = listener.getLogger();
            EnvVars env = getEnvVars(run, listener);

            expandedProject = env.expand(project);

            // Get the src job
            Job<?, ?> srcJob = getJob(run, expandedProject);
            if (srcJob == null) {
                console.println(Messages.CopyArtifact_MissingProject(expandedProject));
                return false;
            }

            // Get the src Run associated with the src Job
            src = getRun(srcJob, run, env);
            if (src == null) {
                console.println(Messages.CopyArtifact_MissingBuild(expandedProject));
                return false;
            }

            // Work out the target dir
            targetDir = workspace;
            if (!targetDirExists()) {
                console.println(Messages.CopyArtifact_MissingWorkspace()); // (see JENKINS-3330)
                return false;
            }
            baseTargetDir = targetDir;

            // Expand the target dir
            if (target.length() > 0) {
                targetDir = new FilePath(targetDir, env.expand(target));
            }

            // Add info about the selected build into the environment
            EnvAction envData = run.getAction(EnvAction.class);
            if (envData != null) {
                envData.add(getItemGroup(run), expandedProject, src.getNumber());
            }

            // Expand the filter
            expandedFilter = env.expand(filter);
            if (expandedFilter.trim().length() == 0) {
                expandedFilter = "**";
            }

            // Expand the excludes
            expandedExcludes = env.expand(expandedExcludes);
            if (StringUtils.isBlank(expandedExcludes)) {
                expandedExcludes = null;
            }

            return true;
        }

        private boolean targetDirExists() throws IOException, InterruptedException {
            return (targetDir != null && targetDir.exists());
        }

        private Run getRun(Job<?, ?> srcJob, Run<?, ?> build, EnvVars env) {
            BuildFilter buildFilter = parameters != null ? new ParametersBuildFilter(env.expand(parameters)) : new BuildFilter();
            return buildSelector.getBuild(srcJob, env, buildFilter, build);
        }

        private Job<?, ?> getJob(Run<?, ?> run, String expandedProjectName) {
            ItemGroup itemGroup = getItemGroup(run);

            Job<?, ?> job = Jenkins.getInstance().getItem(expandedProjectName, itemGroup, Job.class);
            if (job != null && !expandedProjectName.equals(project)
                // If projectName is parameterized, need to do permission check on source project.  No need to perform the check if not parameterized.
                && !canReadFrom(job, run)) {
                job = null; // Disallow access
            }
            return job;
        }
    }

    /**
     * Get the {@link ItemGroup} associated with the supplied {@link Run}.
     * @param run The {@link Run} instance.
     * @return The associated {@link ItemGroup}.
     */
    private ItemGroup getItemGroup(Run<?, ?> run) {
        if (run instanceof AbstractBuild) {
            return ((AbstractBuild)run).getProject().getRootProject().getParent();
        } else {
            // TODO: This feels wrong ?? :)
            return run.getParent().getParent();
        }
    }

    private boolean perform(Run src, Run<?,?> dst, String expandedFilter, String expandedExcludes, FilePath targetDir,
                            FilePath baseTargetDir, Copier copier, PrintStream console, BuildSelector buildSelector)
            throws IOException, InterruptedException {
        FilePath srcDir = buildSelector.getSourceDirectory(src, console);
        if (srcDir == null) {
            return isOptional();  // Fail build unless copy is optional
        }

        copier.init(src,dst,srcDir,baseTargetDir);
        try {
            int cnt;
            if (!isFlatten())
                cnt = copier.copyAll(srcDir, expandedFilter, expandedExcludes, targetDir, isFingerprintArtifacts());
            else {
                targetDir.mkdirs();  // Create target if needed
                FilePath[] list = srcDir.list(expandedFilter, expandedExcludes, false);
                for (FilePath file : list)
                    copier.copyOne(file, new FilePath(targetDir, file.getName()), isFingerprintArtifacts());
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

    private EnvVars getEnvVars(Run<?, ?> run, TaskListener listener) throws IOException, InterruptedException {
        EnvVars env = run.getEnvironment(listener);
        if (run instanceof AbstractBuild) {
            env.overrideAll(((AbstractBuild)run).getBuildVariables()); // Add in matrix axes..
        }
        return env;
    }

    private boolean canReadFrom(Job<?, ?> job, Run<?, ?> run) {
        if (run instanceof AbstractBuild) {
            AbstractBuild build = (AbstractBuild) run;
            if ((job instanceof AbstractProject) && CopyArtifactPermissionProperty.canCopyArtifact(
                    build.getProject().getRootProject(),
                    ((AbstractProject<?, ?>) job).getRootProject()
            )) {
                return true;
            }
        }

        Authentication a = Jenkins.getAuthentication();
        if (!ACL.SYSTEM.equals(a)) {
            // if the build does not run on SYSTEM authorization,
            // Jenkins is configured to use QueueItemAuthenticator.
            // In this case, builds are configured to run with a proper authorization
            // (for example, builds run with the authorization of the user who triggered the build),
            // and we should check access permission with that authorization.
            // QueueItemAuthenticator is available from Jenkins 1.520.
            // See also JENKINS-14999, JENKINS-16956, JENKINS-18285.
            boolean b = job.getACL().hasPermission(Item.READ);
            if (!b)
                LOGGER.fine(String.format("Refusing to copy artifact from %s to %s because %s lacks Item.READ access",job,run, a));
            return b;
        }

        // for the backward compatibility,
        // test the permission as an anonymous authenticated user.
        boolean b = job.getACL().hasPermission(
                new UsernamePasswordAuthenticationToken("authenticated", "",
                        new GrantedAuthority[]{ SecurityRealm.AUTHENTICATED_AUTHORITY }),
                Item.READ);
        if (!b)
            LOGGER.fine(String.format("Refusing to copy artifact from %s to %s because 'authenticated' lacks Item.READ access",job,run));
        return b;
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

    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {

        public FormValidation doCheckProjectName(
                @AncestorInPath AbstractProject anc, @QueryParameter String value) {
            // Require CONFIGURE permission on this project
            if (!anc.hasPermission(Item.CONFIGURE)) return FormValidation.ok();
            FormValidation result;
            Item item = Jenkins.getInstance().getItem(value, anc.getParent());
            if (item != null)
                if (Hudson.getInstance().getPlugin("maven-plugin") != null && item instanceof MavenModuleSet) {
                    result = FormValidation.warning(Messages.CopyArtifact_MavenProject());
                } else {
                    result = (item instanceof MatrixProject)
                          ? FormValidation.warning(Messages.CopyArtifact_MatrixProject())
                          : FormValidation.ok();
                }
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
            List<CopyArtifact> copiers = getCopyArtifactsInProject(project);
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

        /**
         * Retrieve root Job identified by this projectPath. For legacy reason, projectPath uses '/' as separator for
         * job name and parameters or matrix axe, so can't just use {@link Jenkins#getItemByFullName(String)}.
         * As a workaround, we split the path into parts and retrieve the item(group)s up to a Job.
         */
        private Job getProject(ItemGroup ctx, String projectPath) {
            String[] parts = projectPath.split("/");
            if (projectPath.startsWith("/")) ctx = Jenkins.getInstance();
            for (int i =0; i<parts.length; i++) {
                String part = parts[i];
                if (part.length() == 0) continue;
                if (part.equals("..")) {
                    ctx = ((Item) ctx).getParent();
                    continue;
                }
                Item item = ctx.getItem(part);
                if (item == null && i == 0) {
                    // not a relative job name, fall back to "classic" interpretation to consider absolute
                    item = Jenkins.getInstance().getItem(part);
                }
                if (item instanceof Job) return (Job) item;
                ctx = (ItemGroup) item;
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
