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

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

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
     * This also contains information about the path.
     */
    protected class FileInfoImpl extends FileInfo {
        private final VirtualFile file;
        /**
         * path fragments for the file.
         * You can get the path by joining them with the path separator.
         * Fragments themselves may contain path separators.
         */
        private final List<String> pathFragments;
        
        /**
         * @param file file to wrap.
         */
        protected FileInfoImpl(@Nonnull VirtualFile file) {
            this.file = file;
            this.pathFragments = Arrays.asList("");
        }
        
        /**
         * @param parent parent directory
         * @param file file to wrap
         * @param path the relative path of the file from the parent
         */
        protected FileInfoImpl(@Nonnull FileInfoImpl parent, @Nonnull VirtualFile file, @Nonnull String path) {
            this.file = file;
            this.pathFragments = new ArrayList<String>(parent.pathFragments);
            this.pathFragments.add(path);
        }
        
        /**
         * @param path the relative path to the file to get
         * @return new {@link FileInfoImpl} object of the child file.
         */
        public FileInfoImpl child(@Nonnull String path) {
            return new FileInfoImpl(this, file.child(path), path);
        }
        
        /**
         * {@inheritDoc}
         */
        @Override
        @Nonnull
        public FilePath getRelativeFrom(@Nonnull FilePath path) {
            for (String fragment : pathFragments) {
                path = new FilePath(path, fragment);
            }
            return path;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        @Nonnull
        public String getFilename() {
            return file.getName();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        @Nonnull
        public InputStream open() throws IOException {
            return file.open();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void copyMetaInfoTo(@Nonnull FilePath dest, @Nonnull CopyArtifactCopyContext context) throws IOException, InterruptedException {
            super.copyMetaInfoTo(dest, context);
            try {
                dest.touch(file.lastModified());
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
     * {@inheritDoc}
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
     * {@inheritDoc}
     */
    @Override
    public boolean init(@Nonnull CopyArtifactCopyContext context) throws IOException, InterruptedException {
        ArtifactManager manager = context.getSrc().getArtifactManager();
        VirtualFile srcDir = manager.root();
        if (srcDir == null || !srcDir.exists()) {
            context.logInfo(Messages.AbstractCopyOperation_MissingSrcArtifacts(srcDir));
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
     * {@inheritDoc}
     */
    @Override
    @Nonnull
    public Iterable<? extends FileInfoImpl> scanFilesToCopy(@Nonnull CopyArtifactCopyContext context) throws IOException, InterruptedException {
        ArtifactManager manager = context.getSrc().getArtifactManager();
        VirtualFile srcDir = manager.root();
        
        if (StringUtils.isBlank(context.getSrcBaseDir())) {
            srcDir = srcDir.child(context.getSrcBaseDir());
            if (srcDir == null || !srcDir.exists()) {
                return Collections.emptyList();
            }
        }
        
        final FileInfoImpl root = new FileInfoImpl(srcDir);
        
        return Lists.transform(
                Arrays.asList(srcDir.list(context.getIncludes())),
                new Function<String, FileInfoImpl>() {
                    @Override
                    public FileInfoImpl apply(String file) {
                        return root.child(file);
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
         * {@inheritDoc}
         */
        @Override
        public String getDisplayName() {
            return Messages.CopyArtifactFiles_DisplayName();
        }
    }
}
