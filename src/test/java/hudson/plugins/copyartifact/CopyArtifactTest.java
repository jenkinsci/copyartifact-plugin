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

import hudson.FilePath;
import hudson.Functions;
import hudson.Launcher;
import hudson.matrix.Axis;
import hudson.matrix.AxisList;
import hudson.matrix.Combination;
import hudson.matrix.MatrixBuild;
import hudson.matrix.MatrixProject;
import hudson.matrix.TextAxis;
import hudson.matrix.MatrixRun;
import hudson.maven.MavenModuleSet;
import hudson.maven.MavenModuleSetBuild;
import hudson.model.*;
import hudson.model.Cause.UserCause;
import hudson.plugins.copyartifact.testutils.CopyArtifactUtil;
import hudson.plugins.copyartifact.testutils.FileWriteBuilder;
import hudson.plugins.copyartifact.testutils.WrapperBuilder;
import hudson.remoting.VirtualChannel;
import hudson.security.ACL;
import hudson.security.Permission;
import hudson.security.AuthorizationMatrixProperty;
import hudson.security.GlobalMatrixAuthorizationStrategy;
import hudson.security.ProjectMatrixAuthorizationStrategy;
import hudson.slaves.DumbSlave;
import hudson.slaves.SlaveComputer;
import hudson.tasks.ArtifactArchiver;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildTrigger;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import hudson.util.VersionNumber;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import jenkins.model.Jenkins;
import jenkins.security.QueueItemAuthenticatorConfiguration;

import org.acegisecurity.context.SecurityContext;
import org.acegisecurity.context.SecurityContextHolder;
import org.acegisecurity.providers.UsernamePasswordAuthenticationToken;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.jvnet.hudson.test.ExtractResourceSCM;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.CaptureEnvironmentBuilder;
import org.jvnet.hudson.test.FailureBuilder;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.JenkinsRule.WebClient;
import org.jvnet.hudson.test.MockFolder;
import org.jvnet.hudson.test.ToolInstallations;
import org.jvnet.hudson.test.UnstableBuilder;
import org.jvnet.hudson.test.recipes.LocalData;
import org.jvnet.hudson.test.recipes.WithPlugin;

import com.cloudbees.hudson.plugins.folder.Folder;
import com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException;
import com.google.common.collect.Sets;

import org.jvnet.hudson.test.TestBuilder;

import static org.junit.Assert.*;

/**
 * Test interaction of copyartifact plugin with Jenkins core.
 * @author Alan Harder
 */
public class CopyArtifactTest {
    @Rule
    public final JenkinsRule rule = new JenkinsRule();

    @Rule
    public TestName name = new TestName();

    // Tests using slaves fails with Jenkins < 1.520 on Windows.
    // See https://wiki.jenkins-ci.org/display/JENKINS/Unit+Test+on+Windows
    private void purgeSlaves() {
        List<Computer> disconnectingComputers = new ArrayList<Computer>();
        List<VirtualChannel> closingChannels = new ArrayList<VirtualChannel>();
        for (Computer computer: rule.jenkins.getComputers()) {
            if (!(computer instanceof SlaveComputer)) {
                continue;
            }
            // disconnect slaves.
            // retrieve the channel before disconnecting.
            // even a computer gets offline, channel delays to close.
            if (!computer.isOffline()) {
                VirtualChannel ch = computer.getChannel();
                computer.disconnect(null);
                disconnectingComputers.add(computer);
                closingChannels.add(ch);
            }
        }
        
        try {
            // Wait for all computers disconnected and all channels closed.
            for (Computer computer: disconnectingComputers) {
                computer.waitUntilOffline();
            }
            for (VirtualChannel ch: closingChannels) {
                ch.join();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
    
    @After
    public void tearDown() throws Exception {
        if(Functions.isWindows()) {
            purgeSlaves();
        }
    }

    private FreeStyleProject createProject(String otherProject, String parameters, String filter,
            String target, boolean stable, boolean flatten, boolean optional, boolean fingerprintArtifacts)
            throws IOException {
        FreeStyleProject p = rule.createFreeStyleProject();
        CopyArtifact copyArtifact = CopyArtifactUtil.createCopyArtifact(otherProject, parameters, new StatusBuildSelector(stable), filter, target, flatten, optional, fingerprintArtifacts);
        p.getBuildersList().add(copyArtifact);
        return p;
    }

    @Deprecated
    private FreeStyleProject createProject(String otherProject, String parameters, String filter,
            String target, boolean stable, boolean flatten, boolean optional)
            throws IOException {
        return createProject(otherProject, parameters, filter, target, stable, flatten, optional, true);
    }

    private static class ArtifactBuilder extends Builder {
        @Override
        public boolean perform(AbstractBuild<?,?> build, Launcher launcher, BuildListener listener)
                throws InterruptedException, IOException {
            // Make some files to archive as artifacts
            FilePath ws = build.getWorkspace();
            ws.child("foo.txt").touch(System.currentTimeMillis());
            ws.child("subdir").mkdirs();
            ws.child("subdir/subfoo.txt").touch(System.currentTimeMillis());
            ws.child("deepfoo/a/b").mkdirs();
            ws.child("deepfoo/a/b/c.log").touch(System.currentTimeMillis());
            ws.child(".hg").mkdirs();
            ws.child(".hg/defaultexclude.txt").touch(System.currentTimeMillis());
            // For matrix tests write one more file:
            String foo = build.getBuildVariables().get("FOO");
            if (foo != null) ws.child(foo + ".txt").touch(System.currentTimeMillis());
            return true;
        }
    }

    private FreeStyleProject createArtifactProject(String name) throws IOException {
        FreeStyleProject p = name != null ? rule.createFreeStyleProject(name) : rule.createFreeStyleProject();
        p.getBuildersList().add(new ArtifactBuilder());
        p.getPublishersList().add(new ArtifactArchiver("**", "", false, false));
        return p;
    }

    private FreeStyleProject createArtifactProject() throws IOException {
        return createArtifactProject(null);
    }

    private String createUniqueProjectName() {
        return "test" + rule.jenkins.getItems().size();
    }

    /**
     * Creates an empty Maven project with an unique name.
     *
     * @return an empty Maven project with an unique name.
     */
    private MavenModuleSet createMavenProject() throws IOException {
        MavenModuleSet mavenModuleSet = rule.jenkins.createProject(MavenModuleSet.class, createUniqueProjectName());
        mavenModuleSet.setRunHeadless(true);
        return mavenModuleSet;
    }

    /**
     * Creates an empty Matrix project with an unique name.
     *
     * @return an empty Matrix project with an unique name.
     */
    private MatrixProject createMatrixProject() throws IOException {
        return rule.jenkins.createProject(MatrixProject.class, createUniqueProjectName());
    }


    private MatrixProject createMatrixArtifactProject() throws IOException {
        MatrixProject p = createMatrixProject();
        p.setAxes(new AxisList(new Axis("FOO", "one", "two")));
        p.getBuildersList().add(new ArtifactBuilder());
        p.getPublishersList().add(new ArtifactArchiver("**", "", false, false));
        return p;
    }

    private void assertFile(boolean exists, String path, AbstractBuild<?,?> b)
            throws IOException, InterruptedException {
        if (b.getWorkspace().child(path).exists() != exists)
            assertEquals(path + ": " + rule.getLog(b), exists, !exists);
    }

    @Test
    public void testMissingProject() throws Exception {
        FreeStyleProject p = createProject("invalid", null, "", "", false, false, false, true);
        rule.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0, new UserCause()).get());
    }

    @Test
    public void testMissingBuild() throws Exception {
        FreeStyleProject other = rule.createFreeStyleProject(),
                         p = createProject(other.getName(), null, "", "", false, false, false, true);
        rule.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0, new UserCause()).get());
    }

    @Test
    public void testMissingStableBuild() throws Exception {
        FreeStyleProject other = rule.createFreeStyleProject(),
                         p = createProject(other.getName(), null, "", "", true, false, false, true);
        // Make an unstable build in "other"
        other.getBuildersList().add(new UnstableBuilder());
        rule.assertBuildStatus(Result.UNSTABLE, other.scheduleBuild2(0, new UserCause()).get());
        rule.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0, new UserCause()).get());
    }

    @Test
    public void testMissingArtifact() throws Exception {
        FreeStyleProject other = rule.createFreeStyleProject(),
                         p = createProject(other.getName(), null, "*.txt", "", false, false, false, true);
        rule.assertBuildStatusSuccess(other.scheduleBuild2(0, new UserCause()).get());
        rule.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0, new UserCause()).get());
    }

    @Test
    public void testCopyAllWithFingerprints() throws Exception {
        FreeStyleProject other = createArtifactProject(),
                         p = createProject(other.getName(), null, "", "", false, false, false, true);
        FreeStyleBuild s = rule.assertBuildStatusSuccess(other.scheduleBuild2(0, new UserCause()).get());
        FreeStyleBuild b = p.scheduleBuild2(0, new UserCause()).get();
        rule.assertBuildStatusSuccess(b);
        assertFile(true, "foo.txt", b);
        assertFile(true, "subdir/subfoo.txt", b);
        assertFile(true, "deepfoo/a/b/c.log", b);
        
        // testing fingerprints
        String d = b.getWorkspace().child("foo.txt").digest();
        Fingerprint f = Hudson.getInstance().getFingerprintMap().get(d);
        assertSame(f.getOriginal().getRun(),s);
        assertTrue(f.getRangeSet(p).includes(b.getNumber()));
    }

    @Test
    public void testCopyAllWithoutFingerprints() throws Exception {
        FreeStyleProject other = createArtifactProject(),
                         p = createProject(other.getName(), null, "", "", false, false, false, false);
        FreeStyleBuild s = rule.assertBuildStatusSuccess(other.scheduleBuild2(0, new UserCause()).get());
        FreeStyleBuild b = p.scheduleBuild2(0, new UserCause()).get();
        rule.assertBuildStatusSuccess(b);
        assertFile(true, "foo.txt", b);
        assertFile(true, "subdir/subfoo.txt", b);
        assertFile(true, "deepfoo/a/b/c.log", b);
        
        // testing no fingerprints
        String d = b.getWorkspace().child("foo.txt").digest();
        assertNull(Hudson.getInstance().getFingerprintMap().get(d));
    }

    @Test
    public void testCopyWithFilter() throws Exception {
        FreeStyleProject other = createArtifactProject(),
                 p = createProject(other.getName(), null, "**/bogus*, **/sub*, bogus/**", "",
                                   false, false, false, true);
        rule.assertBuildStatusSuccess(other.scheduleBuild2(0, new UserCause()).get());
        FreeStyleBuild b = p.scheduleBuild2(0, new UserCause()).get();
        rule.assertBuildStatusSuccess(b);
        assertFile(false, "foo.txt", b);
        assertFile(true, "subdir/subfoo.txt", b);
        assertFile(false, "deepfoo/a/b/c.log", b);
    }

    @Test
    public void testCopyToTarget() throws Exception {
        FreeStyleProject other = createArtifactProject(),
                 p = createProject(other.getName(), null, "deep*/**", "new/deep/dir",
                                   true, false, false, true);
        rule.assertBuildStatusSuccess(other.scheduleBuild2(0, new UserCause()).get());
        FreeStyleBuild b = p.scheduleBuild2(0, new UserCause()).get();
        rule.assertBuildStatusSuccess(b);
        assertFile(false, "foo.txt", b);
        assertFile(false, "new/deep/dir/foo.txt", b);
        assertFile(true, "new/deep/dir/deepfoo/a/b/c.log", b);
    }

    @Test
    public void testCopyToSlave() throws Exception {
        DumbSlave node = rule.createSlave();
        SlaveComputer c = node.getComputer();
        c.connect(false).get(); // wait until it's connected
        if(c.isOffline())
            fail("Slave failed to go online: " + c.getLog());
        FreeStyleProject other = createArtifactProject(),
                         p = createProject(other.getName(), null, "", "", false, false, false, true);
        rule.assertBuildStatusSuccess(other.scheduleBuild2(0, new UserCause()).get());
        p.setAssignedLabel(node.getSelfLabel());
        FreeStyleBuild b = p.scheduleBuild2(0, new UserCause()).get();
        rule.assertBuildStatusSuccess(b);
        assertSame(node, b.getBuiltOn());
        assertFile(true, "foo.txt", b);
        assertFile(true, "subdir/subfoo.txt", b);
        assertFile(true, "deepfoo/a/b/c.log", b);
    }

    @Test
    public void testParameters() throws Exception {
        FreeStyleProject other = createArtifactProject(),
                         p = createProject("$PROJSRC", null, "$BASE/*.txt", "$TARGET/bar",
                                           false, false, false, true);
        rule.assertBuildStatusSuccess(other.scheduleBuild2(0, new UserCause()).get());
        FreeStyleBuild b = p.scheduleBuild2(0, new UserCause(),
                new ParametersAction(new StringParameterValue("PROJSRC", other.getName()),
                                     new StringParameterValue("BASE", "*r"),
                                     new StringParameterValue("TARGET", "foo"))).get();
        rule.assertBuildStatusSuccess(b);
        assertFile(false, "foo/bar/foo.txt", b);
        assertFile(true, "foo/bar/subdir/subfoo.txt", b);
    }

    /** Test copying artifacts from a particular configuration of a matrix job */
    @Test
    public void testMatrixJob() throws Exception {
        MatrixProject other = createMatrixArtifactProject();
        FreeStyleProject p = createProject(other.getName() + "/FOO=two", null, "", "",
                                           true, false, false, true);
        rule.assertBuildStatusSuccess(other.scheduleBuild2(0, new UserCause()).get());
        FreeStyleBuild b = p.scheduleBuild2(0, new UserCause()).get();
        rule.assertBuildStatusSuccess(b);
        assertFile(true, "foo.txt", b);
        assertFile(true, "two.txt", b);
        assertFile(true, "subdir/subfoo.txt", b);
        assertFile(true, "deepfoo/a/b/c.log", b);
    }

    /** Test artifact copy between matrix jobs, for artifact from matching axis */
    @Test
    public void testMatrixToMatrix() throws Exception {
        MatrixProject other = createMatrixArtifactProject(),
                      p = createMatrixProject();
        p.setAxes(new AxisList(new Axis("FOO", "one", "two"))); // should match other job
        p.getBuildersList().add(CopyArtifactUtil.createCopyArtifact(other.getName() + "/FOO=$FOO", null,
                new StatusBuildSelector(true), "", "", false, false, true));
        rule.assertBuildStatusSuccess(other.scheduleBuild2(0, new UserCause()).get());
        MatrixBuild b = p.scheduleBuild2(0, new UserCause()).get();
        rule.assertBuildStatusSuccess(b);
        MatrixRun r = b.getRun(new Combination(Collections.singletonMap("FOO", "one")));
        assertFile(true, "one.txt", r);
        assertFile(false, "two.txt", r);
        r = b.getRun(new Combination(Collections.singletonMap("FOO", "two")));
        assertFile(false, "one.txt", r);
        assertFile(true, "two.txt", r);
    }

    private static class ArchMatrixBuilder extends Builder {
        @Override
        public boolean perform(AbstractBuild<?,?> build, Launcher launcher, BuildListener listener)
                throws InterruptedException, IOException {
            // Get matrix axis value
            String arch = build.getBuildVariables().get("ARCH");
            FilePath ws = build.getWorkspace();
            ws.child("target").mkdirs();
            // One fixed filename:
            ws.child("target/readme.txt").touch(System.currentTimeMillis());
            // Use axis value in one filename:
            ws.child("target/" + arch + ".out").touch(System.currentTimeMillis());
            return true;
        }
    }

    /** Test copying artifacts from all configurations of a matrix job */
    @Test
    public void testMatrixAll() throws Exception {
        MatrixProject mp = createMatrixProject();
        mp.setAxes(new AxisList(new Axis("ARCH", "sparc", "x86")));
        mp.getBuildersList().add(new ArchMatrixBuilder());
        mp.getPublishersList().add(new ArtifactArchiver("target/*", "", false, false));
        rule.assertBuildStatusSuccess(mp.scheduleBuild2(0, new UserCause()).get());
        FreeStyleProject p = createProject(mp.getName(), null, "", "", true, false, false, true);
        FreeStyleBuild b = p.scheduleBuild2(0, new UserCause()).get();
        rule.assertBuildStatusSuccess(b);
        assertFile(true, "ARCH=sparc/target/readme.txt", b);
        assertFile(true, "ARCH=sparc/target/sparc.out", b);
        assertFile(true, "ARCH=x86/target/readme.txt", b);
        assertFile(true, "ARCH=x86/target/x86.out", b);
    }

    private MavenModuleSet setupMavenJob() throws Exception {
        ToolInstallations.configureDefaultMaven();
        MavenModuleSet mp = createMavenProject();
        mp.setGoals("clean package");
        mp.setScm(new ExtractResourceSCM(getClass().getResource("maven-job.zip")));
        return mp;
    }

    private static final VersionNumber MAVEN_POM_CUTOFF = new VersionNumber("1.405");

    private static String pomName(String module, String version) {
        return module + '/' + version + '/' +
               (MAVEN_POM_CUTOFF.isNewerThan(Hudson.getVersion()) ? "pom.xml"
                                                                  : (module + '-' + version + ".pom"));
    }

    /** Test copying from a particular module of a maven job */
    @Test
    public void testMavenJob() throws Exception {
        MavenModuleSet mp = setupMavenJob();
        rule.assertBuildStatusSuccess(mp.scheduleBuild2(0, new UserCause()).get());
        FreeStyleProject p = createProject(mp.getName() + "/org.jvnet.hudson.main.test.multimod$moduleB",
                null, "", "", true, false, false, true);
        FreeStyleBuild b = p.scheduleBuild2(0, new UserCause()).get();
        String dir = "org.jvnet.hudson.main.test.multimod/";
        assertFile(true, dir + "moduleB/1.0-SNAPSHOT/moduleB-1.0-SNAPSHOT.jar", b);
        assertFile(true, dir + pomName("moduleB", "1.0-SNAPSHOT"), b);
    }

    /** Test copying all artifacts from a maven job */
    @Test
    public void testMavenAll() throws Exception {
        MavenModuleSet mp = setupMavenJob();
        rule.assertBuildStatusSuccess(mp.scheduleBuild2(0, new UserCause()).get());
        FreeStyleProject p = createProject(mp.getName(), null, "", "", true, false, false, true);
        FreeStyleBuild b = p.scheduleBuild2(0, new UserCause()).get();
        String dir = "org.jvnet.hudson.main.test.multimod/";
        assertFile(true, dir + "moduleA/1.0-SNAPSHOT/moduleA-1.0-SNAPSHOT.jar", b);
        assertFile(true, dir + pomName("moduleA", "1.0-SNAPSHOT"), b);
        assertFile(true, dir + "moduleB/1.0-SNAPSHOT/moduleB-1.0-SNAPSHOT.jar", b);
        assertFile(true, dir + pomName("moduleB", "1.0-SNAPSHOT"), b);
        assertFile(true, dir + "moduleC/1.0-SNAPSHOT/moduleC-1.0-SNAPSHOT.jar", b);
        assertFile(true, dir + pomName("moduleC", "1.0-SNAPSHOT"), b);
        // Test with filter
        p = createProject(mp.getName(), null, "**/*.jar", "", true, false, false, true);
        b = p.scheduleBuild2(0, new UserCause()).get();
        assertFile(true, dir + "moduleA/1.0-SNAPSHOT/moduleA-1.0-SNAPSHOT.jar", b);
        assertFile(false, dir + pomName("moduleA", "1.0-SNAPSHOT"), b);
        assertFile(true, dir + "moduleB/1.0-SNAPSHOT/moduleB-1.0-SNAPSHOT.jar", b);
        assertFile(false, dir + pomName("moduleB", "1.0-SNAPSHOT"), b);
        assertFile(true, dir + "moduleC/1.0-SNAPSHOT/moduleC-1.0-SNAPSHOT.jar", b);
        assertFile(false, dir + pomName("moduleC", "1.0-SNAPSHOT"), b);
    }

    /** Test copying from maven job where artifacts manually archived instead of automatic */
    @Test
    public void testMavenJobWithArchivePostBuildStep() throws Exception {
        MavenModuleSet mp = setupMavenJob();
        // Turn off automatic archiving and use a post-build step instead.
        // Artifacts will be stored with the parent build instead of the child module builds.
        mp.setIsArchivingDisabled(true);
        mp.getPublishersList().add(new ArtifactArchiver("moduleB/*.xml", "", false, false));
        rule.assertBuildStatusSuccess(mp.scheduleBuild2(0, new UserCause()).get());
        FreeStyleProject p = createProject(mp.getName(), null, "", "", true, false, false, true);
        FreeStyleBuild b = p.scheduleBuild2(0, new UserCause()).get();
        // Archived artifact should be copied:
        assertFile(true, "moduleB/pom.xml", b);
        // None of the maven artifacts should be archived or copied:
        String dir = "org.jvnet.hudson.main.test.multimod/";
        assertFile(false, dir + "moduleA/1.0-SNAPSHOT/moduleA-1.0-SNAPSHOT.jar", b);
        assertFile(false, dir + pomName("moduleA", "1.0-SNAPSHOT"), b);
        assertFile(false, dir + "moduleB/1.0-SNAPSHOT/moduleB-1.0-SNAPSHOT.jar", b);
        assertFile(false, dir + pomName("moduleB", "1.0-SNAPSHOT"), b);
        assertFile(false, dir + "moduleC/1.0-SNAPSHOT/moduleC-1.0-SNAPSHOT.jar", b);
        assertFile(false, dir + pomName("moduleC", "1.0-SNAPSHOT"), b);
    }

    /** Test copy from workspace instead of artifacts area */
    @Test
    public void testCopyFromWorkspace() throws Exception {
        FreeStyleProject other = rule.createFreeStyleProject(), p = rule.createFreeStyleProject();
        p.getBuildersList().add(CopyArtifactUtil.createCopyArtifact(other.getName(), null, new WorkspaceSelector(),
                "**/*.txt", "", true, false, true));
        // Run a build that places a file in the workspace, but does not archive anything
        other.getBuildersList().add(new ArtifactBuilder());
        rule.assertBuildStatusSuccess(other.scheduleBuild2(0, new UserCause()).get());
        FreeStyleBuild b = p.scheduleBuild2(0, new UserCause()).get();
        rule.assertBuildStatusSuccess(b);
        assertFile(true, "foo.txt", b);
        assertFile(true, "subfoo.txt", b);
        assertFile(false, "c.log", b);
    }

    /** Test copy from workspace containing default ant excludes */
    @Test
    public void testCopyFromWorkspaceWithDefaultExcludes() throws Exception {
        FreeStyleProject other = rule.createFreeStyleProject(), p = rule.createFreeStyleProject();
        p.getBuildersList().add(CopyArtifactUtil.createCopyArtifact(other.getName(), "", new WorkspaceSelector(),
                "", "", false, false));
        // Run a build that places a file in the workspace, but does not archive anything
        other.getBuildersList().add(new ArtifactBuilder());
        rule.assertBuildStatusSuccess(other.scheduleBuild2(0, new UserCause()).get());
        FreeStyleBuild b = p.scheduleBuild2(0, new UserCause()).get();
        rule.assertBuildStatusSuccess(b);
        assertFile(true, ".hg/defaultexclude.txt", b);
    }

    @Test
    public void testExcludes() throws Exception {
        FreeStyleProject other = rule.createFreeStyleProject(), p = rule.createFreeStyleProject();
        p.getBuildersList().add(CopyArtifactUtil.createCopyArtifact(other.getName(), "", new WorkspaceSelector(),
                "**", ".hg/**", "", false, false, true));
        // Run a build that places a file in the workspace, but does not archive anything
        other.getBuildersList().add(new ArtifactBuilder());
        rule.assertBuildStatusSuccess(other.scheduleBuild2(0, new UserCause()).get());
        FreeStyleBuild b = p.scheduleBuild2(0, new UserCause()).get();
        rule.assertBuildStatusSuccess(b);
        assertFile(true, "subdir/subfoo.txt", b);
        assertFile(false, ".hg/defaultexclude.txt", b);
    }

    @Test
    public void testCopyFromWorkspaceWithDefaultExcludesWithFlatten() throws Exception {
        FreeStyleProject other = rule.createFreeStyleProject(), p = rule.createFreeStyleProject();
        p.getBuildersList().add(CopyArtifactUtil.createCopyArtifact(other.getName(), "", new WorkspaceSelector(),
                "", "", true, false));
        // Run a build that places a file in the workspace, but does not archive anything
        other.getBuildersList().add(new ArtifactBuilder());
        rule.assertBuildStatusSuccess(other.scheduleBuild2(0, new UserCause()).get());
        FreeStyleBuild b = p.scheduleBuild2(0, new UserCause()).get();
        rule.assertBuildStatusSuccess(b);
        assertFile(true, "defaultexclude.txt", b);
    }

    @Test
    public void testExcludesWithFlatten() throws Exception {
        FreeStyleProject other = rule.createFreeStyleProject(), p = rule.createFreeStyleProject();
        p.getBuildersList().add(CopyArtifactUtil.createCopyArtifact(other.getName(), "", new WorkspaceSelector(),
                "**", ".hg/**", "", true, false, true));
        // Run a build that places a file in the workspace, but does not archive anything
        other.getBuildersList().add(new ArtifactBuilder());
        rule.assertBuildStatusSuccess(other.scheduleBuild2(0, new UserCause()).get());
        FreeStyleBuild b = p.scheduleBuild2(0, new UserCause()).get();
        rule.assertBuildStatusSuccess(b);
        assertFile(true, "subfoo.txt", b);
        assertFile(false, "defaultexclude.txt", b);
    }

    /** projectName in CopyArtifact build steps should be updated if a job is renamed */
    @Test
    public void testJobRename() throws Exception {
        FreeStyleProject other = rule.createFreeStyleProject(),
                         p = createProject(other.getName(), null, "", "", true, false, false, true);
        assertEquals("before", other.getName(),
                     ((CopyArtifact)p.getBuilders().get(0)).getProjectName());
        String newName = other.getName() + "-new";
        other.renameTo(newName);
        assertEquals("after", newName,
                     ((CopyArtifact)p.getBuilders().get(0)).getProjectName());

        // Test reference to a matrix configuration
        MatrixProject otherm = createMatrixProject(),
                      mp = createMatrixProject();
        mp.getBuildersList().add(CopyArtifactUtil.createCopyArtifact(otherm.getName(), "FOO=$FOO",
                new SavedBuildSelector(), "", "", false, false, true));
        assertEquals("before", otherm.getName(),
                     ((CopyArtifact)mp.getBuilders().get(0)).getProjectName());
        otherm.renameTo(newName = otherm.getName() + "-new");
        assertEquals("after", newName,
                     ((CopyArtifact)mp.getBuilders().get(0)).getProjectName());
    }

    @Test
    public void testSavedBuildSelector() throws Exception {
        FreeStyleProject other = createArtifactProject(),
                         p = rule.createFreeStyleProject();
        p.getBuildersList().add(CopyArtifactUtil.createCopyArtifact(other.getName(),
                null, new SavedBuildSelector(), "*.txt", "", false, false, true));
        FreeStyleBuild b = other.scheduleBuild2(0, new UserCause(),
                new ParametersAction(new StringParameterValue("FOO", "buildone"))).get();
        rule.assertBuildStatusSuccess(b);
        rule.assertBuildStatusSuccess(other.scheduleBuild2(0, new UserCause()));
        b.keepLog(true);
        b = p.scheduleBuild2(0, new UserCause()).get();
        rule.assertBuildStatusSuccess(b);
        assertFile(true, "foo.txt", b);
        assertFile(true, "buildone.txt", b);
        assertFile(false, "subdir/subfoo.txt", b);
    }

    @Test
    public void testSpecificBuildSelector() throws Exception {
        FreeStyleProject other = createArtifactProject(),
                         p = rule.createFreeStyleProject();
        SpecificBuildSelector sbs = new SpecificBuildSelector("1");
        assertEquals("1", sbs.getBuildNumber());
        p.getBuildersList().add(CopyArtifactUtil.createCopyArtifact(other.getName(), null, sbs, "*.txt", "", false, false, true));
        rule.assertBuildStatusSuccess(other.scheduleBuild2(0, new UserCause(),
                new ParametersAction(new StringParameterValue("FOO", "buildone"))).get());
        rule.assertBuildStatusSuccess(other.scheduleBuild2(0, new UserCause()));
        FreeStyleBuild b = p.scheduleBuild2(0, new UserCause()).get();
        rule.assertBuildStatusSuccess(b);
        assertFile(true, "foo.txt", b);
        assertFile(true, "buildone.txt", b);
        assertFile(false, "subdir/subfoo.txt", b);
    }

    @Test
    public void testSpecificBuildSelectorParameter() throws Exception {
        FreeStyleProject other = createArtifactProject(),
                         p = rule.createFreeStyleProject();
        p.getBuildersList().add(CopyArtifactUtil.createCopyArtifact(other.getName(),
                null, new SpecificBuildSelector("$BAR"), "*.txt", "", false, false, true));
        rule.assertBuildStatusSuccess(other.scheduleBuild2(0, new UserCause(),
                new ParametersAction(new StringParameterValue("FOO", "buildone"))).get());
        rule.assertBuildStatusSuccess(other.scheduleBuild2(0, new UserCause()));
        FreeStyleBuild b = p.scheduleBuild2(0, new UserCause(),
                new ParametersAction(new StringParameterValue("BAR", "1"))).get();
        rule.assertBuildStatusSuccess(b);
        assertFile(true, "foo.txt", b);
        assertFile(true, "buildone.txt", b);
        assertFile(false, "subdir/subfoo.txt", b);
    }

    @Test
    public void testParameterizedBuildSelector() throws Exception {
        FreeStyleProject other = createArtifactProject(),
                         p = rule.createFreeStyleProject();
        ParameterizedBuildSelector pbs = new ParameterizedBuildSelector("PBS");
        assertEquals("PBS", pbs.getParameterName());
        p.getBuildersList().add(CopyArtifactUtil.createCopyArtifact(other.getName(), null, pbs, "*.txt", "", false, false, true));
        FreeStyleBuild b = other.scheduleBuild2(0, new UserCause(),
                new ParametersAction(new StringParameterValue("FOO", "buildone"))).get();
        rule.assertBuildStatusSuccess(b);
        rule.assertBuildStatusSuccess(other.scheduleBuild2(0, new UserCause()));
        b.keepLog(true);
        b = p.scheduleBuild2(0, new UserCause(),
                new ParametersAction(new StringParameterValue("PBS", "<SavedBuildSelector/>"))).get();
        rule.assertBuildStatusSuccess(b);
        assertFile(true, "foo.txt", b);
        assertFile(true, "buildone.txt", b);
        assertFile(false, "subdir/subfoo.txt", b);
    }

    @Test
    public void testPermalinkBuildSelector() throws Exception {
        FreeStyleProject other = createArtifactProject(),
                         p = rule.createFreeStyleProject();
        p.getBuildersList().add(CopyArtifactUtil.createCopyArtifact(other.getName(),
                null, new PermalinkBuildSelector("lastStableBuild"), "*.txt", "", false, false, true));
        FreeStyleBuild b = other.scheduleBuild2(0, new UserCause(),
                new ParametersAction(new StringParameterValue("FOO", "buildone"))).get();
        rule.assertBuildStatusSuccess(b);
        other.getBuildersList().add(new UnstableBuilder());
        rule.assertBuildStatus(Result.UNSTABLE, other.scheduleBuild2(0, new UserCause()).get());
        b = p.scheduleBuild2(0, new UserCause()).get();
        rule.assertBuildStatusSuccess(b);
        assertFile(true, "foo.txt", b);
        assertFile(true, "buildone.txt", b);
        assertFile(false, "subdir/subfoo.txt", b);
        // Invalid permalink
        p.getBuildersList().replace(CopyArtifactUtil.createCopyArtifact(other.getName(),
                null, new PermalinkBuildSelector("fooBuild"), "*.txt", "", false, false, true));
        rule.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0, new UserCause()).get());
    }

    @Test
    public void testTriggeredBuildSelector() throws Exception {
        FreeStyleProject other = createArtifactProject(),
                         p = rule.createFreeStyleProject();
        p.getBuildersList().add(CopyArtifactUtil.createCopyArtifact(other.getName(),
                null, new TriggeredBuildSelector(false), "*.txt", "", false, false, true));
        other.getPublishersList().add(new BuildTrigger(p.getFullName(), false));
        rule.jenkins.rebuildDependencyGraph();
        rule.assertBuildStatusSuccess(other.scheduleBuild2(0, new UserCause()));
        // p#1 was triggered, now building.
        FreeStyleBuild b = p.getBuildByNumber(1);
        for (int i = 0; b == null && i < 1000; i++) { Thread.sleep(10); b = p.getBuildByNumber(1); }
        assertNotNull(b);
        while (b.isBuilding()) Thread.sleep(10);
        rule.assertBuildStatusSuccess(b);
        assertFile(true, "foo.txt", b);
        assertFile(false, "subdir/subfoo.txt", b);
        // Verify error if build not triggered by upstream job:
        rule.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0, new UserCause()).get());
        // test fallback
        
        //run a failing build to make sure the fallback selects the last successful build
        other.getPublishersList().clear();
        other.getBuildersList().add(new FailureBuilder());
        rule.assertBuildStatus(Result.FAILURE, other.scheduleBuild2(0, new UserCause()).get());
        
        p.getBuildersList().remove(CopyArtifact.class);
        p.getBuildersList().add(CopyArtifactUtil.createCopyArtifact(other.getName(),
                null, new TriggeredBuildSelector(true), "*.txt", "", false, false, true));
        rule.assertBuildStatus(Result.SUCCESS, p.scheduleBuild2(0, new UserCause()).get());
    }

    @Test
    public void testTriggeredBuildSelectorWithParentOfParent() throws Exception {
        FreeStyleProject grandparent = createArtifactProject(),
                         parent = rule.createFreeStyleProject(),
                         p = rule.createFreeStyleProject();
        p.getBuildersList().add(CopyArtifactUtil.createCopyArtifact(grandparent.getName(), null,
                new TriggeredBuildSelector(false), "*.txt", "", false, false, true));
        parent.getPublishersList().add(new BuildTrigger(p.getFullName(), false));
        grandparent.getPublishersList().add(new BuildTrigger(parent.getFullName(), false));
        rule.jenkins.rebuildDependencyGraph();
        rule.assertBuildStatusSuccess(grandparent.scheduleBuild2(0, new UserCause()));
        // parent#1 was triggered
        FreeStyleBuild b = parent.getBuildByNumber(1);
        for (int i = 0; b == null && i < 2000; i++) { Thread.sleep(10); b = p.getBuildByNumber(1); }
        assertNotNull(b);
        while (b.isBuilding()) Thread.sleep(10);
        rule.assertBuildStatusSuccess(b);
        // p#1 was triggered, now building.
        b = p.getBuildByNumber(1);
        for (int i = 0; b == null && i < 2000; i++) { Thread.sleep(10); b = p.getBuildByNumber(1); }
        assertNotNull(b);
        while (b.isBuilding()) Thread.sleep(10);
        rule.assertBuildStatusSuccess(b);
        assertFile(true, "foo.txt", b);
        assertFile(false, "subdir/subfoo.txt", b);
        // Verify error if build not triggered by upstream job:
        rule.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0, new UserCause()).get());
        // test fallback
        
        //run a failing build to make sure the fallback selects the last successful build
        grandparent.getPublishersList().clear();
        grandparent.getBuildersList().add(new FailureBuilder());
        rule.assertBuildStatus(Result.FAILURE, grandparent.scheduleBuild2(0, new UserCause()).get());
        
        p.getBuildersList().remove(CopyArtifact.class);
        p.getBuildersList().add(CopyArtifactUtil.createCopyArtifact(grandparent.getName(), null,
                new TriggeredBuildSelector(true), "*.txt", "", false, false, true));
        rule.assertBuildStatus(Result.SUCCESS, p.scheduleBuild2(0, new UserCause()).get());
    }

    /**
     * When copying from a particular matrix configuration, the upstream project
     * is the matrix parent.
     */
    @Test
    public void testTriggeredBuildSelectorFromMatrix() throws Exception {
        MatrixProject other = createMatrixArtifactProject();
        FreeStyleProject p = rule.createFreeStyleProject();
        p.getBuildersList().add(CopyArtifactUtil.createCopyArtifact(other.getName() + "/FOO=two",
                null, new TriggeredBuildSelector(false), "*.txt", "", false, false, true));
        other.getPublishersList().add(new BuildTrigger(p.getFullName(), false));
        rule.jenkins.rebuildDependencyGraph();
        rule.assertBuildStatusSuccess(other.scheduleBuild2(0, new UserCause()).get());
        // p#1 was triggered, now building.
        FreeStyleBuild b = p.getBuildByNumber(1);
        for (int i = 0; b == null && i < 1000; i++) { Thread.sleep(10); b = p.getBuildByNumber(1); }
        assertNotNull(b);
        while (b.isBuilding()) Thread.sleep(10);
        rule.assertBuildStatusSuccess(b);
        assertFile(true, "foo.txt", b);
        assertFile(true, "two.txt", b);
    }

    /**
     * When copying to a matrix job, need to check the upstream cause of the
     * matrix parent.
     */
    @Test
    public void testTriggeredBuildSelectorToMatrix() throws Exception {
        FreeStyleProject other = createArtifactProject();
        MatrixProject p = createMatrixProject();
        p.setAxes(new AxisList(new Axis("FOO", "one", "two")));
        p.getBuildersList().add(CopyArtifactUtil.createCopyArtifact(other.getName(),
                null, new TriggeredBuildSelector(false), "*.txt", "", false, false, true));
        other.getPublishersList().add(new BuildTrigger(p.getFullName(), false));
        rule.jenkins.rebuildDependencyGraph();
        rule.assertBuildStatusSuccess(other.scheduleBuild2(0, new UserCause()).get());
        // p#1 was triggered, now building.
        MatrixBuild b = p.getBuildByNumber(1);
        for (int i = 0; b == null && i < 1000; i++) { Thread.sleep(10); b = p.getBuildByNumber(1); }
        assertNotNull(b);
        while (b.isBuilding()) Thread.sleep(10);
        rule.assertBuildStatusSuccess(b);
        MatrixRun r = b.getRuns().get(0);
        assertFile(true, "foo.txt", r);
    }

    @Test
    public void testFlatten() throws Exception {
        FreeStyleProject other = createArtifactProject(),
                         p = createProject(other.getName(), null, "", "newdir", false, true, false, true);
        rule.assertBuildStatusSuccess(other.scheduleBuild2(0, new UserCause()).get());
        FreeStyleBuild b = p.scheduleBuild2(0, new UserCause()).get();
        rule.assertBuildStatusSuccess(b);
        assertFile(true, "newdir/foo.txt", b);
        assertFile(true, "newdir/subfoo.txt", b);
        assertFile(true, "newdir/c.log", b);
    }

    @Test
    public void testOptional_MissingProject() throws Exception {
        // Missing project still fails even when copy is optional
        FreeStyleProject p = createProject("invalid", null, "", "", false, false, true, true);
        rule.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0, new UserCause()).get());
    }

    @Test
    public void testOptional_MissingBuild() throws Exception {
        FreeStyleProject other = rule.createFreeStyleProject(),
                         p = createProject(other.getName(), null, "", "", false, false, true, true);
        rule.assertBuildStatusSuccess(p.scheduleBuild2(0, new UserCause()).get());
    }

    @Test
    public void testOptional_MissingArtifact() throws Exception {
        FreeStyleProject other = rule.createFreeStyleProject(),
                         p = createProject(other.getName(), null, "*.txt", "", false, false, true, true);
        rule.assertBuildStatusSuccess(other.scheduleBuild2(0, new UserCause()).get());
        rule.assertBuildStatusSuccess(p.scheduleBuild2(0, new UserCause()).get());
    }

    /**
     * Test that a user is prevented from bypassing permissions on other jobs when configuring
     * a copyartifact build step.
     */
    @Test
    public void testPermission() throws Exception {
        // any users can be authenticated with the password same to the user id.
        rule.jenkins.setSecurityRealm(rule.createDummySecurityRealm());
        ProjectMatrixAuthorizationStrategy pmas = new ProjectMatrixAuthorizationStrategy();
        pmas.add(Jenkins.READ, Jenkins.ANONYMOUS.getName());
        pmas.add(Jenkins.READ, "joe");
        rule.jenkins.setAuthorizationStrategy(pmas);
        
        // only joe can access project "src"
        FreeStyleProject src = rule.createFreeStyleProject();
        {
            Map<Permission, Set<String>> auths = new HashMap<Permission, Set<String>>();
            auths.put(Item.READ, Sets.newHashSet("joe"));
            src.addProperty(new AuthorizationMatrixProperty(auths));
        }
        
        // test access from anonymous
        {
            FreeStyleProject dest = rule.createFreeStyleProject();
            dest.getBuildersList().add(CopyArtifactUtil.createCopyArtifact(
                    src.getName(),
                    "",
                    new StatusBuildSelector(true),
                    "",
                    "",
                    false,
                    false,
                    true
            ));
            Map<Permission, Set<String>> auths = new HashMap<Permission, Set<String>>();
            auths.put(Item.READ, Sets.newHashSet(Jenkins.ANONYMOUS.getName()));
            auths.put(Item.CONFIGURE, Sets.newHashSet(Jenkins.ANONYMOUS.getName()));
            dest.addProperty(new AuthorizationMatrixProperty(auths));
            
            WebClient wc = rule.createWebClient();
            try {
                wc.getPage(src);
                fail("Job should not be accessible to anonymous");
            } catch(FailingHttpStatusCodeException e) {
                assertEquals("Job should not be accessible to anonymous", 404, e.getStatusCode());
            }

            rule.submit(wc.getPage(dest, "configure").getFormByName("config"));
            
            dest = rule.jenkins.getItemByFullName(dest.getFullName(), FreeStyleProject.class);
            CopyArtifact ca = dest.getBuildersList().getAll(CopyArtifact.class).get(0);
            assertEquals("Should ignore/clear value for inaccessible project", "", ca.getProjectName());
        }
        
        // test access from joe
        {
            FreeStyleProject dest = rule.createFreeStyleProject();
            dest.getBuildersList().add(CopyArtifactUtil.createCopyArtifact(
                    src.getName(),
                    "",
                    new StatusBuildSelector(true),
                    "",
                    "",
                    false,
                    false,
                    true
            ));
            Map<Permission, Set<String>> auths = new HashMap<Permission, Set<String>>();
            auths.put(Item.READ, Sets.newHashSet("joe"));
            auths.put(Item.CONFIGURE, Sets.newHashSet("joe"));
            dest.addProperty(new AuthorizationMatrixProperty(auths));
            
            WebClient wc = rule.createWebClient();
            wc.login("joe", "joe");
            assertNotNull(wc.getPage(src));

            rule.submit(wc.getPage(dest, "configure").getFormByName("config"));
            
            dest = rule.jenkins.getItemByFullName(dest.getFullName(), FreeStyleProject.class);
            CopyArtifact ca = dest.getBuildersList().getAll(CopyArtifact.class).get(0);
            assertEquals("Should ignore/clear value for inaccessible project", src.getName(), ca.getProjectName());
        }
    }

    /**
     * When the source project name is parameterized, cannot check at configure time whether
     * the project is accessible.  In this case, permission check is done when the build runs.
     * Only jobs accessible to all authenticated users are allowed.
     */
    @LocalData
    @Test
    public void testPermissionWhenParameterized() throws Exception {
        FreeStyleProject p = createProject("test$JOB", null, "", "", false, false, false, true);
        // Build step should succeed when this parameter expands to a job accessible
        // to authenticated users (even if triggered by anonymous, as in this case):
        SecurityContextHolder.clearContext();
        FreeStyleBuild b = p.scheduleBuild2(0, new UserCause(),
                new ParametersAction(new StringParameterValue("JOB", "Job2"))).get();
        assertFile(true, "foo2.txt", b);
        rule.assertBuildStatusSuccess(b);
        // Build step should fail for a job not accessible to all authenticated users,
        // even when accessible to the user starting the job, as in this case:
        SecurityContext old = ACL.impersonate(
                new UsernamePasswordAuthenticationToken("joe","joe"));
        try {
        b = p.scheduleBuild2(0, new UserCause(),
                new ParametersAction(new StringParameterValue("JOB", "Job"))).get();
        assertFile(false, "foo.txt", b);
            rule.assertBuildStatus(Result.FAILURE, b);
        } finally {
            SecurityContextHolder.setContext(old);
        }
    }

    @LocalData
    @Test
    public void testPermissionWhenParameterizedForMatrixConfig() throws Exception {
        // This test fails before Jenkins 1.406
        if (new VersionNumber("1.406").isNewerThan(Hudson.getVersion())) return; // Skip

        FreeStyleProject p = createProject("testMatrix/FOO=$FOO", null, "", "", false, false, false, true);
        // Build step should succeed when this parameter expands to a job accessible to
        // authenticated users, even when selecting a single matrix config, not the parent job:
        FreeStyleBuild b = p.scheduleBuild2(0, new UserCause(),
                new ParametersAction(new StringParameterValue("FOO", "foo"))).get();
        assertFile(true, "foo.txt", b);
        rule.assertBuildStatusSuccess(b);
    }

    @LocalData
    @Test
    public void testPermissionWhenParameterizedForMavenModule() throws Exception {
        // This test fails before Jenkins 1.406
        if (new VersionNumber("1.406").isNewerThan(Hudson.getVersion())) return; // Skip

        MavenModuleSet mp = setupMavenJob();
        mp.addProperty(new AuthorizationMatrixProperty(
                Collections.singletonMap(Item.READ, Collections.singleton("authenticated"))));
        rule.assertBuildStatusSuccess(mp.scheduleBuild2(0, new UserCause()).get());
        FreeStyleProject p = createProject(mp.getName() + "/org.jvnet.hudson.main.test.multimod$FOO",
                                           null, "", "", false, false, false, true);
        // Build step should succeed when this parameter expands to a job accessible to
        // authenticated users, even when selecting a single maven module, not the parent job:
        FreeStyleBuild b = p.scheduleBuild2(0, new UserCause(),
                new ParametersAction(new StringParameterValue("FOO", "$moduleA"))).get();
        String dir = "org.jvnet.hudson.main.test.multimod/";
        assertFile(true, dir + "moduleA/1.0-SNAPSHOT/moduleA-1.0-SNAPSHOT.jar", b);
        assertFile(true, dir + pomName("moduleA", "1.0-SNAPSHOT"), b);
        assertFile(false, dir + "moduleB/1.0-SNAPSHOT/moduleB-1.0-SNAPSHOT.jar", b);
        assertFile(false, dir + pomName("moduleB", "1.0-SNAPSHOT"), b);
        rule.assertBuildStatusSuccess(b);
    }

    /**
     * Test that info about selected builds is added into the environment for later build steps.
     */
    @Test
    public void testEnvData() throws Exception {
        // Also test conversion of job name to env var name, only keeping letters:
        FreeStyleProject other = createArtifactProject("My (Test) Job"),
                 p = createProject(other.getName(), null, "", "", false, false, false, true);
        CaptureEnvironmentBuilder envStep = new CaptureEnvironmentBuilder();
        p.getBuildersList().add(envStep);
        // Bump up the build number a bit:
        for (int i = 0; i < 3; i++) other.assignBuildNumber();
        rule.assertBuildStatusSuccess(other.scheduleBuild2(0, new UserCause()).get());
        FreeStyleBuild b = p.scheduleBuild2(0, new UserCause()).get();
        rule.assertBuildStatusSuccess(b);
        assertEquals("4", envStep.getEnvVars().get("COPYARTIFACT_BUILD_NUMBER_MY_TEST_JOB"));
    }

    @Issue("JENKINS-16028")
    @Test
    public void testEnvDataInMavenProject() throws Exception {
        FreeStyleProject upstream = rule.createFreeStyleProject("upstream");
        upstream.getBuildersList().add(new FileWriteBuilder("artifact.txt", "foobar"));
        upstream.getPublishersList().add(new ArtifactArchiver("**/*", "", false, false));
        FreeStyleBuild upstreamBuild = upstream.scheduleBuild2(0).get();
        rule.assertBuildStatusSuccess(upstreamBuild);
        
        MavenModuleSet downstream = setupMavenJob();
        downstream.getPrebuilders().add(CopyArtifactUtil.createCopyArtifact(
                "upstream",
                "",
                new SpecificBuildSelector(Integer.toString(upstreamBuild.getNumber())),
                "**/*",
                "",
                "",
                false,
                false,
                false
        ));
        CaptureEnvironmentBuilder envStep = new CaptureEnvironmentBuilder();
        downstream.getPrebuilders().add(envStep);
        
        MavenModuleSetBuild downstreamBuild = downstream.scheduleBuild2(0).get();
        rule.assertBuildStatusSuccess(downstreamBuild);
        assertFile(true, "artifact.txt", downstreamBuild);
        assertEquals(
                Integer.toString(upstreamBuild.getNumber()),
                envStep.getEnvVars().get("COPYARTIFACT_BUILD_NUMBER_UPSTREAM")
        );
    }
    
    @Issue("JENKINS-18762")
    @Test
    public void testEnvDataWrapped() throws Exception {
        FreeStyleProject upstream = rule.createFreeStyleProject("upstream");
        upstream.getBuildersList().add(new FileWriteBuilder("artifact.txt", "foobar"));
        upstream.getPublishersList().add(new ArtifactArchiver("**/*", "", false, false));
        FreeStyleBuild upstreamBuild = upstream.scheduleBuild2(0).get();
        rule.assertBuildStatusSuccess(upstreamBuild);
        
        FreeStyleProject downstream = rule.createFreeStyleProject();
        downstream.getBuildersList().add(new WrapperBuilder(CopyArtifactUtil.createCopyArtifact(
                "upstream",
                "",
                new SpecificBuildSelector(Integer.toString(upstreamBuild.getNumber())),
                "**/*",
                "",
                "",
                false,
                false,
                false
        )));
        CaptureEnvironmentBuilder envStep = new CaptureEnvironmentBuilder();
        downstream.getBuildersList().add(envStep);
        
        FreeStyleBuild downstreamBuild = downstream.scheduleBuild2(0).get();
        rule.assertBuildStatusSuccess(downstreamBuild);
        assertFile(true, "artifact.txt", downstreamBuild);
        assertEquals(
                Integer.toString(upstreamBuild.getNumber()),
                envStep.getEnvVars().get("COPYARTIFACT_BUILD_NUMBER_UPSTREAM")
        );
    }
    
    /**
     * Test filtering on parameters, ie. last stable build with parameter FOO=bar.
     */
    @Test
    public void testFilterByParameters() throws Exception {
        FreeStyleProject other = createArtifactProject("Foo job");
        other.addProperty(new ParametersDefinitionProperty(
                new StringParameterDefinition("FOO", ""),
                new BooleanParameterDefinition("BAR", false, ""),
                new ChoiceParameterDefinition("BAZ", new String[] { "foo", "bar", "baz" }, "")));
        // #1: FOO=foo BAR=false BAZ=baz
        rule.assertBuildStatusSuccess(other.scheduleBuild2(0, new UserCause(), new ParametersAction(
                new StringParameterValue("FOO", "foo"),
                new BooleanParameterValue("BAR", false),
                new StringParameterValue("BAZ", "baz"))).get());
        // #2: FOO=bar BAR=true BAZ=foo
        rule.assertBuildStatusSuccess(other.scheduleBuild2(0, new UserCause(), new ParametersAction(
                new StringParameterValue("FOO", "bar"),
                new BooleanParameterValue("BAR", true),
                new StringParameterValue("BAZ", "foo"))).get());
        // #3: FOO=foo BAR=true BAZ=bar
        rule.assertBuildStatusSuccess(other.scheduleBuild2(0, new UserCause(), new ParametersAction(
                new StringParameterValue("FOO", "foo"),
                new BooleanParameterValue("BAR", true),
                new StringParameterValue("BAZ", "bar"))).get());

        FreeStyleProject p = createProject(other.getName(), "FOO=bar", "*.txt", "", true, false, false, true);
        CaptureEnvironmentBuilder envStep = new CaptureEnvironmentBuilder();
        p.getBuildersList().add(envStep);
        FreeStyleBuild b = p.scheduleBuild2(0, new UserCause()).get();
        rule.assertBuildStatusSuccess(b);
        assertEquals("2", envStep.getEnvVars().get("COPYARTIFACT_BUILD_NUMBER_FOO_JOB"));

        p = createProject(other.getName(), "BAR=false", "*.txt", "", true, false, false, true);
        p.getBuildersList().add(envStep);
        b = p.scheduleBuild2(0, new UserCause()).get();
        rule.assertBuildStatusSuccess(b);
        assertEquals("1", envStep.getEnvVars().get("COPYARTIFACT_BUILD_NUMBER_FOO_JOB"));

        p = createProject(other.getName(), "BAZ=foo,BAR=true", "*.txt", "", true, false, false, true);
        p.getBuildersList().add(envStep);
        b = p.scheduleBuild2(0, new UserCause()).get();
        rule.assertBuildStatusSuccess(b);
        assertEquals("2", envStep.getEnvVars().get("COPYARTIFACT_BUILD_NUMBER_FOO_JOB"));

        p = createProject(other.getName(), "FOO=foo,BAR=false,BAZ=baz", "*.txt", "", true, false, false, true);
        p.getBuildersList().add(envStep);
        b = p.scheduleBuild2(0, new UserCause()).get();
        rule.assertBuildStatusSuccess(b);
        assertEquals("1", envStep.getEnvVars().get("COPYARTIFACT_BUILD_NUMBER_FOO_JOB"));

        p = createProject(other.getName(), "BAZ=bar,FOO=bogus", "*.txt", "", true, false, false, true);
        b = p.scheduleBuild2(0, new UserCause()).get();
        rule.assertBuildStatus(Result.FAILURE, b);

        // Test matching other build variables besides parameters
        p = createProject(other.getName(), "BUILD_NUMBER=2", "*.txt", "", true, false, false, true);
        p.getBuildersList().add(envStep);
        b = p.scheduleBuild2(0, new UserCause()).get();
        rule.assertBuildStatusSuccess(b);
        assertEquals("2", envStep.getEnvVars().get("COPYARTIFACT_BUILD_NUMBER_FOO_JOB"));
        
        // Test coverage for EnvAction
        boolean ok = false;
        for (Action a : b.getActions()) {
            if ("hudson.plugins.copyartifact.CopyArtifact$EnvAction".equals(a.getClass().getName())) {
                assertNull(a.getIconFileName());
                assertNull(a.getDisplayName());
                assertNull(a.getUrlName());
                ok = true;
            }
        }
        assertTrue(ok);
    }

    @Test
    public void testFilterByMetaParameters() throws Exception {
        FreeStyleProject other = createArtifactProject("Foo job");
        other.addProperty(new ParametersDefinitionProperty(new BooleanParameterDefinition("BAR", false, "")));
        rule.assertBuildStatusSuccess(other.scheduleBuild2(0, new UserCause(), new ParametersAction(new BooleanParameterValue("BAR", false))).get());
        rule.assertBuildStatusSuccess(other.scheduleBuild2(0, new UserCause(), new ParametersAction(new BooleanParameterValue("BAR", true))).get());
        rule.assertBuildStatusSuccess(other.scheduleBuild2(0, new UserCause(), new ParametersAction(new BooleanParameterValue("BAR", false))).get());
        FreeStyleProject p = createProject(other.getName(), "$VAR=true", "*.txt", "", true, false, false, true);
        p.addProperty(new ParametersDefinitionProperty(new StringParameterDefinition("VAR", "")));
        CaptureEnvironmentBuilder envStep = new CaptureEnvironmentBuilder();
        p.getBuildersList().add(envStep);
        FreeStyleBuild b = p.scheduleBuild2(0, new UserCause(), new ParametersAction(new StringParameterValue("VAR", "BAR"))).get();
        rule.assertBuildStatusSuccess(b);
        assertEquals("2", envStep.getEnvVars().get("COPYARTIFACT_BUILD_NUMBER_FOO_JOB"));
    }

    @Test
    public void testSavedBuildSelectorWithParameterFilter() throws Exception {
        FreeStyleProject other = createArtifactProject(),
                         p = rule.createFreeStyleProject();
        other.addProperty(new ParametersDefinitionProperty(new StringParameterDefinition("FOO", "")));
        FreeStyleBuild b = other.scheduleBuild2(0, new UserCause(),
                new ParametersAction(new StringParameterValue("FOO", "buildone"))).get();
        rule.assertBuildStatusSuccess(b);
        b.keepLog(true);
        p.getBuildersList().add(CopyArtifactUtil.createCopyArtifact(other.getName(), "FOO=buildone",
                new SavedBuildSelector(), "*.txt", "", false, false, true));
        rule.assertBuildStatusSuccess(b = other.scheduleBuild2(0, new UserCause()).get());
        b.keepLog(true); // Keep #2 too, but it doesn't have FOO=buildone so should not be selected
        rule.assertBuildStatusSuccess(b = p.scheduleBuild2(0, new UserCause()).get());
        assertFile(true, "foo.txt", b);
        assertFile(true, "buildone.txt", b);
        assertFile(false, "subdir/subfoo.txt", b);
    }

    // Verify build fails if given build# does not match params
    @Test
    public void testSpecificBuildSelectorWithParameterFilter() throws Exception {
        FreeStyleProject other = createArtifactProject(),
                         p = rule.createFreeStyleProject();
        other.addProperty(new ParametersDefinitionProperty(new StringParameterDefinition("FOO", "")));
        p.getBuildersList().add(CopyArtifactUtil.createCopyArtifact(other.getName(), "FOO=bogus",
                new SpecificBuildSelector("1"), "*.txt", "", false, false, true));
        rule.assertBuildStatusSuccess(other.scheduleBuild2(0, new UserCause(),
                new ParametersAction(new StringParameterValue("FOO", "foo"))).get());
        rule.assertBuildStatusSuccess(other.scheduleBuild2(0, new UserCause()));
        FreeStyleBuild b = p.scheduleBuild2(0, new UserCause()).get();
        rule.assertBuildStatus(Result.FAILURE, b);
        assertFile(false, "foo.txt", b);
    }

    // Verify BuildSelector defaults to false
    @Test
    public void testBuildSelectorDefault() {
        assertFalse(new BuildSelector() { }.isSelectable(null, null));
    }

    // Test field getters
    @Test
    public void testFields() throws Exception {
        FreeStyleProject p = rule.createFreeStyleProject();
        CopyArtifact ca = CopyArtifactUtil.createCopyArtifact(p.getFullName(), null, new SavedBuildSelector(), "filter", "target", false, true, true);
        assertEquals(p.getFullName(), ca.getProjectName());
        assertSame(SavedBuildSelector.class, ca.getBuildSelector().getClass());
        assertEquals("filter", ca.getFilter());
        assertEquals("target", ca.getTarget());
        assertFalse(ca.isFlatten());
        assertTrue(ca.isOptional());
        ca = CopyArtifactUtil.createCopyArtifact("foo", null, null, null, null, true, false, true);
        assertTrue(ca.isFlatten());
        assertFalse(ca.isOptional());
    }

    @Test
    public void testFieldValidation() throws Exception {
        FreeStyleProject p = rule.createFreeStyleProject();
        CopyArtifact.DescriptorImpl descriptor = rule.jenkins.getDescriptorByType(CopyArtifact.DescriptorImpl.class);
        assertNotNull(descriptor);
        // Valid value
        assertSame(FormValidation.Kind.OK, descriptor.doCheckProjectName(p, p.getFullName()).kind);
        // Empty value
        assertSame(FormValidation.Kind.ERROR, descriptor.doCheckProjectName(p, "").kind);
        // Parameterized value
        assertSame(FormValidation.Kind.WARNING, descriptor.doCheckProjectName(p, "$FOO").kind);
        // Just returns OK if no permission
        rule.jenkins.setAuthorizationStrategy(new GlobalMatrixAuthorizationStrategy());
        SecurityContextHolder.clearContext();
        assertSame(FormValidation.Kind.OK, descriptor.doCheckProjectName(p, "").kind);
        // Other descriptor methods
        assertTrue(descriptor.isApplicable(null));
        assertTrue(descriptor.getDisplayName().length() > 0);
    }

    @LocalData
    @Test
    public void testProjectNameSplit() throws Exception {
        FreeStyleProject copier = Jenkins.getInstance().getItemByFullName("copier", FreeStyleProject.class);
        assertNotNull(copier);
        String configXml = copier.getConfigFile().asString();
        assertFalse(configXml, configXml.contains("<projectName>"));
        assertTrue(configXml, configXml.contains("<project>plain</project>"));
        assertTrue(configXml, configXml.contains("<project>parameterized</project>"));
        assertTrue(configXml, configXml.contains("<parameters>good=true</parameters>"));
        assertTrue(configXml, configXml.contains("<project>matrix/which=two</project>"));
        
        MatrixProject matrixCopier = Jenkins.getInstance().getItemByFullName("matrix-copier", MatrixProject.class);
        assertNotNull(matrixCopier);
        configXml = matrixCopier.getConfigFile().asString();
        assertFalse(configXml, configXml.contains("<projectName>"));
        // When a project is specified with a variable, it is split improperly.
        assertTrue(configXml, configXml.contains("<project>matrix</project>"));
        assertTrue(configXml, configXml.contains("<parameters>which=${which}</parameters>"));
    }

    // A builder wrapping another builder.
    public static class WrapBuilder extends Builder {
        private Builder wrappedBuilder;
        
        @Override
        public boolean perform(AbstractBuild<?, ?> build, Launcher launcher,
                BuildListener listener) throws InterruptedException, IOException {
            return wrappedBuilder.perform(build, launcher, listener);
        }
        
        public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {
            @Override
            public boolean isApplicable(Class<? extends AbstractProject> jobType) {
                return true;
            }
            
            @Override
            public String getDisplayName() {
                return "WrapBuilder";
            }
        }
    }
    
    @LocalData
    @Test
    public void testWrappedCopierProjectNameSplit() throws Exception {
        // Project "copier" is configured with CopyArtifact wrapped with WrapBuilder.
        // This causes failure of upgrading on loaded.
        // Upgrading is performed when build is triggered.
        FreeStyleProject copier = Jenkins.getInstance().getItemByFullName("copier", FreeStyleProject.class);
        assertNotNull(copier);
        String configXml = copier.getConfigFile().asString();
        // not upgraded on loaded
        assertTrue(configXml, configXml.contains("<projectName>plain</projectName>"));
        
        // upgraded when a build is triggered.
        FreeStyleBuild b = copier.scheduleBuild2(0).get();
        rule.assertBuildStatusSuccess(b);
        FilePath fileToTest = b.getWorkspace().child("from-plain/tag.txt");
        assertTrue(fileToTest.exists());
        assertEquals("jenkins-plain-2\n", fileToTest.readToString());
        
        configXml = copier.getConfigFile().asString();
        assertFalse(configXml, configXml.contains("<projectName>"));
        assertTrue(configXml, configXml.contains("<project>plain</project>"));
    }
    
    @Issue("JENKINS-17447")
    @LocalData
    @Test
    public void testRenameBeforeProjectNameSplit() throws Exception {
        rule.jenkins.getItemByFullName("old", FreeStyleProject.class).renameTo("new");
        FreeStyleProject nue = rule.jenkins.getItemByFullName("new", FreeStyleProject.class);
        rule.assertBuildStatusSuccess(nue.scheduleBuild2(0));
        FreeStyleProject copier = rule.jenkins.getItemByFullName("copier", FreeStyleProject.class);
        rule.assertBuildStatusSuccess(copier.scheduleBuild2(0));
        assertEquals("jenkins-new-1\n", copier.getLastBuild().getWorkspace().child("stuff").readToString());
    }

    @Test
    public void testRelative() throws Exception {
        MockFolder folder = rule.jenkins.createProject(MockFolder.class, "folder");
        FreeStyleProject other = folder.createProject(FreeStyleProject.class, "foo");
        other.getBuildersList().add(new ArtifactBuilder());
        other.getPublishersList().add(new ArtifactArchiver("**", "", false, false));

        FreeStyleProject p = createProject("folder/foo", null, "", "", true, false, false, true);

        rule.assertBuildStatusSuccess(other.scheduleBuild2(0, new UserCause()).get());
        FreeStyleBuild b = p.scheduleBuild2(0, new UserCause()).get();
        rule.assertBuildStatusSuccess(b);
        assertFile(true, "foo.txt", b);
    }

    @Test
    public void testAbsolute() throws Exception {
        MockFolder folder = rule.jenkins.createProject(MockFolder.class, "folder");
        FreeStyleProject other = folder.createProject(FreeStyleProject.class, "foo");
        other.getBuildersList().add(new ArtifactBuilder());
        other.getPublishersList().add(new ArtifactArchiver("**", "", false, false));

        FreeStyleProject p = createProject("/folder/foo", null, "", "", true, false, false, true);

        rule.assertBuildStatusSuccess(other.scheduleBuild2(0, new UserCause()).get());
        FreeStyleBuild b = p.scheduleBuild2(0, new UserCause()).get();
        rule.assertBuildStatusSuccess(b);
        assertFile(true, "foo.txt", b);
    }

    @Issue("JENKINS-19833")
    @Test
    public void testMostlyAbsolute() throws Exception {
        MockFolder folder = rule.jenkins.createProject(MockFolder.class, "folder");
        FreeStyleProject other = folder.createProject(FreeStyleProject.class, "foo");
        other.getBuildersList().add(new ArtifactBuilder());
        other.getPublishersList().add(new ArtifactArchiver("**", "", false, false));

        MockFolder folder2 = rule.jenkins.createProject(MockFolder.class, "other");
        FreeStyleProject p = folder2.createProject(FreeStyleProject.class, "bar");

        // "folder/foo" should be resolved as "/folder/foo" even from "/other/bar", for backward compatibility
        p.getBuildersList().add(CopyArtifactUtil.createCopyArtifact("folder/foo", null, new StatusBuildSelector(true), "", "", false, false, true));

        rule.assertBuildStatusSuccess(other.scheduleBuild2(0, new UserCause()).get());
        FreeStyleBuild b = p.scheduleBuild2(0, new UserCause()).get();
        rule.assertBuildStatusSuccess(b);
        assertFile(true, "foo.txt", b);
    }

    @Test
    public void testAbsoluteFromFolder() throws Exception {
        FreeStyleProject other = rule.jenkins.createProject(FreeStyleProject.class, "foo");
        other.getBuildersList().add(new ArtifactBuilder());
        other.getPublishersList().add(new ArtifactArchiver("**", "", false, false));

        MockFolder folder = rule.jenkins.createProject(MockFolder.class, "folder");
        FreeStyleProject p = folder.createProject(FreeStyleProject.class, "bar");
        p.getBuildersList().add(CopyArtifactUtil.createCopyArtifact("/foo", null, new StatusBuildSelector(true), "", "", false, false, true));

        rule.assertBuildStatusSuccess(other.scheduleBuild2(0, new UserCause()).get());
        FreeStyleBuild b = p.scheduleBuild2(0, new UserCause()).get();
        rule.assertBuildStatusSuccess(b);
        assertFile(true, "foo.txt", b);
    }

    @Test
    public void testRelativeFromFolder() throws Exception {
        FreeStyleProject other = rule.jenkins.createProject(FreeStyleProject.class, "foo");
        other.getBuildersList().add(new ArtifactBuilder());
        other.getPublishersList().add(new ArtifactArchiver("**", "", false, false));

        MockFolder folder = rule.jenkins.createProject(MockFolder.class, "folder");
        FreeStyleProject p = folder.createProject(FreeStyleProject.class, "bar");
        p.getBuildersList().add(CopyArtifactUtil.createCopyArtifact("../foo", null, new StatusBuildSelector(true), "", "", false, false, true));

        rule.assertBuildStatusSuccess(other.scheduleBuild2(0, new UserCause()).get());
        FreeStyleBuild b = p.scheduleBuild2(0, new UserCause()).get();
        rule.assertBuildStatusSuccess(b);
        assertFile(true, "foo.txt", b);
    }

    @Test
    public void testSameFolder() throws Exception {
        Folder folder = rule.jenkins.createProject(Folder.class, "folder");
        FreeStyleProject src = folder.createProject(FreeStyleProject.class, "src");
        src.getBuildersList().add(new ArtifactBuilder());
        src.getPublishersList().add(new ArtifactArchiver("**", "", false, false));
        rule.assertBuildStatusSuccess(src.scheduleBuild2(0));
        
        FreeStyleProject dest = folder.createProject(FreeStyleProject.class, "dest");
        dest.getBuildersList().add(CopyArtifactUtil.createCopyArtifact(src.getName(), null, new StatusBuildSelector(true), "", "", false, false, true));
        FreeStyleBuild b = dest.scheduleBuild2(0).get();
        rule.assertBuildStatusSuccess(b);
        assertFile(true, "foo.txt", b);
        
        WebClient wc = rule.createWebClient();
        rule.submit(wc.getPage(dest, "configure").getFormByName("config"));
        
        dest = rule.jenkins.getItemByFullName(dest.getFullName(), FreeStyleProject.class);
        CopyArtifact ca = (CopyArtifact)dest.getBuildersList().get(0);
        assertEquals(src.getName(), ca.getProjectName());
    }

    @Test
    public void testSameFolderFromMatrix() throws Exception {
        Folder folder = rule.jenkins.createProject(Folder.class, "folder");
        MatrixProject src = folder.createProject(MatrixProject.class, "src");
        src.setAxes(new AxisList(new TextAxis("axis1", "value1", "value2")));
        src.getBuildersList().add(new ArtifactBuilder());
        src.getPublishersList().add(new ArtifactArchiver("**", "", false, false));
        rule.assertBuildStatusSuccess(src.scheduleBuild2(0));
        
        String projectNameToCopyFrom = String.format("%s/axis1=value1", src.getName());
        FreeStyleProject dest = folder.createProject(FreeStyleProject.class, "dest");
        dest.getBuildersList().add(CopyArtifactUtil.createCopyArtifact(projectNameToCopyFrom, null, new StatusBuildSelector(true), "", "", false, false, true));
        FreeStyleBuild b = dest.scheduleBuild2(0).get();
        rule.assertBuildStatusSuccess(b);
        assertFile(true, "foo.txt", b);
        
        WebClient wc = rule.createWebClient();
        rule.submit(wc.getPage(dest, "configure").getFormByName("config"));
        
        dest = rule.jenkins.getItemByFullName(dest.getFullName(), FreeStyleProject.class);
        CopyArtifact ca = (CopyArtifact)dest.getBuildersList().get(0);
        assertEquals(projectNameToCopyFrom, ca.getProjectName());
    }

    @Issue("JENKINS-20940")
    @Test
    public void testSameFolderToMatrix() throws Exception {
        Folder folder = rule.jenkins.createProject(Folder.class, "foler");
        FreeStyleProject src = folder.createProject(FreeStyleProject.class, "src");
        src.getBuildersList().add(new ArtifactBuilder());
        src.getPublishersList().add(new ArtifactArchiver("**", "", false, false));
        rule.assertBuildStatusSuccess(src.scheduleBuild2(0));
        
        MatrixProject dest = folder.createProject(MatrixProject.class, "dest");
        dest.setAxes(new AxisList(new TextAxis("axis1", "value1", "value2")));
        dest.getBuildersList().add(CopyArtifactUtil.createCopyArtifact(src.getName(), null, new StatusBuildSelector(true), "", "", false, false, true));
        MatrixBuild b = dest.scheduleBuild2(0).get();
        rule.assertBuildStatusSuccess(b);
        for(MatrixRun r: b.getExactRuns()) {
            assertFile(true, "foo.txt", r);
        }
        
        WebClient wc = rule.createWebClient();
        rule.submit(wc.getPage(dest, "configure").getFormByName("config"));
        
        dest = rule.jenkins.getItemByFullName(dest.getFullName(), MatrixProject.class);
        CopyArtifact ca = (CopyArtifact)dest.getBuildersList().get(0);
        assertEquals(src.getName(), ca.getProjectName());
    }

    @Test
    @LocalData
    public void testOldCopyArtifactConfigIsLoadedCorrectly() throws Exception {
        FreeStyleProject p = (FreeStyleProject) rule.jenkins.getItem("copy-artifact");
        CopyArtifact trigger = (CopyArtifact) p.getBuilders().get(0);

        assertTrue(trigger.isFingerprintArtifacts());
    }

    @LocalData // enable Jenkins security
    @Test
    public void testCopyArtifactPermissionProperty() throws Exception {
        // invalid permission configuration can hang builds.
        final int TIMEOUT = 60;
        
        // LocalData provides following user/password pairs:
        //  test1/test1 : have all privileges except accessing jobs.
        
        User test1 = User.get("test1");
        
        // Prepare projects:
        //   copiee: a project creates an artifact.
        //   copier: a project copies an artifact from copiee.
        //   matrixCopiee: a matrix project creates an artifact.
        //   matrixCopier: a matrix project copies an artifact from copiee.
        // Only allowed users can access projects.
        FreeStyleProject copiee = createArtifactProject();
        FreeStyleProject copier = createProject("${copyfrom}", null, "foo.txt", "", false, false, false);
        copier.addProperty(new ParametersDefinitionProperty(
                new StringParameterDefinition("copyfrom",  copiee.getFullName())
        ));
        
        MatrixProject matrixCopiee = createMatrixArtifactProject();
        MatrixProject matrixCopier = createMatrixProject();
        matrixCopier.setAxes(new AxisList(new Axis("FOO", "one", "two"))); // this matches axes of matrixCopiee
        matrixCopier.getBuildersList().add(CopyArtifactUtil.createCopyArtifact(matrixCopiee.getName() + "/FOO=$FOO", null,
                new StatusBuildSelector(true), "", "", false, false));
        
        // test permissions
        // not all user can access projects.
        assertFalse(copiee.getACL().hasPermission(test1.impersonate(), Item.READ));
        assertFalse(copier.getACL().hasPermission(test1.impersonate(), Item.READ));
        assertFalse(matrixCopiee.getACL().hasPermission(test1.impersonate(), Item.READ));
        assertFalse(matrixCopier.getACL().hasPermission(test1.impersonate(), Item.READ));
        
        // prepare an artifact
        rule.assertBuildStatusSuccess(copiee.scheduleBuild2(0));
        rule.assertBuildStatusSuccess(matrixCopiee.scheduleBuild2(0));
        
        // Without CopyArtifactPermissionProperty, build fails with access check.
        rule.assertBuildStatus(Result.FAILURE, copier.scheduleBuild2(0).get(TIMEOUT, TimeUnit.SECONDS));
        rule.assertBuildStatus(Result.FAILURE, matrixCopier.scheduleBuild2(0).get(TIMEOUT, TimeUnit.SECONDS));
        
        copiee.addProperty(new CopyArtifactPermissionProperty(copier.getFullName()));
        matrixCopiee.addProperty(new CopyArtifactPermissionProperty(matrixCopier.getFullName()));
        
        // By using CopyArtifactPermissionProperty,
        // builds succeed.
        rule.assertBuildStatusSuccess(copier.scheduleBuild2(0).get(TIMEOUT, TimeUnit.SECONDS));
        rule.assertBuildStatusSuccess(matrixCopier.scheduleBuild2(0).get(TIMEOUT, TimeUnit.SECONDS));
    }

    @Test
    public void testWebConfiguration() throws Exception {
        FreeStyleProject upstream1 = rule.createFreeStyleProject();
        FreeStyleProject upstream2 = rule.createFreeStyleProject();
        FreeStyleProject p = rule.createFreeStyleProject();
        p.getBuildersList().add(CopyArtifactUtil.createCopyArtifact(
                upstream1.getName(),
                "",
                new StatusBuildSelector(true),
                "",
                "",
                "",
                false,
                true,
                true,
                ""
        ));
        p.getBuildersList().add(CopyArtifactUtil.createCopyArtifact(
                upstream2.getName(),
                "param=value",
                new TriggeredBuildSelector(false),
                "**",
                "foobar.txt",
                "targetdir",
                true,
                false,
                false,
                "SomeSuffix"
        ));
        p.save();
        
        WebClient wc = rule.createWebClient();
        rule.submit(wc.getPage(p, "configure").getFormByName("config"));
        
        p = rule.jenkins.getItemByFullName(p.getFullName(), FreeStyleProject.class);
        
        List<CopyArtifact> caList = p.getBuildersList().getAll(CopyArtifact.class);
        assertEquals(2, caList.size());
        {
            CopyArtifact ca = caList.get(0);
            assertEquals(upstream1.getName(), ca.getProjectName());
            assertEquals(null, ca.getParameters());
            assertEquals(StatusBuildSelector.class, ca.getBuildSelector().getClass());
            assertEquals("", ca.getFilter());
            assertEquals("", ca.getExcludes());
            assertEquals("", ca.getTarget());
            assertFalse(ca.isFlatten());
            assertTrue(ca.isOptional());
            assertTrue(ca.isFingerprintArtifacts());
            assertNull(ca.getResultVariableSuffix());
        }
        {
            CopyArtifact ca = caList.get(1);
            assertEquals(upstream2.getName(), ca.getProjectName());
            assertEquals("param=value", ca.getParameters());
            assertEquals(TriggeredBuildSelector.class, ca.getBuildSelector().getClass());
            assertEquals("**", ca.getFilter());
            assertEquals("foobar.txt", ca.getExcludes());
            assertEquals("targetdir", ca.getTarget());
            assertTrue(ca.isFlatten());
            assertFalse(ca.isOptional());
            assertFalse(ca.isFingerprintArtifacts());
            assertEquals("SomeSuffix", ca.getResultVariableSuffix());
        }
    }
    
    private boolean isFilePermissionSupported() throws Exception {
        return rule.jenkins.getRootPath().mode() != -1;
    }
    
    @Test
    public void testFilePermission() throws Exception {
        if (!isFilePermissionSupported()) {
            Logger.getLogger(CopyArtifactTest.class.getName()).warning(String.format(
                    "Skipped %s as file permission is not supported on this platform",
                    name.getMethodName()
            ));
            return;
        }
        
        FreeStyleProject copiee = rule.createFreeStyleProject();
        FreeStyleBuild copieeBuild = copiee.scheduleBuild2(0).get();
        rule.assertBuildStatusSuccess(copieeBuild);
        
        // As I cannot trust ArtifactArchiver (JENKINS-14269),
        // creates artifacts manually.
        FilePath artifactDir = new FilePath(copieeBuild.getArtifactsDir());
        artifactDir.child("artifact.txt").write("some content", Charset.defaultCharset().name());
        artifactDir.child("artifact.txt").chmod(0644);
        artifactDir.child("artifactWithExecute.txt").write("some content", Charset.defaultCharset().name());
        artifactDir.child("artifactWithExecute.txt").chmod(0755);
        artifactDir.child("subdir").mkdirs();
        artifactDir.child("subdir/artifactInSubdir.txt").write("some content", Charset.defaultCharset().name());
        artifactDir.child("subdir/artifactInSubdir.txt").chmod(0644);
        artifactDir.child("subdir/artifactWithExecuteInSubdir.txt").write("some content", Charset.defaultCharset().name());
        artifactDir.child("subdir/artifactWithExecuteInSubdir.txt").chmod(0755);
        
        assertEquals(0644, artifactDir.child("artifact.txt").mode() & 0777);
        assertEquals(0755, artifactDir.child("artifactWithExecute.txt").mode() & 0777);
        assertEquals(0644, artifactDir.child("subdir/artifactInSubdir.txt").mode() & 0777);
        assertEquals(0755, artifactDir.child("subdir/artifactWithExecuteInSubdir.txt").mode() & 0777);
        
        // on master, without flatten
        {
            FreeStyleProject p = rule.createFreeStyleProject();
            p.setAssignedNode(rule.jenkins);
            p.getBuildersList().add(CopyArtifactUtil.createCopyArtifact(
                    copiee.getFullName(),
                    "",
                    new SpecificBuildSelector(Integer.toString(copieeBuild.getNumber())),
                    "",
                    "",
                    "",
                    false,
                    false,
                    false
            ));
            FreeStyleBuild b = p.scheduleBuild2(0).get();
            rule.assertBuildStatusSuccess(b);
            
            assertEquals(rule.jenkins, b.getBuiltOn());
            
            FilePath w = b.getWorkspace();
            assertEquals(0644, w.child("artifact.txt").mode() & 0777);
            assertEquals(0755, w.child("artifactWithExecute.txt").mode() & 0777);
            assertEquals(0644, w.child("subdir/artifactInSubdir.txt").mode() & 0777);
            assertEquals(0755, w.child("subdir/artifactWithExecuteInSubdir.txt").mode() & 0777);
        }
        
        // on master, with flatten
        {
            FreeStyleProject p = rule.createFreeStyleProject();
            p.setAssignedNode(rule.jenkins);
            p.getBuildersList().add(CopyArtifactUtil.createCopyArtifact(
                    copiee.getFullName(),
                    "",
                    new SpecificBuildSelector(Integer.toString(copieeBuild.getNumber())),
                    "",
                    "",
                    "",
                    true,   // flatten
                    false,
                    false
            ));
            FreeStyleBuild b = p.scheduleBuild2(0).get();
            rule.assertBuildStatusSuccess(b);
            
            assertEquals(rule.jenkins, b.getBuiltOn());
            
            FilePath w = b.getWorkspace();
            assertEquals(0644, w.child("artifact.txt").mode() & 0777);
            assertEquals(0755, w.child("artifactWithExecute.txt").mode() & 0777);
            assertEquals(0644, w.child("artifactInSubdir.txt").mode() & 0777);
            assertEquals(0755, w.child("artifactWithExecuteInSubdir.txt").mode() & 0777);
        }
        
        DumbSlave node = rule.createOnlineSlave();
        
        // on slave, without flatten
        {
            FreeStyleProject p = rule.createFreeStyleProject();
            p.setAssignedNode(node);
            p.getBuildersList().add(CopyArtifactUtil.createCopyArtifact(
                    copiee.getFullName(),
                    "",
                    new SpecificBuildSelector(Integer.toString(copieeBuild.getNumber())),
                    "",
                    "",
                    "",
                    false,
                    false,
                    false
            ));
            FreeStyleBuild b = p.scheduleBuild2(0).get();
            rule.assertBuildStatusSuccess(b);
            
            assertEquals(node, b.getBuiltOn());
            
            FilePath w = b.getWorkspace();
            assertEquals(0644, w.child("artifact.txt").mode() & 0777);
            assertEquals(0755, w.child("artifactWithExecute.txt").mode() & 0777);
            assertEquals(0644, w.child("subdir/artifactInSubdir.txt").mode() & 0777);
            assertEquals(0755, w.child("subdir/artifactWithExecuteInSubdir.txt").mode() & 0777);
        }
        
        // on slave, with flatten
        {
            FreeStyleProject p = rule.createFreeStyleProject();
            p.setAssignedNode(node);
            p.getBuildersList().add(CopyArtifactUtil.createCopyArtifact(
                    copiee.getFullName(),
                    "",
                    new SpecificBuildSelector(Integer.toString(copieeBuild.getNumber())),
                    "",
                    "",
                    "",
                    true,   // flatten
                    false,
                    false
            ));
            FreeStyleBuild b = p.scheduleBuild2(0).get();
            rule.assertBuildStatusSuccess(b);
            
            assertEquals(node, b.getBuiltOn());
            
            FilePath w = b.getWorkspace();
            assertEquals(0644, w.child("artifact.txt").mode() & 0777);
            assertEquals(0755, w.child("artifactWithExecute.txt").mode() & 0777);
            assertEquals(0644, w.child("artifactInSubdir.txt").mode() & 0777);
            assertEquals(0755, w.child("artifactWithExecuteInSubdir.txt").mode() & 0777);
        }
    }

    @Issue("JENKINS-20546")
    @Test
    public void testSymlinks() throws Exception {
        FreeStyleProject p1 = rule.createFreeStyleProject("p1");
        p1.getBuildersList().add(new TestBuilder() {
            @Override public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
                build.getWorkspace().child("plain").write("text", null);
                build.getWorkspace().child("link1").symlinkTo("plain", listener);
                build.getWorkspace().child("link2").symlinkTo("nonexistent", listener);
                return true;
            }
        });
        p1.getPublishersList().add(new ArtifactArchiver("**", "", false, false));
        rule.buildAndAssertSuccess(p1);
        FreeStyleProject p2 = rule.createFreeStyleProject("p2");
        p2.getBuildersList().add(CopyArtifactUtil.createCopyArtifact("p1", null, new StatusBuildSelector(true), null, "", false, false, true));
        FreeStyleBuild b = rule.buildAndAssertSuccess(p2);
        FilePath ws = b.getWorkspace();
        assertEquals("text", ws.child("plain").readToString());
        assertEquals("plain", ws.child("link1").readLink());
        assertEquals("nonexistent", ws.child("link2").readLink());
    }
    
    private static class TestQueueItemAuthenticator extends jenkins.security.QueueItemAuthenticator {
        private final org.acegisecurity.Authentication auth;
        
        public TestQueueItemAuthenticator(org.acegisecurity.Authentication auth) {
            this.auth = auth;
        }
        
        @Override
        @javax.annotation.CheckForNull
        public org.acegisecurity.Authentication authenticate(Queue.Item item) {
            return auth;
        }
        
    }
    
    @LocalData
    @Test
    public void testQueueItemAuthenticator() throws Exception {
        
        // This test may hang without timeout with improper authorization configuration.
        int TIMEOUT = 60;
        
        // LocalData provides following user/password pairs:
        //  admin/admin : have all privileges
        //  test1/test1 : have all privileges except accessing jobs.
        //  test2/test2 : have all privileges except accessing jobs.
        
        User admin = User.get("admin");
        User test1 = User.get("test1");
        User test2 = User.get("test2");
        
        // Prepare projects:
        //   copiee: a project creates an artifact.
        //   copier: a project copies an artifact from copiee.
        // permissions:
        //   test1 can access copiee, copier
        //   test2 can access copier
        //
        FreeStyleProject copiee = createArtifactProject();
        Map<Permission, Set<String>> copieePermissions
                = new HashMap<Permission, Set<String>>();
        copieePermissions.put(Item.READ, Sets.newHashSet(test1.getId()));
        copiee.addProperty(new AuthorizationMatrixProperty(copieePermissions));
        
        FreeStyleProject copier = createProject("${copyfrom}", null, "foo.txt", "", false, false, false);
        copier.addProperty(new ParametersDefinitionProperty(
                new StringParameterDefinition("copyfrom",  copiee.getFullName())
        ));
        Map<Permission, Set<String>> copierPermissions
                = new HashMap<Permission, Set<String>>();
        copierPermissions.put(Item.READ, Sets.newHashSet(test1.getId(), test2.getId()));
        copierPermissions.put(Item.BUILD, Sets.newHashSet(test1.getId(), test2.getId(), Jenkins.ANONYMOUS.getName()));
        copier.addProperty(new AuthorizationMatrixProperty(copierPermissions));
        
        // test permissions
        assertTrue (copiee.getACL().hasPermission(admin.impersonate(), Item.READ));
        assertTrue (copiee.getACL().hasPermission(test1.impersonate(), Item.READ));
        assertFalse(copiee.getACL().hasPermission(test2.impersonate(), Item.READ));
        
        assertTrue (copier.getACL().hasPermission(admin.impersonate(), Item.BUILD));
        assertTrue (copier.getACL().hasPermission(test1.impersonate(), Item.BUILD));
        assertTrue (copier.getACL().hasPermission(test2.impersonate(), Item.BUILD));
        assertTrue (copier.getACL().hasPermission(Jenkins.ANONYMOUS, Item.BUILD));
        
        // Computer.BUILD is required since Jenkins 1.521.
        assertTrue (Jenkins.getInstance().getACL().hasPermission(admin.impersonate(), Computer.BUILD));
        assertTrue (Jenkins.getInstance().getACL().hasPermission(test1.impersonate(), Computer.BUILD));
        assertTrue (Jenkins.getInstance().getACL().hasPermission(test2.impersonate(), Computer.BUILD));
        assertTrue (Jenkins.getInstance().getACL().hasPermission(Jenkins.ANONYMOUS, Computer.BUILD));
        
        // prepare an artifact
        rule.assertBuildStatusSuccess(copiee.scheduleBuild2(0));
        
        // Without QueueItemAuthenticator, build fails with access check.
        {
            rule.assertBuildStatus(Result.FAILURE, copier.scheduleBuild2(0).get(TIMEOUT, TimeUnit.SECONDS));
        }
        
        // Set QueueItemAuthenticator to run with authorization of admin.
        // This succeeds.
        {
            QueueItemAuthenticatorConfiguration.get().getAuthenticators().clear();
            QueueItemAuthenticatorConfiguration.get().getAuthenticators().add(
                    new TestQueueItemAuthenticator(admin.impersonate())
            );
            rule.assertBuildStatus(Result.SUCCESS, copier.scheduleBuild2(0).get(TIMEOUT, TimeUnit.SECONDS));
        }
        
        // Set QueueItemAuthenticator to run with authorization of test1.
        // This succeeds.
        {
            QueueItemAuthenticatorConfiguration.get().getAuthenticators().clear();
            QueueItemAuthenticatorConfiguration.get().getAuthenticators().add(
                    new TestQueueItemAuthenticator(test1.impersonate())
            );
            rule.assertBuildStatus(Result.SUCCESS, copier.scheduleBuild2(0).get(TIMEOUT, TimeUnit.SECONDS));
        }
        
        // Set QueueItemAuthenticator to run with authorization of test2.
        // This fails.
        {
            QueueItemAuthenticatorConfiguration.get().getAuthenticators().clear();
            QueueItemAuthenticatorConfiguration.get().getAuthenticators().add(
                    new TestQueueItemAuthenticator(test2.impersonate())
            );
            rule.assertBuildStatus(Result.FAILURE, copier.scheduleBuild2(0).get(TIMEOUT, TimeUnit.SECONDS));
        }
        
        // Set QueueItemAuthenticator to run with anonymous authentication.
        // This fails.
        {
            QueueItemAuthenticatorConfiguration.get().getAuthenticators().clear();
            QueueItemAuthenticatorConfiguration.get().getAuthenticators().add(
                    new TestQueueItemAuthenticator(Jenkins.ANONYMOUS)
            );
            rule.assertBuildStatus(Result.FAILURE, copier.scheduleBuild2(0).get(TIMEOUT, TimeUnit.SECONDS));
        }
    }
    
    @Issue("JENKINS-28972")
    @LocalData
    @WithPlugin("copyartifact-extension-test.hpi")  // JENKINS-28792 reproduces only when classes are located in different class loaders.
    @Test
    public void testSimpleBuildSelectorDescriptorInOtherPlugin() throws Exception {
        WebClient wc = rule.createWebClient();
        
        // An extension using SimpleBuildSelectorDescriptorSelector
        {
            FreeStyleProject p = rule.jenkins.getItemByFullName("UsingSimpleBuildSelectorDescriptorSelector", FreeStyleProject.class);
            assertNotNull(p);
            wc.getPage(p, "configure");
        }
        
        // An extension using SimpleBuildSelectorDescriptorSelector without configuration pages.
        {
            FreeStyleProject p = rule.jenkins.getItemByFullName("NoConfigPageSimpleBuildSelectorDescriptorSelector", FreeStyleProject.class);
            assertNotNull(p);
            wc.getPage(p, "configure");
        }
        
        // An extension extending SimpleBuildSelectorDescriptorSelector.
        // (Even though generally it is useless)
        {
            FreeStyleProject p = rule.jenkins.getItemByFullName("ExtendingSimpleBuildSelectorDescriptorSelector", FreeStyleProject.class);
            assertNotNull(p);
            wc.getPage(p, "configure");
        }
    }

    @Test
    public void testIsValidVariableName() throws Exception {
        assertTrue(CopyArtifact.isValidVariableName("VarName"));
        assertTrue(CopyArtifact.isValidVariableName("Var_Name"));
        assertFalse(CopyArtifact.isValidVariableName(null));
        assertFalse(CopyArtifact.isValidVariableName(""));
        assertFalse(CopyArtifact.isValidVariableName("  "));
        assertFalse(CopyArtifact.isValidVariableName("=/?!\""));
    }
    
    @Test
    public void testResultVariableSuffix() throws Exception {
        FreeStyleProject srcProject = createArtifactProject("SRC-PROJECT1");
        FreeStyleBuild srcBuild = srcProject.scheduleBuild2(0).get();
        rule.assertBuildStatusSuccess(srcBuild);
        
        // if no result variable suffix is provided
        // the default suffix (SRC_PROJECT) is used.
        {
            FreeStyleProject p = rule.createFreeStyleProject();
            p.getBuildersList().add(CopyArtifactUtil.createCopyArtifact(
                    srcProject.getFullName(),
                    null,       // parameters
                    new PermalinkBuildSelector("lastStableBuild"),
                    "*.txt",    // filter
                    "",         // excludes
                    "",         // target
                    false,      // flatten
                    false,      // optional
                    true,       // fingerprintArtifacts
                    ""          // resultVariableSuffix
            ));
            CaptureEnvironmentBuilder ceb = new CaptureEnvironmentBuilder();
            p.getBuildersList().add(ceb);

            rule.assertBuildStatusSuccess(p.scheduleBuild2(0));
            
            assertEquals(
                    Integer.toString(srcBuild.getNumber()),
                    ceb.getEnvVars().get("COPYARTIFACT_BUILD_NUMBER_SRC_PROJECT_")
            );
        }
        
        // if result variable suffix is provided
        // it is used for the variable name to store.
        {
            FreeStyleProject p = rule.createFreeStyleProject();
            p.getBuildersList().add(CopyArtifactUtil.createCopyArtifact(
                    srcProject.getFullName(),
                    null,       // parameters
                    new PermalinkBuildSelector("lastStableBuild"),
                    "*.txt",    // filter
                    "",         // excludes
                    "",         // target
                    false,      // flatten
                    false,      // optional
                    true,       // fingerprintArtifacts
                    "DEST1"     // resultVariableSuffix
            ));
            CaptureEnvironmentBuilder ceb = new CaptureEnvironmentBuilder();
            p.getBuildersList().add(ceb);

            rule.assertBuildStatusSuccess(p.scheduleBuild2(0));
            
            assertEquals(
                    Integer.toString(srcBuild.getNumber()),
                    ceb.getEnvVars().get("COPYARTIFACT_BUILD_NUMBER_DEST1")
            );
            assertNull(ceb.getEnvVars().get("COPYARTIFACT_BUILD_NUMBER_SRC_PROJECT_"));
        }
        
        // if result variable suffix is invalid,
        // the default suffix (SRC_PROJECT) is used.
        {
            FreeStyleProject p = rule.createFreeStyleProject();
            p.getBuildersList().add(CopyArtifactUtil.createCopyArtifact(
                    srcProject.getFullName(),
                    null,       // parameters
                    new PermalinkBuildSelector("lastStableBuild"),
                    "*.txt",    // filter
                    "",         // excludes
                    "",         // target
                    false,      // flatten
                    false,      // optional
                    true,       // fingerprintArtifacts
                    "= =!?"     // resultVariableSuffix
            ));
            CaptureEnvironmentBuilder ceb = new CaptureEnvironmentBuilder();
            p.getBuildersList().add(ceb);

            rule.assertBuildStatusSuccess(p.scheduleBuild2(0));
            
            assertEquals(
                    Integer.toString(srcBuild.getNumber()),
                    ceb.getEnvVars().get("COPYARTIFACT_BUILD_NUMBER_SRC_PROJECT_")
            );
        }
    }
}
