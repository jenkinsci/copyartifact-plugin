/*
 * The MIT License
 * 
 * Copyright (c) 2015 IKEDA Yasuyuki
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

package hudson.plugins.copyartifact.operation;

import hudson.Extension;
import hudson.FilePath;
import hudson.model.AbstractBuild;
import hudson.model.Run;
import hudson.plugins.copyartifact.CopyArtifactOperationDescriptor;

import java.io.IOException;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

import org.kohsuke.stapler.DataBoundConstructor;

/**
 * copy files in workspaces.
 * 
 * @since 2.0
 */
public class CopyWorkspaceFiles extends AbstractFilePathCopyOperation {
    /**
     * ctor
     */
    @DataBoundConstructor
    public CopyWorkspaceFiles() {
    }
    
    /**
     * @param context
     * @return
     * @throws IOException
     * @throws InterruptedException
     * @see hudson.plugins.copyartifact.operation.AbstractFilePathCopyOperation#getSrcDir(hudson.plugins.copyartifact.operation.CopyArtifactCopyContext)
     */
    @Override
    @CheckForNull
    protected FilePath getSrcDir(@Nonnull CopyArtifactCopyContext context) throws IOException, InterruptedException {
        Run<?, ?> src = context.getSrc();
        if (!(src instanceof AbstractBuild<?, ?>)) {
            context.logInfo("Workspaces are available only for AbstractBuild.");
            return null;
        }
        return ((AbstractBuild<?, ?>)src).getWorkspace();
    }
    
    /**
     * Descriptor for {@link CopyWorkspaceFiles}
     */
    @Extension
    public static class DescriptorImpl extends CopyArtifactOperationDescriptor {
        /**
         * @return
         * @see hudson.model.Descriptor#getDisplayName()
         */
        @Override
        public String getDisplayName() {
            return Messages.CopyWorkspaceFiles_DisplayName();
        }
    }
}
