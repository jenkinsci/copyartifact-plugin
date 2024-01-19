/*
 * The MIT License
 * 
 * Copyright (c) 2015 IKEDA Yasuyuki
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

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import jenkins.util.VirtualFile;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.ParameterDefinition;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.StringParameterDefinition;
import hudson.model.StringParameterValue;
import hudson.plugins.copyartifact.testutils.CopyArtifactUtil;
import hudson.plugins.copyartifact.testutils.FileWriteBuilder;
import hudson.tasks.ArtifactArchiver;

import org.apache.commons.io.IOUtils;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

/**
 * Tests for {@link ParameterizedBuildSelector}
 * 
 * @see CopyArtifactTest#testParameterizedBuildSelector()
 */
public class ParameterizedBuildSelectorTest {
    @ClassRule
    public static JenkinsRule j = new JenkinsRule();
    
    private WorkflowJob createWorkflowJob() throws IOException {
        return j.jenkins.createProject(WorkflowJob.class, "test"+j.jenkins.getItems().size());
    }

    /**
     * Should not cause a fatal error even for an undefined variable.
     * 
     * @throws Exception
     */
    @Issue("JENKINS-30357")
    @Test
    public void testUndefinedParameter() throws Exception {
        FreeStyleProject copiee = j.createFreeStyleProject();
        FreeStyleProject copier = j.createFreeStyleProject();
        
        ParameterizedBuildSelector pbs = new ParameterizedBuildSelector("NosuchVariable");
        copier.getBuildersList().add(CopyArtifactUtil.createCopyArtifact(
                copiee.getFullName(),
                null,   // parameters
                pbs,
                "**/*", // filter
                "",     // excludes
                false,  // flatten
                true,   // optional
                false   // fingerprintArtifacts
        ));
        FreeStyleBuild b = copier.scheduleBuild2(0).get();
        j.assertBuildStatusSuccess(b);
    }
    
    /**
     * Also applicable for workflow jobs.
     * 
     * @throws Exception
     */
    @Issue("JENKINS-30357")
    @Test
    public void testWorkflow() throws Exception {
        // Prepare an artifact to be copied.
        FreeStyleProject copiee = j.createFreeStyleProject();
        copiee.getBuildersList().add(new FileWriteBuilder("artifact.txt", "foobar"));
        copiee.getPublishersList().add(new ArtifactArchiver("artifact.txt"));
        j.assertBuildStatusSuccess(copiee.scheduleBuild2(0));

        WorkflowJob copier = createWorkflowJob();
        ParameterDefinition paramDef = new StringParameterDefinition("SELECTOR", "<StatusBuildSelector><stable>true</stable></StatusBuildSelector>");
        ParametersDefinitionProperty paramsDef = new ParametersDefinitionProperty(paramDef);
        copier.addProperty(paramsDef);
        copier.setDefinition(new CpsFlowDefinition(
            String.format(
                "node {"
                    + "step([$class: 'CopyArtifact',"
                        + "projectName: '%1$s',"
                        + "filter: '**/*',"
                        + "selector: [$class: 'ParameterizedBuildSelector', parameterName: 'SELECTOR'],"
                    + "]);"
                    + "step([$class: 'ArtifactArchiver', artifacts: '**/*']);"
                + "}",
                copiee.getFullName()
            ),
            true
        ));
        
        WorkflowRun b = j.assertBuildStatusSuccess(copier.scheduleBuild2(
                0,
                null,
                new ParametersAction(new StringParameterValue(
                        "SELECTOR",
                        "<StatusBuildSelector><stable>true</stable></StatusBuildSelector>"
                ))
        ));
        
        VirtualFile vf = b.getArtifactManager().root().child("artifact.txt");
        try(InputStream in = vf.open()) {
            assertEquals("foobar", IOUtils.toString(in, StandardCharsets.UTF_8));
        }
    }
    
    /**
     * Should not cause a fatal error even for a broken selector.
     * 
     * @throws Exception
     */
    @Test
    public void testBrokenParameter() throws Exception {
        FreeStyleProject copiee = j.createFreeStyleProject();
        FreeStyleProject copier = j.createFreeStyleProject();
        
        ParameterizedBuildSelector pbs = new ParameterizedBuildSelector("SELECTOR");
        copier.getBuildersList().add(CopyArtifactUtil.createCopyArtifact(
                copiee.getFullName(),
                null,   // parameters
                pbs,
                "**/*", // filter
                "",     // excludes
                false,  // flatten
                true,   // optional
                false   // fingerprintArtifacts
        ));
        FreeStyleBuild b = copier.scheduleBuild2(
                0,
                new ParametersAction(
                    new StringParameterValue("SELECTOR", "<SomeBrokenSelector")
                )
        ).get();
        j.assertBuildStatusSuccess(b);
    }
    
    /**
     * Should not cause a fatal error even for an unavailable selector.
     * 
     * @throws Exception
     */
    @Test
    public void testUnavailableSelector() throws Exception {
        FreeStyleProject copiee = j.createFreeStyleProject();
        FreeStyleProject copier = j.createFreeStyleProject();
        
        ParameterizedBuildSelector pbs = new ParameterizedBuildSelector("SELECTOR");
        copier.getBuildersList().add(CopyArtifactUtil.createCopyArtifact(
                copiee.getFullName(),
                null,   // parameters
                pbs,
                "**/*", // filter
                "",     // excludes
                false,  // flatten
                true,   // optional
                false   // fingerprintArtifacts
        ));
        FreeStyleBuild b = copier.scheduleBuild2(
                0,
                new ParametersAction(
                    new StringParameterValue("SELECTOR", "<NoSuchSelector />")
                )
        ).get();
        j.assertBuildStatusSuccess(b);
    }
    
    
    /**
     * Should not cause a fatal error even for an empty selector.
     * 
     * @throws Exception
     */
    @Test
    public void testEmptySelector() throws Exception {
        FreeStyleProject copiee = j.createFreeStyleProject();
        FreeStyleProject copier = j.createFreeStyleProject();
        
        ParameterizedBuildSelector pbs = new ParameterizedBuildSelector("SELECTOR");
        copier.getBuildersList().add(CopyArtifactUtil.createCopyArtifact(
                copiee.getFullName(),
                null,   // parameters
                pbs,
                "**/*", // filter
                "",     // excludes
                false,  // flatten
                true,   // optional
                false   // fingerprintArtifacts
        ));
        FreeStyleBuild b = copier.scheduleBuild2(
                0,
                new ParametersAction(
                    new StringParameterValue("SELECTOR", "")
                )
        ).get();
        j.assertBuildStatusSuccess(b);
    }
    
    /**
     * Also accepts immediate value.
     * 
     * @throws Exception
     */
    @Test
    public void testImmediateValue() throws Exception {
        // Prepare an artifact to be copied.
        FreeStyleProject copiee = j.createFreeStyleProject();
        copiee.getBuildersList().add(new FileWriteBuilder("artifact.txt", "foobar"));
        copiee.getPublishersList().add(new ArtifactArchiver("artifact.txt"));
        j.assertBuildStatusSuccess(copiee.scheduleBuild2(0));

        WorkflowJob copier = createWorkflowJob();
        ParameterDefinition paramDef = new StringParameterDefinition("SELECTOR", "<StatusBuildSelector><stable>true</stable></StatusBuildSelector>");
        ParametersDefinitionProperty paramsDef = new ParametersDefinitionProperty(paramDef);
        copier.addProperty(paramsDef);
        copier.setDefinition(new CpsFlowDefinition(
            String.format(
                "node {"
                    + "step([$class: 'CopyArtifact',"
                        + "projectName: '%1$s',"
                        + "filter: '**/*',"
                        + "selector: [$class: 'ParameterizedBuildSelector', parameterName: '${SELECTOR}'],"
                    + "]);"
                    + "step([$class: 'ArtifactArchiver', artifacts: '**/*']);"
                + "}",
                copiee.getFullName()
            ),
            true
        ));
        
        WorkflowRun b = j.assertBuildStatusSuccess(copier.scheduleBuild2(
                0,
                null,
                new ParametersAction(new StringParameterValue(
                        "SELECTOR",
                        "<StatusBuildSelector><stable>true</stable></StatusBuildSelector>"
                ))
        ));
        
        VirtualFile vf = b.getArtifactManager().root().child("artifact.txt");
        try(InputStream in = vf.open()) {
            assertEquals("foobar", IOUtils.toString(in, StandardCharsets.UTF_8));
        }
    }
    
    
    /**
     * Also accepts variable expression.
     * 
     * @throws Exception
     */
    @Test
    public void testVariableExpression() throws Exception {
        // Prepare an artifact to be copied.
        FreeStyleProject copiee = j.createFreeStyleProject();
        copiee.getBuildersList().add(new FileWriteBuilder("artifact.txt", "foobar"));
        copiee.getPublishersList().add(new ArtifactArchiver("artifact.txt"));
        j.assertBuildStatusSuccess(copiee.scheduleBuild2(0));
        
        FreeStyleProject copier = j.createFreeStyleProject();
        ParameterDefinition paramDef = new StringParameterDefinition("SELECTOR", "<StatusBuildSelector><stable>true</stable></StatusBuildSelector>");
        ParametersDefinitionProperty paramsDef = new ParametersDefinitionProperty(paramDef);
        copier.addProperty(paramsDef);
        ParameterizedBuildSelector pbs = new ParameterizedBuildSelector("${SELECTOR}");
        copier.getBuildersList().add(CopyArtifactUtil.createCopyArtifact(
                copiee.getFullName(),
                null,   // parameters
                pbs,
                "**/*", // filter
                "",     // excludes
                false,  // flatten
                false,  // optional
                false   // fingerprintArtifacts
        ));
        FreeStyleBuild b = j.assertBuildStatusSuccess(copier.scheduleBuild2(
                0,
                new ParametersAction(new StringParameterValue(
                        "SELECTOR",
                        "<StatusBuildSelector><stable>true</stable></StatusBuildSelector>"
                ))
        ).get());
        
        assertEquals("foobar", b.getWorkspace().child("artifact.txt").readToString());
    }
    
}
