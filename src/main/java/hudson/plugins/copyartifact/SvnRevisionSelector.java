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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import hudson.EnvVars;
import hudson.Extension;
import hudson.model.Descriptor;
import hudson.model.Job;
import hudson.model.Run;
import hudson.util.RunList;

import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Copy artifacts with given SVN revision.
 * 
 * @author Sascha Vetter
 */
public class SvnRevisionSelector extends BuildSelector {
    private String svnRevision;

    @DataBoundConstructor
    public SvnRevisionSelector(String svnRevision) {
        this.svnRevision = svnRevision;
    }

    public String getSvnRevision() {
        return svnRevision;
    }

    @Override
    public Run<?, ?> getBuild(Job<?, ?> job, EnvVars env, BuildFilter filter, Run<?, ?> parent) {
        String num = env.expand(svnRevision);
        if (num.startsWith("$"))
            return null; // unresolved variable, probably unset
        Run<?, ?> run = getBuildBySvnRevision(job, Integer.parseInt(num));
        return (run != null && filter.isSelectable(run, env)) ? run : null;
    }

    private Run getBuildBySvnRevision(Job job, int revision) {
        RunList runlist = job.getBuilds();

        for (Object item : runlist) {
            Run build = (Run) item;
            Map<String, Integer> revs = null;
            try {
                revs = parseRevisionFile(build.getRootDir());
            } catch (IOException e) {
                // corrupt file -> ignore
            }

            if (revs == null)
                continue;

            for (Integer rev : revs.values()) {
                if (rev.intValue() == revision)
                    return build;
            }
        }

        return null;
    }

    private static Map<String, Integer> parseRevisionFile(File rootDir) throws IOException {
        Map<String, Integer> revisions = new HashMap<String, Integer>(); // module
                                                                         // ->
        // revision
        {// read the revision file of the last build
            File file = new File(rootDir, "revision.txt");
            if (!file.exists())
                // nothing to compare against
                return revisions;

            BufferedReader br = new BufferedReader(new FileReader(file));
            try {
                String line;
                while ((line = br.readLine()) != null) {
                    int index = line.lastIndexOf('/');
                    if (index < 0) {
                        continue; // invalid line?
                    }
                    try {
                        revisions.put(line.substring(0, index), Integer.parseInt(line.substring(index + 1)));
                    } catch (NumberFormatException e) {
                        // perhaps a corrupted line. ignore
                    }
                }
            } finally {
                br.close();
            }
        }

        return revisions;
    }

    @Extension(ordinal = -10)
    public static final Descriptor<BuildSelector> DESCRIPTOR = new SimpleBuildSelectorDescriptor(
            SvnRevisionSelector.class, Messages._SvnRevisionSelector_DisplayName());
}
