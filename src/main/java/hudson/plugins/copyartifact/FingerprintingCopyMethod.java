package hudson.plugins.copyartifact;

import hudson.Extension;
import hudson.FilePath;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.Fingerprint;
import hudson.model.FingerprintMap;
import hudson.model.Run;
import hudson.tasks.Fingerprinter.FingerprintAction;
import hudson.util.IOException2;
import jenkins.model.Jenkins;

import java.io.IOException;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;

/**
 * Performs fingerprinting during the copy.
 *
 * This minimizes the cost of the fingerprinting as the I/O bound nature of the copy operation
 * masks the cost of digest computation.
 *
 * @author Kohsuke Kawaguchi
 */
@Extension(ordinal=-100)
public class FingerprintingCopyMethod extends Copier {
    /**
     * Null if the source of the copy operation isn't {@link AbstractBuild} but some other Run type.
     */
    private AbstractBuild<?,?> src;
    
    private AbstractBuild<?,?> dst;
    private final MessageDigest md5 = newMD5();
    private final Map<String,String> fingerprints = new HashMap<String, String>();

    @Override
    public void init(Run src, AbstractBuild<?, ?> dst, FilePath srcDir, FilePath baseTargetDir) throws IOException, InterruptedException {
        this.src = src instanceof AbstractBuild ? (AbstractBuild)src : null;
        this.dst = dst;
        fingerprints.clear();
    }

    private MessageDigest newMD5() {
        try {
            return MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(e);    // impossible
        }
    }

    @Override
    public int copyAll(FilePath srcDir, String filter, FilePath targetDir) throws IOException, InterruptedException {
        targetDir.mkdirs();  // Create target if needed
        FilePath[] list = srcDir.list(filter);
        for (FilePath file : list) {
            String tail = file.getRemote().substring(srcDir.getRemote().length());
            if (tail.startsWith("\\") || tail.startsWith("/"))
                tail = tail.substring(1);
            copyOne(file, new FilePath(targetDir, tail));
        }
        return list.length;
    }

    @Override
    public void copyOne(FilePath s, FilePath d) throws IOException, InterruptedException {
        try {
            md5.reset();
            DigestOutputStream out =new DigestOutputStream(d.write(),md5);
            try {
                s.copyTo(out);
            } finally {
                out.close();
            }
            d.chmod(s.mode());
            d.touch(s.lastModified());
            String digest = Util.toHexString(md5.digest());

            FingerprintMap map = Jenkins.getInstance().getFingerprintMap();

            Fingerprint f = map.getOrCreate(src, s.getName(), digest);
            if (src!=null) {
                f.add((AbstractBuild)src);
            }
            f.add(dst);
            fingerprints.put(s.getName(), digest);
        } catch (IOException e) {
            throw new IOException2("Failed to copy "+s+" to "+d,e);
        }
    }

    @Override
    public void end() {
        // add action
        for (AbstractBuild r : new AbstractBuild[]{src,dst}) {
            if (r == null)
                continue;

            FingerprintAction fa = r.getAction(FingerprintAction.class);
            if (fa != null) fa.add(fingerprints);
            else            r.getActions().add(new FingerprintAction(r, fingerprints));
        }
    }

    @Override
    public Copier clone() {
        return new FingerprintingCopyMethod();
    }
}
