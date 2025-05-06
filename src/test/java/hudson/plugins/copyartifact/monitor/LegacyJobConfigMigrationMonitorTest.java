/*
 * The MIT License
 *
 * Copyright (c) 2019 CloudBees, Inc.
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
package hudson.plugins.copyartifact.monitor;

import hudson.model.Computer;
import hudson.model.FreeStyleProject;
import hudson.model.Item;
import hudson.model.Job;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.Queue;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.StringParameterDefinition;
import hudson.model.StringParameterValue;
import hudson.model.User;
import hudson.model.queue.QueueTaskFuture;
import hudson.plugins.copyartifact.CopyArtifact;
import hudson.plugins.copyartifact.CopyArtifactCompatibilityMode;
import hudson.plugins.copyartifact.CopyArtifactConfiguration;
import hudson.plugins.copyartifact.CopyArtifactPermissionProperty;
import hudson.plugins.copyartifact.testutils.FileWriteBuilder;
import hudson.plugins.copyartifact.testutils.JenkinsRuleUtil;
import hudson.tasks.ArtifactArchiver;
import java.util.Arrays;
import java.util.HashSet;
import jenkins.model.Jenkins;
import jenkins.model.ParameterizedJobMixIn;
import jenkins.security.QueueItemAuthenticator;
import jenkins.security.QueueItemAuthenticatorConfiguration;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.springframework.security.core.Authentication;
import org.hamcrest.Matchers;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.recipes.LocalData;

import edu.umd.cs.findbugs.annotations.CheckForNull;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@WithJenkins
class LegacyJobConfigMigrationMonitorTest {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
        CopyArtifactConfiguration.get().setMode(CopyArtifactCompatibilityMode.MIGRATION);
    }

    private void setupGlobalQueueAuthAs(Authentication authentication) {
        QueueItemAuthenticatorConfiguration.get().getAuthenticators().clear();
        QueueItemAuthenticatorConfiguration.get().getAuthenticators().add(
                new SimpleQueueItemAuthenticator(authentication)
        );
    }

    private void setupAnonymousJob() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        MockAuthorizationStrategy auth = new MockAuthorizationStrategy();
        j.jenkins.setAuthorizationStrategy(auth);

        FreeStyleProject toBeCopied = j.createFreeStyleProject("to-be-copied");
        auth.grant(Item.READ).onItems(toBeCopied).toEveryone();
        toBeCopied.getBuildersList().add(new FileWriteBuilder("test.txt", "test"));
        toBeCopied.getPublishersList().add(new ArtifactArchiver("**"));

        FreeStyleProject copier = j.createFreeStyleProject("copier");
        copier.getBuildersList().add(new CopyArtifact(toBeCopied.getFullName()));
    }

    private void setupAuthJob() throws Exception {
        setupParamJob();
        MockAuthorizationStrategy auth = (MockAuthorizationStrategy)j.jenkins.getAuthorizationStrategy();
        auth.grant(Computer.BUILD).everywhere().toEveryone();
    }

    private void setupParamJob() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        MockAuthorizationStrategy auth = new MockAuthorizationStrategy();
        j.jenkins.setAuthorizationStrategy(auth);

        FreeStyleProject toBeCopiedAnonymous = j.createFreeStyleProject("to-be-copied_anonymous");
        auth.grant(Item.READ, Item.CONFIGURE).onItems(toBeCopiedAnonymous).toEveryone();
        toBeCopiedAnonymous.getBuildersList().add(new FileWriteBuilder("test.txt", "test"));
        toBeCopiedAnonymous.getPublishersList().add(new ArtifactArchiver("**"));

        // Originally this is configured without AuthorizationMatrixProperty,
        // which results inheriting the global configuration,
        // in contract to that other jobs doesn't inherit the global configuration
        // MockAuthorizationStrategy doesn't provide feature to refuse inheriting
        // in a specific job, so this job is configured to grant Item.READ to everyone,
        // which was originally configured in the global configuration.
        FreeStyleProject toBeCopiedNoauth = j.createFreeStyleProject("to-be-copied_noauth");
        auth.grant(Item.READ).onItems(toBeCopiedNoauth).toEveryone();
        toBeCopiedNoauth.getBuildersList().add(new FileWriteBuilder("test.txt", "test"));
        toBeCopiedNoauth.getPublishersList().add(new ArtifactArchiver("**"));

        FreeStyleProject toBeCopiedRestricted = j.createFreeStyleProject("to-be-copied_restricted");
        auth.grant(Item.READ).onItems(toBeCopiedRestricted).to("leader", "admin");
        toBeCopiedRestricted.getBuildersList().add(new FileWriteBuilder("test.txt", "test"));
        toBeCopiedRestricted.getPublishersList().add(new ArtifactArchiver("**"));

        FreeStyleProject copier = j.createFreeStyleProject("copier");
        copier.addProperty(new ParametersDefinitionProperty(
            new StringParameterDefinition("sourceProject", "")
        ));
        copier.getBuildersList().add(new CopyArtifact("${sourceProject}"));
    }

    private void setupRestrictedJob() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        MockAuthorizationStrategy auth = new MockAuthorizationStrategy();
        j.jenkins.setAuthorizationStrategy(auth);

        FreeStyleProject toBeCopied = j.createFreeStyleProject("to-be-copied");
        auth.grant(Item.READ).onItems(toBeCopied).to("admin");
        toBeCopied.getBuildersList().add(new FileWriteBuilder("test.txt", "test"));
        toBeCopied.getPublishersList().add(new ArtifactArchiver("**"));

        FreeStyleProject copier = j.createFreeStyleProject("copier");
        copier.getBuildersList().add(new CopyArtifact(toBeCopied.getFullName()));
    }

    private void setupWorkflowJob() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        MockAuthorizationStrategy auth = new MockAuthorizationStrategy()
            .grant(Jenkins.ADMINISTER).everywhere().to("admin")
            .grant(Jenkins.READ).onRoot().toEveryone()
            .grant(Computer.BUILD).everywhere().toEveryone();
        j.jenkins.setAuthorizationStrategy(auth);

        FreeStyleProject toBeCopiedAnonymous = j.createFreeStyleProject("to-be-copied_anonymous");
        auth.grant(Item.READ, Item.CONFIGURE).onItems(toBeCopiedAnonymous).toEveryone();
        toBeCopiedAnonymous.getBuildersList().add(new FileWriteBuilder("test.txt", "test"));
        toBeCopiedAnonymous.getPublishersList().add(new ArtifactArchiver("**"));

        // Originally this is configured without AuthorizationMatrixProperty,
        // which results inheriting the global configuration,
        // in contract to that other jobs doesn't inherit the global configuration
        // MockAuthorizationStrategy doesn't provide feature to refuse inheriting
        // in a specific job, so this job is configured to grant Item.READ to everyone,
        // which was originally configured in the global configuration.
        FreeStyleProject toBeCopiedNoauth = j.createFreeStyleProject("to-be-copied_noauth");
        auth.grant(Item.READ).onItems(toBeCopiedNoauth).toEveryone();
        toBeCopiedNoauth.getBuildersList().add(new FileWriteBuilder("test.txt", "test"));
        toBeCopiedNoauth.getPublishersList().add(new ArtifactArchiver("**"));

        FreeStyleProject toBeCopiedRestricted = j.createFreeStyleProject("to-be-copied_restricted");
        auth.grant(Item.READ).onItems(toBeCopiedRestricted).to("leader", "admin");
        toBeCopiedRestricted.getBuildersList().add(new FileWriteBuilder("test.txt", "test"));
        toBeCopiedRestricted.getPublishersList().add(new ArtifactArchiver("**"));

        WorkflowJob copierAnonymous = JenkinsRuleUtil.createWorkflow(
                j,
            "copier_anonymous",
            "copyArtifacts(projectName: 'to-be-copied_anonymous');"
        );
        copierAnonymous.addProperty(new ParametersDefinitionProperty(
            new StringParameterDefinition("sourceProject", "")
        ));

        WorkflowJob copierNoauth = JenkinsRuleUtil.createWorkflow(
                j,
            "copier_noauth",
            "copyArtifacts(projectName: 'to-be-copied_noauth');"
        );
        copierNoauth.addProperty(new ParametersDefinitionProperty(
            new StringParameterDefinition("sourceProject", "")
        ));

        WorkflowJob copierParam = JenkinsRuleUtil.createWorkflow(
                j,
            "copier_param",
            "copyArtifacts(projectName: '${sourceProject}');"
        );
        copierParam.addProperty(new ParametersDefinitionProperty(
            new StringParameterDefinition("sourceProject", "")
        ));

        WorkflowJob copierRestricted = JenkinsRuleUtil.createWorkflow(
                j,
            "copier_restricted",
            "copyArtifacts(projectName: 'to-be-copied_restricted');"
        );
        copierRestricted.addProperty(new ParametersDefinitionProperty(
            new StringParameterDefinition("sourceProject", "")
        ));
    }

    @Test
    @LocalData("publicJob")
    void publicJob_copy_legacy_migration() throws Exception {
        String fileNameToCreate = "test.txt";

        assertNoLegacyMonitor();

        FreeStyleProject projectToBeCopied = j.jenkins.getItem("to-be-copied", j.jenkins, FreeStyleProject.class);
        FreeStyleProject projectCopier = j.jenkins.getItem("copier", j.jenkins, FreeStyleProject.class);

        j.assertBuildStatus(Result.SUCCESS, projectToBeCopied.scheduleBuild2(0));
        assertThat(projectToBeCopied.getLastSuccessfulBuild().getWorkspace().list(), hasSize(1));
        assertTrue(projectToBeCopied.getLastSuccessfulBuild().getWorkspace().child(fileNameToCreate).exists(), "The file should have been created");
        assertThat(projectToBeCopied.getLastSuccessfulBuild().getArtifacts(), hasSize(1));

        j.assertBuildStatus(Result.SUCCESS, projectCopier.scheduleBuild2(0));

        assertThat(projectCopier.getLastSuccessfulBuild().getWorkspace().list(), hasSize(1));

        assertNoLegacyMonitor();
    }

    @Test
    void anonymousJob_copy_legacy_migration() throws Exception {
        setupAnonymousJob();

        String fileNameToCreate = "test.txt";

        assertNoLegacyMonitor();

        FreeStyleProject projectToBeCopied = j.jenkins.getItem("to-be-copied", j.jenkins, FreeStyleProject.class);
        FreeStyleProject projectCopier = j.jenkins.getItem("copier", j.jenkins, FreeStyleProject.class);

        j.assertBuildStatus(Result.SUCCESS, projectToBeCopied.scheduleBuild2(0));
        assertThat(projectToBeCopied.getLastSuccessfulBuild().getWorkspace().list(), hasSize(1));
        assertTrue(projectToBeCopied.getLastSuccessfulBuild().getWorkspace().child(fileNameToCreate).exists(), "The file should have been created");
        assertThat(projectToBeCopied.getLastSuccessfulBuild().getArtifacts(), hasSize(1));

        j.assertBuildStatus(Result.SUCCESS, projectCopier.scheduleBuild2(0));

        assertThat(projectCopier.getLastSuccessfulBuild().getWorkspace().list(), hasSize(1));

        assertNoLegacyMonitor();
    }

    @Test
    void restrictedJob_copy_legacy_migration() throws Exception {
        setupRestrictedJob();

        String fileNameToCreate = "test.txt";

        assertNoLegacyMonitor();

        FreeStyleProject projectToBeCopied = j.jenkins.getItem("to-be-copied", j.jenkins, FreeStyleProject.class);
        FreeStyleProject projectCopier = j.jenkins.getItem("copier", j.jenkins, FreeStyleProject.class);

        j.assertBuildStatus(Result.SUCCESS, projectToBeCopied.scheduleBuild2(0));
        assertThat(projectToBeCopied.getLastSuccessfulBuild().getWorkspace().list(), hasSize(1));
        assertTrue(projectToBeCopied.getLastSuccessfulBuild().getWorkspace().child(fileNameToCreate).exists(), "The file should have been created");
        assertThat(projectToBeCopied.getLastSuccessfulBuild().getArtifacts(), hasSize(1));

        j.assertBuildStatus(Result.SUCCESS, projectCopier.scheduleBuild2(0));

        assertThat(projectCopier.getLastSuccessfulBuild().getWorkspace().list(), hasSize(1));

        assertLegacyMonitorHasOnly_andClear("to-be-copied");
    }

    @Test
    void restrictedJob_copy_legacy_production() throws Exception {
        setupRestrictedJob();

        CopyArtifactConfiguration.get().setMode(CopyArtifactCompatibilityMode.PRODUCTION);

        String fileNameToCreate = "test.txt";

        assertNoLegacyMonitor();

        FreeStyleProject projectToBeCopied = j.jenkins.getItem("to-be-copied", j.jenkins, FreeStyleProject.class);
        FreeStyleProject projectCopier = j.jenkins.getItem("copier", j.jenkins, FreeStyleProject.class);

        j.assertBuildStatus(Result.SUCCESS, projectToBeCopied.scheduleBuild2(0));
        assertThat(projectToBeCopied.getLastSuccessfulBuild().getWorkspace().list(), hasSize(1));
        assertTrue(projectToBeCopied.getLastSuccessfulBuild().getWorkspace().child(fileNameToCreate).exists(), "The file should have been created");
        assertThat(projectToBeCopied.getLastSuccessfulBuild().getArtifacts(), hasSize(1));

        j.assertBuildStatus(Result.FAILURE, projectCopier.scheduleBuild2(0));

        // Production mode does not care about monitoring the legacy jobs
        assertNoLegacyMonitor();
    }

    @Test
    void paramJob_copy_legacy_migration() throws Exception {
        setupParamJob();

        // Migration mode
        paramJob_copy_legacy();
    }

    @Test
    void paramJob_copy_legacy_production() throws Exception {
        setupParamJob();

        CopyArtifactConfiguration.get().setMode(CopyArtifactCompatibilityMode.PRODUCTION);

        // Production mode does not change because it's a parameterized target
        // that was already checked at runtime before
        paramJob_copy_legacy();
    }

    private void paramJob_copy_legacy() throws Exception {
        assertNoLegacyMonitor();

        FreeStyleProject projectToBeCopied_noAuth = j.jenkins.getItem("to-be-copied_noauth", j.jenkins, FreeStyleProject.class);
        FreeStyleProject projectToBeCopied_anonymous = j.jenkins.getItem("to-be-copied_anonymous", j.jenkins, FreeStyleProject.class);
        FreeStyleProject projectToBeCopied_restricted = j.jenkins.getItem("to-be-copied_restricted", j.jenkins, FreeStyleProject.class);
        FreeStyleProject projectCopier = j.jenkins.getItem("copier", j.jenkins, FreeStyleProject.class);

        for (FreeStyleProject p : new FreeStyleProject[]{projectToBeCopied_noAuth, projectToBeCopied_anonymous, projectToBeCopied_restricted}) {
            j.assertBuildStatus(Result.SUCCESS, p.scheduleBuild2(0));
            assertThat(p.getLastSuccessfulBuild().getWorkspace().list(), hasSize(1));
            assertThat(p.getLastSuccessfulBuild().getArtifacts(), hasSize(1));
        }

        // it can be built because it has no specific authentication requirement
        assertCopyParameterizedResult(Result.SUCCESS, projectCopier, projectToBeCopied_noAuth);
        // it can be built because it allowed authenticated to build (anonymous > authenticated)
        assertCopyParameterizedResult(Result.SUCCESS, projectCopier, projectToBeCopied_anonymous);
        // cannot build because the process cannot determine if the user has access to the job or not
        // due to the missing authentication queue
        assertCopyParameterizedResult(Result.FAILURE, projectCopier, projectToBeCopied_restricted);

        assertNoLegacyMonitor();
    }

    // no migration because the project target that are composed of variables were already dynamically checked before
    @Test
    void authJob_copy_legacy_migration() throws Exception {
        setupAuthJob();

        assertNoLegacyMonitor();

        FreeStyleProject projectToBeCopied_noAuth = j.jenkins.getItem("to-be-copied_noauth", j.jenkins, FreeStyleProject.class);
        FreeStyleProject projectToBeCopied_anonymous = j.jenkins.getItem("to-be-copied_anonymous", j.jenkins, FreeStyleProject.class);
        FreeStyleProject projectToBeCopied_restricted = j.jenkins.getItem("to-be-copied_restricted", j.jenkins, FreeStyleProject.class);
        FreeStyleProject projectCopier = j.jenkins.getItem("copier", j.jenkins, FreeStyleProject.class);

        for (FreeStyleProject p : new FreeStyleProject[]{projectToBeCopied_noAuth, projectToBeCopied_anonymous, projectToBeCopied_restricted}) {
            j.assertBuildStatus(Result.SUCCESS, p.scheduleBuild2(0));
            assertThat(p.getLastSuccessfulBuild().getWorkspace().list(), hasSize(1));
            assertThat(p.getLastSuccessfulBuild().getArtifacts(), hasSize(1));
        }

        { // no authQueue
            assertCopyParameterizedResult(Result.SUCCESS, projectCopier, projectToBeCopied_noAuth);
            assertCopyParameterizedResult(Result.SUCCESS, projectCopier, projectToBeCopied_anonymous);
            // the projects as variable were already dynamically checked before
            assertCopyParameterizedResult(Result.FAILURE, projectCopier, projectToBeCopied_restricted);
        }

        assertNoLegacyMonitor();

        { // authQueue as anonymous
            setupGlobalQueueAuthAs(Jenkins.ANONYMOUS2);

            assertCopyParameterizedResult(Result.SUCCESS, projectCopier, projectToBeCopied_noAuth);
            assertCopyParameterizedResult(Result.SUCCESS, projectCopier, projectToBeCopied_anonymous);
            // the projects as variable were already dynamically checked before
            assertCopyParameterizedResult(Result.FAILURE, projectCopier, projectToBeCopied_restricted);
        }

        assertNoLegacyMonitor();

        {// authQueue as dev = no access to restricted
            setupGlobalQueueAuthAs(createUserAndImpersonate("dev"));

            assertCopyParameterizedResult(Result.SUCCESS, projectCopier, projectToBeCopied_noAuth);
            assertCopyParameterizedResult(Result.SUCCESS, projectCopier, projectToBeCopied_anonymous);
            assertCopyParameterizedResult(Result.FAILURE, projectCopier, projectToBeCopied_restricted);
        }

        assertNoLegacyMonitor();

        {// authQueue as leader (access to restricted)
            setupGlobalQueueAuthAs(createUserAndImpersonate("leader"));

            assertCopyParameterizedResult(Result.SUCCESS, projectCopier, projectToBeCopied_noAuth);
            assertCopyParameterizedResult(Result.SUCCESS, projectCopier, projectToBeCopied_anonymous);
            assertCopyParameterizedResult(Result.SUCCESS, projectCopier, projectToBeCopied_restricted);
        }

        assertNoLegacyMonitor();

        {// authQueue as admin
            setupGlobalQueueAuthAs(createUserAndImpersonate("admin"));

            assertCopyParameterizedResult(Result.SUCCESS, projectCopier, projectToBeCopied_noAuth);
            assertCopyParameterizedResult(Result.SUCCESS, projectCopier, projectToBeCopied_anonymous);
            assertCopyParameterizedResult(Result.SUCCESS, projectCopier, projectToBeCopied_restricted);
        }

        assertNoLegacyMonitor();
    }

    @Test
    void authJob_copy_legacy_production() throws Exception {
        setupAuthJob();

        CopyArtifactConfiguration.get().setMode(CopyArtifactCompatibilityMode.PRODUCTION);

        assertNoLegacyMonitor();

        FreeStyleProject projectToBeCopied_noAuth = j.jenkins.getItem("to-be-copied_noauth", j.jenkins, FreeStyleProject.class);
        FreeStyleProject projectToBeCopied_anonymous = j.jenkins.getItem("to-be-copied_anonymous", j.jenkins, FreeStyleProject.class);
        FreeStyleProject projectToBeCopied_restricted = j.jenkins.getItem("to-be-copied_restricted", j.jenkins, FreeStyleProject.class);
        FreeStyleProject projectCopier = j.jenkins.getItem("copier", j.jenkins, FreeStyleProject.class);

        for (FreeStyleProject p : new FreeStyleProject[]{projectToBeCopied_noAuth, projectToBeCopied_anonymous, projectToBeCopied_restricted}) {
            j.assertBuildStatus(Result.SUCCESS, p.scheduleBuild2(0));
            assertThat(p.getLastSuccessfulBuild().getWorkspace().list(), hasSize(1));
            assertThat(p.getLastSuccessfulBuild().getArtifacts(), hasSize(1));
        }

        { // no authQueue
            assertCopyParameterizedResult(Result.SUCCESS, projectCopier, projectToBeCopied_noAuth);
            assertCopyParameterizedResult(Result.SUCCESS, projectCopier, projectToBeCopied_anonymous);
            assertCopyParameterizedResult(Result.FAILURE, projectCopier, projectToBeCopied_restricted);
        }
        { // authQueue as anonymous
            setupGlobalQueueAuthAs(Jenkins.ANONYMOUS2);

            assertCopyParameterizedResult(Result.SUCCESS, projectCopier, projectToBeCopied_noAuth);
            assertCopyParameterizedResult(Result.SUCCESS, projectCopier, projectToBeCopied_anonymous);
            assertCopyParameterizedResult(Result.FAILURE, projectCopier, projectToBeCopied_restricted);
        }
        {// authQueue as dev
            setupGlobalQueueAuthAs(createUserAndImpersonate("dev"));

            assertCopyParameterizedResult(Result.SUCCESS, projectCopier, projectToBeCopied_noAuth);
            assertCopyParameterizedResult(Result.SUCCESS, projectCopier, projectToBeCopied_anonymous);
            assertCopyParameterizedResult(Result.FAILURE, projectCopier, projectToBeCopied_restricted);
        }
        {// authQueue as leader (access to restricted)
            setupGlobalQueueAuthAs(createUserAndImpersonate("leader"));

            assertCopyParameterizedResult(Result.SUCCESS, projectCopier, projectToBeCopied_noAuth);
            assertCopyParameterizedResult(Result.SUCCESS, projectCopier, projectToBeCopied_anonymous);
            assertCopyParameterizedResult(Result.SUCCESS, projectCopier, projectToBeCopied_restricted);
        }
        {// authQueue as admin
            setupGlobalQueueAuthAs(createUserAndImpersonate("admin"));

            assertCopyParameterizedResult(Result.SUCCESS, projectCopier, projectToBeCopied_noAuth);
            assertCopyParameterizedResult(Result.SUCCESS, projectCopier, projectToBeCopied_anonymous);
            assertCopyParameterizedResult(Result.SUCCESS, projectCopier, projectToBeCopied_restricted);
        }

        // Production mode does not care about monitoring the legacy jobs
        assertNoLegacyMonitor();
    }

    @Test
    void authJob_copy_legacy_production_property() throws Exception {
        setupAuthJob();

        CopyArtifactConfiguration.get().setMode(CopyArtifactCompatibilityMode.PRODUCTION);

        assertNoLegacyMonitor();

        FreeStyleProject projectToBeCopied_restricted = j.jenkins.getItem("to-be-copied_restricted", j.jenkins, FreeStyleProject.class);
        FreeStyleProject projectCopier = j.jenkins.getItem("copier", j.jenkins, FreeStyleProject.class);

        j.assertBuildStatus(Result.SUCCESS, projectToBeCopied_restricted.scheduleBuild2(0));
        assertThat(projectToBeCopied_restricted.getLastSuccessfulBuild().getWorkspace().list(), hasSize(1));
        assertThat(projectToBeCopied_restricted.getLastSuccessfulBuild().getArtifacts(), hasSize(1));

        // allow the copier to copy that restricted project
        projectToBeCopied_restricted.addProperty(new CopyArtifactPermissionProperty(projectCopier.getFullName()));

        { // no authQueue
            assertCopyParameterizedResult(Result.SUCCESS, projectCopier, projectToBeCopied_restricted);
        }
        { // authQueue as anonymous
            setupGlobalQueueAuthAs(Jenkins.ANONYMOUS2);

            assertCopyParameterizedResult(Result.FAILURE, projectCopier, projectToBeCopied_restricted);
        }
        {// authQueue as dev
            setupGlobalQueueAuthAs(createUserAndImpersonate("dev"));

            assertCopyParameterizedResult(Result.FAILURE, projectCopier, projectToBeCopied_restricted);
        }

        // Production mode does not care about monitoring the legacy jobs
        assertNoLegacyMonitor();
    }

    // param used, so no migration as it was already checked at runtime before
    @Test
    void workflowJob_param_copy_legacy_migration() throws Exception {
        setupWorkflowJob();

        assertNoLegacyMonitor();

        FreeStyleProject projectToBeCopied_noAuth = j.jenkins.getItem("to-be-copied_noauth", j.jenkins, FreeStyleProject.class);
        FreeStyleProject projectToBeCopied_anonymous = j.jenkins.getItem("to-be-copied_anonymous", j.jenkins, FreeStyleProject.class);
        FreeStyleProject projectToBeCopied_restricted = j.jenkins.getItem("to-be-copied_restricted", j.jenkins, FreeStyleProject.class);
        WorkflowJob projectCopier = j.jenkins.getItem("copier_param", j.jenkins, WorkflowJob.class);

        for (FreeStyleProject p : new FreeStyleProject[]{projectToBeCopied_noAuth, projectToBeCopied_anonymous, projectToBeCopied_restricted}) {
            j.assertBuildStatus(Result.SUCCESS, p.scheduleBuild2(0));
            assertThat(p.getLastSuccessfulBuild().getWorkspace().list(), hasSize(1));
            assertThat(p.getLastSuccessfulBuild().getArtifacts(), hasSize(1));
        }

        { // no authQueue
            assertCopyParameterizedResult(Result.SUCCESS, projectCopier, projectToBeCopied_noAuth);
            assertCopyParameterizedResult(Result.SUCCESS, projectCopier, projectToBeCopied_anonymous);
            // param + non reachable project
            assertCopyParameterizedResult(Result.FAILURE, projectCopier, projectToBeCopied_restricted);
        }

        assertNoLegacyMonitor();

        { // authQueue as anonymous
            setupGlobalQueueAuthAs(Jenkins.ANONYMOUS2);

            assertCopyParameterizedResult(Result.SUCCESS, projectCopier, projectToBeCopied_noAuth);
            assertCopyParameterizedResult(Result.SUCCESS, projectCopier, projectToBeCopied_anonymous);
            assertCopyParameterizedResult(Result.FAILURE, projectCopier, projectToBeCopied_restricted);
        }

        assertNoLegacyMonitor();

        {// authQueue as dev
            setupGlobalQueueAuthAs(createUserAndImpersonate("dev"));

            assertCopyParameterizedResult(Result.SUCCESS, projectCopier, projectToBeCopied_noAuth);
            assertCopyParameterizedResult(Result.SUCCESS, projectCopier, projectToBeCopied_anonymous);
            assertCopyParameterizedResult(Result.FAILURE, projectCopier, projectToBeCopied_restricted);
        }

        assertNoLegacyMonitor();

        {// authQueue as leader (access to restricted)
            setupGlobalQueueAuthAs(createUserAndImpersonate("leader"));

            assertCopyParameterizedResult(Result.SUCCESS, projectCopier, projectToBeCopied_noAuth);
            assertCopyParameterizedResult(Result.SUCCESS, projectCopier, projectToBeCopied_anonymous);
            assertCopyParameterizedResult(Result.SUCCESS, projectCopier, projectToBeCopied_restricted);
        }

        assertNoLegacyMonitor();

        {// authQueue as admin
            setupGlobalQueueAuthAs(createUserAndImpersonate("admin"));

            assertCopyParameterizedResult(Result.SUCCESS, projectCopier, projectToBeCopied_noAuth);
            assertCopyParameterizedResult(Result.SUCCESS, projectCopier, projectToBeCopied_anonymous);
            assertCopyParameterizedResult(Result.SUCCESS, projectCopier, projectToBeCopied_restricted);
        }

        assertNoLegacyMonitor();
    }

    // param used, so no migration as it was already checked at runtime before
    @Test
    void workflowJob_param_copy_legacy_production() throws Exception {
        setupWorkflowJob();

        CopyArtifactConfiguration.get().setMode(CopyArtifactCompatibilityMode.PRODUCTION);

        assertNoLegacyMonitor();

        FreeStyleProject projectToBeCopied_noAuth = j.jenkins.getItem("to-be-copied_noauth", j.jenkins, FreeStyleProject.class);
        FreeStyleProject projectToBeCopied_anonymous = j.jenkins.getItem("to-be-copied_anonymous", j.jenkins, FreeStyleProject.class);
        FreeStyleProject projectToBeCopied_restricted = j.jenkins.getItem("to-be-copied_restricted", j.jenkins, FreeStyleProject.class);
        WorkflowJob projectCopier = j.jenkins.getItem("copier_param", j.jenkins, WorkflowJob.class);

        for (FreeStyleProject p : new FreeStyleProject[]{projectToBeCopied_noAuth, projectToBeCopied_anonymous, projectToBeCopied_restricted}) {
            j.assertBuildStatus(Result.SUCCESS, p.scheduleBuild2(0));
            assertThat(p.getLastSuccessfulBuild().getWorkspace().list(), hasSize(1));
            assertThat(p.getLastSuccessfulBuild().getArtifacts(), hasSize(1));
        }

        { // no authQueue
            assertCopyParameterizedResult(Result.SUCCESS, projectCopier, projectToBeCopied_noAuth);
            assertCopyParameterizedResult(Result.SUCCESS, projectCopier, projectToBeCopied_anonymous);
            assertCopyParameterizedResult(Result.FAILURE, projectCopier, projectToBeCopied_restricted);
        }
        { // authQueue as anonymous
            setupGlobalQueueAuthAs(Jenkins.ANONYMOUS2);

            assertCopyParameterizedResult(Result.SUCCESS, projectCopier, projectToBeCopied_noAuth);
            assertCopyParameterizedResult(Result.SUCCESS, projectCopier, projectToBeCopied_anonymous);
            assertCopyParameterizedResult(Result.FAILURE, projectCopier, projectToBeCopied_restricted);
        }
        {// authQueue as dev
            setupGlobalQueueAuthAs(createUserAndImpersonate("dev"));

            assertCopyParameterizedResult(Result.SUCCESS, projectCopier, projectToBeCopied_noAuth);
            assertCopyParameterizedResult(Result.SUCCESS, projectCopier, projectToBeCopied_anonymous);
            assertCopyParameterizedResult(Result.FAILURE, projectCopier, projectToBeCopied_restricted);
        }
        {// authQueue as leader (access to restricted)
            setupGlobalQueueAuthAs(createUserAndImpersonate("leader"));

            assertCopyParameterizedResult(Result.SUCCESS, projectCopier, projectToBeCopied_noAuth);
            assertCopyParameterizedResult(Result.SUCCESS, projectCopier, projectToBeCopied_anonymous);
            assertCopyParameterizedResult(Result.SUCCESS, projectCopier, projectToBeCopied_restricted);
        }
        {// authQueue as admin
            setupGlobalQueueAuthAs(createUserAndImpersonate("admin"));
            assertCopyParameterizedResult(Result.SUCCESS, projectCopier, projectToBeCopied_noAuth);
            assertCopyParameterizedResult(Result.SUCCESS, projectCopier, projectToBeCopied_anonymous);
            assertCopyParameterizedResult(Result.SUCCESS, projectCopier, projectToBeCopied_restricted);
        }

        // Production mode does not care about monitoring the legacy jobs
        assertNoLegacyMonitor();
    }

    @Test
    void workflowJob_param_copy_legacy_production_property() throws Exception {
        setupWorkflowJob();

        CopyArtifactConfiguration.get().setMode(CopyArtifactCompatibilityMode.PRODUCTION);

        assertNoLegacyMonitor();

        FreeStyleProject projectToBeCopied_restricted = j.jenkins.getItem("to-be-copied_restricted", j.jenkins, FreeStyleProject.class);
        WorkflowJob projectCopier = j.jenkins.getItem("copier_param", j.jenkins, WorkflowJob.class);

        j.assertBuildStatus(Result.SUCCESS, projectToBeCopied_restricted.scheduleBuild2(0));
        assertThat(projectToBeCopied_restricted.getLastSuccessfulBuild().getWorkspace().list(), hasSize(1));
        assertThat(projectToBeCopied_restricted.getLastSuccessfulBuild().getArtifacts(), hasSize(1));

        // allow the copier to copy that restricted project
        projectToBeCopied_restricted.addProperty(new CopyArtifactPermissionProperty(projectCopier.getFullName()));

        { // no authQueue
            assertCopyParameterizedResult(Result.SUCCESS, projectCopier, projectToBeCopied_restricted);
        }
        { // authQueue as anonymous
            setupGlobalQueueAuthAs(Jenkins.ANONYMOUS2);

            // even with access, the other job is not visible for anonymous
            assertCopyParameterizedResult(Result.FAILURE, projectCopier, projectToBeCopied_restricted);
        }
        {// authQueue as dev
            setupGlobalQueueAuthAs(createUserAndImpersonate("dev"));

            // even with access, the other job is not visible for dev
            assertCopyParameterizedResult(Result.FAILURE, projectCopier, projectToBeCopied_restricted);
        }

        // Production mode does not care about monitoring the legacy jobs
        assertNoLegacyMonitor();
    }

    @Test
    void workflowJob_direct_copy_legacy_migration() throws Exception {
        setupWorkflowJob();

        assertNoLegacyMonitor();

        FreeStyleProject projectToBeCopied_noAuth = j.jenkins.getItem("to-be-copied_noauth", j.jenkins, FreeStyleProject.class);
        FreeStyleProject projectToBeCopied_anonymous = j.jenkins.getItem("to-be-copied_anonymous", j.jenkins, FreeStyleProject.class);
        FreeStyleProject projectToBeCopied_restricted = j.jenkins.getItem("to-be-copied_restricted", j.jenkins, FreeStyleProject.class);

        WorkflowJob projectCopier_noAuth = j.jenkins.getItem("copier_noauth", j.jenkins, WorkflowJob.class);
        WorkflowJob projectCopier_anonymous = j.jenkins.getItem("copier_anonymous", j.jenkins, WorkflowJob.class);
        WorkflowJob projectCopier_restricted = j.jenkins.getItem("copier_restricted", j.jenkins, WorkflowJob.class);

        for (FreeStyleProject p : new FreeStyleProject[]{projectToBeCopied_noAuth, projectToBeCopied_anonymous, projectToBeCopied_restricted}) {
            j.assertBuildStatus(Result.SUCCESS, p.scheduleBuild2(0));
            assertThat(p.getLastSuccessfulBuild().getWorkspace().list(), hasSize(1));
            assertThat(p.getLastSuccessfulBuild().getArtifacts(), hasSize(1));
        }

        { // no authQueue
            j.assertBuildStatus(Result.SUCCESS, projectCopier_noAuth.scheduleBuild2(0));
            j.assertBuildStatus(Result.SUCCESS, projectCopier_anonymous.scheduleBuild2(0));
            j.assertBuildStatus(Result.SUCCESS, projectCopier_restricted.scheduleBuild2(0));
        }

        assertLegacyMonitorHasOnly_andClear("to-be-copied_restricted");

        { // authQueue as anonymous
            setupGlobalQueueAuthAs(Jenkins.ANONYMOUS2);

            j.assertBuildStatus(Result.SUCCESS, projectCopier_noAuth.scheduleBuild2(0));
            j.assertBuildStatus(Result.SUCCESS, projectCopier_anonymous.scheduleBuild2(0));
            j.assertBuildStatus(Result.FAILURE, projectCopier_restricted.scheduleBuild2(0));
        }

        assertNoLegacyMonitor();

        {// authQueue as dev
            setupGlobalQueueAuthAs(createUserAndImpersonate("dev"));

            j.assertBuildStatus(Result.SUCCESS, projectCopier_noAuth.scheduleBuild2(0));
            j.assertBuildStatus(Result.SUCCESS, projectCopier_anonymous.scheduleBuild2(0));
            j.assertBuildStatus(Result.FAILURE, projectCopier_restricted.scheduleBuild2(0));
        }

        assertNoLegacyMonitor();

        {// authQueue as leader (access to restricted)
            setupGlobalQueueAuthAs(createUserAndImpersonate("leader"));

            j.assertBuildStatus(Result.SUCCESS, projectCopier_noAuth.scheduleBuild2(0));
            j.assertBuildStatus(Result.SUCCESS, projectCopier_anonymous.scheduleBuild2(0));
            j.assertBuildStatus(Result.SUCCESS, projectCopier_restricted.scheduleBuild2(0));
        }

        assertNoLegacyMonitor();

        {// authQueue as admin
            setupGlobalQueueAuthAs(createUserAndImpersonate("admin"));

            j.assertBuildStatus(Result.SUCCESS, projectCopier_noAuth.scheduleBuild2(0));
            j.assertBuildStatus(Result.SUCCESS, projectCopier_anonymous.scheduleBuild2(0));
            j.assertBuildStatus(Result.SUCCESS, projectCopier_restricted.scheduleBuild2(0));
        }

        assertNoLegacyMonitor();
    }

    @Test
    void workflowJob_direct_copy_legacy_production() throws Exception {
        setupWorkflowJob();

        CopyArtifactConfiguration.get().setMode(CopyArtifactCompatibilityMode.PRODUCTION);

        assertNoLegacyMonitor();

        FreeStyleProject projectToBeCopied_noAuth = j.jenkins.getItem("to-be-copied_noauth", j.jenkins, FreeStyleProject.class);
        FreeStyleProject projectToBeCopied_anonymous = j.jenkins.getItem("to-be-copied_anonymous", j.jenkins, FreeStyleProject.class);
        FreeStyleProject projectToBeCopied_restricted = j.jenkins.getItem("to-be-copied_restricted", j.jenkins, FreeStyleProject.class);

        WorkflowJob projectCopier_noAuth = j.jenkins.getItem("copier_noauth", j.jenkins, WorkflowJob.class);
        WorkflowJob projectCopier_anonymous = j.jenkins.getItem("copier_anonymous", j.jenkins, WorkflowJob.class);
        WorkflowJob projectCopier_restricted = j.jenkins.getItem("copier_restricted", j.jenkins, WorkflowJob.class);

        for (FreeStyleProject p : new FreeStyleProject[]{projectToBeCopied_noAuth, projectToBeCopied_anonymous, projectToBeCopied_restricted}) {
            j.assertBuildStatus(Result.SUCCESS, p.scheduleBuild2(0));
            assertThat(p.getLastSuccessfulBuild().getWorkspace().list(), hasSize(1));
            assertThat(p.getLastSuccessfulBuild().getArtifacts(), hasSize(1));
        }

        { // no authQueue
            j.assertBuildStatus(Result.SUCCESS, projectCopier_noAuth.scheduleBuild2(0));
            j.assertBuildStatus(Result.SUCCESS, projectCopier_anonymous.scheduleBuild2(0));
            // due to SYSTEM, the code must check using authenticated or property
            j.assertBuildStatus(Result.FAILURE, projectCopier_restricted.scheduleBuild2(0));
        }
        { // authQueue as anonymous
            setupGlobalQueueAuthAs(Jenkins.ANONYMOUS2);

            j.assertBuildStatus(Result.SUCCESS, projectCopier_noAuth.scheduleBuild2(0));
            j.assertBuildStatus(Result.SUCCESS, projectCopier_anonymous.scheduleBuild2(0));
            j.assertBuildStatus(Result.FAILURE, projectCopier_restricted.scheduleBuild2(0));
        }
        {// authQueue as dev
            setupGlobalQueueAuthAs(createUserAndImpersonate("dev"));

            j.assertBuildStatus(Result.SUCCESS, projectCopier_noAuth.scheduleBuild2(0));
            j.assertBuildStatus(Result.SUCCESS, projectCopier_anonymous.scheduleBuild2(0));
            j.assertBuildStatus(Result.FAILURE, projectCopier_restricted.scheduleBuild2(0));
        }
        {// authQueue as leader (access to restricted)
            setupGlobalQueueAuthAs(createUserAndImpersonate("leader"));

            j.assertBuildStatus(Result.SUCCESS, projectCopier_noAuth.scheduleBuild2(0));
            j.assertBuildStatus(Result.SUCCESS, projectCopier_anonymous.scheduleBuild2(0));
            j.assertBuildStatus(Result.SUCCESS, projectCopier_restricted.scheduleBuild2(0));
        }
        {// authQueue as admin
            setupGlobalQueueAuthAs(createUserAndImpersonate("admin"));
            j.assertBuildStatus(Result.SUCCESS, projectCopier_noAuth.scheduleBuild2(0));
            j.assertBuildStatus(Result.SUCCESS, projectCopier_anonymous.scheduleBuild2(0));
            j.assertBuildStatus(Result.SUCCESS, projectCopier_restricted.scheduleBuild2(0));
        }

        // Production mode does not care about monitoring the legacy jobs
        assertNoLegacyMonitor();
    }

    @Test
    void remove_succeeded() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy());

        WorkflowJob src = j.jenkins.createProject(WorkflowJob.class, "src");
        src.setDefinition(new CpsFlowDefinition(
            "node {"
                + "writeFile(text: 'artifact', file: 'artifact.txt');"
                + "archiveArtifacts(artifacts: 'artifact.txt');"
                + "}",
            true
        ));
        j.assertBuildStatusSuccess(src.scheduleBuild2(0));

        WorkflowJob dest = j.jenkins.createProject(WorkflowJob.class, "dest");
        dest.setDefinition(new CpsFlowDefinition(
            "node {"
                + "copyArtifacts(projectName: 'src');"
                + "}",
            true
        ));
        j.assertBuildStatusSuccess(dest.scheduleBuild2(0));

        LegacyJobConfigMigrationMonitor monitor = LegacyJobConfigMigrationMonitor.get();
        assertThat(
            monitor.getData().getFullNameToKey().keySet(),
            Matchers.is(new HashSet<>(Arrays.asList("dest", "src")))
        );

        src.addProperty(new CopyArtifactPermissionProperty("dest"));
        j.assertBuildStatusSuccess(dest.scheduleBuild2(0));

        assertThat(
            monitor.getData().getFullNameToKey().keySet(),
            Matchers.is(Matchers.empty())
        );
    }

    private Authentication createUserAndImpersonate(String userName) {
        User user = User.getById(userName, true);
        assertNotNull(user);
        return user.impersonate2();
    }

    private void assertNoLegacyMonitor() {
        LegacyJobConfigMigrationMonitor monitor = LegacyJobConfigMigrationMonitor.get();

        assertFalse(monitor.isActivated());
    }

    private void assertLegacyMonitorHasOnly_andClear(String jobName) {
        LegacyJobConfigMigrationMonitor monitor = LegacyJobConfigMigrationMonitor.get();

        assertThat(monitor.getData().getFullNameToKey().containsKey(jobName), is(true));
        // size = 2, from + to
        assertThat(monitor.getData().getFullNameToKey().entrySet(), hasSize(2));
        assertThat(monitor.getData().getLegacyJobInfos().entrySet(), hasSize(1));

        assertTrue(monitor.isActivated());

        monitor.getData().clear();
    }

    private void assertCopyParameterizedResult(Result expectedResult, ParameterizedJobMixIn.ParameterizedJob<?, ?> copier, Job<?, ?> toBeCopied) throws Exception {
        QueueTaskFuture<? extends Run<?, ?>> build = copier.scheduleBuild2(0, new ParametersAction(
                new StringParameterValue("sourceProject", toBeCopied.getFullName())
        ));
        assertNotNull(build);

        j.assertBuildStatus(expectedResult, build);
    }

    private static class SimpleQueueItemAuthenticator extends QueueItemAuthenticator {
        // transient is to avoid `Failed to serialize ...` logs in the test console.
        private final transient Authentication authentication;

        public SimpleQueueItemAuthenticator(Authentication authentication) {
            this.authentication = authentication;
        }

        @Override
        public @CheckForNull Authentication authenticate2(Queue.Item item) {
            return authentication;
        }
    }
}
