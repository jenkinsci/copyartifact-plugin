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

import static org.junit.jupiter.api.Assertions.assertNotNull;

import hudson.model.FreeStyleProject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.jvnet.hudson.test.recipes.LocalData;
import org.jvnet.hudson.test.recipes.WithPlugin;

@WithJenkins
class SimpleBuildSelectorDescriptorTest {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    @Issue("JENKINS-28972")
    @LocalData
    @WithPlugin("copyartifact-extension-test.hpi")
    // JENKINS-28792 reproduces only when classes are located in different class loaders.
    @Test
    void testSimpleBuildSelectorDescriptorInOtherPlugin() throws Exception {
        JenkinsRule.WebClient wc = j.createWebClient();

        // An extension using SimpleBuildSelectorDescriptorSelector
        {
            FreeStyleProject p = j.jenkins.getItemByFullName("UsingSimpleBuildSelectorDescriptorSelector", FreeStyleProject.class);
            assertNotNull(p);
            wc.getPage(p, "configure");
        }

        // An extension using SimpleBuildSelectorDescriptorSelector without configuration pages.
        {
            FreeStyleProject p = j.jenkins.getItemByFullName("NoConfigPageSimpleBuildSelectorDescriptorSelector", FreeStyleProject.class);
            assertNotNull(p);
            wc.getPage(p, "configure");
        }

        // An extension extending SimpleBuildSelectorDescriptorSelector.
        // (Even though generally it is useless)
        {
            FreeStyleProject p = j.jenkins.getItemByFullName("ExtendingSimpleBuildSelectorDescriptorSelector", FreeStyleProject.class);
            assertNotNull(p);
            wc.getPage(p, "configure");
        }
    }
}
