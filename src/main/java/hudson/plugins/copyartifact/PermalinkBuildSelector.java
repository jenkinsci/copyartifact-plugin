/*
 * The MIT License
 *
 * Copyright (c) 2010-2011, InfraDNA, Inc., Alan Harder
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

import java.util.Map;

import hudson.EnvVars;
import hudson.Extension;
import hudson.RelativePath;
import hudson.model.Descriptor;
import hudson.model.Job;
import hudson.model.PermalinkProjectAction.Permalink;
import hudson.model.Run;
import hudson.util.ComboBoxModel;
import hudson.util.ListBoxModel;
import hudson.util.ListBoxModel.Option;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

/**
 * Picks up a build through {@link Permalink}
 *
 * @author Kohsuke Kawaguchi
 */
public class PermalinkBuildSelector extends BuildSelector {
    public final String id;

    @DataBoundConstructor
    public PermalinkBuildSelector(String id) {
        this.id = id;
    }

    @Override
    public Run<?,?> getBuild(Job<?, ?> job, EnvVars env, BuildFilter filter, Run<?,?> parent) {
        Permalink p = job.getPermalinks().get(id);
        if (p==null)    return null;
        Run<?,?> run = p.resolve(job);
        return (run != null && filter.isSelectable(run, env)) ? run : null;
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<BuildSelector> {
        @Override
        public String getDisplayName() {
            return Messages.PermalinkBuildSelector_DisplayName();
        }

        public ListBoxModel doFillIdItems(@AncestorInPath Job defaultJob, @RelativePath("..") @QueryParameter("projectName") String projectName) {
            // gracefully fall back to some job, if none is given
            Job j = null;
            if (projectName!=null)  j = Jenkins.getInstance().getItem(projectName,defaultJob,Job.class);
            if (j==null)    j = defaultJob;

            ListBoxModel r = new ListBoxModel();
            for (Permalink p : j != null ? j.getPermalinks() : Permalink.BUILTIN) {
                r.add(new Option(p.getDisplayName(),p.getId()));
            }
            return r;
        }

        public ComboBoxModel doFillIdForComboItems(@AncestorInPath Job copyingJob, @RelativePath("..") @QueryParameter("projectName") String projectName) {
            Job j = null;
            if (projectName != null) {
                j = Jenkins.getInstance().getItem(projectName, copyingJob, Job.class);
            }
            ComboBoxModel r = new ComboBoxModel();
            for (Permalink p : j != null ? j.getPermalinks() : Permalink.BUILTIN) {
                r.add(p.getId());
            }
            return r;
        }

        /**
         * Allow use another field name with attribute "fillField"
         * 
         * @param field
         * @param attributes
         * @see hudson.model.Descriptor#calcFillSettings(java.lang.String, java.util.Map)
         */
        @Override
        public void calcFillSettings(String field, Map<String, Object> attributes) {
            super.calcFillSettings(attributes.containsKey("fillField")?(String)attributes.get("fillField"):field, attributes);
        }
    }
}
