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

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.Job;
import hudson.security.ACL;
import hudson.security.ACLContext;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import jenkins.model.Jenkins;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Responsible of the data for the monitor.
 *
 * Holds the jobs to warn about the failure in Production mode,
 * and also holds runtime information like names of source jobs.
 *
 * @since 1.44
 */
@Restricted(NoExternalUse.class)
public class LegacyMonitorData {
    private static final Logger LOGGER = Logger.getLogger(LegacyMonitorData.class.getName());

    /**
     * Map from the pair of the source job and the destination job
     * to the information of the build that would fail in Production mode.
     *
     * buildKey() is used to create the key of this map
     */
    @NonNull
    private Map<JobKey, LegacyBuildStorage> legacyJobInfos;

    /**
     * Store all the keys that are using a particular job fullName,
     * considering both as source jobs and destination jobs.
     * This is used for the rename part.
     */
    @NonNull
    private Map<String, List<JobKey>> fullNameToKey;

    /**
     * ctor
     */
    public LegacyMonitorData() {
        this.legacyJobInfos = new HashMap<>();
        this.fullNameToKey = new HashMap<>();
    }

    /**
     * @return {@code true} if there're no jobs to warn.
     */
    public boolean isEmpty() {
        return legacyJobInfos.isEmpty();
    }

    /**
     * Check a job is listed either as a source job or a destination job.
     *
     * @param jobFullName the job name to check
     * @return {@code true} if that job is already listed.
     */
    public boolean hasJobFullName(@NonNull String jobFullName) {
        return fullNameToKey.containsKey(jobFullName);
    }

    /**
     * Apply the rename to the job list.
     *
     * @param previousFullName the previous name of the job.
     * @param newFullName the new name of the job.
     */
    public void onJobRename(@NonNull String previousFullName, @NonNull String newFullName) {
        List<JobKey> keys = fullNameToKey.get(previousFullName);
        if (keys == null) {
            return;
        }

        List<JobKey> newKeys = new ArrayList<>(keys.size());

        // keys are composed of fullNames of both job (from and to)
        for (JobKey key : keys) {
            JobKey newKey;
            String otherFullName;
            if (key.from.equals(previousFullName)) {
                newKey = buildKey(newFullName, key.to);
                otherFullName = key.to;
            } else {
                newKey = buildKey(key.from, newFullName);
                otherFullName = key.from;
            }
            newKeys.add(newKey);

            // update other part of the key
            List<JobKey> otherList = fullNameToKey.get(otherFullName);
            otherList.remove(key);
            otherList.add(newKey);

            LegacyBuildStorage newBuildInfo = null;
            LegacyBuildStorage buildInfo = legacyJobInfos.get(key);
            if (buildInfo != null) {
                if (buildInfo.getJobFullNameFrom().equals(previousFullName)) {
                    LOGGER.log(Level.FINE, "Renaming [from] of {0}, with new name: {1}", new Object[] {
                        buildInfo, newFullName
                    });
                    newBuildInfo = buildInfo.renameJobFrom(newFullName);
                } else if (buildInfo.getJobFullNameTo().equals(previousFullName)) {
                    LOGGER.log(
                            Level.FINE, "Renaming [to] of {0}, with new name: {1}", new Object[] {buildInfo, newFullName
                            });
                    newBuildInfo = buildInfo.renameJobTo(newFullName);
                }
            }
            if (newBuildInfo != null) {
                legacyJobInfos.remove(key);
                legacyJobInfos.put(newKey, newBuildInfo);
            }
        }

        fullNameToKey.remove(previousFullName);
        fullNameToKey.put(newFullName, newKeys);
    }

    /**
     * Used for the testing purpose.
     *
     * @return map map from the pair of jobs to the possible failure build.
     */
    @Restricted(NoExternalUse.class)
    /* Visible for testing */ @NonNull
    Map<JobKey, LegacyBuildStorage> getLegacyJobInfos() {
        return new HashMap<>(legacyJobInfos);
    }

    /**
     * Used for the testing purpose.
     *
     * @return map map from the job name to all existing pair of jobs.
     */
    @Restricted(NoExternalUse.class)
    /* Visible for testing */ @NonNull
    Map<String, List<JobKey>> getFullNameToKey() {
        Map<String, List<JobKey>> result = new HashMap<>();

        fullNameToKey.forEach((key, value) -> result.put(key, new ArrayList<>(value)));

        return result;
    }

    /**
     * Used for the testing purpose.
     *
     * Clear all.
     */
    @Restricted(NoExternalUse.class)
    /* Visible for testing */ void clear() {
        this.fullNameToKey.clear();
        this.legacyJobInfos.clear();
    }

    /**
     * Helper to ease the display of the information in a table
     *
     * @return the list of pairs of source and destination jobs
     *     to show warnings for that user.
     */
    @NonNull
    public List<LegacyBuildInfoModel> buildDataForCurrentUser() {
        Map<String, JobInfoModel> jobCache = new HashMap<>();

        List<LegacyBuildInfoModel> result = new ArrayList<>();

        legacyJobInfos.values().stream()
                .collect(Collectors.groupingBy(LegacyBuildStorage::getJobFullNameFrom))
                .forEach((jobFullNameFrom, buildInfos) -> {
                    JobInfoModel jobFrom = retrieveOrBuildJobInfoForCurrentUser(jobCache, jobFullNameFrom);

                    LegacyBuildInfoModel model = new LegacyBuildInfoModel(jobFrom);
                    buildInfos.forEach(buildInfo -> {
                        JobInfoModel jobTo = retrieveOrBuildJobInfoForCurrentUser(jobCache, buildInfo.jobFullNameTo);
                        LegacyJobInfoItemModel itemModel = new LegacyJobInfoItemModel(
                                jobTo, buildInfo.username, buildInfo.lastBuildDate, buildInfo.numOfBuild);
                        model.addItem(itemModel);
                    });

                    result.add(model);
                });

        result.sort((a, b) -> a.jobFrom.getJobFullName().compareToIgnoreCase(b.jobFrom.getJobFullName()));
        for (LegacyBuildInfoModel jobInfo : result) {
            jobInfo.jobToList.sort(Comparator.comparing(LegacyJobInfoItemModel::getLastBuildDate));
        }

        return result;
    }

    @NonNull
    private JobInfoModel retrieveOrBuildJobInfoForCurrentUser(
            @NonNull Map<String, JobInfoModel> jobCache, @NonNull String jobFullName) {
        if (jobCache.containsKey(jobFullName)) {
            return jobCache.get(jobFullName);
        }

        Jenkins jenkins = Jenkins.get();

        boolean hasAccessTo = true;
        Job<?, ?> job = jenkins.getItem(jobFullName, jenkins, Job.class);
        if (job == null) {
            hasAccessTo = false;
            try (ACLContext acl = ACL.as(ACL.SYSTEM)) {
                job = jenkins.getItem(jobFullName, jenkins, Job.class);
            }
        }

        JobInfoModel jobInfo = new JobInfoModel(job, hasAccessTo, jobFullName);
        jobCache.put(jobFullName, jobInfo);
        return jobInfo;
    }

    /**
     * Add information that would fail in Production mode.
     *
     * @param jobTryingToCopy the name of the destination job.
     * @param jobToBeCopiedFrom the name of the source job.
     * @param lastBuildDate the build timestamp.
     * @param username the user name that the destination job ran as.
     */
    public void addLegacyJob(
            @NonNull Job<?, ?> jobTryingToCopy,
            @NonNull Job<?, ?> jobToBeCopiedFrom,
            @NonNull Date lastBuildDate,
            @NonNull String username) {
        String jobFullNameTo = jobTryingToCopy.getFullName();
        String jobFullNameFrom = jobToBeCopiedFrom.getFullName();
        JobKey key = buildKey(jobFullNameFrom, jobFullNameTo);

        addEntryToKeyMap(jobFullNameFrom, key);
        addEntryToKeyMap(jobFullNameTo, key);

        if (legacyJobInfos.containsKey(key)) {
            LegacyBuildStorage currentInfo = legacyJobInfos.get(key);
            LegacyBuildStorage newInfo = currentInfo.addNewBuild(username, lastBuildDate);
            legacyJobInfos.put(key, newInfo);
        } else {
            LegacyBuildStorage info = new LegacyBuildStorage(jobFullNameFrom, jobFullNameTo, username, lastBuildDate);
            legacyJobInfos.put(key, info);
        }
    }

    /**
     * Remove information that would fail in Production mode.
     *
     * @param jobFullNameFrom the name of the source job.
     * @param jobFullNameTo the name of the destination job.
     *
     * @return {@code true} if removed (the pair was registered).
     */
    public boolean removeLegacyJob(@NonNull String jobFullNameFrom, @NonNull String jobFullNameTo) {
        JobKey key = buildKey(jobFullNameFrom, jobFullNameTo);
        if (legacyJobInfos.remove(key) == null) {
            return false;
        }

        removeEntryFromKeyMap(jobFullNameFrom, key);
        removeEntryFromKeyMap(jobFullNameTo, key);
        return true;
    }

    private void addEntryToKeyMap(@NonNull String fullName, @NonNull JobKey key) {
        List<JobKey> fromList = fullNameToKey.get(fullName);
        if (fromList == null) {
            fromList = new ArrayList<>();
            fromList.add(key);
            fullNameToKey.put(fullName, fromList);
        } else {
            if (!fromList.contains(key)) {
                fromList.add(key);
            }
        }
    }

    private void removeEntryFromKeyMap(@NonNull String fullName, @NonNull JobKey key) {
        List<JobKey> fromList = fullNameToKey.get(fullName);
        if (fromList != null) {
            fromList.remove(key);
            if (fromList.isEmpty()) {
                fullNameToKey.remove(fullName);
            }
        }
    }

    /**
     * Exported for the testing purpose.
     *
     * @param jobFrom the name of the source job.
     * @param jobTo the name of the destination job.
     * @return the pair of the source job and the destination job.
     */
    @Restricted(NoExternalUse.class)
    @NonNull
    /* Visible for testing */ static JobKey buildKey(@NonNull String jobFrom, @NonNull String jobTo) {
        return new JobKey(jobFrom, jobTo);
    }

    /**
     * The pair of the source job and the destination job.
     */
    public static class JobKey {
        public final String from;
        public final String to;

        /**
         * ctor
         *
         * @param from the name of the source job.
         * @param to the name of the destination job.
         */
        public JobKey(@NonNull String from, @NonNull String to) {
            this.from = from;
            this.to = to;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int hashCode() {
            return from.hashCode() * 17 - to.hashCode() * 19;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean equals(@CheckForNull Object obj) {
            if (!(obj instanceof JobKey)) {
                return false;
            }
            JobKey that = (JobKey) obj;
            return this.from.equals(that.from) && this.to.equals(that.to);
        }
    }

    /**
     * Data stored in the monitor
     * Information about the build that would fail in Production mode.
     */
    public static class LegacyBuildStorage {
        private final String jobFullNameFrom;
        private final String jobFullNameTo;
        private final String username;
        private final Date lastBuildDate;
        private final int numOfBuild;

        /**
         * ctor.
         *
         * @param jobFullNameFrom the name of the source job.
         * @param jobFullNameTo the name of the destination job.
         * @param username the user name that the destination job ran as.
         * @param lastBuildDate the build timestamp.
         */
        public LegacyBuildStorage(
                @NonNull String jobFullNameFrom,
                @NonNull String jobFullNameTo,
                @NonNull String username,
                @NonNull Date lastBuildDate) {
            this(jobFullNameFrom, jobFullNameTo, username, lastBuildDate, 1);
        }

        private LegacyBuildStorage(
                @NonNull String jobFullNameFrom,
                @NonNull String jobFullNameTo,
                @NonNull String username,
                @NonNull Date lastBuildDate,
                int numOfBuild) {
            this.jobFullNameFrom = jobFullNameFrom;
            this.jobFullNameTo = jobFullNameTo;
            this.username = username;
            this.lastBuildDate = lastBuildDate;
            this.numOfBuild = numOfBuild;
        }

        /**
         * @return the name of the source job.
         */
        public @NonNull String getJobFullNameFrom() {
            return jobFullNameFrom;
        }

        /**
         * @return the name of the destination job.
         */
        public @NonNull String getJobFullNameTo() {
            return jobFullNameTo;
        }

        /**
         * @return the user name that the destination job ran as.
         */
        public @NonNull String getUsername() {
            return username;
        }

        /**
         * @return the build timestamp.
         */
        public @NonNull Date getLastBuildDate() {
            return new Date(lastBuildDate.getTime());
        }

        /**
         * @return the number of builds that would fail in Production mode.
         */
        public int getNumOfBuild() {
            return numOfBuild;
        }

        /**
         * Create a new build information.
         *
         * @param username the user name that the destination job ran as.
         * @param lastBuildDate the build timestamp.
         * @return data to store in the monitor.
         */
        @NonNull
        public LegacyBuildStorage addNewBuild(String username, Date lastBuildDate) {
            return new LegacyBuildStorage(
                    this.jobFullNameFrom, this.jobFullNameTo, username, lastBuildDate, numOfBuild + 1);
        }

        /**
         * Create a new build information renaming the source job.
         *
         * @param newJobFullName the new name of the source job.
         * @return data to store in the monitor.
         */
        public LegacyBuildStorage renameJobFrom(String newJobFullName) {
            return new LegacyBuildStorage(newJobFullName, this.jobFullNameTo, username, lastBuildDate, numOfBuild);
        }

        /**
         * Create a new build information renaming the destination job.
         *
         * @param newJobFullName the new name of the destination job.
         * @return data to store in the monitor.
         */
        public LegacyBuildStorage renameJobTo(String newJobFullName) {
            return new LegacyBuildStorage(this.jobFullNameFrom, newJobFullName, username, lastBuildDate, numOfBuild);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String toString() {
            return jobFullNameFrom + " => " + jobFullNameTo;
        }
    }

    /**
     * For Jelly display only
     *
     * Holds information of a source job and destination jobs for that source job.
     */
    public static class LegacyBuildInfoModel {
        private final JobInfoModel jobFrom;
        private final List<LegacyJobInfoItemModel> jobToList;

        /**
         * @param jobFrom the source job.
         */
        public LegacyBuildInfoModel(@NonNull JobInfoModel jobFrom) {
            this.jobFrom = jobFrom;
            this.jobToList = new ArrayList<>();
        }

        /**
         * @return the source job.
         */
        public @NonNull JobInfoModel getJobFrom() {
            return jobFrom;
        }

        /**
         * @return the destination jobs that would fail on Production mode.
         */
        public List<LegacyJobInfoItemModel> getJobToList() {
            return jobToList;
        }

        /**
         * @param item a destination job that would fail on Production mode.
         */
        public void addItem(LegacyJobInfoItemModel item) {
            this.jobToList.add(item);
        }
    }

    /**
     * For Jelly display only
     *
     * Holds information of the job and builds that would fail in Production mode.
     */
    @Restricted(NoExternalUse.class)
    public static class LegacyJobInfoItemModel {
        private final JobInfoModel jobTo;
        private final String username;
        private final Date lastBuildDate;
        private final int numOfBuild;

        /**
         * ctor.
         *
         * @param jobTo the information of the destination job.
         * @param username the user name the destination job ran as.
         * @param lastBuildDate the build timestamp.
         * @param numOfBuild the number of builds that would fail in Production mode.
         */
        public LegacyJobInfoItemModel(
                @NonNull JobInfoModel jobTo, @NonNull String username, @NonNull Date lastBuildDate, int numOfBuild) {
            this.jobTo = jobTo;
            this.username = username;
            this.lastBuildDate = new Date(lastBuildDate.getTime());
            this.numOfBuild = numOfBuild;
        }

        /**
         * @return the information of the destination job.
         */
        public @NonNull JobInfoModel getJobTo() {
            return jobTo;
        }

        /**
         * @return the user name the destination job ran as.
         */
        public @NonNull String getUsername() {
            return username;
        }

        /**
         * @return the build timestamp.
         */
        public @NonNull Date getLastBuildDate() {
            return new Date(lastBuildDate.getTime());
        }

        /**
         * @return the number of builds that would fail in Production mode.
         */
        public int getNumOfBuild() {
            return numOfBuild;
        }
    }

    /**
     * For Jelly display only
     *
     * Holds the information of the job to display.
     */
    public static class JobInfoModel {
        /**
         * Null in case the job does not exist
         */
        private final Job<?, ?> validJob;

        private final boolean regularAccess;
        private final String jobFullName;
        private final boolean autoMigratable;

        /**
         * ctor.
         *
         * @param validJob the job. {@code null} if the job doesn't exist.
         * @param regularAccess {@code true} if the current user can access.
         * @param jobFullName the full name of the job.
         */
        public JobInfoModel(@CheckForNull Job<?, ?> validJob, boolean regularAccess, @NonNull String jobFullName) {
            this.validJob = validJob;
            this.regularAccess = regularAccess;
            this.jobFullName = jobFullName;
            this.autoMigratable = LegacyJobConfigMigrationMonitor.canMigrate(validJob);
        }

        /**
         * @return the job. {@code null} if the job doesn't exist.
         */
        public @CheckForNull Job<?, ?> getValidJob() {
            return validJob;
        }

        /**
         * Check wheter the current user have read permission to that job.
         *
         * This will be {@code false} also if the job no longer exists.
         *
         * @return {@code true} if the current user can access.
         */
        public boolean isRegularAccess() {
            return regularAccess;
        }

        /**
         * @return the full name of the job.
         */
        public @NonNull String getJobFullName() {
            return jobFullName;
        }

        /**
         * @return {@code true} if auto-migration is applicable.
         */
        public boolean isAutoMigratable() {
            return autoMigratable;
        }
    }
}
