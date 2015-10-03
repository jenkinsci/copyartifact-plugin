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
import java.util.List;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

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
 */
public abstract class AbstractCopyOperation extends CopyArtifactOperation {
    private String targetDir;
    private String srcBaseDir;
    private String includes;
    private String excludes;
    private boolean flatten;
    private boolean fingerprintArtifacts;
    
    protected abstract static class FileInfo {
        public abstract FilePath getRelativeFrom(FilePath path);
        public abstract String getFilename();
        public abstract InputStream open() throws IOException, InterruptedException;
        public void copyMetaInfoTo(FilePath dest, CopyArtifactCopyContext context) throws IOException, InterruptedException {
            // nothing to do.
        }
    }
    
    protected AbstractCopyOperation() {
        setTargetDir("");
        setSrcBaseDir("");
    }
    
    @DataBoundSetter
    public void setTargetDir(@CheckForNull String targetDir) {
        this.targetDir = Util.fixNull(targetDir).trim();
    }
    
    @Nonnull
    public String getTargetDir() {
        return targetDir;
    }
    
    @DataBoundSetter
    public void setSrcBaseDir(@CheckForNull String srcBaseDir) {
        this.srcBaseDir = Util.fixNull(srcBaseDir).trim();
    }
    
    @Nonnull
    public String getSrcBaseDir() {
        return srcBaseDir;
    }
    
    @DataBoundSetter
    public void setIncludes(@CheckForNull String includes) {
        this.includes = Util.fixNull(includes).trim();
    }
    
    @Nonnull
    public String getIncludes() {
        return includes;
    }
    
    @DataBoundSetter
    public void setExcludes(@CheckForNull String excludes) {
        this.excludes = Util.fixNull(excludes).trim();
    }
    
    @Nonnull
    public String getExcludes() {
        return excludes;
    }
    
    @DataBoundSetter
    public void setFlatten(boolean flatten) {
        this.flatten = flatten;
    }
    
    public boolean isFlatten() {
        return flatten;
    }
    
    @DataBoundSetter
    public void setFingerprintArtifacts(boolean fingerprintArtifacts) {
        this.fingerprintArtifacts = fingerprintArtifacts;
    }
    
    public boolean isFingerprintArtifacts() {
        return fingerprintArtifacts;
    }
    
    @Override
    public Result perform(Run<?, ?> src, CopyArtifactOperationContext _context) throws IOException, InterruptedException {
        CopyArtifactCopyContext context = new CopyArtifactCopyContext(_context);
        
        String targetDirPath = "";
        if (!StringUtils.isEmpty(getTargetDir())) {
            targetDirPath = context.getEnvVars().expand(getTargetDir());
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

    private Result copyArtifactsFromDirect(CopyArtifactCopyContext context)
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
                FilePath path = context.isFlatten()
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

    protected void copyOne(FileInfo file, FilePath path, CopyArtifactCopyContext context)
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
     * @param src
     *      The build record from which we are copying artifacts.
     * @param context
     *      parameters to copying operations.
     *      You can save execution state with {@link CopyArtifactOperationContext#addExtension(Object)}
     * 
     * @since 2.0
     */
    public boolean init(CopyArtifactCopyContext context) throws IOException, InterruptedException {
        context.setMd5(context.isFingerprintArtifacts() ? newMD5() : null);
        context.setFingerprints(new HashMap<String, String>());
        return true;
    }
    
    public void end(CopyArtifactCopyContext context) throws IOException, InterruptedException {
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
    
    protected abstract Iterable<? extends FileInfo> scanFilesToCopy(CopyArtifactCopyContext context) throws IOException, InterruptedException;

}
