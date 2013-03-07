package hudson.plugins.copyartifact;

import hudson.ExtensionPoint;
import hudson.FilePath;
import hudson.model.AbstractBuild;
import hudson.model.Run;

import java.io.IOException;

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
    public abstract void init(Run src, AbstractBuild<?,?> dst, FilePath srcDir, FilePath baseTargetDir) throws IOException, InterruptedException;
    
    /**
     * Copy files matching the given file mask to the specified target.
     * @param srcDir Source directory
     * @param filter Ant GLOB pattern
     * @param targetDir Target directory
     * @return Number of files that were copied
     * @see FilePath#copyRecursiveTo(String,FilePath)
     */
    public abstract int copyAll(FilePath srcDir, String filter, FilePath targetDir) throws IOException, InterruptedException;

    /**
     * Copy a single file.
     * @param source Source file
     * @param target Target file (includes filename; this is not the target directory).
     *   Directory for target should already exist (copy-artifact build step calls mkdirs).
     * @see FilePath#copyTo(FilePath)
     */
    public abstract void copyOne(FilePath source, FilePath target) throws IOException, InterruptedException;

    /**
     * Ends what's started by the {@link #init(Run, AbstractBuild, FilePath, FilePath)} method.
     */
    public void end() throws IOException, InternalError {}

    /**
     * Creates a clone.
     * 
     * This method is only called before the {@link #init(Run, AbstractBuild, FilePath, FilePath)} method
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
