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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

import jenkins.util.VirtualFile;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundSetter;

import hudson.FilePath;
import hudson.Util;
import hudson.console.HyperlinkNote;
import hudson.matrix.MatrixBuild;
import hudson.maven.MavenModuleSetBuild;
import hudson.model.Fingerprint;
import hudson.model.FingerprintMap;
import hudson.model.Run;
import hudson.plugins.copyartifact.CopyArtifactOperationContext;
import hudson.plugins.copyartifact.CopyArtifactOperation;
import hudson.plugins.copyartifact.Messages;
import hudson.tasks.Fingerprinter.FingerprintAction;

/**
 * A basic operation for copying files.
 * 
 * @since 2.0
 */
public abstract class AbstractCopyOperation extends CopyArtifactOperation {
    private String targetDir;
    private String srcBaseDir;
    private String includes;
    private String excludes;
    private boolean flatten;
    private boolean fingerprintArtifacts;
    
    /**
     * Information for a file.
     * 
     * An abstract layer for file classes (such as {@link FilePath}, {@link VirtualFile}).
     * Derived classes of {@link AbstractCopyOperation} should extend this
     * to support file classes it handles.
     */
    protected abstract static class FileInfo {
        /**
         * Returns the path where this file copied to.
         * 
         * @param basePath
         * @return the path relative from <code>basePath</code>.
         */
        @Nonnull
        public abstract FilePath getRelativeFrom(@Nonnull FilePath basePath);
        
        /**
         * @return the name of this file.
         */
        @Nonnull
        public abstract String getFilename();
        
        /**
         * @return the stream to read contents of this file.
         * @throws IOException
         * @throws InterruptedException
         */
        @Nonnull
        public abstract InputStream open() throws IOException, InterruptedException;
        
        /**
         * Performs additional copy operation, such as setting the permission of the file.
         * 
         * @param dest
         * @param context
         * @throws IOException
         * @throws InterruptedException
         */
        public void copyMetaInfoTo(@Nonnull FilePath dest, @Nonnull CopyArtifactCopyContext context) throws IOException, InterruptedException {
            // nothing to do.
        }
    }
    
    /**
     * ctor
     */
    protected AbstractCopyOperation() {
        setTargetDir("");
        setSrcBaseDir("");
        setIncludes("");
        setExcludes("");
        setFlatten(false);
        setFingerprintArtifacts(true);
    }
    
    /**
     * @param targetDir {@link FilePath} to copy files to.
     */
    @DataBoundSetter
    public void setTargetDir(@CheckForNull String targetDir) {
        this.targetDir = Util.fixNull(targetDir).trim();
    }
    
    /**
     * @return {@link FilePath} to copy files to.
     */
    @Nonnull
    public String getTargetDir() {
        return targetDir;
    }
    
    /**
     * @param srcBaseDir the additional relative path from the source directory
     */
    @DataBoundSetter
    public void setSrcBaseDir(@CheckForNull String srcBaseDir) {
        this.srcBaseDir = Util.fixNull(srcBaseDir).trim();
    }
    
    /**
     * @return the additional relative path from the source directory
     */
    @Nonnull
    public String getSrcBaseDir() {
        return srcBaseDir;
    }
    
    /**
     * @param includes
     */
    @DataBoundSetter
    public void setIncludes(@CheckForNull String includes) {
        this.includes = Util.fixNull(includes).trim();
    }
    
    /**
     * @return comma separated apache-ant file patterns for files to include.
     */
    @Nonnull
    public String getIncludes() {
        return includes;
    }
    
    /**
     * @param excludes
     */
    @DataBoundSetter
    public void setExcludes(@CheckForNull String excludes) {
        this.excludes = Util.fixNull(excludes).trim();
    }
    
    /**
     * @return comma separated apache-ant file patterns for files to exclude.
     */
    @Nonnull
    public String getExcludes() {
        return excludes;
    }
    
    /**
     * @param flatten
     */
    @DataBoundSetter
    public void setFlatten(boolean flatten) {
        this.flatten = flatten;
    }
    
    /**
     * @return whether copy files ignoring directory trees.
     */
    public boolean isFlatten() {
        return flatten;
    }
    
    /**
     * @param fingerprintArtifacts
     */
    @DataBoundSetter
    public void setFingerprintArtifacts(boolean fingerprintArtifacts) {
        this.fingerprintArtifacts = fingerprintArtifacts;
    }
    
    /**
     * @return whether to fingerprint copied artifacts.
     */
    public boolean isFingerprintArtifacts() {
        return fingerprintArtifacts;
    }
    
    /**
     * @param src
     * @param _context
     * @return
     * @throws IOException
     * @throws InterruptedException
     * @see hudson.plugins.copyartifact.CopyArtifactOperation#perform(hudson.model.Run, hudson.plugins.copyartifact.CopyArtifactOperationContext)
     */
    @Override
    @Nonnull
    public Result perform(@Nonnull Run<?, ?> src, @Nonnull CopyArtifactOperationContext _context) throws IOException, InterruptedException {
        CopyArtifactCopyContext context = new CopyArtifactCopyContext(_context);
        
        String targetDirPath = "";
        if (!StringUtils.isEmpty(getTargetDir())) {
            targetDirPath = context.getEnvVars().expand(getTargetDir());
        }
        String srcBaseDir = getSrcBaseDir();
        if (!StringUtils.isEmpty(srcBaseDir)) {
            srcBaseDir = context.getEnvVars().expand(srcBaseDir);
        }
        String expandedIncludes = context.getEnvVars().expand(getIncludes());
        if (StringUtils.isBlank(expandedIncludes)) {
            expandedIncludes = "**";
        }
        String expandedExcludes = context.getEnvVars().expand(getExcludes());
        if (StringUtils.isBlank(expandedExcludes)) {
            expandedExcludes = null;
        }
        
        context.setTargetBaseDir(context.getWorkspace());
        context.setTargetDirPath(targetDirPath);
        context.setSrcBaseDir(srcBaseDir);
        context.setIncludes(expandedIncludes);
        context.setExcludes(expandedExcludes);
        context.setFingerprintArtifacts(isFingerprintArtifacts());
        context.setFlatten(isFlatten());
        context.setSrc(src);
        
        if (context.getJenkins().getPlugin("maven-plugin") != null && (src instanceof MavenModuleSetBuild) ) {
        // use classes in the "maven-plugin" plugin as might not be installed
            // Copy artifacts from the build (ArchiveArtifacts build step)
            CopyArtifactOperation.Result copyResult = copyArtifactsFromDirect(context);
            
            // Copy artifacts from all modules of this Maven build (automatic archiving)
            for (Run<?, ?> r : ((MavenModuleSetBuild)src).getModuleLastBuilds().values()) {
                CopyArtifactCopyContext childContext = context.clone();
                childContext.setSrc(r);
                copyResult = copyResult.merge(copyArtifactsFromDirect(childContext));
            }
            
            return copyResult;
        } else if (src instanceof MatrixBuild) {
            CopyArtifactOperation.Result copyResult = CopyArtifactOperation.Result.NothingToDo;
            
            // Copy artifacts from all configurations of this matrix build
            // Use MatrixBuild.getExactRuns if available
            for (Run<?, ?> r : ((MatrixBuild) src).getExactRuns()) {
                // Use subdir of targetDir with configuration name (like "jdk=java6u20")
                CopyArtifactCopyContext childContext = context.clone();
                childContext.setSrc(r);
                childContext.setTargetBaseDir(context.getTargetBaseDir().child(r.getParent().getName()));
                copyResult = copyResult.merge(copyArtifactsFromDirect(childContext));
            }
            
            return copyResult;
        }
        
        return copyArtifactsFromDirect(context);
    }

    @Nonnull
    private Result copyArtifactsFromDirect(@Nonnull CopyArtifactCopyContext context)
            throws IOException, InterruptedException {
        context.logDebug("Copying artifacts from {0}", context.getSrc().getFullDisplayName());
        context.getTargetDir().mkdirs();
        
        try {
            if (!init(context)) {
                return Result.NothingToDo;
            }
            
            Iterable<? extends FileInfo> fileList = scanFilesToCopy(context);
            int cnt = 0;
            
            for (FileInfo file : fileList) {
                ++cnt;
                FilePath path = (!context.isFlatten())
                        ? file.getRelativeFrom(context.getTargetDir())
                        : new FilePath(context.getTargetDir(), file.getFilename());
                context.logDebug("Copying to {0}", path);
                
                copyOne(file, path, context);
            }
            
            context.logInfo(Messages.CopyArtifact_Copied(
                    cnt,
                    HyperlinkNote.encodeTo('/' + context.getSrc().getParent().getUrl(), context.getSrc().getParent().getFullDisplayName()),
                    HyperlinkNote.encodeTo('/' + context.getSrc().getUrl(), Integer.toString(context.getSrc().getNumber()))
            ));
            return (cnt > 0)?CopyArtifactOperation.Result.Succeess:CopyArtifactOperation.Result.NothingToDo;
        } finally {
            end(context);
        }
    }

    /**
     * Copy a file.
     * 
     * Subclasses can override this class to perform additional operations when copying files.
     * see also {@link FileInfo#copyMetaInfoTo(FilePath, CopyArtifactCopyContext)}
     * 
     * @param file      the file to copy
     * @param path      the destination path
     * @param context   context of the operation.
     * @throws IOException
     * @throws InterruptedException
     */
    protected void copyOne(@Nonnull FileInfo file, @Nonnull FilePath path, @Nonnull CopyArtifactCopyContext context)
            throws IOException, InterruptedException {
        MessageDigest md5 = context.getMd5();
        InputStream in = file.open();
        OutputStream out = path.write();
        if (md5 != null) {
            out = new DigestOutputStream(out, md5);
        }
        
        try {
            IOUtils.copy(in, out);
        } finally {
            in.close();
            out.close();
        }

        file.copyMetaInfoTo(path, context);

        if (md5 != null) {
            String digest = Util.toHexString(md5.digest());
            
            FingerprintMap map = context.getJenkins().getFingerprintMap();

            Fingerprint f = map.getOrCreate(context.getSrc(), file.getFilename(), digest);
            f.addFor(context.getSrc());
            f.addFor(context.getCopierBuild());
            context.getFingerprints().put(file.getFilename(), digest);
            md5.reset();
        }
    }

    private MessageDigest newMD5() {
        try {
            return MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(e);    // impossible
        }
    }

    /**
     * Called before copy-artifact operation.
     *
     * @param context
     *      parameters to copying operations.
     *      You can save execution state with {@link CopyArtifactCopyContext#addExtension(Object)}
     * @return true to start the copy operation.
     * 
     * @see #end(CopyArtifactCopyContext)
     */
    public boolean init(@Nonnull CopyArtifactCopyContext context) throws IOException, InterruptedException {
        context.setMd5(context.isFingerprintArtifacts() ? newMD5() : null);
        context.setFingerprints(new HashMap<String, String>());
        return true;
    }
    
    /**
     * Called after copy-artifact operation.
     * Be aware that this may be called even you returned <code>false</code> in {@link #init(CopyArtifactCopyContext)}
     *
     * @param context
     * @throws IOException
     * @throws InterruptedException
     * 
     * @see #init(CopyArtifactCopyContext)
     */
    public void end(@Nonnull CopyArtifactCopyContext context) throws IOException, InterruptedException {
        // add action
        for (Run<?,?> r : new Run[]{context.getSrc(), context.getCopierBuild()}) {
            if (context.getFingerprints().size() > 0) {
                FingerprintAction fa = r.getAction(FingerprintAction.class);
                if (fa != null) {
                    fa.add(context.getFingerprints());
                } else {
                    r.addAction(new FingerprintAction(r, context.getFingerprints()));
                }
            }
        }
    }
    
    /**
     * Return the list of files to copy.
     * Subclasses should override this.
     * Don't forget to handle {@link CopyArtifactCopyContext#getSrcBaseDir()}.
     * 
     * @param context
     * @return the list of files to copy.
     * @throws IOException
     * @throws InterruptedException
     */
    @Nonnull
    protected abstract Iterable<? extends FileInfo> scanFilesToCopy(@Nonnull CopyArtifactCopyContext context) throws IOException, InterruptedException;

}
