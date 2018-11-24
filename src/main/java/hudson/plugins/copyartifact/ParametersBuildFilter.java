/*
 * The MIT License
 *
 * Copyright (c) 2011, Alan Harder
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

import hudson.EnvVars;
import hudson.model.ParameterValue;
import hudson.model.TaskListener;
import hudson.model.AbstractBuild;
import hudson.model.Job;
import hudson.model.ParametersAction;
import hudson.model.Run;
import hudson.model.StringParameterValue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Filter to find builds matching particular parameters.
 * @author Alan Harder
 */
public class ParametersBuildFilter extends BuildFilter {
    private List<StringParameterValue> filters;

    private static final Pattern PARAMVAL_PATTERN = Pattern.compile("(.*?)=([^,]*)(,|$)");

    public ParametersBuildFilter(String paramsToMatch) {
        // Initialize.. parse out the given parameters/values.
        filters = new ArrayList<StringParameterValue>(5);
        Matcher m = PARAMVAL_PATTERN.matcher(paramsToMatch);
        while (m.find()) {
            filters.add(new StringParameterValue(m.group(1), m.group(2)));
        }
    }

    public boolean isValid(Job<?,?> job) {
        if (filters.isEmpty()) return false;  // Unable to parse text after /
        // Consider the filter valid for this job if any build for this job has all the filter params
        outer:
        for (Run<?,?> run = job.getLastCompletedBuild(); run != null; run = run.getPreviousCompletedBuild()) try {
            EnvVars env = run.getEnvironment(TaskListener.NULL);
            for (StringParameterValue spv : filters) {
                if (!env.containsKey(spv.getName())) {
                    continue outer;
                }
            }
            return true;
        } catch (InterruptedException | IOException ignore) {
        }
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isSelectable(Run<?,?> run, EnvVars env) {
        EnvVars otherEnv;
        try {
            otherEnv = run.getEnvironment(TaskListener.NULL);
        } catch (Exception ex) {
            return false;
        }
        if(!(run instanceof AbstractBuild)) {
            // Abstract#getEnvironment(TaskListener) put build parameters to
            // environments, but Run#getEnvironment(TaskListener) doesn't.
            // That means we can't retrieve build parameters from WorkflowRun
            // as it is a subclass of Run, not of AbstractBuild.
            // We need expand build parameters manually.
            // See JENKINS-26694 for details.
            for(ParametersAction pa: run.getActions(ParametersAction.class)) {
                // We have to extract parameters manually as ParametersAction#buildEnvVars
                // (overrides EnvironmentContributingAction#buildEnvVars)
                // is applicable only for AbstractBuild.
                for(ParameterValue pv: pa.getParameters()) {
                    pv.buildEnvironment(run, otherEnv);
                }
            }
        }
        for (StringParameterValue spv : filters) {
            if (!Objects.equals(spv.getValue(), otherEnv.get(spv.getName()))) {
                return false;
            }
        }
        return true;
    }
}
