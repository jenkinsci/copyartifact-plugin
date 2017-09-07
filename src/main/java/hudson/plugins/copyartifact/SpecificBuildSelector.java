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
import hudson.model.Descriptor;
import hudson.model.Job;
import hudson.model.PermalinkProjectAction;
import hudson.model.Run;
import hudson.plugins.copyartifact.selector.AbstractSpecificBuildSelector;

import java.io.IOException;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Copy artifacts from a specific build.
 * @author Alan Harder
 */
public class SpecificBuildSelector extends AbstractSpecificBuildSelector {

    private final String buildNumber;

    @DataBoundConstructor
    public SpecificBuildSelector(String buildNumber) {
        this.buildNumber = buildNumber;
    }

    public String getBuildNumber() {
        return buildNumber;
    }

    @Override
    @CheckForNull
    public Run<?, ?> getBuild(@Nonnull Job<?, ?> job, @Nonnull CopyArtifactPickContext context) throws IOException, InterruptedException {
        String num = context.getEnvVars().expand(buildNumber);
        if (num.startsWith("$")) {
            context.logDebug("unresolved variable {0}", num);
            return null;
        }

        Run<?,?> run = null;

        if(num.matches("[0-9]*")) {
            //If its a number, retrieve the build.
            run = job.getBuildByNumber(Integer.parseInt(num));
        } else {
            //Otherwise, check if the buildNumber value is a permalink or a display name.
            PermalinkProjectAction.Permalink p = job.getPermalinks().get(num);
            if (p == null) {
                //Not a permalink so check if the buildNumber value is a display name.
                for(Run<?,?> build: job.getBuilds()){
                    if(num.equals(build.getDisplayName())) {
                        //First named build found is the right one, going from latest build to oldest.
                        run = build;
                        break;
                    }
                }
            } else {
                //Retrieve the permalink
                run = p.resolve(job);
            }
        }

        if (run == null) {
            context.logDebug("no such build {0} in {1}", num, job.getFullName());
            return null;
        }
        return run;
    }

    @Extension(ordinal=-10)
    public static final Descriptor<BuildSelector> DESCRIPTOR =
            new SimpleBuildSelectorDescriptor(
                SpecificBuildSelector.class, Messages._SpecificBuildSelector_DisplayName());
}
