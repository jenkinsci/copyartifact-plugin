package hudson.plugins.copyartifact;

import hudson.EnvVars;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

import static org.junit.Assert.*;

public class SpecificBuildSelectorTest {
    @Rule
    public final JenkinsRule rule = new JenkinsRule();

    @Issue("JENKINS-14266")
    @Test
    public void testUnsetVar() throws Exception {
        FreeStyleProject p = rule.createFreeStyleProject();
        rule.assertBuildStatusSuccess(p.scheduleBuild2(0));
        rule.assertBuildStatusSuccess(p.scheduleBuild2(0));
        rule.assertBuildStatusSuccess(p.scheduleBuild2(0));
        assertEquals(3, p.getLastBuild().number);
        BuildSelector s = new SpecificBuildSelector("$NUM");
        BuildFilter f = new BuildFilter();
        assertEquals(p.getBuildByNumber(2), s.getBuild(p, new EnvVars("NUM", "2"), f, null));
        assertEquals(null, s.getBuild(p, new EnvVars("HUM", "two"), f, null));
    }

    @Issue("JENKINS-19693")
    @Test
    public void testDisplayName() throws Exception {
        FreeStyleProject p = rule.createFreeStyleProject();
        rule.assertBuildStatusSuccess(p.scheduleBuild2(0));
        rule.assertBuildStatusSuccess(p.scheduleBuild2(0));
        rule.assertBuildStatusSuccess(p.scheduleBuild2(0));
        assertEquals(3, p.getLastBuild().number);
        p.getBuildByNumber(2).setDisplayName("RC1");
        BuildSelector s = new SpecificBuildSelector("$NUM");
        BuildFilter f = new BuildFilter();
        assertEquals(p.getBuildByNumber(2), s.getBuild(p, new EnvVars("NUM", "RC1"), f, null));
        assertEquals(null, s.getBuild(p, new EnvVars("NUM", "RC2"), f, null));
    }

    @Test
    public void testPermalink() throws Exception {
        FreeStyleProject p = rule.createFreeStyleProject();
        rule.assertBuildStatusSuccess(p.scheduleBuild2(0));
        rule.assertBuildStatusSuccess(p.scheduleBuild2(0));
        rule.assertBuildStatusSuccess(p.scheduleBuild2(0));
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

    @Issue("JENKINS-47074")
    @Test
    public void testBuildId() throws Exception {
        FreeStyleProject p = rule.createFreeStyleProject();
        FreeStyleBuild b1 = rule.assertBuildStatusSuccess(p.scheduleBuild2(0));
        FreeStyleBuild b2 = rule.assertBuildStatusSuccess(p.scheduleBuild2(0));

        // Build ID precedes to display names.
        b1.setDisplayName(b2.getId());
        b2.setDisplayName(b1.getId());

        assertEquals(b1, new SpecificBuildSelector(b1.getId()).getBuild(p, new EnvVars(), new BuildFilter(), null));
        assertEquals(b2, new SpecificBuildSelector(b2.getId()).getBuild(p, new EnvVars(), new BuildFilter(), null));
    }
}
