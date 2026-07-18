/*
 * Copyright 2026 DemonZ Development
 * Licensed under the Apache License, Version 2.0.
 */
package com.demonz.velocitynavigator;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class MenuTemplateFormatter {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([a-z_]+)}|<([a-z_]+)>");

    private MenuTemplateFormatter() {
    }

    static String replace(String template, Map<String, String> values) {
        if (template == null || template.isEmpty() || values == null || values.isEmpty()) {
            return template == null ? "" : template;
        }
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuffer result = new StringBuffer(template.length());
        while (matcher.find()) {
            String key = matcher.group(1) == null ? matcher.group(2) : matcher.group(1);
            if (!values.containsKey(key)) {
                continue;
            }
            String value = values.get(key);
            matcher.appendReplacement(result, Matcher.quoteReplacement(value == null ? "" : value));
        }
        matcher.appendTail(result);
        return result.toString();
    }
}
