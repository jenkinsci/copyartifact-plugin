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
import hudson.model.Run;
import hudson.plugins.copyartifact.CopyArtifactOperationContext;
import hudson.plugins.copyartifact.CopyArtifactOperationDescriptor;
import hudson.plugins.copyartifact.VirtualFileScanner;
import hudson.plugins.copyartifact.VirtualFileScanner.VirtualFileWithPathInfo;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;

import javax.annotation.Nonnull;

import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;

import com.google.common.base.Function;
import com.google.common.collect.Lists;

import jenkins.model.ArtifactManager;
import jenkins.model.StandardArtifactManager;
import jenkins.util.VirtualFile;

/**
 * Copy files from {@link ArtifactManager}.
 * 
 * @since 2.0
 */
public class CopyArtifactFiles extends AbstractCopyOperation {
    /**
     * {@link FileInfo} wrapping {@link VirtualFile}.
     */
    protected class FileInfoImpl extends FileInfo {
        private final VirtualFileWithPathInfo file;
        
        /**
         * @param file
         */
        protected FileInfoImpl(@Nonnull VirtualFileWithPathInfo file) {
            this.file = file;
        }

        /**
         * @param path
         * @return
         * @see hudson.plugins.copyartifact.operation.AbstractCopyOperation.FileInfo#getRelativeFrom(hudson.FilePath)
         */
        @Override
        @Nonnull
        public FilePath getRelativeFrom(@Nonnull FilePath path) {
            for (String fragment : file.pathFragments) {
                path = new FilePath(path, fragment);
            }
            return path;
        }

        /**
         * @return
         * @see hudson.plugins.copyartifact.operation.AbstractCopyOperation.FileInfo#getFilename()
         */
        @Override
        @Nonnull
        public String getFilename() {
            return file.file.getName();
        }

        /**
         * @return
         * @throws IOException
         * @see hudson.plugins.copyartifact.operation.AbstractCopyOperation.FileInfo#open()
         */
        @Override
        @Nonnull
        public InputStream open() throws IOException {
            return file.file.open();
        }

        /**
         * @param dest
         * @param context
         * @throws IOException
         * @throws InterruptedException
         * @see hudson.plugins.copyartifact.operation.AbstractCopyOperation.FileInfo#copyMetaInfoTo(hudson.FilePath, hudson.plugins.copyartifact.operation.CopyArtifactCopyContext)
         */
        @Override
        public void copyMetaInfoTo(@Nonnull FilePath dest, @Nonnull CopyArtifactCopyContext context) throws IOException, InterruptedException {
            super.copyMetaInfoTo(dest, context);
            try {
                dest.touch(file.file.lastModified());
            } catch (IOException x) {
                context.logException("Failed to set last modification time", x);
            }
        }
    }
    
    /**
     * ctor
     */
    @DataBoundConstructor
    public CopyArtifactFiles() {
    }
    
    /**
     * @param src
     * @param context
     * @return
     * @throws IOException
     * @throws InterruptedException
     * @see hudson.plugins.copyartifact.operation.AbstractCopyOperation#perform(hudson.model.Run, hudson.plugins.copyartifact.CopyArtifactOperationContext)
     */
    @Override
    public Result perform(Run<?, ?> src, CopyArtifactOperationContext context) throws IOException, InterruptedException {
        ArtifactManager manager = src.getArtifactManager();
        
        if (manager instanceof StandardArtifactManager) {
            CopyLegacyArtifactFiles legacy = new CopyLegacyArtifactFiles();
            legacy.copyConfiguration(this);
            return legacy.perform(src, context);
        }
        
        return super.perform(src, context);
    }
    
    /**
     * @param context
     * @return
     * @throws IOException
     * @throws InterruptedException
     * @see hudson.plugins.copyartifact.operation.AbstractCopyOperation#init(hudson.plugins.copyartifact.operation.CopyArtifactCopyContext)
     */
    @Override
    public boolean init(@Nonnull CopyArtifactCopyContext context) throws IOException, InterruptedException {
        ArtifactManager manager = context.getSrc().getArtifactManager();
        VirtualFile srcDir = manager.root();
        if (srcDir == null || !srcDir.exists()) {
            context.logDebug("No artifacts to copy");
            return false;
        }
        if (!StringUtils.isBlank(context.getExcludes())) {
            context.logInfo(
                    "WARNING: Doesn't support exclude filters with non-standard artifact managers."
                    + "Exclude filters are ignored: {0}",
                    context.getExcludes()
            );
        }
        return super.init(context);
    }
    
    /**
     * @param context
     * @return
     * @throws IOException
     * @throws InterruptedException
     * @see hudson.plugins.copyartifact.operation.AbstractCopyOperation#scanFilesToCopy(hudson.plugins.copyartifact.operation.CopyArtifactCopyContext)
     */
    @Override
    @Nonnull
    public Iterable<? extends FileInfoImpl> scanFilesToCopy(@Nonnull CopyArtifactCopyContext context) throws IOException, InterruptedException {
        ArtifactManager manager = context.getSrc().getArtifactManager();
        VirtualFile srcDir = manager.root();
        
        if (srcDir == null || !srcDir.exists()) {
            context.logInfo(Messages.AbstractCopyOperation_MissingSrcArtifacts(srcDir));
            return Collections.emptyList();
        }
        
        if (StringUtils.isBlank(context.getSrcBaseDir())) {
            srcDir = srcDir.child(context.getSrcBaseDir());
            if (srcDir == null || !srcDir.exists()) {
                return Collections.emptyList();
            }
        }
        
        VirtualFileScanner scanner = new VirtualFileScanner(
                context.getIncludes(),
                context.getExcludes(),
                false       // useDefaultExcludes
        );
        return Lists.transform(
                scanner.scanFile(srcDir),
                new Function<VirtualFileWithPathInfo, FileInfoImpl>() {
                    @Override
                    public FileInfoImpl apply(VirtualFileWithPathInfo file) {
                        return new FileInfoImpl(file);
                    }
                }
        );
    }
    
    /**
     * Descriptor for {@link CopyArtifactFiles}
     */
    @Extension(ordinal=100)    // topmost
    public static class DescriptorImpl extends CopyArtifactOperationDescriptor {
        /**
         * @return
         * @see hudson.model.Descriptor#getDisplayName()
         */
        @Override
        public String getDisplayName() {
            return Messages.CopyArtifactFiles_DisplayName();
        }
    }
}
