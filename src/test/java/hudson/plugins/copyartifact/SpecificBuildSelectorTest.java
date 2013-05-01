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

}
