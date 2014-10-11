/*
 * The MIT License
 * 
 * Copyright (c) 2014 IKEDA Yasuyuki
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

package hudson.plugins.copyartifact.testutils;

import java.io.IOException;

import jenkins.model.Jenkins;
import hudson.Launcher;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.Cause;
import hudson.model.Job;
import hudson.model.Run;
import hudson.model.BuildListener;
import hudson.tasks.Builder;

/**
 * Removes the upstream build.
 * (Only removes the direct upstream build).
 */
public class RemoveUpstreamBuilder extends Builder {
    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener)
            throws InterruptedException, IOException {
        for (Cause.UpstreamCause c: Util.filter(build.getCauses(), Cause.UpstreamCause.class)) {
            Job<?,?> upstreamProject = Jenkins.getInstance().getItemByFullName(c.getUpstreamProject(), Job.class);
            if (upstreamProject == null) {
                listener.getLogger().println(String.format("Not Found: %s", c.getUpstreamProject()));
                continue;
            }
            
            Run<?,?> upstreamBuild = upstreamProject.getBuildByNumber(c.getUpstreamBuild());
            if (upstreamBuild == null) {
                listener.getLogger().println(String.format("Not Found: %s - %d", upstreamProject.getFullName(), c.getUpstreamBuild()));
                continue;
            }
            
            listener.getLogger().println(String.format("Removed: %s - %s", upstreamProject.getFullName(), upstreamBuild.getFullDisplayName()));
            upstreamBuild.delete();
        }
        return true;
    }
}
