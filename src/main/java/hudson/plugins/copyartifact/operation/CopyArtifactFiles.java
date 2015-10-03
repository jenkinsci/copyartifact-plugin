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
import hudson.plugins.copyartifact.VirtualFileScanner;
import hudson.plugins.copyartifact.VirtualFileScanner.VirtualFileWithPathInfo;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import com.google.common.base.Function;
import com.google.common.collect.Lists;

import jenkins.model.ArtifactManager;
import jenkins.util.VirtualFile;

/**
 *
 */
public class CopyArtifactFiles extends AbstractCopyOperation {
    protected class FileInfoImpl extends FileInfo {
        private final VirtualFileWithPathInfo file;
        
        protected FileInfoImpl(VirtualFileWithPathInfo file) {
            this.file = file;
        }

        @Override
        public FilePath getRelativeFrom(FilePath path) {
            for (String fragment : file.pathFragments) {
                path = new FilePath(path, fragment);
            }
            return path;
        }

        @Override
        public String getFilename() {
            return file.file.getName();
        }

        @Override
        public InputStream open() throws IOException {
            return file.file.open();
        }

        @Override
        public void copyMetaInfoTo(FilePath dest, CopyArtifactCopyContext context) throws IOException, InterruptedException {
            super.copyMetaInfoTo(dest, context);
            try {
                dest.touch(file.file.lastModified());
            } catch (IOException x) {
                context.logException("Failed to set last modification time", x);
            }
        }
    }
    
    @Override
    public boolean init(CopyArtifactCopyContext context) throws IOException, InterruptedException {
        ArtifactManager manager = context.getSrc().getArtifactManager();
        VirtualFile srcDir = manager.root();
        if (srcDir == null || !srcDir.exists()) {
            context.logDebug("No artifacts to copy");
            return false;
        }
        return super.init(context);
    }
    
    @Override
    public Iterable<? extends FileInfoImpl> scanFilesToCopy(CopyArtifactCopyContext context) throws IOException, InterruptedException {
        ArtifactManager manager = context.getSrc().getArtifactManager();
        VirtualFile srcDir = manager.root();
        
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
}
