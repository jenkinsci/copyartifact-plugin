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

import org.htmlunit.HttpMethod;
import org.htmlunit.WebClientOptions;
import org.htmlunit.WebRequest;
import org.htmlunit.html.HtmlForm;
import org.htmlunit.util.NameValuePair;
import hudson.cli.CLICommandInvoker;
import hudson.model.FreeStyleProject;
import hudson.model.ParametersDefinitionProperty;

import java.net.URL;
import java.util.Arrays;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.CaptureEnvironmentBuilder;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.JenkinsRule.WebClient;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Test interaction of BuildSelectorParameter with Jenkins core.
 * @author Alan Harder
 */
@WithJenkins
class BuildSelectorParameterTest {

    private JenkinsRule rule;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        this.rule = rule;
    }

    /**
     * Verify BuildSelectorParameter works via HTML form, http POST and CLI.
     */
    @Test
    void testParameter() throws Exception {
        FreeStyleProject job = rule.createFreeStyleProject();
        job.addProperty(new ParametersDefinitionProperty(
                new BuildSelectorParameter("SELECTOR", new StatusBuildSelector(false), "foo")));
        CaptureEnvironmentBuilder ceb = new CaptureEnvironmentBuilder();
        job.getBuildersList().add(ceb);

        // Run via UI (HTML form)
        WebClient wc = rule.createWebClient();
        WebClientOptions wco = wc.getOptions();
        // Jenkins sends 405 response for GET of build page.. deal with that:
        wco.setThrowExceptionOnFailingStatusCode(false);
        wco.setPrintContentOnFailingStatusCode(false);
        HtmlForm form = wc.getPage(job, "build").getFormByName("parameters");
        form.getSelectByName("").getOptionByText("Specific build").setSelected(true);
        wc.waitForBackgroundJavaScript(10000);
        form.getInputByName("_.buildNumber").setValue("6");
        rule.submit(form);
        rule.waitUntilNoActivity();
        assertEquals("<SpecificBuildSelector><buildNumber>6</buildNumber></SpecificBuildSelector>",
                     ceb.getEnvVars().get("SELECTOR").replaceAll("\\s+", ""));
        job.getBuildersList().replace(ceb = new CaptureEnvironmentBuilder());

        // Run via HTTP POST (buildWithParameters)
        WebRequest post = new WebRequest(
                new URL(rule.getURL(), job.getUrl()+"/buildWithParameters"), HttpMethod.POST);
        wc.addCrumb(post);
        String xml = "<StatusBuildSelector><stable>true</stable></StatusBuildSelector>";
        post.setRequestParameters(Arrays.asList(new NameValuePair("SELECTOR", xml),
                                                post.getRequestParameters().get(0)));
        wc.getPage(post);
        rule.waitUntilNoActivity();
        assertEquals(xml, ceb.getEnvVars().get("SELECTOR"));
        job.getBuildersList().replace(ceb = new CaptureEnvironmentBuilder());

        // Run via CLI
        assertThat(new CLICommandInvoker(rule, "build").invokeWithArgs(job.getFullName(), "-p", "SELECTOR=<SavedBuildSelector/>"),
                CLICommandInvoker.Matcher.succeeded());
        rule.waitUntilNoActivity();
        assertEquals("<SavedBuildSelector/>", ceb.getEnvVars().get("SELECTOR"));
    }

    @Test
    void testConfiguration() throws Exception {
        BuildSelectorParameter expected = new BuildSelectorParameter("SELECTOR", new StatusBuildSelector(true), "foo");
        FreeStyleProject job = rule.createFreeStyleProject();
        job.addProperty(new ParametersDefinitionProperty(expected));
        job.save();

        job = rule.configRoundtrip(job);
        BuildSelectorParameter actual = (BuildSelectorParameter)job.getProperty(ParametersDefinitionProperty.class).getParameterDefinition("SELECTOR");
        rule.assertEqualDataBoundBeans(expected, actual);
    }
}
