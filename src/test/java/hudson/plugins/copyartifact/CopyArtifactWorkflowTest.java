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

import static org.junit.Assert.*;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.Run;
import hudson.model.StringParameterDefinition;
import hudson.model.StringParameterValue;
import hudson.model.Cause.UserCause;
import hudson.plugins.copyartifact.testutils.CopyArtifactJenkinsRule;
import hudson.plugins.copyartifact.testutils.CopyArtifactUtil;
import hudson.plugins.copyartifact.testutils.FileWriteBuilder;
import hudson.tasks.ArtifactArchiver;

import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.List;

/**
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 */
public class CopyArtifactWorkflowTest {

    @Rule
    public CopyArtifactJenkinsRule jenkinsRule = new CopyArtifactJenkinsRule();

    @Test
    public void test_simpleUntriggeredCopy() throws Exception {
        // create "project_1" with an archived artifact...
        WorkflowJob project_1 = jenkinsRule.createWorkflow("project_1",
                "writeFile text: 'hello', file: 'hello.txt'; " +
                "step([$class: 'ArtifactArchiver', artifacts: 'hello.txt', fingerprint: true])");
        WorkflowRun b = jenkinsRule.assertBuildStatusSuccess(project_1.scheduleBuild2(0));
        assertArtifactInArchive(b);

        // Now lets try copy the artifact from "project_1" to "project_2"
        WorkflowJob project_2 = jenkinsRule.createWorkflow("project_2",
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
    public void testFilterByParametersForWorkflow() throws Exception {
        WorkflowJob copiee = jenkinsRule.createWorkflow("copiee",
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
        
        FreeStyleBuild build = copier.scheduleBuild2(0, new UserCause(), new ParametersAction(
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
    public void copyFromDownstreamBuild() throws Exception {
        WorkflowJob us = jenkinsRule.createWorkflow("us", "step([$class: 'CopyArtifact', projectName: 'ds', selector: [$class: 'SpecificBuildSelector', buildNumber: \"${build('ds').number}\"]]); echo readFile('art')");
        WorkflowJob ds = jenkinsRule.createWorkflow("ds", "writeFile file: 'art', text: env.BUILD_TAG; archive includes: 'art'");
        jenkinsRule.assertLogContains("jenkins-ds-1", jenkinsRule.assertBuildStatusSuccess(us.scheduleBuild2(0)));
    }

    private void assertArtifactInArchive(Run b) {
        List<WorkflowRun.Artifact> artifacts = b.getArtifacts();
        Assert.assertEquals(1, artifacts.size());
        Assert.assertEquals("hello.txt", artifacts.get(0).relativePath);
    }

    // Tests for symbols.
    // Test only that symbols are accessible as expected.
    // No tests for features here as they should be tested in other tests.

    @Test
    public void testDownstreamBuildSelector() throws Exception {
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

        WorkflowJob copier = jenkinsRule.createWorkflow(
            "copier",
            "copyArtifacts(projectName: 'copiee', selector: downstream(upstreamProjectName: 'upstream', upstreamBuildNumber: '1'));"
            + "echo readFile('artifact.txt');"
        );
        jenkinsRule.assertLogContains("foobar", jenkinsRule.assertBuildStatusSuccess(copier.scheduleBuild2(0)));
    }

    @Test
    public void testLastCompletedBuildSelector() throws Exception {
        WorkflowJob copiee = jenkinsRule.createWorkflow(
            "copiee",
            "writeFile text: 'foobar', file: 'artifact.txt';"
            + "archive includes: 'artifact.txt';"
        );
        jenkinsRule.assertBuildStatusSuccess(copiee.scheduleBuild2(0));

        WorkflowJob copier = jenkinsRule.createWorkflow(
            "copier",
            "copyArtifacts(projectName: 'copiee', selector: lastCompleted());"
            + "echo readFile('artifact.txt');"
        );
        jenkinsRule.assertLogContains("foobar", jenkinsRule.assertBuildStatusSuccess(copier.scheduleBuild2(0)));
    }

    @Test
    public void testParameterizedBuildSelector() throws Exception {
        WorkflowJob copiee = jenkinsRule.createWorkflow(
            "copiee",
            "writeFile text: 'foobar', file: 'artifact.txt';"
            + "archive includes: 'artifact.txt';"
        );
        jenkinsRule.assertBuildStatusSuccess(copiee.scheduleBuild2(0));

        WorkflowJob copier = jenkinsRule.createWorkflow(
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
        JenkinsRule.WebClient wc = jenkinsRule.createAllow405WebClient();
        jenkinsRule.submit(wc.getPage(copier, "build").getFormByName("parameters"));
        jenkinsRule.waitUntilNoActivity();

        jenkinsRule.assertLogContains("foobar", jenkinsRule.assertBuildStatusSuccess(copier.getLastBuild()));
    }

    @Test
    public void testPermalinkBuildSelector() throws Exception {
        WorkflowJob copiee = jenkinsRule.createWorkflow(
            "copiee",
            "writeFile text: 'foobar', file: 'artifact.txt';"
            + "archive includes: 'artifact.txt';"
        );
        jenkinsRule.assertBuildStatusSuccess(copiee.scheduleBuild2(0));

        WorkflowJob copier = jenkinsRule.createWorkflow(
            "copier",
            "copyArtifacts(projectName: 'copiee', selector: permalink('lastStableBuild'));"
            + "echo readFile('artifact.txt');"
        );
        jenkinsRule.assertLogContains("foobar", jenkinsRule.assertBuildStatusSuccess(copier.scheduleBuild2(0)));
    }

    @Test
    public void testSavedBuildSelector() throws Exception {
        WorkflowJob copiee = jenkinsRule.createWorkflow(
            "copiee",
            "writeFile text: 'foobar', file: 'artifact.txt';"
            + "archive includes: 'artifact.txt';"
        );
        jenkinsRule.assertBuildStatusSuccess(copiee.scheduleBuild2(0)).keepLog();

        WorkflowJob copier = jenkinsRule.createWorkflow(
            "copier",
            "copyArtifacts(projectName: 'copiee', selector: latestSavedBuild());"
            + "echo readFile('artifact.txt');"
        );
        jenkinsRule.assertLogContains("foobar", jenkinsRule.assertBuildStatusSuccess(copier.scheduleBuild2(0)));
    }

    @Test
    public void testSpecificBuildSelector() throws Exception {
        WorkflowJob copier = jenkinsRule.createWorkflow(
            "copier",
            "copyArtifacts(projectName: 'copiee', selector: specific(\"${build('copiee').number}\"));"
            + "echo readFile('artifact.txt');"
        );
        jenkinsRule.createWorkflow(
            "copiee",
            "writeFile text: 'foobar', file: 'artifact.txt';"
            + "archive includes: 'artifact.txt';"
        );
        jenkinsRule.assertLogContains("foobar", jenkinsRule.assertBuildStatusSuccess(copier.scheduleBuild2(0)));
    }

    @Issue("JENKINS-47074")
    @Test
    public void testSpecificBuildSelectorWithId() throws Exception {
        // build number == build id since Jenkins 1.597 (JENKINS-24380).
        WorkflowJob copier = jenkinsRule.createWorkflow(
            "copier",
            "copyArtifacts(projectName: 'copiee', selector: specific(build('copiee').id));"
            + "echo readFile('artifact.txt');"
        );
        jenkinsRule.createWorkflow(
            "copiee",
            "writeFile text: 'foobar', file: 'artifact.txt';"
            + "archive includes: 'artifact.txt';"
        );
        jenkinsRule.assertLogContains("foobar", jenkinsRule.assertBuildStatusSuccess(copier.scheduleBuild2(0)));
    }

    @Test
    public void testStatusBuildSelector() throws Exception {
        WorkflowJob copiee = jenkinsRule.createWorkflow(
            "copiee",
            "writeFile text: 'foobar', file: 'artifact.txt';"
            + "archive includes: 'artifact.txt';"
        );
        jenkinsRule.assertBuildStatusSuccess(copiee.scheduleBuild2(0));

        WorkflowJob copier = jenkinsRule.createWorkflow(
            "copier",
            "copyArtifacts(projectName: 'copiee', selector: lastSuccessful());"
            + "echo readFile('artifact.txt');"
        );
        jenkinsRule.assertLogContains("foobar", jenkinsRule.assertBuildStatusSuccess(copier.scheduleBuild2(0)));
    }

    @Test
    public void testTriggeredBuildSelector() throws Exception {
        WorkflowJob copiee = jenkinsRule.createWorkflow(
            "copiee",
            "writeFile text: 'foobar', file: 'artifact.txt';"
            + "archive includes: 'artifact.txt';"
            + "build('copier');"
        );

        WorkflowJob copier = jenkinsRule.createWorkflow(
            "copier",
            "copyArtifacts(projectName: 'copiee', selector: upstream());"
            + "echo readFile('artifact.txt');"
        );
        jenkinsRule.assertBuildStatusSuccess(copiee.scheduleBuild2(0));
        jenkinsRule.assertLogContains("foobar", jenkinsRule.assertBuildStatusSuccess(copier.getLastBuild()));
    }

    @Test
    public void testWorkspaceBuildSelector() throws Exception {
        // workspace can copy only from `AbstractProject`s
        FreeStyleProject copiee = jenkinsRule.createFreeStyleProject("copiee");
        copiee.getBuildersList().add(new FileWriteBuilder("artifact.txt", "foobar"));
        copiee.getPublishersList().add(new ArtifactArchiver("artifact.txt"));
        jenkinsRule.assertBuildStatusSuccess(copiee.scheduleBuild2(0));

        WorkflowJob copier = jenkinsRule.createWorkflow(
            "copier",
            "copyArtifacts(projectName: 'copiee', selector: workspace());"
            + "echo readFile('artifact.txt');"
        );
        jenkinsRule.assertLogContains("foobar", jenkinsRule.assertBuildStatusSuccess(copier.scheduleBuild2(0)));
    }

    @Issue("JENKINS-61811")
    @Test
    public void testBuildSelectorParameter() throws Exception {
        WorkflowJob src = jenkinsRule.createWorkflow(
            "TEST",
            "writeFile text: 'foobar', file: 'artifact.txt';"
            + "archive includes: 'artifact.txt';"
        );
        jenkinsRule.assertBuildStatusSuccess(src.scheduleBuild2(0));

        WorkflowJob dest = jenkinsRule.jenkins.createProject(WorkflowJob.class, "dest");
        dest.setDefinition(new CpsFlowDefinition(
            "pipeline {\n"
            + "  agent any\n"
            + "  parameters {\n"
            + "    buildSelector(defaultSelector: lastSuccessful(), description: 'Build Selector', name: 'BUILD_SELECTOR')\n"
            + "  }\n"
            + "  stages {\n"
            + "    stage('Test') {\n"
            + "      steps {\n"
            + "        copyArtifacts fingerprintArtifacts: true, projectName: 'TEST', selector: buildParameter(params.BUILD_SELECTOR)\n"
            + "      }\n"
            + "    }\n"
            + " }\n"
            + "}",
            true
        ));
        jenkinsRule.assertBuildStatusSuccess(dest.scheduleBuild2(0));
    }
}
