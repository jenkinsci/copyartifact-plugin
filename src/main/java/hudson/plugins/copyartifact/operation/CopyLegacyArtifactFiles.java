package hudson.plugins.copyartifact.operation;

import hudson.Extension;
import hudson.FilePath;
import hudson.plugins.copyartifact.CopyArtifactOperationDescriptor;

import java.io.IOException;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

import jenkins.model.StandardArtifactManager;

import org.kohsuke.stapler.DataBoundConstructor;

/**
 * copy artifact files stored in {@link StandardArtifactManager}.
 * 
 * @since 2.0
 */
public class CopyLegacyArtifactFiles extends AbstractFilePathCopyOperation {
    /**
     * ctor
     */
    @DataBoundConstructor
    public CopyLegacyArtifactFiles() {
    }
    
    /**
     * @param context
     * @return
     * @throws IOException
     * @throws InterruptedException
     * @see hudson.plugins.copyartifact.operation.AbstractFilePathCopyOperation#getSrcDir(hudson.plugins.copyartifact.operation.CopyArtifactCopyContext)
     */
    @SuppressWarnings("deprecation")
    @Override
    @CheckForNull
    protected FilePath getSrcDir(@Nonnull CopyArtifactCopyContext context) throws IOException, InterruptedException {
        FilePath srcDir = new FilePath(context.getSrc().getArtifactsDir());
        if (srcDir.exists()) {
            return srcDir;
        } else {
            context.logInfo(hudson.plugins.copyartifact.Messages.CopyArtifact_MissingSrcArtifacts(srcDir));
            return null;
        }
    }
    
    /**
     * Descriptor for {@link CopyLegacyArtifactFiles}
     */
    @Extension(ordinal=100)    // topmost
    public static class DescriptorImpl extends CopyArtifactOperationDescriptor {
        @Override
        public String getDisplayName() {
            return Messages.CopyLegacyArtifactFiles_DisplayName();
        }
    }
}
