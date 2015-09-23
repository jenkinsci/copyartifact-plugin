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

package hudson.plugins.copyartifact;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

import hudson.FilePath;

/**
 * Context for file-copy-operation of copyartifact.
 * This allows us to adding new fields without affecting
 * existing plugins.
 * 
 * @since 2.0
 */
public class CopyArtifactCopyContext extends CopyArtifactCommonContext {
    private FilePath targetDir;
    private String includes;
    private String excludes;
    private Copier copier;
    private boolean flatten;
    private boolean fingerprintArtifacts;

    /**
     * @param targetDir
     */
    public void setTargetDir(@Nonnull FilePath targetDir) {
        this.targetDir = targetDir;
    }

    /**
     * @return {@link FilePath} to copy files to.
     */
    @Nonnull
    public FilePath getTargetDir() {
        return targetDir;
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
     * @param copier
     */
    public void setCopier(@Nonnull Copier copier) {
        this.copier = copier;
    }
    
    /**
     * @return a object to perform file copies.
     */
    @Nonnull
    public Copier getCopier() {
        return copier;
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

    /**
     * ctor
     */
    public CopyArtifactCopyContext() {
    }

    /**
     * Creates a new instance copying src data.
     * 
     * @param src
     */
    protected CopyArtifactCopyContext(@Nonnull CopyArtifactCopyContext src) {
        super(src);
        this.targetDir = src.targetDir;
        this.includes = src.includes;
        this.excludes = src.excludes;
        this.copier = src.copier;
        this.flatten = src.flatten;
    }

    /**
     * @return
     * @see hudson.plugins.copyartifact.CopyArtifactCommonContext#clone()
     */
    @Override
    protected CopyArtifactCopyContext clone() {
        return new CopyArtifactCopyContext(this);
    }
}
