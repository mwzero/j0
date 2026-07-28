package io.j0.react.model.providers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.nio.file.Path;
import java.time.Instant;
import java.util.regex.Pattern;

import io.j0.react.model.ModelRequest;
import io.j0.react.model.ModelResponse;

/**
 * Abstraction for calling a large language model.
 *
 * <p>Implementations may delegate to a single underlying provider (e.g. Ollama, OpenAI)
 * or act as a routing registry that picks the right provider based on
 * {@link ModelRequest#getProvider()}.</p>
 *
 * <p>This interface intentionally has no {@code providerName()} method so that it can
 * also be used as a {@code @FunctionalInterface} and passed as a lambda from
 * {@code hubbers-core} wiring code.</p>
 */
public abstract class ModelProvider {
    
    protected String baseUrl;
    protected Path traceFileName;
    private static final Pattern UNICODE_PATTERN = Pattern.compile("\\\\u([0-9A-Fa-f]{4})");

    /**
     * Send a request to the underlying LLM and return its response.
     *
     * @param request the model request (prompt, messages, functions, etc.)
     * @return the model response
     */
    public abstract ModelResponse generate(ModelRequest request);

    protected String decodeUnicode(String input) {
        java.util.regex.Matcher matcher = UNICODE_PATTERN.matcher(input);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            char ch = (char) Integer.parseInt(matcher.group(1), 16);
            matcher.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(String.valueOf(ch)));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    // =========================================================================
    // Trace helpers
    // =========================================================================
    protected void traceRequest(String model, String messagesJson) {
        String entry = """
                ================================================================================
                [%s] REQUEST [%s] endpoint=%s/v1/chat/completions
                --------------------------------------------------------------------------------
                %s
                """.formatted(Instant.now(), model,baseUrl, messagesJson);
        appendTrace(entry);
    }

    protected void traceResponse(String content) {
        String entry = """
                [%s] RESPONSE
                --------------------------------------------------------------------------------
                %s

                """.formatted(Instant.now(), content);
        appendTrace(entry);
    }

    protected void traceError(String message) {
        String entry = "[%s] ERROR: %s%n%n".formatted(Instant.now(), message);
        appendTrace(entry);
    }

    protected void appendTrace(String text) {
        try {
            Files.writeString(traceFileName, text, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {
            // Trace failures must never propagate to the main flow
        }
    }
}
