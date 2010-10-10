/*
 * The MIT License
 *
 * Copyright (c) 2004-2010, Sun Microsystems, Inc., Alan Harder
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
import hudson.model.AbstractBuild;
import hudson.model.Build;
import hudson.model.BuildListener;
import hudson.model.Cause.UserCause;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.ParametersAction;
import hudson.model.Result;
import hudson.model.StringParameterValue;
import hudson.slaves.DumbSlave;
import hudson.slaves.SlaveComputer;
import hudson.tasks.ArtifactArchiver;
import hudson.tasks.Builder;
import java.io.IOException;
import java.util.Collections;
import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.UnstableBuilder;

/**
 * Test interaction of copyartifact plugin with Hudson core.
 * @author Alan Harder
 */
public class CopyArtifactTest extends HudsonTestCase {

    private FreeStyleProject createProject(String otherProject, String filter,
            String target, boolean stable, boolean flatten, boolean optional)
            throws IOException {
        FreeStyleProject p = createFreeStyleProject();
        p.getBuildersList().add(new CopyArtifact(otherProject,
                new StatusBuildSelector(stable), filter, target, flatten, optional));
        return p;
    }

    private static class ArtifactBuilder extends Builder {
        @Override public boolean perform(
                AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener)
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

    private FreeStyleProject createArtifactProject() throws IOException {
        FreeStyleProject p = createFreeStyleProject();
        p.getBuildersList().add(new ArtifactBuilder());
        p.getPublishersList().add(new ArtifactArchiver("**", "", false));
        return p;
    }

    private MatrixProject createMatrixArtifactProject() throws IOException {
        MatrixProject p = createMatrixProject();
        p.setAxes(new AxisList(new Axis("FOO", "one", "two")));
        p.getBuildersList().add(new ArtifactBuilder());
        p.getPublishersList().add(new ArtifactArchiver("**", "", false));
        return p;
    }

    private static void assertFile(boolean exists, String path, Build b)
            throws IOException, InterruptedException {
        if (b.getWorkspace().child(path).exists() != exists)
            assertEquals(path + ": " + getLog(b), exists, !exists);
    }

    public void testMissingProject() throws Exception {
        FreeStyleProject p = createProject("invalid", "", "", false, false, false);
        assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0, new UserCause()).get());
    }

    public void testMissingBuild() throws Exception {
        FreeStyleProject other = createFreeStyleProject(),
                         p = createProject(other.getName(), "", "", false, false, false);
        assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0, new UserCause()).get());
    }

    public void testMissingStableBuild() throws Exception {
        FreeStyleProject other = createFreeStyleProject(),
                         p = createProject(other.getName(), "", "", true, false, false);
        // Make an unstable build in "other"
        other.getBuildersList().add(new UnstableBuilder());
        assertBuildStatus(Result.UNSTABLE, other.scheduleBuild2(0, new UserCause()).get());
        assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0, new UserCause()).get());
    }

    public void testMissingArtifact() throws Exception {
        FreeStyleProject other = createFreeStyleProject(),
                         p = createProject(other.getName(), "*.txt", "", false, false, false);
        assertBuildStatusSuccess(other.scheduleBuild2(0, new UserCause()).get());
        assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0, new UserCause()).get());
    }

    public void testCopyAll() throws Exception {
        FreeStyleProject other = createArtifactProject(),
                         p = createProject(other.getName(), "", "", false, false, false);
        assertBuildStatusSuccess(other.scheduleBuild2(0, new UserCause()).get());
        FreeStyleBuild b = p.scheduleBuild2(0, new UserCause()).get();
        assertBuildStatusSuccess(b);
        assertFile(true, "foo.txt", b);
        assertFile(true, "subdir/subfoo.txt", b);
        assertFile(true, "deepfoo/a/b/c.log", b);
    }

    public void testCopyWithFilter() throws Exception {
        FreeStyleProject other = createArtifactProject(),
                 p = createProject(other.getName(), "**/bogus*, **/sub*, bogus/**", "",
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
                 p = createProject(other.getName(), "deep*/**", "new/deep/dir",
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
                         p = createProject(other.getName(), "", "", false, false, false);
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
                         p = createProject("$PROJSRC", "$BASE/*.txt", "$TARGET/bar",
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
        FreeStyleProject p = createProject(other.getName() + "/FOO=two", "", "",
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
        p.getBuildersList().add(new CopyArtifact(other.getName() + "/FOO=$FOO",
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

    /** Test copy from workspace instead of artifacts area */
    public void testCopyFromWorkspace() throws Exception {
        FreeStyleProject other = createFreeStyleProject(), p = createFreeStyleProject();
        p.getBuildersList().add(new CopyArtifact(other.getName(), new WorkspaceSelector(),
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
                         p = createProject(other.getName(), "", "", true, false, false);
        assertEquals("before", other.getName(),
                     ((CopyArtifact)p.getBuilders().get(0)).getProjectName());
        String newName = other.getName() + "-new";
        other.renameTo(newName);
        assertEquals("after", newName,
                     ((CopyArtifact)p.getBuilders().get(0)).getProjectName());

        // Test reference to a matrix configuration
        MatrixProject otherm = createMatrixProject(),
                      mp = createMatrixProject();
        mp.getBuildersList().add(new CopyArtifact(otherm.getName() + "/FOO=$FOO",
                                     new SavedBuildSelector(), "", "", false, false));
        assertEquals("before", otherm.getName() + "/FOO=$FOO",
                     ((CopyArtifact)mp.getBuilders().get(0)).getProjectName());
        otherm.renameTo(newName = otherm.getName() + "-new");
        assertEquals("after", newName + "/FOO=$FOO",
                     ((CopyArtifact)mp.getBuilders().get(0)).getProjectName());
    }

    public void testSavedBuildSelector() throws Exception {
        FreeStyleProject other = createArtifactProject(),
                         p = createFreeStyleProject();
        p.getBuildersList().add(new CopyArtifact(other.getName(),
                                    new SavedBuildSelector(), "*.txt", "", false, false));
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
        p.getBuildersList().add(new CopyArtifact(other.getName(),
                                    new SpecificBuildSelector("1"), "*.txt", "", false, false));
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
                                    new SpecificBuildSelector("$BAR"), "*.txt", "", false, false));
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

    public void testFlatten() throws Exception {
        FreeStyleProject other = createArtifactProject(),
                         p = createProject(other.getName(), "", "newdir", false, true, false);
        assertBuildStatusSuccess(other.scheduleBuild2(0, new UserCause()).get());
        FreeStyleBuild b = p.scheduleBuild2(0, new UserCause()).get();
        assertBuildStatusSuccess(b);
        assertFile(true, "newdir/foo.txt", b);
        assertFile(true, "newdir/subfoo.txt", b);
        assertFile(true, "newdir/c.log", b);
    }

    public void testOptional_MissingProject() throws Exception {
        // Missing project still fails even when copy is optional
        FreeStyleProject p = createProject("invalid", "", "", false, false, true);
        assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0, new UserCause()).get());
    }

    public void testOptional_MissingBuild() throws Exception {
        FreeStyleProject other = createFreeStyleProject(),
                         p = createProject(other.getName(), "", "", false, false, true);
        assertBuildStatusSuccess(p.scheduleBuild2(0, new UserCause()).get());
    }

    public void testOptional_MissingArtifact() throws Exception {
        FreeStyleProject other = createFreeStyleProject(),
                         p = createProject(other.getName(), "*.txt", "", false, false, true);
        assertBuildStatusSuccess(other.scheduleBuild2(0, new UserCause()).get());
        assertBuildStatusSuccess(p.scheduleBuild2(0, new UserCause()).get());
    }
}
