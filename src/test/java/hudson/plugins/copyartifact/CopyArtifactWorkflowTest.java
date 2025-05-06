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

import static org.junit.jupiter.api.Assertions.assertEquals;

import hudson.model.Cause;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.StringParameterDefinition;
import hudson.model.StringParameterValue;
import hudson.plugins.copyartifact.testutils.CopyArtifactUtil;
import hudson.plugins.copyartifact.testutils.FileWriteBuilder;
import hudson.plugins.copyartifact.testutils.JenkinsRuleUtil;
import hudson.tasks.ArtifactArchiver;

import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import java.util.List;

/**
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 */
@WithJenkins
class CopyArtifactWorkflowTest {

    private JenkinsRule jenkinsRule;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        jenkinsRule = rule;
    }

    @Test
    void test_simpleUntriggeredCopy() throws Exception {
        // create "project_1" with an archived artifact...
        WorkflowJob project_1 = JenkinsRuleUtil.createWorkflow(jenkinsRule,
                "project_1",
                "writeFile text: 'hello', file: 'hello.txt'; " +
                "step([$class: 'ArtifactArchiver', artifacts: 'hello.txt', fingerprint: true])");
        WorkflowRun b = jenkinsRule.assertBuildStatusSuccess(project_1.scheduleBuild2(0));
        assertArtifactInArchive(b);

        // Now lets try copy the artifact from "project_1" to "project_2"
        WorkflowJob project_2 = JenkinsRuleUtil.createWorkflow(jenkinsRule,
                "project_2",
                "step([$class: 'CopyArtifact', projectName: 'project_1', filter: 'hello.txt']); " +
                "step([$class: 'ArtifactArchiver', artifacts: 'hello.txt', fingerprint: true]);");
        b = jenkinsRule.assertBuildStatusSuccess(project_2.scheduleBuild2(0));
        assertArtifactInArchive(b);
    }

    /**
     * Test filtering on parameters works to copy from workflow jobs.
     */
    @Issue("JENKINS-26694")
    @Test
    void testFilterByParametersForWorkflow() throws Exception {
        WorkflowJob copiee = JenkinsRuleUtil.createWorkflow(jenkinsRule,
                "copiee",
                "writeFile text: \"${PARAM}\", file:'artifact.txt';"
                + "archive includes:'artifact.txt';"
        );
        copiee.addProperty(new ParametersDefinitionProperty(
                new StringParameterDefinition("PARAM", "")
        ));
        copiee.setDefinition(new CpsFlowDefinition(
                "node {"
                        + "writeFile text: \"${PARAM}\", file:'artifact.txt';"
                        + "archive includes:'artifact.txt';"
                + "}",
                true
        ));

        FreeStyleProject copier = jenkinsRule.createFreeStyleProject();
        copier.addProperty(new ParametersDefinitionProperty(
                new StringParameterDefinition("PARAM_TO_COPY", "")
        ));
        copier.getBuildersList().add(CopyArtifactUtil.createCopyArtifact(
                copiee.getFullName(),
                "PARAM=${PARAM_TO_COPY}",
                new LastCompletedBuildSelector(),
                "artifact.txt",
                "",
                false,
                false
        ));

        // #1: PARAM=foo
        jenkinsRule.assertBuildStatusSuccess(copiee.scheduleBuild2(0, new ParametersAction(
                new StringParameterValue("PARAM", "foo")
        )));
        // #2: PARAM=bar
        jenkinsRule.assertBuildStatusSuccess(copiee.scheduleBuild2(0, new ParametersAction(
                new StringParameterValue("PARAM", "bar")
        )));

        FreeStyleBuild build = copier.scheduleBuild2(0, new Cause.UserIdCause(), new ParametersAction(
                new StringParameterValue("PARAM_TO_COPY", "foo")
        )).get();
        jenkinsRule.assertBuildStatusSuccess(build);

        assertEquals("foo", build.getWorkspace().child("artifact.txt").readToString());
    }

    /**
     * Demonstrate that we can run a downstream build and then copy artifacts from it.
     */
    @Issue("JENKINS-33577")
    @Test
    void copyFromDownstreamBuild() throws Exception {
        WorkflowJob us = JenkinsRuleUtil.createWorkflow(jenkinsRule,
                "us",
                "step([$class: 'CopyArtifact', projectName: 'ds', selector: [$class: 'SpecificBuildSelector', buildNumber: \"${build('ds').number}\"]]); echo readFile('art')");
        WorkflowJob ds = JenkinsRuleUtil.createWorkflow(jenkinsRule,
                "ds",
                "writeFile file: 'art', text: env.BUILD_TAG; archive includes: 'art'");
        jenkinsRule.assertLogContains("jenkins-ds-1", jenkinsRule.assertBuildStatusSuccess(us.scheduleBuild2(0)));
    }

    private void assertArtifactInArchive(WorkflowRun b) {
        List<WorkflowRun.Artifact> artifacts = b.getArtifacts();
        assertEquals(1, artifacts.size());
        assertEquals("hello.txt", artifacts.get(0).relativePath);
    }

    // Tests for symbols.
    // Test only that symbols are accessible as expected.
    // No tests for features here as they should be tested in other tests.

    @Test
    void testDownstreamBuildSelector() throws Exception {
        // upstream (freestyle) -> copiee (freestyle)
        // copier (pipeline) copies from copiee, which is downstream of `upstream`.
        // DownstreamBuildSelector support detecting relations between only `AbstractProject`s.

        FreeStyleProject upstream = jenkinsRule.createFreeStyleProject("upstream");
        upstream.getBuildersList().add(new FileWriteBuilder("upstream_artifact.txt", "${BUILD_TAG}"));
        ArtifactArchiver aa = new ArtifactArchiver("upstream_artifact.txt");
        aa.setFingerprint(true);        // important to have Jenkins track builds
        upstream.getPublishersList().add(aa);

        FreeStyleProject copiee = jenkinsRule.createFreeStyleProject("copiee");
        CopyArtifact ca = new CopyArtifact("upstream");
        ca.setFingerprintArtifacts(true);       // important to have Jenkins track builds
        copiee.getBuildersList().add(ca);
        copiee.getBuildersList().add(new FileWriteBuilder("artifact.txt", "foobar"));
        copiee.getPublishersList().add(new ArtifactArchiver("artifact.txt"));

        jenkinsRule.assertBuildStatusSuccess(upstream.scheduleBuild2(0));
        jenkinsRule.assertBuildStatusSuccess(copiee.scheduleBuild2(0));

        WorkflowJob copier = JenkinsRuleUtil.createWorkflow(
                jenkinsRule,
                "copier",
            "copyArtifacts(projectName: 'copiee', selector: downstream(upstreamProjectName: 'upstream', upstreamBuildNumber: '1'));"
            + "echo readFile('artifact.txt');"
        );
        jenkinsRule.assertLogContains("foobar", jenkinsRule.assertBuildStatusSuccess(copier.scheduleBuild2(0)));
    }

    @Test
    void testLastCompletedBuildSelector() throws Exception {
        WorkflowJob copiee = JenkinsRuleUtil.createWorkflow(
                jenkinsRule,
                "copiee",
            "writeFile text: 'foobar', file: 'artifact.txt';"
            + "archive includes: 'artifact.txt';"
        );
        jenkinsRule.assertBuildStatusSuccess(copiee.scheduleBuild2(0));

        WorkflowJob copier = JenkinsRuleUtil.createWorkflow(
                jenkinsRule,
                "copier",
            "copyArtifacts(projectName: 'copiee', selector: lastCompleted());"
            + "echo readFile('artifact.txt');"
        );
        jenkinsRule.assertLogContains("foobar", jenkinsRule.assertBuildStatusSuccess(copier.scheduleBuild2(0)));
    }

    @Test
    void testParameterizedBuildSelector() throws Exception {
        WorkflowJob copiee = JenkinsRuleUtil.createWorkflow(
                jenkinsRule,
                "copiee",
            "writeFile text: 'foobar', file: 'artifact.txt';"
            + "archive includes: 'artifact.txt';"
        );
        jenkinsRule.assertBuildStatusSuccess(copiee.scheduleBuild2(0));

        WorkflowJob copier = JenkinsRuleUtil.createWorkflow(
                jenkinsRule,
                "copier",
            "copyArtifacts(projectName: 'copiee', selector: buildParameter(\"${SELECTOR}\"));"
            + "echo readFile('artifact.txt');"
        );
        copier.addProperty(new ParametersDefinitionProperty(
            new BuildSelectorParameter(
                "SELECTOR",
                new StatusBuildSelector(),
                ""
            )
        ));
        // Build with default parameters
        JenkinsRule.WebClient wc = JenkinsRuleUtil.createAllow405WebClient(jenkinsRule);
        jenkinsRule.submit(wc.getPage(copier, "build").getFormByName("parameters"));
        jenkinsRule.waitUntilNoActivity();

        jenkinsRule.assertLogContains("foobar", jenkinsRule.assertBuildStatusSuccess(copier.getLastBuild()));
    }

    @Test
    void testPermalinkBuildSelector() throws Exception {
        WorkflowJob copiee = JenkinsRuleUtil.createWorkflow(
                jenkinsRule,
                "copiee",
            "writeFile text: 'foobar', file: 'artifact.txt';"
            + "archive includes: 'artifact.txt';"
        );
        jenkinsRule.assertBuildStatusSuccess(copiee.scheduleBuild2(0));

        WorkflowJob copier = JenkinsRuleUtil.createWorkflow(
                jenkinsRule,
                "copier",
            "copyArtifacts(projectName: 'copiee', selector: permalink('lastStableBuild'));"
            + "echo readFile('artifact.txt');"
        );
        jenkinsRule.assertLogContains("foobar", jenkinsRule.assertBuildStatusSuccess(copier.scheduleBuild2(0)));
    }

    @Test
    void testSavedBuildSelector() throws Exception {
        WorkflowJob copiee = JenkinsRuleUtil.createWorkflow(
                jenkinsRule,
                "copiee",
            "writeFile text: 'foobar', file: 'artifact.txt';"
            + "archive includes: 'artifact.txt';"
        );
        jenkinsRule.assertBuildStatusSuccess(copiee.scheduleBuild2(0)).keepLog();

        WorkflowJob copier = JenkinsRuleUtil.createWorkflow(
                jenkinsRule,
                "copier",
            "copyArtifacts(projectName: 'copiee', selector: latestSavedBuild());"
            + "echo readFile('artifact.txt');"
        );
        jenkinsRule.assertLogContains("foobar", jenkinsRule.assertBuildStatusSuccess(copier.scheduleBuild2(0)));
    }

    @Test
    void testSpecificBuildSelector() throws Exception {
        WorkflowJob copier = JenkinsRuleUtil.createWorkflow(
                jenkinsRule,
                "copier",
            "copyArtifacts(projectName: 'copiee', selector: specific(\"${build('copiee').number}\"));"
            + "echo readFile('artifact.txt');"
        );
        JenkinsRuleUtil.createWorkflow(
                jenkinsRule,
                "copiee",
            "writeFile text: 'foobar', file: 'artifact.txt';"
            + "archive includes: 'artifact.txt';"
        );
        jenkinsRule.assertLogContains("foobar", jenkinsRule.assertBuildStatusSuccess(copier.scheduleBuild2(0)));
    }

    @Issue("JENKINS-47074")
    @Test
    void testSpecificBuildSelectorWithId() throws Exception {
        // build number == build id since Jenkins 1.597 (JENKINS-24380).
        WorkflowJob copier = JenkinsRuleUtil.createWorkflow(
                jenkinsRule,
                "copier",
            "copyArtifacts(projectName: 'copiee', selector: specific(build('copiee').id));"
            + "echo readFile('artifact.txt');"
        );
        JenkinsRuleUtil.createWorkflow(
                jenkinsRule,
                "copiee",
            "writeFile text: 'foobar', file: 'artifact.txt';"
            + "archive includes: 'artifact.txt';"
        );
        jenkinsRule.assertLogContains("foobar", jenkinsRule.assertBuildStatusSuccess(copier.scheduleBuild2(0)));
    }

    @Test
    void testStatusBuildSelector() throws Exception {
        WorkflowJob copiee = JenkinsRuleUtil.createWorkflow(
                jenkinsRule,
                "copiee",
            "writeFile text: 'foobar', file: 'artifact.txt';"
            + "archive includes: 'artifact.txt';"
        );
        jenkinsRule.assertBuildStatusSuccess(copiee.scheduleBuild2(0));

        WorkflowJob copier = JenkinsRuleUtil.createWorkflow(
                jenkinsRule,
                "copier",
            "copyArtifacts(projectName: 'copiee', selector: lastSuccessful());"
            + "echo readFile('artifact.txt');"
        );
        jenkinsRule.assertLogContains("foobar", jenkinsRule.assertBuildStatusSuccess(copier.scheduleBuild2(0)));
    }

    @Test
    void testTriggeredBuildSelector() throws Exception {
        WorkflowJob copiee = JenkinsRuleUtil.createWorkflow(
                jenkinsRule,
                "copiee",
            "writeFile text: 'foobar', file: 'artifact.txt';"
            + "archive includes: 'artifact.txt';"
            + "build('copier');"
        );

        WorkflowJob copier = JenkinsRuleUtil.createWorkflow(
                jenkinsRule,
                "copier",
            "copyArtifacts(projectName: 'copiee', selector: upstream());"
            + "echo readFile('artifact.txt');"
        );
        jenkinsRule.assertBuildStatusSuccess(copiee.scheduleBuild2(0));
        jenkinsRule.assertLogContains("foobar", jenkinsRule.assertBuildStatusSuccess(copier.getLastBuild()));
    }

    @Test
    void testWorkspaceBuildSelector() throws Exception {
        // workspace can copy only from `AbstractProject`s
        FreeStyleProject copiee = jenkinsRule.createFreeStyleProject("copiee");
        copiee.getBuildersList().add(new FileWriteBuilder("artifact.txt", "foobar"));
        copiee.getPublishersList().add(new ArtifactArchiver("artifact.txt"));
        jenkinsRule.assertBuildStatusSuccess(copiee.scheduleBuild2(0));

        WorkflowJob copier = JenkinsRuleUtil.createWorkflow(
                jenkinsRule,
                "copier",
            "copyArtifacts(projectName: 'copiee', selector: workspace());"
            + "echo readFile('artifact.txt');"
        );
        jenkinsRule.assertLogContains("foobar", jenkinsRule.assertBuildStatusSuccess(copier.scheduleBuild2(0)));
    }

    @Issue("JENKINS-61811")
    @Test
    void testBuildSelectorParameter() throws Exception {
        WorkflowJob src = JenkinsRuleUtil.createWorkflow(
                jenkinsRule,
                "TEST",
            "writeFile text: 'foobar', file: 'artifact.txt';"
            + "archive includes: 'artifact.txt';"
        );
        jenkinsRule.assertBuildStatusSuccess(src.scheduleBuild2(0));

        WorkflowJob dest = jenkinsRule.jenkins.createProject(WorkflowJob.class, "dest");
        dest.setDefinition(new CpsFlowDefinition(
                """
                        pipeline {
                          agent any
                          parameters {
                            buildSelector(defaultSelector: lastSuccessful(), description: 'Build Selector', name: 'BUILD_SELECTOR')
                          }
                          stages {
                            stage('Test') {
                              steps {
                                copyArtifacts fingerprintArtifacts: true, projectName: 'TEST', selector: buildParameter(params.BUILD_SELECTOR)
                              }
                            }
                         }
                        }""",
            true
        ));
        jenkinsRule.assertBuildStatusSuccess(dest.scheduleBuild2(0));
    }
}
