package io.j0.react.model.providers;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.j0.react.model.Message;
import io.j0.react.model.ModelRequest;
import io.j0.react.model.ModelResponse; 

/**
 * Minimal Ollama HTTP provider for standalone demo use.
 * All requests and responses are traced to {@code ollama-trace.log} in the working directory.
 * Production code should use {@code OllamaModelProvider} from {@code hubbers-core}.
 */
public class OllamaProvider extends ModelProvider {

    private static final String DEFAULT_BASE_URL = "http://localhost:11434/api/generate";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final HttpClient  httpClient;

    public OllamaProvider() {
        this(DEFAULT_BASE_URL);
    }

    public OllamaProvider(String baseUrl) {
        this.baseUrl    = baseUrl.replaceAll("/+$", ""); // strip trailing slash
        this.traceFileName = Paths.get("ollama-trace.log");
        this.httpClient = HttpClient.newHttpClient();
    }

    @Override
    public ModelResponse generate(ModelRequest request) {
        try {
            String prompt  = buildPrompt(request);
            String escaped = prompt.replace("\\", "\\\\")
                                    .replace("\"", "\\\"")
                                    .replace("\n", "\\n")
                                    .replace("\r", "\\r");
            String payload = "{\"model\":\"" + request.getModel() + "\",\"prompt\":\"" + escaped + "\",\"stream\":false}";

            traceRequest(request.getModel(), prompt);

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl))
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
            err.setContent("<thought>Errore di connessione a Ollama</thought>");
            return err;
        }
    }

    // =========================================================================
    // Prompt / response helpers
    // =========================================================================

    private String buildPrompt(ModelRequest request) {
        if (request.getMessages() != null && !request.getMessages().isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (Message msg : request.getMessages()) {
                sb.append(msg.getRole()).append(": ").append(msg.getContent()).append("\n");
            }
            return sb.toString();
        }
        String sys  = request.getSystemPrompt() != null ? request.getSystemPrompt() + "\n" : "";
        String user = request.getUserPrompt()   != null ? request.getUserPrompt()            : "";
        return sys + user;
    }

    /**
     * Extracts the {@code response} field using proper JSON parsing (see
     * {@code LlamaCppProvider.extractContent} for why a regex is unsafe here:
     * the model's own output may contain JSON-like text that confuses a
     * naive quote/comma-based terminator match).
     */
    private String extractContent(String body) {
        try {
            JsonNode root = MAPPER.readTree(body);
            JsonNode responseNode = root.path("response");
            if (!responseNode.isMissingNode() && !responseNode.isNull()) {
                return responseNode.asText();
            }
        } catch (IOException e) {
            // Not valid JSON (e.g. an HTML error page) — fall through to raw body.
        }
        return body;
    }

    
}
