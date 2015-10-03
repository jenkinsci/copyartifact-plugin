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
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
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
 */
public class CopyArtifactCopyContext extends CopyArtifactOperationContext {
    private FilePath targetBaseDir;
    private String targetDirPath;
    private String includes;
    private String excludes;
    private boolean flatten;
    private boolean fingerprintArtifacts;
    private Run<?,?> src;
    private MessageDigest md5;
    private Map<String,String> fingerprints;

    /**
     * @param targetBaseDir
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
     * @param targetDirPath
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
     * @param includes
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
     * @param excludes
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
     * @param flatten
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
     * @param fingerprintArtifacts
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

    public void setSrc(Run<?, ?> src) {
        this.src = src;
    }

    public Run<?, ?> getSrc() {
        return src;
    }

    public void setMd5(MessageDigest md5) {
        this.md5 = md5;
    }

    public MessageDigest getMd5() {
        return md5;
    }

    public void setFingerprints(Map<String, String> fingerprints) {
        this.fingerprints = fingerprints;
    }

    public Map<String, String> getFingerprints() {
        return fingerprints;
    }

    public CopyArtifactCopyContext(CopyArtifactOperationContext src) {
        super(src);
    }

    public CopyArtifactCopyContext(CopyArtifactCopyContext src) {
        super(src);
        this.targetBaseDir = src.targetBaseDir;
        this.targetDirPath = src.targetDirPath;
        this.includes = src.includes;
        this.excludes = src.excludes;
        this.flatten = src.flatten;
        this.fingerprintArtifacts = src.fingerprintArtifacts;
        this.src = src.src;
        this.md5 = src.md5;
        this.fingerprints = src.fingerprints;
    }

    @Override
    public CopyArtifactCopyContext clone() {
        return new CopyArtifactCopyContext(this);
    }
}
