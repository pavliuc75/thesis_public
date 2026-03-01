package org.camunda.bpm.getstarted.loanapproval.delegates.validation.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JuelToGroovyParser {

    private static final Pattern JUEL_PATTERN = Pattern.compile("^\\$\\{(.*)\\}$");

    /**
     * Parses a JUEL expression like ${req1.status == "NEEDS_CLARIFICATION"} 
     * into a Groovy-compatible string.
     */
    public static String parse(String expression) {
        if (expression == null || expression.isBlank()) {
            return "";
        }

        String trimmed = expression.trim();
        Matcher matcher = JUEL_PATTERN.matcher(trimmed);

        if (matcher.find()) {
            // Extract the content between ${ and }
            String content = matcher.group(1).trim();
            
            // Handle basic JUEL vs Groovy differences if necessary
            // For simple expressions like req1.status == "...", Groovy syntax is identical
            return content;
        }

        // Return as is if it doesn't match the ${} pattern, 
        // as it might already be a raw expression or a constant
        return trimmed;
    }
}
