package hudson.plugins.copyartifact;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;

import hudson.FilePath;
import hudson.model.Cause.UserCause;
import hudson.model.FreeStyleProject;
import hudson.model.FreeStyleBuild;
import hudson.model.Result;
import hudson.plugins.copyartifact.testutils.FileWriteBuilder;
import hudson.tasks.ArtifactArchiver;

import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import com.tikal.jenkins.plugins.multijob.MultiJobBuild;
import com.tikal.jenkins.plugins.multijob.MultiJobBuilder;
import com.tikal.jenkins.plugins.multijob.MultiJobBuilder.ContinuationCondition;
import com.tikal.jenkins.plugins.multijob.MultiJobProject;
import com.tikal.jenkins.plugins.multijob.PhaseJobsConfig;
import com.tikal.jenkins.plugins.multijob.PhaseJobsConfig.KillPhaseOnJobResultCondition;

/**
 * Test interaction of MultiJobBuildSelector with Jenkins Core & MultiJob Plugin.
 * @author Ray Sennewald
 */
public class MultiJobBuildSelectorTest {
    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void testConfiguration() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();

        p.getBuildersList().add(
                new CopyArtifact(
                        "${PROJECT}",
                        "",
                        new MultiJobBuildSelector(),
                        "**/*",
                        "",
                        "",
                        false,
                        false,
                        true
                )
        );

        p.save();

        // Test that configuration persists when we load page.
        j.submit(j.createWebClient().getPage(p, "configure").getFormByName("config"));

        p = j.jenkins.getItemByFullName(p.getFullName(), FreeStyleProject.class);
        assertNotNull(p);

        CopyArtifact ca = p.getBuildersList().get(CopyArtifact.class);
        assertNotNull(ca);

        assertEquals(MultiJobBuildSelector.class, ca.getBuildSelector().getClass());
    }

    private FreeStyleProject setupArtifactSourcePhaseJob(String name) throws Exception {
        FreeStyleProject phaseJob = j.jenkins.createProject(FreeStyleProject.class, name);
        phaseJob.getBuildersList().add(new FileWriteBuilder("artifact.txt", "${BUILD_ID}"));
        phaseJob.getPublishersList().add(new ArtifactArchiver(
                "artifact.txt",
                "",
                false,
                false
        ));
        phaseJob.save();
        return phaseJob;
    }

    @Test
    public void testCopyFromPhaseJobToMultiJob() throws Exception {
        // Create Phase Job
        FreeStyleProject phaseJob = setupArtifactSourcePhaseJob("phaseJob");
        // Create MultiJob
        // multiJobProject
        //  |_ phase
        //      |_ phaseJob
        //
        MultiJobProject multiJobProject = j.jenkins.createProject(MultiJobProject.class, "multi");
        multiJobProject.setConcurrentBuild(true);
        // Create Phase containing Job named 'phaseJob'
        PhaseJobsConfig phase = new PhaseJobsConfig("phaseJob", null, true, null, KillPhaseOnJobResultCondition.NEVER, false);
        List<PhaseJobsConfig> configTopList = new ArrayList<PhaseJobsConfig>();
        configTopList.add(phase);
        MultiJobBuilder phaseBuilder = new MultiJobBuilder("Phase", configTopList, ContinuationCondition.SUCCESSFUL);
        multiJobProject.getBuildersList().add(phaseBuilder);
        // Copy Artifact from 'phaseJob'
        MultiJobBuildSelector mjbSelector = new MultiJobBuildSelector();
        multiJobProject.getBuildersList().add(new CopyArtifact(
                phaseJob.getFullName(),
                "",
                mjbSelector,
                "**/*",
                "",
                "",
                false,
                false,
                true
        ));
        multiJobProject.save();
        // Trigger MultiJob
        MultiJobBuild b = multiJobProject.scheduleBuild2(0, new UserCause()).get();
        j.waitUntilNoActivity();
        // Assert it did what we wanted
        j.assertBuildStatus(Result.SUCCESS, b);
        FilePath artifact = b.getWorkspace().child("artifact.txt");
        assertTrue(artifact.exists());
        assertEquals(phaseJob.getLastBuild().getId(), artifact.readToString());
        // Ensure that the phaseJobBuild which occurred in the MultiJobBuild above is no longer the
        //  lastBuild if we trigger a new independent build for phaseJob1
        FreeStyleBuild expectedPhaseBuild = phaseJob.getLastBuild();
        FreeStyleBuild lastBuild = phaseJob.scheduleBuild2(0, new UserCause()).get();
        j.waitUntilNoActivity();
        j.assertBuildStatus(Result.SUCCESS, lastBuild);
        assertNotEquals(expectedPhaseBuild, lastBuild);
        // Ensure that if we call getBuild on the mjbSelector it still returns the build
        // that was part of the current MultiJobBuild and not the latest Build we just triggered.
        FreeStyleBuild returnedPhaseBuild = (FreeStyleBuild)mjbSelector.getBuild(phaseJob, null, null, b);
        assertEquals(returnedPhaseBuild, expectedPhaseBuild);
    }

    @Test
    public void testCopyFromPhaseJobToPhaseJob() throws Exception {
        // Create MultiJob
        // multiJobProject
        //  |_ phase 1
        //      |_ phaseJob1
        //  |_ phase 2
        //      |_ phaseJob2
        //
        MultiJobProject multiJobProject = j.jenkins.createProject(MultiJobProject.class, "multi");
        // Create PhaseJob1
        FreeStyleProject phaseJob1 = setupArtifactSourcePhaseJob("phaseJob1");
        // Create Phase1 containing Job named 'phaseJob1'
        PhaseJobsConfig phase1 = new PhaseJobsConfig("phaseJob1", null, true, null, KillPhaseOnJobResultCondition.NEVER, false);
        List<PhaseJobsConfig> configTopList = new ArrayList<PhaseJobsConfig>();
        configTopList.add(phase1);
        MultiJobBuilder phaseBuilder1 = new MultiJobBuilder("Phase1", configTopList, ContinuationCondition.SUCCESSFUL);
        multiJobProject.getBuildersList().add(phaseBuilder1);
        // Create PhaseJob2
        FreeStyleProject phaseJob2 = j.jenkins.createProject(FreeStyleProject.class, "phaseJob2");
        // Copy Artifact from 'phaseJob1'
        MultiJobBuildSelector mjbSelector = new MultiJobBuildSelector();
        phaseJob2.getBuildersList().add(new CopyArtifact(
                phaseJob1.getFullName(),
                "",
                mjbSelector,
                "**/*",
                "",
                "",
                false,
                false,
                true
        ));
        phaseJob2.save();
        // Create Phase2 containing Job named 'phaseJob2'
        PhaseJobsConfig phase2 = new PhaseJobsConfig("phaseJob2", null, true, null, KillPhaseOnJobResultCondition.NEVER, false);
        List<PhaseJobsConfig> configTopList2 = new ArrayList<PhaseJobsConfig>();
        configTopList.add(phase2);
        MultiJobBuilder phaseBuilder2 = new MultiJobBuilder("Phase2", configTopList2, ContinuationCondition.SUCCESSFUL);
        multiJobProject.getBuildersList().add(phaseBuilder2);
        multiJobProject.save();
        // Trigger MultiJob
        multiJobProject.scheduleBuild2(0, new UserCause()).get();
        FreeStyleBuild b = phaseJob2.getLastBuild();
        j.waitUntilNoActivity();
        // Assert it did what we wanted
        j.assertBuildStatus(Result.SUCCESS, b);
        FilePath artifact = b.getWorkspace().child("artifact.txt");
        assertTrue(artifact.exists());
        assertEquals(phaseJob1.getLastBuild().getId(), artifact.readToString());
        // Ensure that the phaseJobBuild1 which occurred in the MultiJobBuild above is no longer the
        //  lastBuild if we trigger a new independent build for phaseJob1
        FreeStyleBuild expectedPhaseBuild = phaseJob1.getLastBuild();
        FreeStyleBuild lastBuild = phaseJob1.scheduleBuild2(0, new UserCause()).get();
        j.waitUntilNoActivity();
        j.assertBuildStatus(Result.SUCCESS, lastBuild);
        assertNotEquals(expectedPhaseBuild, lastBuild);
        // Ensure that if we call getBuild on the mjbSelector it still returns the build
        // that was part of the current MultiJobBuild and not the latest Build we just triggered.
        FreeStyleBuild returnedPhaseBuild = (FreeStyleBuild)mjbSelector.getBuild(phaseJob1, null, null, b);
        assertEquals(returnedPhaseBuild, expectedPhaseBuild);
    }
}
