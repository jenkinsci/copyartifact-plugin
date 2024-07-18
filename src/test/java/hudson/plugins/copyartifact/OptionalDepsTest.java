/*
 * The MIT License
 *
 * Copyright 2024 CloudBees, Inc.
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

import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.RealJenkinsRule;
import org.jvnet.hudson.test.TailLog;

/** Verifies that basic functionality works without optional plugin dependencies. */
public final class OptionalDepsTest {

    @Rule public RealJenkinsRule r = new RealJenkinsRule().omitPlugins("maven-plugin", "matrix-project");

    @Test public void usePipeline() throws Throwable {
        r.then(OptionalDepsTest::_usePipeline);
    }

    /** Adapted from {@link CopyArtifactWorkflowTest#testLastCompletedBuildSelector}. */
    private static void _usePipeline(JenkinsRule r) throws Throwable {
        var upstream = r.createProject(WorkflowJob.class, "upstream");
        upstream.setDefinition(new CpsFlowDefinition("node {writeFile text: 'upstream content', file: 'x.txt'; archiveArtifacts 'x.txt'}", true));
        try (var tail = new TailLog(r, "upstream", 1)) {
            r.buildAndAssertSuccess(upstream);
        }
        var downstream = r.createProject(WorkflowJob.class, "downstream");
        downstream.setDefinition(new CpsFlowDefinition("node {copyArtifacts(projectName: 'upstream', selector: lastCompleted()); echo readFile('x.txt')}", true));
        try (var tail = new TailLog(r, "downstream", 1)) {
            r.assertLogContains("upstream content", r.buildAndAssertSuccess(downstream));
        }
    }

}
