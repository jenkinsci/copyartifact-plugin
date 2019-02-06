/*
 * The MIT License
 *
 * Copyright (c) 2019, Chad Gilman
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
import hudson.model.Run;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

public class LastBuildWithArtifactSelector extends BuildSelector {

    @DataBoundConstructor
    public LastBuildWithArtifactSelector() {
    }

    @Override
    public boolean isSelectable(Run<?, ?> run, EnvVars env) {
        return run.getHasArtifacts();
    }

    @Extension @Symbol("lastWithArtifacts")
    public static class DescriptorImpl extends Descriptor<BuildSelector> {
        @Override
        public String getDisplayName() {
            return Messages.LastBuildWithArtifactSelector_DisplayName();
        }
    }
}
