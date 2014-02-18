package hudson.plugins.copyartifact;

import hudson.model.Action;
import hudson.model.Job;
import hudson.model.Run;
import hudson.FilePath;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Set;
import java.util.HashSet;
import java.util.ArrayList;
import java.io.Serializable;

import org.kohsuke.stapler.StaplerProxy;

public class CopiedArtifactsRunAction implements Action, StaplerProxy {

    static {
        Run.XSTREAM2.alias("copied-artifact", CopySource.class);
    }

    private Collection<CopySource> copiedArtifacts;

    public static class CopySource {
        private String jobname;
        private int buildNumber;
        private Set<String> files;

        public CopySource(String jobname, int buildNumber) {
            this.jobname = jobname;
            this.buildNumber = buildNumber;
            this.files = new HashSet<String>();
        }

        public void addSourceFile(String file) {
            this.files.add(file);
        }

        public String getSourceJobName() {
            return jobname;
        }

        public int getSourceBuildNumber() {
            return buildNumber;
        }

        public Collection<String> getFiles() {
            return this.files;
        }
    }

    public Object getTarget() {
        return this;
    }

    public CopiedArtifactsRunAction() {
        this.copiedArtifacts = new ArrayList<CopySource>();
    }

    public String getIconFileName() {
        return "package.png";
    }

    public String getDisplayName() {
        return "Copied Artifacts";
    }

    public String getUrlName() {
        return "copiedArtifacts";
    }

    public Collection<CopySource> getCopiedArtifacts() {
        ArrayList<CopySource> result = new ArrayList<CopySource>(this.copiedArtifacts);
        Collections.sort(result, new Comparator<CopySource>() {
            public int compare(CopySource one, CopySource two) {
                if (!one.getSourceJobName().equals(two.getSourceJobName())) {
                    return one.getSourceJobName().compareTo(two.getSourceJobName());
                }
                return Integer.valueOf(one.getSourceBuildNumber()).compareTo(Integer.valueOf(two.getSourceBuildNumber()));
            }
        });
        return result;
    }

    public void recordSourceFile(Run src, String file) {
        Job job = src.getParent();
        int buildNumber = src.getNumber();

        CopySource cs = null;

        for (CopySource cs2: this.copiedArtifacts) {
            if (cs2.getSourceJobName().equals(job.getFullName()) && cs2.getSourceBuildNumber() == buildNumber) {
                cs = cs2;
                break;
            }
        }

        if (cs == null) {
            cs = new CopySource(job.getFullName(), buildNumber);
            this.copiedArtifacts.add(cs);
        }

        cs.addSourceFile(file);
    }
}
