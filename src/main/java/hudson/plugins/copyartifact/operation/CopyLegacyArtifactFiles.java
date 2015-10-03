package hudson.plugins.copyartifact.operation;

import hudson.FilePath;
import hudson.plugins.copyartifact.Messages;

import java.io.IOException;

public class CopyLegacyArtifactFiles extends AbstractFilePathCopyOperation {
    @SuppressWarnings("deprecation")
    @Override
    protected FilePath getSrcDir(CopyArtifactCopyContext context) throws IOException, InterruptedException {
        FilePath srcDir = new FilePath(context.getSrc().getArtifactsDir());
        if (srcDir.exists()) {
            return srcDir;
        } else {
            context.logInfo(Messages.CopyArtifact_MissingSrcArtifacts(srcDir));
            return null;
        }
    }
}
