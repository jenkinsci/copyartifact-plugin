/*
 * The MIT License
 *
 * Copyright (c) 2019, CloudBees, Inc.
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
package hudson.plugins.copyartifact.monitor;

import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.Job;
import hudson.model.Run;
import hudson.model.TopLevelItem;
import hudson.model.TopLevelItemDescriptor;
import hudson.model.User;
import hudson.security.ACL;
import hudson.security.ACLContext;
import jenkins.model.Jenkins;
import org.acegisecurity.AccessDeniedException;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.WithoutJenkins;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.SortedMap;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LegacyMonitorDataTest {
    
    @Rule
    public JenkinsRule j = new JenkinsRule();
    
    @Test
    @WithoutJenkins
    public void simpleAddJob() {
        LegacyMonitorData data = new LegacyMonitorData();
        assertTrue(data.isEmpty());
        
        data.addLegacyJob(new SimpleJob("copier"), new SimpleJob("copiee"), new Date(), "tester");
        
        assertFalse(data.isEmpty());
        assertTrue(data.hasJobFullName("copier"));
    }
    
    @Test
    @WithoutJenkins
    public void advancedAddJob() {
        LegacyMonitorData data = new LegacyMonitorData();
        assertTrue(data.isEmpty());
        
        data.addLegacyJob(new SimpleJob("copier1"), new SimpleJob("copiee1"), new Date(), "tester1");
        data.addLegacyJob(new SimpleJob("copier1"), new SimpleJob("copiee2"), new Date(), "tester2");
        data.addLegacyJob(new SimpleJob("copier2"), new SimpleJob("copiee1"), new Date(), "tester3");
        
        assertFalse(data.isEmpty());
        assertTrue(data.hasJobFullName("copier1"));
        assertTrue(data.hasJobFullName("copier2"));
        assertTrue(data.hasJobFullName("copiee1"));
        assertTrue(data.hasJobFullName("copiee2"));
    }
    
    @Test
    @WithoutJenkins
    public void simpleRenameJob() {
        LegacyMonitorData data = new LegacyMonitorData();
        data.addLegacyJob(new SimpleJob("copier"), new SimpleJob("copiee"), new Date(), "tester");
        
        data.onJobRename("copier", "copier2");
        
        assertFalse(data.isEmpty());
        // was renamed
        assertFalse(data.hasJobFullName("copier"));
        assertTrue(data.hasJobFullName("copier2"));
    }
    
    @Test
    @WithoutJenkins
    public void advancedRenameJob() {
        LegacyMonitorData data = new LegacyMonitorData();
        data.addLegacyJob(new SimpleJob("copier1"), new SimpleJob("copiee1"), new Date(), "tester1");
        data.addLegacyJob(new SimpleJob("copier1"), new SimpleJob("copiee2"), new Date(), "tester2");
        data.addLegacyJob(new SimpleJob("copier2"), new SimpleJob("copiee1"), new Date(), "tester3");
        
        data.onJobRename("copier1", "copier1-rename");
        
        assertFalse(data.hasJobFullName("copier1"));
        assertTrue(data.hasJobFullName("copier1-rename"));
        assertTrue(data.hasJobFullName("copier2"));
        assertTrue(data.hasJobFullName("copiee1"));
        assertTrue(data.hasJobFullName("copiee2"));
    }
    
    @Test
    @WithoutJenkins
    public void expertRenameJob() {
        LegacyMonitorData data = new LegacyMonitorData();
        data.addLegacyJob(new SimpleJob("a1"), new SimpleJob("c1"), new Date(), "tester1");
        data.addLegacyJob(new SimpleJob("a1"), new SimpleJob("d1"), new Date(), "tester2");
        data.addLegacyJob(new SimpleJob("b1"), new SimpleJob("c1"), new Date(), "tester3");
        
        // ensure all keys are updated
        data.onJobRename("b1", "b2");
        data.onJobRename("b2", "b3");
        data.onJobRename("c1", "c2");
        data.onJobRename("a1", "a2");
        data.onJobRename("c2", "c3");
        data.onJobRename("b3", "b4");
        data.onJobRename("d1", "d2");
        data.onJobRename("a2", "a3");
        
        assertTrue(data.hasJobFullName("d2"));
        assertTrue(data.hasJobFullName("b4"));
        assertTrue(data.hasJobFullName("c3"));
        assertTrue(data.hasJobFullName("a3"));
        
        assertFalse(data.hasJobFullName("a2"));
        assertFalse(data.hasJobFullName("a1"));
        assertFalse(data.hasJobFullName("d1"));
        assertFalse(data.hasJobFullName("b3"));
        assertFalse(data.hasJobFullName("b2"));
        assertFalse(data.hasJobFullName("b1"));
        assertFalse(data.hasJobFullName("c2"));
        assertFalse(data.hasJobFullName("c1"));
    }
    
    @Test
    @WithoutJenkins
    public void multipleTimeAddSameJob() {
        LegacyMonitorData data = new LegacyMonitorData();
        
        SimpleJob copier = new SimpleJob("copier1");
        SimpleJob copiee1 = new SimpleJob("copiee1");
        SimpleJob copiee2 = new SimpleJob("copiee2");
        
        data.addLegacyJob(copier, copiee1, new Date(), "tester1");
        data.addLegacyJob(copier, copiee2, new Date(), "tester2");
        
        LegacyMonitorData.JobKey key1 = LegacyMonitorData.buildKey(copiee1.getFullName(), copier.getFullName());
        LegacyMonitorData.JobKey key2 = LegacyMonitorData.buildKey(copiee2.getFullName(), copier.getFullName());
        
        LegacyMonitorData.LegacyBuildStorage legacyBuildStorage1 = data.getLegacyJobInfos().get(key1);
        LegacyMonitorData.LegacyBuildStorage legacyBuildStorage2 = data.getLegacyJobInfos().get(key2);
        
        assertThat(legacyBuildStorage1.getNumOfBuild(), is(1));
        assertThat(legacyBuildStorage2.getNumOfBuild(), is(1));
        
        data.addLegacyJob(copier, copiee2, new Date(), "tester2");
        data.addLegacyJob(copier, copiee2, new Date(), "tester2");
        
        legacyBuildStorage1 = data.getLegacyJobInfos().get(key1);
        legacyBuildStorage2 = data.getLegacyJobInfos().get(key2);
        
        assertThat(legacyBuildStorage1.getNumOfBuild(), is(1));
        assertThat(legacyBuildStorage2.getNumOfBuild(), is(3));
    }
    
    @Test
    @WithoutJenkins
    public void removeJob() {
        LegacyMonitorData data = new LegacyMonitorData();
        
        data.addLegacyJob(new SimpleJob("copier1"), new SimpleJob("copiee1"), new Date(), "tester1");
        data.addLegacyJob(new SimpleJob("copier1"), new SimpleJob("copiee2"), new Date(), "tester2");
        data.addLegacyJob(new SimpleJob("copier2"), new SimpleJob("copiee1"), new Date(), "tester3");
        
        data.onJobRename("copier1", "copier1-rename");
        
        assertFalse(data.hasJobFullName("copier1"));
        assertTrue(data.hasJobFullName("copier1-rename"));
        assertTrue(data.hasJobFullName("copier2"));
        assertTrue(data.hasJobFullName("copiee1"));
        assertTrue(data.hasJobFullName("copiee2"));
    }
    
    @Test
    public void buildForCurrentUser_fullAccess() throws Exception {
        LegacyMonitorData data = new LegacyMonitorData();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy.MM.dd");
        Date day1 = sdf.parse("2019.01.01");
        Date day2 = sdf.parse("2019.02.05");
        Date day3 = sdf.parse("2019.03.16");
        
        SimpleJob copier1 = new SimpleJob(j.jenkins, "copier1");
        SimpleJob copier2 = new SimpleJob(j.jenkins, "copier2");
        SimpleJob copiee1 = new SimpleJob(j.jenkins, "copiee1");
        SimpleJob copiee2 = new SimpleJob(j.jenkins, "copiee2");
        j.jenkins.putItem(copier1);
        j.jenkins.putItem(copier2);
        j.jenkins.putItem(copiee1);
        j.jenkins.putItem(copiee2);
        
        data.addLegacyJob(copier2, copiee1, day3, "tester3");
        data.addLegacyJob(copier1, copiee1, day2, "tester1");
        data.addLegacyJob(copier1, copiee2, day1, "tester2");
        
        List<LegacyMonitorData.LegacyBuildInfoModel> model = data.buildDataForCurrentUser();
        
        assertThat(model, hasSize(2));
        assertThat(model.get(0).getJobFrom().getJobFullName(), is("copiee1"));
        assertThat(model.get(0).getJobToList(), hasSize(2));
        // order by date
        assertThat(model.get(0).getJobToList().get(0).getJobTo().getJobFullName(), is("copier1"));
        assertThat(model.get(0).getJobToList().get(0).getJobTo().isRegularAccess(), is(true));
        assertThat(model.get(0).getJobToList().get(0).getJobTo().getValidJob(), is(copier1));
        assertThat(model.get(0).getJobToList().get(1).getJobTo().getJobFullName(), is("copier2"));
        assertThat(model.get(0).getJobToList().get(1).getJobTo().isRegularAccess(), is(true));
        assertThat(model.get(0).getJobToList().get(1).getJobTo().getValidJob(), is(copier2));
        
        assertThat(model.get(1).getJobFrom().getJobFullName(), is("copiee2"));
        assertThat(model.get(1).getJobToList(), hasSize(1));
        assertThat(model.get(1).getJobToList().get(0).getJobTo().getJobFullName(), is("copier1"));
    }
    
    @Test
    public void buildForCurrentUser_noAccessAndNonExistent() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        MockAuthorizationStrategy auth = new MockAuthorizationStrategy()
            .grant(Jenkins.READ).onRoot().to("manager", "user");
        j.jenkins.setAuthorizationStrategy(auth);
        
        LegacyMonitorData data = new LegacyMonitorData();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy.MM.dd");
        Date day1 = sdf.parse("2019.01.01");
        Date day2 = sdf.parse("2019.02.05");
        Date day3 = sdf.parse("2019.03.16");
        
        SimpleJob copier1 = new SimpleJob(j.jenkins, "copier1");
        SimpleJob copier2 = new SimpleJob(j.jenkins, "copier2");
        
        auth.grant(Item.READ).onItems(copier1).to("manager", "user");
        // only SYSTEM has access to it
        auth.grant(Item.READ).onItems(copier2).to("manager");
        
        SimpleJob copiee1 = new SimpleJob(j.jenkins, "copiee1");
        SimpleJob copiee2 = new SimpleJob(j.jenkins, "copiee2");
        
        j.jenkins.putItem(copier1);
        j.jenkins.putItem(copier2);
        j.jenkins.putItem(copiee1);
        // simulate the deletion of the copiee2
        // j.jenkins.putItem(copiee2);
        
        data.addLegacyJob(copier2, copiee1, day3, "tester3");
        data.addLegacyJob(copier1, copiee1, day2, "tester1");
        data.addLegacyJob(copier1, copiee2, day1, "tester2");
        
        List<LegacyMonitorData.LegacyBuildInfoModel> modelUser;
        { // user
            try (ACLContext acl = ACL.as(User.getById("user", true))) {
                modelUser = data.buildDataForCurrentUser();
            }
            regularModelCheck(modelUser);
    
            // job exists but "user" does not have access to it
            assertThat(modelUser.get(0).getJobToList().get(1).getJobTo().getValidJob(), is(copier2));
            assertThat(modelUser.get(0).getJobToList().get(1).getJobTo().isRegularAccess(), is(false));
            // job does not exist
            assertThat(modelUser.get(1).getJobFrom().getValidJob(), nullValue());
        }
        
        List<LegacyMonitorData.LegacyBuildInfoModel> modelManager;
        { // manager
            try (ACLContext acl = ACL.as(User.getById("manager", true))) {
                modelManager = data.buildDataForCurrentUser();
            }
            regularModelCheck(modelManager);
    
            // job exists and "manager" has access to it
            assertThat(modelManager.get(0).getJobToList().get(1).getJobTo().getValidJob(), is(copier2));
            assertThat(modelManager.get(0).getJobToList().get(1).getJobTo().isRegularAccess(), is(true));
            // job does not exist
            assertThat(modelManager.get(1).getJobFrom().getValidJob(), nullValue());
        }
    }
    
    private void regularModelCheck(List<LegacyMonitorData.LegacyBuildInfoModel> model) {
        assertThat(model, hasSize(2));
        assertThat(model.get(0).getJobFrom().getJobFullName(), is("copiee1"));
        assertThat(model.get(0).getJobToList(), hasSize(2));
        // order by date
        assertThat(model.get(0).getJobToList().get(0).getJobTo().getJobFullName(), is("copier1"));
        assertThat(model.get(0).getJobToList().get(1).getJobTo().getJobFullName(), is("copier2"));
        
        assertThat(model.get(1).getJobFrom().getJobFullName(), is("copiee2"));
        assertThat(model.get(1).getJobToList(), hasSize(1));
        assertThat(model.get(1).getJobToList().get(0).getJobTo().getJobFullName(), is("copier1"));
    }
    
    private class SimpleJob extends Job<SimpleJob, SimpleRun> implements TopLevelItem {
        private SimpleJob(String name) {
            super(new SimpleItemGroup(), name);
        }
        
        private SimpleJob(ItemGroup parent, String name) {
            super(parent, name);
        }
        
        @Override
        public boolean isBuildable() {
            return false;
        }
        
        @Override
        protected SortedMap<Integer, SimpleRun> _getRuns() {
            return null;
        }
        
        @Override
        protected void removeRun(SimpleRun run) {
            
        }
        
        @Override
        public TopLevelItemDescriptor getDescriptor() {
            return null;
        }
    
        @Override 
        public synchronized void save() {
            // do nothing to avoid problem when using addProperty
        }
    }
    
    private class SimpleRun extends Run<SimpleJob, SimpleRun> {
        protected SimpleRun(@NonNull SimpleJob job) throws IOException {
            super(job);
        }
    }
    
    private class SimpleItemGroup implements ItemGroup<SimpleJob> {
        @Override
        public String getFullName() {
            return "";
        }
        
        @Override
        public String getFullDisplayName() {
            return "";
        }
        
        @Override
        public Collection<SimpleJob> getItems() {
            return null;
        }
        
        @Override
        public String getUrl() {
            return null;
        }
        
        @Override
        public String getUrlChildPrefix() {
            return null;
        }
        
        @Override
        public @CheckForNull SimpleJob getItem(String name) throws AccessDeniedException {
            return null;
        }
        
        @Override
        public File getRootDirFor(SimpleJob child) {
            return null;
        }
        
        @Override
        public void onDeleted(SimpleJob item) {
            
        }
        
        @Override
        public String getDisplayName() {
            return null;
        }
        
        @Override
        public File getRootDir() {
            return null;
        }
        
        @Override
        public void save() {
            
        }
    }
}
