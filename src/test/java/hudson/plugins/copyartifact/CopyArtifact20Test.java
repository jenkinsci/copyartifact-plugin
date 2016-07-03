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

package hudson.plugins.copyartifact;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockFolder;
import org.kohsuke.stapler.DataBoundConstructor;

import hudson.EnvVars;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.plugins.copyartifact.CopyArtifact.CopyArtifactPickResult;
import hudson.plugins.copyartifact.filter.AndBuildFilter;
import hudson.plugins.copyartifact.filter.NoBuildFilter;
import hudson.plugins.copyartifact.filter.ParameterizedBuildFilter;
import hudson.plugins.copyartifact.operation.CopyArtifactFiles;
import hudson.plugins.copyartifact.operation.CopyWorkspaceFiles;
import hudson.plugins.copyartifact.selector.Version1BuildSelector;
import hudson.plugins.copyartifact.selector.Version1BuildSelector.MigratedConfiguration;
import hudson.security.GlobalMatrixAuthorizationStrategy;

/**
 * Tests mainly for features introduced since 2.0
 */
public class CopyArtifact20Test {
    @ClassRule
    public final static JenkinsRule j = new JenkinsRule();

    @SuppressWarnings("deprecation")
    private static class DummyVersion1BuildSelector extends Version1BuildSelector {
        private final MigratedConfiguration conf;

        public DummyVersion1BuildSelector(
                @Nonnull BuildSelector buildSelector,
                @CheckForNull BuildFilter buildFilter,
                @CheckForNull CopyArtifactOperation copyArtifactOperation
        ) {
            this.conf = new MigratedConfiguration(buildSelector, buildFilter);
            this.conf.copyArtifactOperation = copyArtifactOperation;
        }
        @Override
        public MigratedConfiguration migrateToVersion2() {
            return conf;
        }
    }

    @Test
    public void testUpgradeFromCopyArtifact10NotApplied() throws Exception {
        CopyArtifact ca = new CopyArtifact("test");
        ca.setSelector(new StatusBuildSelector());
        assertFalse(ca.upgradeFromCopyartifact10());
    }

    @Test
    public void testUpgradeFromCopyArtifact10Applied() throws Exception {
        CopyArtifact ca = new CopyArtifact("test");
        BuildSelector selector = new StatusBuildSelector();
        ca.setSelector(new DummyVersion1BuildSelector(
                selector,
                null,
                null
        ));
        assertTrue(ca.upgradeFromCopyartifact10());
        j.assertEqualDataBoundBeans(selector, ca.getSelector());
    }

    @Test
    public void testUpgradeFromCopyArtifact10NullBuildFilter() throws Exception {
        CopyArtifact ca = new CopyArtifact("test");
        NoBuildFilter filter = new NoBuildFilter();
        ca.setBuildFilter(filter);
        ca.setSelector(new DummyVersion1BuildSelector(
                new StatusBuildSelector(),
                null,
                null
        ));
        assertTrue(ca.upgradeFromCopyartifact10());
        j.assertEqualDataBoundBeans(filter, ca.getBuildFilter());
    }

    @Test
    public void testUpgradeFromCopyArtifact10NoBuildFilter() throws Exception {
        CopyArtifact ca = new CopyArtifact("test");
        NoBuildFilter filter = new NoBuildFilter();
        ca.setBuildFilter(filter);
        ca.setSelector(new DummyVersion1BuildSelector(
                new StatusBuildSelector(),
                new NoBuildFilter(),
                null
        ));
        assertTrue(ca.upgradeFromCopyartifact10());
        j.assertEqualDataBoundBeans(filter, ca.getBuildFilter());
    }

    @Test
    public void testUpgradeFromCopyArtifact10ReplaceBuildFilter() throws Exception {
        CopyArtifact ca = new CopyArtifact("test");
        ParametersBuildFilter filter = new ParametersBuildFilter("param=value");
        ca.setSelector(new DummyVersion1BuildSelector(
                new StatusBuildSelector(),
                filter,
                null
        ));
        assertTrue(ca.upgradeFromCopyartifact10());
        j.assertEqualDataBoundBeans(filter, ca.getBuildFilter());
    }

    @Test
    public void testUpgradeFromCopyArtifact10MergeBuildFilter() throws Exception {
        CopyArtifact ca = new CopyArtifact("test");
        ParametersBuildFilter filter1 = new ParametersBuildFilter("param1=value1");
        ParameterizedBuildFilter filter2 = new ParameterizedBuildFilter("${PARAM}");
        ca.setBuildFilter(filter1);
        ca.setSelector(new DummyVersion1BuildSelector(
                new StatusBuildSelector(),
                filter2,
                null
        ));
        assertTrue(ca.upgradeFromCopyartifact10());

        assertEquals(AndBuildFilter.class, ca.getBuildFilter().getClass());

        // sort elements with class hashes to ensure its order.
        Comparator<BuildFilter> c = new Comparator<BuildFilter>() {
            @Override
            public int compare(BuildFilter o1, BuildFilter o2) {
                return o1.getClass().hashCode() - o2.getClass().hashCode();
            }
        };
        List<BuildFilter> expected = new ArrayList<BuildFilter>();
        expected.add(filter1);
        expected.add(filter2);
        expected.sort(c);

        List<BuildFilter> actual = new ArrayList<BuildFilter>(((AndBuildFilter)(ca.getBuildFilter())).getBuildFilterList());
        actual.sort(c);

        j.assertEqualDataBoundBeans(expected, actual);
    }

    @Test
    public void testUpgradeFromCopyArtifact10NullOperation() throws Exception {
        CopyArtifact ca = new CopyArtifact("test");
        CopyArtifactFiles operation = new CopyArtifactFiles();
        operation.setIncludes("artifact.txt");
        ca.setOperation(operation);

        ca.setSelector(new DummyVersion1BuildSelector(
                new StatusBuildSelector(),
                null,
                null
        ));
        assertTrue(ca.upgradeFromCopyartifact10());
        j.assertEqualDataBoundBeans(operation, ca.getOperation());
    }

    public static class DummyCopyArtifactOperation extends CopyArtifactOperation {
        private final String dummyParam;

        @DataBoundConstructor
        public DummyCopyArtifactOperation(String dummyParam) {
            this.dummyParam = dummyParam;
        }

        public String getDummyParam() {
            return dummyParam;
        }

        @Override
        public Result perform(Run<?, ?> src, CopyArtifactOperationContext context) throws IOException, InterruptedException {
            // do nothing
            return null;
        }
    }

    @Test
    public void testUpgradeFromCopyArtifact10ReplaceOperation() throws Exception {
        CopyArtifact ca = new CopyArtifact("test");
        CopyArtifactFiles operation = new CopyArtifactFiles();
        operation.setIncludes("artifact.txt");
        ca.setOperation(operation);

        DummyCopyArtifactOperation newOperation = new DummyCopyArtifactOperation("test");
        ca.setSelector(new DummyVersion1BuildSelector(
                new StatusBuildSelector(),
                null,
                newOperation
        ));
        assertTrue(ca.upgradeFromCopyartifact10());
        j.assertEqualDataBoundBeans(newOperation, ca.getOperation());
    }

    @Test
    public void testUpgradeFromCopyArtifact10MergeOperation() throws Exception {
        CopyArtifact ca = new CopyArtifact("test");
        CopyArtifactFiles operation = new CopyArtifactFiles();
        operation.setIncludes("artifact.txt");
        ca.setOperation(operation);

        CopyWorkspaceFiles newOperation = new CopyWorkspaceFiles();
        ca.setSelector(new DummyVersion1BuildSelector(
                new StatusBuildSelector(),
                null,
                newOperation
        ));
        assertTrue(ca.upgradeFromCopyartifact10());

        assertEquals(CopyWorkspaceFiles.class, ca.getOperation().getClass());
        // configuration of operation is populated to newOperation
        assertEquals(operation.getIncludes(), ((CopyWorkspaceFiles)ca.getOperation()).getIncludes());
    }

    @Test
    public void testPickBuildToCopyFromBuildFound() throws Exception {
        FreeStyleProject copier = j.createFreeStyleProject();
        FreeStyleProject copiee = j.createFreeStyleProject();
        FreeStyleBuild copierBuild = j.buildAndAssertSuccess(copier);
        FreeStyleBuild copieeBuild = j.buildAndAssertSuccess(copiee);

        CopyArtifactPickContext context = new CopyArtifactPickContext();
        context.setJenkins(j.jenkins);
        context.setCopierBuild(copierBuild);
        context.setListener(TaskListener.NULL);
        context.setEnvVars(new EnvVars());
        context.setVerbose(false);

        context.setProjectName(copiee.getFullName());
        context.setBuildFilter(new NoBuildFilter());

        SpecificBuildSelector selector = new SpecificBuildSelector(Integer.toString(copieeBuild.getNumber()));

        CopyArtifact ca = new CopyArtifact(copiee.getFullName());

        CopyArtifactPickResult r = ca.pickBuildToCopyFrom(selector, context);
        assertEquals(CopyArtifactPickResult.Result.Found, r.result);
        assertEquals(copiee.getFullName(), r.getJob().getFullName());
        assertEquals(copieeBuild.getId(), r.getBuild().getId());
    }

    @Test
    public void testPickBuildToCopyFromBuildNotFound() throws Exception {
        FreeStyleProject copier = j.createFreeStyleProject();
        FreeStyleProject copiee = j.createFreeStyleProject();
        FreeStyleBuild copierBuild = j.buildAndAssertSuccess(copier);

        CopyArtifactPickContext context = new CopyArtifactPickContext();
        context.setJenkins(j.jenkins);
        context.setCopierBuild(copierBuild);
        context.setListener(TaskListener.NULL);
        context.setEnvVars(new EnvVars());
        context.setVerbose(false);

        context.setProjectName(copiee.getFullName());
        context.setBuildFilter(new NoBuildFilter());

        SpecificBuildSelector selector = new SpecificBuildSelector("nosuchbuild");

        CopyArtifact ca = new CopyArtifact(copiee.getFullName());

        CopyArtifactPickResult r = ca.pickBuildToCopyFrom(selector, context);
        assertEquals(CopyArtifactPickResult.Result.BuildNotFound, r.result);
        assertEquals(copiee.getFullName(), r.getJob().getFullName());
    }

    @Test
    public void testPickBuildToCopyFromBuildNotFoundInSameFolder() throws Exception {
        MockFolder f = j.createFolder("folder");
        try {
            FreeStyleProject copier = f.createProject(FreeStyleProject.class, "copier");
            FreeStyleProject copiee = f.createProject(FreeStyleProject.class, "copiee");
            FreeStyleBuild copierBuild = j.buildAndAssertSuccess(copier);

            CopyArtifactPickContext context = new CopyArtifactPickContext();
            context.setJenkins(j.jenkins);
            context.setCopierBuild(copierBuild);
            context.setListener(TaskListener.NULL);
            context.setEnvVars(new EnvVars());
            context.setVerbose(false);

            context.setProjectName(copiee.getName());
            context.setBuildFilter(new NoBuildFilter());

            SpecificBuildSelector selector = new SpecificBuildSelector("nosuchbuild");

            CopyArtifact ca = new CopyArtifact(copiee.getFullName());

            CopyArtifactPickResult r = ca.pickBuildToCopyFrom(selector, context);
            assertEquals(CopyArtifactPickResult.Result.BuildNotFound, r.result);
            assertEquals(copiee.getFullName(), r.getJob().getFullName());
        } finally {
            f.delete();
        }
    }

    @Test
    public void testPickBuildToCopyFromBuildNotFoundInDifferentFolder() throws Exception {
        MockFolder f1 = j.createFolder("folder1");
        MockFolder f2 = j.createFolder("folder2");
        try {
            FreeStyleProject copier = f1.createProject(FreeStyleProject.class, "copier");
            FreeStyleProject copiee = f2.createProject(FreeStyleProject.class, "copiee");
            FreeStyleBuild copierBuild = j.buildAndAssertSuccess(copier);

            CopyArtifactPickContext context = new CopyArtifactPickContext();
            context.setJenkins(j.jenkins);
            context.setCopierBuild(copierBuild);
            context.setListener(TaskListener.NULL);
            context.setEnvVars(new EnvVars());
            context.setVerbose(false);

            context.setProjectName("../folder2/copiee");
            context.setBuildFilter(new NoBuildFilter());

            SpecificBuildSelector selector = new SpecificBuildSelector("nosuchbuild");

            CopyArtifact ca = new CopyArtifact(copiee.getFullName());

            CopyArtifactPickResult r = ca.pickBuildToCopyFrom(selector, context);
            assertEquals(CopyArtifactPickResult.Result.BuildNotFound, r.result);
            assertEquals(copiee.getFullName(), r.getJob().getFullName());
        } finally {
            f1.delete();
            f2.delete();
        }
    }

    @Test
    public void testPickBuildToCopyFromBuildNotFoundInUpperFolder() throws Exception {
        FreeStyleProject copiee = j.createFreeStyleProject();
        MockFolder f = j.createFolder("folder");
        try {
            FreeStyleProject copier = f.createProject(FreeStyleProject.class, "copier");
            FreeStyleBuild copierBuild = j.buildAndAssertSuccess(copier);

            CopyArtifactPickContext context = new CopyArtifactPickContext();
            context.setJenkins(j.jenkins);
            context.setCopierBuild(copierBuild);
            context.setListener(TaskListener.NULL);
            context.setEnvVars(new EnvVars());
            context.setVerbose(false);

            context.setProjectName(String.format("../%s", copiee.getName()));
            context.setBuildFilter(new NoBuildFilter());

            SpecificBuildSelector selector = new SpecificBuildSelector("nosuchbuild");

            CopyArtifact ca = new CopyArtifact(copiee.getFullName());

            CopyArtifactPickResult r = ca.pickBuildToCopyFrom(selector, context);
            assertEquals(CopyArtifactPickResult.Result.BuildNotFound, r.result);
            assertEquals(copiee.getFullName(), r.getJob().getFullName());
        } finally {
            f.delete();
        }
    }

    @Test
    public void testPickBuildToCopyFromBuildNotFoundInSubfolder() throws Exception {
        FreeStyleProject copier = j.createFreeStyleProject();
        MockFolder f = j.createFolder("folder");
        try {
            FreeStyleProject copiee = f.createProject(FreeStyleProject.class, "copiee");
            FreeStyleBuild copierBuild = j.buildAndAssertSuccess(copier);

            CopyArtifactPickContext context = new CopyArtifactPickContext();
            context.setJenkins(j.jenkins);
            context.setCopierBuild(copierBuild);
            context.setListener(TaskListener.NULL);
            context.setEnvVars(new EnvVars());
            context.setVerbose(false);

            context.setProjectName(copiee.getFullName());
            context.setBuildFilter(new NoBuildFilter());

            SpecificBuildSelector selector = new SpecificBuildSelector("nosuchbuild");

            CopyArtifact ca = new CopyArtifact(copiee.getFullName());

            CopyArtifactPickResult r = ca.pickBuildToCopyFrom(selector, context);
            assertEquals(CopyArtifactPickResult.Result.BuildNotFound, r.result);
            assertEquals(copiee.getFullName(), r.getJob().getFullName());
        } finally {
            f.delete();
        }
    }

    @Test
    public void testPickBuildToCopyFromProjectNotFound() throws Exception {
        FreeStyleProject copier = j.createFreeStyleProject();
        FreeStyleBuild copierBuild = j.buildAndAssertSuccess(copier);

        CopyArtifactPickContext context = new CopyArtifactPickContext();
        context.setJenkins(j.jenkins);
        context.setCopierBuild(copierBuild);
        context.setListener(TaskListener.NULL);
        context.setEnvVars(new EnvVars());
        context.setVerbose(false);

        context.setProjectName("nosuchproject");
        context.setBuildFilter(new NoBuildFilter());

        SpecificBuildSelector selector = new SpecificBuildSelector("nosuchbuild");

        CopyArtifact ca = new CopyArtifact("nosuchproject");

        CopyArtifactPickResult r = ca.pickBuildToCopyFrom(selector, context);
        assertEquals(CopyArtifactPickResult.Result.ProjectNotFound, r.result);
    }

    @Test
    public void testPickBuildToCopyFromProjectNotFoundForPermission() throws Exception {
        try {
            j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
            j.jenkins.setAuthorizationStrategy(new GlobalMatrixAuthorizationStrategy());

            FreeStyleProject copier = j.createFreeStyleProject();
            FreeStyleProject copiee = j.createFreeStyleProject();
            FreeStyleBuild copierBuild = j.buildAndAssertSuccess(copier);
            FreeStyleBuild copieeBuild = j.buildAndAssertSuccess(copiee);

            CopyArtifactPickContext context = new CopyArtifactPickContext();
            context.setJenkins(j.jenkins);
            context.setCopierBuild(copierBuild);
            context.setListener(TaskListener.NULL);
            context.setEnvVars(new EnvVars());
            context.setVerbose(false);

            context.setProjectName(copiee.getFullName());
            context.setBuildFilter(new NoBuildFilter());

            SpecificBuildSelector selector = new SpecificBuildSelector(Integer.toString(copieeBuild.getNumber()));

            CopyArtifact ca = new CopyArtifact(copiee.getFullName());

            CopyArtifactPickResult r = ca.pickBuildToCopyFrom(selector, context);
            assertEquals(CopyArtifactPickResult.Result.ProjectNotFound, r.result);
        } finally {
            j.jenkins.setSecurityRealm(null);
            j.jenkins.setAuthorizationStrategy(null);
        }
    }
}
