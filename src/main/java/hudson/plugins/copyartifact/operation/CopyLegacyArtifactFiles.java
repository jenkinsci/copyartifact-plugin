package hudson.plugins.copyartifact.operation;

import hudson.FilePath;
import hudson.model.Descriptor;
import hudson.plugins.copyartifact.CopyArtifactOperation;
import hudson.plugins.copyartifact.CopyArtifactOperationDescriptor;

import java.io.IOException;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

import jenkins.model.StandardArtifactManager;

/**
 * copy artifact files stored in {@link StandardArtifactManager}.
 * Not exposed to users, and used from {@link CopyArtifactFiles} internally.
 * 
 * @since 2.0
 */
/*package*/ class CopyLegacyArtifactFiles extends AbstractFilePathCopyOperation {
    /**
     * ctor
     */
    //@DataBoundConstructor
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
     * @return
     * @see hudson.model.AbstractDescribableImpl#getDescriptor()
     */
    @Override
    public Descriptor<CopyArtifactOperation> getDescriptor() {
        return DESCRIPTOR;
    }
    
    /**
     * Descriptor for {@link CopyLegacyArtifactFiles}
     */
    //@Extension(ordinal=100)
    public static class DescriptorImpl extends CopyArtifactOperationDescriptor {
        /**
         * @return
         * @see hudson.model.Descriptor#getDisplayName()
         */
        @Override
        public String getDisplayName() {
            return Messages.CopyLegacyArtifactFiles_DisplayName();
        }
    }
    
    /**
     * the descriptor for {@link CopyLegacyArtifactFiles}.
     * Not listed as extensions.
     */
    public static DescriptorImpl DESCRIPTOR = new DescriptorImpl();
}
