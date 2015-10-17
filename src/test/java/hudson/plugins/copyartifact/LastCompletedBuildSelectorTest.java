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

import java.io.File;

import hudson.model.Build;
import hudson.model.FreeStyleBuild;
import hudson.model.Project;
import hudson.model.Result;
import hudson.model.FreeStyleProject;
import hudson.plugins.copyartifact.testutils.FileWriteBuilder;
import hudson.tasks.ArtifactArchiver;

import org.apache.commons.io.FileUtils;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.FailureBuilder;
import org.jvnet.hudson.test.SleepBuilder;
import org.jvnet.hudson.test.UnstableBuilder;

/**
 *
 */
public class LastCompletedBuildSelectorTest {
    @Rule
    public JenkinsRule j = new JenkinsRule();
    
    @Test
    @Ignore("incompatible since 2.0")
    public void testConfiguration() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        
        p.getBuildersList().add(
                new CopyArtifact(
                        "${PROJECT}",
                        "",
                        new LastCompletedBuildSelector(),
                        "**/*",
                        "",
                        "",
                        false,
                        false,
                        true
                )
        );
        
        p.save();
        
        // Test that the configuration preserved when updated from the web page.
        // This is helpful to find a bug in jelly files.
        j.submit(j.createWebClient().getPage(p, "configure").getFormByName("config"));
        
        p = j.jenkins.getItemByFullName(p.getFullName(), FreeStyleProject.class);
        assertNotNull(p);
        
        CopyArtifact ca = p.getBuildersList().get(CopyArtifact.class);
        assertNotNull(ca);
        
        assertEquals(LastCompletedBuildSelector.class, ca.getBuildSelector().getClass());
    }
    
    private <P extends Project<P,B>, B extends Build<P,B>> B waitForBuildStarts(P project, long timeoutMillis) throws Exception {
        long current = System.currentTimeMillis();
        while(project.getLastBuild() == null || !project.getLastBuild().isBuilding()) {
            assertTrue(System.currentTimeMillis() - current < timeoutMillis);
            Thread.sleep(100);
        }
        B build = project.getLastBuild();
        assertTrue(build.isBuilding());
        assertNotNull(build.getExecutor());
        
        return build;
    }
    
    @Test
    public void testCopyFromBuilds() throws Exception {
        FreeStyleProject upstream = j.createFreeStyleProject();
        FreeStyleProject downstream = j.createFreeStyleProject();
        
        upstream.getBuildersList().add(new FileWriteBuilder("artifact.txt", "${BUILD_ID}"));
        upstream.getPublishersList().add(new ArtifactArchiver(
                "artifact.txt",
                "",
                false,
                false
        ));
        
        CopyArtifact ca = new CopyArtifact(
                upstream.getFullName(),
                "",
                new LastCompletedBuildSelector(),
                "**/*",
                "",
                "",
                false,
                false,
                true
        );
        ca.upgradeFromCopyartifact10();
        downstream.getBuildersList().add(ca);
        downstream.getPublishersList().add(new ArtifactArchiver(
                "artifact.txt",
                "",
                false,
                false
        ));
        
        // Copy from a job without builds
        {
            assertNull(upstream.getLastBuild());
            j.assertBuildStatus(Result.FAILURE, downstream.scheduleBuild2(0).get());
            
        }
        
        // Copy from a job with a succeeded build
        {
            FreeStyleBuild upstreamBuild = upstream.scheduleBuild2(0).get();
            j.assertBuildStatusSuccess(upstreamBuild);
            FreeStyleBuild downstreamBuild = downstream.scheduleBuild2(0).get();
            j.assertBuildStatusSuccess(downstreamBuild);
            
            File artifact = new File(downstreamBuild.getArtifactsDir(), "artifact.txt");
            assertEquals(upstreamBuild.getId(), FileUtils.readFileToString(artifact, "UTF-8"));
        }
        
        // Copy from a job with a unstable build
        {
            upstream.getBuildersList().add(new UnstableBuilder());
            
            FreeStyleBuild upstreamBuild = upstream.scheduleBuild2(0).get();
            j.assertBuildStatus(Result.UNSTABLE, upstreamBuild);
            FreeStyleBuild downstreamBuild = downstream.scheduleBuild2(0).get();
            j.assertBuildStatusSuccess(downstreamBuild);
            
            File artifact = new File(downstreamBuild.getArtifactsDir(), "artifact.txt");
            assertEquals(upstreamBuild.getId(), FileUtils.readFileToString(artifact, "UTF-8"));
            
            upstream.getBuildersList().removeAll(UnstableBuilder.class);
        }
        
        // Copy from a job with a failed build
        {
            upstream.getBuildersList().add(new FailureBuilder());
            
            FreeStyleBuild upstreamBuild = upstream.scheduleBuild2(0).get();
            j.assertBuildStatus(Result.FAILURE, upstreamBuild);
            FreeStyleBuild downstreamBuild = downstream.scheduleBuild2(0).get();
            j.assertBuildStatusSuccess(downstreamBuild);
            
            File artifact = new File(downstreamBuild.getArtifactsDir(), "artifact.txt");
            assertEquals(upstreamBuild.getId(), FileUtils.readFileToString(artifact, "UTF-8"));
            
            upstream.getBuildersList().removeAll(FailureBuilder.class);
        }
        
        // Copy from a job with an aborted build
        {
            upstream.getBuildersList().add(new SleepBuilder(60000));
            
            upstream.scheduleBuild2(0);
            FreeStyleBuild upstreamBuild = waitForBuildStarts(upstream, 5000);
            upstreamBuild.getExecutor().interrupt();
            j.waitUntilNoActivity();
            j.assertBuildStatus(Result.ABORTED, upstreamBuild);
            FreeStyleBuild downstreamBuild = downstream.scheduleBuild2(0).get();
            j.assertBuildStatusSuccess(downstreamBuild);
            
            File artifact = new File(downstreamBuild.getArtifactsDir(), "artifact.txt");
            assertEquals(upstreamBuild.getId(), FileUtils.readFileToString(artifact, "UTF-8"));
            
            upstream.getBuildersList().removeAll(SleepBuilder.class);
        }
    }
    
    @Test
    public void testWithPermalinkBuildSelector() throws Exception {
        FreeStyleProject upstream = j.createFreeStyleProject();
        
        FreeStyleProject downstreamLastCompleted = j.createFreeStyleProject();
        FreeStyleProject downstreamLast = j.createFreeStyleProject();
        
        upstream.getBuildersList().add(new FileWriteBuilder("artifact.txt", "${BUILD_ID}"));
        upstream.getPublishersList().add(new ArtifactArchiver(
                "artifact.txt",
                "",
                false,
                false
        ));
        
        CopyArtifact ca = new CopyArtifact(
                upstream.getFullName(),
                "",
                new LastCompletedBuildSelector(),
                "**/*",
                "",
                "",
                false,
                false,
                true
        );
        ca.upgradeFromCopyartifact10();
        downstreamLastCompleted.getBuildersList().add(ca);
        downstreamLastCompleted.getPublishersList().add(new ArtifactArchiver(
                "artifact.txt",
                "",
                false,
                false
        ));
        
        
        downstreamLast.getBuildersList().add(new CopyArtifact(
                upstream.getFullName(),
                "",
                new PermalinkBuildSelector("lastBuild"),
                "**/*",
                "",
                "",
                false,
                false,
                true
        ));
        downstreamLast.getPublishersList().add(new ArtifactArchiver(
                "artifact.txt",
                "",
                false,
                false
        ));
        
        FreeStyleBuild upstreamCompletedBuild = upstream.scheduleBuild2(0).get();
        upstream.getBuildersList().add(new SleepBuilder(60000));
        upstream.scheduleBuild2(0);
        FreeStyleBuild upstreamIncompletedBuild = waitForBuildStarts(upstream, 5000);
        
        FreeStyleBuild downstreamLastCompletedBuild = downstreamLastCompleted.scheduleBuild2(0).get();
        FreeStyleBuild downstreamLastBuild = downstreamLast.scheduleBuild2(0).get();
        
        assertTrue(upstreamIncompletedBuild.isBuilding());
        upstreamIncompletedBuild.getExecutor().interrupt();
        
        j.assertBuildStatusSuccess(downstreamLastCompletedBuild);
        j.assertBuildStatus(Result.FAILURE, downstreamLastBuild);   // this fails as upstream have no artifact.
        
        File artifact = new File(downstreamLastCompletedBuild.getArtifactsDir(), "artifact.txt");
        assertEquals(upstreamCompletedBuild.getId(), FileUtils.readFileToString(artifact, "UTF-8"));
    }
}
