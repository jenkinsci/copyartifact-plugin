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

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import jenkins.model.Jenkins;
import hudson.Extension;
import hudson.Util;
import hudson.model.AbstractProject;
import hudson.model.Project;
import hudson.model.listeners.ItemListener;

/**
 * Upgrade configuration of CopyArtifact after all jobs are loaded.
 */
@Extension
public class CopyArtifactUpgradeListener extends ItemListener {
    private static boolean upgradeNeeded = false;
    private static Logger LOGGER = Logger.getLogger(CopyArtifactUpgradeListener.class.getName());
    
    /**
     * Set {@link CopyArtifactUpgradeListener} work.
     */
    synchronized static void setUpgradeNeeded() {
        if (!upgradeNeeded) {
            LOGGER.info("Upgrade for Copy Artifact is scheduled.");
            upgradeNeeded = true;
        }
    }
    
    /**
     * Scan all jobs and upgrade the configuration of Copy Artfifact if needed.
     * 
     * Works when {@link CopyArtifactUpgradeListener#setUpgradeNeeded()} was called.
     */
    @Override
    public void onLoaded() {
        if (!upgradeNeeded) {
            return;
        }
        upgradeNeeded = false;
        
        boolean isUpgraded = false;
        for (Project<?,?> project: Jenkins.getInstance().getAllItems(Project.class)) {
            for (CopyArtifact target: Util.filter(project.getBuilders(), CopyArtifact.class)) {
                try {
                    if (target.upgradeIfNecessary(project)) {
                        isUpgraded = true;
                    }
                } catch(IOException e) {
                    LOGGER.log(Level.SEVERE, String.format("Failed to upgrade CopyArtifact in %s", project.getFullName()), e);
                }
            }
        }
        
        if (!isUpgraded) {
            // No CopyArtifact is upgraded.
            LOGGER.warning("Update of CopyArtifact is scheduled, but no CopyArtifact to upgrade was found!");
        }
    }
}
