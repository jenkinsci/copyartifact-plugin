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

import com.gargoylesoftware.htmlunit.HttpMethod;
import com.gargoylesoftware.htmlunit.WebClientOptions;
import com.gargoylesoftware.htmlunit.WebRequest;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.util.NameValuePair;
import hudson.cli.CLI;
import hudson.model.FreeStyleProject;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.Queue;
import java.net.URL;
import java.util.Arrays;

import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.CaptureEnvironmentBuilder;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.JenkinsRule.WebClient;

import static org.junit.Assert.*;

/**
 * Test interaction of BuildSelectorParameter with Jenkins core.
 * @author Alan Harder
 */
public class BuildSelectorParameterTest {
    @Rule
    public final JenkinsRule rule = new JenkinsRule();

    /**
     * Verify BuildSelectorParameter works via HTML form, http POST and CLI.
     */
    @Test
    public void testParameter() throws Exception {
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
        form.getInputByName("_.buildNumber").setValueAttribute("6");
        rule.submit(form);
        Queue.Item q = rule.jenkins.getQueue().getItem(job);
        if (q != null) q.getFuture().get();
        while (job.getLastBuild().isBuilding()) Thread.sleep(100);
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
        q = rule.jenkins.getQueue().getItem(job);
        if (q != null) q.getFuture().get();
        while (job.getLastBuild().isBuilding()) Thread.sleep(100);
        assertEquals(xml, ceb.getEnvVars().get("SELECTOR"));
        job.getBuildersList().replace(ceb = new CaptureEnvironmentBuilder());

        // Run via CLI
        CLI cli = new CLI(rule.getURL());
        assertEquals(0, cli.execute(
                "build", job.getFullName(), "-p", "SELECTOR=<SavedBuildSelector/>"));
        q = rule.jenkins.getQueue().getItem(job);
        if (q != null) q.getFuture().get();
        while (job.getLastBuild().isBuilding()) Thread.sleep(100);
        assertEquals("<SavedBuildSelector/>", ceb.getEnvVars().get("SELECTOR"));
    }
    
    @Test
    public void testConfiguration() throws Exception {
        BuildSelectorParameter expected = new BuildSelectorParameter("SELECTOR", new StatusBuildSelector(true), "foo");
        FreeStyleProject job = rule.createFreeStyleProject();
        job.addProperty(new ParametersDefinitionProperty(expected));
        job.save();
        
        job = rule.configRoundtrip(job);
        BuildSelectorParameter actual = (BuildSelectorParameter)job.getProperty(ParametersDefinitionProperty.class).getParameterDefinition("SELECTOR");
        rule.assertEqualDataBoundBeans(expected, actual);
    }
}
