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

import hudson.EnvVars;
import hudson.Extension;
import hudson.model.Descriptor;
import hudson.model.Job;
import hudson.model.PermalinkProjectAction;
import hudson.model.Run;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Copy artifacts from a specific build.
 * @author Alan Harder
 */
public class SpecificBuildSelector extends BuildSelector {

    private static final Logger LOGGER = Logger.getLogger(SpecificBuildSelector.class.getName());

    private final String buildNumber;

    @DataBoundConstructor
    public SpecificBuildSelector(String buildNumber) {
        this.buildNumber = buildNumber;
    }

    public String getBuildNumber() {
        return buildNumber;
    }

    @Override
    public Run<?, ?> getBuild(Job<?, ?> job, EnvVars env, BuildFilter filter, Run<?, ?> parent) {
        String num = env.expand(buildNumber);
        if (num.startsWith("$")) {
            LOGGER.log(Level.FINE, "unresolved variable {0}", num);
            return null;
        }

        Run<?, ?> run = null;

        if (num.matches("[0-9]*")) {
            // If its a number, retrieve the build.
            run = job.getBuildByNumber(Integer.parseInt(num));
        } else {
            // Otherwise, check if the buildNumber value is a permalink or a display name.
            PermalinkProjectAction.Permalink p = job.getPermalinks().get(num);
            if (p == null) {
                // Not a permalink so check if the buildNumber value is a display name.
                for (Run<?, ?> build : job.getBuilds()) {
                    if (num.equals(build.getDisplayName())) {
                        // First named build found is the right one, going from latest build to oldest.
                        run = build;
                        break;
                    }
                }
            } else {
                // Retrieve the permalink
                run = p.resolve(job);
            }
        }

        if (run == null) {
            LOGGER.log(Level.FINE, "no such build {0} in {1}", new Object[] {num, job.getFullName()});
            return null;
        }
        if (!filter.isSelectable(run, env)) {
            LOGGER.log(Level.FINE, "{0} claims {1} is not selectable", new Object[] {filter, run});
            return null;
        }
        return run;
    }

    /**
     * @deprecated
     *      here for backward compatibility. Get it from {@link Jenkins#getDescriptor(Class)}
     */
    @Deprecated
    public static /*almost final*/ Descriptor<BuildSelector> DESCRIPTOR;

    @Extension(ordinal = -10)
    @Symbol("specific")
    public static final class DescriptorImpl extends SimpleBuildSelectorDescriptor {
        public DescriptorImpl() {
            super(SpecificBuildSelector.class, Messages._SpecificBuildSelector_DisplayName());
            DESCRIPTOR = this;
        }
    }
}
