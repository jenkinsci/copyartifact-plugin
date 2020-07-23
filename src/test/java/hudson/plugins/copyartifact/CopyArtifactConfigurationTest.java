/*
 * The MIT License
 *
 * Copyright (c) 2019 IKEDA Yasuyuki
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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.hamcrest.Matchers;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;

import com.gargoylesoftware.htmlunit.html.HtmlForm;

import hudson.XmlFile;
import hudson.model.Saveable;
import hudson.model.User;
import hudson.model.listeners.SaveableListener;

/**
 * Tests for {@link CopyArtifactConfiguration}
 */
public class CopyArtifactConfigurationTest {
    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void configProduction() throws Exception {
        CopyArtifactConfiguration config = CopyArtifactConfiguration.get();
        config.setMode(CopyArtifactCompatibilityMode.PRODUCTION);
        assertThat(config.getMode(), Matchers.is(CopyArtifactCompatibilityMode.PRODUCTION));
        // just do as JenkinsRule#configRoundTrip()
        HtmlForm form = j.createWebClient().goTo("configureSecurity").getFormByName("config");
        config.setMode(CopyArtifactCompatibilityMode.MIGRATION);
        assertThat(config.getMode(), Matchers.is(CopyArtifactCompatibilityMode.MIGRATION));
        j.submit(form);
        assertThat(config.getMode(), Matchers.is(CopyArtifactCompatibilityMode.PRODUCTION));
    }

    @Test
    public void configMigration() throws Exception {
        CopyArtifactConfiguration config = CopyArtifactConfiguration.get();
        config.setMode(CopyArtifactCompatibilityMode.MIGRATION);
        assertThat(config.getMode(), Matchers.is(CopyArtifactCompatibilityMode.MIGRATION));
        // just do as JenkinsRule#configRoundTrip()
        HtmlForm form = j.createWebClient().goTo("configureSecurity").getFormByName("config");
        config.setMode(CopyArtifactCompatibilityMode.PRODUCTION);
        assertThat(config.getMode(), Matchers.is(CopyArtifactCompatibilityMode.PRODUCTION));
        j.submit(form);
        assertThat(config.getMode(), Matchers.is(CopyArtifactCompatibilityMode.MIGRATION));
    }

    @Test
    public void productionMode_forFresh() throws Exception {
        CopyArtifactConfiguration config = CopyArtifactConfiguration.get();
        // autoconfigured to production, and stored to the disk.
        assertThat(config.getMode(), Matchers.is(CopyArtifactCompatibilityMode.PRODUCTION));
        assertFalse(config.isFirstLoad());
        config.load();
        assertThat(config.getMode(), Matchers.is(CopyArtifactCompatibilityMode.PRODUCTION));
        assertFalse(config.isFirstLoad());

        config.setMode(CopyArtifactCompatibilityMode.MIGRATION);
        assertThat(config.getMode(), Matchers.is(CopyArtifactCompatibilityMode.MIGRATION));
        config.setToFirstLoad();
        config.load();
        assertThat(config.getMode(), Matchers.is(CopyArtifactCompatibilityMode.PRODUCTION));
    }

    @Test
    @Ignore("No way to detect we are in a new version of the plugin within a test")
    public void migrationMode_forUpgrade() throws Exception {
        CopyArtifactConfiguration config = CopyArtifactConfiguration.get();
        config.setMode(CopyArtifactCompatibilityMode.PRODUCTION);
        assertThat(config.getMode(), Matchers.is(CopyArtifactCompatibilityMode.PRODUCTION));

        // TODO: somehow do upgrading sequence

        config.setToFirstLoad();
        config.load();
        assertThat(config.getMode(), Matchers.is(CopyArtifactCompatibilityMode.MIGRATION));
    }

    @Test
    public void productionMode_storedToTheDisk() throws Exception {
        CopyArtifactConfiguration config = CopyArtifactConfiguration.get();
        assertThat(config.getMode(), Matchers.is(CopyArtifactCompatibilityMode.PRODUCTION));
        assertFalse(config.isFirstLoad());

        config.setModeWithoutSave(CopyArtifactCompatibilityMode.MIGRATION);
        assertThat(config.getMode(), Matchers.is(CopyArtifactCompatibilityMode.MIGRATION));
        config.load();
    }

    @Issue("JENKINS-62267")
    @Test
    public void circularDependencyTestWithSavableListener() throws Exception {
        assertNotNull(CopyArtifactConfiguration.get());
    }

    @TestExtension("circularDependencyTestWithSavableListener")
    public static class LoadingExtensionFinderSavableListener extends SaveableListener {
        Logger LOG = Logger.getLogger(LoadingExtensionFinderSavableListener.class.getName());

        @Override
        public void onChange(Saveable config, XmlFile file) {
            // Initialization of CopyArtifactConfiguration triggers me.

            // An operation to call ExtensionFinder to verify JENKINS-62267 is fixed.
            User user = User.current();
            LOG.log(
                Level.INFO,
                "LoadingExtensionFinderSavableListener#onChange with: {0}",
                (user != null) ? user.getId() : "NULL"
            );
        }
    }
}
