/*
 * The MIT License
 *
 * Copyright (c) 2019 IKEDA Yasuyuki
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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.plugins.copyartifact.testutils.JenkinsRuleUtil;
import jenkins.plugins.git.junit.jupiter.WithGitSampleRepo;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject;
import org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProjectTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.MockFolder;
import org.jvnet.hudson.test.ToolInstallations;

import hudson.matrix.Axis;

import java.io.File;
import hudson.matrix.AxisList;
import hudson.matrix.Combination;
import hudson.matrix.MatrixProject;
import hudson.maven.MavenModuleSet;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.plugins.copyartifact.CopyArtifact;
import hudson.plugins.copyartifact.CopyArtifactCompatibilityMode;
import hudson.plugins.copyartifact.CopyArtifactConfiguration;
import hudson.plugins.copyartifact.monitor.LegacyMonitorData.JobKey;
import hudson.plugins.copyartifact.testutils.FileWriteBuilder;
import hudson.tasks.ArtifactArchiver;
import jenkins.branch.BranchProperty;
import jenkins.branch.BranchSource;
import jenkins.branch.DefaultBranchPropertyStrategy;
import jenkins.branch.NoTriggerBranchProperty;
import jenkins.plugins.git.GitSCMSource;
import jenkins.plugins.git.GitSampleRepoRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * Test that migration is applicable to various jobs.
 */
@WithJenkins
@WithGitSampleRepo
class LegacyJobConfigMigrationMonitorMigrationTest {

    private JenkinsRule j;
    private GitSampleRepoRule sampleRepo;
    @TempDir
    private File tempFolder;

    private boolean applyAutoMigrationToAll() throws Exception {
        boolean migrated = true;
        for (JobKey key: LegacyJobConfigMigrationMonitor.get().getData().getLegacyJobInfos().keySet()) {
            migrated &= LegacyJobConfigMigrationMonitor.get().applyAutoMigration(
                key.from,
                key.to
            );
        }
        return migrated;
    }

    @BeforeEach
    void setUp(JenkinsRule rule, GitSampleRepoRule repo) {
        j = rule;
        sampleRepo = repo;

        LegacyJobConfigMigrationMonitor.get().getData().clear();
        CopyArtifactConfiguration.get().setMode(CopyArtifactCompatibilityMode.MIGRATION);

        // anonymous cannot read jobs.
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        MockAuthorizationStrategy authStrategy = new MockAuthorizationStrategy();
        j.jenkins.setAuthorizationStrategy(authStrategy);
    }

    @Test
    void migrate_freestyle_to_freestyle() throws Exception {
        FreeStyleProject src = j.createFreeStyleProject();
        src.getBuildersList().add(
            new FileWriteBuilder("artifact.txt", "artifact content")
        );
        src.getPublishersList().add(
            new ArtifactArchiver("**/*")
        );
        j.assertBuildStatusSuccess(src.scheduleBuild2(0));

        FreeStyleProject dst = j.createFreeStyleProject();
        dst.getBuildersList().add(
            new CopyArtifact(src.getFullName())
        );

        j.assertBuildStatusSuccess(dst.scheduleBuild2(0));

        CopyArtifactConfiguration.get().setMode(CopyArtifactCompatibilityMode.PRODUCTION);

        j.assertBuildStatus(Result.FAILURE, dst.scheduleBuild2(0));

        assertTrue(applyAutoMigrationToAll());

        j.assertBuildStatusSuccess(dst.scheduleBuild2(0));
    }

    @Test
    void migrate_pipeline_to_pipeline() throws Exception {
        WorkflowJob src = j.createProject(WorkflowJob.class);
        src.setDefinition(new CpsFlowDefinition(
            "node {"
                + "writeFile(text: 'artifact', file: 'artifact.txt');"
                + "archiveArtifacts(artifacts: 'artifact.txt');"
                + "}",
            true
        ));
        j.assertBuildStatusSuccess(src.scheduleBuild2(0));

        WorkflowJob dst = j.createProject(WorkflowJob.class);
        dst.setDefinition(new CpsFlowDefinition(
            "node {"
                + "copyArtifacts('" + src.getFullName() + "');"
                + "}",
            true
        ));
        j.assertBuildStatusSuccess(dst.scheduleBuild2(0));

        CopyArtifactConfiguration.get().setMode(CopyArtifactCompatibilityMode.PRODUCTION);

        j.assertBuildStatus(Result.FAILURE, dst.scheduleBuild2(0));

        assertTrue(applyAutoMigrationToAll());

        j.assertBuildStatusSuccess(dst.scheduleBuild2(0));
    }

    @Test
    void migrate_pipeline_in_folder_to_pipeline() throws Exception {
        MockFolder f = j.createFolder("folder");
        WorkflowJob src = f.createProject(WorkflowJob.class, "src");
        src.setDefinition(new CpsFlowDefinition(
            "node {"
                + "writeFile(text: 'artifact', file: 'artifact.txt');"
                + "archiveArtifacts(artifacts: 'artifact.txt');"
                + "}",
            true
        ));
        j.assertBuildStatusSuccess(src.scheduleBuild2(0));

        WorkflowJob dst = j.createProject(WorkflowJob.class);
        dst.setDefinition(new CpsFlowDefinition(
            "node {"
                + "copyArtifacts('" + src.getFullName() + "');"
                + "}",
            true
        ));
        j.assertBuildStatusSuccess(dst.scheduleBuild2(0));

        CopyArtifactConfiguration.get().setMode(CopyArtifactCompatibilityMode.PRODUCTION);

        j.assertBuildStatus(Result.FAILURE, dst.scheduleBuild2(0));

        assertTrue(applyAutoMigrationToAll());

        j.assertBuildStatusSuccess(dst.scheduleBuild2(0));
    }

    @Test
    void migrate_pipeline_to_pipeline_in_folder() throws Exception {
        WorkflowJob src = j.createProject(WorkflowJob.class);
        src.setDefinition(new CpsFlowDefinition(
            "node {"
                + "writeFile(text: 'artifact', file: 'artifact.txt');"
                + "archiveArtifacts(artifacts: 'artifact.txt');"
                + "}",
            true
        ));
        j.assertBuildStatusSuccess(src.scheduleBuild2(0));

        MockFolder f = j.createFolder("folder");
        WorkflowJob dst = f.createProject(WorkflowJob.class, "dst");
        dst.setDefinition(new CpsFlowDefinition(
            "node {"
                + "copyArtifacts('" + src.getFullName() + "');"
                + "}",
            true
        ));
        j.assertBuildStatusSuccess(dst.scheduleBuild2(0));

        CopyArtifactConfiguration.get().setMode(CopyArtifactCompatibilityMode.PRODUCTION);

        j.assertBuildStatus(Result.FAILURE, dst.scheduleBuild2(0));

        assertTrue(applyAutoMigrationToAll());

        j.assertBuildStatusSuccess(dst.scheduleBuild2(0));
    }

    @Test
    void migrate_pipeline_to_pipeline_in_same_folder() throws Exception {
        MockFolder f = j.createFolder("folder");
        WorkflowJob src = f.createProject(WorkflowJob.class, "src");
        src.setDefinition(new CpsFlowDefinition(
            "node {"
                + "writeFile(text: 'artifact', file: 'artifact.txt');"
                + "archiveArtifacts(artifacts: 'artifact.txt');"
                + "}",
            true
        ));
        j.assertBuildStatusSuccess(src.scheduleBuild2(0));

        WorkflowJob dst = f.createProject(WorkflowJob.class, "dst");
        dst.setDefinition(new CpsFlowDefinition(
            "node {"
                + "copyArtifacts('" + src.getFullName() + "');"
                + "}",
            true
        ));
        j.assertBuildStatusSuccess(dst.scheduleBuild2(0));

        CopyArtifactConfiguration.get().setMode(CopyArtifactCompatibilityMode.PRODUCTION);

        j.assertBuildStatus(Result.FAILURE, dst.scheduleBuild2(0));

        assertTrue(applyAutoMigrationToAll());

        j.assertBuildStatusSuccess(dst.scheduleBuild2(0));
    }

    @Test
    void migrate_pipeline_to_pipeline_in_different_folder() throws Exception {
        MockFolder f1 = j.createFolder("folder1");
        WorkflowJob src = f1.createProject(WorkflowJob.class, "src");
        src.setDefinition(new CpsFlowDefinition(
            "node {"
                + "writeFile(text: 'artifact', file: 'artifact.txt');"
                + "archiveArtifacts(artifacts: 'artifact.txt');"
                + "}",
            true
        ));
        j.assertBuildStatusSuccess(src.scheduleBuild2(0));

        MockFolder f2 = j.createFolder("folder2");
        WorkflowJob dst = f2.createProject(WorkflowJob.class, "dst");
        dst.setDefinition(new CpsFlowDefinition(
            "node {"
                + "copyArtifacts('" + src.getFullName() + "');"
                + "}",
            true
        ));
        j.assertBuildStatusSuccess(dst.scheduleBuild2(0));

        CopyArtifactConfiguration.get().setMode(CopyArtifactCompatibilityMode.PRODUCTION);

        j.assertBuildStatus(Result.FAILURE, dst.scheduleBuild2(0));

        assertTrue(applyAutoMigrationToAll());

        j.assertBuildStatusSuccess(dst.scheduleBuild2(0));
    }

    @Test
    void migrate_matrix_to_pipeline() throws Exception {
        MatrixProject src = j.createProject(MatrixProject.class);
        src.setAxes(new AxisList(
            new Axis("axis1", "value1", "value2")
        ));
        src.getBuildersList().add(
            new FileWriteBuilder("artifact.txt", "artifact content")
        );
        src.getPublishersList().add(
            new ArtifactArchiver("**/*")
        );
        j.assertBuildStatusSuccess(src.scheduleBuild2(0));

        WorkflowJob dst = j.createProject(WorkflowJob.class);
        dst.setDefinition(new CpsFlowDefinition(
            "node {"
                + "copyArtifacts('" + src.getFullName() + "');"
                + "}",
            true
        ));
        j.assertBuildStatusSuccess(dst.scheduleBuild2(0));

        CopyArtifactConfiguration.get().setMode(CopyArtifactCompatibilityMode.PRODUCTION);

        j.assertBuildStatus(Result.FAILURE, dst.scheduleBuild2(0));

        assertTrue(applyAutoMigrationToAll());

        j.assertBuildStatusSuccess(dst.scheduleBuild2(0));
    }

    @Test
    void migrate_matrixchild_to_pipeline() throws Exception {
        MatrixProject src = j.createProject(MatrixProject.class);
        AxisList axisList = new AxisList(
            new Axis("axis1", "value1", "value2")
        );
        src.setAxes(axisList);
        src.getBuildersList().add(
            new FileWriteBuilder("artifact.txt", "artifact content")
        );
        src.getPublishersList().add(
            new ArtifactArchiver("**/*")
        );
        j.assertBuildStatusSuccess(src.scheduleBuild2(0));

        WorkflowJob dst = j.createProject(WorkflowJob.class);
        dst.setDefinition(new CpsFlowDefinition(
            "node {"
                + "copyArtifacts('" + src.getItem(new Combination(axisList, "value1")).getFullName() + "');"
                + "}",
            true
        ));
        j.assertBuildStatusSuccess(dst.scheduleBuild2(0));

        CopyArtifactConfiguration.get().setMode(CopyArtifactCompatibilityMode.PRODUCTION);

        j.assertBuildStatus(Result.FAILURE, dst.scheduleBuild2(0));

        assertTrue(applyAutoMigrationToAll());

        j.assertBuildStatusSuccess(dst.scheduleBuild2(0));
    }

    @Test
    void migrate_pipeline_to_matrix() throws Exception {
        WorkflowJob src = j.createProject(WorkflowJob.class);
        src.setDefinition(new CpsFlowDefinition(
            "node {"
                + "writeFile(text: 'artifact', file: 'artifact.txt');"
                + "archiveArtifacts(artifacts: 'artifact.txt');"
                + "}",
            true
        ));
        j.assertBuildStatusSuccess(src.scheduleBuild2(0));

        MatrixProject dst = j.createProject(MatrixProject.class);
        AxisList axisList = new AxisList(
            new Axis("axis1", "value1", "value2")
        );
        dst.setAxes(axisList);
        dst.getBuildersList().add(
            new CopyArtifact(src.getFullName())
        );
        j.assertBuildStatusSuccess(dst.scheduleBuild2(0));

        CopyArtifactConfiguration.get().setMode(CopyArtifactCompatibilityMode.PRODUCTION);

        j.assertBuildStatus(Result.FAILURE, dst.scheduleBuild2(0));

        assertTrue(applyAutoMigrationToAll());

        j.assertBuildStatusSuccess(dst.scheduleBuild2(0));
    }

    @Test
    void migrate_maven_to_pipeline() throws Exception {
        ToolInstallations.configureMaven3();
        MavenModuleSet src = j.createProject(MavenModuleSet.class);
        src.setScm(JenkinsRuleUtil.getExtractResourceScm(tempFolder, getClass().getResource("../maven-job")));
        src.setRunHeadless(true);
        src.setGoals("clean package -Dmaven.compiler.source=1.8 -Dmaven.compiler.target=1.8");
        j.assertBuildStatusSuccess(src.scheduleBuild2(0));

        WorkflowJob dst = j.createProject(WorkflowJob.class);
        dst.setDefinition(new CpsFlowDefinition(
            "node {"
                + "copyArtifacts('" + src.getFullName() + "');"
                + "}",
            true
        ));
        j.assertBuildStatusSuccess(dst.scheduleBuild2(0));

        CopyArtifactConfiguration.get().setMode(CopyArtifactCompatibilityMode.PRODUCTION);

        j.assertBuildStatus(Result.FAILURE, dst.scheduleBuild2(0));

        assertTrue(applyAutoMigrationToAll());

        j.assertBuildStatusSuccess(dst.scheduleBuild2(0));
    }

    @Test
    void migrate_mavenmodule_to_pipeline() throws Exception {
        ToolInstallations.configureMaven3();
        MavenModuleSet src = j.createProject(MavenModuleSet.class);
        src.setScm(JenkinsRuleUtil.getExtractResourceScm(tempFolder, getClass().getResource("../maven-job")));
        src.setRunHeadless(true);
        src.setGoals("clean package -Dmaven.compiler.source=1.8 -Dmaven.compiler.target=1.8");
        j.assertBuildStatusSuccess(src.scheduleBuild2(0));

        WorkflowJob dst = j.createProject(WorkflowJob.class);
        dst.setDefinition(new CpsFlowDefinition(
            "node {"
                + "copyArtifacts('" + src.getFullName() + "/org.jvnet.hudson.main.test.multimod$moduleA');"
                + "}",
            true
        ));
        j.assertBuildStatusSuccess(dst.scheduleBuild2(0));

        CopyArtifactConfiguration.get().setMode(CopyArtifactCompatibilityMode.PRODUCTION);

        j.assertBuildStatus(Result.FAILURE, dst.scheduleBuild2(0));

        assertTrue(applyAutoMigrationToAll());

        j.assertBuildStatusSuccess(dst.scheduleBuild2(0));
    }

    @Test
    void migrate_pipeline_to_maven() throws Exception {
        WorkflowJob src = j.createProject(WorkflowJob.class);
        src.setDefinition(new CpsFlowDefinition(
            "node {"
                + "writeFile(text: 'artifact', file: 'artifact.txt');"
                + "archiveArtifacts(artifacts: 'artifact.txt');"
                + "}",
            true
        ));
        j.assertBuildStatusSuccess(src.scheduleBuild2(0));

        ToolInstallations.configureMaven3();
        MavenModuleSet dst = j.createProject(MavenModuleSet.class);
        dst.setScm(JenkinsRuleUtil.getExtractResourceScm(tempFolder, getClass().getResource("../maven-job")));
        dst.setRunHeadless(true);
        dst.setGoals("clean package -Dmaven.compiler.source=1.8 -Dmaven.compiler.target=1.8");
        dst.getPrebuilders().add(
            new CopyArtifact(src.getFullName())
        );
        j.assertBuildStatusSuccess(dst.scheduleBuild2(0));

        CopyArtifactConfiguration.get().setMode(CopyArtifactCompatibilityMode.PRODUCTION);

        j.assertBuildStatus(Result.FAILURE, dst.scheduleBuild2(0));

        assertTrue(applyAutoMigrationToAll());

        j.assertBuildStatusSuccess(dst.scheduleBuild2(0));
    }

    @Test
    void migrate_pipeline_to_multibranch() throws Exception {
        WorkflowJob src = j.createProject(WorkflowJob.class);
        src.setDefinition(new CpsFlowDefinition(
            "node {"
                + "writeFile(text: 'artifact', file: 'artifact.txt');"
                + "archiveArtifacts(artifacts: 'artifact.txt');"
                + "}",
            true
        ));
        j.assertBuildStatusSuccess(src.scheduleBuild2(0));

        sampleRepo.init();
        sampleRepo.write(
            "Jenkinsfile",
            "node {"
                + "copyArtifacts('" + src.getFullName() + "');"
                + "}"
        );
        sampleRepo.git("add", "Jenkinsfile");
        sampleRepo.git("commit", "--message=initial commit");

        WorkflowMultiBranchProject mp = j.createProject(
            WorkflowMultiBranchProject.class
        );
        BranchSource branch = new BranchSource(new GitSCMSource(
            null,   // id
            sampleRepo.toString(),
            "",     // credentialsId
            "*",    // includes
            "",     // excludes
            false   // ignoreOnPushNotification
        ));
        branch.setStrategy(
            new DefaultBranchPropertyStrategy(new BranchProperty[] {
                new NoTriggerBranchProperty()
            })
        );
        mp.getSourcesList().add(branch);
        WorkflowJob dst = WorkflowMultiBranchProjectTest.scheduleAndFindBranchProject(
            mp,
            "master"
        );
        j.assertBuildStatusSuccess(dst.scheduleBuild2(0));

        CopyArtifactConfiguration.get().setMode(CopyArtifactCompatibilityMode.PRODUCTION);

        j.assertBuildStatus(Result.FAILURE, dst.scheduleBuild2(0));

        assertTrue(applyAutoMigrationToAll());

        j.assertBuildStatusSuccess(dst.scheduleBuild2(0));
    }

    @Test
    void migrate_multibranch_to_pipeline() throws Exception {
        sampleRepo.init();
        sampleRepo.write(
            "Jenkinsfile",
            "node {"
                + "writeFile(text: 'artifact', file: 'artifact.txt');"
                + "archiveArtifacts(artifacts: 'artifact.txt');"
                + "}"
        );
        sampleRepo.git("add", "Jenkinsfile");
        sampleRepo.git("commit", "--message=initial commit");

        WorkflowMultiBranchProject mp = j.createProject(
            WorkflowMultiBranchProject.class
        );
        BranchSource branch = new BranchSource(new GitSCMSource(
            null,   // id
            sampleRepo.toString(),
            "",     // credentialsId
            "*",    // includes
            "",     // excludes
            false   // ignoreOnPushNotification
        ));
        branch.setStrategy(
            new DefaultBranchPropertyStrategy(new BranchProperty[] {
                new NoTriggerBranchProperty()
            })
        );
        mp.getSourcesList().add(branch);
        WorkflowJob src = WorkflowMultiBranchProjectTest.scheduleAndFindBranchProject(
            mp,
            "master"
        );
        j.assertBuildStatusSuccess(src.scheduleBuild2(0));

        WorkflowJob dst = j.createProject(WorkflowJob.class);
        src.setDefinition(new CpsFlowDefinition(
            "node {"
                + "copyArtifacts('" + src.getFullName() + "');"
                + "}",
            true
        ));
        j.assertBuildStatusSuccess(src.scheduleBuild2(0));

        CopyArtifactConfiguration.get().setMode(CopyArtifactCompatibilityMode.PRODUCTION);

        j.assertBuildStatus(Result.FAILURE, dst.scheduleBuild2(0));

        // This should fail as the configuration of multibranch
        // cannot be overwritten.
        assertFalse(applyAutoMigrationToAll());

        j.assertBuildStatus(Result.FAILURE, dst.scheduleBuild2(0));
    }
}
