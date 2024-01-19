/*
 * The MIT License
 *
 * Copyright (c) 2004-2011, Sun Microsystems, Inc., Alan Harder
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

import hudson.Extension;
import hudson.FilePath;
import hudson.model.Run;

import java.io.IOException;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Default implementation of CopyMethod extension point,
 * using the Jenkins FilePath class.  Has -100 ordinal value so any other
 * plugin implementing this extension point should override this one.
 * @author Alan Harder
 * @deprecated No longer used.
 */
@Deprecated
@Extension(ordinal=-200)
public class FilePathCopyMethod extends Copier {
    /** @see FilePath#copyRecursiveTo(String,FilePath) */
    @Override
    public int copyAll(FilePath srcDir, String filter, String excludes, FilePath targetDir, boolean fingerprintArtifacts)
            throws IOException, InterruptedException {
        return srcDir.copyRecursiveTo(filter, excludes, targetDir);
    }

    /** @see FilePath#copyTo(FilePath) */
    @Override
    public void copyOne(FilePath source, FilePath target, boolean fingerprintArtifacts)
            throws IOException, InterruptedException {
        source.copyToWithPermission(target);
    }

    @SuppressFBWarnings(
            value = "CN_IMPLEMENTS_CLONE_BUT_NOT_CLONEABLE",
            justification = "This is a method not of Cloneable but of Copier."
    )
    @Override
    public Copier clone() {
        return this;
    }

    @Override public void initialize(Run<?, ?> src, Run<?, ?> dst, FilePath srcDir, FilePath baseTargetDir) throws IOException, InterruptedException {}

}
