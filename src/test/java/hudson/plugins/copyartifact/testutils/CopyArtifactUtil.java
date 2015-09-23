/*
 * The MIT License
 *
 * Copyright (c) 2013-2014, CloudBees, Inc.
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
package hudson.plugins.copyartifact.testutils;

import hudson.plugins.copyartifact.BuildSelector;
import hudson.plugins.copyartifact.CopyArtifact;

/**
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 */
public class CopyArtifactUtil {

    private CopyArtifactUtil() {
    }

    public static CopyArtifact createCopyArtifact(String projectName, String parameters, BuildSelector selector, String filter, String target,
            boolean flatten, boolean optional) {
        return createCopyArtifact(projectName, parameters, selector, filter, null, target, flatten, optional, false);
    }

    public static CopyArtifact createCopyArtifact(String projectName, String parameters, BuildSelector selector, String filter, String target,
            boolean flatten, boolean optional, boolean fingerprintArtifacts) {
        return createCopyArtifact(projectName, parameters, selector, filter, null, target, flatten, optional, fingerprintArtifacts);
    }

    public static CopyArtifact createCopyArtifact(String projectName, String parameters, BuildSelector selector, String filter, String excludes, String target,
                        boolean flatten, boolean optional, boolean fingerprintArtifacts) {
        return createCopyArtifact(projectName, parameters, selector, filter, excludes, target, flatten, optional, fingerprintArtifacts, null);
    }
    
    public static CopyArtifact createCopyArtifact(String projectName, String parameters, BuildSelector selector, String filter, String excludes, String target,
                        boolean flatten, boolean optional, boolean fingerprintArtifacts, String resultVariableSuffix) {
        CopyArtifact copyArtifact = new CopyArtifact(projectName);
        copyArtifact.setParameters(parameters);
        copyArtifact.setSelector(selector);
        copyArtifact.setFilter(filter);
        copyArtifact.setExcludes(excludes);
        copyArtifact.setTarget(target);
        copyArtifact.setFlatten(flatten);
        copyArtifact.setOptional(optional);
        copyArtifact.setFingerprintArtifacts(fingerprintArtifacts);
        copyArtifact.setResultVariableSuffix(resultVariableSuffix);
        copyArtifact.setVerbose(true);
        return copyArtifact;
    }
}
