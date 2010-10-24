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

import hudson.ExtensionPoint;
import hudson.FilePath;
import java.io.IOException;

/**
 * Extension point for how files are copied.
 * CopyArtifact plugin provides a default implementation using methods
 * available in Hudson's FilePath class.
 * @author Alan Harder
 */
public interface CopyMethod extends ExtensionPoint {

    /**
     * Called before copy-artifact operation.
     * @param srcDir Source for upcoming file copy
     * @param baseTargetDir Base target dir for upcoming file copy (the copy-artifact
     *   build step may later specify a deeper target dir)
     */
    void init(FilePath srcDir, FilePath baseTargetDir) throws IOException, InterruptedException;

    /**
     * Copy files matching the given file mask to the specified target.
     * @param srcDir Source directory
     * @param filter Ant GLOB pattern
     * @param targetDir Target directory
     * @return Number of files that were copied
     * @see FilePath#copyRecursiveTo(String,FilePath)
     */
    int copyAll(FilePath srcDir, String filter, FilePath targetDir) throws IOException, InterruptedException;

    /**
     * Copy a single file.
     * @param source Source file
     * @param target Target file (includes filename; this is not the target directory).
     *   Directory for target should already exist (copy-artifact build step calls mkdirs).
     * @see FilePath#copyTo(FilePath)
     */
    void copyOne(FilePath source, FilePath target) throws IOException, InterruptedException;
}
