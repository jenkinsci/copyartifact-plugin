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

import static org.junit.Assert.*;
import hudson.Util;
import hudson.model.Cause;
import hudson.model.FreeStyleProject;
import hudson.model.ParametersAction;
import hudson.model.StringParameterValue;
import hudson.model.FreeStyleBuild;
import hudson.model.Result;
import hudson.plugins.copyartifact.testutils.FileWriteBuilder;
import hudson.tasks.ArtifactArchiver;
import hudson.tasks.BuildTrigger;

import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.JenkinsRule.WebClient;

/**
 * Tests for {@link TriggeredBuildSelector}.
 * Some of tests of {@link TriggeredBuildSelector} are also in {@link CopyArtifactTest}
 */
public class TriggeredBuildSelectorTest {
    @Rule
    public JenkinsRule j = new JenkinsRule();
    
    /**
     * Tests that web configuration page works correct.
     * @throws Exception
     */
    @Test
    public void testWebConfiguration() throws Exception {
        WebClient wc = j.createWebClient();
        {
            FreeStyleProject p = j.createFreeStyleProject();
            p.getBuildersList().add(new CopyArtifact(
                    "${upstream}",
                    "",
                    new TriggeredBuildSelector(true, false),
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
            assertFalse(selector.isUseNewest());
        }
        
        {
            FreeStyleProject p = j.createFreeStyleProject();
            p.getBuildersList().add(new CopyArtifact(
                    "${upstream}",
                    "",
                    new TriggeredBuildSelector(false, true),
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
            assertTrue(selector.isUseNewest());
        }
    }
    
    @Test
    public void testUseOldest() throws Exception {
        FreeStyleProject upstream = j.createFreeStyleProject();
        FreeStyleProject downstream = j.createFreeStyleProject();
        
        upstream.getBuildersList().add(new FileWriteBuilder("artifact.txt", "${CONTENT}"));
        upstream.getPublishersList().add(new ArtifactArchiver("artifact.txt", "", false, false));
        upstream.getPublishersList().add(new BuildTrigger(downstream.getName(), Result.SUCCESS));
        
        downstream.getBuildersList().add(new CopyArtifact(
                upstream.getName(),
                "",
                new TriggeredBuildSelector(false, false),
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
        j.assertBuildStatusSuccess(upstream.scheduleBuild2(0, new Cause.UserCause(), new ParametersAction(new StringParameterValue("CONTENT", "value1"))));
        j.assertBuildStatusSuccess(upstream.scheduleBuild2(0, new Cause.UserCause(), new ParametersAction(new StringParameterValue("CONTENT", "value2"))));
        j.assertBuildStatusSuccess(upstream.scheduleBuild2(0, new Cause.UserCause(), new ParametersAction(new StringParameterValue("CONTENT", "value3"))));
        
        // wait till downstream will be triggered and completed
        j.waitUntilNoActivity();
        
        FreeStyleBuild b = downstream.getLastBuild();
        assertNotNull(b);
        j.assertBuildStatusSuccess(b);
        
        assertEquals(
                String.format("upstream triggers seem not to be merged into one downstream build. This means quietPeriod of downstream is too short in this environment: %s", b.getCauses()),
                3,
                Util.filter(b.getCauses(), Cause.UpstreamCause.class).size()
        );
        
        assertEquals("value1", b.getWorkspace().child("artifact.txt").readToString());
    }
    
    
    @Test
    public void testUseNewest() throws Exception {
        FreeStyleProject upstream = j.createFreeStyleProject();
        FreeStyleProject downstream = j.createFreeStyleProject();
        
        upstream.getBuildersList().add(new FileWriteBuilder("artifact.txt", "${CONTENT}"));
        upstream.getPublishersList().add(new ArtifactArchiver("artifact.txt", "", false, false));
        upstream.getPublishersList().add(new BuildTrigger(downstream.getName(), Result.SUCCESS));
        
        downstream.getBuildersList().add(new CopyArtifact(
                upstream.getName(),
                "",
                new TriggeredBuildSelector(false, true),
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
        j.assertBuildStatusSuccess(upstream.scheduleBuild2(0, new Cause.UserCause(), new ParametersAction(new StringParameterValue("CONTENT", "value1"))));
        j.assertBuildStatusSuccess(upstream.scheduleBuild2(0, new Cause.UserCause(), new ParametersAction(new StringParameterValue("CONTENT", "value2"))));
        j.assertBuildStatusSuccess(upstream.scheduleBuild2(0, new Cause.UserCause(), new ParametersAction(new StringParameterValue("CONTENT", "value3"))));
        
        // wait till downstream will be triggered and completed
        j.waitUntilNoActivity();
        
        FreeStyleBuild b = downstream.getLastBuild();
        assertNotNull(b);
        j.assertBuildStatusSuccess(b);
        
        assertEquals(
                String.format("upstream triggers seem not to be merged into one downstream build. This means quietPeriod of downstream is too short in this environment: %s", b.getCauses()),
                3,
                Util.filter(b.getCauses(), Cause.UpstreamCause.class).size()
        );
        
        assertEquals("value3", b.getWorkspace().child("artifact.txt").readToString());
    }
    
    
    @Test
    public void testUseOldestNested() throws Exception {
        FreeStyleProject upstream = j.createFreeStyleProject();
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
        
        downstream.getBuildersList().add(new CopyArtifact(
                upstream.getName(),
                "",
                new TriggeredBuildSelector(false, false),
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
        j.assertBuildStatusSuccess(upstream.scheduleBuild2(0, new Cause.UserCause(), new ParametersAction(new StringParameterValue("CONTENT", "value1"))));
        j.assertBuildStatusSuccess(upstream.scheduleBuild2(0, new Cause.UserCause(), new ParametersAction(new StringParameterValue("CONTENT", "value2"))));
        
        // 2 upstream builds for intermediate2.
        upstream.getPublishersList().clear();
        upstream.getPublishersList().add(new ArtifactArchiver("artifact.txt", "", false, false));
        upstream.getPublishersList().add(new BuildTrigger(intermediate2.getName(), Result.SUCCESS));
        upstream.save();
        j.jenkins.rebuildDependencyGraph();
        j.assertBuildStatusSuccess(upstream.scheduleBuild2(0, new Cause.UserCause(), new ParametersAction(new StringParameterValue("CONTENT", "value3"))));
        j.assertBuildStatusSuccess(upstream.scheduleBuild2(0, new Cause.UserCause(), new ParametersAction(new StringParameterValue("CONTENT", "value4"))));
        
        // wait till downstream will be triggered and completed
        j.waitUntilNoActivity();
        
        assertNotNull(intermediate1.getLastBuild());
        j.assertBuildStatusSuccess(intermediate1.getLastBuild());
        assertEquals(
                String.format("upstream triggers seem not to be merged into one downstream build. This means quietPeriod of intermediate1 is too short in this environment: %s", intermediate1.getLastBuild().getCauses()),
                2,
                Util.filter(intermediate1.getLastBuild().getCauses(), Cause.UpstreamCause.class).size()
        );
        
        assertNotNull(intermediate2.getLastBuild());
        j.assertBuildStatusSuccess(intermediate2.getLastBuild());
        assertEquals(
                String.format("upstream triggers seem not to be merged into one downstream build. This means quietPeriod of intermediate2 is too short in this environment: %s", intermediate2.getLastBuild().getCauses()),
                2,
                Util.filter(intermediate2.getLastBuild().getCauses(), Cause.UpstreamCause.class).size()
        );
        
        FreeStyleBuild b = downstream.getLastBuild();
        assertNotNull(b);
        j.assertBuildStatusSuccess(b);
        
        assertEquals(
                String.format("upstream triggers seem not to be merged into one downstream build. This means quietPeriod of downstream is too short in this environment: %s", b.getCauses()),
                2,
                Util.filter(b.getCauses(), Cause.UpstreamCause.class).size()
        );
        
        assertEquals("value1", b.getWorkspace().child("artifact.txt").readToString());
    }
    
    @Test
    public void testUseNewestNested() throws Exception {
        FreeStyleProject upstream = j.createFreeStyleProject();
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
        
        downstream.getBuildersList().add(new CopyArtifact(
                upstream.getName(),
                "",
                new TriggeredBuildSelector(false, true),
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
        j.assertBuildStatusSuccess(upstream.scheduleBuild2(0, new Cause.UserCause(), new ParametersAction(new StringParameterValue("CONTENT", "value1"))));
        j.assertBuildStatusSuccess(upstream.scheduleBuild2(0, new Cause.UserCause(), new ParametersAction(new StringParameterValue("CONTENT", "value2"))));
        
        // 2 upstream builds for intermediate2.
        upstream.getPublishersList().clear();
        upstream.getPublishersList().add(new ArtifactArchiver("artifact.txt", "", false, false));
        upstream.getPublishersList().add(new BuildTrigger(intermediate2.getName(), Result.SUCCESS));
        upstream.save();
        j.jenkins.rebuildDependencyGraph();
        j.assertBuildStatusSuccess(upstream.scheduleBuild2(0, new Cause.UserCause(), new ParametersAction(new StringParameterValue("CONTENT", "value3"))));
        j.assertBuildStatusSuccess(upstream.scheduleBuild2(0, new Cause.UserCause(), new ParametersAction(new StringParameterValue("CONTENT", "value4"))));
        
        // wait till downstream will be triggered and completed
        j.waitUntilNoActivity();
        
        assertNotNull(intermediate1.getLastBuild());
        j.assertBuildStatusSuccess(intermediate1.getLastBuild());
        assertEquals(
                String.format("upstream triggers seem not to be merged into one downstream build. This means quietPeriod of intermediate1 is too short in this environment: %s", intermediate1.getLastBuild().getCauses()),
                2,
                Util.filter(intermediate1.getLastBuild().getCauses(), Cause.UpstreamCause.class).size()
        );
        
        assertNotNull(intermediate2.getLastBuild());
        j.assertBuildStatusSuccess(intermediate2.getLastBuild());
        assertEquals(
                String.format("upstream triggers seem not to be merged into one downstream build. This means quietPeriod of intermediate2 is too short in this environment: %s", intermediate2.getLastBuild().getCauses()),
                2,
                Util.filter(intermediate2.getLastBuild().getCauses(), Cause.UpstreamCause.class).size()
        );
        
        FreeStyleBuild b = downstream.getLastBuild();
        assertNotNull(b);
        j.assertBuildStatusSuccess(b);
        
        assertEquals(
                String.format("upstream triggers seem not to be merged into one downstream build. This means quietPeriod of downstream is too short in this environment: %s", b.getCauses()),
                2,
                Util.filter(b.getCauses(), Cause.UpstreamCause.class).size()
        );
        
        assertEquals("value4", b.getWorkspace().child("artifact.txt").readToString());
    }
}
