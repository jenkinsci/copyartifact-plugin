package hudson.plugins.copyartifact;

import hudson.ExtensionPoint;
import hudson.FilePath;
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
 */
public abstract class Copier implements ExtensionPoint {

    private static Logger LOG = Logger.getLogger(Copier.class.getName());

    /**
     * Called before copy-artifact operation.
     * @param src
     *      The build record from which we are copying artifacts.
     * @param dst
     *      The built into which we are copying artifacts.
     * @param srcDir Source for upcoming file copy
     * @param baseTargetDir Base target dir for upcoming file copy (the copy-artifact
     *   build step may later specify a deeper target dir)
     */
    public abstract void init(Run src, Run<?,?> dst, FilePath srcDir, FilePath baseTargetDir) throws IOException, InterruptedException;

    /**
     * @deprecated 
     *      call/override {@link #copyAll(FilePath srcDir, String filter, String excludes, FilePath targetDir, boolean fingerprintArtifacts)} instead.
     */
    public int copyAll(FilePath srcDir, String filter, FilePath targetDir) throws IOException, InterruptedException {
        return copyAll(srcDir, filter, null, targetDir, true);
    }

    /**
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
     * @deprecated 
     *      call/override {@link #copyOne(FilePath source, FilePath target, boolean fingerprintArtifacts)} instead.
     */
    public void copyOne(FilePath source, FilePath target) throws IOException, InterruptedException {
        copyOne(source, target, true);
    }

    /**
     * Copy a single file.
     * @param source Source file
     * @param target Target file (includes filename; this is not the target directory).
     *   Directory for target should already exist (copy-artifact build step calls mkdirs).
     * @param fingerprintArtifacts boolean controlling if the copy should also fingerprint the artifacts
     * @see FilePath#copyTo(FilePath)
     */
    public abstract void copyOne(FilePath source, FilePath target, boolean fingerprintArtifacts) throws IOException, InterruptedException;

    /**
     * Ends what's started by the {@link #init(Run, Run, FilePath, FilePath)} method.
     */
    public void end() throws IOException, InternalError {}

    /**
     * Creates a clone.
     * 
     * This method is only called before the {@link #init(Run, Run, FilePath, FilePath)} method
     * to allow each init-end session to run against different objects, so you need not copy any state
     * that your {@link Copier} might maintain.
     * 
     * This is a cheap hack to implement a factory withot breaking backward compatibility.
     *
     * If you maintain no state, this method can return {@code this} without creating a copy.
     */
    @Override
    public abstract Copier clone();

}
