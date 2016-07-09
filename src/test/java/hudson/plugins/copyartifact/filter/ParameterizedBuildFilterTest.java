/*
 * The MIT License
 * 
 * Copyright (c) 2016 IKEDA Yasuyuki
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

package hudson.plugins.copyartifact.filter;

import static org.junit.Assert.*;

import java.util.Arrays;

import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.JenkinsRule.WebClient;

import hudson.model.Cause;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Item;
import hudson.model.ParameterDefinition;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.Result;
import hudson.model.StringParameterDefinition;
import hudson.model.StringParameterValue;
import hudson.plugins.copyartifact.BuildFilterParameter;
import hudson.plugins.copyartifact.CopyArtifact;
import hudson.plugins.copyartifact.ParametersBuildFilter;
import hudson.plugins.copyartifact.operation.CopyArtifactFiles;
import hudson.plugins.copyartifact.testutils.FileWriteBuilder;
import hudson.tasks.ArtifactArchiver;

/**
 * Tests for {@link BuildFilterParameter} and {@link ParameterizedBuildFilter}
 */
public class ParameterizedBuildFilterTest {
    @ClassRule
    public static final JenkinsRule j = new JenkinsRule();

    @Test
    public void testConfigureBuildFilterParameter() throws Exception {
        BuildFilterParameter param = new BuildFilterParameter(
                "PARAM",
                "description",
                new AndBuildFilter(
                        new ParametersBuildFilter("PARAM1=VALUE1"),
                        new SavedBuildFilter()
                )
        );
        FreeStyleProject p = j.createFreeStyleProject();
        p.addProperty(new ParametersDefinitionProperty(
                Arrays.<ParameterDefinition>asList(param)
        ));
        j.configRoundtrip((Item)p);
        j.assertEqualDataBoundBeans(
                param,
                p.getProperty(ParametersDefinitionProperty.class).getParameterDefinition("PARAM")
        );
    }

    @Test
    public void testConfigureParameterizedBuildFilter() throws Exception {
        ParameterizedBuildFilter filter = new ParameterizedBuildFilter("${PARAM}");
        FreeStyleProject p = j.createFreeStyleProject();
        CopyArtifact ca = new CopyArtifact("test");
        ca.setBuildFilter(filter);
        p.getBuildersList().add(ca);
        j.configRoundtrip((Item)p);
        j.assertEqualDataBoundBeans(
                filter,
                p.getBuildersList().get(CopyArtifact.class).getBuildFilter()
        );
    }

    @Test
    public void testIsSelectableWithDefault() throws Exception {
        FreeStyleProject copiee = j.createFreeStyleProject();
        copiee.getBuildersList().add(new FileWriteBuilder("artifact.txt", "${BUILD_ID}"));
        copiee.getPublishersList().add(new ArtifactArchiver("artifact.txt"));
        FreeStyleBuild copieeBuild1 = j.buildAndAssertSuccess(copiee);
        @SuppressWarnings("unused")
        FreeStyleBuild copieeBuild2 = j.buildAndAssertSuccess(copiee);
        copieeBuild1.keepLog();

        FreeStyleProject copier = j.createFreeStyleProject();
        copier.addProperty(new ParametersDefinitionProperty(
                new BuildFilterParameter(
                        "FILTER",
                        "description",
                        new SavedBuildFilter()
                )
        ));
        CopyArtifact ca = new CopyArtifact(copiee.getFullName());
        ca.setVerbose(true);
        CopyArtifactFiles copy = new CopyArtifactFiles();
        copy.setIncludes("artifact.txt");
        ca.setOperation(copy);
        ca.setBuildFilter(new ParameterizedBuildFilter("${FILTER}"));
        copier.getBuildersList().add(ca);

        FreeStyleBuild copierBuild = j.buildAndAssertSuccess(copier);
        assertEquals(
                copieeBuild1.getId(),
                copierBuild.getWorkspace().child("artifact.txt").readToString()
        );
    }

    @Test
    public void testIsSelectableWithParameter() throws Exception {
        FreeStyleProject copiee = j.createFreeStyleProject();
        copiee.getBuildersList().add(new FileWriteBuilder("artifact.txt", "${BUILD_ID}"));
        copiee.getPublishersList().add(new ArtifactArchiver("artifact.txt"));
        FreeStyleBuild copieeBuild1 = j.buildAndAssertSuccess(copiee);
        @SuppressWarnings("unused")
        FreeStyleBuild copieeBuild2 = j.buildAndAssertSuccess(copiee);
        copieeBuild1.keepLog();

        FreeStyleProject copier = j.createFreeStyleProject();
        copier.addProperty(new ParametersDefinitionProperty(
                new BuildFilterParameter(
                        "FILTER",
                        "description",
                        new NoBuildFilter()
                )
        ));
        CopyArtifact ca = new CopyArtifact(copiee.getFullName());
        CopyArtifactFiles copy = new CopyArtifactFiles();
        copy.setIncludes("artifact.txt");
        ca.setOperation(copy);
        ca.setBuildFilter(new ParameterizedBuildFilter("${FILTER}"));
        copier.getBuildersList().add(ca);

        FreeStyleBuild copierBuild = j.assertBuildStatusSuccess(
                copier.scheduleBuild2(
                        0,
                        new Cause.UserIdCause(),
                        new ParametersAction(
                                new StringParameterValue("FILTER", "<SavedBuildFilter />")
                        )
                )
        );
        assertEquals(
                copieeBuild1.getId(),
                copierBuild.getWorkspace().child("artifact.txt").readToString()
        );
    }

    @Test
    public void testIsSelectableWithUI() throws Exception {
        FreeStyleProject copiee = j.createFreeStyleProject();
        copiee.getBuildersList().add(new FileWriteBuilder("artifact.txt", "${BUILD_ID}"));
        copiee.getPublishersList().add(new ArtifactArchiver("artifact.txt"));
        FreeStyleBuild copieeBuild1 = j.buildAndAssertSuccess(copiee);
        @SuppressWarnings("unused")
        FreeStyleBuild copieeBuild2 = j.buildAndAssertSuccess(copiee);
        copieeBuild1.keepLog();

        FreeStyleProject copier = j.createFreeStyleProject();
        copier.addProperty(new ParametersDefinitionProperty(
                new BuildFilterParameter(
                        "FILTER",
                        "description",
                        new SavedBuildFilter()
                )
        ));
        CopyArtifact ca = new CopyArtifact(copiee.getFullName());
        ca.setVerbose(true);
        CopyArtifactFiles copy = new CopyArtifactFiles();
        copy.setIncludes("artifact.txt");
        ca.setOperation(copy);
        ca.setBuildFilter(new ParameterizedBuildFilter("${FILTER}"));
        copier.getBuildersList().add(ca);

        WebClient wc = j.createWebClient();
        // Jenkins sends 405 response for GET of build page.. deal with that:
        wc.getOptions().setThrowExceptionOnFailingStatusCode(false);
        wc.getOptions().setPrintContentOnFailingStatusCode(false);
        j.submit(wc.getPage(copier, "build").getFormByName("parameters"));
        FreeStyleBuild copierBuild = j.buildAndAssertSuccess(copier);
        assertEquals(
                copieeBuild1.getId(),
                copierBuild.getWorkspace().child("artifact.txt").readToString()
        );
    }

    @Test
    public void testIsSelectableBadParameter() throws Exception {
        FreeStyleProject copiee = j.createFreeStyleProject();
        copiee.getBuildersList().add(new FileWriteBuilder("artifact.txt", "${BUILD_ID}"));
        copiee.getPublishersList().add(new ArtifactArchiver("artifact.txt"));
        j.buildAndAssertSuccess(copiee);

        FreeStyleProject copier = j.createFreeStyleProject();
        copier.addProperty(new ParametersDefinitionProperty(
                new BuildFilterParameter(
                        "FILTER",
                        "description",
                        new NoBuildFilter()
                )
        ));
        CopyArtifact ca = new CopyArtifact(copiee.getFullName());
        CopyArtifactFiles copy = new CopyArtifactFiles();
        copy.setIncludes("artifact.txt");
        ca.setOperation(copy);
        ca.setBuildFilter(new ParameterizedBuildFilter("${FILTER}"));
        copier.getBuildersList().add(ca);

        j.assertBuildStatus(
                Result.FAILURE,
                copier.scheduleBuild2(
                        0,
                        new Cause.UserIdCause(),
                        new ParametersAction(
                                new StringParameterValue("FILTER", "Bad Parameter")
                        )
                ).get()
        );
    }

    @Test
    public void testIsSelectableEmptyParameter() throws Exception {
        FreeStyleProject copiee = j.createFreeStyleProject();
        copiee.getBuildersList().add(new FileWriteBuilder("artifact.txt", "${BUILD_ID}"));
        copiee.getPublishersList().add(new ArtifactArchiver("artifact.txt"));
        j.buildAndAssertSuccess(copiee);
        FreeStyleBuild copieeBuild2 = j.buildAndAssertSuccess(copiee);

        FreeStyleProject copier = j.createFreeStyleProject();
        copier.addProperty(new ParametersDefinitionProperty(
                new StringParameterDefinition(
                        "FILTER",
                        "",
                        "description"
                )
        ));
        CopyArtifact ca = new CopyArtifact(copiee.getFullName());
        ca.setVerbose(true);
        CopyArtifactFiles copy = new CopyArtifactFiles();
        copy.setIncludes("artifact.txt");
        ca.setOperation(copy);
        ca.setBuildFilter(new ParameterizedBuildFilter("${FILTER}"));
        copier.getBuildersList().add(ca);

        FreeStyleBuild copierBuild = j.buildAndAssertSuccess(copier);
        assertEquals(
                copieeBuild2.getId(),
                copierBuild.getWorkspace().child("artifact.txt").readToString()
        );
    }
}
