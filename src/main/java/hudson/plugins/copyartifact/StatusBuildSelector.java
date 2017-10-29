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
import hudson.model.Result;
import hudson.model.Run;
import jenkins.model.Jenkins;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

/**
 * Copy artifacts from the latest successful or stable build.
 * @author Alan Harder
 */
public class StatusBuildSelector extends BuildSelector {
    private Boolean stable;

    @Deprecated
    public StatusBuildSelector(boolean stable) {
        setStable(stable);
    }

    @DataBoundConstructor
    public StatusBuildSelector() {
    }

    @DataBoundSetter
    public void setStable(boolean stable) {
        this.stable = stable ? Boolean.TRUE : null;
    }


    public boolean isStable() {
        return stable != null && stable.booleanValue();
    }

    @Override
    public boolean isSelectable(Run<?,?> run, EnvVars env) {
        return isBuildResultBetterOrEqualTo(run, isStable() ? Result.SUCCESS : Result.UNSTABLE);
    }

    /**
     * @deprecated
     *      here for backward compatibility. Get it from {@link Jenkins#getDescriptor(Class)}
     */
    public static /*almost final*/ Descriptor<BuildSelector> DESCRIPTOR;

    @Extension(ordinal=100) @Symbol("lastSuccessful")
    public static final class DescriptorImpl extends SimpleBuildSelectorDescriptor {
        public DescriptorImpl() {
            super(StatusBuildSelector.class, Messages._StatusBuildSelector_DisplayName());
            DESCRIPTOR = this;
        }
    }
}
