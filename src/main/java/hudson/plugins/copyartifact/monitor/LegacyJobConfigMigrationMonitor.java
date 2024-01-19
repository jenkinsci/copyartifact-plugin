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

import com.cloudbees.hudson.plugins.folder.computed.ComputedFolder;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.BulkChange;
import hudson.Extension;
import hudson.XmlFile;
import hudson.model.AbstractProject;
import hudson.model.AdministrativeMonitor;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.Items;
import hudson.model.Job;
import hudson.model.Saveable;
import hudson.model.listeners.ItemListener;
import hudson.model.listeners.SaveableListener;
import hudson.plugins.copyartifact.CopyArtifactPermissionProperty;
import hudson.security.ACL;
import hudson.security.ACLContext;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.http.HttpServletResponse;
import jenkins.model.Jenkins;
import net.jcip.annotations.GuardedBy;
import org.apache.commons.io.IOUtils;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.interceptor.RequirePOST;
import org.kohsuke.stapler.json.JsonBody;
import org.kohsuke.stapler.lang.Klass;

/**
 * Monitor the list of legacy job configuration that require administrator attention
 *
 * @since 1.44
 */
@Extension
@Symbol("copyArtifactLegacyJobConfigMigration")
public class LegacyJobConfigMigrationMonitor extends AdministrativeMonitor implements Saveable {
    private static final Logger LOGGER = Logger.getLogger(LegacyJobConfigMigrationMonitor.class.getName());

    public static final String ID = "copyArtifactLegacyJobConfigMigration";

    private final transient ReadWriteLock lock = new ReentrantReadWriteLock(true);

    @GuardedBy("lock")
    private final LegacyMonitorData data = new LegacyMonitorData();

    /**
     * ctor.
     */
    public LegacyJobConfigMigrationMonitor() {
        super(ID);
        load();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDisplayName() {
        return Messages.LegacyJobConfigMigrationMonitor_displayName();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isActivated() {
        // Note: activated even in Production mode.
        // Administrators may want to update configurations
        // after changing the mode to Production mode.
        Lock readLock = lock.readLock();
        readLock.lock();
        try {
            return !data.isEmpty();
        } finally {
            readLock.unlock();
        }
    }

    /**
     * @return data holding the list of jobs to warn.
     */
    @Restricted(NoExternalUse.class)
    /* Visible for testing */ LegacyMonitorData getData() {
        return data;
    }

    /**
     * Load recorded warnings from serialized data.
     */
    public void load() {
        XmlFile file = getConfigXml();
        if (!file.exists()) {
            return;
        }

        Lock writeLock = lock.writeLock();
        writeLock.lock();
        try {
            file.unmarshal(this);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to load " + file, e);
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Save recorded warnings to the file.
     *
     * @see hudson.model.Saveable#save()
     */
    @Override
    public void save() {
        if (BulkChange.contains(this)) {
            return;
        }
        XmlFile config = getConfigXml();

        Lock readLock = lock.readLock();
        readLock.lock();
        try {
            config.write(this);
            SaveableListener.fireOnChange(this, config);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to save " + config, e);
        } finally {
            readLock.unlock();
        }
    }

    /**
     * @return the path of the xml file to save warnings.
     */
    @NonNull
    public static XmlFile getConfigXml() {
        return new XmlFile(
                new File(Jenkins.get().getRootDir(), LegacyJobConfigMigrationMonitor.class.getName() + ".xml"));
    }

    /**
     * Used by Jelly
     *
     * @return the list of the source and destination jobs to warn.
     */
    @NonNull
    @Restricted(DoNotUse.class)
    public List<LegacyMonitorData.LegacyBuildInfoModel> getAllJobInformation() {
        Lock readLock = lock.readLock();
        readLock.lock();
        try {
            return data.buildDataForCurrentUser();
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Add information of the source job and destination job that would fail in Production mode.
     *
     * @param jobTryingToCopy the destination job.
     * @param jobToBeCopiedFrom the source job.
     * @param lastBuildDate the build timestamp.
     * @param username the user name the destination job ran as.
     */
    public void addLegacyJob(
            @NonNull Job<?, ?> jobTryingToCopy,
            @NonNull Job<?, ?> jobToBeCopiedFrom,
            @NonNull Date lastBuildDate,
            @NonNull String username) {
        LOGGER.log(Level.FINE, "Adding a legacy job to the monitor: from {0} to {1}", new Object[] {
            jobToBeCopiedFrom.getFullName(), jobTryingToCopy.getFullName()
        });

        Lock writeLock = lock.writeLock();
        writeLock.lock();
        try {
            data.addLegacyJob(jobTryingToCopy, jobToBeCopiedFrom, lastBuildDate, username);
            save();
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Remove information of the source job and destination job that would fail in Production mode.
     *
     * @param jobTryingToCopy the destination job.
     * @param jobToBeCopiedFrom the source job.
     */
    public void removeLegacyJob(@NonNull Job<?, ?> jobTryingToCopy, @NonNull Job<?, ?> jobToBeCopiedFrom) {
        String jobToBeCopiedFromName = jobToBeCopiedFrom.getFullName();
        String jobTryingToCopyName = jobTryingToCopy.getFullName();
        Lock writeLock = lock.writeLock();
        writeLock.lock();
        try {
            if (data.removeLegacyJob(jobToBeCopiedFromName, jobTryingToCopyName)) {
                save();
                LOGGER.log(Level.FINE, "Removed a legacy job form the monitor: from {0} to {1}", new Object[] {
                    jobToBeCopiedFromName, jobTryingToCopyName
                });
            }
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Called from jelly (stapler).
     *
     * Ignore selected items.
     *
     * @param content selected items.
     * @return the response.
     */
    @RequirePOST
    @Restricted(DoNotUse.class)
    public HttpResponse doIgnoreAllSelected(@JsonBody MigrateAllSelectedModel content) {
        if (content.values == null) {
            return HttpResponses.ok();
        }
        try (BulkChange bc = new BulkChange(this)) {
            for (MigrateAllSelectedFromAndTo value : content.values) {
                if (value.jobFrom == null || value.jobTo == null) {
                    continue;
                }
                this.data.removeLegacyJob(value.jobFrom, value.jobTo);
            }
            bc.commit();
        } catch (IOException e) {
            LOGGER.log(Level.INFO, "Problem during bulk save", e);
        }

        return HttpResponses.ok();
    }

    /**
     * Called from jelly (stapler).
     *
     * Apply automatic migrations to selected items.
     * Add {@link CopyArtifactPermissionProperty} to the source jobs.
     *
     * @param content selected items.
     * @return the response.
     */
    @RequirePOST
    @Restricted(DoNotUse.class)
    public HttpResponse doMigrateAllSelected(@JsonBody MigrateAllSelectedModel content) {
        if (content.values == null) {
            return HttpResponses.ok();
        }
        // to avoid issue when a project is not visible for the regular admin
        // like with early version of ProjectMatrix
        try (ACLContext acl = ACL.as(ACL.SYSTEM)) {
            try (BulkChange bc = new BulkChange(this)) {
                for (MigrateAllSelectedFromAndTo value : content.values) {
                    if (value.jobFrom == null || value.jobTo == null) {
                        continue;
                    }
                    if (applyAutoMigration(value.jobFrom, value.jobTo)) {
                        this.data.removeLegacyJob(value.jobFrom, value.jobTo);
                    }
                }

                bc.commit();
            } catch (IOException e) {
                LOGGER.log(Level.INFO, "Problem during bulk save", e);
            }
        }
        return HttpResponses.ok();
    }

    private static Job<?, ?> getRootProject(Job<?, ?> job) {
        if (job instanceof AbstractProject) {
            return ((AbstractProject<?, ?>) job).getRootProject();
        } else {
            return job;
        }
    }

    /* Visible for testing */
    @Restricted(NoExternalUse.class)
    boolean applyAutoMigration(@NonNull String jobFromName, @NonNull String jobToName) throws IOException {
        Jenkins jenkins = Jenkins.get();
        Job<?, ?> jobFrom = getRootProject(jenkins.getItemByFullName(jobFromName, Job.class));
        Job<?, ?> jobTo = getRootProject(jenkins.getItemByFullName(jobToName, Job.class));
        if (jobFrom == null) {
            LOGGER.log(
                    Level.INFO,
                    "Project (from) {0} not found, corresponds with (to) {1}, it was perhaps renamed or removed recently",
                    new Object[] {jobFromName, jobToName});
            return false;
        }
        if (jobTo == null) {
            LOGGER.log(
                    Level.INFO,
                    "Project (to) {0} not found, corresponds with (from) {1}, it was perhaps renamed or removed recently",
                    new Object[] {jobToName, jobFromName});
            return false;
        }

        if (!canMigrate(jobFrom)) {
            LOGGER.log(Level.INFO, "Auto-migration is not applicable to project (from) {0}.", jobFromName);
            return false;
        }

        CopyArtifactPermissionProperty property = jobFrom.getProperty(CopyArtifactPermissionProperty.class);
        if (property == null) {
            String relativeName = jobTo.getRelativeNameFrom(jobFrom);
            jobFrom.addProperty(new CopyArtifactPermissionProperty(relativeName));

            LOGGER.log(Level.INFO, "Project {0} is now authorized to copy from {1} as {2}", new Object[] {
                jobFromName, jobToName, relativeName
            });
        } else {
            if (property.canCopiedBy(jobTo)) {
                LOGGER.log(
                        Level.FINE, "Project {0} was already authorized by {1}", new Object[] {jobToName, jobFromName});
            } else {
                // see CopyArtifactPermissionProperty#canCopiedBy(Job)
                String relativeName = jobTo.getRelativeNameFrom(jobFrom);
                String newProjectNames = property.getProjectNames() + "," + relativeName;

                jobFrom.removeProperty(CopyArtifactPermissionProperty.class);
                jobFrom.addProperty(new CopyArtifactPermissionProperty(newProjectNames));

                LOGGER.log(Level.INFO, "Project {0} is now authorized to copy from {1} as {2}", new Object[] {
                    jobFromName, jobToName, relativeName
                });
            }
        }

        return true;
    }

    /*package*/ static boolean canMigrate(@CheckForNull Job<?, ?> job) {
        if (job == null) {
            return false;
        }
        Jenkins jenkins = Jenkins.getInstanceOrNull();
        if (jenkins == null) {
            return false;
        }

        if (jenkins.getPlugin("cloudbees-folder") == null) {
            return true;
        }
        ItemGroup<?> parent = job.getParent();
        while (parent instanceof Job) {
            parent = ((Job<?, ?>) parent).getParent();
        }
        return !(parent instanceof ComputedFolder<?>);
    }

    /**
     * Used from jelly (stapler) to hold selected items.
     */
    @Restricted(NoExternalUse.class)
    @SuppressFBWarnings(value = "UWF_NULL_FIELD", justification = "Fields are set from the json in the HTTP request")
    public static final class MigrateAllSelectedModel {
        public MigrateAllSelectedFromAndTo[] values = null;
    }

    /**
     * Used from jelly (stapler) to hold a selected item.
     */
    @Restricted(NoExternalUse.class)
    @SuppressFBWarnings(value = "UWF_NULL_FIELD", justification = "Fields are set from the json in the HTTP request")
    public static final class MigrateAllSelectedFromAndTo {
        public String jobFrom = null;
        public String jobTo = null;
    }

    /**
     * @return the singleton instance.
     */
    @NonNull
    public static LegacyJobConfigMigrationMonitor get() {
        AdministrativeMonitor monitor = Jenkins.get().getAdministrativeMonitor(ID);
        if (monitor instanceof LegacyJobConfigMigrationMonitor) {
            return (LegacyJobConfigMigrationMonitor) monitor;
        } else {
            throw new AssertionError(
                    "The desired monitor is missing: " + LegacyJobConfigMigrationMonitor.class.getName());
        }
    }

    /**
     * Called from jelly (stapler).
     *
     * Returns the contents of help-detailedSteps.html
     *
     * @param rsp to write contents to.
     * @throws IOException servlet communication errors.
     */
    @Restricted(DoNotUse.class)
    public void doHelpDetailedSteps(StaplerResponse rsp) throws IOException {
        URL url = getStaticResourceUrl("help-detailedSteps");
        if (url == null) {
            rsp.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        rsp.setContentType("text/html; charset=UTF-8");
        IOUtils.copy(url.openStream(), rsp.getWriter(), "UTF-8");
    }

    /**
     * Get html resource.
     *
     * This is just as Descriptor#getStaticHelpUrl
     *
     * @param base the resource name without locales and the extension.
     * @return the url of the resource.
     */
    private URL getStaticResourceUrl(String base) {
        Locale locale = Stapler.getCurrentRequest().getLocale();
        // allow to load html files.
        Klass<?> c = Klass.java(getClass());

        URL url;
        url = c.getResource(
                base + '_' + locale.getLanguage() + '_' + locale.getCountry() + '_' + locale.getVariant() + ".html");
        if (url != null) {
            return url;
        }
        url = c.getResource(base + '_' + locale.getLanguage() + '_' + locale.getCountry() + ".html");
        if (url != null) {
            return url;
        }
        url = c.getResource(base + '_' + locale.getLanguage() + ".html");
        if (url != null) {
            return url;
        }

        return c.getResource(base + ".html");
    }

    /**
     * To keep track of the job rename that occurs while the application is running
     */
    @Extension
    public static final class ListenerImpl extends ItemListener {
        /**
         * {@inheritDoc}
         */
        @Override
        public void onRenamed(Item item, String oldName, String newName) {
            String oldFullName = Items.getCanonicalName(item.getParent(), oldName);
            String newFullName = Items.getCanonicalName(item.getParent(), newName);

            LegacyJobConfigMigrationMonitor monitor = LegacyJobConfigMigrationMonitor.get();
            Lock writeLock = monitor.lock.writeLock();
            writeLock.lock();
            try {
                monitor.getData().onJobRename(oldFullName, newFullName);
                monitor.save();
            } finally {
                writeLock.unlock();
            }
        }
    }
}
