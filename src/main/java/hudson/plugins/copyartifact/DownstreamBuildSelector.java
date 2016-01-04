/*
 * The MIT License
 * 
 * Copyright (c) 2014 IKEDA Yasuyuki
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

import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;

import hudson.plugins.copyartifact.filter.DownstreamBuildFilter;
import hudson.plugins.copyartifact.selector.Version1BuildSelector;

/**
 * Select a build which is a downstream of a specified build.
 * @deprecated use {@link DownstreamBuildFilter}
 */
@Deprecated
public class DownstreamBuildSelector extends Version1BuildSelector {
    private final String upstreamProjectName;
    private final String upstreamBuildNumber;
    
    /**
     * @param upstreamProjectName
     * @param upstreamBuildNumber
     */
    @DataBoundConstructor
    public DownstreamBuildSelector(String upstreamProjectName, String upstreamBuildNumber) {
        this.upstreamProjectName = StringUtils.trim(upstreamProjectName);
        this.upstreamBuildNumber = StringUtils.trim(upstreamBuildNumber);
    }
    
    /**
     * @return upstream project name. May include variable expression.
     */
    public String getUpstreamProjectName() {
        return upstreamProjectName;
    }
    
    /**
     * @return upstream build number. May include variable expression.
     */
    public String getUpstreamBuildNumber() {
        return upstreamBuildNumber;
    }
    
    /**
     * @return
     * @see hudson.plugins.copyartifact.selector.Version1BuildSelector#migrateToVersion2()
     */
    @Override
    public MigratedConfiguration migrateToVersion2() {
        return new MigratedConfiguration(
                new StatusBuildSelector(StatusBuildSelector.BuildStatus.Completed),
                new DownstreamBuildFilter(getUpstreamProjectName(), getUpstreamBuildNumber())
        );
    }
}
