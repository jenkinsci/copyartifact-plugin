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

import java.io.IOException;

import javax.annotation.Nonnull;

import hudson.model.AbstractDescribableImpl;
import hudson.model.Run;

/**
 * Operation performed after the source build is decided.
 * use {@link CopyArtifactOperationDescriptor} for the descriptor.
 * 
 * @since 2.0
 */
public abstract class CopyArtifactOperation extends AbstractDescribableImpl<CopyArtifactOperation> {
    /**
     * The result of copying artifacts.
     */
    public static enum Result {
        /**
         * No files to copy.
         * Fails when not optional
         */
        NothingToDo     (0),
        /**
         * Copied one or more files.
         */
        Succeess        (1),
        ;
        
        private int numeric;
        private Result(int numeric) {
            this.numeric = numeric;
        }
        
        @Nonnull
        private static Result byNumber(int numeric) {
            for (Result v : Result.values()) {
                if (v.numeric == numeric) {
                    return v;
                }
            }
            // never happens!
            return Succeess;
        }
        
        @Nonnull
        public Result merge(@Nonnull Result valueToMerge) {
            return Result.byNumber(Math.max(numeric, valueToMerge.numeric));
        }
    }

    /**
     * Performs the operation for the target build.
     * 
     * @param src       the target build
     * @param context   context of the operation
     * @return  the result of the process
     * @throws IOException
     * @throws InterruptedException
     */
    @Nonnull
    public abstract Result perform(@Nonnull Run<?, ?> src, @Nonnull CopyArtifactOperationContext context) throws IOException, InterruptedException;
}
