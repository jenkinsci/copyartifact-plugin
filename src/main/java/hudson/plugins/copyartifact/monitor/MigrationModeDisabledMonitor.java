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

import hudson.Extension;
import hudson.model.AdministrativeMonitor;
import hudson.plugins.copyartifact.CopyArtifactCompatibilityMode;
import hudson.plugins.copyartifact.CopyArtifactConfiguration;
import hudson.util.HttpResponses;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.interceptor.RequirePOST;

import java.io.IOException;

/**
 * Responsible to ensure the {@link hudson.plugins.copyartifact.CopyArtifactCompatibilityMode#PRODUCTION} is used.
 * @since 1.44
 */
@Extension
@Symbol("copyArtifactMigrationMode")
public class MigrationModeDisabledMonitor extends AdministrativeMonitor {
    
    /**
     * ctor
     */
    public MigrationModeDisabledMonitor(){
        super("copyArtifactMigrationMode");
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public String getDisplayName() {
        return Messages.MigrationModeDisabledMonitor_displayName();
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isActivated() {
        return CopyArtifactConfiguration.isMigrationMode();
    }
    
    @RequirePOST
    public HttpResponse doAct() throws IOException {
        return HttpResponses.redirectViaContextPath("configureSecurity");
    }
}
