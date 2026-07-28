package io.j0.code;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class JavaSourceUtils {

    private static final Pattern FENCED_CODE_PATTERN = Pattern.compile("```(?:java)?\\s*(.*?)```", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Pattern PUBLIC_CLASS_PATTERN = Pattern.compile("public\\s+class\\s+([A-Za-z_$][\\w$]*)");
    private static final Pattern MAIN_METHOD_PATTERN = Pattern.compile("public\\s+static\\s+void\\s+main\\s*\\(\\s*String\\s*\\[\\s*]\\s*args\\s*\\)");

    private JavaSourceUtils() {
    }

    public static String extractJavaSource(String rawContent) {
        String trimmed = rawContent == null ? "" : rawContent.trim();
        Matcher matcher = FENCED_CODE_PATTERN.matcher(trimmed);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return trimmed;
    }

    public static String detectPublicClassName(String source) {
        Matcher matcher = PUBLIC_CLASS_PATTERN.matcher(source);
        if (!matcher.find()) {
            throw new IllegalArgumentException("Generated source must contain one public class.");
        }
        return matcher.group(1);
    }

    public static void validateRunnableSource(String source) {
        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException("Generated source is empty.");
        }
        if (source.contains("package ")) {
            throw new IllegalArgumentException("Generated source must not declare a package.");
        }
        detectPublicClassName(source);
        if (!MAIN_METHOD_PATTERN.matcher(source).find()) {
            throw new IllegalArgumentException("Generated source must declare public static void main(String[] args).");
        }
    }
}