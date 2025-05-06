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

import hudson.cli.CLICommandInvoker;
import hudson.model.FreeStyleProject;
import hudson.model.ParameterDefinition;
import hudson.model.ParametersDefinitionProperty;

import java.io.ByteArrayInputStream;

import hudson.plugins.copyartifact.testutils.JenkinsRuleUtil;
import org.hamcrest.Matchers;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 */
@WithJenkins
class BuildSelectorParameterWorkflowTest {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    // Behaviors integrated with the CopyArtifact builder should be tested in other tests.

    @Test
    void testBuildSelectorParameter() throws Exception {
        WorkflowJob pipeline = JenkinsRuleUtil.createWorkflow(
                j,
                "pipeline",
                "properties([parameters([buildSelector(name: 'foo', description: 'description', defaultSelector: lastCompleted())])])"
        );
        j.assertBuildStatusSuccess(pipeline.scheduleBuild2(0));
        assertTrue(pipeline.isParameterized());
        ParametersDefinitionProperty property = pipeline.getProperty(ParametersDefinitionProperty.class);
        ParameterDefinition parameter = property.getParameterDefinition("foo");
        assertThat(parameter, Matchers.instanceOf(BuildSelectorParameter.class));
        BuildSelectorParameter buildSelectorParameter = (BuildSelectorParameter) parameter;
        assertEquals("description", buildSelectorParameter.getDescription());
        assertEquals(LastCompletedBuildSelector.class, buildSelectorParameter.getDefaultSelector().getClass());
    }

    @Test
    void testMigrateionFromOlderThan1_46() throws Exception {
        CLICommandInvoker.Result r = new CLICommandInvoker(j, "create-job")
            .withArgs("job1")
            .withStdin(new ByteArrayInputStream(
                (
                    "<?xml version='1.1' encoding='UTF-8'?>"
                    + "<project>"
                    + "  <actions/>"
                    + "  <description></description>"
                    + "  <keepDependencies>false</keepDependencies>"
                    + "  <properties>"
                    + "    <hudson.model.ParametersDefinitionProperty>"
                    + "      <parameterDefinitions>"
                    + "        <hudson.plugins.copyartifact.BuildSelectorParameter plugin='copyartifact@1.45.3'>"
                    + "          <name>BUILD_SELECTOR</name>"
                    + "          <description></description>"
                    + "          <defaultSelector class='hudson.plugins.copyartifact.PermalinkBuildSelector'>"
                    + "            <id>lastCompletedBuild</id>"
                    + "          </defaultSelector>"
                    + "       </hudson.plugins.copyartifact.BuildSelectorParameter>"
                    + "     </parameterDefinitions>"
                    + "   </hudson.model.ParametersDefinitionProperty>"
                    + "  </properties>"
                    + "  <scm class='hudson.scm.NullSCM'/>"
                    + "  <canRoam>true</canRoam>"
                    + "  <disabled>false</disabled>"
                    + "  <blockBuildWhenDownstreamBuilding>false</blockBuildWhenDownstreamBuilding>"
                    + "  <blockBuildWhenUpstreamBuilding>false</blockBuildWhenUpstreamBuilding>"
                    + "  <triggers/>"
                    + "  <concurrentBuild>false</concurrentBuild>"
                    + "  <builders/>"
                    + "  <publishers/>"
                    + "  <buildWrappers/>"
                    + "</project>"
                ).getBytes()
            ))
            .invoke();
        assertEquals(0, r.returnCode(), r.stderr());

        FreeStyleProject p = j.jenkins.getItemByFullName("job1", FreeStyleProject.class);
        BuildSelectorParameter buildSelectorParameter = (BuildSelectorParameter)p.getProperty(ParametersDefinitionProperty.class)
            .getParameterDefinition("BUILD_SELECTOR");
        j.assertEqualDataBoundBeans(
            new PermalinkBuildSelector("lastCompletedBuild"),
            buildSelectorParameter.getDefaultSelector()
        );

        p.save();
        j.jenkins.reload();

        p = j.jenkins.getItemByFullName("job1", FreeStyleProject.class);
        buildSelectorParameter = (BuildSelectorParameter)p.getProperty(ParametersDefinitionProperty.class)
            .getParameterDefinition("BUILD_SELECTOR");
        j.assertEqualDataBoundBeans(
            new PermalinkBuildSelector("lastCompletedBuild"),
            buildSelectorParameter.getDefaultSelector()
        );
    }

    @Test
    void testRestoreDefaultSelector() throws Exception {
        CLICommandInvoker.Result r = new CLICommandInvoker(j, "create-job")
            .withArgs("job1")
            .withStdin(new ByteArrayInputStream(
                (
                    "<?xml version='1.1' encoding='UTF-8'?>"
                    + "<project>"
                    + "  <actions/>"
                    + "  <description></description>"
                    + "  <keepDependencies>false</keepDependencies>"
                    + "  <properties>"
                    + "    <hudson.model.ParametersDefinitionProperty>"
                    + "      <parameterDefinitions>"
                    + "        <hudson.plugins.copyartifact.BuildSelectorParameter plugin='copyartifact@1.46'>"
                    + "          <name>BUILD_SELECTOR</name>"
                    + "          <description></description>"
                    + "          <defaultSelectorXml>&lt;PermalinkBuildSelector plugin=&quot;copyartifact@1.46-SNAPSHOT&quot;&gt;  &lt;id&gt;lastCompletedBuild&lt;/id&gt;&lt;/PermalinkBuildSelector&gt;</defaultSelectorXml>"
                    + "       </hudson.plugins.copyartifact.BuildSelectorParameter>"
                    + "     </parameterDefinitions>"
                    + "   </hudson.model.ParametersDefinitionProperty>"
                    + "  </properties>"
                    + "  <scm class='hudson.scm.NullSCM'/>"
                    + "  <canRoam>true</canRoam>"
                    + "  <disabled>false</disabled>"
                    + "  <blockBuildWhenDownstreamBuilding>false</blockBuildWhenDownstreamBuilding>"
                    + "  <blockBuildWhenUpstreamBuilding>false</blockBuildWhenUpstreamBuilding>"
                    + "  <triggers/>"
                    + "  <concurrentBuild>false</concurrentBuild>"
                    + "  <builders/>"
                    + "  <publishers/>"
                    + "  <buildWrappers/>"
                    + "</project>"
                ).getBytes()
            ))
            .invoke();
        assertEquals(0, r.returnCode(), r.stderr());

        FreeStyleProject p = j.jenkins.getItemByFullName("job1", FreeStyleProject.class);
        BuildSelectorParameter buildSelectorParameter = (BuildSelectorParameter)p.getProperty(ParametersDefinitionProperty.class)
            .getParameterDefinition("BUILD_SELECTOR");
        j.assertEqualDataBoundBeans(
            new PermalinkBuildSelector("lastCompletedBuild"),
            buildSelectorParameter.getDefaultSelector()
        );
    }

    @Test
    void testBrokenXml() throws Exception {
        // Broken configuration, no defaultSelector nor defaultSelectorXml.
        CLICommandInvoker.Result r = new CLICommandInvoker(j, "create-job")
            .withArgs("job1")
            .withStdin(new ByteArrayInputStream(
                (
                    "<?xml version='1.1' encoding='UTF-8'?>"
                    + "<project>"
                    + "  <actions/>"
                    + "  <description></description>"
                    + "  <keepDependencies>false</keepDependencies>"
                    + "  <properties>"
                    + "    <hudson.model.ParametersDefinitionProperty>"
                    + "      <parameterDefinitions>"
                    + "        <hudson.plugins.copyartifact.BuildSelectorParameter plugin='copyartifact@1.45.3'>"
                    + "          <name>BUILD_SELECTOR</name>"
                    + "          <description></description>"
                    + "       </hudson.plugins.copyartifact.BuildSelectorParameter>"
                    + "     </parameterDefinitions>"
                    + "   </hudson.model.ParametersDefinitionProperty>"
                    + "  </properties>"
                    + "  <scm class='hudson.scm.NullSCM'/>"
                    + "  <canRoam>true</canRoam>"
                    + "  <disabled>false</disabled>"
                    + "  <blockBuildWhenDownstreamBuilding>false</blockBuildWhenDownstreamBuilding>"
                    + "  <blockBuildWhenUpstreamBuilding>false</blockBuildWhenUpstreamBuilding>"
                    + "  <triggers/>"
                    + "  <concurrentBuild>false</concurrentBuild>"
                    + "  <builders/>"
                    + "  <publishers/>"
                    + "  <buildWrappers/>"
                    + "</project>"
                ).getBytes()
            ))
            .invoke();
        assertEquals(0, r.returnCode(), r.stderr());

        FreeStyleProject p = j.jenkins.getItemByFullName("job1", FreeStyleProject.class);
        BuildSelectorParameter buildSelectorParameter = (BuildSelectorParameter)p.getProperty(ParametersDefinitionProperty.class)
            .getParameterDefinition("BUILD_SELECTOR");
        assertNull(buildSelectorParameter.getDefaultSelector());

        p.save();
        j.jenkins.reload();

        p = j.jenkins.getItemByFullName("job1", FreeStyleProject.class);
        buildSelectorParameter = (BuildSelectorParameter)p.getProperty(ParametersDefinitionProperty.class)
            .getParameterDefinition("BUILD_SELECTOR");
        assertNull(buildSelectorParameter.getDefaultSelector());
    }
}
