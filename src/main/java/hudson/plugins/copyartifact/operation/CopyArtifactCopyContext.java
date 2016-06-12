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

import java.security.MessageDigest;
import java.util.Map;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

import hudson.FilePath;
import hudson.model.Run;
import hudson.plugins.copyartifact.CopyArtifactOperationContext;

/**
 * Context for file-copy-operation of copyartifact.
 * This allows us to adding new fields without affecting
 * existing plugins.
 * 
 * @since 2.0
 * @see AbstractCopyOperation
 */
public class CopyArtifactCopyContext extends CopyArtifactOperationContext {
    private FilePath targetBaseDir;
    private String targetDirPath;
    private String srcBaseDir;
    private String includes;
    private String excludes;
    private boolean flatten;
    private boolean fingerprintArtifacts;
    private Run<?,?> src;
    private MessageDigest md5;
    private Map<String,String> fingerprints;

    /**
     * @param targetBaseDir bese directory to copy files to
     */
    public void setTargetBaseDir(@Nonnull FilePath targetBaseDir) {
        this.targetBaseDir = targetBaseDir;
    }

    /**
     * Returns the base directory to copy files to.
     * Usually you want to use {@link #getTargetDir()}.
     * 
     * This is stored to allow insert intermediate directories
     * for target directories (${targetBaseDir}/something/${targetDirName})
     * 
     * @return the bese directory of {@link FilePath} to copy files to.
     */
    @Nonnull
    public FilePath getTargetBaseDir() {
        return targetBaseDir;
    }

    /**
     * @param targetDirPath path from {@link #getTargetBaseDir()} to copy files to
     */
    public void setTargetDirPath(@Nonnull String targetDirPath) {
        this.targetDirPath = targetDirPath;
    }

    /**
     * @return the path from {@link #getTargetBaseDir()} to copy files to.
     */
    @Nonnull
    public String getTargetDirPath() {
        return targetDirPath;
    }

    /**
     * @return {@link FilePath} to copy files to.
     */
    @Nonnull
    public FilePath getTargetDir() {
        return getTargetBaseDir().child(getTargetDirPath());
    }

    /**
     * @param srcBaseDir the additional relative path from the source directory
     */
    public void setSrcBaseDir(@Nonnull String srcBaseDir) {
        this.srcBaseDir = srcBaseDir;
    }

    /**
     * @return the additional relative path from the source directory
     */
    @Nonnull
    public String getSrcBaseDir() {
        return srcBaseDir;
    }

    /**
     * @param includes comma separated apache-ant file patterns for files to include
     */
    public void setIncludes(@CheckForNull String includes) {
        this.includes = includes;
    }

    /**
     * @return comma separated apache-ant file patterns for files to include.
     */
    @CheckForNull
    public String getIncludes() {
        return includes;
    }

    /**
     * @param excludes comma separated apache-ant file patterns for files to exclude
     */
    public void setExcludes(@CheckForNull String excludes) {
        this.excludes = excludes;
    }

    /**
     * @return comma separated apache-ant file patterns for files to exclude.
     */
    @CheckForNull
    public String getExcludes() {
        return excludes;
    }

    /**
     * @param flatten whether copy files ignoring directory trees
     */
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
     * @param fingerprintArtifacts whether to fingerprint copied artifacts
     */
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
     * @param src the target build
     */
    public void setSrc(@Nonnull Run<?, ?> src) {
        this.src = src;
    }

    /**
     * @return the target build
     */
    @Nonnull
    public Run<?, ?> getSrc() {
        return src;
    }

    /**
     * @param md5 used for fingerprinting.
     */
    public void setMd5(@CheckForNull MessageDigest md5) {
        this.md5 = md5;
    }

    /**
     * @return md5 generator for fingerprinting.
     */
    @CheckForNull
    public MessageDigest getMd5() {
        return md5;
    }

    /**
     * @param fingerprints a map to store fingerprints for copied files.
     */
    public void setFingerprints(@Nonnull Map<String, String> fingerprints) {
        this.fingerprints = fingerprints;
    }

    /**
     * @return a map to store fingerprints for copied files.
     */
    @Nonnull
    public Map<String, String> getFingerprints() {
        return fingerprints;
    }

    /**
     * Creates a new context extending existing {@link CopyArtifactOperationContext}.
     * 
     * @param src exisiting {@link CopyArtifactOperationContext}
     */
    public CopyArtifactCopyContext(CopyArtifactOperationContext src) {
        super(src);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CopyArtifactCopyContext clone() {
        return (CopyArtifactCopyContext)super.clone();
    }
}
