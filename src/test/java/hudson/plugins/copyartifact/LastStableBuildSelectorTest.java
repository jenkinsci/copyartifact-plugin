/*
 * The MIT License
 *
 * Copyright (c) 2019, Chad Gilman
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

import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

public class LastStableBuildSelectorTest {

    @Rule
    public final JenkinsRule rule = new JenkinsRule();

    @Test
    public void testNullSettingSelectorNullFallsBackToDefaultAkaStableSelector() throws Exception {
        CopyArtifact copyArtifact = new CopyArtifact("dummy");
        copyArtifact.setSelector(null);
        BuildSelector s = copyArtifact.getSelector();
        org.junit.Assert.assertNotNull(s);
        org.junit.Assert.assertTrue(s instanceof StatusBuildSelector);
        org.junit.Assert.assertTrue(((StatusBuildSelector) s).isStable());
    }
}
