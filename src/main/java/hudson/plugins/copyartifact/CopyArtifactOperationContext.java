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

import javax.annotation.Nonnull;

import hudson.FilePath;

/**
 * Context for operation of copyartifact.
 * This allows us to adding new fields without affecting
 * existing plugins.
 * 
 * @since 2.0
 */
public class CopyArtifactOperationContext extends CopyArtifactCommonContext {
    private FilePath workspace;
    
    /**
     * @param workspace
     */
    public void setWorkspace(@Nonnull FilePath workspace) {
        this.workspace = workspace;
    }
    
    @Nonnull
    public FilePath getWorkspace() {
        return workspace;
    }
    
    /**
     * ctor
     */
    public CopyArtifactOperationContext() {
    }

    /**
     * Creates a new instance copying src data.
     * 
     * @param src
     */
    protected CopyArtifactOperationContext(@Nonnull CopyArtifactOperationContext src) {
        super(src);
        this.workspace = src.workspace;
    }

    /**
     * @return
     * @see hudson.plugins.copyartifact.CopyArtifactCommonContext#clone()
     */
    @Override
    public CopyArtifactOperationContext clone() {
        return new CopyArtifactOperationContext(this);
    }
}
