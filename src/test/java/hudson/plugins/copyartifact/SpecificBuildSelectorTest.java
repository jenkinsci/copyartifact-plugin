package hudson.plugins.copyartifact;

import hudson.EnvVars;
import hudson.model.Cause;
import hudson.model.FreeStyleProject;
import hudson.model.ParametersAction;
import hudson.model.Run;
import hudson.model.StringParameterValue;
import hudson.plugins.copyartifact.testutils.CopyArtifactUtil;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.CaptureEnvironmentBuilder;
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
        
        FreeStyleProject copier = rule.createFreeStyleProject();
        copier.getBuildersList().add(CopyArtifactUtil.createCopyArtifact(
                p.getFullDisplayName(),
                "",     // parameter
                new SpecificBuildSelector("$NUM"),
                "**/*", // filter
                "",     // excludes
                "",     // target
                false,  // flatten
                true,   // optional
                false,  // fingerprintArtifacts
                "RESULT"// resultVariableSuffix
        ));
        CaptureEnvironmentBuilder ceb = new CaptureEnvironmentBuilder();
        copier.getBuildersList().add(ceb);
        
        rule.assertBuildStatusSuccess(copier.scheduleBuild2(
                0,
                new Cause.UserIdCause(),
                new ParametersAction(
                        new StringParameterValue("NUM", "2")
                )
        ));
        assertEquals("2", ceb.getEnvVars().get("COPYARTIFACT_BUILD_NUMBER_RESULT"));
        rule.assertBuildStatusSuccess(copier.scheduleBuild2(
                0,
                new Cause.UserIdCause(),
                new ParametersAction(
                        new StringParameterValue("HUM", "two")
                )
        ));
        assertEquals(null, ceb.getEnvVars().get("COPYARTIFACT_BUILD_NUMBER_RESULT"));
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
        
        FreeStyleProject copier = rule.createFreeStyleProject();
        copier.getBuildersList().add(CopyArtifactUtil.createCopyArtifact(
                p.getFullDisplayName(),
                "",     // parameter
                new SpecificBuildSelector("$NUM"),
                "**/*", // filter
                "",     // excludes
                "",     // target
                false,  // flatten
                true,   // optional
                false,  // fingerprintArtifacts
                "RESULT"// resultVariableSuffix
        ));
        CaptureEnvironmentBuilder ceb = new CaptureEnvironmentBuilder();
        copier.getBuildersList().add(ceb);
        
        rule.assertBuildStatusSuccess(copier.scheduleBuild2(
                0,
                new Cause.UserIdCause(),
                new ParametersAction(
                        new StringParameterValue("NUM", "RC1")
                )
        ));
        assertEquals("2", ceb.getEnvVars().get("COPYARTIFACT_BUILD_NUMBER_RESULT"));
        rule.assertBuildStatusSuccess(copier.scheduleBuild2(
                0,
                new Cause.UserIdCause(),
                new ParametersAction(
                        new StringParameterValue("NUM", "RC2")
                )
        ));
        assertEquals(null, ceb.getEnvVars().get("COPYARTIFACT_BUILD_NUMBER_RESULT"));
    }

    private String getBuildNumberOf(Run<?,?> r) {
        if (r == null) {
            return null;
        }
        return Integer.toString(r.getNumber());
    }

    @Test
    public void testPermalink() throws Exception {
        FreeStyleProject p = rule.createFreeStyleProject();
        rule.assertBuildStatusSuccess(p.scheduleBuild2(0));
        rule.assertBuildStatusSuccess(p.scheduleBuild2(0));
        rule.assertBuildStatusSuccess(p.scheduleBuild2(0));
        assertEquals(3, p.getLastBuild().number);
        
        FreeStyleProject copier = rule.createFreeStyleProject();
        copier.getBuildersList().add(CopyArtifactUtil.createCopyArtifact(
                p.getFullDisplayName(),
                "",     // parameter
                new SpecificBuildSelector("$NUM"),
                "**/*", // filter
                "",     // excludes
                "",     // target
                false,  // flatten
                true,   // optional
                false,  // fingerprintArtifacts
                "RESULT"// resultVariableSuffix
        ));
        CaptureEnvironmentBuilder ceb = new CaptureEnvironmentBuilder();
        copier.getBuildersList().add(ceb);
        
        rule.assertBuildStatusSuccess(copier.scheduleBuild2(
                0,
                new Cause.UserIdCause(),
                new ParametersAction(
                        new StringParameterValue("NUM", "lastSuccessfulBuild")
                )
        ));
        assertEquals(getBuildNumberOf(p.getLastSuccessfulBuild()), ceb.getEnvVars().get("COPYARTIFACT_BUILD_NUMBER_RESULT"));
        
        rule.assertBuildStatusSuccess(copier.scheduleBuild2(
                0,
                new Cause.UserIdCause(),
                new ParametersAction(
                        new StringParameterValue("NUM", "lastStableBuild")
                )
        ));
        assertEquals(getBuildNumberOf(p.getLastStableBuild()), ceb.getEnvVars().get("COPYARTIFACT_BUILD_NUMBER_RESULT"));
        
        rule.assertBuildStatusSuccess(copier.scheduleBuild2(
                0,
                new Cause.UserIdCause(),
                new ParametersAction(
                        new StringParameterValue("NUM", "lastBuild")
                )
        ));
        assertEquals(getBuildNumberOf(p.getLastBuild()), ceb.getEnvVars().get("COPYARTIFACT_BUILD_NUMBER_RESULT"));
        
        rule.assertBuildStatusSuccess(copier.scheduleBuild2(
                0,
                new Cause.UserIdCause(),
                new ParametersAction(
                        new StringParameterValue("NUM", "lastFailedBuild")
                )
        ));
        assertEquals(getBuildNumberOf(p.getLastFailedBuild()), ceb.getEnvVars().get("COPYARTIFACT_BUILD_NUMBER_RESULT"));
        
        rule.assertBuildStatusSuccess(copier.scheduleBuild2(
                0,
                new Cause.UserIdCause(),
                new ParametersAction(
                        new StringParameterValue("NUM", "lastUnstableBuild")
                )
        ));
        assertEquals(getBuildNumberOf(p.getLastUnstableBuild()), ceb.getEnvVars().get("COPYARTIFACT_BUILD_NUMBER_RESULT"));
        
        rule.assertBuildStatusSuccess(copier.scheduleBuild2(
                0,
                new Cause.UserIdCause(),
                new ParametersAction(
                        new StringParameterValue("NUM", "lastUnsuccessfulBuild")
                )
        ));
        assertEquals(getBuildNumberOf(p.getLastUnsuccessfulBuild()), ceb.getEnvVars().get("COPYARTIFACT_BUILD_NUMBER_RESULT"));
    }

}
