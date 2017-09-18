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

import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

import hudson.Plugin;
import jenkins.model.Jenkins;

/**
 * Copy Artifact plugin.
 */
public class CopyArtifactPlugin extends Plugin {
    @Override
    public void postInitialize() throws Exception {
        Jenkins jenkins = Jenkins.getInstance();
        if (jenkins != null) {
            Logger.getLogger(CopyArtifactPlugin.class.getName()).log(
                Level.INFO,
                String.format(
                    "I'm called with Plugin#postInitialize at %s",
                    jenkins.getInitLevel().toString()
                )
            );
            System.err.println(String.format(
                "%s: I'm called with Plugin#postInitialize at %s",
                new Date(),
                jenkins.getInitLevel().toString()
            ));
        }
    }
}
