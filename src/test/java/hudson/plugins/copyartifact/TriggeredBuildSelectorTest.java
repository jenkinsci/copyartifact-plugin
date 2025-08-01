/*
 * The MIT License
 *
 * Copyright (c) 2014 IKEDA Yasuyuki
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.Arrays;
import java.io.File;

import hudson.plugins.copyartifact.testutils.JenkinsRuleUtil;
import org.apache.commons.io.FileUtils;

import hudson.Functions;
import hudson.Util;
import hudson.maven.MavenModuleSet;
import hudson.model.Cause;
import hudson.model.FreeStyleProject;
import hudson.model.ParameterDefinition;
import hudson.model.ParametersAction;
import hudson.model.StringParameterValue;
import hudson.model.FreeStyleBuild;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.Result;
import hudson.model.StringParameterDefinition;
import hudson.plugins.copyartifact.testutils.CopyArtifactUtil;
import hudson.plugins.copyartifact.testutils.FileWriteBuilder;
import hudson.plugins.copyartifact.testutils.RemoveUpstreamBuilder;
import hudson.tasks.ArtifactArchiver;
import hudson.tasks.BuildTrigger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.JenkinsRule.WebClient;
import org.jvnet.hudson.test.SleepBuilder;
import org.jvnet.hudson.test.ToolInstallations;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * Tests for {@link TriggeredBuildSelector}.
 * Some of tests of {@link TriggeredBuildSelector} are also in {@link CopyArtifactTest}
 */
@WithJenkins
class TriggeredBuildSelectorTest {

    private JenkinsRule j;

    @TempDir
    private File tempFolder;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    /**
     * Tests that web configuration page works correct.
     * @throws Exception
     */
    @Test
    void testWebConfiguration() throws Exception {
        WebClient wc = j.createWebClient();
        {
            FreeStyleProject p = j.createFreeStyleProject();
            p.getBuildersList().add(CopyArtifactUtil.createCopyArtifact(
                    "${upstream}",
                    "",
                    new TriggeredBuildSelector(true, TriggeredBuildSelector.UpstreamFilterStrategy.UseOldest, false),
                    "",
                    "",
                    false,
                    false,
                    false
            ));
            p.save();

            j.submit(wc.getPage(p, "configure").getFormByName("config"));

            p = j.jenkins.getItemByFullName(p.getFullName(), FreeStyleProject.class);
            assertNotNull(p);

            CopyArtifact copyArtifact = p.getBuildersList().get(CopyArtifact.class);
            assertNotNull(p);

            assertNotNull(copyArtifact.getBuildSelector());
            assertEquals(TriggeredBuildSelector.class, copyArtifact.getBuildSelector().getClass());

            TriggeredBuildSelector selector = (TriggeredBuildSelector)copyArtifact.getBuildSelector();

            assertTrue(selector.isFallbackToLastSuccessful());
            assertEquals(TriggeredBuildSelector.UpstreamFilterStrategy.UseOldest, selector.getUpstreamFilterStrategy());
        }

        {
            FreeStyleProject p = j.createFreeStyleProject();
            p.getBuildersList().add(CopyArtifactUtil.createCopyArtifact(
                    "${upstream}",
                    "",
                    new TriggeredBuildSelector(false, TriggeredBuildSelector.UpstreamFilterStrategy.UseNewest, false),
                    "",
                    "",
                    false,
                    false,
                    false
            ));
            p.save();

            j.submit(wc.getPage(p, "configure").getFormByName("config"));

            p = j.jenkins.getItemByFullName(p.getFullName(), FreeStyleProject.class);
            assertNotNull(p);

            CopyArtifact copyArtifact = p.getBuildersList().get(CopyArtifact.class);
            assertNotNull(p);

            assertNotNull(copyArtifact.getBuildSelector());
            assertEquals(TriggeredBuildSelector.class, copyArtifact.getBuildSelector().getClass());

            TriggeredBuildSelector selector = (TriggeredBuildSelector)copyArtifact.getBuildSelector();

            assertFalse(selector.isFallbackToLastSuccessful());
            assertEquals(TriggeredBuildSelector.UpstreamFilterStrategy.UseNewest, selector.getUpstreamFilterStrategy());
        }

        {
            FreeStyleProject p = j.createFreeStyleProject();
            p.getBuildersList().add(CopyArtifactUtil.createCopyArtifact(
                    "${upstream}",
                    "",
                    new TriggeredBuildSelector(true, TriggeredBuildSelector.UpstreamFilterStrategy.UseGlobalSetting, false),
                    "",
                    "",
                    false,
                    false,
                    false
            ));
            p.save();

            j.submit(wc.getPage(p, "configure").getFormByName("config"));

            p = j.jenkins.getItemByFullName(p.getFullName(), FreeStyleProject.class);
            assertNotNull(p);

            CopyArtifact copyArtifact = p.getBuildersList().get(CopyArtifact.class);
            assertNotNull(p);

            assertNotNull(copyArtifact.getBuildSelector());
            assertEquals(TriggeredBuildSelector.class, copyArtifact.getBuildSelector().getClass());

            TriggeredBuildSelector selector = (TriggeredBuildSelector)copyArtifact.getBuildSelector();

            assertTrue(selector.isFallbackToLastSuccessful());
            assertEquals(TriggeredBuildSelector.UpstreamFilterStrategy.UseGlobalSetting, selector.getUpstreamFilterStrategy());
        }
    }

    @Test
    void testGlobalConfiguration() throws Exception {
        WebClient wc = j.createWebClient();
        TriggeredBuildSelector.DescriptorImpl d = (TriggeredBuildSelector.DescriptorImpl)j.jenkins.getDescriptorOrDie(TriggeredBuildSelector.class);
        {
            d.setGlobalUpstreamFilterStrategy(TriggeredBuildSelector.UpstreamFilterStrategy.UseNewest);
            j.submit(wc.getPage(j.jenkins, "configure").getFormByName("config"));
            assertEquals(TriggeredBuildSelector.UpstreamFilterStrategy.UseNewest, d.getGlobalUpstreamFilterStrategy());
        }
        {
            d.setGlobalUpstreamFilterStrategy(TriggeredBuildSelector.UpstreamFilterStrategy.UseOldest);
            j.submit(wc.getPage(j.jenkins, "configure").getFormByName("config"));
            assertEquals(TriggeredBuildSelector.UpstreamFilterStrategy.UseOldest, d.getGlobalUpstreamFilterStrategy());
        }
    }

    @Test
    void testUseOldest() throws Exception {
        FreeStyleProject upstream = j.createFreeStyleProject();
        ParameterDefinition paramDef = new StringParameterDefinition("CONTENT", "foo");
        ParametersDefinitionProperty paramsDef = new ParametersDefinitionProperty(paramDef);
        upstream.addProperty(paramsDef);
        FreeStyleProject downstream = j.createFreeStyleProject();

        upstream.getBuildersList().add(new FileWriteBuilder("artifact.txt", "${CONTENT}"));
        upstream.getPublishersList().add(new ArtifactArchiver("artifact.txt", "", false, false));
        upstream.getPublishersList().add(new BuildTrigger(downstream.getName(), Result.SUCCESS));

        downstream.getBuildersList().add(CopyArtifactUtil.createCopyArtifact(
                upstream.getName(),
                "",
                new TriggeredBuildSelector(false, TriggeredBuildSelector.UpstreamFilterStrategy.UseOldest, false),
                "artifact.txt",
                "",
                false,
                false,
                true
        ));
        downstream.setQuietPeriod(5); // this allows upstream trigger can be merged.

        upstream.save();
        downstream.save();
        j.jenkins.rebuildDependencyGraph();

        // 3 upstream builds.
        j.assertBuildStatusSuccess(upstream.scheduleBuild2(0, new Cause.UserIdCause(), new ParametersAction(new StringParameterValue("CONTENT", "value1"))));
        j.assertBuildStatusSuccess(upstream.scheduleBuild2(0, new Cause.UserIdCause(), new ParametersAction(new StringParameterValue("CONTENT", "value2"))));
        j.assertBuildStatusSuccess(upstream.scheduleBuild2(0, new Cause.UserIdCause(), new ParametersAction(new StringParameterValue("CONTENT", "value3"))));

        // wait till downstream will be triggered and completed
        j.waitUntilNoActivity();

        FreeStyleBuild b = downstream.getLastBuild();
        assertNotNull(b);
        j.assertBuildStatusSuccess(b);

        assertEquals(
                3,
                Util.filter(b.getCauses(), Cause.UpstreamCause.class).size(),
                String.format("upstream triggers seem not to be merged into one downstream build. This means quietPeriod of downstream is too short in this environment: %s", b.getCauses())
        );

        assertEquals("value1", b.getWorkspace().child("artifact.txt").readToString());
    }

    @Test
    void testUseOldestByGlobalSetting() throws Exception {
        TriggeredBuildSelector.DescriptorImpl d = (TriggeredBuildSelector.DescriptorImpl)j.jenkins.getDescriptorOrDie(TriggeredBuildSelector.class);
        d.setGlobalUpstreamFilterStrategy(TriggeredBuildSelector.UpstreamFilterStrategy.UseOldest);

        FreeStyleProject upstream = j.createFreeStyleProject();
        ParameterDefinition paramDef = new StringParameterDefinition("CONTENT", "foo");
        ParametersDefinitionProperty paramsDef = new ParametersDefinitionProperty(paramDef);
        upstream.addProperty(paramsDef);
        FreeStyleProject downstream = j.createFreeStyleProject();

        upstream.getBuildersList().add(new FileWriteBuilder("artifact.txt", "${CONTENT}"));
        upstream.getPublishersList().add(new ArtifactArchiver("artifact.txt", "", false, false));
        upstream.getPublishersList().add(new BuildTrigger(downstream.getName(), Result.SUCCESS));

        downstream.getBuildersList().add(CopyArtifactUtil.createCopyArtifact(
                upstream.getName(),
                "",
                new TriggeredBuildSelector(false, TriggeredBuildSelector.UpstreamFilterStrategy.UseGlobalSetting, false),
                "artifact.txt",
                "",
                false,
                false,
                true
        ));
        downstream.setQuietPeriod(5); // this allows upstream trigger can be merged.

        upstream.save();
        downstream.save();
        j.jenkins.rebuildDependencyGraph();

        // 3 upstream builds.
        j.assertBuildStatusSuccess(upstream.scheduleBuild2(0, new Cause.UserIdCause(), new ParametersAction(new StringParameterValue("CONTENT", "value1"))));
        j.assertBuildStatusSuccess(upstream.scheduleBuild2(0, new Cause.UserIdCause(), new ParametersAction(new StringParameterValue("CONTENT", "value2"))));
        j.assertBuildStatusSuccess(upstream.scheduleBuild2(0, new Cause.UserIdCause(), new ParametersAction(new StringParameterValue("CONTENT", "value3"))));

        // wait till downstream will be triggered and completed
        j.waitUntilNoActivity();

        FreeStyleBuild b = downstream.getLastBuild();
        assertNotNull(b);
        j.assertBuildStatusSuccess(b);

        assertEquals(
                3,
                Util.filter(b.getCauses(), Cause.UpstreamCause.class).size(),
                String.format("upstream triggers seem not to be merged into one downstream build. This means quietPeriod of downstream is too short in this environment: %s", b.getCauses())
        );

        assertEquals("value1", b.getWorkspace().child("artifact.txt").readToString());
    }

    @Test
    void testUseNewest() throws Exception {
        FreeStyleProject upstream = j.createFreeStyleProject();
        ParameterDefinition paramDef = new StringParameterDefinition("CONTENT", "foo");
        ParametersDefinitionProperty paramsDef = new ParametersDefinitionProperty(paramDef);
        upstream.addProperty(paramsDef);
        FreeStyleProject downstream = j.createFreeStyleProject();

        upstream.getBuildersList().add(new FileWriteBuilder("artifact.txt", "${CONTENT}"));
        upstream.getPublishersList().add(new ArtifactArchiver("artifact.txt", "", false, false));
        upstream.getPublishersList().add(new BuildTrigger(downstream.getName(), Result.SUCCESS));

        downstream.getBuildersList().add(CopyArtifactUtil.createCopyArtifact(
                upstream.getName(),
                "",
                new TriggeredBuildSelector(false, TriggeredBuildSelector.UpstreamFilterStrategy.UseNewest, false),
                "artifact.txt",
                "",
                false,
                false,
                true
        ));
        downstream.setQuietPeriod(5); // this allows upstream trigger can be merged.

        upstream.save();
        downstream.save();
        j.jenkins.rebuildDependencyGraph();

        // 3 upstream builds.
        j.assertBuildStatusSuccess(upstream.scheduleBuild2(0, new Cause.UserIdCause(), new ParametersAction(new StringParameterValue("CONTENT", "value1"))));
        j.assertBuildStatusSuccess(upstream.scheduleBuild2(0, new Cause.UserIdCause(), new ParametersAction(new StringParameterValue("CONTENT", "value2"))));
        j.assertBuildStatusSuccess(upstream.scheduleBuild2(0, new Cause.UserIdCause(), new ParametersAction(new StringParameterValue("CONTENT", "value3"))));

        // wait till downstream will be triggered and completed
        j.waitUntilNoActivity();

        FreeStyleBuild b = downstream.getLastBuild();
        assertNotNull(b);
        j.assertBuildStatusSuccess(b);

        assertEquals(
                3,
                Util.filter(b.getCauses(), Cause.UpstreamCause.class).size(),
                String.format("upstream triggers seem not to be merged into one downstream build. This means quietPeriod of downstream is too short in this environment: %s", b.getCauses())
        );

        assertEquals("value3", b.getWorkspace().child("artifact.txt").readToString());
    }

    @Test
    void testUseNewestByGlobalSetting() throws Exception {
        TriggeredBuildSelector.DescriptorImpl d = (TriggeredBuildSelector.DescriptorImpl)j.jenkins.getDescriptorOrDie(TriggeredBuildSelector.class);
        d.setGlobalUpstreamFilterStrategy(TriggeredBuildSelector.UpstreamFilterStrategy.UseNewest);

        FreeStyleProject upstream = j.createFreeStyleProject();
        ParameterDefinition paramDef = new StringParameterDefinition("CONTENT", "foo");
        ParametersDefinitionProperty paramsDef = new ParametersDefinitionProperty(paramDef);
        upstream.addProperty(paramsDef);
        FreeStyleProject downstream = j.createFreeStyleProject();

        upstream.getBuildersList().add(new FileWriteBuilder("artifact.txt", "${CONTENT}"));
        upstream.getPublishersList().add(new ArtifactArchiver("artifact.txt", "", false, false));
        upstream.getPublishersList().add(new BuildTrigger(downstream.getName(), Result.SUCCESS));

        downstream.getBuildersList().add(CopyArtifactUtil.createCopyArtifact(
                upstream.getName(),
                "",
                new TriggeredBuildSelector(false, TriggeredBuildSelector.UpstreamFilterStrategy.UseGlobalSetting, false),
                "artifact.txt",
                "",
                false,
                false,
                true
        ));
        downstream.setQuietPeriod(5); // this allows upstream trigger can be merged.

        upstream.save();
        downstream.save();
        j.jenkins.rebuildDependencyGraph();

        // 3 upstream builds.
        j.assertBuildStatusSuccess(upstream.scheduleBuild2(0, new Cause.UserIdCause(), new ParametersAction(new StringParameterValue("CONTENT", "value1"))));
        j.assertBuildStatusSuccess(upstream.scheduleBuild2(0, new Cause.UserIdCause(), new ParametersAction(new StringParameterValue("CONTENT", "value2"))));
        j.assertBuildStatusSuccess(upstream.scheduleBuild2(0, new Cause.UserIdCause(), new ParametersAction(new StringParameterValue("CONTENT", "value3"))));

        // wait till downstream will be triggered and completed
        j.waitUntilNoActivity();

        FreeStyleBuild b = downstream.getLastBuild();
        assertNotNull(b);
        j.assertBuildStatusSuccess(b);

        assertEquals(
                3,
                Util.filter(b.getCauses(), Cause.UpstreamCause.class).size(),
                String.format("upstream triggers seem not to be merged into one downstream build. This means quietPeriod of downstream is too short in this environment: %s", b.getCauses())
        );

        assertEquals("value3", b.getWorkspace().child("artifact.txt").readToString());
    }

    @Test
    void testUseOldestNested() throws Exception {
        FreeStyleProject upstream = j.createFreeStyleProject();
        ParameterDefinition paramDef = new StringParameterDefinition("CONTENT", "foo");
        ParametersDefinitionProperty paramsDef = new ParametersDefinitionProperty(paramDef);
        upstream.addProperty(paramsDef);
        FreeStyleProject intermediate1 = j.createFreeStyleProject();
        FreeStyleProject intermediate2 = j.createFreeStyleProject();
        FreeStyleProject downstream = j.createFreeStyleProject();

        upstream.getBuildersList().add(new FileWriteBuilder("artifact.txt", "${CONTENT}"));

        intermediate1.getPublishersList().add(new BuildTrigger(downstream.getName(), Result.SUCCESS));
        intermediate1.setQuietPeriod(5);
        upstream.getPublishersList().add(new BuildTrigger(downstream.getName(), Result.SUCCESS));

        intermediate2.getPublishersList().add(new BuildTrigger(downstream.getName(), Result.SUCCESS));
        intermediate2.setQuietPeriod(5);
        upstream.getPublishersList().add(new BuildTrigger(downstream.getName(), Result.SUCCESS));

        intermediate1.getPublishersList().add(new BuildTrigger(downstream.getName(), Result.SUCCESS));

        downstream.getBuildersList().add(CopyArtifactUtil.createCopyArtifact(
                upstream.getName(),
                "",
                new TriggeredBuildSelector(false, TriggeredBuildSelector.UpstreamFilterStrategy.UseOldest, false),
                "artifact.txt",
                "",
                false,
                false,
                true
        ));
        downstream.setQuietPeriod(5); // this allows upstream trigger can be merged.

        upstream.save();
        intermediate1.save();
        intermediate2.save();
        downstream.save();
        j.jenkins.rebuildDependencyGraph();

        // 2 upstream builds for intermediate1.
        upstream.getPublishersList().clear();
        upstream.getPublishersList().add(new ArtifactArchiver("artifact.txt", "", false, false));
        upstream.getPublishersList().add(new BuildTrigger(intermediate1.getName(), Result.SUCCESS));
        upstream.save();
        j.jenkins.rebuildDependencyGraph();
        j.assertBuildStatusSuccess(upstream.scheduleBuild2(0, new Cause.UserIdCause(), new ParametersAction(new StringParameterValue("CONTENT", "value1"))));
        j.assertBuildStatusSuccess(upstream.scheduleBuild2(0, new Cause.UserIdCause(), new ParametersAction(new StringParameterValue("CONTENT", "value2"))));

        // 2 upstream builds for intermediate2.
        upstream.getPublishersList().clear();
        upstream.getPublishersList().add(new ArtifactArchiver("artifact.txt", "", false, false));
        upstream.getPublishersList().add(new BuildTrigger(intermediate2.getName(), Result.SUCCESS));
        upstream.save();
        j.jenkins.rebuildDependencyGraph();
        j.assertBuildStatusSuccess(upstream.scheduleBuild2(0, new Cause.UserIdCause(), new ParametersAction(new StringParameterValue("CONTENT", "value3"))));
        j.assertBuildStatusSuccess(upstream.scheduleBuild2(0, new Cause.UserIdCause(), new ParametersAction(new StringParameterValue("CONTENT", "value4"))));

        // wait till downstream will be triggered and completed
        j.waitUntilNoActivity();

        assertNotNull(intermediate1.getLastBuild());
        j.assertBuildStatusSuccess(intermediate1.getLastBuild());
        assertEquals(
                2,
                Util.filter(intermediate1.getLastBuild().getCauses(), Cause.UpstreamCause.class).size(),
                String.format("upstream triggers seem not to be merged into one downstream build. This means quietPeriod of intermediate1 is too short in this environment: %s", intermediate1.getLastBuild().getCauses())
        );

        assertNotNull(intermediate2.getLastBuild());
        j.assertBuildStatusSuccess(intermediate2.getLastBuild());
        assertEquals(
                2,
                Util.filter(intermediate2.getLastBuild().getCauses(), Cause.UpstreamCause.class).size(),
                String.format("upstream triggers seem not to be merged into one downstream build. This means quietPeriod of intermediate2 is too short in this environment: %s", intermediate2.getLastBuild().getCauses())
        );

        FreeStyleBuild b = downstream.getLastBuild();
        assertNotNull(b);
        j.assertBuildStatusSuccess(b);

        assertEquals(
                2,
                Util.filter(b.getCauses(), Cause.UpstreamCause.class).size(),
                String.format("upstream triggers seem not to be merged into one downstream build. This means quietPeriod of downstream is too short in this environment: %s", b.getCauses())
        );

        assertEquals("value1", b.getWorkspace().child("artifact.txt").readToString());
    }

    @Test
    void testUseNewestNested() throws Exception {
        FreeStyleProject upstream = j.createFreeStyleProject();
        ParameterDefinition paramDef = new StringParameterDefinition("CONTENT", "foo");
        ParametersDefinitionProperty paramsDef = new ParametersDefinitionProperty(paramDef);
        upstream.addProperty(paramsDef);
        FreeStyleProject intermediate1 = j.createFreeStyleProject();
        FreeStyleProject intermediate2 = j.createFreeStyleProject();
        FreeStyleProject downstream = j.createFreeStyleProject();

        upstream.getBuildersList().add(new FileWriteBuilder("artifact.txt", "${CONTENT}"));

        intermediate1.getPublishersList().add(new BuildTrigger(downstream.getName(), Result.SUCCESS));
        intermediate1.setQuietPeriod(5);
        upstream.getPublishersList().add(new BuildTrigger(downstream.getName(), Result.SUCCESS));

        intermediate2.getPublishersList().add(new BuildTrigger(downstream.getName(), Result.SUCCESS));
        intermediate2.setQuietPeriod(5);
        upstream.getPublishersList().add(new BuildTrigger(downstream.getName(), Result.SUCCESS));

        intermediate1.getPublishersList().add(new BuildTrigger(downstream.getName(), Result.SUCCESS));

        downstream.getBuildersList().add(CopyArtifactUtil.createCopyArtifact(
                upstream.getName(),
                "",
                new TriggeredBuildSelector(false, TriggeredBuildSelector.UpstreamFilterStrategy.UseNewest, false),
                "artifact.txt",
                "",
                false,
                false,
                true
        ));
        downstream.setQuietPeriod(5); // this allows upstream trigger can be merged.

        upstream.save();
        intermediate1.save();
        intermediate2.save();
        downstream.save();
        j.jenkins.rebuildDependencyGraph();

        // 2 upstream builds for intermediate1.
        upstream.getPublishersList().clear();
        upstream.getPublishersList().add(new ArtifactArchiver("artifact.txt", "", false, false));
        upstream.getPublishersList().add(new BuildTrigger(intermediate1.getName(), Result.SUCCESS));
        upstream.save();
        j.jenkins.rebuildDependencyGraph();
        j.assertBuildStatusSuccess(upstream.scheduleBuild2(0, new Cause.UserIdCause(), new ParametersAction(new StringParameterValue("CONTENT", "value1"))));
        j.assertBuildStatusSuccess(upstream.scheduleBuild2(0, new Cause.UserIdCause(), new ParametersAction(new StringParameterValue("CONTENT", "value2"))));

        // 2 upstream builds for intermediate2.
        upstream.getPublishersList().clear();
        upstream.getPublishersList().add(new ArtifactArchiver("artifact.txt", "", false, false));
        upstream.getPublishersList().add(new BuildTrigger(intermediate2.getName(), Result.SUCCESS));
        upstream.save();
        j.jenkins.rebuildDependencyGraph();
        j.assertBuildStatusSuccess(upstream.scheduleBuild2(0, new Cause.UserIdCause(), new ParametersAction(new StringParameterValue("CONTENT", "value3"))));
        j.assertBuildStatusSuccess(upstream.scheduleBuild2(0, new Cause.UserIdCause(), new ParametersAction(new StringParameterValue("CONTENT", "value4"))));

        // wait till downstream will be triggered and completed
        j.waitUntilNoActivity();

        assertNotNull(intermediate1.getLastBuild());
        j.assertBuildStatusSuccess(intermediate1.getLastBuild());
        assertEquals(
                2,
                Util.filter(intermediate1.getLastBuild().getCauses(), Cause.UpstreamCause.class).size(),
                String.format("upstream triggers seem not to be merged into one downstream build. This means quietPeriod of intermediate1 is too short in this environment: %s", intermediate1.getLastBuild().getCauses())
        );

        assertNotNull(intermediate2.getLastBuild());
        j.assertBuildStatusSuccess(intermediate2.getLastBuild());
        assertEquals(
                2,
                Util.filter(intermediate2.getLastBuild().getCauses(), Cause.UpstreamCause.class).size(),
                String.format("upstream triggers seem not to be merged into one downstream build. This means quietPeriod of intermediate2 is too short in this environment: %s", intermediate2.getLastBuild().getCauses())
        );

        FreeStyleBuild b = downstream.getLastBuild();
        assertNotNull(b);
        j.assertBuildStatusSuccess(b);

        assertEquals(
                2,
                Util.filter(b.getCauses(), Cause.UpstreamCause.class).size(),
                String.format("upstream triggers seem not to be merged into one downstream build. This means quietPeriod of downstream is too short in this environment: %s", b.getCauses())
        );

        assertEquals("value4", b.getWorkspace().child("artifact.txt").readToString());
    }

    @Test
    void testBackwardCompatibility() throws Exception {
        TriggeredBuildSelector.DescriptorImpl d = (TriggeredBuildSelector.DescriptorImpl)j.jenkins.getDescriptorOrDie(TriggeredBuildSelector.class);
        d.setGlobalUpstreamFilterStrategy(TriggeredBuildSelector.UpstreamFilterStrategy.UseOldest);

        FreeStyleProject upstream = j.createFreeStyleProject();
        ParameterDefinition paramDef = new StringParameterDefinition("CONTENT", "foo");
        ParametersDefinitionProperty paramsDef = new ParametersDefinitionProperty(paramDef);
        upstream.addProperty(paramsDef);
        FreeStyleProject downstream = j.createFreeStyleProject();

        upstream.getBuildersList().add(new FileWriteBuilder("artifact.txt", "${CONTENT}"));
        upstream.getPublishersList().add(new ArtifactArchiver("artifact.txt", "", false, false));
        upstream.getPublishersList().add(new BuildTrigger(downstream.getName(), Result.SUCCESS));

        downstream.getBuildersList().add(CopyArtifactUtil.createCopyArtifact(
                upstream.getName(),
                "",
                new TriggeredBuildSelector(false, null, false),
                "artifact.txt",
                "",
                false,
                false,
                true
        ));
        downstream.setQuietPeriod(5); // this allows upstream trigger can be merged.

        upstream.save();
        downstream.save();
        j.jenkins.rebuildDependencyGraph();

        // 3 upstream builds.
        j.assertBuildStatusSuccess(upstream.scheduleBuild2(0, new Cause.UserIdCause(), new ParametersAction(new StringParameterValue("CONTENT", "value1"))));
        j.assertBuildStatusSuccess(upstream.scheduleBuild2(0, new Cause.UserIdCause(), new ParametersAction(new StringParameterValue("CONTENT", "value2"))));
        j.assertBuildStatusSuccess(upstream.scheduleBuild2(0, new Cause.UserIdCause(), new ParametersAction(new StringParameterValue("CONTENT", "value3"))));

        // wait till downstream will be triggered and completed
        j.waitUntilNoActivity();

        FreeStyleBuild b = downstream.getLastBuild();
        assertNotNull(b);
        j.assertBuildStatusSuccess(b);

        assertEquals(
                3,
                Util.filter(b.getCauses(), Cause.UpstreamCause.class).size(),
                String.format("upstream triggers seem not to be merged into one downstream build. This means quietPeriod of downstream is too short in this environment: %s", b.getCauses())
        );

        assertEquals("value1", b.getWorkspace().child("artifact.txt").readToString());
    }

    @Test
    void testIsUseNewest() {
        // |Descriptor      |BuildSelector   |Result|
        // |:---------------|:---------------|:-----|
        // |null            |null            |false |
        // |UseGlobalSetting|UseGlobalSetting|false |
        // |UseOldest       |UseGlobalSetting|false |
        // |UseNewest       |UseGlobalSetting|true  |
        // |UseOldest       |UseNewest       |true  |
        // |UseNewest       |UseOldest       |false |
        TriggeredBuildSelector.DescriptorImpl d = (TriggeredBuildSelector.DescriptorImpl)j.jenkins.getDescriptorOrDie(TriggeredBuildSelector.class);

        d.setGlobalUpstreamFilterStrategy(null);
        assertFalse(new TriggeredBuildSelector(false, null, false).isUseNewest());

        d.setGlobalUpstreamFilterStrategy(TriggeredBuildSelector.UpstreamFilterStrategy.UseGlobalSetting);
        assertFalse(new TriggeredBuildSelector(false, TriggeredBuildSelector.UpstreamFilterStrategy.UseGlobalSetting, false).isUseNewest());

        d.setGlobalUpstreamFilterStrategy(TriggeredBuildSelector.UpstreamFilterStrategy.UseOldest);
        assertFalse(new TriggeredBuildSelector(false, TriggeredBuildSelector.UpstreamFilterStrategy.UseGlobalSetting, false).isUseNewest());

        d.setGlobalUpstreamFilterStrategy(TriggeredBuildSelector.UpstreamFilterStrategy.UseNewest);
        assertTrue(new TriggeredBuildSelector(false, TriggeredBuildSelector.UpstreamFilterStrategy.UseGlobalSetting, false).isUseNewest());

        d.setGlobalUpstreamFilterStrategy(TriggeredBuildSelector.UpstreamFilterStrategy.UseOldest);
        assertTrue(new TriggeredBuildSelector(false, TriggeredBuildSelector.UpstreamFilterStrategy.UseNewest, false).isUseNewest());

        d.setGlobalUpstreamFilterStrategy(TriggeredBuildSelector.UpstreamFilterStrategy.UseNewest);
        assertFalse(new TriggeredBuildSelector(false, TriggeredBuildSelector.UpstreamFilterStrategy.UseOldest, false).isUseNewest());
    }

    private String getDownstreamAfterOverlappingFlow(boolean allowUpstreamDependencies) throws Exception {
        //
        //     upstream   |   intermediate   |   downstream
        //
        // Initial build
        // 1     #1 -------.
        // 2               '-----> #1 ---------.
        // 3                                   '-----> #1
        //
        // Direct trigger of intermediate, then upstream
        // 4                 *---> #2 -------.
        // 5     #2 -----.                   |
        // 6             |                   '-------> #2
        // 7             '-------> #3 -----.
        // 8                               '---------> #3
        //

        FreeStyleProject upstream = j.createFreeStyleProject("upstream");
        ParameterDefinition paramDef = new StringParameterDefinition("CONTENT", "foo");
        ParametersDefinitionProperty paramsDef = new ParametersDefinitionProperty(paramDef);
        upstream.addProperty(paramsDef);
        FreeStyleProject intermediate = j.createFreeStyleProject("intermediate");
        FreeStyleProject downstream = j.createFreeStyleProject("downstream");

        upstream.getBuildersList().add(new FileWriteBuilder("artifact.txt", "${CONTENT}"));
        upstream.getPublishersList().add(new ArtifactArchiver("artifact.txt", "", false, false));
        upstream.getPublishersList().add(new BuildTrigger(intermediate.getName(), Result.SUCCESS));
        upstream.setQuietPeriod(0);

        intermediate.getBuildersList().add(CopyArtifactUtil.createCopyArtifact(
                upstream.getName(),
                "",
                new TriggeredBuildSelector(true, TriggeredBuildSelector.UpstreamFilterStrategy.UseGlobalSetting, allowUpstreamDependencies),
                "artifact.txt",
                "",
                false,
                false,
                true
        ));
        intermediate.getBuildersList().add(new SleepBuilder(1000));
        intermediate.getPublishersList().add(new ArtifactArchiver("artifact.txt", "", false, false));
        intermediate.getPublishersList().add(new BuildTrigger(downstream.getName(), Result.SUCCESS));
        intermediate.setQuietPeriod(0);

        downstream.getBuildersList().add(CopyArtifactUtil.createCopyArtifact(
                upstream.getName(),
                "",
                new TriggeredBuildSelector(true, TriggeredBuildSelector.UpstreamFilterStrategy.UseGlobalSetting, allowUpstreamDependencies),
                "artifact.txt",
                "upstream/",
                false,
                false,
                true
        ));
        downstream.getBuildersList().add(CopyArtifactUtil.createCopyArtifact(
                intermediate.getName(),
                "",
                new TriggeredBuildSelector(true, TriggeredBuildSelector.UpstreamFilterStrategy.UseGlobalSetting, allowUpstreamDependencies),
                "artifact.txt",
                "intermediate/",
                false,
                false,
                true
        ));
        downstream.getPublishersList().add(new ArtifactArchiver("upstream/artifact.txt,intermediate/artifact.txt", "", false, false));
        downstream.setQuietPeriod(0);

        upstream.save();
        intermediate.save();
        downstream.save();
        j.jenkins.rebuildDependencyGraph();

        // First (initial) build for each job
        j.assertBuildStatusSuccess(upstream.scheduleBuild2(0, new Cause.UserIdCause(), new ParametersAction(new StringParameterValue("CONTENT", "upstreamValue1"))));

        j.waitUntilNoActivity();

        // Trigger directly an 'intermediate#2' build, which depends on 'upstream#1'
        intermediate.scheduleBuild2(0, new Cause.UserIdCause()).waitForStart();

        // 'intermediate#2' build is running. Meanwhile, a new 'upstream#2' is completing and triggers 'intermediate#3':
        upstream.scheduleBuild2(0, new Cause.UserIdCause(), new ParametersAction(new StringParameterValue("CONTENT", "upstreamValue2")));

        j.waitUntilNoActivity();

        assertEquals(2, upstream.getBuilds().size(), "Upstream builds " + upstream.getBuilds());
        assertEquals(3, intermediate.getBuilds().size(), "Intermediate builds " + intermediate.getBuilds());
        if (!Functions.isWindows()) {
            // ci.jenkins.io Windows builds often report 2 downstream builds
            assertEquals(3, downstream.getBuilds().size(), "Downstream builds " + downstream.getBuilds());
        }

        // Get the 'downstream#2' build ...
        FreeStyleBuild downstreamBuild2 = downstream.getBuildByNumber(2);
        assertNotNull(downstreamBuild2);
        j.assertBuildStatusSuccess(downstreamBuild2);
        // ... that were triggered by the directly triggered 'intermediate#2' build.
        Cause.UpstreamCause cause = downstreamBuild2.getCause(Cause.UpstreamCause.class);
        assertNotNull(cause);
        assertEquals("intermediate #2", cause.getUpstreamRun().getFullDisplayName());

        // Return artifacts from downstream#2. One of them was copied from 'upstream' that is
        // - either the last successful upstream#2 build
        // - or upstream#1, which is the same that the triggering intermediate#2 depends on
        // depending on the value of allowUpstreamDependencies
        String artifactFromUpstream = FileUtils.readFileToString(new File(downstreamBuild2.getArtifactsDir(), "upstream/artifact.txt"), "UTF-8");
        String artifactFromIntermediate = FileUtils.readFileToString(new File(downstreamBuild2.getArtifactsDir(), "intermediate/artifact.txt"), "UTF-8");

        return artifactFromIntermediate + "," + artifactFromUpstream;
    }

    @Test
    void testTryUpstreamBuildDisabled() throws Exception {
        assertEquals("upstreamValue1,upstreamValue2", getDownstreamAfterOverlappingFlow(false));
    }

    @Test
    void testTryUpstreamBuildEnabled() throws Exception {
        assertEquals("upstreamValue1,upstreamValue1", getDownstreamAfterOverlappingFlow(true));
    }

    @Issue("JENKINS-18804")
    @Test
    void testUpstreamWasRemoved() throws Exception {
        // upstream -> downstream
        // Remove the build of upstream.
        {
            FreeStyleProject upstream = j.createFreeStyleProject();
            FreeStyleProject downstream = j.createFreeStyleProject();

            upstream.getBuildersList().add(new FileWriteBuilder(
                    "artifact.txt",
                    "foobar"
            ));
            upstream.getPublishersList().add(new ArtifactArchiver(
                    "**/*",
                    "",
                    false,
                    false
            ));
            upstream.getPublishersList().add(new BuildTrigger(
                    downstream.getFullName(),
                    false
            ));

            downstream.getBuildersList().add(new RemoveUpstreamBuilder());
            downstream.getBuildersList().add(CopyArtifactUtil.createCopyArtifact(
                    upstream.getFullName(),
                    "",
                    new TriggeredBuildSelector(false, TriggeredBuildSelector.UpstreamFilterStrategy.UseGlobalSetting, false),
                    "**/*",
                    "",
                    "",
                    false,
                    true, // This results build succeed even if the upstream build has been removed.
                    false
            ));

            j.jenkins.rebuildDependencyGraph();

            upstream.scheduleBuild2(0);
            j.waitUntilNoActivity();

            assertNull(upstream.getLastBuild());
            j.assertBuildStatusSuccess(downstream.getLastBuild());
        }

        // upstream -> intermediate -> downstream
        // Remove the build of upstream.
        {
            FreeStyleProject upstream = j.createFreeStyleProject();
            FreeStyleProject intermediate = j.createFreeStyleProject();
            FreeStyleProject downstream = j.createFreeStyleProject();

            upstream.getBuildersList().add(new FileWriteBuilder(
                    "artifact.txt",
                    "foobar"
            ));
            upstream.getPublishersList().add(new ArtifactArchiver(
                    "**/*",
                    "",
                    false,
                    false
            ));
            upstream.getPublishersList().add(new BuildTrigger(
                    intermediate.getFullName(),
                    false
            ));

            intermediate.getBuildersList().add(new RemoveUpstreamBuilder());
            intermediate.getPublishersList().add(new BuildTrigger(
                    downstream.getFullName(),
                    false
            ));

            downstream.getBuildersList().add(CopyArtifactUtil.createCopyArtifact(
                    upstream.getFullName(),
                    "",
                    new TriggeredBuildSelector(false, TriggeredBuildSelector.UpstreamFilterStrategy.UseGlobalSetting, false),
                    "**/*",
                    "",
                    "",
                    false,
                    true, // This results build succeed even if the upstream build has been removed.
                    false
            ));

            j.jenkins.rebuildDependencyGraph();

            upstream.scheduleBuild2(0);
            j.waitUntilNoActivity();

            assertNull(upstream.getLastBuild());
            j.assertBuildStatusSuccess(intermediate.getLastBuild());
            j.assertBuildStatusSuccess(downstream.getLastBuild());
        }

        // upstream -> intermediate -> downstream
        // Remove the build of intermediate.
        {
            FreeStyleProject upstream = j.createFreeStyleProject();
            FreeStyleProject intermediate = j.createFreeStyleProject();
            FreeStyleProject downstream = j.createFreeStyleProject();

            upstream.getBuildersList().add(new FileWriteBuilder(
                    "artifact.txt",
                    "foobar"
            ));
            upstream.getPublishersList().add(new ArtifactArchiver(
                    "**/*",
                    "",
                    false,
                    false
            ));
            upstream.getPublishersList().add(new BuildTrigger(
                    intermediate.getFullName(),
                    false
            ));

            intermediate.getPublishersList().add(new BuildTrigger(
                    downstream.getFullName(),
                    false
            ));

            downstream.getBuildersList().add(new RemoveUpstreamBuilder());
            downstream.getBuildersList().add(CopyArtifactUtil.createCopyArtifact(
                    upstream.getFullName(),
                    "",
                    new TriggeredBuildSelector(false, TriggeredBuildSelector.UpstreamFilterStrategy.UseGlobalSetting, false),
                    "**/*",
                    "",
                    "",
                    false,
                    true, // This results build succeed even if the upstream build has been removed.
                    false
            ));

            j.jenkins.rebuildDependencyGraph();

            upstream.scheduleBuild2(0);
            j.waitUntilNoActivity();

            j.assertBuildStatusSuccess(upstream.getLastBuild());
            assertNull(intermediate.getLastBuild());
            j.assertBuildStatusSuccess(downstream.getLastBuild());
        }
    }

    @Issue("JENKINS-14653")
    @Test
    void testMavenModule() throws Exception {
        ToolInstallations.configureMaven3();
        MavenModuleSet upstream = createMavenProject();
        FreeStyleProject downstream = j.createFreeStyleProject();

        upstream.setGoals("clean package -Dmaven.compiler.source=1.8 -Dmaven.compiler.target=1.8");
        upstream.setScm(JenkinsRuleUtil.getExtractResourceScm(tempFolder, getClass().getResource("maven-job")));
        upstream.getPublishersList().add(new BuildTrigger(downstream.getName(), Result.SUCCESS));

        downstream.getBuildersList().add(CopyArtifactUtil.createCopyArtifact(
                String.format("%s/org.jvnet.hudson.main.test.multimod$moduleB", upstream.getName()),
                "",
                new TriggeredBuildSelector(false, null, false),
                "**/*",
                "",
                "",
                false,
                false,
                false
        ));

        upstream.save();
        downstream.save();
        j.jenkins.rebuildDependencyGraph();

        j.assertBuildStatusSuccess(upstream.scheduleBuild2(0));
        j.waitUntilNoActivity();

        FreeStyleBuild b = downstream.getLastBuild();
        j.assertBuildStatusSuccess(b);

        assertTrue(
                b.getWorkspace().child("org.jvnet.hudson.main.test.multimod/moduleB/1.0-SNAPSHOT/moduleB-1.0-SNAPSHOT.jar").exists(),
                String.format(
                        "File not found: files are: %s",
                        Arrays.asList(b.getWorkspace().list("**/*"))
                )
        );
    }

    /**
     * Creates an empty Maven project with an unique name.
     *
     * @return an empty Maven project with an unique name.
     */
    private MavenModuleSet createMavenProject() throws IOException {
        MavenModuleSet mavenModuleSet = j.jenkins.createProject(MavenModuleSet.class, "test"+j.jenkins.getItems().size());
        mavenModuleSet.setRunHeadless(true);
        return mavenModuleSet;
    }

}
