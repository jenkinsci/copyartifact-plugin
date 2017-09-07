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

import hudson.FilePath;
import hudson.os.PosixException;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

import org.apache.commons.lang.StringUtils;

import com.google.common.base.Function;
import com.google.common.collect.Lists;

/**
 * Copy files from {@link FilePath}.
 * 
 * @since 2.0
 */
public abstract class AbstractFilePathCopyOperation extends AbstractCopyOperation {
    /**
     * {@link FileInfo} for {@link FilePath}.
     */
    protected class FileInfoImpl extends FileInfo {
        private final FilePath src;
        private final String relativePath;

        /**
         * ctor
         * 
         * @param src source directory
         * @param baseDir base directory to calculate directory structures. should be parent of <code>src</code>.
         */
        public FileInfoImpl(@Nonnull FilePath src, @Nonnull FilePath baseDir) {
            this.src = src;
            String relativePath = src.getRemote().substring(baseDir.getRemote().length());
            if (relativePath.startsWith("\\") || relativePath.startsWith("/")) {
                relativePath = relativePath.substring(1);
            }
            this.relativePath = relativePath;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        @Nonnull
        public FilePath getRelativeFrom(@Nonnull FilePath path) {
            return new FilePath(path, relativePath);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        @Nonnull
        public String getFilename() {
            return src.getName();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        @Nonnull
        public InputStream open() throws IOException, InterruptedException {
            return src.read();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        @Nonnull
        public void copyMetaInfoTo(@Nonnull FilePath dest, @Nonnull CopyArtifactCopyContext context) throws IOException, InterruptedException {
            super.copyMetaInfoTo(dest, context);
            
            try {
                dest.chmod(src.mode());
            } catch (PosixException x) {
                context.logException("could not check mode of " + src, x);
            }
            
            try {
                dest.touch(src.lastModified());
            } catch (IOException x) {
                context.logException("Failed to set last modification time", x);
            }
        }
    }
    
    
    
    /**
     * Addition to original behavior, handles symbolic links.
     * 
     * @param file      the file to copy
     * @param path      the destination path
     * @param context   context of the operation.
     * @throws IOException if an error occurs while performing the operation.
     * @throws InterruptedException if any thread interrupts the current thread.
     * @see hudson.plugins.copyartifact.operation.AbstractCopyOperation#copyOne(hudson.plugins.copyartifact.operation.AbstractCopyOperation.FileInfo, hudson.FilePath, hudson.plugins.copyartifact.operation.CopyArtifactCopyContext)
     */
    @Override
    protected void copyOne(@Nonnull FileInfo file, @Nonnull FilePath path, @Nonnull CopyArtifactCopyContext context) throws IOException, InterruptedException {
        if (file instanceof FileInfoImpl) {    // this should be.
            FileInfoImpl s = (FileInfoImpl)file;
            String link = s.src.readLink();
            if (link != null) {
                path.getParent().mkdirs();
                path.symlinkTo(link, context.getListener());
                return;
            }
        }
        super.copyOne(file, path, context);
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    @Nonnull
    protected Iterable<? extends FileInfoImpl> scanFilesToCopy(@Nonnull CopyArtifactCopyContext context) throws IOException, InterruptedException {
        FilePath srcBaseDir = getSrcDir(context);
        if (srcBaseDir == null || !srcBaseDir.exists()) {
            return Collections.<FileInfoImpl>emptyList();
        }
        
        final FilePath srcDir = !StringUtils.isBlank(context.getSrcBaseDir())
                ? new FilePath(srcBaseDir, context.getSrcBaseDir())
                : srcBaseDir;
        if (!srcDir.exists()) {
            return Collections.<FileInfoImpl>emptyList();
        }
        
        FilePath[] list = srcDir.list(
                context.getIncludes(),
                context.getExcludes(),
                false
        );
        return Lists.transform(
                Arrays.asList(list),
                new Function<FilePath, FileInfoImpl>() {
                    @Override
                    public FileInfoImpl apply(FilePath file) {
                        return new FileInfoImpl(file, srcDir);
                    }
                }
        );
    }
    
    /**
     * Returns the source directory to copy files from.
     * You do not need to handle {@link CopyArtifactCopyContext#getSrcBaseDir()}
     * 
     * @param context
     *      parameters to copying operations.
     *      You can save execution state with {@link CopyArtifactCopyContext#addExtension(Object)}
     * @return source directory to copy files from.
     * @throws IOException if an error occurs while performing the operation.
     * @throws InterruptedException if any thread interrupts the current thread.
     */
    @CheckForNull
    protected abstract FilePath getSrcDir(@Nonnull CopyArtifactCopyContext context) throws IOException, InterruptedException ;
}
