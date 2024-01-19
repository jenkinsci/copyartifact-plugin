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

import com.thoughtworks.xstream.XStreamException;
import hudson.DescriptorExtensionList;
import hudson.Extension;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.model.Descriptor;
import hudson.model.ParameterValue;
import hudson.model.SimpleParameterDefinition;
import hudson.model.StringParameterValue;
import hudson.util.XStream2;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

/**
 * @author Alan Harder
 */
public class BuildSelectorParameter extends SimpleParameterDefinition {
    private static final long serialVersionUID = 1;
    // Serialize defaultSelector into XML as ParameterDefinition must be a Serializable
    // but BuildSelector is not. (since version 1.46)
    private transient BuildSelector defaultSelector;
    private String defaultSelectorXml;

    private static final Logger LOGGER = Logger.getLogger(BuildSelectorParameter.class.getName());

    @DataBoundConstructor
    public BuildSelectorParameter(String name, BuildSelector defaultSelector, String description) {
        super(name, description);
        setDefaultSelector(defaultSelector);
    }

    public BuildSelector getDefaultSelector() {
        return defaultSelector;
    }

    private void setDefaultSelector(BuildSelector selector) {
        defaultSelectorXml = toXML(selector);
        defaultSelector = selector;
    }

    private Object readResolve() {
        if (defaultSelector == null) {
            // Restore from the serialized value.
            if (defaultSelectorXml != null) {
                defaultSelector = getSelectorFromXml(defaultSelectorXml);
            } else {
                // Both defaultSelector and defaultSelectorXml is null. Strange!
                LOGGER.warning("Broken BuildSelectorParameter: defaultSelector is not configured.");
            }
        } else {
            // upgrade from older than 1.46.
            defaultSelectorXml = toXML(defaultSelector);
        }
        return this;
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
        return new StringParameterValue(getName(), toXML(selector), getDescription());
    }

    private static String toXML(BuildSelector selector) {
        return XSTREAM.toXML(selector).replaceAll("[\n\r]+", "");
    }

    /**
     * Convert xml fragment into a BuildSelector object.
     * @param xml XML fragment to parse.
     * @return the BuildSelector represented by the input XML.
     * @throws XStreamException if the object cannot be deserialized
     * @throws ClassCastException if input is invalid
     */
    public static BuildSelector getSelectorFromXml(String xml) {
        return (BuildSelector) XSTREAM.fromXML(xml);
    }

    @Extension
    @Symbol("buildSelector")
    public static class DescriptorImpl extends ParameterDescriptor {
        @Override
        public String getDisplayName() {
            return Messages.BuildSelectorParameter_DisplayName();
        }

        public DescriptorExtensionList<BuildSelector, Descriptor<BuildSelector>> getBuildSelectors() {
            Jenkins jenkins = Jenkins.getInstanceOrNull();
            if (jenkins == null) {
                return DescriptorExtensionList.createDescriptorList((Jenkins) null, BuildSelector.class);
            }
            return jenkins.getDescriptorList(BuildSelector.class);
        }

        /**
         * @return {@link BuildSelector}s available for BuildSelectorParameter.
         */
        public List<Descriptor<BuildSelector>> getAvailableBuildSelectorList() {
            Jenkins jenkins = Jenkins.getInstanceOrNull();
            if (jenkins == null) {
                return Collections.emptyList();
            }
            return jenkins.getDescriptorList(BuildSelector.class).stream()
                    .filter(input -> !"ParameterizedBuildSelector".equals(input.clazz.getSimpleName()))
                    .collect(Collectors.toList());
        }

        @Override
        public String getHelpFile(String fieldName) {
            if ("defaultSelector".equals(fieldName) || "parameter".equals(fieldName)) {
                // Display the help file of `Copyartifact#getSelector` ("which build" field)
                // for `defaultSelector` ("Default Selector" field) in project configuration pages
                // and the value of build parameter ("Build selector for Copy Artifact" field)
                // in "This build requires parameters" pages.
                Jenkins jenkins = Jenkins.getInstanceOrNull();
                Descriptor<?> d = jenkins == null ? null : jenkins.getDescriptor(CopyArtifact.class);
                if (d != null) {
                    return d.getHelpFile("selector");
                }
            }
            return super.getHelpFile(fieldName);
        }
    }

    private static final XStream2 XSTREAM = new XStream2();

    @Initializer(after = InitMilestone.PLUGINS_STARTED)
    public static void initAliases() {
        Jenkins jenkins = Jenkins.getInstanceOrNull();
        if (jenkins == null) {
            LOGGER.severe("Called for initialization but Jenkins instance no longer available.");
            return;
        }
        DescriptorImpl descriptor = jenkins.getDescriptorByType(DescriptorImpl.class);
        List<Descriptor<BuildSelector>> descriptorList = descriptor.getBuildSelectors();
        // Alias all BuildSelectors to their simple names
        for (Descriptor<BuildSelector> d : descriptorList) {
            XSTREAM.alias(d.clazz.getSimpleName(), d.clazz);
        }
    }
}
