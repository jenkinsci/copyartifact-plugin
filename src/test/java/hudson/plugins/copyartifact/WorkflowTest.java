package hudson.plugins.copyartifact;

import org.jenkinsci.plugins.workflow.steps.CoreStep;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepConfigTester;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public class WorkflowTest {

    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Test
    public void snippetGeneration() throws Exception {
        r.createFreeStyleProject("foo");
        Step step = new CoreStep(new CopyArtifact("foo", "a", new LastCompletedBuildSelector(), "b", "c", "d", true, true, false));
        Step roudtrip = new StepConfigTester(r).configRoundTrip(step);
        r.assertEqualDataBoundBeans(step, roudtrip);
    }
}
