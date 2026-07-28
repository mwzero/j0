package io.j0.react.model.providers;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Paths;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.j0.react.model.Message;
import io.j0.react.model.ModelRequest;
import io.j0.react.model.ModelResponse;

/**
 * Minimal llama.cpp server HTTP provider for standalone demo use.
 *
 * <p>Targets the llama.cpp OpenAI-compatible endpoint:
 * {@code POST http://localhost:8080/v1/chat/completions}.</p>
 *
 * <p>All requests and responses are traced to {@code llamacpp-trace.log}
 * in the working directory.</p>
 *
 * <p>Production code should use {@code OllamaModelProvider} or a dedicated
 * {@code LlamaCppModelProvider} from {@code hubbers-core}.</p>
 */
public class LlamaCppProvider extends ModelProvider {

    private static final String DEFAULT_BASE_URL = "http://localhost:8080";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String      baseUrl;
    private final HttpClient  httpClient;

    public LlamaCppProvider() {
        this(DEFAULT_BASE_URL);
    }

    public LlamaCppProvider(String baseUrl) {
        this.baseUrl    = baseUrl.replaceAll("/+$", ""); // strip trailing slash
        this.httpClient = HttpClient.newHttpClient();
        this.traceFileName = Paths.get("llamacpp-trace.log");
    }

    @Override
    public ModelResponse generate(ModelRequest request) {
        try {
            String messagesJson = buildMessagesJson(request);
            String payload = "{\"messages\":" + messagesJson + ",\"stream\":false}";

            traceRequest(request.getModel(), messagesJson);

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/v1/chat/completions"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

            HttpResponse<String> resp = httpClient.send(httpRequest,
                    HttpResponse.BodyHandlers.ofString());

            String content = extractContent(resp.body());
            traceResponse(content);

            ModelResponse response = new ModelResponse();
            response.setContent(content);
            return response;

        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            traceError(e.getMessage());
            ModelResponse err = new ModelResponse();
            err.setContent("<thought>Errore di connessione a llama.cpp</thought>");
            return err;
        }
    }

    // =========================================================================
    // Prompt builder
    // =========================================================================

    /**
     * Serializes the message list into a JSON array compatible with the
     * OpenAI {@code /v1/chat/completions} format.
     */
    private String buildMessagesJson(ModelRequest request) {
        StringBuilder sb = new StringBuilder("[");

        if (request.getMessages() != null && !request.getMessages().isEmpty()) {
            for (int i = 0; i < request.getMessages().size(); i++) {
                Message msg = request.getMessages().get(i);
                if (i > 0) sb.append(",");
                sb.append("{\"role\":\"").append(escape(msg.getRole()))
                  .append("\",\"content\":\"").append(escape(msg.getContent()))
                  .append("\"}");
            }
        } else {
            // Fallback: system + user from flat fields
            if (request.getSystemPrompt() != null) {
                sb.append("{\"role\":\"system\",\"content\":\"")
                  .append(escape(request.getSystemPrompt())).append("\"}");
                if (request.getUserPrompt() != null) sb.append(",");
            }
            if (request.getUserPrompt() != null) {
                sb.append("{\"role\":\"user\",\"content\":\"")
                  .append(escape(request.getUserPrompt())).append("\"}");
            }
        }

        sb.append("]");
        return sb.toString();
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    // =========================================================================
    // Response extraction
    // =========================================================================

    /**
     * Extracts {@code choices[0].message.content} using proper JSON parsing.
     *
     * <p><strong>Why not a regex:</strong> a previous regex-based implementation
     * (matching up to the first {@code "} followed by {@code ,} or {@code }})
     * silently truncated the response whenever the model's own output contained
     * JSON-like text (e.g. echoing a tool observation such as
     * {@code {"status":"success"}} inside its {@code <thought>}). The escaped
     * quote {@code \"} before the comma looks identical to the field terminator
     * to a naive regex, cutting the message mid-sentence and silently discarding
     * any tool call that followed. Proper JSON parsing handles escaping correctly.</p>
     */
    private String extractContent(String body) {
        try {
            JsonNode root = MAPPER.readTree(body);
            JsonNode contentNode = root.path("choices").path(0).path("message").path("content");
            if (contentNode.isMissingNode() || contentNode.isNull()) {
                // Fallback for servers that put content at the top level.
                contentNode = root.path("content");
            }
            if (!contentNode.isMissingNode() && !contentNode.isNull()) {
                return contentNode.asText();
            }
        } catch (IOException e) {
            // Not valid JSON (e.g. an HTML error page) — fall through to raw body.
        }
        return body;
    }
}
