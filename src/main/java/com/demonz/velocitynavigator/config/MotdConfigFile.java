package com.demonz.velocitynavigator.config;

import org.tomlj.Toml;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MotdConfigFile {
    private static final int CONTENT_WIDTH = 76;

    private MotdConfigFile() {
    }

    public static String create(MotdConfig config) {
        return update("# VelocityNavigator MOTD Configuration\n"
                + "# Each triple-quoted block is one MOTD. \\n creates an in-game line break.\n"
                + "# A backslash at the end of a file line continues it without adding a line break.\n"
                + "# Modes: ROTATING, RANDOM, SEQUENTIAL.\n", config);
    }

    public static String reformat(String source) {
        List<Edit> edits = new ArrayList<>();
        for (Map.Entry<String, Span> entry : assignments(source).entrySet()) {
            if (!entry.getKey().equals("motds") && !entry.getKey().equals("maintenance.motds")) continue;
            Span span = entry.getValue();
            int cursor = span.start();
            while (cursor < span.end()) {
                char c = source.charAt(cursor);
                if (c == '#') {
                    cursor = lineEnd(source, cursor);
                } else if (c == '"' || c == '\'') {
                    int end = stringEnd(source, cursor);
                    String raw = source.substring(cursor, end);
                    String value = Toml.parse("value = " + raw).getString("value");
                    if (value.contains("\n") || raw.lines().anyMatch(line -> line.length() > CONTENT_WIDTH)) {
                        edits.add(new Edit(cursor, end, multiline(value)));
                    }
                    cursor = end;
                } else {
                    cursor++;
                }
            }
        }
        String formatted = apply(source, edits);
        List<Edit> packedArrays = new ArrayList<>();
        for (Map.Entry<String, Span> entry : assignments(formatted).entrySet()) {
            if (!entry.getKey().equals("motds") && !entry.getKey().equals("maintenance.motds")) continue;
            Span span = entry.getValue();
            String raw = formatted.substring(span.start(), span.end());
            if (raw.lines().filter(line -> !line.stripLeading().startsWith("#")).anyMatch(line -> line.length() > 88)) {
                List<String> values = Toml.parse("value = " + raw).getArray("value").toList().stream().map(String.class::cast).toList();
                String replacement = array(values);
                replacement = "[\n" + comments(raw) + replacement.substring(2);
                packedArrays.add(new Edit(span.start(), span.end(), replacement));
            }
        }
        return apply(formatted, packedArrays);
    }

    public static String update(String source, MotdConfig config) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("enabled", String.valueOf(config.enabled()));
        values.put("mode", "\"" + config.mode() + "\"");
        values.put("rotation_interval_seconds", String.valueOf(config.rotationIntervalSeconds()));
        values.put("motds", array(config.motds()));
        values.put("maintenance.override_motd_on_maintenance", String.valueOf(config.maintenance().overrideMotdOnMaintenance()));
        values.put("maintenance.motds", array(config.maintenance().motds()));
        Map<String, Span> spans = assignments(source);
        List<Edit> edits = new ArrayList<>();
        StringBuilder rootMissing = new StringBuilder();
        StringBuilder maintenanceMissing = new StringBuilder();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            Span span = spans.get(entry.getKey());
            if (span != null) {
                String value = entry.getValue();
                if (value.startsWith("[")) {
                    value = "[\n" + comments(source.substring(span.start(), span.end())) + value.substring(2);
                }
                edits.add(new Edit(span.start(), span.end(), value));
            } else if (entry.getKey().startsWith("maintenance.")) {
                maintenanceMissing.append(entry.getKey().substring("maintenance.".length()))
                        .append(" = ").append(entry.getValue()).append('\n');
            } else {
                rootMissing.append(entry.getKey()).append(" = ").append(entry.getValue()).append('\n');
            }
        }
        if (!rootMissing.isEmpty()) {
            int position = spans.get("@root").start();
            edits.add(new Edit(position, position, "\n" + rootMissing));
        }
        if (!maintenanceMissing.isEmpty()) {
            Span section = spans.get("@maintenance");
            int position = section == null ? source.length() : section.end();
            edits.add(0, new Edit(position, position, "\n" + (section == null ? "[maintenance]\n" : "") + maintenanceMissing));
        }
        return apply(source, edits);
    }

    private static String array(List<String> motds) {
        StringBuilder text = new StringBuilder("[\n");
        for (int i = 0; i < motds.size(); i++) {
            text.append("    ").append(multiline(motds.get(i)));
            if (i < motds.size() - 1) text.append(',');
            text.append('\n');
        }
        return text.append(']').toString();
    }

    private static String multiline(String value) {
        StringBuilder text = new StringBuilder("\"\"\"\\\n    ");
        int width = 0;
        for (int offset = 0; offset < value.length();) {
            int end = tokenEnd(value, offset);
            int tokenWidth = value.substring(offset, end).codePoints().map(cp -> escape(cp).length()).sum();
            if (width > 0 && tokenWidth <= CONTENT_WIDTH && width + tokenWidth > CONTENT_WIDTH
                    && value.charAt(offset) != '\n') {
                text.append("\\\n    ");
                width = 0;
            }
            while (offset < end) {
                int cp = value.codePointAt(offset);
                offset += Character.charCount(cp);
                String unit = escape(cp);
                if (width + unit.length() > CONTENT_WIDTH && cp != '\n') {
                    text.append("\\\n    ");
                    width = 0;
                }
                if (width == 0 && cp == ' ') unit = "\\u0020";
                text.append(unit);
                width += unit.length();
                if (cp == '\n') {
                    text.append("\\\n    ");
                    width = 0;
                }
            }
        }
        return text.append("\\\n    \"\"\"").toString();
    }

    private static int tokenEnd(String value, int offset) {
        if (value.charAt(offset) == '<') {
            int end = value.indexOf('>', offset);
            if (end >= 0) return end + 1;
        }
        int end = offset + Character.charCount(value.codePointAt(offset));
        if (Character.isWhitespace(value.codePointAt(offset))) return end;
        while (end < value.length() && value.charAt(end) != '<' && !Character.isWhitespace(value.codePointAt(end))) {
            end += Character.charCount(value.codePointAt(end));
        }
        return end;
    }

    private static String escape(int cp) {
        return switch (cp) {
            case '\\' -> "\\\\";
            case '"' -> "\\\"";
            case '\n' -> "\\n";
            case '\r' -> "\\r";
            case '\t' -> "\\t";
            case '\b' -> "\\b";
            case '\f' -> "\\f";
            default -> cp < 0x20 || cp == 0x7f ? String.format("\\u%04X", cp) : new String(Character.toChars(cp));
        };
    }

    private static String comments(String text) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < text.length();) {
            char c = text.charAt(i);
            if (c == '\'' || c == '"') i = stringEnd(text, i);
            else if (c == '#') {
                int end = lineEnd(text, i);
                result.append("    ").append(text, i, end).append('\n');
                i = end;
            } else i++;
        }
        return result.toString();
    }

    private static Map<String, Span> assignments(String source) {
        Map<String, Span> spans = new LinkedHashMap<>();
        String section = "";
        int firstHeader = source.length();
        int maintenanceStart = -1;
        int maintenanceEnd = source.length();
        for (int cursor = 0; cursor < source.length();) {
            char c = source.charAt(cursor);
            if (Character.isWhitespace(c)) { cursor++; continue; }
            if (c == '#') { cursor = lineEnd(source, cursor); continue; }
            if (c == '[') {
                firstHeader = Math.min(firstHeader, cursor);
                if (section.equals("maintenance")) maintenanceEnd = cursor;
                int end = source.indexOf(']', cursor);
                if (end < 0) throw new IllegalArgumentException("Unclosed TOML section");
                section = source.substring(cursor + 1, end).trim().replace("\"", "").replace("'", "");
                if (section.equals("maintenance")) maintenanceStart = cursor;
                cursor = lineEnd(source, end);
                continue;
            }
            int equals = cursor;
            while (equals < source.length() && source.charAt(equals) != '=') {
                char k = source.charAt(equals);
                equals = k == '\'' || k == '"' ? stringEnd(source, equals) : equals + 1;
            }
            if (equals == source.length()) break;
            String key = source.substring(cursor, equals).trim().replace("\"", "").replace("'", "");
            int start = equals + 1;
            while (start < source.length() && Character.isWhitespace(source.charAt(start))) start++;
            int end = valueEnd(source, start);
            spans.put(section.isEmpty() ? key : section + "." + key, new Span(start, end));
            cursor = end;
        }
        spans.put("@root", new Span(firstHeader, firstHeader));
        if (maintenanceStart >= 0) spans.put("@maintenance", new Span(maintenanceStart, maintenanceEnd));
        return spans;
    }

    private static int valueEnd(String source, int start) {
        char first = source.charAt(start);
        if (first == '"' || first == '\'') return stringEnd(source, start);
        if (first != '[' && first != '{') {
            int end = start;
            while (end < source.length() && source.charAt(end) != '\n' && source.charAt(end) != '#') end++;
            while (end > start && Character.isWhitespace(source.charAt(end - 1))) end--;
            return end;
        }
        int depth = 0;
        for (int i = start; i < source.length();) {
            char c = source.charAt(i);
            if (c == '"' || c == '\'') { i = stringEnd(source, i); continue; }
            if (c == '#') { i = lineEnd(source, i); continue; }
            if (c == '[' || c == '{') depth++;
            if ((c == ']' || c == '}') && --depth == 0) return i + 1;
            i++;
        }
        throw new IllegalArgumentException("Unclosed TOML value");
    }

    private static int stringEnd(String source, int start) {
        char quote = source.charAt(start);
        String delimiter = source.startsWith(String.valueOf(quote).repeat(3), start)
                ? String.valueOf(quote).repeat(3) : String.valueOf(quote);
        for (int i = start + delimiter.length(); i < source.length(); i++) {
            if (quote == '"' && source.charAt(i) == '\\') { i++; continue; }
            if (source.startsWith(delimiter, i)) {
                int end = i + delimiter.length();
                if (delimiter.length() == 3) {
                    while (end < source.length() && end < i + 5 && source.charAt(end) == quote) end++;
                }
                return end;
            }
        }
        throw new IllegalArgumentException("Unclosed TOML string");
    }

    private static int lineEnd(String text, int start) {
        int end = text.indexOf('\n', start);
        return end < 0 ? text.length() : end;
    }

    private static String apply(String source, List<Edit> edits) {
        StringBuilder result = new StringBuilder(source);
        edits.sort(Comparator.comparingInt(Edit::start).thenComparingInt(Edit::end).reversed());
        for (Edit edit : edits) result.replace(edit.start(), edit.end(), edit.value());
        return result.toString();
    }

    public static void writeAtomic(Path path, String content) throws IOException {
        Files.createDirectories(path.toAbsolutePath().getParent());
        Path temporary = Files.createTempFile(path.toAbsolutePath().getParent(), "motd-", ".tmp");
        try {
            Files.writeString(temporary, content);
            try {
                Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException error) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private record Span(int start, int end) {}
    private record Edit(int start, int end, String value) {}
}
