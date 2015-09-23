package hudson.plugins.copyartifact;

import hudson.Extension;
import hudson.FilePath;
import hudson.Util;
import hudson.model.Fingerprint;
import hudson.model.FingerprintMap;
import hudson.model.Run;
import hudson.tasks.Fingerprinter.FingerprintAction;
import jenkins.util.VirtualFile;

import java.io.IOException;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nonnull;

import org.apache.commons.io.IOUtils;

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
    private static class ContextExtension {
        @Nonnull
        private final Run<?,?> src;
        
        @Nonnull
        private final Run<?,?> dst;
        
        @Nonnull
        private final MessageDigest md5;
        
        @Nonnull
        private final Map<String,String> fingerprints;
        
        private MessageDigest newMD5() {
            try {
                return MessageDigest.getInstance("MD5");
            } catch (NoSuchAlgorithmException e) {
                throw new AssertionError(e);    // impossible
            }
        }
        
        private ContextExtension(@Nonnull Run<?,?> src, @Nonnull Run<?,?> dst) {
            this.src = src;
            this.dst = dst;
            this.md5 = newMD5();
            this.fingerprints = new HashMap<String, String>();
        }
    }

    @Override
    public void init(@Nonnull Run<?, ?> src, @Nonnull CopyArtifactCopyContext context) {
        context.replaceExtension(new ContextExtension(src, context.getCopierBuild()));
    }

    @Override
    public void copy(VirtualFile s, FilePath d, CopyArtifactCopyContext context) throws IOException, InterruptedException {
        ContextExtension ext = context.getExtension(ContextExtension.class);
        if (ext == null) {
            throw new IllegalStateException("FingerprintingCopyMethod.ContextExtension is not available.");
        }
        
        // Unfortunately, VirtualFile doesn't support symbolic links.
        // String link = s.readLink();
        // if (link != null) {
        //     d.getParent().mkdirs();
        //     d.symlinkTo(link, /* TODO Copier signature does not offer a TaskListener; anyway this is rarely used */TaskListener.NULL);
        //     return;
        // }
        
        try {
            ext.md5.reset();
            DigestOutputStream out =new DigestOutputStream(d.write(), ext.md5);
            try {
                IOUtils.copy(s.open(), out);
            } finally {
                out.close();
            }
            /*
            // VirtualFile doesn't provide modes.
            try {
                d.chmod(s.mode());
            } catch (PosixException x) {
                LOGGER.log(Level.WARNING, "could not check mode of " + s, x);
            }
            */
            // FilePath.setLastModifiedIfPossible private; copyToWithPermission OK but would have to calc digest separately:
            try {
                d.touch(s.lastModified());
            } catch (IOException x) {
                context.logException("Failed to set last modification time", x);
            }
            String digest = Util.toHexString(ext.md5.digest());

            if (context.isFingerprintArtifacts()) {
                FingerprintMap map = context.getJenkins().getFingerprintMap();

                Fingerprint f = map.getOrCreate(ext.src, s.getName(), digest);
                f.addFor(ext.src);
                f.addFor(ext.dst);
                ext.fingerprints.put(s.getName(), digest);
            }
        } catch (IOException e) {
            throw new IOException("Failed to copy "+s+" to "+d,e);
        }
    }

    @Override
    public void end(CopyArtifactCopyContext context) {
        ContextExtension ext = context.getExtension(ContextExtension.class);
        if (ext == null) {
            throw new IllegalStateException("FingerprintingCopyMethod.ContextExtension is not available.");
        }
        
        // add action
        for (Run<?,?> r : new Run[]{ext.src, ext.dst}) {
            if (ext.fingerprints.size() > 0) {
                FingerprintAction fa = r.getAction(FingerprintAction.class);
                if (fa != null) {
                    fa.add(ext.fingerprints);
                } else {
                    r.addAction(new FingerprintAction(r, ext.fingerprints));
                }
            }
        }
    }
}
