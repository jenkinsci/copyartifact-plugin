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

import hudson.AbortException;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.diagnosis.OldDataMonitor;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.matrix.MatrixProject;
import hudson.maven.MavenModuleSet;
import hudson.model.*;
import hudson.model.listeners.ItemListener;
import hudson.plugins.copyartifact.filter.NoBuildFilter;
import hudson.plugins.copyartifact.operation.AbstractCopyOperation;
import hudson.plugins.copyartifact.operation.CopyLegacyArtifactFiles;
import hudson.security.ACL;
import hudson.security.SecurityRealm;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.DescribableList;
import hudson.util.FormValidation;
import hudson.util.VariableResolver;
import hudson.util.XStream2;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
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
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Build step to copy artifacts from another project.
 * @author Alan Harder
 */
public class CopyArtifact extends Builder implements SimpleBuildStep {

    // specifies upgradeCopyArtifact is needed to work.
    private static boolean upgradeNeeded = false;
    private static Logger LOGGER = Logger.getLogger(CopyArtifact.class.getName());

    /**
     * The result of picking the build to copy from.
     */
    public static class CopyArtifactPickResult {
        public static enum Result {
            /**
             * a build is found.
             */
            Found,
            /**
             * no project (or job) is found.
             */
            ProjectNotFound,
            /**
             * a project is found but no build is found.
             */
            BuildNotFound,
        };
        
        @Nonnull
        public final Result result;
        
        @CheckForNull
        public final Job<?, ?> job;
        
        @CheckForNull
        public final Run<?, ?> build;
        
        private CopyArtifactPickResult(Result result, Job<?, ?> job, Run<?, ?> build) {
            this.result = result;
            this.job = job;
            this.build = build;
        }
        
        private static CopyArtifactPickResult found(Run<?, ?> run) {
            return new CopyArtifactPickResult(
                    Result.Found,
                    run.getParent(),
                    run
            );
        }
        
        private static CopyArtifactPickResult projectNotFound() {
            return new CopyArtifactPickResult(
                    Result.ProjectNotFound,
                    null,
                    null
            );
        }
        
        private static CopyArtifactPickResult buildNotFound(Job<?, ?> job) {
            return new CopyArtifactPickResult(
                    Result.BuildNotFound,
                    job,
                    null
            );
        }
    };

    @Deprecated private String projectName;
    private String project;
    @Deprecated transient private String parameters;
    @Deprecated transient private String filter, target;
    @Deprecated transient private String excludes;
    private /*almost final*/ BuildSelector selector;
    @Deprecated private transient Boolean stable;
    @Deprecated transient private Boolean flatten;
    private Boolean optional;
    @Deprecated transient private boolean doNotFingerprintArtifacts;
    private String resultVariableSuffix;
    private boolean verbose;
    private BuildFilter buildFilter;
    private CopyArtifactOperation operation;

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

    @Deprecated
    public CopyArtifact(String projectName, String parameters, BuildSelector selector, String filter, String excludes, String target,
                        boolean flatten, boolean optional, boolean fingerprintArtifacts) {
        this(projectName);
        setParameters(parameters);
        setSelector(selector);
        setOptional(optional);
        
        setOperation(null);
        setFilter(filter);
        setTarget(target);
        setExcludes(excludes);
        setFlatten(flatten);
        setFingerprintArtifacts(fingerprintArtifacts);
    }

    @DataBoundConstructor
    public CopyArtifact(String projectName) {
        this.project = projectName;

        // Apply defaults to all other properties.
        setSelector(null);
        setOptional(false);
        setResultVariableSuffix(null);
        setBuildFilter(null);
        setOperation(null);
    }

    /**
     * @param parameters
     * @deprecated use {@link #setBuildFilter(BuildFilter)} and {@link ParametersBuildFilter} instead.
     */
    @Deprecated
    public void setParameters(String parameters) {
        parameters = Util.fixEmptyAndTrim(parameters);
        setBuildFilter((parameters != null)?new ParametersBuildFilter(parameters):null);
        this.parameters = null;
    }

    /**
     * @param filter
     * @deprecated see {@link #setOperation(CopyArtifactOperation)} and {@link AbstractCopyOperation}.
     */
    @Deprecated
    public void setFilter(String filter) {
        CopyArtifactOperation op = getOperation();
        if (op instanceof AbstractCopyOperation) {
            ((AbstractCopyOperation)op).setIncludes(filter);
        } else {
            LOGGER.log(
                    Level.WARNING,
                    "CopyArtifact#setFilter is deprecated and not applicable {0}",
                    op.getDescriptor().getDisplayName()
            );
        }
        this.filter = null;
    }

    /**
     * @param target
     * @deprecated see {@link #setOperation(CopyArtifactOperation)} and {@link AbstractCopyOperation}.
     */
    @Deprecated
    public void setTarget(String target) {
        CopyArtifactOperation op = getOperation();
        if (op instanceof AbstractCopyOperation) {
            ((AbstractCopyOperation)op).setTargetDir(target);
        } else {
            LOGGER.log(
                    Level.WARNING,
                    "CopyArtifact#setTarget is deprecated and not applicable {0}",
                    op.getDescriptor().getDisplayName()
            );
        }
        this.target = null;
    }

    /**
     * @param excludes
     * @deprecated see {@link #setOperation(CopyArtifactOperation)} and {@link AbstractCopyOperation}.
     */
    @Deprecated
    public void setExcludes(String excludes) {
        CopyArtifactOperation op = getOperation();
        if (op instanceof AbstractCopyOperation) {
            ((AbstractCopyOperation)op).setExcludes(excludes);
        } else {
            LOGGER.log(
                    Level.WARNING,
                    "CopyArtifact#setExcludes is deprecated and not applicable {0}",
                    op.getDescriptor().getDisplayName()
            );
        }
        this.excludes = null;
    }

    @DataBoundSetter
    public void setSelector(@CheckForNull BuildSelector selector) {
        this.selector = (selector != null)?selector:new StatusBuildSelector();
    }

    /**
     * @param flatten
     * @deprecated see {@link #setOperation(CopyArtifactOperation)} and {@link AbstractCopyOperation}.
     */
    @Deprecated
    public void setFlatten(boolean flatten) {
        CopyArtifactOperation op = getOperation();
        if (op instanceof AbstractCopyOperation) {
            ((AbstractCopyOperation)op).setFlatten(flatten);
        } else {
            LOGGER.log(
                    Level.WARNING,
                    "CopyArtifact#setFlatten is deprecated and not applicable {0}",
                    op.getDescriptor().getDisplayName()
            );
        }
        this.flatten = null;
    }

    @DataBoundSetter
    public void setOptional(boolean optional) {
        this.optional = optional ? Boolean.TRUE : null;
    }

    /**
     * @param fingerprintArtifacts
     * @deprecated see {@link #setOperation(CopyArtifactOperation)} and {@link AbstractCopyOperation}.
     */
    @Deprecated
    public void setFingerprintArtifacts(boolean fingerprintArtifacts) {
        CopyArtifactOperation op = getOperation();
        if (op instanceof AbstractCopyOperation) {
            ((AbstractCopyOperation)op).setFingerprintArtifacts(fingerprintArtifacts);
        } else {
            LOGGER.log(
                    Level.WARNING,
                    "CopyArtifact#setFingerprintArtifacts is deprecated and not applicable {0}",
                    op.getDescriptor().getDisplayName()
            );
        }
        this.doNotFingerprintArtifacts = false;
    }

    /**
     * Set the suffix for variables to store copying results.
     * 
     * @param resultVariableSuffix Variable suffix to use.
     */
    @DataBoundSetter
    public void setResultVariableSuffix(String resultVariableSuffix) {
        this.resultVariableSuffix = Util.fixEmptyAndTrim(resultVariableSuffix);
    }

    /**
     * @param verbose
     * @since 2.0
     */
    @DataBoundSetter
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * @param buildFilter
     * @since 2.0
     */
    @DataBoundSetter
    public void setBuildFilter(@CheckForNull BuildFilter buildFilter) {
        this.buildFilter = (buildFilter != null)?buildFilter:new NoBuildFilter();
    }

    /**
     * @param operation
     * 
     * @since 2.0
     */
    @DataBoundSetter
    public void setOperation(@CheckForNull CopyArtifactOperation operation) {
        this.operation = (operation != null)?operation:new CopyLegacyArtifactFiles();
    }

    // Upgrade data from old format
    public static class ConverterImpl extends XStream2.PassthruConverter<CopyArtifact> {
        public ConverterImpl(XStream2 xstream) { super(xstream); }
        @Override protected void callback(CopyArtifact obj, UnmarshallingContext context) {
            if (obj.selector == null) {
                obj.setSelector(new StatusBuildSelector((obj.stable != null)?obj.stable.booleanValue():true));
                OldDataMonitor.report(context, "1.355"); // Core version# when CopyArtifact 1.2 released
            }
            if (obj.parameters != null) {
                obj.setParameters(obj.parameters);
            }
            if (obj.buildFilter == null) {
                obj.setBuildFilter(null);
            }
            if (obj.operation == null) {
                obj.setOperation(null);
                obj.setFilter(obj.filter);
                obj.setTarget(obj.target);
                obj.setExcludes(obj.excludes);
                obj.setFlatten((obj.flatten != null)?obj.flatten:false);
                obj.setFingerprintArtifacts(!obj.doNotFingerprintArtifacts);
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
        Jenkins jenkins = Jenkins.getInstance();
        if (jenkins == null) {
            LOGGER.log(Level.SEVERE, "Called for initializing, but Jenkins instance is unavailable.");
            return;
        }
        upgradeNeeded = false;
        
        boolean isUpgraded = false;
        for (AbstractProject<?,?> project: jenkins.getAllItems(AbstractProject.class)) {
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
    
    /**
     * @return
     * @deprecated use {@link #getBuildFilter()} instead.
     */
    @Deprecated
    public String getParameters() {
        return parameters;
    }

    @Deprecated
    public BuildSelector getBuildSelector() {
        return selector;
    }

    public BuildSelector getSelector() {
        return selector;
    }

    /**
     * @return
     * @deprecated use {@link #getOperation()} instead.
     */
    @Deprecated
    public String getFilter() {
        CopyArtifactOperation op = getOperation();
        if (op instanceof AbstractCopyOperation) {
            return ((AbstractCopyOperation)op).getIncludes();
        }
        return filter;
    }

    /**
     * @return
     * @deprecated use {@link #getOperation()} instead.
     */
    @Deprecated
    public String getExcludes() {
        CopyArtifactOperation op = getOperation();
        if (op instanceof AbstractCopyOperation) {
            return ((AbstractCopyOperation)op).getExcludes();
        }
        return excludes;
    }

    /**
     * @return
     * @deprecated use {@link #getOperation()} instead.
     */
    @Deprecated
    public String getTarget() {
        CopyArtifactOperation op = getOperation();
        if (op instanceof AbstractCopyOperation) {
            return ((AbstractCopyOperation)op).getTargetDir();
        }
        return target;
    }

    /**
     * @return
     * @deprecated use {@link #getOperation()} instead.
     */
    @Deprecated
    public boolean isFlatten() {
        CopyArtifactOperation op = getOperation();
        if (op instanceof AbstractCopyOperation) {
            return ((AbstractCopyOperation)op).isFlatten();
        }
        return flatten != null && flatten;
    }

    public boolean isOptional() {
        return optional != null && optional;
    }

    /**
     * @return the suffix for variables to store copying results.
     */
    public String getResultVariableSuffix() {
        return resultVariableSuffix;
    }

    /**
     * @return whether output logs for diagnostics.
     * @since 2.0
     */
    public boolean isVerbose() {
        return verbose;
    }

    /**
     * @return the filter for builds.
     * @since 2.0
     */
    @Nonnull
    public BuildFilter getBuildFilter() {
        return buildFilter;
    }

    /**
     * @return the operation performed against the target build.
     * @since 2.0
     */
    @Nonnull
    public CopyArtifactOperation getOperation() {
        return operation;
    }

    private boolean upgradeIfNecessary(AbstractProject<?,?> job) throws IOException {
        if (isUpgradeNeeded()) {
            Jenkins jenkins = Jenkins.getInstance();
            if (jenkins == null) {
                LOGGER.log(Level.SEVERE, "upgrading copyartifact is required for {0} but Jenkins instance is unavailable", job.getDisplayName());
                return false;
            }
            int i = projectName.lastIndexOf('/');
            if (i != -1 && projectName.indexOf('=', i) != -1 && /* not matrix */jenkins.getItem(projectName, job.getParent(), Job.class) == null) {
                project = projectName.substring(0, i);
                setParameters(projectName.substring(i + 1));
            } else {
                project = projectName;
                setParameters(null);
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

    /**
     * @return
     * @deprecated use {@link #getOperation()} instead.
     */
    @Deprecated
    public boolean isFingerprintArtifacts() {
        CopyArtifactOperation op = getOperation();
        if (op instanceof AbstractCopyOperation) {
            return ((AbstractCopyOperation)op).isFingerprintArtifacts();
        }
        return !doNotFingerprintArtifacts;
    }

    @Override
    public void perform(@Nonnull Run<?, ?> build, @Nonnull FilePath workspace, @Nonnull Launcher launcher, @Nonnull TaskListener listener) throws InterruptedException, IOException {
        Jenkins jenkins = Jenkins.getInstance();
        if (jenkins == null) {
            throw new AbortException("Jenkins instance is unavailable.");
        }
        if (build instanceof AbstractBuild) {
            upgradeIfNecessary(((AbstractBuild)build).getProject());
        }

        EnvVars env = build.getEnvironment(listener);
        if (build instanceof AbstractBuild) {
            env.putAll(((AbstractBuild)build).getBuildVariables()); // Add in matrix axes..
        } else {
            // Abstract#getEnvironment(TaskListener) put build parameters to
            // environments, but Run#getEnvironment(TaskListener) doesn't.
            // That means we can't retrieve build parameters from WorkflowRun
            // as it is a subclass of Run, not of AbstractBuild.
            // We need expand build parameters manually.
            // See JENKINS-26694, JENKINS-30357 for details.
            for(ParametersAction pa: build.getActions(ParametersAction.class)) {
                // We have to extract parameters manally as ParametersAction#buildEnvVars
                // (overrides EnvironmentContributingAction#buildEnvVars)
                // is applicable only for AbstractBuild.
                for(ParameterValue pv: pa.getParameters()) {
                    pv.buildEnvironment(build, env);
                }
            }
        }

        CopyArtifactPickContext pickContext = new CopyArtifactPickContext();
        pickContext.setJenkins(jenkins);
        pickContext.setCopierBuild(build);
        pickContext.setListener(listener);
        pickContext.setEnvVars(env);
        pickContext.setVerbose(isVerbose());
        
        String jobName = env.expand(getProjectName());
        pickContext.setProjectName(jobName);
        pickContext.setBuildFilter(getBuildFilter());

        CopyArtifactPickResult pick = pickBuildToCopyFrom(getSelector(), pickContext);
        switch(pick.result) {
            case ProjectNotFound:
            {
                throw new AbortException(Messages.CopyArtifact_MissingProject(jobName));
            }
            case BuildNotFound:
            {
                String message = Messages.CopyArtifact_MissingBuild(jobName);
                if (isOptional()) {
                    // just return without an error
                    pickContext.logInfo(message);
                    return;
                } else {
                    // Fail build if copy is not optional
                    throw new AbortException(message);
                }
            }
            case Found:
            {
                // nothing to do.
                break;
            }
        }

        // Add info about the selected build into the environment
        EnvAction envData = build.getAction(EnvAction.class);
        if (envData == null) {
            envData = new EnvAction();
            build.addAction(envData);
        }
        envData.add(build, pick.build, jobName, getResultVariableSuffix());
        
        CopyArtifactOperationContext copyContext = new CopyArtifactOperationContext();
        copyContext.setJenkins(jenkins);
        copyContext.setCopierBuild(build);
        copyContext.setListener(listener);
        copyContext.setEnvVars(env);
        copyContext.setVerbose(isVerbose());
        copyContext.setWorkspace(workspace);

        switch (getOperation().perform(pick.build, copyContext)) {
        case NothingToDo:
            if (!isOptional()) {
                // TODO: filter is no longer available here.
                throw new AbortException(Messages.CopyArtifact_FailedToCopy(jobName, ""));
            }
            // fall through
        case Succeess:
            // nothing to do
            break;
        }
        
    }

    /**
     * @param selector
     * @param context
     * @return
     * @since 2.0
     */
    public static CopyArtifactPickResult pickBuildToCopyFrom(BuildSelector selector, CopyArtifactPickContext context) {
        Job<?, ?> job = context.getJenkins().getItem(
                context.getProjectName(),
                getItemGroup(context.getCopierBuild()),
                Job.class
        );
        if (job != null && !canReadFrom(job, context.getCopierBuild())) {
            job = null; // Disallow access
        }
        if (job == null) {
            return CopyArtifactPickResult.projectNotFound();
        }
        
        Run<?,?> src = selector.pickBuildToCopyFrom(job, context);
        if (src == null) {
            return CopyArtifactPickResult.buildNotFound(job);
        }
        
        return CopyArtifactPickResult.found(src);
    }
    
    private static boolean canReadFrom(Job<?, ?> job, Run<?, ?> build) {
        Job<?, ?> fromJob = job;
        Job<?, ?> toJob = build.getParent();

        if (CopyArtifactPermissionProperty.canCopyArtifact(getRootProject(toJob), getRootProject(fromJob))) {
            return true;
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
                LOGGER.fine(String.format("Refusing to copy artifact from %s to %s because %s lacks Item.READ access",job,build, a));
            return b;
        }
        
        // for the backward compatibility, 
        // test the permission as an anonymous authenticated user.
        boolean b = job.getACL().hasPermission(
                new UsernamePasswordAuthenticationToken("authenticated", "",
                        new GrantedAuthority[]{ SecurityRealm.AUTHENTICATED_AUTHORITY }),
                Item.READ);
        if (!b)
            LOGGER.fine(String.format("Refusing to copy artifact from %s to %s because 'authenticated' lacks Item.READ access",job,build));
        return b;
    }

    private static Job<?, ?> getRootProject(Job<?, ?> job) {
        if (job instanceof AbstractProject) {
            return ((AbstractProject<?,?>)job).getRootProject();
        } else {
            return job;
        }
    }

    // retrieve the "folder" (jenkins root if no folder used) for this build
    private static ItemGroup getItemGroup(Run<?, ?> build) {
        return getRootProject(build.getParent()).getParent();
    }


    /**
     * Tests whether specified variable name is valid.
     * Package scope for testing purpose.
     * 
     * @param variableName
     * @return true if <code>variableName</code> is valid as a variable name.
     */
    static boolean isValidVariableName(final String variableName) {
        if(StringUtils.isBlank(variableName)) {
            return false;
        }
        
        // The pattern for variables are defined in hudson.Util.VARIABLE.
        // It's not exposed unfortunately and tests the variable
        // by actually expanding that.
        final String expected = "GOOD";
        String expanded = Util.replaceMacro(
            String.format("${%s}", variableName),
            new VariableResolver<String>() {
                @Override
                public String resolve(String name) {
                    if(variableName.equals(name)) {
                        return expected;
                    }
                    return null;
                }
            }
        );
        
        return expected.equals(expanded);
    }
    
    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {

        public FormValidation doCheckProjectName(
                @AncestorInPath Job<?,?> anc, @QueryParameter String value) {
            // JENKINS-32526: Check that it behaves gracefully for an unknown context
            if (anc == null) return FormValidation.ok(Messages.CopyArtifact_AncestorIsNull());
            // Require CONFIGURE permission on this project
            if (!anc.hasPermission(Item.CONFIGURE)) return FormValidation.ok();
            
            Jenkins jenkins = Jenkins.getInstance();
            if (jenkins == null) {
                // validation is useless if Jenkins is no longer available.
                return FormValidation.ok();
            }
            FormValidation result;
            Item item = jenkins.getItem(value, anc.getParent());
            if (item != null)
                if (jenkins.getPlugin("maven-plugin") != null && item instanceof MavenModuleSet) {
                    result = FormValidation.warning(Messages.CopyArtifact_MavenProject());
                } else {
                    result = (item instanceof MatrixProject)
                          ? FormValidation.warning(Messages.CopyArtifact_MatrixProject())
                          : FormValidation.ok();
                }
            else if (value.indexOf('$') >= 0)
                result = FormValidation.warning(Messages.CopyArtifact_ParameterizedName());
            else {
                Job<?,?> nearest = Items.findNearest(Job.class, value, anc.getParent());
                if (nearest != null) {
                result = FormValidation.error(
                    hudson.tasks.Messages.BuildTrigger_NoSuchProject(
                        value, nearest.getName()));
                } else {
                    result = FormValidation.error(hudson.tasks.Messages.BuildTrigger_NoProjectSpecified());
                }
            }
            return result;
        }

        public FormValidation doCheckResultVariableSuffix(@QueryParameter String value) {
            value = Util.fixEmptyAndTrim(value);
            if (value == null) {
                // optional field.
                return FormValidation.ok();
            }
            
            if (!isValidVariableName(value)) {
                return FormValidation.error(Messages.CopyArtifact_InvalidVariableName());
            }
            
            return FormValidation.ok();
        }

        public boolean isApplicable(Class<? extends AbstractProject> clazz) {
            return true;
        }

        public String getDisplayName() {
            return Messages.CopyArtifact_DisplayName();
        }

        public List<BuildFilterDescriptor> getBuildFilterDescriptorList() {
            return BuildFilter.allWithNoBuildFilter();
        }
    }

    // Listen for project renames and update property here if needed.
    @Extension
    public static final class ListenerImpl extends ItemListener {
        @Override
        public void onRenamed(Item item, String oldName, String newName) {
            String oldFullName = Items.getCanonicalName(item.getParent(), oldName);
            String newFullName = Items.getCanonicalName(item.getParent(), newName);
            Jenkins jenkins = Jenkins.getInstance();
            if (jenkins == null) {
                LOGGER.log(Level.SEVERE, "Jenkins instance is no longer available.");
                return;
            }
            for (AbstractProject<?,?> project
                    : jenkins.getAllItems(AbstractProject.class)) {
                try {
                for (CopyArtifact ca : getCopiers(project)) {
                    String projectName = ca.getProjectName();
                    if (projectName == null) {
                        // JENKINS-27475 (not sure why this happens).
                        continue;
                    }

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

    private static class EnvAction implements EnvironmentContributingAction {
        // Decided not to record this data in build.xml, so marked transient:
        private transient Map<String,String> data = new HashMap<String,String>();

        @Nullable
        private String calculateDefaultSuffix(@Nonnull Run<?,?> build, @Nonnull Run<?,?> src, @Nonnull String projectName) {
            ItemGroup<?> ctx = getItemGroup(build);
            Job<?,?> item = src.getParent();
            // Use full name if configured with absolute path
            // and relative otherwise
            projectName = projectName.startsWith("/") ? item.getFullName() : item.getRelativeNameFrom(ctx);
            if (projectName == null) {
                // this is a case when the copying project doesn't belong to Jenkins item tree.
                // (e.g. promotion for Promoted Builds plugin)
                LOGGER.log(
                        Level.WARNING,
                        "Failed to calculate a relative path of {0} from {2}",
                        new Object[] {
                                item.getFullName(),
                                ctx.getFullName(),
                        }
                );
                return null;
            }
            
            return  projectName.toUpperCase().replaceAll("[^A-Z]+", "_"); // Only use letters and _
        }
        
        private void add(
                @Nonnull Run<?,?> build,
                @Nonnull Run<?,?> src,
                @Nonnull String projectName,
                @Nullable String resultVariableSuffix
        ) {
            if (data==null) return;
            
            if (!isValidVariableName(resultVariableSuffix)) {
                resultVariableSuffix = calculateDefaultSuffix(build, src, projectName);
                if (resultVariableSuffix == null) {
                    return;
                }
            }
            data.put(
                String.format("COPYARTIFACT_BUILD_NUMBER_%s", resultVariableSuffix),
                Integer.toString(src.getNumber())
            );
        }

        public void buildEnvVars(AbstractBuild<?,?> build, EnvVars env) {
            if (data!=null) env.putAll(data);
        }

        public String getIconFileName() { return null; }
        public String getDisplayName() { return null; }
        public String getUrlName() { return null; }
    }
}
