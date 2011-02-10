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
import hudson.diagnosis.OldDataMonitor;
import hudson.matrix.MatrixBuild;
import hudson.matrix.MatrixProject;
import hudson.maven.MavenModuleSet;
import hudson.maven.MavenModuleSetBuild;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BooleanParameterValue;
import hudson.model.Build;
import hudson.model.BuildListener;
import hudson.model.Descriptor;
import hudson.model.EnvironmentContributingAction;
import hudson.model.Hudson;
import hudson.model.Job;
import hudson.model.Item;
import hudson.model.ParameterValue;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.Project;
import hudson.model.Run;
import hudson.model.StringParameterValue;
import hudson.model.TaskListener;
import hudson.model.listeners.ItemListener;
import hudson.model.listeners.RunListener;
import hudson.security.AccessControlled;
import hudson.security.SecurityRealm;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.DescribableList;
import hudson.util.FormValidation;
import hudson.util.XStream2;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.acegisecurity.GrantedAuthority;
import org.acegisecurity.providers.UsernamePasswordAuthenticationToken;
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
        // Prevents both invalid values and access to artifacts of projects which this user cannot see.
        // If value is parameterized, it will be checked when build runs.
        if (projectName.indexOf('$') < 0 && new JobResolver(projectName).job == null)
            projectName = ""; // Ignore/clear bad value to avoid ugly 500 page
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
            JobResolver job = new JobResolver(expandedProject);
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
            Run run = selector.getBuild(job.job, job.getApplicableBuilds(), env);
            if (run == null) {
                console.println(Messages.CopyArtifact_MissingBuild(expandedProject));
                return isOptional();  // Fail build unless copy is optional
            }
            FilePath targetDir = build.getWorkspace(), baseTargetDir = targetDir;
            if (targetDir == null || !targetDir.exists()) {
                console.println(Messages.CopyArtifact_MissingWorkspace()); // (see HUDSON-3330)
                return isOptional();  // Fail build unless copy is optional
            }
            // Add info about the selected build into the environment
            EnvAction envData = build.getAction(EnvAction.class);
            if (envData != null) {
                envData.add(expandedProject, run.getNumber());
            }
            if (target.length() > 0) targetDir = new FilePath(targetDir, env.expand(target));
            expandedFilter = env.expand(filter);
            if (expandedFilter.trim().length() == 0) expandedFilter = "**";
            CopyMethod copier = Hudson.getInstance().getExtensionList(CopyMethod.class).get(0);

            if (run instanceof MavenModuleSetBuild) {
                // Copy artifacts from the build (ArchiveArtifacts build step)
                boolean ok = perform(run, expandedFilter, targetDir, baseTargetDir, copier, console);
                // Copy artifacts from all modules of this Maven build (automatic archiving)
                for (Run r : ((MavenModuleSetBuild)run).getModuleLastBuilds().values())
                    ok |= perform(r, expandedFilter, targetDir, baseTargetDir, copier, console);
                return ok;
            } else if (run instanceof MatrixBuild) {
                boolean ok = false;
                // Copy artifacts from all configurations of this matrix build
                for (Run r : ((MatrixBuild)run).getRuns())
                    // Use subdir of targetDir with configuration name (like "jdk=java6u20")
                    ok |= perform(r, expandedFilter, targetDir.child(r.getParent().getName()),
                                  baseTargetDir, copier, console);
                return ok;
            } else {
                return perform(run, expandedFilter, targetDir, baseTargetDir, copier, console);
            }
        }
        catch (IOException ex) {
            Util.displayIOException(ex, listener);
            ex.printStackTrace(listener.error(
                    Messages.CopyArtifact_FailedToCopy(expandedProject, expandedFilter)));
            return false;
        }
    }

    private boolean perform(Run run, String expandedFilter, FilePath targetDir,
            FilePath baseTargetDir, CopyMethod copier, PrintStream console)
            throws IOException, InterruptedException {
        // Check special case for copying from workspace instead of artifacts:
        FilePath srcDir = (selector instanceof WorkspaceSelector && run instanceof AbstractBuild)
                        ? ((AbstractBuild)run).getWorkspace() : new FilePath(run.getArtifactsDir());
        if (srcDir == null || !srcDir.exists()) {
            console.println(Messages.CopyArtifact_MissingWorkspace()); // (see HUDSON-3330)
            return isOptional();  // Fail build unless copy is optional
        }

        copier.init(srcDir, baseTargetDir);

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
        console.println(Messages.CopyArtifact_Copied(cnt, run.getFullDisplayName()));
        // Fail build if 0 files copied unless copy is optional
        return cnt > 0 || isOptional();
    }

    // Find the job from the given name; usually just a Hudson.getItemByFullName lookup,
    // but this class encapsulates additional logic like filtering on parameters.
    private static class JobResolver {
        Job<?,?> job;
        private String paramsToMatch;

        JobResolver(String projectName) {
            Hudson hudson = Hudson.getInstance();
            job = hudson.getItemByFullName(projectName, Job.class);
            if (job == null) {
                int i = projectName.indexOf('/');
                if (i > 0) {
                    Job<?,?> candidate = hudson.getItemByFullName(projectName.substring(0, i), Job.class);
                    if (candidate != null && candidate.getProperty(ParametersDefinitionProperty.class) != null) {
                        job = candidate;
                        paramsToMatch = projectName.substring(i + 1);
                    }
                }
            }
        }

        List<Run<?,?>> getApplicableBuilds() {
            // Normally just return null and BuildSelector will look at all completed builds.
            if (paramsToMatch == null) return null;

            // Collect the given parameters.
            Matcher m = Pattern.compile("(.*?)=([^,]*)(,|$)").matcher(paramsToMatch);
            List<StringParameterValue> stringMatches = new ArrayList<StringParameterValue>(5);
            List<BooleanParameterValue> booleanMatches = new ArrayList<BooleanParameterValue>(5);
            while (m.find()) {
                String name = m.group(1), value = m.group(2);
                stringMatches.add(new StringParameterValue(name, value));
                // Try Boolean if parameter value looks boolean
                if ("true".equalsIgnoreCase(value) || "1".equals(value)
                        || "yes".equalsIgnoreCase(value) || "on".equalsIgnoreCase(value)) {
                    booleanMatches.add(new BooleanParameterValue(name, true));
                }
                else if ("false".equalsIgnoreCase(value) || "0".equals(value)
                        || "no".equalsIgnoreCase(value) || "off".equalsIgnoreCase(value)) {
                    booleanMatches.add(new BooleanParameterValue(name, false));
                }
                else booleanMatches.add(null);
            }
            List<Run<?,?>> runs = new ArrayList<Run<?,?>>();
            if (stringMatches.isEmpty()) return runs;  // Unable to parse text after /
            outer:
            for (Run<?,?> run = job.getLastCompletedBuild(); run != null; run = run.getPreviousCompletedBuild()) {
                ParametersAction pa = run.getAction(ParametersAction.class);
                if (pa == null) continue;
                int i = 0;
                // All parameters must match (either as string or boolean):
                for (StringParameterValue spv : stringMatches) {
                    BooleanParameterValue bpv = booleanMatches.get(i++);
                    boolean ok = false;
                    for (ParameterValue pv : pa.getParameters()) {
                        if (spv.equals(pv) || (bpv != null && bpv.equals(pv))) {
                            ok = true;
                            break;
                        }
                    }
                    if (!ok) continue outer; // No match for this parameter so skip this build
                }
                runs.add(run);
            }
            return runs;
        }
    }

    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {

        public FormValidation doCheckProjectName(
                @AncestorInPath AccessControlled anc, @QueryParameter String value) {
            // Require CONFIGURE permission on this project
            if (!anc.hasPermission(Item.CONFIGURE)) return FormValidation.ok();
            FormValidation result;
            Item item = new JobResolver(value).job;
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
            int i = projectName.indexOf('/'); // Omit any detail after a /
            if (i > 0) projectName = projectName.substring(0, i);
            data.put("COPYARTIFACT_BUILD_NUMBER_"
                       + projectName.toUpperCase().replaceAll("[^A-Z]+", "_"), // Only use letters and _
                     Integer.toString(buildNumber));
        }

        public void buildEnvVars(AbstractBuild<?,?> build, EnvVars env) {
            env.putAll(data);
        }

        public String getIconFileName() { return null; }
        public String getDisplayName() { return null; }
        public String getUrlName() { return null; }
    }
}
