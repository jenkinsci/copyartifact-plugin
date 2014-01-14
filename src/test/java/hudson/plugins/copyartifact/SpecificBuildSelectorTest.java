package hudson.plugins.copyartifact;

import hudson.EnvVars;
import hudson.model.FreeStyleProject;
import org.jvnet.hudson.test.Bug;
import org.jvnet.hudson.test.HudsonTestCase;

public class SpecificBuildSelectorTest extends HudsonTestCase {

    @Bug(14266)
    public void testUnsetVar() throws Exception {
        FreeStyleProject p = createFreeStyleProject();
        assertBuildStatusSuccess(p.scheduleBuild2(0));
        assertBuildStatusSuccess(p.scheduleBuild2(0));
        assertBuildStatusSuccess(p.scheduleBuild2(0));
        assertEquals(3, p.getLastBuild().number);
        BuildSelector s = new SpecificBuildSelector("$NUM");
        BuildFilter f = new BuildFilter();
        assertEquals(p.getBuildByNumber(2), s.getBuild(p, new EnvVars("NUM", "2"), f, null));
        assertEquals(null, s.getBuild(p, new EnvVars("HUM", "two"), f, null));
    }

    @Bug(19693)
    public void testDisplayName() throws Exception {
        FreeStyleProject p = createFreeStyleProject();
        assertBuildStatusSuccess(p.scheduleBuild2(0));
        assertBuildStatusSuccess(p.scheduleBuild2(0));
        assertBuildStatusSuccess(p.scheduleBuild2(0));
        assertEquals(3, p.getLastBuild().number);
        p.getBuildByNumber(2).setDisplayName("RC1");
        BuildSelector s = new SpecificBuildSelector("$NUM");
        BuildFilter f = new BuildFilter();
        assertEquals(p.getBuildByNumber(2), s.getBuild(p, new EnvVars("NUM", "RC1"), f, null));
        assertEquals(null, s.getBuild(p, new EnvVars("NUM", "RC2"), f, null));
    }

    public void testPermalink() throws Exception {
        FreeStyleProject p = createFreeStyleProject();
        assertBuildStatusSuccess(p.scheduleBuild2(0));
        assertBuildStatusSuccess(p.scheduleBuild2(0));
        assertBuildStatusSuccess(p.scheduleBuild2(0));
        assertEquals(3, p.getLastBuild().number);
        BuildSelector s = new SpecificBuildSelector("$NUM");
        BuildFilter f = new BuildFilter();
        assertEquals(p.getLastSuccessfulBuild(), s.getBuild(p, new EnvVars("NUM", "lastSuccessfulBuild"), f, null));
        assertEquals(p.getLastStableBuild(), s.getBuild(p, new EnvVars("NUM", "lastStableBuild"), f, null));
        assertEquals(p.getLastBuild(), s.getBuild(p, new EnvVars("NUM", "lastBuild"), f, null));
        assertEquals(p.getLastFailedBuild(), s.getBuild(p, new EnvVars("NUM", "lastFailedBuild"), f, null));
        assertEquals(p.getLastUnstableBuild(), s.getBuild(p, new EnvVars("NUM", "lastUnstableBuild"), f, null));
        assertEquals(p.getLastUnsuccessfulBuild(), s.getBuild(p, new EnvVars("NUM", "lastUnsuccessfulBuild"), f, null));
    }

}
