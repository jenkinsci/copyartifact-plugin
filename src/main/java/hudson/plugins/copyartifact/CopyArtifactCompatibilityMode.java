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

import hudson.util.EnumConverter;
import org.jvnet.localizer.Localizable;
import org.kohsuke.stapler.Stapler;

public enum CopyArtifactCompatibilityMode {
    /**
     * The production-ready mode for the permission check on the projects we want to copy the artifacts from.
     *
     * Once all the jobs are migrated using the {@link #MIGRATION} mode, this mode could be activated to ensure better security.
     */
    PRODUCTION(Messages._CopyArtifactCompatibilityMode_ProductionMode()),
    
    /**
     * Legacy behavior where the project we want to copy the artifact from is checked either at configure time or at runtime
     * depending on who is configuring the project and if it's static or dynamic (using parameter)
     *
     * When a copy is done but the permission should not have been correct in {@link #PRODUCTION}, an item is added
     * to the monitor list to help the administrator to improve the current situation and allow them to use the better mode
     * in short/mid term.
     *
     * This mode is NOT meant to be used in long term as it contains security defect.
     *
     * @see hudson.plugins.copyartifact.monitor.MigrationModeDisabledMonitor
     */
    MIGRATION(Messages._CopyArtifactCompatibilityMode_MigrationMode());
    
    private final Localizable description;
    
    public String getDescription() {
        return description.toString();
    }
    
    public String getName() {
        return name();
    }
    
    CopyArtifactCompatibilityMode(Localizable description) {
        this.description = description;
    }
    
    static {
        // to allow the conversion from the string to the Enum in the databinding process
        Stapler.CONVERT_UTILS.register(new EnumConverter(), CopyArtifactCompatibilityMode.class);
    }
}
