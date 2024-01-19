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
package hudson.plugins.copyartifact;

import hudson.Extension;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import jenkins.model.GlobalConfiguration;
import jenkins.model.GlobalConfigurationCategory;
import jenkins.model.Jenkins;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import java.io.IOException;
import java.util.logging.Logger;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Provide the configuration about the compatibility mode (either production or migration)
 *
 * @since 1.44
 */
@Extension
@Symbol("copyartifact")
public class CopyArtifactConfiguration extends GlobalConfiguration {
    private static final Logger LOGGER = Logger.getLogger(CopyArtifactConfiguration.class.getName());

    /**
     * @see CopyArtifactCompatibilityMode
     */
    @NonNull
    private CopyArtifactCompatibilityMode mode = CopyArtifactCompatibilityMode.PRODUCTION;
    
    /**
     * ctor.
     */
    public CopyArtifactConfiguration() {
        //TODO could be replaced by PersistentDescriptor once core is 2.140+
        load();
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized void load() {
        boolean firstLoadOfConfiguration = isFirstLoad();
        
        super.load();
        
        // Set to Migration mode when both of the followings are met:
        // * The configuration hasn't be saved.
        //       That is, when this is the first boot
        //       after copyartifact 1.44 or later is installed.
        // * The backup of the copyartifact exists.
        //       That is, the copyartifact 1.43.1 or earlier was installed.
        if (firstLoadOfConfiguration) {
            boolean alreadyInstalledBefore = false;
            try {
                alreadyInstalledBefore = Jenkins.get().getRootPath().child("plugins").child("copyartifact.bak").exists();
            } catch (Exception e) {
                // no care, that's just an heuristic
            }
            if (!alreadyInstalledBefore) {
                LOGGER.info("CopyArtifact is set to Production mode.");
                // Set it explicitly to save the configuration
                // though it should be already Production mode.
                setModeWithoutSave(CopyArtifactCompatibilityMode.PRODUCTION);
            } else {
                LOGGER.info(
                    "CopyArtifact is set to Migration mode"
                    + " as the older version of copyartifact is detected."
                );
                setModeWithoutSave(CopyArtifactCompatibilityMode.MIGRATION);
            }
        }
    }
    
    @Initializer(after = InitMilestone.EXTENSIONS_AUGMENTED, before = InitMilestone.JOB_LOADED)
    public static void syncToDiskForFirstLoad() {
        // save the default configuration to the disk in the first load.
        // This should be performed AFTER all extensoins are resolved.
        CopyArtifactConfiguration config = CopyArtifactConfiguration.get();
        if (config == null) {
            LOGGER.severe(
                "Could not get CopyArtifactConfiguration." +
                " It looks failed to initialized for some reason"
            );
            return;
        }
        if (config.isFirstLoad()) {
            config.save();
        }
    }

    /**
     * @return true if the configuration is not stored to the disk
     */
    protected boolean isFirstLoad() {
        return !getConfigFile().exists();
    }

    /**
     * Set the the first load status. Use only for testing purpose.
     */
    @Restricted(NoExternalUse.class)
    public void setToFirstLoad() throws IOException {
        getConfigFile().delete();
    }
    
    /**
     * @return the compatibility mode
     */
    @NonNull
    public CopyArtifactCompatibilityMode getMode() {
        return mode;
    }
    
    /**
     * Set the compatibility mode without storing to the disk. Use only for testing purpose.
     */
    @Restricted(NoExternalUse.class)
    protected void setModeWithoutSave(@NonNull CopyArtifactCompatibilityMode mode) {
        this.mode = mode;
    }

    /**
     * @param mode the compatibility mode.
     */
    public void setMode(@NonNull CopyArtifactCompatibilityMode mode) {
        setModeWithoutSave(mode);
        save();
    }
    
    /**
     * @return {@code true} if set to Migration mode.
     */
    public static boolean isMigrationMode() {
        CopyArtifactConfiguration config = get();
        if (config == null) {
            // failsafe
            return false;
        }
        return CopyArtifactCompatibilityMode.MIGRATION.equals(config.getMode());
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public @NonNull GlobalConfigurationCategory getCategory() {
        return GlobalConfigurationCategory.get(GlobalConfigurationCategory.Security.class);
    }
    
    /**
     * @return the singleton instance.
     */
    @CheckForNull
    public static CopyArtifactConfiguration get() {
        return GlobalConfiguration.all().get(CopyArtifactConfiguration.class);
    }
}
