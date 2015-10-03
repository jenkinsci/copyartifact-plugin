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

import com.google.common.base.Function;
import com.google.common.collect.Lists;

/**
 *
 */
public abstract class AbstractFilePathCopyOperation extends AbstractCopyOperation {
    protected class FileInfoImpl extends FileInfo {
        private final FilePath src;
        private final String relativePath;

        public FileInfoImpl(FilePath src, FilePath baseDir) {
            this.src = src;
            String relativePath = src.getRemote().substring(baseDir.getRemote().length());
            if (relativePath.startsWith("\\") || relativePath.startsWith("/")) {
                relativePath = relativePath.substring(1);
            }
            this.relativePath = relativePath;
        }

        @Override
        public FilePath getRelativeFrom(FilePath path) {
            return new FilePath(path, relativePath);
        }

        @Override
        public String getFilename() {
            return src.getName();
        }

        @Override
        public InputStream open() throws IOException, InterruptedException {
            return src.read();
        }

        @Override
        public void copyMetaInfoTo(FilePath dest, CopyArtifactCopyContext context) throws IOException, InterruptedException {
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
    
    
    
    @Override
    protected void copyOne(FileInfo file, FilePath path, CopyArtifactCopyContext context) throws IOException, InterruptedException {
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
     * @param context
     * @return
     * @throws IOException
     * @throws InterruptedException
     * @see hudson.plugins.copyartifact.operation.AbstractCopyOperation#scanFilesToCopy(hudson.plugins.copyartifact.operation.CopyArtifactCopyContext)
     */
    @Override
    protected Iterable<? extends FileInfoImpl> scanFilesToCopy(CopyArtifactCopyContext context) throws IOException, InterruptedException {
        final FilePath srcDir = getSrcDir(context);
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
    
    protected abstract FilePath getSrcDir(CopyArtifactCopyContext context) throws IOException, InterruptedException ;
}
