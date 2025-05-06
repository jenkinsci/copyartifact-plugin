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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.plugins.copyartifact.testutils.FileWriteBuilder;
import hudson.tasks.ArtifactArchiver;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import java.io.File;

@WithJenkins
class LastBuildWithArtifactSelectorTest {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    @Test
    void testNoArtifactInPreviousBuild() throws Exception {
        // configure project
        FreeStyleProject project = j.createFreeStyleProject();
        String artifactString = "artifact.txt";
        project.getBuildersList().add(new FileWriteBuilder(artifactString, "${BUILD_ID}"));
        project.getPublishersList().add(new ArtifactArchiver(artifactString));

        // schedule build with archiving artifacts
        FreeStyleBuild buildWithArtifact = project.scheduleBuild2(0).get();
        assertTrue(buildWithArtifact.getHasArtifacts());

        // remove archiver from project
        project.getPublishersList().removeAll(ArtifactArchiver.class);

        // schedule build without archiving artifacts
        FreeStyleBuild buildWithoutArtifact = project.scheduleBuild2(0).get();
        assertFalse(buildWithoutArtifact.getHasArtifacts());

        // add copy artifact selector and archiver to project
        CopyArtifact copyArtifact = new CopyArtifact(project.getName());
        copyArtifact.setSelector(new LastBuildWithArtifactSelector());
        copyArtifact.setFilter(artifactString);
        project.getBuildersList().add(copyArtifact);
        project.getPublishersList().add(new ArtifactArchiver(artifactString));

        // schedule build that will copy artifacts from the "buildWithArtifact" build
        FreeStyleBuild copyArtifactBuild = project.scheduleBuild2(0).get();

        // confirm the artifact from the "buildWithArtifact" build was copied and archived
        File artifact = new File(copyArtifactBuild.getArtifactsDir(), artifactString);
        assertEquals(buildWithArtifact.getId(), FileUtils.readFileToString(artifact, "UTF-8"));
    }
}
