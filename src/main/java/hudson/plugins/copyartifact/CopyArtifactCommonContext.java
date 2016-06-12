/*
 * The MIT License
 * 
 * Copyright (c) 2015 IKEDA Yasuyuki
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package hudson.plugins.copyartifact;

import java.io.PrintStream;
import java.lang.reflect.Method;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

import hudson.EnvVars;
import hudson.model.Run;
import hudson.model.TaskListener;
import jenkins.model.Jenkins;

/**
 * Context for an execution of copyartifact.
 * This allows us to adding new fields without affecting
 * existing plugins.
 * 
 * You can manage plugin specific informations using
 * {@link #addExtension(Object)} and {@link #getExtension(Class)}.
 * 
 * @since 2.0
 */
public class CopyArtifactCommonContext implements Cloneable {
    private static final Logger LOGGER = Logger.getLogger(CopyArtifactCommonContext.class.getName());
    private Jenkins jenkins;
    private Run<?,?> copierBuild;
    private TaskListener listener;
    private EnvVars envVars;
    private boolean verbose;
    private List<Object> extensionList;

    /**
     * @param jenkins Jenkins instance.
     */
    public void setJenkins(@Nonnull Jenkins jenkins) {
        this.jenkins = jenkins;
    }

    /**
     * Returns {@link Jenkins} instance.
     * Never be <code>null</code>.
     * 
     * @return Jenkins instance.
     */
    @Nonnull
    public Jenkins getJenkins() {
        return jenkins;
    }

    /**
     * @param copierBuild the build running copyartifact
     */
    public void setCopierBuild(@Nonnull Run<?, ?> copierBuild) {
        this.copierBuild = copierBuild;
    }

    /**
     * @return the build running copyartifact
     */
    @Nonnull
    public Run<?, ?> getCopierBuild() {
        return copierBuild;
    }

    /**
     * @param listener listener for the build running copyartifact.
     */
    public void setListener(TaskListener listener) {
        this.listener = listener;
    }

    /**
     * @return the listener for the build running copyartifact.
     */
    public TaskListener getListener() {
        return listener;
    }

    /**
     * Shortcut for <code>getListener().getLogger()</code>
     * 
     * @return stream to output logs
     */
    @Nonnull
    protected PrintStream getConsole() {
        return listener.getLogger();
    }

    /**
     * @param envVars variables for the current build
     */
    public void setEnvVars(@Nonnull EnvVars envVars) {
        this.envVars = envVars;
    }

    /**
     * @return variables for the current build.
     */
    @Nonnull
    public EnvVars getEnvVars() {
        return envVars;
    }

    /**
     * @param verbose whether output verbose (for diagnostics) logs.
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * @return whether output verbose (for diagnostics) logs.
     */
    public boolean isVerbose() {
        return verbose;
    }

    /**
     * @return additional informations by plugins.
     */
    @Nonnull
    public List<Object> getExtensionList() {
        return extensionList;
    }

    /**
     * Add an object to hold plugin specific information.
     * 
     * @param extension extension object
     */
    public void addExtension(@Nonnull Object extension) {
        getExtensionList().add(extension);
    }

    /**
     * @param extension extension object to remove
     * @return true if the extension is contained.
     */
    public boolean removeExtension(@Nonnull Object extension) {
        return getExtensionList().remove(extension);
    }

    /**
     * Removes extensions with the same class type before adding.
     * 
     * @param extension extension object to replace with
     * @return true if an extension object of the same class class is contained.
     */
    public boolean replaceExtension(@Nonnull Object extension) {
        boolean removed = false;
        while(true) {
            Object e = getExtension(extension.getClass());
            if (e == null) {
                break;
            }
            removeExtension(e);
            removed = true;
        }
        addExtension(extension);
        return removed;
    }

    /**
     * Extract an extension object of the specified class.
     * 
     * @param <T> specified with <code>klass</code>
     * @param klass class of the extension to extract
     * @return extension of the class
     */
    @CheckForNull
    public <T> T getExtension(@Nonnull Class<T> klass) {
        for (Object e : getExtensionList())
            if (klass.isInstance(e))
                return klass.cast(e);
        return null;
    }

    private void log(@Nonnull String message) {
        getConsole().println(message);
    }
    
    private void log(@Nonnull String message, @Nonnull Throwable t) {
        getConsole().println(message);
        t.printStackTrace(getConsole());
    }
    
    /**
     * Outputs a log message
     * 
     * @param message message to log
     */
    public void logInfo(@Nonnull String message) {
        log(message);
    }
    
    /**
     * Outputs a log message in {@link MessageFormat} formats.
     * 
     * @param pattern pattern for {@link MessageFormat}
     * @param arguments values to format
     */
    public void logInfo(@Nonnull String pattern, Object... arguments) {
        log(MessageFormat.format(pattern, arguments));
    }
    
    /**
     * Outputs a log message if {@link #isVerbose()} is <code>true</code>.
     * 
     * @param message message to log
     */
    public void logDebug(@Nonnull String message) {
        if (isVerbose()) {
            log(message);
        }
    }

    /**
     * Outputs a log message in {@link MessageFormat} formats
     * if {@link #isVerbose()} is <code>true</code>.
     * 
     * @param pattern pattern for {@link MessageFormat}
     * @param arguments values to format
     */
    public void logDebug(@Nonnull String pattern, Object... arguments) {
        if (isVerbose()) {
            log(MessageFormat.format(pattern, arguments));
        }
    }

    /**
     * Outputs a log message with an exception
     * 
     * @param string message to log.
     * @param t exception to log.
     */
    public void logException(@Nonnull String string, @Nonnull Throwable t) {
        log(string, t);
    }

    /**
     * ctor
     */
    public CopyArtifactCommonContext() {
        extensionList = new ArrayList<Object>();
    }

    /**
     * Creates a new instance copying src data.
     * 
     * @param src {@link CopyArtifactCommonContext} to copy from.
     */
    protected CopyArtifactCommonContext(@Nonnull CopyArtifactCommonContext src) {
        this.jenkins = src.jenkins;
        this.copierBuild = src.copierBuild;
        this.listener = src.listener;
        this.envVars = new EnvVars(src.envVars);
        this.verbose = src.verbose;
        copyExtensionListFrom(src);
    }

    private void copyExtensionListFrom(CopyArtifactCommonContext src) {
        this.extensionList = new ArrayList<Object>();
        for (Object ext : src.extensionList) {
            if (ext instanceof Cloneable) {
                try {
                    Method m = ext.getClass().getMethod("clone");
                    this.extensionList.add(m.invoke(ext));
                } catch(NoSuchMethodException e) {
                    LOGGER.log(
                            Level.WARNING,
                            "Could not clone {0} as clone() is not public.",
                            ext.getClass()
                    );
                    this.extensionList.add(ext);
                } catch (Exception e) {
                    LOGGER.log(
                            Level.WARNING,
                            MessageFormat.format("Could not clone {0}.", ext.getClass()),
                            e
                    );
                    this.extensionList.add(ext);
                }
            } else {
                this.extensionList.add(ext);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected CopyArtifactCommonContext clone() {
        CopyArtifactCommonContext c = null;
        try {
            c = (CopyArtifactCommonContext)super.clone();
        } catch(CloneNotSupportedException e) {
            throw new IllegalStateException(e);
        }
        c.envVars = new EnvVars(this.envVars);
        c.copyExtensionListFrom(this);
        
        return c;
    }
}
