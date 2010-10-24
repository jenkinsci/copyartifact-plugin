/*
 * The MIT License
 *
 * Copyright (c) 2004-2010, Sun Microsystems, Inc., Alan Harder
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
import hudson.model.Hudson;
import java.io.File;
import java.io.IOException;
import java.net.URL;

/**
 * Default implementation of CopyMethod extension point,
 * using Hudson's FilePath class.  Has -100 ordinal value so any other
 * plugin implementing this extension point should override this one.
 * @author Alan Harder
 */
@Extension(ordinal=-100)
public class FilePathCopyMethod implements CopyMethod {

    public void init(FilePath srcDir, FilePath baseTargetDir)
            throws IOException, InterruptedException {
        // Workaround for HUDSON-5977.. this block can be removed whenever
        // copyartifact plugin raises its minimum Hudson version to whatever
        // release fixes #5977.
        // Make a call to copy a small file, to get all class-loading to happen.
        // When we copy the real stuff there won't be any classloader requests
        // coming the other direction, which due to full-buffer-deadlock problem
        // can cause slave to hang.
        hudson.PluginWrapper pw = Hudson.getInstance().getPluginManager().getPlugin("copyartifact");
        if (pw==null) { System.out.println("foo"); return; }
        URL base = pw.baseResourceURL;
        if (base != null && "file".equals(base.getProtocol())) {
            FilePath tmp = baseTargetDir.createTempDir("copyartifact", ".dir");
            new FilePath(new File(base.getPath())).copyRecursiveTo("HUDSON-5977/**", tmp);
            tmp.deleteRecursive();
        }
        // End workaround
    }

    /** @see FilePath#recursiveCopyTo(String,FilePath) */
    public int copyAll(FilePath srcDir, String filter, FilePath targetDir)
            throws IOException, InterruptedException {
        return srcDir.copyRecursiveTo(filter, targetDir);
    }

    /** @see FilePath#copyTo(FilePath) */
    public void copyOne(FilePath source, FilePath target)
            throws IOException, InterruptedException {
        source.copyTo(target);
    }
}
