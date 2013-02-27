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
import hudson.Launcher;
import hudson.matrix.Axis;
import hudson.matrix.AxisList;
import hudson.matrix.Combination;
import hudson.matrix.MatrixBuild;
import hudson.matrix.MatrixProject;
import hudson.matrix.MatrixRun;
import hudson.maven.MavenModuleSet;
import hudson.model.AbstractBuild;
import hudson.model.BooleanParameterDefinition;
import hudson.model.BooleanParameterValue;
import hudson.model.Action;
import hudson.model.Build;
import hudson.model.BuildListener;
import hudson.model.Fingerprint;
import hudson.model.Item;
import hudson.model.Cause.UserCause;
import hudson.model.ChoiceParameterDefinition;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Hudson;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.Result;
import hudson.model.StringParameterDefinition;
import hudson.model.StringParameterValue;
import hudson.security.AuthorizationMatrixProperty;
import hudson.security.GlobalMatrixAuthorizationStrategy;
import hudson.slaves.DumbSlave;
import hudson.slaves.SlaveComputer;
import hudson.tasks.ArtifactArchiver;
import hudson.tasks.BuildTrigger;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import hudson.util.VersionNumber;
import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.Callable;

import org.acegisecurity.context.SecurityContextHolder;
import org.acegisecurity.providers.UsernamePasswordAuthenticationToken;
import org.jvnet.hudson.test.ExtractResourceSCM;
import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.CaptureEnvironmentBuilder;
import org.jvnet.hudson.test.FailureBuilder;
import org.jvnet.hudson.test.UnstableBuilder;
import org.jvnet.hudson.test.recipes.LocalData;

/**
 * Test interaction of copyartifact plugin with Jenkins core.
 * @author Alan Harder
 */
public class CopyArtifactTest extends HudsonTestCase {

    private FreeStyleProject createProject(String otherProject, String parameters, String filter,
            String target, boolean stable, boolean flatten, boolean optional)
            throws IOException {
        FreeStyleProject p = createFreeStyleProject();
        p.getBuildersList().add(new CopyArtifact(otherProject, parameters,
                new StatusBuildSelector(stable), filter, target, flatten, optional));
        return p;
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
            // For matrix tests write one more file:
            String foo = build.getBuildVariables().get("FOO");
            if (foo != null) ws.child(foo + ".txt").touch(System.currentTimeMillis());
            return true;
        }
    }

    private FreeStyleProject createArtifactProject(String name) throws IOException {
        FreeStyleProject p = name != null ? createFreeStyleProject(name) : createFreeStyleProject();
        p.getBuildersList().add(new ArtifactBuilder());
        p.getPublishersList().add(new ArtifactArchiver("**", "", false));
        return p;
    }

    private FreeStyleProject createArtifactProject() throws IOException {
        return createArtifactProject(null);
    }

    private MatrixProject createMatrixArtifactProject() throws IOException {
        MatrixProject p = createMatrixProject();
        p.setAxes(new AxisList(new Axis("FOO", "one", "two")));
        p.getBuildersList().add(new ArtifactBuilder());
        p.getPublishersList().add(new ArtifactArchiver("**", "", false));
        return p;
    }

    private static void assertFile(boolean exists, String path, Build<?,?> b)
            throws IOException, InterruptedException {
        if (b.getWorkspace().child(path).exists() != exists)
            assertEquals(path + ": " + getLog(b), exists, !exists);
    }

    public void testMissingProject() throws Exception {
        FreeStyleProject p = createProject("invalid", null, "", "", false, false, false);
        assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0, new UserCause()).get());
    }

    public void testMissingBuild() throws Exception {
        FreeStyleProject other = createFreeStyleProject(),
                         p = createProject(other.getName(), null, "", "", false, false, false);
        assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0, new UserCause()).get());
    }

    public void testMissingStableBuild() throws Exception {
        FreeStyleProject other = createFreeStyleProject(),
                         p = createProject(other.getName(), null, "", "", true, false, false);
        // Make an unstable build in "other"
        other.getBuildersList().add(new UnstableBuilder());
        assertBuildStatus(Result.UNSTABLE, other.scheduleBuild2(0, new UserCause()).get());
        assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0, new UserCause()).get());
    }

    public void testMissingArtifact() throws Exception {
        FreeStyleProject other = createFreeStyleProject(),
                         p = createProject(other.getName(), null, "*.txt", "", false, false, false);
        assertBuildStatusSuccess(other.scheduleBuild2(0, new UserCause()).get());
        assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0, new UserCause()).get());
    }

    public void testCopyAll() throws Exception {
        FreeStyleProject other = createArtifactProject(),
                         p = createProject(other.getName(), null, "", "", false, false, false);
        FreeStyleBuild s = assertBuildStatusSuccess(other.scheduleBuild2(0, new UserCause()).get());
        FreeStyleBuild b = p.scheduleBuild2(0, new UserCause()).get();
        assertBuildStatusSuccess(b);
        assertFile(true, "foo.txt", b);
        assertFile(true, "subdir/subfoo.txt", b);
        assertFile(true, "deepfoo/a/b/c.log", b);
        
        // testing fingerprints
        String d = b.getWorkspace().child("foo.txt").digest();
        Fingerprint f = Hudson.getInstance().getFingerprintMap().get(d);
        assertSame(f.getOriginal().getRun(),s);
        assertTrue(f.getRangeSet(p).includes(b.getNumber()));
    }

    public void testCopyWithFilter() throws Exception {
        FreeStyleProject other = createArtifactProject(),
                 p = createProject(other.getName(), null, "**/bogus*, **/sub*, bogus/**", "",
                                   false, false, false);
        assertBuildStatusSuccess(other.scheduleBuild2(0, new UserCause()).get());
        FreeStyleBuild b = p.scheduleBuild2(0, new UserCause()).get();
        assertBuildStatusSuccess(b);
        assertFile(false, "foo.txt", b);
        assertFile(true, "subdir/subfoo.txt", b);
        assertFile(false, "deepfoo/a/b/c.log", b);
    }

    public void testCopyToTarget() throws Exception {
        FreeStyleProject other = createArtifactProject(),
                 p = createProject(other.getName(), null, "deep*/**", "new/deep/dir",
                                   true, false, false);
        assertBuildStatusSuccess(other.scheduleBuild2(0, new UserCause()).get());
        FreeStyleBuild b = p.scheduleBuild2(0, new UserCause()).get();
        assertBuildStatusSuccess(b);
        assertFile(false, "foo.txt", b);
        assertFile(false, "new/deep/dir/foo.txt", b);
        assertFile(true, "new/deep/dir/deepfoo/a/b/c.log", b);
    }

    public void testCopyToSlave() throws Exception {
        DumbSlave node = createSlave();
        SlaveComputer c = node.getComputer();
        c.connect(false).get(); // wait until it's connected
        if(c.isOffline())
            fail("Slave failed to go online: " + c.getLog());
        FreeStyleProject other = createArtifactProject(),
                         p = createProject(other.getName(), null, "", "", false, false, false);
        assertBuildStatusSuccess(other.scheduleBuild2(0, new UserCause()).get());
        p.setAssignedLabel(node.getSelfLabel());
        FreeStyleBuild b = p.scheduleBuild2(0, new UserCause()).get();
        assertBuildStatusSuccess(b);
        assertSame(node, b.getBuiltOn());
        assertFile(true, "foo.txt", b);
        assertFile(true, "subdir/subfoo.txt", b);
        assertFile(true, "deepfoo/a/b/c.log", b);
    }

    public void testParameters() throws Exception {
        FreeStyleProject other = createArtifactProject(),
                         p = createProject("$PROJSRC", null, "$BASE/*.txt", "$TARGET/bar",
                                           false, false, false);
        assertBuildStatusSuccess(other.scheduleBuild2(0, new UserCause()).get());
        FreeStyleBuild b = p.scheduleBuild2(0, new UserCause(),
                new ParametersAction(new StringParameterValue("PROJSRC", other.getName()),
                                     new StringParameterValue("BASE", "*r"),
                                     new StringParameterValue("TARGET", "foo"))).get();
        assertBuildStatusSuccess(b);
        assertFile(false, "foo/bar/foo.txt", b);
        assertFile(true, "foo/bar/subdir/subfoo.txt", b);
    }

    /** Test copying artifacts from a particular configuration of a matrix job */
    public void testMatrixJob() throws Exception {
        MatrixProject other = createMatrixArtifactProject();
        FreeStyleProject p = createProject(other.getName() + "/FOO=two", null, "", "",
                                           true, false, false);
        assertBuildStatusSuccess(other.scheduleBuild2(0, new UserCause()).get());
        FreeStyleBuild b = p.scheduleBuild2(0, new UserCause()).get();
        assertBuildStatusSuccess(b);
        assertFile(true, "foo.txt", b);
        assertFile(true, "two.txt", b);
        assertFile(true, "subdir/subfoo.txt", b);
        assertFile(true, "deepfoo/a/b/c.log", b);
    }

    /** Test artifact copy between matrix jobs, for artifact from matching axis */
    public void testMatrixToMatrix() throws Exception {
        MatrixProject other = createMatrixArtifactProject(),
                      p = createMatrixProject();
        p.setAxes(new AxisList(new Axis("FOO", "one", "two"))); // should match other job
        p.getBuildersList().add(new CopyArtifact(other.getName() + "/FOO=$FOO", null,
                                    new StatusBuildSelector(true), "", "", false, false));
        assertBuildStatusSuccess(other.scheduleBuild2(0, new UserCause()).get());
        MatrixBuild b = p.scheduleBuild2(0, new UserCause()).get();
        assertBuildStatusSuccess(b);
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
    public void testMatrixAll() throws Exception {
        MatrixProject mp = createMatrixProject();
        mp.setAxes(new AxisList(new Axis("ARCH", "sparc", "x86")));
        mp.getBuildersList().add(new ArchMatrixBuilder());
        mp.getPublishersList().add(new ArtifactArchiver("target/*", "", false));
        assertBuildStatusSuccess(mp.scheduleBuild2(0, new UserCause()).get());
        FreeStyleProject p = createProject(mp.getName(), null, "", "", true, false, false);
        FreeStyleBuild b = p.scheduleBuild2(0, new UserCause()).get();
        assertBuildStatusSuccess(b);
        assertFile(true, "ARCH=sparc/target/readme.txt", b);
        assertFile(true, "ARCH=sparc/target/sparc.out", b);
        assertFile(true, "ARCH=x86/target/readme.txt", b);
        assertFile(true, "ARCH=x86/target/x86.out", b);
    }

    private MavenModuleSet setupMavenJob() throws Exception {
        configureDefaultMaven();
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
    public void testMavenJob() throws Exception {
        MavenModuleSet mp = setupMavenJob();
        assertBuildStatusSuccess(mp.scheduleBuild2(0, new UserCause()).get());
        FreeStyleProject p = createProject(mp.getName() + "/org.jvnet.hudson.main.test.multimod$moduleB",
                                           null, "", "", true, false, false);
        FreeStyleBuild b = p.scheduleBuild2(0, new UserCause()).get();
        String dir = "org.jvnet.hudson.main.test.multimod/";
        assertFile(true, dir + "moduleB/1.0-SNAPSHOT/moduleB-1.0-SNAPSHOT.jar", b);
        assertFile(true, dir + pomName("moduleB", "1.0-SNAPSHOT"), b);
    }

    /** Test copying all artifacts from a maven job */
    public void testMavenAll() throws Exception {
        MavenModuleSet mp = setupMavenJob();
        assertBuildStatusSuccess(mp.scheduleBuild2(0, new UserCause()).get());
        FreeStyleProject p = createProject(mp.getName(), null, "", "", true, false, false);
        FreeStyleBuild b = p.scheduleBuild2(0, new UserCause()).get();
        String dir = "org.jvnet.hudson.main.test.multimod/";
        assertFile(true, dir + "moduleA/1.0-SNAPSHOT/moduleA-1.0-SNAPSHOT.jar", b);
        assertFile(true, dir + pomName("moduleA", "1.0-SNAPSHOT"), b);
        assertFile(true, dir + "moduleB/1.0-SNAPSHOT/moduleB-1.0-SNAPSHOT.jar", b);
        assertFile(true, dir + pomName("moduleB", "1.0-SNAPSHOT"), b);
        assertFile(true, dir + "moduleC/1.0-SNAPSHOT/moduleC-1.0-SNAPSHOT.jar", b);
        assertFile(true, dir + pomName("moduleC", "1.0-SNAPSHOT"), b);
        // Test with filter
        p = createProject(mp.getName(), null, "**/*.jar", "", true, false, false);
        b = p.scheduleBuild2(0, new UserCause()).get();
        assertFile(true, dir + "moduleA/1.0-SNAPSHOT/moduleA-1.0-SNAPSHOT.jar", b);
        assertFile(false, dir + pomName("moduleA", "1.0-SNAPSHOT"), b);
        assertFile(true, dir + "moduleB/1.0-SNAPSHOT/moduleB-1.0-SNAPSHOT.jar", b);
        assertFile(false, dir + pomName("moduleB", "1.0-SNAPSHOT"), b);
        assertFile(true, dir + "moduleC/1.0-SNAPSHOT/moduleC-1.0-SNAPSHOT.jar", b);
        assertFile(false, dir + pomName("moduleC", "1.0-SNAPSHOT"), b);
    }

    /** Test copying from maven job where artifacts manually archived instead of automatic */
    public void testMavenJobWithArchivePostBuildStep() throws Exception {
        MavenModuleSet mp = setupMavenJob();
        // Turn off automatic archiving and use a post-build step instead.
        // Artifacts will be stored with the parent build instead of the child module builds.
        mp.setIsArchivingDisabled(true);
        mp.getPublishersList().add(new ArtifactArchiver("moduleB/*.xml", "", false));
        assertBuildStatusSuccess(mp.scheduleBuild2(0, new UserCause()).get());
        FreeStyleProject p = createProject(mp.getName(), null, "", "", true, false, false);
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
    public void testCopyFromWorkspace() throws Exception {
        FreeStyleProject other = createFreeStyleProject(), p = createFreeStyleProject();
        p.getBuildersList().add(new CopyArtifact(other.getName(), null, new WorkspaceSelector(),
                                "**/*.txt", "", true, false));
        // Run a build that places a file in the workspace, but does not archive anything
        other.getBuildersList().add(new ArtifactBuilder());
        assertBuildStatusSuccess(other.scheduleBuild2(0, new UserCause()).get());
        FreeStyleBuild b = p.scheduleBuild2(0, new UserCause()).get();
        assertBuildStatusSuccess(b);
        assertFile(true, "foo.txt", b);
        assertFile(true, "subfoo.txt", b);
        assertFile(false, "c.log", b);
    }

    /** projectName in CopyArtifact build steps should be updated if a job is renamed */
    public void testJobRename() throws Exception {
        FreeStyleProject other = createFreeStyleProject(),
                         p = createProject(other.getName(), null, "", "", true, false, false);
        assertEquals("before", other.getName(),
                     ((CopyArtifact)p.getBuilders().get(0)).getProjectName());
        String newName = other.getName() + "-new";
        other.renameTo(newName);
        assertEquals("after", newName,
                     ((CopyArtifact)p.getBuilders().get(0)).getProjectName());

        // Test reference to a matrix configuration
        MatrixProject otherm = createMatrixProject(),
                      mp = createMatrixProject();
        mp.getBuildersList().add(new CopyArtifact(otherm.getName(), "FOO=$FOO",
                                     new SavedBuildSelector(), "", "", false, false));
        assertEquals("before", otherm.getName(),
                     ((CopyArtifact)mp.getBuilders().get(0)).getProjectName());
        otherm.renameTo(newName = otherm.getName() + "-new");
        assertEquals("after", newName,
                     ((CopyArtifact)mp.getBuilders().get(0)).getProjectName());
    }

    public void testSavedBuildSelector() throws Exception {
        FreeStyleProject other = createArtifactProject(),
                         p = createFreeStyleProject();
        p.getBuildersList().add(new CopyArtifact(other.getName(),
                                    null, new SavedBuildSelector(), "*.txt", "", false, false));
        FreeStyleBuild b = other.scheduleBuild2(0, new UserCause(),
                new ParametersAction(new StringParameterValue("FOO", "buildone"))).get();
        assertBuildStatusSuccess(b);
        assertBuildStatusSuccess(other.scheduleBuild2(0, new UserCause()));
        b.keepLog(true);
        b = p.scheduleBuild2(0, new UserCause()).get();
        assertBuildStatusSuccess(b);
        assertFile(true, "foo.txt", b);
        assertFile(true, "buildone.txt", b);
        assertFile(false, "subdir/subfoo.txt", b);
    }

    public void testSpecificBuildSelector() throws Exception {
        FreeStyleProject other = createArtifactProject(),
                         p = createFreeStyleProject();
        SpecificBuildSelector sbs = new SpecificBuildSelector("1");
        assertEquals("1", sbs.getBuildNumber());
        p.getBuildersList().add(new CopyArtifact(other.getName(), null, sbs, "*.txt", "", false, false));
        assertBuildStatusSuccess(other.scheduleBuild2(0, new UserCause(),
                new ParametersAction(new StringParameterValue("FOO", "buildone"))).get());
        assertBuildStatusSuccess(other.scheduleBuild2(0, new UserCause()));
        FreeStyleBuild b = p.scheduleBuild2(0, new UserCause()).get();
        assertBuildStatusSuccess(b);
        assertFile(true, "foo.txt", b);
        assertFile(true, "buildone.txt", b);
        assertFile(false, "subdir/subfoo.txt", b);
    }

    public void testSpecificBuildSelectorParameter() throws Exception {
        FreeStyleProject other = createArtifactProject(),
                         p = createFreeStyleProject();
        p.getBuildersList().add(new CopyArtifact(other.getName(),
                                    null, new SpecificBuildSelector("$BAR"), "*.txt", "", false, false));
        assertBuildStatusSuccess(other.scheduleBuild2(0, new UserCause(),
                new ParametersAction(new StringParameterValue("FOO", "buildone"))).get());
        assertBuildStatusSuccess(other.scheduleBuild2(0, new UserCause()));
        FreeStyleBuild b = p.scheduleBuild2(0, new UserCause(),
                new ParametersAction(new StringParameterValue("BAR", "1"))).get();
        assertBuildStatusSuccess(b);
        assertFile(true, "foo.txt", b);
        assertFile(true, "buildone.txt", b);
        assertFile(false, "subdir/subfoo.txt", b);
    }

    public void testParameterizedBuildSelector() throws Exception {
        FreeStyleProject other = createArtifactProject(),
                         p = createFreeStyleProject();
        ParameterizedBuildSelector pbs = new ParameterizedBuildSelector("PBS");
        assertEquals("PBS", pbs.getParameterName());
        p.getBuildersList().add(new CopyArtifact(other.getName(), null, pbs, "*.txt", "", false, false));
        FreeStyleBuild b = other.scheduleBuild2(0, new UserCause(),
                new ParametersAction(new StringParameterValue("FOO", "buildone"))).get();
        assertBuildStatusSuccess(b);
        assertBuildStatusSuccess(other.scheduleBuild2(0, new UserCause()));
        b.keepLog(true);
        b = p.scheduleBuild2(0, new UserCause(),
                new ParametersAction(new StringParameterValue("PBS", "<SavedBuildSelector/>"))).get();
        assertBuildStatusSuccess(b);
        assertFile(true, "foo.txt", b);
        assertFile(true, "buildone.txt", b);
        assertFile(false, "subdir/subfoo.txt", b);
    }

    public void testPermalinkBuildSelector() throws Exception {
        FreeStyleProject other = createArtifactProject(),
                         p = createFreeStyleProject();
        p.getBuildersList().add(new CopyArtifact(other.getName(),
                                    null, new PermalinkBuildSelector("lastStableBuild"), "*.txt", "", false, false));
        FreeStyleBuild b = other.scheduleBuild2(0, new UserCause(),
                new ParametersAction(new StringParameterValue("FOO", "buildone"))).get();
        assertBuildStatusSuccess(b);
        other.getBuildersList().add(new UnstableBuilder());
        assertBuildStatus(Result.UNSTABLE, other.scheduleBuild2(0, new UserCause()).get());
        b = p.scheduleBuild2(0, new UserCause()).get();
        assertBuildStatusSuccess(b);
        assertFile(true, "foo.txt", b);
        assertFile(true, "buildone.txt", b);
        assertFile(false, "subdir/subfoo.txt", b);
        // Invalid permalink
        p.getBuildersList().replace(new CopyArtifact(other.getName(),
                null, new PermalinkBuildSelector("fooBuild"), "*.txt", "", false, false));
        assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0, new UserCause()).get());
    }

    public void testTriggeredBuildSelector() throws Exception {
        FreeStyleProject other = createArtifactProject(),
                         p = createFreeStyleProject();
        p.getBuildersList().add(new CopyArtifact(other.getName(),
                                    null, new TriggeredBuildSelector(false), "*.txt", "", false, false));
        other.getPublishersList().add(new BuildTrigger(p.getFullName(), false));
        hudson.rebuildDependencyGraph();
        assertBuildStatusSuccess(other.scheduleBuild2(0, new UserCause()));
        // p#1 was triggered, now building.
        FreeStyleBuild b = p.getBuildByNumber(1);
        for (int i = 0; b == null && i < 1000; i++) { Thread.sleep(10); b = p.getBuildByNumber(1); }
        assertNotNull(b);
        while (b.isBuilding()) Thread.sleep(10);
        assertBuildStatusSuccess(b);
        assertFile(true, "foo.txt", b);
        assertFile(false, "subdir/subfoo.txt", b);
        // Verify error if build not triggered by upstream job:
        assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0, new UserCause()).get());
        // test fallback
        
        //run a failing build to make sure the fallback selects the last successful build
        other.getPublishersList().clear();
        other.getBuildersList().add(new FailureBuilder());
        assertBuildStatus(Result.FAILURE, other.scheduleBuild2(0, new UserCause()).get());
        
        p.getBuildersList().remove(CopyArtifact.class);
        p.getBuildersList().add(new CopyArtifact(other.getName(),
                null, new TriggeredBuildSelector(true), "*.txt", "", false, false));
        assertBuildStatus(Result.SUCCESS, p.scheduleBuild2(0, new UserCause()).get());
    }

    /**
     * When copying from a particular matrix configuration, the upstream project
     * is the matrix parent.
     */
    public void testTriggeredBuildSelectorFromMatrix() throws Exception {
        MatrixProject other = createMatrixArtifactProject();
        FreeStyleProject p = createFreeStyleProject();
        p.getBuildersList().add(new CopyArtifact(other.getName() + "/FOO=two",
                                    null, new TriggeredBuildSelector(false), "*.txt", "", false, false));
        other.getPublishersList().add(new BuildTrigger(p.getFullName(), false));
        hudson.rebuildDependencyGraph();
        assertBuildStatusSuccess(other.scheduleBuild2(0, new UserCause()).get());
        // p#1 was triggered, now building.
        FreeStyleBuild b = p.getBuildByNumber(1);
        for (int i = 0; b == null && i < 1000; i++) { Thread.sleep(10); b = p.getBuildByNumber(1); }
        assertNotNull(b);
        while (b.isBuilding()) Thread.sleep(10);
        assertBuildStatusSuccess(b);
        assertFile(true, "foo.txt", b);
        assertFile(true, "two.txt", b);
    }

    /**
     * When copying to a matrix job, need to check the upstream cause of the
     * matrix parent.
     */
    public void testTriggeredBuildSelectorToMatrix() throws Exception {
        FreeStyleProject other = createArtifactProject();
        MatrixProject p = createMatrixProject();
        p.setAxes(new AxisList(new Axis("FOO", "one", "two")));
        p.getBuildersList().add(new CopyArtifact(other.getName(),
                                    null, new TriggeredBuildSelector(false), "*.txt", "", false, false));
        other.getPublishersList().add(new BuildTrigger(p.getFullName(), false));
        hudson.rebuildDependencyGraph();
        assertBuildStatusSuccess(other.scheduleBuild2(0, new UserCause()).get());
        // p#1 was triggered, now building.
        MatrixBuild b = p.getBuildByNumber(1);
        for (int i = 0; b == null && i < 1000; i++) { Thread.sleep(10); b = p.getBuildByNumber(1); }
        assertNotNull(b);
        while (b.isBuilding()) Thread.sleep(10);
        assertBuildStatusSuccess(b);
        MatrixRun r = b.getRuns().get(0);
        assertFile(true, "foo.txt", r);
    }

    public void testFlatten() throws Exception {
        FreeStyleProject other = createArtifactProject(),
                         p = createProject(other.getName(), null, "", "newdir", false, true, false);
        assertBuildStatusSuccess(other.scheduleBuild2(0, new UserCause()).get());
        FreeStyleBuild b = p.scheduleBuild2(0, new UserCause()).get();
        assertBuildStatusSuccess(b);
        assertFile(true, "newdir/foo.txt", b);
        assertFile(true, "newdir/subfoo.txt", b);
        assertFile(true, "newdir/c.log", b);
    }

    public void testOptional_MissingProject() throws Exception {
        // Missing project still fails even when copy is optional
        FreeStyleProject p = createProject("invalid", null, "", "", false, false, true);
        assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0, new UserCause()).get());
    }

    public void testOptional_MissingBuild() throws Exception {
        FreeStyleProject other = createFreeStyleProject(),
                         p = createProject(other.getName(), null, "", "", false, false, true);
        assertBuildStatusSuccess(p.scheduleBuild2(0, new UserCause()).get());
    }

    public void testOptional_MissingArtifact() throws Exception {
        FreeStyleProject other = createFreeStyleProject(),
                         p = createProject(other.getName(), null, "*.txt", "", false, false, true);
        assertBuildStatusSuccess(other.scheduleBuild2(0, new UserCause()).get());
        assertBuildStatusSuccess(p.scheduleBuild2(0, new UserCause()).get());
    }

    /**
     * Test that a user is prevented from bypassing permissions on other jobs when configuring
     * a copyartifact build step.
     */
    @LocalData
    public void testPermission() throws Exception {
        executeOnServer(new Callable<Object>() {
            public Object call() throws Exception {
                assertNull("Job should not be accessible to anonymous", hudson.getItem("testJob"));
                assertEquals("Should ignore/clear value for inaccessible project", "",
                        new CopyArtifact("testJob", null, null, null, null, false, false).getProjectName());
                return null;
            }
        });

        // Login as user with access to testJob:
        WebClient wc = createWebClient();
        wc.login("joe", "joe");
        wc.executeOnServer(new Callable<Object>() {
            public Object call() throws Exception {
                assertEquals("Should allow use of testJob for joe", "testJob",
                             new CopyArtifact("testJob", null, null, null, null, false, false).getProjectName());
                return null;
            }
        });
    }

    /**
     * When the source project name is parameterized, cannot check at configure time whether
     * the project is accessible.  In this case, permission check is done when the build runs.
     * Only jobs accessible to all authenticated users are allowed.
     */
    @LocalData
    public void testPermissionWhenParameterized() throws Exception {
        FreeStyleProject p = createProject("test$JOB", null, "", "", false, false, false);
        // Build step should succeed when this parameter expands to a job accessible
        // to authenticated users (even if triggered by anonymous, as in this case):
        SecurityContextHolder.clearContext();
        FreeStyleBuild b = p.scheduleBuild2(0, new UserCause(),
                new ParametersAction(new StringParameterValue("JOB", "Job2"))).get();
        assertFile(true, "foo2.txt", b);
        assertBuildStatusSuccess(b);
        // Build step should fail for a job not accessible to all authenticated users,
        // even when accessible to the user starting the job, as in this case:
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("joe","joe"));
        b = p.scheduleBuild2(0, new UserCause(),
                new ParametersAction(new StringParameterValue("JOB", "Job"))).get();
        assertFile(false, "foo.txt", b);
        assertBuildStatus(Result.FAILURE, b);
    }

    @LocalData
    public void testPermissionWhenParameterizedForMatrixConfig() throws Exception {
        // This test fails before Jenkins 1.406
        if (new VersionNumber("1.406").isNewerThan(Hudson.getVersion())) return; // Skip

        FreeStyleProject p = createProject("testMatrix/FOO=$FOO", null, "", "", false, false, false);
        // Build step should succeed when this parameter expands to a job accessible to
        // authenticated users, even when selecting a single matrix config, not the parent job:
        FreeStyleBuild b = p.scheduleBuild2(0, new UserCause(),
                new ParametersAction(new StringParameterValue("FOO", "foo"))).get();
        assertFile(true, "foo.txt", b);
        assertBuildStatusSuccess(b);
    }

    @LocalData
    public void testPermissionWhenParameterizedForMavenModule() throws Exception {
        // This test fails before Jenkins 1.406
        if (new VersionNumber("1.406").isNewerThan(Hudson.getVersion())) return; // Skip

        MavenModuleSet mp = setupMavenJob();
        mp.addProperty(new AuthorizationMatrixProperty(
                Collections.singletonMap(Item.READ, Collections.singleton("authenticated"))));
        assertBuildStatusSuccess(mp.scheduleBuild2(0, new UserCause()).get());
        FreeStyleProject p = createProject(mp.getName() + "/org.jvnet.hudson.main.test.multimod$FOO",
                                           null, "", "", false, false, false);
        // Build step should succeed when this parameter expands to a job accessible to
        // authenticated users, even when selecting a single maven module, not the parent job:
        FreeStyleBuild b = p.scheduleBuild2(0, new UserCause(),
                new ParametersAction(new StringParameterValue("FOO", "$moduleA"))).get();
        String dir = "org.jvnet.hudson.main.test.multimod/";
        assertFile(true, dir + "moduleA/1.0-SNAPSHOT/moduleA-1.0-SNAPSHOT.jar", b);
        assertFile(true, dir + pomName("moduleA", "1.0-SNAPSHOT"), b);
        assertFile(false, dir + "moduleB/1.0-SNAPSHOT/moduleB-1.0-SNAPSHOT.jar", b);
        assertFile(false, dir + pomName("moduleB", "1.0-SNAPSHOT"), b);
        assertBuildStatusSuccess(b);
    }

    /**
     * Test that info about selected builds is added into the environment for later build steps.
     */
    public void testEnvData() throws Exception {
        // Also test conversion of job name to env var name, only keeping letters:
        FreeStyleProject other = createArtifactProject("My (Test) Job"),
                 p = createProject(other.getName(), null, "", "", false, false, false);
        CaptureEnvironmentBuilder envStep = new CaptureEnvironmentBuilder();
        p.getBuildersList().add(envStep);
        // Bump up the build number a bit:
        for (int i = 0; i < 3; i++) other.assignBuildNumber();
        assertBuildStatusSuccess(other.scheduleBuild2(0, new UserCause()).get());
        FreeStyleBuild b = p.scheduleBuild2(0, new UserCause()).get();
        assertBuildStatusSuccess(b);
        assertEquals("4", envStep.getEnvVars().get("COPYARTIFACT_BUILD_NUMBER_MY_TEST_JOB"));
    }

    /**
     * Test filtering on parameters, ie. last stable build with parameter FOO=bar.
     */
    public void testFilterByParameters() throws Exception {
        FreeStyleProject other = createArtifactProject("Foo job");
        other.addProperty(new ParametersDefinitionProperty(
                new StringParameterDefinition("FOO", ""),
                new BooleanParameterDefinition("BAR", false, ""),
                new ChoiceParameterDefinition("BAZ", new String[] { "foo", "bar", "baz" }, "")));
        // #1: FOO=foo BAR=false BAZ=baz
        assertBuildStatusSuccess(other.scheduleBuild2(0, new UserCause(), new ParametersAction(
                new StringParameterValue("FOO", "foo"),
                new BooleanParameterValue("BAR", false),
                new StringParameterValue("BAZ", "baz"))).get());
        // #2: FOO=bar BAR=true BAZ=foo
        assertBuildStatusSuccess(other.scheduleBuild2(0, new UserCause(), new ParametersAction(
                new StringParameterValue("FOO", "bar"),
                new BooleanParameterValue("BAR", true),
                new StringParameterValue("BAZ", "foo"))).get());
        // #3: FOO=foo BAR=true BAZ=bar
        assertBuildStatusSuccess(other.scheduleBuild2(0, new UserCause(), new ParametersAction(
                new StringParameterValue("FOO", "foo"),
                new BooleanParameterValue("BAR", true),
                new StringParameterValue("BAZ", "bar"))).get());

        FreeStyleProject p = createProject(other.getName(), "FOO=bar", "*.txt", "", true, false, false);
        CaptureEnvironmentBuilder envStep = new CaptureEnvironmentBuilder();
        p.getBuildersList().add(envStep);
        FreeStyleBuild b = p.scheduleBuild2(0, new UserCause()).get();
        assertBuildStatusSuccess(b);
        assertEquals("2", envStep.getEnvVars().get("COPYARTIFACT_BUILD_NUMBER_FOO_JOB"));

        p = createProject(other.getName(), "BAR=false", "*.txt", "", true, false, false);
        p.getBuildersList().add(envStep);
        b = p.scheduleBuild2(0, new UserCause()).get();
        assertBuildStatusSuccess(b);
        assertEquals("1", envStep.getEnvVars().get("COPYARTIFACT_BUILD_NUMBER_FOO_JOB"));

        p = createProject(other.getName(), "BAZ=foo,BAR=true", "*.txt", "", true, false, false);
        p.getBuildersList().add(envStep);
        b = p.scheduleBuild2(0, new UserCause()).get();
        assertBuildStatusSuccess(b);
        assertEquals("2", envStep.getEnvVars().get("COPYARTIFACT_BUILD_NUMBER_FOO_JOB"));

        p = createProject(other.getName(), "FOO=foo,BAR=false,BAZ=baz", "*.txt", "", true, false, false);
        p.getBuildersList().add(envStep);
        b = p.scheduleBuild2(0, new UserCause()).get();
        assertBuildStatusSuccess(b);
        assertEquals("1", envStep.getEnvVars().get("COPYARTIFACT_BUILD_NUMBER_FOO_JOB"));

        p = createProject(other.getName(), "BAZ=bar,FOO=bogus", "*.txt", "", true, false, false);
        b = p.scheduleBuild2(0, new UserCause()).get();
        assertBuildStatus(Result.FAILURE, b);

        // Test matching other build variables besides parameters
        p = createProject(other.getName(), "BUILD_NUMBER=2", "*.txt", "", true, false, false);
        p.getBuildersList().add(envStep);
        b = p.scheduleBuild2(0, new UserCause()).get();
        assertBuildStatusSuccess(b);
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

    public void testFilterByMetaParameters() throws Exception {
        FreeStyleProject other = createArtifactProject("Foo job");
        other.addProperty(new ParametersDefinitionProperty(new BooleanParameterDefinition("BAR", false, "")));
        assertBuildStatusSuccess(other.scheduleBuild2(0, new UserCause(), new ParametersAction(new BooleanParameterValue("BAR", false))).get());
        assertBuildStatusSuccess(other.scheduleBuild2(0, new UserCause(), new ParametersAction(new BooleanParameterValue("BAR", true))).get());
        assertBuildStatusSuccess(other.scheduleBuild2(0, new UserCause(), new ParametersAction(new BooleanParameterValue("BAR", false))).get());
        FreeStyleProject p = createProject(other.getName(), "$VAR=true", "*.txt", "", true, false, false);
        p.addProperty(new ParametersDefinitionProperty(new StringParameterDefinition("VAR", "")));
        CaptureEnvironmentBuilder envStep = new CaptureEnvironmentBuilder();
        p.getBuildersList().add(envStep);
        FreeStyleBuild b = p.scheduleBuild2(0, new UserCause(), new ParametersAction(new StringParameterValue("VAR", "BAR"))).get();
        assertBuildStatusSuccess(b);
        assertEquals("2", envStep.getEnvVars().get("COPYARTIFACT_BUILD_NUMBER_FOO_JOB"));
    }

    public void testSavedBuildSelectorWithParameterFilter() throws Exception {
        FreeStyleProject other = createArtifactProject(),
                         p = createFreeStyleProject();
        other.addProperty(new ParametersDefinitionProperty(new StringParameterDefinition("FOO", "")));
        FreeStyleBuild b = other.scheduleBuild2(0, new UserCause(),
                new ParametersAction(new StringParameterValue("FOO", "buildone"))).get();
        assertBuildStatusSuccess(b);
        b.keepLog(true);
        p.getBuildersList().add(new CopyArtifact(other.getName(), "FOO=buildone",
                                    new SavedBuildSelector(), "*.txt", "", false, false));
        assertBuildStatusSuccess(b = other.scheduleBuild2(0, new UserCause()).get());
        b.keepLog(true); // Keep #2 too, but it doesn't have FOO=buildone so should not be selected
        assertBuildStatusSuccess(b = p.scheduleBuild2(0, new UserCause()).get());
        assertFile(true, "foo.txt", b);
        assertFile(true, "buildone.txt", b);
        assertFile(false, "subdir/subfoo.txt", b);
    }

    // Verify build fails if given build# does not match params
    public void testSpecificBuildSelectorWithParameterFilter() throws Exception {
        FreeStyleProject other = createArtifactProject(),
                         p = createFreeStyleProject();
        other.addProperty(new ParametersDefinitionProperty(new StringParameterDefinition("FOO", "")));
        p.getBuildersList().add(new CopyArtifact(other.getName(), "FOO=bogus",
                                    new SpecificBuildSelector("1"), "*.txt", "", false, false));
        assertBuildStatusSuccess(other.scheduleBuild2(0, new UserCause(),
                new ParametersAction(new StringParameterValue("FOO", "foo"))).get());
        assertBuildStatusSuccess(other.scheduleBuild2(0, new UserCause()));
        FreeStyleBuild b = p.scheduleBuild2(0, new UserCause()).get();
        assertBuildStatus(Result.FAILURE, b);
        assertFile(false, "foo.txt", b);
    }

    // Verify BuildSelector defaults to false
    public void testBuildSelectorDefault() {
        assertFalse(new BuildSelector() { }.isSelectable(null, null));
    }

    // Test field getters
    public void testFields() throws Exception {
        FreeStyleProject p = createFreeStyleProject();
        CopyArtifact ca = new CopyArtifact(p.getFullName(), null, new SavedBuildSelector(), "filter", "target", false, true);
        assertEquals(p.getFullName(), ca.getProjectName());
        assertSame(SavedBuildSelector.class, ca.getBuildSelector().getClass());
        assertEquals("filter", ca.getFilter());
        assertEquals("target", ca.getTarget());
        assertFalse(ca.isFlatten());
        assertTrue(ca.isOptional());
        ca = new CopyArtifact("foo", null, null, null, null, true, false);
        assertTrue(ca.isFlatten());
        assertFalse(ca.isOptional());
    }

    public void testFieldValidation() throws Exception {
        FreeStyleProject p = createFreeStyleProject();
        CopyArtifact.DescriptorImpl descriptor = hudson.getDescriptorByType(CopyArtifact.DescriptorImpl.class);
        assertNotNull(descriptor);
        // Valid value
        assertSame(FormValidation.Kind.OK, descriptor.doCheckProjectName(p, p.getFullName()).kind);
        // Empty value
        assertSame(FormValidation.Kind.ERROR, descriptor.doCheckProjectName(p, "").kind);
        // Parameterized value
        assertSame(FormValidation.Kind.WARNING, descriptor.doCheckProjectName(p, "$FOO").kind);
        // Just returns OK if no permission
        hudson.setAuthorizationStrategy(new GlobalMatrixAuthorizationStrategy());
        SecurityContextHolder.clearContext();
        assertSame(FormValidation.Kind.OK, descriptor.doCheckProjectName(p, "").kind);
        // Other descriptor methods
        assertTrue(descriptor.isApplicable(null));
        assertTrue(descriptor.getDisplayName().length() > 0);
        assertTrue(descriptor.getBuildSelectors().size() > 0);
    }
}
