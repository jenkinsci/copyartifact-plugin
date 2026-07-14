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
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Filter to find builds matching particular parameters.
 * @author Alan Harder
 */
public class ParametersBuildFilter extends BuildFilter {
    private static final Logger LOGGER = Logger.getLogger(ParametersBuildFilter.class.getName());
    private List<StringParameterValue> filters;

    private static final Pattern PARAMVAL_PATTERN = Pattern.compile("(.*?)=([^,]*)(,|$)");
    // Pattern to detect if a value contains regex special characters
    private static final Pattern REGEX_PATTERN = Pattern.compile("[.*+?^$()\\[\\]{}|\\\\]");

    /**
     * Check if an actual parameter value matches the expected value.
     * Supports both exact string matching and regex matching.
     * If the expected value contains regex special characters, it's treated as a regex pattern.
     * Otherwise, exact string matching is used.
     *
     * @param expectedValue the expected parameter value (may contain regex)
     * @param actualValue the actual parameter value from the build
     * @return true if values match
     */
    private boolean matchesParameterValue(String expectedValue, String actualValue) {
        if (actualValue == null) {
            return false;
        }

        // If expected value contains regex special characters, treat it as regex
        if (REGEX_PATTERN.matcher(expectedValue).find()) {
            try {
                Pattern pattern = Pattern.compile(expectedValue);
                boolean matches = pattern.matcher(actualValue).matches();
                LOGGER.log(Level.FINE, "ParametersBuildFilter.matchesParameterValue: Regex match - pattern: ''{0}'', value: ''{1}'', matches: {2}",
                        new Object[]{expectedValue, actualValue, matches});
                return matches;
            } catch (PatternSyntaxException e) {
                LOGGER.log(Level.FINE, "ParametersBuildFilter.matchesParameterValue: Invalid regex pattern ''{0}'', falling back to exact match",
                        expectedValue);
                // Fall back to exact match if regex is invalid
                return Objects.equals(expectedValue, actualValue);
            }
        } else {
            // Exact string match
            return Objects.equals(expectedValue, actualValue);
        }
    }

    public ParametersBuildFilter(String paramsToMatch) {
        // Initialize.. parse out the given parameters/values.
        filters = new ArrayList<>(5);
        Matcher m = PARAMVAL_PATTERN.matcher(paramsToMatch);
        while (m.find()) {
            filters.add(new StringParameterValue(m.group(1), m.group(2)));
        }
    }

    public boolean isValid(Job<?,?> job) {
        if (filters.isEmpty()) {
            LOGGER.log(Level.FINE, "ParametersBuildFilter.isValid: No filters parsed for job {0}", job.getFullName());
            return false;
        }
        LOGGER.log(Level.FINE, "ParametersBuildFilter.isValid: Checking if job {0} has any build with required parameters: {1}",
                new Object[]{job.getFullName(), filters});

        // Consider the filter valid for this job if any build for this job has all the filter params
        int checkedBuilds = 0;
        outer:
        for (Run<?, ?> run = job.getLastCompletedBuild(); run != null; run = run.getPreviousCompletedBuild()) {
            checkedBuilds++;
            try {
                EnvVars env = run.getEnvironment(TaskListener.NULL);
                for (StringParameterValue spv : filters) {
                    if (!env.containsKey(spv.getName())) {
                        LOGGER.log(Level.FINE, "ParametersBuildFilter.isValid: Build #{0} missing parameter {1}, continuing search",
                                new Object[]{run.getNumber(), spv.getName()});
                        continue outer;
                    }
                }
                LOGGER.log(Level.FINE, "ParametersBuildFilter.isValid: Found valid build #{0} for job {1} with all required parameters",
                        new Object[]{run.getNumber(), job.getFullName()});
                return true;
            } catch (InterruptedException | IOException ex) {
                LOGGER.log(Level.FINE, "ParametersBuildFilter.isValid: Failed to get environment for build #{0}: {1}",
                        new Object[]{run.getNumber(), ex.getMessage()});
            }
            if (checkedBuilds >= 10) { // Limit log spam
                LOGGER.log(Level.FINE, "ParametersBuildFilter.isValid: Checked {0} builds for job {1}, stopping search to avoid spam",
                        new Object[]{checkedBuilds, job.getFullName()});
                break;
            }
        }
        LOGGER.log(Level.FINE, "ParametersBuildFilter.isValid: No valid builds found for job {0} after checking {1} builds",
                new Object[]{job.getFullName(), checkedBuilds});
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isSelectable(Run<?,?> run, EnvVars env) {
        LOGGER.log(Level.FINE, "ParametersBuildFilter.isSelectable: Checking build #{0} with filters: {1}",
                new Object[]{run.getNumber(), filters});

        EnvVars otherEnv;
        try {
            // First, try to get environment variables directly
            // This maintains backward compatibility and handles build variables like BUILD_NUMBER
            otherEnv = run.getEnvironment(TaskListener.NULL);
            LOGGER.log(Level.FINE, "ParametersBuildFilter.isSelectable: Successfully got environment for build #{0}",
                    run.getNumber());
        } catch (Exception ex) {
            LOGGER.log(Level.FINE, "ParametersBuildFilter.isSelectable: getEnvironment() failed for build #{0}, trying ParametersAction fallback: {1}",
                    new Object[]{run.getNumber(), ex.getMessage()});
            // If getEnvironment fails due to permission restrictions,
            // try to get parameters from ParametersAction as a fallback
            otherEnv = new EnvVars();
            try {
                for(ParametersAction pa: run.getActions(ParametersAction.class)) {
                    for(ParameterValue pv: pa.getParameters()) {
                        pv.buildEnvironment(run, otherEnv);
                    }
                }
                LOGGER.log(Level.FINE, "ParametersBuildFilter.isSelectable: Successfully extracted {0} parameters from ParametersAction for build #{1}",
                        new Object[]{otherEnv.size(), run.getNumber()});
            } catch (Exception ex2) {
                LOGGER.log(Level.FINE, "ParametersBuildFilter.isSelectable: Failed to get parameters for build #{0}: {1}",
                        new Object[]{run.getNumber(), ex2.getMessage()});
                // If we can't access parameters at all, the build is not selectable
                return false;
            }
        }

        // Check if all filter parameters match
        for (StringParameterValue spv : filters) {
            String expectedValue = spv.getValue();
            String actualValue = otherEnv.get(spv.getName());
            if (!matchesParameterValue(expectedValue, actualValue)) {
                String matchType = REGEX_PATTERN.matcher(expectedValue).find() ? "regex" : "exact";
                LOGGER.log(Level.FINE, "ParametersBuildFilter.isSelectable: Build #{0} parameter mismatch ({4}) - {1}: expected ''{2}'', got ''{3}''",
                        new Object[]{run.getNumber(), spv.getName(), expectedValue, actualValue, matchType});
                return false;
            }
            String matchType = REGEX_PATTERN.matcher(expectedValue).find() ? "regex" : "exact";
            LOGGER.log(Level.FINE, "ParametersBuildFilter.isSelectable: Build #{0} parameter match ({4}) - {1}=''{2}''",
                    new Object[]{run.getNumber(), spv.getName(), actualValue, null, matchType});
        }

        LOGGER.log(Level.FINE, "ParametersBuildFilter.isSelectable: Build #{0} selected - all parameters match",
                run.getNumber());
        return true;
    }
}
