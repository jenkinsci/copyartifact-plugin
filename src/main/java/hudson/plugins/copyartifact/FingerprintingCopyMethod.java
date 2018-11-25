package hudson.plugins.copyartifact;

import hudson.AbortException;
import hudson.Extension;
import hudson.FilePath;
import hudson.Util;
import hudson.model.Fingerprint;
import hudson.model.FingerprintMap;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.os.PosixException;
import hudson.tasks.Fingerprinter.FingerprintAction;
import hudson.util.IOException2;
import jenkins.model.Jenkins;

import java.io.IOException;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Performs fingerprinting during the copy.
 *
 * This minimizes the cost of the fingerprinting as the I/O bound nature of the copy operation
 * masks the cost of digest computation.
 *
 * @author Kohsuke Kawaguchi
 * @deprecated No longer used.
 */
@Deprecated
@Extension(ordinal=-100)
public class FingerprintingCopyMethod extends Copier {

    private static final Logger LOGGER = Logger.getLogger(FingerprintingCopyMethod.class.getName());
    private Run<?,?> src;
    private Run<?,?> dst;
    private final MessageDigest md5 = newMD5();
    private final Map<String,String> fingerprints = new HashMap<String, String>();

    @Override
    public void initialize(Run<?, ?> src, Run<?, ?> dst, FilePath srcDir, FilePath baseTargetDir) throws IOException, InterruptedException {
        this.src = src;
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
    public int copyAll(FilePath srcDir, String filter, String excludes, FilePath targetDir, boolean fingerprintArtifacts) throws IOException, InterruptedException {
        targetDir.mkdirs();  // Create target if needed
        FilePath[] list = srcDir.list(filter, excludes, false);
        for (FilePath file : list) {
            String tail = file.getRemote().substring(srcDir.getRemote().length());
            if (tail.startsWith("\\") || tail.startsWith("/"))
                tail = tail.substring(1);
            copyOne(file, new FilePath(targetDir, tail), fingerprintArtifacts);
        }
        return list.length;
    }

    @Override
    public void copyOne(FilePath s, FilePath d, boolean fingerprintArtifacts) throws IOException, InterruptedException {
        String link = s.readLink();
        if (link != null) {
            d.getParent().mkdirs();
            d.symlinkTo(link, /* TODO Copier signature does not offer a TaskListener; anyway this is rarely used */TaskListener.NULL);
            return;
        }
        try {
            md5.reset();
            try (DigestOutputStream out = new DigestOutputStream(d.write(), md5)) {
                s.copyTo(out);
            }
            try {
                d.chmod(s.mode());
            } catch (PosixException x) {
                LOGGER.log(Level.WARNING, "could not check mode of " + s, x);
            }
            // FilePath.setLastModifiedIfPossible private; copyToWithPermission OK but would have to calc digest separately:
            try {
                d.touch(s.lastModified());
            } catch (IOException x) {
                LOGGER.warning(x.getMessage());
            }
            String digest = Util.toHexString(md5.digest());

            if (fingerprintArtifacts) {
                Jenkins jenkins = Jenkins.getInstanceOrNull();
                if (jenkins == null) {
                    throw new AbortException("Jenkins instance no longer exists.");
                }
                FingerprintMap map = jenkins.getFingerprintMap();

                Fingerprint f = map.getOrCreate(src, s.getName(), digest);
                if (src!=null) {
                    f.addFor(src);
                }
                if (dst != null) {
                    f.addFor(dst);
                }
                fingerprints.put(s.getName(), digest);
            }
        } catch (IOException e) {
            throw new IOException2("Failed to copy "+s+" to "+d,e);
        }
    }

    @Override
    public void end() {
        // add action
        for (Run r : new Run[]{src,dst}) {
            if (r == null)
                continue;

            if (fingerprints.size() > 0) {
                FingerprintAction fa = r.getAction(FingerprintAction.class);
                if (fa != null) fa.add(fingerprints);
                else            r.getActions().add(new FingerprintAction(r, fingerprints));
            }
        }
    }

    @SuppressFBWarnings(
            value = "CN_IMPLEMENTS_CLONE_BUT_NOT_CLONEABLE",
            justification = "This is a method not of Cloneable but of Copier."
    )
    @Override
    public Copier clone() {
        return new FingerprintingCopyMethod();
    }
}
