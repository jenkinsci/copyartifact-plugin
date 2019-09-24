/*
 * The MIT License
 *
 * Copyright (c) 2004-2011, Sun Microsystems, Inc., Alan Harder
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

import hudson.model.ParameterDefinition;
import hudson.model.ParametersDefinitionProperty;
import hudson.plugins.copyartifact.testutils.CopyArtifactJenkinsRule;
import org.hamcrest.Matchers;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.Rule;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

/**
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 */
public class BuildSelectorParameterWorkflowTest {

    @Rule
    public CopyArtifactJenkinsRule j = new CopyArtifactJenkinsRule();

    // Tests for symbols.
    // Test only that symbols are accessible as expected.
    // No tests for features here as they should be tested in other tests.

    @Test
    public void testDownstreamBuildSelector() throws Exception {
        WorkflowJob pipeline = j.createWorkflow(
                "pipeline",
                "properties([parameters([buildSelector(name: 'foo', description: 'description', defaultSelector: lastCompleted())])])"
        );
        j.assertBuildStatusSuccess(pipeline.scheduleBuild2(0));
        assertTrue(pipeline.isParameterized());
        ParametersDefinitionProperty property = pipeline.getProperty(ParametersDefinitionProperty.class);
        ParameterDefinition parameter = property.getParameterDefinition("foo");
        assertThat(parameter, Matchers.instanceOf(BuildSelectorParameter.class));
        BuildSelectorParameter buildSelectorParameter = (BuildSelectorParameter) parameter;
        assertEquals("description", buildSelectorParameter.getDescription());
        assertEquals(LastCompletedBuildSelector.class, buildSelectorParameter.getDefaultSelector().getClass());
    }

}
