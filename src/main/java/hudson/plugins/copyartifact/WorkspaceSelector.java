/*
 * The MIT License
 *
 * Copyright (c) 2004-2011, Sun Microsystems, Inc., Alan Harder
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

import hudson.model.Descriptor;
import hudson.plugins.copyartifact.operation.CopyWorkspaceFiles;
import hudson.plugins.copyartifact.selector.Version1BuildSelector;

/**
 * Copy artifacts from the *workspace* of the latest completed build.
 * @author Alan Harder
 * 
 * @deprecated use {@link CopyWorkspaceFiles} instead.
 */
@Deprecated
public class WorkspaceSelector extends Version1BuildSelector {
    public static final Descriptor<BuildSelector> DESCRIPTOR =
            new SimpleBuildSelectorDescriptor(
                WorkspaceSelector.class, Messages._WorkspaceSelector_DisplayName());
    
    @Override
    public MigratedConfiguration migrateToVersion2() {
        MigratedConfiguration conf = new MigratedConfiguration(
                new StatusBuildSelector(StatusBuildSelector.BuildStatus.Completed)
        );
        conf.copyArtifactOperation = new CopyWorkspaceFiles();
        return conf;
    }
}
