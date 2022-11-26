/*
 * The MIT License
 * 
 * Copyright (c) 2014 IKEDA Yasuyuki
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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

import hudson.model.Cause;
import hudson.model.FreeStyleProject;
import hudson.model.FreeStyleBuild;
import hudson.model.Item;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.StringParameterDefinition;
import hudson.model.StringParameterValue;
import hudson.model.User;
import hudson.model.Result;
import hudson.plugins.copyartifact.testutils.CopyArtifactUtil;
import jenkins.model.Jenkins;
import hudson.FilePath;
import hudson.plugins.copyartifact.testutils.FileWriteBuilder;
import hudson.tasks.ArtifactArchiver;
import hudson.tasks.BuildTrigger;
import hudson.tasks.Fingerprinter;
import hudson.util.FormValidation;

import org.acegisecurity.Authentication;
import org.acegisecurity.context.SecurityContextHolder;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.MockFolder;

/**
 *
 */
public class DownstreamBuildSelectorTest {
    @Rule
    public JenkinsRule j = new JenkinsRule();
    
    @Test
    public void testConfiguration() throws Exception {
        final String UPSTREAM_PROJECT_NAME = "${UPSTREAM_PROJECT_NAME}";
        final String UPSTREAM_BUILD_NUMBER = "${UPSTREAM_BUILD_NUMBER}";
        
        FreeStyleProject p = j.createFreeStyleProject();
        
        p.getBuildersList().add(
                CopyArtifactUtil.createCopyArtifact(
                        "${PROJECT}",
                        "",
                        new DownstreamBuildSelector(
                                UPSTREAM_PROJECT_NAME,
                                UPSTREAM_BUILD_NUMBER
                        ),
                        "**/*",
                        "",
                        "",
                        false,
                        false,
                        true
                )
        );
        
        p.save();
        
        // Test that the configuration preserved when updated from the web page.
        // This is helpful to find a bug in jelly files.
        j.submit(j.createWebClient().getPage(p, "configure").getFormByName("config"));
        
        p = j.jenkins.getItemByFullName(p.getFullName(), FreeStyleProject.class);
        assertNotNull(p);
        
        CopyArtifact ca = p.getBuildersList().get(CopyArtifact.class);
        assertNotNull(ca);
        
        assertEquals(DownstreamBuildSelector.class, ca.getBuildSelector().getClass());
        
        DownstreamBuildSelector selector = (DownstreamBuildSelector)ca.getBuildSelector();
        assertEquals(UPSTREAM_PROJECT_NAME, selector.getUpstreamProjectName());
        assertEquals(UPSTREAM_BUILD_NUMBER, selector.getUpstreamBuildNumber());
    }
    
    @Test
    public void testPerformSuccess() throws Exception {
        FreeStyleProject upstream = j.createFreeStyleProject();
        FreeStyleProject downstream = j.createFreeStyleProject();
        
        upstream.getBuildersList().add(new FileWriteBuilder("artifact.txt", "${BUILD_TAG}"));
        upstream.getPublishersList().add(new ArtifactArchiver(
                "artifact.txt",
                "",
                false,
                false
        ));
        upstream.getPublishersList().add(new Fingerprinter("", true));
        upstream.getPublishersList().add(new BuildTrigger(downstream.getFullName(), Result.SUCCESS.toString()));
        
        downstream.getBuildersList().add(new FileWriteBuilder("artifact2.txt", "${BUILD_ID}"));
        downstream.getBuildersList().add(CopyArtifactUtil.createCopyArtifact(
                upstream.getFullName(),
                "",
                new TriggeredBuildSelector(
                        false,
                        TriggeredBuildSelector.UpstreamFilterStrategy.UseNewest,
                        false
                ),
                "**/*",
                "",
                "",
                false,
                false,
                true    // important! required to have Jenkins track builds.
        ));
        downstream.getPublishersList().add(new ArtifactArchiver(
                "artifact2.txt",
                "",
                false,
                false
        ));
        
        upstream.save();
        downstream.save();
        j.jenkins.rebuildDependencyGraph();
        
        
        // upstreamBuild1 -> downstreamBuild1
        // upstreamBuild2 -> downstreamBuild2
        // upstreamBuild3 -> downstreamBuild3
        FreeStyleBuild upstreamBuild1 = upstream.scheduleBuild2(0).get();
        upstreamBuild1.setDisplayName("upstreamBuild1");
        j.waitUntilNoActivity();
        FreeStyleBuild downstreamBuild1 = downstream.getLastBuild();
        assertEquals(upstreamBuild1, downstreamBuild1.getUpstreamRelationshipBuild(upstream));
        j.assertBuildStatusSuccess(upstreamBuild1);
        j.assertBuildStatusSuccess(downstreamBuild1);
        
        FreeStyleBuild upstreamBuild2 = upstream.scheduleBuild2(0).get();
        upstreamBuild2.setDisplayName("upstreamBuild2");
        j.waitUntilNoActivity();
        FreeStyleBuild downstreamBuild2 = downstream.getLastBuild();
        assertEquals(upstreamBuild2, downstreamBuild2.getUpstreamRelationshipBuild(upstream));
        j.assertBuildStatusSuccess(upstreamBuild2);
        j.assertBuildStatusSuccess(downstreamBuild2);
        
        
        FreeStyleBuild upstreamBuild3 = upstream.scheduleBuild2(0).get();
        upstreamBuild3.setDisplayName("upstreamBuild3");
        j.waitUntilNoActivity();
        FreeStyleBuild downstreamBuild3 = downstream.getLastBuild();
        assertEquals(upstreamBuild3, downstreamBuild3.getUpstreamRelationshipBuild(upstream));
        j.assertBuildStatusSuccess(upstreamBuild3);
        j.assertBuildStatusSuccess(downstreamBuild3);
        
        // copies from downstream2, which is a downstream of upstreamBuild2.
        // specify with a build number.
        // not use variables.
        {
            FreeStyleProject p = j.createFreeStyleProject();
            p.getBuildersList().add(CopyArtifactUtil.createCopyArtifact(
                    downstream.getFullName(),
                    "",
                    new DownstreamBuildSelector(
                            upstream.getFullName(),
                            Integer.toString(upstreamBuild2.getNumber())
                    ),
                    "**/*",
                    "",
                    "",
                    false,
                    false,
                    true
            ));
            
            FreeStyleBuild b = p.scheduleBuild2(0).get();
            j.assertBuildStatusSuccess(b);
            
            FilePath artifact = b.getWorkspace().child("artifact2.txt");
            assertTrue(artifact.exists());
            assertEquals(downstreamBuild2.getId(), artifact.readToString());
        }
        
        // copies from downstream1, which is a downstream of upstreamBuild1.
        // specify with a build id.
        // use variables.
        {
            FreeStyleProject p = j.createFreeStyleProject();
            p.addProperty(new ParametersDefinitionProperty(
                    new StringParameterDefinition("UPSTREAM_PROJECT_NAME", ""),
                    new StringParameterDefinition("UPSTREAM_BUILD_NUMBER", "")
            ));
            p.getBuildersList().add(CopyArtifactUtil.createCopyArtifact(
                    downstream.getFullName(),
                    "",
                    new DownstreamBuildSelector(
                            "${UPSTREAM_PROJECT_NAME}",
                            "${UPSTREAM_BUILD_NUMBER}"
                    ),
                    "**/*",
                    "",
                    "",
                    false,
                    false,
                    true
            ));
            
            FreeStyleBuild b = p.scheduleBuild2(0, new Cause.UserIdCause(), new ParametersAction(
                    new StringParameterValue("UPSTREAM_PROJECT_NAME", upstream.getFullName()),
                    new StringParameterValue("UPSTREAM_BUILD_NUMBER", upstreamBuild1.getId())
            )).get();
            j.assertBuildStatusSuccess(b);
            
            FilePath artifact = b.getWorkspace().child("artifact2.txt");
            assertTrue(artifact.exists());
            assertEquals(downstreamBuild1.getId(), artifact.readToString());
        }
        
        // copies from downstream3, which is a downstream of upstreamBuild3.
        // specify with a display name.
        // use variables.
        {
            FreeStyleProject p = j.createFreeStyleProject();
            p.addProperty(new ParametersDefinitionProperty(
                    new StringParameterDefinition("UPSTREAM_PROJECT_NAME", ""),
                    new StringParameterDefinition("UPSTREAM_BUILD_NUMBER", "")
            ));
            p.getBuildersList().add(CopyArtifactUtil.createCopyArtifact(
                    downstream.getFullName(),
                    "",
                    new DownstreamBuildSelector(
                            "${UPSTREAM_PROJECT_NAME}",
                            "${UPSTREAM_BUILD_NUMBER}"
                    ),
                    "**/*",
                    "",
                    "",
                    false,
                    false,
                    true
            ));
            
            FreeStyleBuild b = p.scheduleBuild2(0, new Cause.UserIdCause(), new ParametersAction(
                    new StringParameterValue("UPSTREAM_PROJECT_NAME", upstream.getFullName()),
                    new StringParameterValue("UPSTREAM_BUILD_NUMBER", "upstreamBuild3")
            )).get();
            j.assertBuildStatusSuccess(b);
            
            FilePath artifact = b.getWorkspace().child("artifact2.txt");
            assertTrue(artifact.exists());
            assertEquals(downstreamBuild3.getId(), artifact.readToString());
        }
    }
    
    @Test
    public void testPerformFailure() throws Exception {
        FreeStyleProject upstream = j.createFreeStyleProject();
        FreeStyleProject downstream = j.createFreeStyleProject();
        
        upstream.getBuildersList().add(new FileWriteBuilder("artifact.txt", "${BUILD_TAG}"));
        upstream.getPublishersList().add(new ArtifactArchiver(
                "artifact.txt",
                "",
                false,
                false
        ));
        upstream.getPublishersList().add(new Fingerprinter("", true));
        upstream.getPublishersList().add(new BuildTrigger(downstream.getFullName(), Result.SUCCESS.toString()));
        
        downstream.getBuildersList().add(new FileWriteBuilder("artifact2.txt", "${BUILD_ID}"));
        downstream.getBuildersList().add(CopyArtifactUtil.createCopyArtifact(
                upstream.getFullName(),
                "",
                new TriggeredBuildSelector(
                        false,
                        TriggeredBuildSelector.UpstreamFilterStrategy.UseNewest,
                        false
                ),
                "**/*",
                "",
                "",
                false,
                false,
                true    // important! required to have Jenkins track builds.
        ));
        downstream.getPublishersList().add(new ArtifactArchiver(
                "artifact2.txt",
                "",
                false,
                false
        ));
        
        upstream.save();
        downstream.save();
        j.jenkins.rebuildDependencyGraph();
        
        
        // upstreamBuild1 -> downstreamBuild1
        // upstreamBuild2 -> (nothing)
        FreeStyleBuild upstreamBuild1 = upstream.scheduleBuild2(0).get();
        upstreamBuild1.setDisplayName("upstreamBuild1");
        j.waitUntilNoActivity();
        FreeStyleBuild downstreamBuild1 = downstream.getLastBuild();
        assertEquals(upstreamBuild1, downstreamBuild1.getUpstreamRelationshipBuild(upstream));
        j.assertBuildStatusSuccess(upstreamBuild1);
        j.assertBuildStatusSuccess(downstreamBuild1);
        
        FreeStyleBuild upstreamBuild2 = upstream.scheduleBuild2(0).get();
        upstreamBuild2.setDisplayName("upstreamBuild2");
        j.waitUntilNoActivity();
        FreeStyleBuild downstreamBuild2 = downstream.getLastBuild();
        assertEquals(upstreamBuild2, downstreamBuild2.getUpstreamRelationshipBuild(upstream));
        j.assertBuildStatusSuccess(upstreamBuild2);
        j.assertBuildStatusSuccess(downstreamBuild2);
        downstreamBuild2.delete();
        
        
        FreeStyleProject p = j.createFreeStyleProject();
        p.addProperty(new ParametersDefinitionProperty(
                new StringParameterDefinition("UPSTREAM_PROJECT_NAME", ""),
                new StringParameterDefinition("UPSTREAM_BUILD_NUMBER", "")
        ));
        p.getBuildersList().add(CopyArtifactUtil.createCopyArtifact(
                downstream.getFullName(),
                "",
                new DownstreamBuildSelector(
                        "${UPSTREAM_PROJECT_NAME}",
                        "${UPSTREAM_BUILD_NUMBER}"
                ),
                "**/*",
                "",
                "",
                false,
                true,
                    // Look! As this is an optional, the build doesn't fail even if the build is not found.
                    // This allows us to find exceptions.
                true
        ));
        
        // upstreamProjectName is empty
        {
            FreeStyleBuild b = p.scheduleBuild2(0, new Cause.UserIdCause(), new ParametersAction(
                    new StringParameterValue("UPSTREAM_PROJECT_NAME", "   "),
                    new StringParameterValue("UPSTREAM_BUILD_NUMBER", "2")
            )).get();
            j.assertBuildStatusSuccess(b);
            assertEquals(Collections.emptyList(), b.getWorkspace().list());
        }
        
        // upstreamBuildNumber is empty
        {
            FreeStyleBuild b = p.scheduleBuild2(0, new Cause.UserIdCause(), new ParametersAction(
                    new StringParameterValue("UPSTREAM_PROJECT_NAME", upstream.getFullName()),
                    new StringParameterValue("UPSTREAM_BUILD_NUMBER", "    ")
            )).get();
            j.assertBuildStatusSuccess(b);
            assertEquals(Collections.emptyList(), b.getWorkspace().list());
        }
        
        // upstreamProjectName is invalid
        {
            FreeStyleBuild b = p.scheduleBuild2(0, new Cause.UserIdCause(), new ParametersAction(
                    new StringParameterValue("UPSTREAM_PROJECT_NAME", "Nosuchproject"),
                    new StringParameterValue("UPSTREAM_BUILD_NUMBER", "2")
            )).get();
            j.assertBuildStatusSuccess(b);
            assertEquals(Collections.emptyList(), b.getWorkspace().list());
        }
        
        // upstreamBuildNumber is invalid
        {
            FreeStyleBuild b = p.scheduleBuild2(0, new Cause.UserIdCause(), new ParametersAction(
                    new StringParameterValue("UPSTREAM_PROJECT_NAME", upstream.getFullName()),
                    new StringParameterValue("UPSTREAM_BUILD_NUMBER", "NoSuchBuild")
            )).get();
            j.assertBuildStatusSuccess(b);
            assertEquals(Collections.emptyList(), b.getWorkspace().list());
        }
        
        // No downstream
        {
            FreeStyleBuild b = p.scheduleBuild2(0, new Cause.UserIdCause(), new ParametersAction(
                    new StringParameterValue("UPSTREAM_PROJECT_NAME", upstream.getFullName()),
                    new StringParameterValue("UPSTREAM_BUILD_NUMBER", "upstreamBuild2")
            )).get();
            j.assertBuildStatusSuccess(b);
            assertEquals(Collections.emptyList(), b.getWorkspace().list());
        }
    }
    
    @Test
    public void testPerformRelative() throws Exception {
        // folder1/upstream -> folder2/downstream
        // folder1/folder3/copier copies
        //    from folder2/downstream (../../folder2/downstream)
        //    which is a downstream of folder1/upstream (../upstream)
        
        MockFolder folder1 = j.jenkins.createProject(MockFolder.class, "folder1");
        MockFolder folder2 = j.jenkins.createProject(MockFolder.class, "folder2");
        MockFolder folder3 = folder1.createProject(MockFolder.class, "folder3");
        
        FreeStyleProject upstream = folder1.createProject(FreeStyleProject.class, "upstream");
        FreeStyleProject downstream = folder2.createProject(FreeStyleProject.class, "downstream");
        FreeStyleProject copier = folder3.createProject(FreeStyleProject.class, "copier");
        
        upstream.getBuildersList().add(new FileWriteBuilder("artifact.txt", "${BUILD_TAG}"));
        upstream.getPublishersList().add(new ArtifactArchiver(
                "artifact.txt",
                "",
                false,
                false
        ));
        upstream.getPublishersList().add(new Fingerprinter("", true));
        upstream.getPublishersList().add(new BuildTrigger("../folder2/downstream", Result.SUCCESS.toString()));
        
        downstream.getBuildersList().add(new FileWriteBuilder("artifact2.txt", "${BUILD_ID}"));
        downstream.getBuildersList().add(CopyArtifactUtil.createCopyArtifact(
                "../folder1/upstream",
                "",
                new TriggeredBuildSelector(
                        false,
                        TriggeredBuildSelector.UpstreamFilterStrategy.UseNewest,
                        false
                ),
                "**/*",
                "",
                "",
                false,
                false,
                true    // important! required to have Jenkins track builds.
        ));
        downstream.getPublishersList().add(new ArtifactArchiver(
                "artifact2.txt",
                "",
                false,
                false
        ));
        
        upstream.save();
        downstream.save();
        j.jenkins.rebuildDependencyGraph();
        
        // upstreamBuild -> downstreamBuild
        FreeStyleBuild upstreamBuild = upstream.scheduleBuild2(0).get();
        j.waitUntilNoActivity();
        FreeStyleBuild downstreamBuild = downstream.getLastBuild();
        assertEquals(upstreamBuild, downstreamBuild.getUpstreamRelationshipBuild(upstream));
        j.assertBuildStatusSuccess(upstreamBuild);
        j.assertBuildStatusSuccess(downstreamBuild);
        
        copier.getBuildersList().add(CopyArtifactUtil.createCopyArtifact(
                "../../folder2/downstream",
                "",
                new DownstreamBuildSelector(
                        "../upstream",
                        Integer.toString(upstreamBuild.getNumber())
                ),
                "**/*",
                "",
                "",
                false,
                false,
                true
        ));
        
        FreeStyleBuild b = copier.scheduleBuild2(0).get();
        j.assertBuildStatusSuccess(b);
        
        FilePath artifact = b.getWorkspace().child("artifact2.txt");
        assertTrue(artifact.exists());
        assertEquals(downstreamBuild.getId(), artifact.readToString());
    }
    
    @Test
    public void testCheckUpstreamProjectName() throws Exception {
        DownstreamBuildSelector.DescriptorImpl d = (DownstreamBuildSelector.DescriptorImpl)j.jenkins.getDescriptorOrDie(DownstreamBuildSelector.class);
        
        MockAuthorizationStrategy auth = new MockAuthorizationStrategy();
        auth.grant(Jenkins.READ).onRoot().to("devel");
        
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(auth);
        
        // project1
        // folder1/project2
        // folder1/project3 cannot read from devel
        MockFolder folder1 = j.jenkins.createProject(MockFolder.class, "folder1");
        
        FreeStyleProject project1 = j.createFreeStyleProject("project1");
        auth.grant(Item.READ).onItems(project1).to("devel");
        
        FreeStyleProject project2 = folder1.createProject(FreeStyleProject.class, "project2");
        auth.grant(Item.READ).onItems(project2).to("devel");

        FreeStyleProject project3 = folder1.createProject(FreeStyleProject.class, "project3");
        
        assertEquals(FormValidation.Kind.ERROR, d.doCheckUpstreamProjectName(project1, null).kind);
        assertEquals(FormValidation.Kind.ERROR, d.doCheckUpstreamProjectName(project1, "").kind);
        assertEquals(FormValidation.Kind.ERROR, d.doCheckUpstreamProjectName(project1, "  ").kind);
        
        assertEquals(FormValidation.Kind.OK, d.doCheckUpstreamProjectName(project1, "$VAR").kind);
        assertEquals(FormValidation.Kind.OK, d.doCheckUpstreamProjectName(project1, "FOO${VAR}").kind);
        assertEquals(FormValidation.Kind.OK, d.doCheckUpstreamProjectName(project1, "Project\\$").kind);    // limitation
        
        assertEquals(FormValidation.Kind.ERROR, d.doCheckUpstreamProjectName(project1, "nosuchproject").kind);
        
        assertEquals(FormValidation.Kind.OK, d.doCheckUpstreamProjectName(project1, "folder1/project2").kind);
        assertEquals(FormValidation.Kind.OK, d.doCheckUpstreamProjectName(project2, "../project1").kind);
        assertEquals(FormValidation.Kind.OK, d.doCheckUpstreamProjectName(project2, "project3").kind);
        assertEquals(FormValidation.Kind.OK, d.doCheckUpstreamProjectName(project2, "/folder1/project3").kind);

        //JENKINS-32526: Check that it behaves gracefully for an unknown context
        assertEquals(FormValidation.Kind.ERROR, d.doCheckUpstreamProjectName(null, null).kind);
        assertEquals(FormValidation.Kind.ERROR, d.doCheckUpstreamProjectName(null, "").kind);
        assertEquals(FormValidation.Kind.ERROR, d.doCheckUpstreamProjectName(null, "  ").kind);

        //Ancestor null
        assertEquals(FormValidation.Kind.OK, d.doCheckUpstreamProjectName(null, "nosuchproject").kind);
        assertEquals(FormValidation.Kind.OK, d.doCheckUpstreamProjectName(null, "$VAR").kind);
        assertEquals(FormValidation.Kind.OK, d.doCheckUpstreamProjectName(null, "FOO${VAR}").kind);
        assertEquals(FormValidation.Kind.OK, d.doCheckUpstreamProjectName(null, "Project\\$").kind);    // limitation
        //Only relative path from Root works
        assertEquals(FormValidation.Kind.OK, d.doCheckUpstreamProjectName(null, "folder1/project2").kind);
        
        // permission check
        Authentication a = Jenkins.getAuthentication();
        try {
            SecurityContextHolder.getContext().setAuthentication(User.get("devel").impersonate());
            assertEquals(FormValidation.Kind.OK, d.doCheckUpstreamProjectName(project2, "../project1").kind);
            assertEquals(FormValidation.Kind.ERROR, d.doCheckUpstreamProjectName(project2, "project3").kind);
            assertEquals(FormValidation.Kind.OK, d.doCheckUpstreamProjectName(null, "/project1").kind);
            assertEquals(FormValidation.Kind.OK, d.doCheckUpstreamProjectName(null, "project3").kind);
        } finally {
            SecurityContextHolder.getContext().setAuthentication(a);
        }
    }
    
    @Test
    public void testCheckUpstreamBuildNumber() throws Exception {
        DownstreamBuildSelector.DescriptorImpl d = (DownstreamBuildSelector.DescriptorImpl)j.jenkins.getDescriptorOrDie(DownstreamBuildSelector.class);
        
        MockAuthorizationStrategy auth = new MockAuthorizationStrategy()
            .grant(Jenkins.READ).onRoot().to("devel");
        
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(auth);
        
        // project1
        // project2
        //   build1
        // project3  cannot read from devel
        FreeStyleProject project1 = j.createFreeStyleProject("project1");
        auth.grant(Item.READ).onItems(project1).to("devel");
        
        FreeStyleProject project2 = j.createFreeStyleProject("project2");
        auth.grant(Item.READ).onItems(project2).to("devel");
        FreeStyleBuild build1 = project2.scheduleBuild2(0).get();
        
        FreeStyleProject project3 = j.createFreeStyleProject("project3");
        
        assertEquals(FormValidation.Kind.OK, d.doCheckUpstreamBuildNumber(project1, "", Integer.toString(build1.getNumber())).kind);
        assertEquals(FormValidation.Kind.OK, d.doCheckUpstreamBuildNumber(project1, "$VAR", Integer.toString(build1.getNumber())).kind);
        
        assertEquals(FormValidation.Kind.ERROR, d.doCheckUpstreamBuildNumber(project1, "project2", null).kind);
        assertEquals(FormValidation.Kind.ERROR, d.doCheckUpstreamBuildNumber(project1, "project2", "").kind);
        assertEquals(FormValidation.Kind.ERROR, d.doCheckUpstreamBuildNumber(project1, "project2", "  ").kind);
        
        assertEquals(FormValidation.Kind.OK, d.doCheckUpstreamBuildNumber(project1, "project2", "FOO${VAR}").kind);
        assertEquals(FormValidation.Kind.OK, d.doCheckUpstreamBuildNumber(project1, "project2", "\\${VAR}").kind);  // limitation
        
        assertEquals(FormValidation.Kind.OK, d.doCheckUpstreamBuildNumber(project1, "project2", Integer.toString(build1.getNumber())).kind);
        assertEquals(FormValidation.Kind.OK, d.doCheckUpstreamBuildNumber(project1, "project2", build1.getId()).kind);
        assertEquals(FormValidation.Kind.OK, d.doCheckUpstreamBuildNumber(project1, "project2", build1.getDisplayName()).kind);
        
        assertEquals(FormValidation.Kind.ERROR, d.doCheckUpstreamBuildNumber(project1, "project2", "9999").kind);
        assertEquals(FormValidation.Kind.ERROR, d.doCheckUpstreamBuildNumber(project1, "project2", "NosuchBuild").kind);

        //JENKINS-32526: Check that it behaves gracefully for an unknown context
        assertEquals(FormValidation.Kind.OK, d.doCheckUpstreamBuildNumber(null, "", Integer.toString(build1.getNumber())).kind);
        assertEquals(FormValidation.Kind.OK, d.doCheckUpstreamBuildNumber(null, "$VAR", Integer.toString(build1.getNumber())).kind);

        assertEquals(FormValidation.Kind.ERROR, d.doCheckUpstreamBuildNumber(null, "project2", null).kind);
        assertEquals(FormValidation.Kind.ERROR, d.doCheckUpstreamBuildNumber(null, "project2", "").kind);
        assertEquals(FormValidation.Kind.ERROR, d.doCheckUpstreamBuildNumber(null, "project2", "  ").kind);

        assertEquals(FormValidation.Kind.OK, d.doCheckUpstreamBuildNumber(null, "project2", "FOO${VAR}").kind);
        assertEquals(FormValidation.Kind.OK, d.doCheckUpstreamBuildNumber(null, "project2", "\\${VAR}").kind);  // limitation

        assertEquals(FormValidation.Kind.OK, d.doCheckUpstreamBuildNumber(null, "project2", Integer.toString(build1.getNumber())).kind);
        assertEquals(FormValidation.Kind.OK, d.doCheckUpstreamBuildNumber(null, "project2", build1.getId()).kind);
        assertEquals(FormValidation.Kind.OK, d.doCheckUpstreamBuildNumber(null, "project2", build1.getDisplayName()).kind);

        assertEquals(FormValidation.Kind.OK, d.doCheckUpstreamBuildNumber(null, "project2", "9999").kind);
        assertEquals(FormValidation.Kind.OK, d.doCheckUpstreamBuildNumber(null, "project2", "NosuchBuild").kind);

        // permission check
        Authentication a = Jenkins.getAuthentication();
        try {
            SecurityContextHolder.getContext().setAuthentication(User.get("devel").impersonate());
            assertEquals(FormValidation.Kind.OK, d.doCheckUpstreamBuildNumber(project1, "project3", "nosuchbuild").kind);  // limitation
            assertEquals(FormValidation.Kind.OK, d.doCheckUpstreamBuildNumber(null, "project3", "nosuchbuild").kind);
        } finally {
            SecurityContextHolder.getContext().setAuthentication(a);
        }
    }

    @Test
    public void testAutoCompleteUpstreamProjectName() throws Exception {
        DownstreamBuildSelector.DescriptorImpl d = (DownstreamBuildSelector.DescriptorImpl) j.jenkins.getDescriptorOrDie(DownstreamBuildSelector.class);

        MockAuthorizationStrategy auth = new MockAuthorizationStrategy();
        auth.grant(Jenkins.READ).onRoot().to("devel");

        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(auth);

        // project1
        // project2  cannot read from devel
        FreeStyleProject project1 = j.createFreeStyleProject("project1");
        auth.grant(Item.READ).onItems(project1).to("devel");

        FreeStyleProject project2 = j.createFreeStyleProject("project2");

        //Check Empty strings
        testAutoCompleteUpstreamProjectName(new String [] {project1.getName(), project2.getName()}, "", project1, d);
        //Check simple matching string
        testAutoCompleteUpstreamProjectName(new String [] {project1.getName(), project2.getName()}, "proj", project1, d);
        //Check non matching string
        testAutoCompleteUpstreamProjectName(new String [] {}, "FOO", project1, d);
        //Check matching string
        testAutoCompleteUpstreamProjectName(new String [] {project1.getName()}, "project1", project2, d);
    }

    private void testAutoCompleteUpstreamProjectName(
            String [] expectedValues,
            String value,
            FreeStyleProject project,
            DownstreamBuildSelector.DescriptorImpl d) {

        Set<String> actualValues = new TreeSet<>(d.doAutoCompleteUpstreamProjectName(value, project).getValues());
        assertArrayEquals(expectedValues, actualValues.toArray(new String[0]));
        //JENKINS-32526: Auto-completion disabled if no context
        actualValues = new TreeSet<>(d.doAutoCompleteUpstreamProjectName(value, null).getValues());
        assertArrayEquals(new String[]{}, actualValues.toArray(new String[0]));
    }

    @Test
    public void testCheckUpstreamProjectNameForWorkflow() throws Exception {
        FreeStyleProject context = j.createFreeStyleProject();
        WorkflowJob target = j.jenkins.createProject(WorkflowJob.class, "workflow-test");
        
        DownstreamBuildSelector.DescriptorImpl d = (DownstreamBuildSelector.DescriptorImpl)j.jenkins.getDescriptorOrDie(DownstreamBuildSelector.class);
        // DownstreamBuildSelector is not applicable to workflow.
        assertEquals(FormValidation.Kind.ERROR, d.doCheckUpstreamProjectName(context, target.getFullName()).kind);
    }
    
    @Test
    public void testUpstreamIsWorkflow() throws Exception {
        WorkflowJob upstream = j.jenkins.createProject(WorkflowJob.class, "upstream");
        upstream.setDefinition(new CpsFlowDefinition(
                "node {"
                + "writeFile text: \"${env.BUILD_TAG}\", file: 'upstream_artifact.txt'; "
                + "step([$class: 'ArtifactArchiver', artifacts: 'upstream_artifact.txt'])"
                + "}",
                true
        ));
        
        WorkflowRun upstreamBuild = j.assertBuildStatusSuccess(upstream.scheduleBuild2(0));
        
        FreeStyleProject downstream = j.createFreeStyleProject();
        CopyArtifact ca = new CopyArtifact(upstream.getFullName());
        ca.setFingerprintArtifacts(true);
        ca.setFilter("upstream_artifact.txt");
        downstream.getBuildersList().add(ca);
        downstream.getBuildersList().add(new FileWriteBuilder("downstream_artifact.txt", "${BUILD_TAG}"));
        ArtifactArchiver aa = new ArtifactArchiver("downstream_artifact.txt");
        aa.setAllowEmptyArchive(false);
        aa.setFingerprint(true);
        downstream.getPublishersList().add(aa);
        
        FreeStyleBuild downstreamBuild = j.assertBuildStatusSuccess(downstream.scheduleBuild2(0));
        
        FreeStyleProject p = j.createFreeStyleProject();
        p.getBuildersList().add(CopyArtifactUtil.createCopyArtifact(
                downstream.getFullName(),
                "",
                new DownstreamBuildSelector(
                        upstream.getFullName(),
                        Integer.toString(upstreamBuild.getNumber())
                ),
                "**/*",
                "",
                "",
                false,
                false,
                true
        ));
        
        // fail as DownstreamBuildSelector doesn't support workflow upstream.
        FreeStyleBuild b = j.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0).get());
        // to see expected log is recorded.
        //System.out.println(b.getLog());
    }
    
    @Test
    public void testDownstreamIsWorkflow() throws Exception {
        FreeStyleProject upstream = j.createFreeStyleProject();
        upstream.getBuildersList().add(new FileWriteBuilder("upstream_artifact.txt", "${BUILD_TAG}"));
        ArtifactArchiver aa = new ArtifactArchiver("upstream_artifact.txt");
        aa.setAllowEmptyArchive(false);
        aa.setFingerprint(true);
        upstream.getPublishersList().add(aa);
        
        FreeStyleBuild upstreamBuild = j.assertBuildStatusSuccess(upstream.scheduleBuild2(0));
        
        WorkflowJob downstream = j.jenkins.createProject(WorkflowJob.class, "downstream");
        downstream.setDefinition(new CpsFlowDefinition(
                "node {"
                + "step([$class: 'CopyArtifact', projectName: '" + upstream.getFullName() + "', filter: 'upstream_artifact.txt', fingerprintArtifacts: true]);"
                + "writeFile text: \"${env.BUILD_TAG}\", file: 'downstream_artifact.txt'; "
                + "step([$class: 'ArtifactArchiver', artifacts: 'downstream_artifact.txt'])"
                + "}",
                true
        ));
        
        WorkflowRun downstreamBuild = j.assertBuildStatusSuccess(downstream.scheduleBuild2(0));
        
        FreeStyleProject p = j.createFreeStyleProject();
        p.getBuildersList().add(CopyArtifactUtil.createCopyArtifact(
                downstream.getFullName(),
                "",
                new DownstreamBuildSelector(
                        upstream.getFullName(),
                        Integer.toString(upstreamBuild.getNumber())
                ),
                "**/*",
                "",
                "",
                false,
                false,
                true
        ));
        
        // fail as DownstreamBuildSelector doesn't support workflow downstream.
        FreeStyleBuild b = j.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0).get());
        // to see expected log is recorded.
        //System.out.println(b.getLog());
    }
}
