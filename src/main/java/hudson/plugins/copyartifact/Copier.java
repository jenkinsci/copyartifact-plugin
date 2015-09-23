package hudson.plugins.copyartifact;

import hudson.ExtensionPoint;
import hudson.FilePath;
import hudson.model.Run;

import java.io.IOException;

import jenkins.util.VirtualFile;

/**
 * Extension point for how files are copied.
 * CopyArtifact plugin provides a default implementation using methods
 * available in the Jenkins FilePath class.
 * 
 * <p>
 * A copier instance 
 * 
 * Note: Completely incompatible since 1.X.
 * 
 * @author Alan Harder
 * @author Kohsuke Kawaguchi
 * @see "JENKINS-7753"
 */
public abstract class Copier implements ExtensionPoint {
    /**
     * Called before copy-artifact operation.
     *
     * @param src
     *      The build record from which we are copying artifacts.
     * @param context
     *      parameters to copying operations.
     *      You can save execution state with {@link CopyArtifactCopyContext#addExtension(Object)}
     * @throws IOException if an error occurs while performing the operation.
     * @throws InterruptedException if any thread interrupts the current thread.
     * 
     * @since 2.0
     */
    public void init(Run<?, ?> src, CopyArtifactCopyContext context) throws IOException, InterruptedException {};

    /**
     * Copy a file.
     * 
     * @param source Source file
     * @param target Target file (includes filename; this is not the target directory).
     *   Directory for target should already exist (copy-artifact build step calls mkdirs).
     * @param context
     *      parameters to copying operations.
     *      You can save execution state with {@link CopyArtifactCopyContext#addExtension(Object)}
     * @throws IOException if an error occurs while performing the operation.
     * @throws InterruptedException if any thread interrupts the current thread.
     * 
     * @since 2.0
     */
    public abstract void copy(VirtualFile source, FilePath target, CopyArtifactCopyContext context) throws IOException, InterruptedException;

    /**
     * Ends what's started by the {@link #init(Run<?, ?>, CopyArtifactCopyContext)} method.
     * 
     * @param context
     *      parameters to copying operations.
     *      You can save execution state with {@link CopyArtifactCopyContext#addExtension(Object)}
     * @throws IOException if an error occurs while performing the operation.
     * @throws InterruptedException if any thread interrupts the current thread.
     * 
     * @since 2.0
     */
    public void end(CopyArtifactCopyContext context) throws IOException, InterruptedException {}

}
