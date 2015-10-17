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

package hudson.plugins.copyartifact.selector;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

import hudson.AbortException;
import hudson.model.Job;
import hudson.model.Run;
import hudson.plugins.copyartifact.BuildFilter;
import hudson.plugins.copyartifact.BuildSelector;
import hudson.plugins.copyartifact.CopyArtifact;
import hudson.plugins.copyartifact.CopyArtifactOperation;
import hudson.plugins.copyartifact.CopyArtifactPickContext;
import hudson.plugins.copyartifact.operation.AbstractCopyOperation;

/**
 * A base class for {@link BuildSelector}s designed in copyartifact 1.0,
 * and should be replaced with {@link BuildFilter}s.
 * You have to implement {@link #migrateToVersion2()} to
 * upgrade the configuration of {@link CopyArtifact} automatically.
 * 
 * @since 2.0
 * @deprecated Use appropriate {@link BuildFilter} instead.
 */
@Deprecated
public abstract class Version1BuildSelector extends BuildSelector {
    /**
     * Holds configuration after migrated.
     */
    public static class MigratedConfiguration {
        /**
         * Build selector to be used instead.
         */
        @Nonnull
        public final BuildSelector buildSelector;
        
        /**
         * Build filter to be used additionally.
         * Can be <code>null</code>.
         */
        @CheckForNull
        public BuildFilter buildFilter;
        
        /**
         * Copy operation to be used.
         * Can be <code>null</code>.
         * Fields for {@link AbstractCopyOperation} will be populated
         * from the configuration of {@link CopyArtifact}.
         */
        @CheckForNull
        public CopyArtifactOperation copyArtifactOperation;
        
        public MigratedConfiguration(@Nonnull BuildSelector buildSelector) {
            this(buildSelector, null);
        }
        
        public MigratedConfiguration(@Nonnull BuildSelector buildSelector, @CheckForNull BuildFilter buildFilter) {
            this.buildSelector = buildSelector;
            this.buildFilter = buildFilter;
        }
    }
    
    /**
     * @return configuration after migration.
     */
    public abstract MigratedConfiguration migrateToVersion2();
    
    @Override
    public Run<?, ?> pickBuildToCopyFrom(Job<?, ?> job, CopyArtifactPickContext context)
            throws AbortException
    {
        throw new AbortException(String.format(
                "%s is designed for copyartifact-1.0"
                + " and the configuration should be migrated to copyartifact-2.0."
                + "It can be automatically performed by restarting Jenkins."
                , getDisplayName()
        ));
    }
}
