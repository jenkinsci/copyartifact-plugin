/*
 * The MIT License
 * 
 * Copyright (c) 2013 IKEDA Yasuyuki
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
import hudson.matrix.AxisList;
import hudson.matrix.Combination;
import hudson.matrix.MatrixConfiguration;
import hudson.matrix.MatrixProject;
import hudson.matrix.TextAxis;
import hudson.model.FreeStyleProject;
import hudson.model.JobProperty;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;

import org.apache.commons.lang.StringUtils;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.JenkinsRule.WebClient;
import org.jvnet.hudson.test.MockFolder;

import org.jenkinsci.plugins.workflow.cps.SnippetizerTester;
import org.jenkinsci.plugins.workflow.multibranch.JobPropertyStep;

/**
 * Tests for {@link CopyArtifactPermissionProperty}
 */
public class CopyArtifactPermissionPropertyTest {
    @Rule
    public JenkinsRule j = new JenkinsRule();
    
    @Test
    public void testCopyArtifactPermissionProperty() throws Exception {
        // single
        {
            CopyArtifactPermissionProperty target = new CopyArtifactPermissionProperty("project1");
            assertEquals(Arrays.asList("project1"), target.getProjectNameList());
        }
        
        // multiple
        {
            CopyArtifactPermissionProperty target = new CopyArtifactPermissionProperty("project1,project2,project3");
            assertEquals(Arrays.asList("project1","project2","project3"), target.getProjectNameList());
        }
        
        // single with blanks
        {
            CopyArtifactPermissionProperty target = new CopyArtifactPermissionProperty("  project1  ");
            assertEquals(Arrays.asList("project1"), target.getProjectNameList());
        }
        
        // multiple with blanks
        {
            CopyArtifactPermissionProperty target = new CopyArtifactPermissionProperty("  project1  ,  project2 ,  project3 ");
            assertEquals(Arrays.asList("project1","project2","project3"), target.getProjectNameList());
        }
        
        // mixed
        {
            CopyArtifactPermissionProperty target = new CopyArtifactPermissionProperty(",  project1 ,  project2  , ,,  project3 ,");
            assertEquals(Arrays.asList("project1","project2","project3"), target.getProjectNameList());
        }
        
        // only blank
        {
            CopyArtifactPermissionProperty target = new CopyArtifactPermissionProperty("  ");
            assertEquals(Collections.emptyList(), target.getProjectNameList());
        }
        
        // empty
        {
            CopyArtifactPermissionProperty target = new CopyArtifactPermissionProperty("");
            assertEquals(Collections.emptyList(), target.getProjectNameList());
        }
        
        // null
        {
            CopyArtifactPermissionProperty target = new CopyArtifactPermissionProperty(null);
            assertEquals(Collections.emptyList(), target.getProjectNameList());
        }
    }
    
    @Test
    public void testIsNameMatch() throws Exception {
        // no pattern
        assertTrue(CopyArtifactPermissionProperty.isNameMatch("project1", "project1"));
        assertFalse(CopyArtifactPermissionProperty.isNameMatch("xproject1", "project1"));
        assertFalse(CopyArtifactPermissionProperty.isNameMatch("roject1", "project1"));
        
        // pattern
        assertTrue(CopyArtifactPermissionProperty.isNameMatch("project1", "*"));
        assertTrue(CopyArtifactPermissionProperty.isNameMatch("project1", "project1*"));
        assertTrue(CopyArtifactPermissionProperty.isNameMatch("project1", "project*"));
        assertTrue(CopyArtifactPermissionProperty.isNameMatch("project1", "p*1"));
        assertTrue(CopyArtifactPermissionProperty.isNameMatch("project1", "p*oject*1"));
        assertTrue(CopyArtifactPermissionProperty.isNameMatch("project1", "*project1"));
        assertFalse(CopyArtifactPermissionProperty.isNameMatch("xproject1", "project*"));
        assertFalse(CopyArtifactPermissionProperty.isNameMatch("xproject1", "p*1"));
        assertFalse(CopyArtifactPermissionProperty.isNameMatch("proxject1", "p*oject*1"));
        
        // regex pattern (should not treat as special characters)
        assertTrue(CopyArtifactPermissionProperty.isNameMatch("+).][(\\\\", "+).][(\\\\"));
        
        // null
        assertFalse(CopyArtifactPermissionProperty.isNameMatch("project1", null));
        assertFalse(CopyArtifactPermissionProperty.isNameMatch(null, "project1"));
        assertFalse(CopyArtifactPermissionProperty.isNameMatch(null, null));
    }
    
    @Test
    public void testCanCopyArtifact() throws Exception {
        MockFolder folder = j.jenkins.createProject(MockFolder.class, "folder");
        
        {
            FreeStyleProject copiee = j.createFreeStyleProject();
            FreeStyleProject copier1 = j.createFreeStyleProject();
            FreeStyleProject copier2 = j.createFreeStyleProject();
            FreeStyleProject copier3 = j.createFreeStyleProject();
            copiee.addProperty(new CopyArtifactPermissionProperty(StringUtils.join(Arrays.asList(
                    copier1.getFullName(), copier2.getFullName()
            ), ',')));
            
            assertTrue(CopyArtifactPermissionProperty.canCopyArtifact(copier1, copiee));
            assertTrue(CopyArtifactPermissionProperty.canCopyArtifact(copier2, copiee));
            assertFalse(CopyArtifactPermissionProperty.canCopyArtifact(copier3, copiee));
        }
        
        // same folder
        {
            FreeStyleProject copiee = folder.createProject(FreeStyleProject.class, "sameCopiee");
            FreeStyleProject copier = folder.createProject(FreeStyleProject.class, "sameCopier");
            copiee.addProperty(new CopyArtifactPermissionProperty("sameCopier"));
            
            assertTrue(CopyArtifactPermissionProperty.canCopyArtifact(copier, copiee));
            
            // absolute
            copiee.removeProperty(CopyArtifactPermissionProperty.class);
            copiee.addProperty(new CopyArtifactPermissionProperty("/folder/sameCopier"));
            
            assertTrue(CopyArtifactPermissionProperty.canCopyArtifact(copier, copiee));
        }
        
        // parent folder
        {
            FreeStyleProject copiee = folder.createProject(FreeStyleProject.class, "parentCopiee");
            FreeStyleProject copier = j.jenkins.createProject(FreeStyleProject.class, "parentCopier");
            copiee.addProperty(new CopyArtifactPermissionProperty("../parentCopier"));
            
            assertTrue(CopyArtifactPermissionProperty.canCopyArtifact(copier, copiee));
            
            // absolute
            copiee.removeProperty(CopyArtifactPermissionProperty.class);
            copiee.addProperty(new CopyArtifactPermissionProperty("/parentCopier"));
            
            assertTrue(CopyArtifactPermissionProperty.canCopyArtifact(copier, copiee));
        }
        
        // child folder
        {
            FreeStyleProject copiee = j.jenkins.createProject(FreeStyleProject.class, "childCopiee");
            FreeStyleProject copier = folder.createProject(FreeStyleProject.class, "childCopier");
            copiee.addProperty(new CopyArtifactPermissionProperty(String.format("%s/childCopier", folder.getName())));
            
            assertTrue(CopyArtifactPermissionProperty.canCopyArtifact(copier, copiee));
            
            // absolute
            copiee.removeProperty(CopyArtifactPermissionProperty.class);
            copiee.addProperty(new CopyArtifactPermissionProperty("/folder/childCopier"));
            
            assertTrue(CopyArtifactPermissionProperty.canCopyArtifact(copier, copiee));
        }
    }
    
    @Test
    public void testDescriptorNewInstance() throws Exception {
        WebClient wc = j.createWebClient();
        
        // not configured
        {
            FreeStyleProject p = j.createFreeStyleProject();
            assertNull(p.getProperty(CopyArtifactPermissionProperty.class));
            
            j.submit(wc.getPage(p, "configure").getFormByName("config"));
            
            p = j.jenkins.getItemByFullName(p.getFullName(), FreeStyleProject.class);
            assertNotNull(p);
            assertNull(p.getProperty(CopyArtifactPermissionProperty.class));
        }
        
        // configured
        {
            FreeStyleProject p = j.createFreeStyleProject();
            p.addProperty(new CopyArtifactPermissionProperty("project1"));
            
            j.submit(wc.getPage(p, "configure").getFormByName("config"));
            
            p = j.jenkins.getItemByFullName(p.getFullName(), FreeStyleProject.class);
            assertNotNull(p);
            CopyArtifactPermissionProperty prop = p.getProperty(CopyArtifactPermissionProperty.class);
            assertNotNull(prop);
            assertEquals("project1", prop.getProjectNames());
        }
    }
    
    @Test
    public void testDescriptorCheckNotFoundProjects() throws Exception {
        CopyArtifactPermissionProperty.DescriptorImpl d
                = (CopyArtifactPermissionProperty.DescriptorImpl)j.jenkins.getDescriptor(CopyArtifactPermissionProperty.class);
        j.createFreeStyleProject("project1");
        j.createFreeStyleProject("project2");
        MatrixProject matrix = createMatrixProject("matrix1");
        AxisList axes = new AxisList(new TextAxis("axis1", "value1"));
        matrix.setAxes(axes);
        MatrixConfiguration matrixConf = matrix.getItem(new Combination(axes, "value1"));
        
        MockFolder folder = j.jenkins.createProject(MockFolder.class, "folder");
        folder.createProject(FreeStyleProject.class, "child1");
        folder.createProject(FreeStyleProject.class, "child2");
        
        assertEquals(Collections.emptyList(), d.checkNotFoundProjects("folder/child1", j.jenkins));
        assertEquals(Collections.emptyList(), d.checkNotFoundProjects(" project1,, project2, matrix1,folder/child1, folder/child2", j.jenkins));
        assertEquals(Collections.emptyList(), d.checkNotFoundProjects("child1,child2,../project1", folder));
        assertEquals(Collections.emptyList(), d.checkNotFoundProjects(null, j.jenkins));
        assertEquals(Collections.emptyList(), d.checkNotFoundProjects("", j.jenkins));
        assertEquals(Collections.emptyList(), d.checkNotFoundProjects("project*,*,nosuch*", j.jenkins));
        
        assertEquals(Arrays.asList(matrixConf.getFullDisplayName()), d.checkNotFoundProjects(matrixConf.getFullDisplayName(), j.jenkins));
        assertEquals(Arrays.asList("nosuch1", "nosuch2"), d.checkNotFoundProjects("nosuch1,project1,,nosuch2", j.jenkins));
    }
    
    @Test
    public void testDescriptorDoAutoCompleteProjectNames() throws Exception {
        CopyArtifactPermissionProperty.DescriptorImpl d
                = (CopyArtifactPermissionProperty.DescriptorImpl)j.jenkins.getDescriptor(CopyArtifactPermissionProperty.class);
        FreeStyleProject freestyle = j.createFreeStyleProject("project1");
        MatrixProject matrix = createMatrixProject("matrix1");
        AxisList axes = new AxisList(new TextAxis("axis1", "value1"));
        matrix.setAxes(axes);
        
        MockFolder folder = j.jenkins.createProject(MockFolder.class, "folder");
        FreeStyleProject child = folder.createProject(FreeStyleProject.class, "child1");
        
        assertEquals(Arrays.asList("project1"), d.doAutoCompleteProjectNames("p", freestyle).getValues());
        assertEquals(Arrays.asList("project1"), d.doAutoCompleteProjectNames(" p", freestyle).getValues());
        assertEquals(Arrays.asList("matrix1"), d.doAutoCompleteProjectNames("m", freestyle).getValues());
        assertEquals(Arrays.asList("folder/child1"), d.doAutoCompleteProjectNames("f", freestyle).getValues());
        assertEquals(Arrays.asList("child1"), d.doAutoCompleteProjectNames("c", child).getValues());
        assertEquals(Arrays.asList("../project1"), d.doAutoCompleteProjectNames("../p", child).getValues());
        assertEquals(Collections.emptyList(), d.doAutoCompleteProjectNames("x", freestyle).getValues());
        assertEquals(Collections.emptyList(), d.doAutoCompleteProjectNames("", freestyle).getValues());
    }

    @Test public void configProps() throws Exception {
        JobProperty property = new CopyArtifactPermissionProperty("project1,project2");
        SnippetizerTester tester = new SnippetizerTester(j);
        tester.assertRoundTrip(new JobPropertyStep(Collections.singletonList(property)),
                "properties([copyArtifactPermission('project1,project2')])" );
    }

    /**
     * Creates an empty Matrix project with the provided name.
     *
     * @param name Project name.
     * @return an empty Matrix project with the provided name.
     */
    private MatrixProject createMatrixProject(String name) throws IOException {
        return j.jenkins.createProject(MatrixProject.class, name);
    }

}
