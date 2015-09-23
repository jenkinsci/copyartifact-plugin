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

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.AbstractBuild;
import hudson.model.Descriptor;
import hudson.model.Run;
import java.io.IOException;
import java.io.PrintStream;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Copy artifacts from the *workspace* of the latest completed build.
 * @author Alan Harder
 */
public class WorkspaceSelector extends BuildSelector {

    @DataBoundConstructor
    public WorkspaceSelector() {
    }

    @Override
    public boolean isSelectable(Run<?,?> run, EnvVars env) {
        return true;
    }

    /*
    // TODO: No longer supports to copy from non-artifacts.
    @Override protected FilePath getSourceDirectory(Run<?,?> src, PrintStream console) throws IOException, InterruptedException {
        if (src instanceof AbstractBuild) {
            FilePath srcDir = ((AbstractBuild) src).getWorkspace();
            if (srcDir != null && srcDir.exists()) {
                return srcDir;
            } else {
                console.println(Messages.CopyArtifact_MissingSrcWorkspace()); // (see JENKINS-3330)
                return null;
            }
        } else {
            return super.getSourceDirectory(src, console);
        }
    }
    */

    @Extension(ordinal=-20)
    public static final Descriptor<BuildSelector> DESCRIPTOR =
            new SimpleBuildSelectorDescriptor(
                WorkspaceSelector.class, Messages._WorkspaceSelector_DisplayName());
}
