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
import hudson.maven.MavenModuleSet;
import hudson.maven.MavenModuleSetBuild;
import hudson.model.AbstractBuild;
import hudson.model.AbstractItem;
import hudson.model.AbstractProject;
import hudson.model.Build;
import hudson.model.BuildListener;
import hudson.model.Descriptor;
import hudson.model.EnvironmentContributingAction;
import hudson.model.Hudson;
import hudson.model.ItemGroup;
import hudson.model.Job;
import hudson.model.Item;
import hudson.model.Project;
import hudson.model.Run;
import hudson.model.TaskListener;
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
import java.util.StringTokenizer;
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

    private String projectName;
    private final String filter, target;
    private /*almost final*/ BuildSelector selector;
    @Deprecated private transient Boolean stable;
    private final Boolean flatten, optional;

    @DataBoundConstructor
    public CopyArtifact(String projectName, BuildSelector selector, String filter, String target,
                        boolean flatten, boolean optional) {
        // check the permissions only if we can
        StaplerRequest req = Stapler.getCurrentRequest();
        if (req!=null) {
            ItemGroup context = req.findAncestorObject(ItemGroup.class);
            if (context == null) context = Jenkins.getInstance();

            // Prevents both invalid values and access to artifacts of projects which this user cannot see.
            // If value is parameterized, it will be checked when build runs.
            if (projectName.indexOf('$') < 0 && new JobResolver(context, projectName).job == null)
                projectName = ""; // Ignore/clear bad value to avoid ugly 500 page
        }

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
                obj.selector = new StatusBuildSelector(obj.stable != null && obj.stable);
                OldDataMonitor.report(context, "1.355"); // Core version# when CopyArtifact 1.2 released
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
        return flatten != null && flatten;
    }

    public boolean isOptional() {
        return optional != null && optional;
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
            JobResolver job = new JobResolver(build.getProject().getParent(),expandedProject);
            if (job.job != null && !expandedProject.equals(projectName)
                // If projectName is parameterized, need to do permission check on source project.
                // Would like to check if user who started build has permission, but unable to get
                // Authentication object for arbitrary user.. instead, only allow use of parameters
                // to select jobs which are accessible to all authenticated users.
                && !job.job.getACL().hasPermission(
                        new UsernamePasswordAuthenticationToken("authenticated", "",
                                new GrantedAuthority[]{ SecurityRealm.AUTHENTICATED_AUTHORITY }),
                        Item.READ)) {
                job.job = null; // Disallow access
            }
            if (job.job == null) {
                console.println(Messages.CopyArtifact_MissingProject(expandedProject));
                return false;
            }
            Run src = selector.getBuild(job.job, env, job.filter, build);
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
                envData.add(expandedProject, src.getNumber());
            }
            if (target.length() > 0) targetDir = new FilePath(targetDir, env.expand(target));
            expandedFilter = env.expand(filter);
            if (expandedFilter.trim().length() == 0) expandedFilter = "**";

            // for backward compatibility, look up the copier as CopyMethod
            Copier copier = Copier.from(Jenkins.getInstance().getExtensionList(CopyMethod.class).get(0)).clone();

            if (Hudson.getInstance().getPlugin("maven-plugin") != null && (src instanceof MavenModuleSetBuild) ) { 
            // use classes in the "maven-plugin" plugin as might not be installed
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

    private boolean perform(Run src, AbstractBuild<?,?> dst, String expandedFilter, FilePath targetDir,
            FilePath baseTargetDir, Copier copier, PrintStream console)
            throws IOException, InterruptedException {
        // Check special case for copying from workspace instead of artifacts:
        boolean useWs = (selector instanceof WorkspaceSelector && src instanceof AbstractBuild);
        FilePath srcDir = useWs ? ((AbstractBuild)src).getWorkspace()
                                : new FilePath(src.getArtifactsDir());
        if (srcDir == null || !srcDir.exists()) {
            console.println(useWs ? Messages.CopyArtifact_MissingSrcWorkspace() // (see JENKINS-3330)
                                  : Messages.CopyArtifact_MissingSrcArtifacts(srcDir));
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

    // Find the job from the given name; usually just a Hudson.getItemByFullName lookup,
    // but this class encapsulates additional logic like filtering on parameters.
    private static class JobResolver {
        Job<?,?> job;
        BuildFilter filter = new BuildFilter();

        JobResolver(ItemGroup context, String projectName) {
            job = getItem(context, projectName);
            if (job == null) {
                // Check for parameterized job with filter (see help file)
                int i = projectName.lastIndexOf('/');
                if (i > 0) {
                    Job<?,?> candidate = getItem(context, projectName.substring(0, i));
                    if (candidate != null) {
                        ParametersBuildFilter pFilter = new ParametersBuildFilter(projectName.substring(i + 1));
                        if (pFilter.isValid(candidate)) {
                            job = candidate;
                            filter = pFilter;
                        }
                    }
                }
            }
        }

        // working around a bug in Jenkins < 1.461 that accepts arbitrary bogus tokens as suffix.
        private Job getItem(ItemGroup context, String pathName) {
            Jenkins jenkins = Jenkins.getInstance();
            if (context==null)  context = jenkins;
            if (pathName==null) return null;
    
            if (pathName.startsWith("/"))   // absolute
                return jenkins.getItemByFullName(pathName,Job.class);
    
            Object/*Item|ItemGroup*/ ctx = context;
    
            StringTokenizer tokens = new StringTokenizer(pathName,"/");
            while (tokens.hasMoreTokens()) {
                String s = tokens.nextToken();
                if (s.equals("..")) {
                    if (ctx instanceof Item) {
                        ctx = ((Item)ctx).getParent();
                        continue;
                    }
    
                    ctx=null;    // can't go up further
                    break;
                }
                if (s.equals(".")) {
                    continue;
                }
    
                if (ctx instanceof ItemGroup) {
                    ItemGroup g = (ItemGroup) ctx;
                    Item i;
                    try {
                        i = g.getItem(s);
                    } catch (Exception e) {
                        // working around a bug in MatrixProject that reports IAE.
                        // With Jenkins > 1.461, this is not necessary
                        i=null;
                    }
                    if (i==null || !i.hasPermission(Item.READ)) {
                        ctx=null;    // can't go up further
                        break;
                    }
                    ctx=i;
                } else {
                    return null;
                }
            }
    
            if (ctx instanceof Job)
                return (Job)ctx;
    
            // fall back to the classic interpretation
            return jenkins.getItemByFullName(pathName,Job.class);
        }
    }

    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {

        public FormValidation doCheckProjectName(
                @AncestorInPath AbstractItem anc, @QueryParameter String value) {
            // Require CONFIGURE permission on this project
            if (!anc.hasPermission(Item.CONFIGURE)) return FormValidation.ok();
            FormValidation result;
            Item item = new JobResolver(anc.getParent(),value).job;
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

        private void add(String projectName, int buildNumber) {
            if (data==null) return;
            int i = projectName.indexOf('/'); // Omit any detail after a /
            if (i > 0) projectName = projectName.substring(0, i);
            data.put("COPYARTIFACT_BUILD_NUMBER_"
                       + projectName.toUpperCase().replaceAll("[^A-Z]+", "_"), // Only use letters and _
                     Integer.toString(buildNumber));
        }

        public void buildEnvVars(AbstractBuild<?,?> build, EnvVars env) {
            if (data!=null) env.putAll(data);
        }

        public String getIconFileName() { return null; }
        public String getDisplayName() { return null; }
        public String getUrlName() { return null; }
    }
}
