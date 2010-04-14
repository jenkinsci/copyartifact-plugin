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
import hudson.tasks.ArtifactArchiver;
import hudson.tasks.Builder;
import java.io.IOException;
import java.util.Collections;
import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.UnstableBuilder;

/**
 * Test interaction of copyartifact plugin with Hudson core.
 * @author Alan.Harder@sun.com
 */
public class CopyArtifactTest extends HudsonTestCase {

    private FreeStyleProject createProject(String otherProject,
            String filter, String target, boolean stable) throws IOException {
        FreeStyleProject p = createFreeStyleProject();
        p.getBuildersList().add(new CopyArtifact(otherProject, filter, target, stable));
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
        FreeStyleProject p = createProject("invalid", "", "", false);
        assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0, new UserCause()).get());
    }

    public void testMissingBuild() throws Exception {
        FreeStyleProject other = createFreeStyleProject(),
                         p = createProject(other.getName(), "", "", false);
        assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0, new UserCause()).get());
    }

    public void testMissingStableBuild() throws Exception {
        FreeStyleProject other = createFreeStyleProject(),
                         p = createProject(other.getName(), "", "", true);
        // Make an unstable build in "other"
        other.getBuildersList().add(new UnstableBuilder());
        assertBuildStatus(Result.UNSTABLE, other.scheduleBuild2(0, new UserCause()).get());
        assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0, new UserCause()).get());
    }

    public void testCopyAll() throws Exception {
        FreeStyleProject other = createArtifactProject(),
                         p = createProject(other.getName(), "", "", false);
        assertBuildStatusSuccess(other.scheduleBuild2(0, new UserCause()).get());
        FreeStyleBuild b = p.scheduleBuild2(0, new UserCause()).get();
        assertBuildStatusSuccess(b);
        assertFile(true, "foo.txt", b);
        assertFile(true, "subdir/subfoo.txt", b);
        assertFile(true, "deepfoo/a/b/c.log", b);
    }

    public void testCopyWithFilter() throws Exception {
        FreeStyleProject other = createArtifactProject(),
                 p = createProject(other.getName(), "**/bogus*, **/sub*, bogus/**", "", false);
        assertBuildStatusSuccess(other.scheduleBuild2(0, new UserCause()).get());
        FreeStyleBuild b = p.scheduleBuild2(0, new UserCause()).get();
        assertBuildStatusSuccess(b);
        assertFile(false, "foo.txt", b);
        assertFile(true, "subdir/subfoo.txt", b);
        assertFile(false, "deepfoo/a/b/c.log", b);
    }

    public void testCopyToTarget() throws Exception {
        FreeStyleProject other = createArtifactProject(),
                 p = createProject(other.getName(), "deep*/**", "new/deep/dir", true);
        assertBuildStatusSuccess(other.scheduleBuild2(0, new UserCause()).get());
        FreeStyleBuild b = p.scheduleBuild2(0, new UserCause()).get();
        assertBuildStatusSuccess(b);
        assertFile(false, "foo.txt", b);
        assertFile(false, "new/deep/dir/foo.txt", b);
        assertFile(true, "new/deep/dir/deepfoo/a/b/c.log", b);
    }

    public void testCopyToSlave() throws Exception {
        DumbSlave node = createSlave();
        FreeStyleProject other = createArtifactProject(),
                         p = createProject(other.getName(), "", "", false);
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
                         p = createProject("$PROJSRC", "$BASE/*.txt", "$TARGET/bar", false);
        assertBuildStatusSuccess(other.scheduleBuild2(0, new UserCause()).get());
        FreeStyleBuild b = p.scheduleBuild2(0, new UserCause(),
                new ParametersAction(new StringParameterValue("PROJSRC", other.getName()),
                                     new StringParameterValue("BASE", "*r"),
                                     new StringParameterValue("TARGET", "foo"))).get();
        assertBuildStatusSuccess(b);
        assertFile(false, "foo/bar/foo.txt", b);
        assertFile(true, "foo/bar/subdir/subfoo.txt", b);
    }

    /** Test copying artifacts from a particluar configuration of a matrix job */
    public void testMatrixJob() throws Exception {
        MatrixProject other = createMatrixArtifactProject();
        FreeStyleProject p = createProject(other.getName() + "/FOO=two", "", "", true);
        assertBuildStatusSuccess(other.scheduleBuild2(0, new UserCause()).get());
        FreeStyleBuild b = p.scheduleBuild2(0, new UserCause()).get();
        assertBuildStatusSuccess(b);
        assertFile(true, "foo.txt", b);
        assertFile(true, "two.txt", b);
        assertFile(true, "subdir/subfoo.txt", b);
        assertFile(true, "deepfoo/a/b/c.log", b);
    }

    /** Test artfiact copy between matrix jobs, for artifact from matching axis */
    public void testMatrixToMatrix() throws Exception {
        MatrixProject other = createMatrixArtifactProject(),
                      p = createMatrixProject();
        p.setAxes(new AxisList(new Axis("FOO", "one", "two"))); // should match other job
        p.getBuildersList().add(
                new CopyArtifact(other.getName() + "/FOO=$FOO", "", "", true));
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
}
