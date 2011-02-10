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
import hudson.model.BooleanParameterValue;
import hudson.model.ParameterValue;
import hudson.model.ParametersAction;
import hudson.model.Run;
import hudson.model.StringParameterValue;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Additional filter used by BuildSelector.
 * @author Alan Harder
 */
public class ParametersBuildFilter extends BuildFilter {
    private String paramsToMatch;
    private List<StringParameterValue> stringMatches;
    private List<BooleanParameterValue> booleanMatches;

    private static final Pattern PARAMVAL_PATTERN = Pattern.compile("(.*?)=([^,]*)(,|$)");

    public ParametersBuildFilter(String paramsToMatch) {
        this.paramsToMatch = paramsToMatch;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isSelectable(Run<?,?> run, EnvVars env) {
        if (stringMatches == null) {
            // Initialize.. parse out the given parameters/values.
            Matcher m = PARAMVAL_PATTERN.matcher(paramsToMatch);
            stringMatches = new ArrayList<StringParameterValue>(5);
            booleanMatches = new ArrayList<BooleanParameterValue>(5);
            while (m.find()) {
                String name = m.group(1), value = m.group(2);
                stringMatches.add(new StringParameterValue(name, value));
                // Try Boolean if parameter value looks boolean
                if ("true".equalsIgnoreCase(value) || "1".equals(value)
                        || "yes".equalsIgnoreCase(value) || "on".equalsIgnoreCase(value)) {
                    booleanMatches.add(new BooleanParameterValue(name, true));
                }
                else if ("false".equalsIgnoreCase(value) || "0".equals(value)
                        || "no".equalsIgnoreCase(value) || "off".equalsIgnoreCase(value)) {
                    booleanMatches.add(new BooleanParameterValue(name, false));
                }
                else booleanMatches.add(null);
            }
        }
        if (stringMatches.isEmpty()) return false;  // Unable to parse text after /

        ParametersAction pa = run.getAction(ParametersAction.class);
        if (pa == null) return false;
        int i = 0;
        // All parameters must match (either as string or boolean):
        for (StringParameterValue spv : stringMatches) {
            BooleanParameterValue bpv = booleanMatches.get(i++);
            boolean ok = false;
            for (ParameterValue pv : pa.getParameters()) {
                if (spv.equals(pv) || (bpv != null && bpv.equals(pv))) {
                    ok = true;
                    break;
                }
            }
            if (!ok) return false; // No match for this parameter
        }
        return true;
    }
}
