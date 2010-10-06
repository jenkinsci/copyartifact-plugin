/*
 * The MIT License
 *
 * Copyright (c) 2004-2010, Sun Microsystems, Inc., Alan Harder
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

import hudson.DescriptorExtensionList;
import hudson.Extension;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.model.ParameterValue;
import hudson.model.SimpleParameterDefinition;
import hudson.model.StringParameterValue;
import hudson.util.XStream2;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

/**
 * Build parameter to select a promotion level from the list of configured levels.
 * @author Alan Harder
 */
public class BuildSelectorParameter extends SimpleParameterDefinition {
    private BuildSelector defaultSelector;

    @DataBoundConstructor
    public BuildSelectorParameter(String name, BuildSelector defaultSelector, String description) {
        super(name, description);
        this.defaultSelector = defaultSelector;
    }

    public BuildSelector getDefaultSelector() {
        return defaultSelector;
    }

    @Override
    public ParameterValue getDefaultParameterValue() {
        return toStringValue(defaultSelector);
    }

    @Override
    public ParameterValue createValue(String value) {
        getSelectorFromXml(value); // validate the input
        return new StringParameterValue(getName(), value, getDescription());
    }

    @Override
    public ParameterValue createValue(StaplerRequest req, JSONObject jo) {
        return toStringValue(req.bindJSON(BuildSelector.class, jo));
    }

    private StringParameterValue toStringValue(BuildSelector selector) {
        return new StringParameterValue(
                getName(), XSTREAM.toXML(selector).replaceAll("[\n\r]+", ""), getDescription());
    }

    /**
     * Convert xml fragment into a BuildSelector object.
     * @throws XStreamException or ClassCastException if input is invalid
     */
    public static BuildSelector getSelectorFromXml(String xml) {
        return (BuildSelector)XSTREAM.fromXML(xml);
    }

    @Extension
    public static class DescriptorImpl extends ParameterDescriptor {
        @Override
        public String getDisplayName() {
            return Messages.BuildSelectorParameter_DisplayName();
        }

        public DescriptorExtensionList<BuildSelector,Descriptor<BuildSelector>> getBuildSelectors() {
            return Hudson.getInstance().getDescriptorList(BuildSelector.class);
        }
    }

    private static final XStream2 XSTREAM = new XStream2();

    static void initAliases() {
        // Alias all BuildSelectors to their simple names
        for (Descriptor<BuildSelector> d : Hudson.getInstance().getDescriptorByType(DescriptorImpl.class).getBuildSelectors())
            XSTREAM.alias(d.clazz.getSimpleName(), d.clazz);
    }
}
