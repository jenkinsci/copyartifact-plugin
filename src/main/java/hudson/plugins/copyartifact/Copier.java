package hudson.plugins.copyartifact;

import hudson.ExtensionPoint;
import hudson.FilePath;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.Run;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Extension point for how files are copied.
 * CopyArtifact plugin provides a default implementation using methods
 * available in the Jenkins FilePath class.
 * 
 * <p>
 * A copier instance 
 * 
 * @author Alan Harder
 * @author Kohsuke Kawaguchi
 * @see "JENKINS-7753"
 * @deprecated No longer used.
 */
@Deprecated
public abstract class Copier implements ExtensionPoint {

    private static final Logger LOG = Logger.getLogger(Copier.class.getName());

    /**
     * Called before copy-artifact operation.
     *
     * @param src
     *      The build record from which we are copying artifacts.
     * @param dst
     *      The built into which we are copying artifacts.
     * @param srcDir Source for upcoming file copy
     * @param baseTargetDir Base target dir for upcoming file copy (the copy-artifact
     *   build step may later specify a deeper target dir)
     *
     * @throws IOException if an error occurs while performing the operation.
     * @throws InterruptedException if any thread interrupts the current thread.
     * @since TODO ?
     */
    public void initialize(Run<?, ?> src, Run<?, ?> dst, FilePath srcDir, FilePath baseTargetDir) throws IOException, InterruptedException {
        if (dst instanceof AbstractBuild && Util.isOverridden(Copier.class, getClass(), "init", Run.class, AbstractBuild.class, FilePath.class, FilePath.class)) {
            init(src, (AbstractBuild<?,?>) dst, srcDir, baseTargetDir);
        } else {
            throw new AbstractMethodError(String.format("Invalid call to Copier.initialize(Run src, Run dst, FilePath, FilePath), passing an AbstractBuild " +
                    "instance for the 'dst' arg when %s does not implement the deprecated version of 'init' that takes an AbstractBuild. Please supply a " +
                    "Run instance for the 'dst' arg.", getClass().getName()));
        }
    }

    /**
     * Called before copy-artifact operation.
     *
     * @param src
     *      The build record from which we are copying artifacts.
     * @param dst
     *      The built into which we are copying artifacts.
     * @param srcDir Source for upcoming file copy
     * @param baseTargetDir Base target dir for upcoming file copy (the copy-artifact
     *   build step may later specify a deeper target dir)
     *
     * @throws IOException if an error occurs while performing the operation.
     * @throws InterruptedException if any thread interrupts the current thread.
     * @deprecated Please use {@link #initialize(hudson.model.Run, hudson.model.Run, hudson.FilePath, hudson.FilePath)}
     */
    @Deprecated
    public void init(Run src, AbstractBuild<?,?> dst, FilePath srcDir, FilePath baseTargetDir) throws IOException, InterruptedException {
        if (Util.isOverridden(Copier.class, getClass(), "initialize", Run.class, Run.class, FilePath.class, FilePath.class)) {
            initialize((Run<?, ?>)src, dst, srcDir, baseTargetDir);
        } else {
            // Is near impossible for this to happen. Copier impl would need to not implement the newer version of the initialize method, while at
            // the same time be changed to call super with a Run instance for dst, which would be bizarre because that could only have been done
            // after the initialize method was changed to not be abstract.
            throw new AbstractMethodError(String.format("Invalid call to Copier.init(Run src, AbstractBuild dst, FilePath, FilePath). " +
                    "%s implements the newer version of 'initialize' that takes a Run instance for the 'dst' arg. Please call that implementation.", getClass().getName()));
        }
    }

    /**
     * Copy files matching the given file mask to the specified target.
     *
     * @param srcDir Source directory
     * @param filter Ant GLOB pattern
     * @param targetDir Target directory
     * @return Number of files that were copied
     * @throws IOException if an error occurs while performing the operation.
     * @throws InterruptedException if any thread interrupts the current thread.
     * @deprecated
     *      call/override {@link #copyAll(FilePath srcDir, String filter, String excludes, FilePath targetDir, boolean fingerprintArtifacts)} instead.
     */
    @Deprecated
    public int copyAll(FilePath srcDir, String filter, FilePath targetDir) throws IOException, InterruptedException {
        return copyAll(srcDir, filter, null, targetDir, true);
    }

    /**
     * Copy files matching the given file mask to the specified target.
     *
     * @param srcDir Source directory
     * @param filter Ant GLOB pattern
     * @param targetDir Target directory
     * @param fingerprintArtifacts boolean controlling if the copy should also fingerprint the artifacts
     * @return Number of files that were copied
     * @throws IOException if an error occurs while performing the operation.
     * @throws InterruptedException if any thread interrupts the current thread.
     * @deprecated
     *      call/override {@link #copyAll(FilePath, String, String, FilePath, boolean)} instead.
     */
    @Deprecated
    public int copyAll(FilePath srcDir, String filter, FilePath targetDir, boolean fingerprintArtifacts) throws IOException, InterruptedException {
        return copyAll(srcDir, filter, null, targetDir, fingerprintArtifacts);
    }
    
    /**
     * Copy files matching the given file mask to the specified target.
     * 
     * You must override this when deriving {@link Copier}.
     * 
     * @param srcDir Source directory
     * @param filter Ant GLOB pattern
     * @param excludes Ant GLOB pattern. Can be null.
     * @param targetDir Target directory
     * @param fingerprintArtifacts boolean controlling if the copy should also fingerprint the artifacts
     * @throws IOException if an error occurs while performing the operation.
     * @throws InterruptedException if any thread interrupts the current thread.
     * @return Number of files that were copied
     * @see FilePath#copyRecursiveTo(String,FilePath)
     */
    public int copyAll(FilePath srcDir, String filter, String excludes, FilePath targetDir, boolean fingerprintArtifacts) throws IOException, InterruptedException {
        try {
            Class<?> classOfCopyAll = getClass().getMethod("copyAll", FilePath.class, String.class, FilePath.class, boolean.class).getDeclaringClass();
            if (!Copier.class.equals(classOfCopyAll)) {
                // For backward compatibility.
                // avoid cyclic invocation.
                return copyAll(srcDir, filter, targetDir, fingerprintArtifacts);
            }
        } catch(SecurityException e) {
            LOG.log(Level.WARNING, "Unexpected exception in copyartifact-plugin", e);
        } catch(NoSuchMethodException e) {
            LOG.log(Level.WARNING, "Unexpected exception in copyartifact-plugin", e);
        }
        throw new AbstractMethodError("You need override Copier#copyAll(FilePath, String, String, FilePath, boolean)");
    }
    
    /**
     * Copy a single file.
     * @param source Source file
     * @param target Target file (includes filename; this is not the target directory).
     *   Directory for target should already exist (copy-artifact build step calls mkdirs).
     * @throws IOException if an error occurs while performing the operation.
     * @throws InterruptedException if any thread interrupts the current thread.
     * @deprecated
     *      call/override {@link #copyOne(FilePath source, FilePath target, boolean fingerprintArtifacts)} instead.
     */
    @Deprecated
    public void copyOne(FilePath source, FilePath target) throws IOException, InterruptedException {
        copyOne(source, target, true);
    }

    /**
     * Copy a single file.
     * @param source Source file
     * @param target Target file (includes filename; this is not the target directory).
     *   Directory for target should already exist (copy-artifact build step calls mkdirs).
     * @param fingerprintArtifacts boolean controlling if the copy should also fingerprint the artifacts
     * @throws IOException if an error occurs while performing the operation.
     * @throws InterruptedException if any thread interrupts the current thread.
     * @see FilePath#copyTo(FilePath)
     */
    public abstract void copyOne(FilePath source, FilePath target, boolean fingerprintArtifacts) throws IOException, InterruptedException;

    /**
     * Ends what's started by the {@link #init(Run, AbstractBuild, FilePath, FilePath)} method.
     * @throws IOException if an error occurs while performing the operation.
     */
    public void end() throws IOException, InternalError {}

    /**
     * Creates a clone.
     * 
     * This method is only called before the {@link #init(Run, AbstractBuild, FilePath, FilePath)} method
     * to allow each initialize-end session to run against different objects, so you need not copy any state
     * that your {@link Copier} might maintain.
     * 
     * This is a cheap hack to implement a factory without breaking backward compatibility.
     *
     * If you maintain no state, this method can return {@code this} without creating a copy.
     */
    @Override
    public abstract Copier clone();

}
