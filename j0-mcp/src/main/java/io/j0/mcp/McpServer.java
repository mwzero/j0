package io.j0.mcp;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.j0.code.JavaCodeExecutor;
import io.javalin.Javalin;

public final class McpServer {

    static final String PROTOCOL_VERSION = "2024-11-05";
    static final String SERVER_NAME = "j0-mcp";
    static final String SERVER_VERSION = "0.1.0-SNAPSHOT";
    static final String TOOL_NAME = "java_compile_and_run";

    private final ObjectMapper mapper;
    private final JavaCodeExecutor executor;
    private final Path defaultOutputDirectory;
    private final Duration defaultTimeout;
    private Javalin app;

    public McpServer() {
        this(new ObjectMapper(), new JavaCodeExecutor(), Path.of("generated", "j0-mcp"), Duration.ofSeconds(300));
    }

    McpServer(ObjectMapper mapper, JavaCodeExecutor executor, Path defaultOutputDirectory, Duration defaultTimeout) {
        this.mapper = mapper;
        this.executor = executor;
        this.defaultOutputDirectory = defaultOutputDirectory;
        this.defaultTimeout = defaultTimeout;
    }

    public Javalin run(int port) {
        if (app != null) {
            return app;
        }

        app = Javalin.create(config -> {
            config.showJavalinBanner = false;
        });

        configureCors(app);

        app.get("/health", ctx -> ctx.result("ok"));
        app.options("/mcp", ctx -> ctx.status(204));
        app.post("/mcp", ctx -> {
            JsonNode request = mapper.readTree(ctx.body());
            JsonNode response = handleRequest(request);
            if (response == null) {
                ctx.status(204);
                return;
            }
            ctx.contentType("application/json");
            ctx.result(mapper.writeValueAsString(response));
        });

        app.exception(Exception.class, (ex, ctx) -> {
            JsonNode id = null;
            try {
                JsonNode request = mapper.readTree(ctx.body());
                id = request.get("id");
            } catch (Exception ignored) {
                // Keep null id when request body is invalid.
            }
            JsonNode error = errorResponse(id, -32603, "Internal error: " + ex.getMessage(), null);
            ctx.status(500).contentType("application/json").result(error.toString());
        });

        app.start(port);
        return app;
    }

    private void configureCors(Javalin server) {
        server.before(ctx -> {
            String origin = ctx.header("Origin");
            if (origin == null || origin.isBlank()) {
                return;
            }

            String requestedHeaders = ctx.header("Access-Control-Request-Headers");
            String allowedHeaders = (requestedHeaders == null || requestedHeaders.isBlank())
                    ? "Content-Type, MCP-Protocol-Version"
                    : requestedHeaders;

            ctx.header("Vary", "Origin, Access-Control-Request-Headers");
            ctx.header("Access-Control-Allow-Origin", origin);
            ctx.header("Access-Control-Allow-Methods", "POST, OPTIONS");
            ctx.header("Access-Control-Allow-Headers", allowedHeaders);
            ctx.header("Access-Control-Max-Age", "600");
        });
    }

    public void stop() {
        if (app != null) {
            app.stop();
            app = null;
        }
    }

    JsonNode handleRequest(JsonNode request) {
        if (request == null || !request.isObject()) {
            return errorResponse(null, -32600, "Invalid request", null);
        }

        JsonNode id = request.get("id");
        String method = textValue(request, "method");
        if (method == null || method.isBlank()) {
            return errorResponse(id, -32600, "Invalid request: missing method", null);
        }

        if ("notifications/initialized".equals(method)) {
            return null;
        }

        try {
            return switch (method) {
                case "initialize" -> successResponse(id, initializeResult());
                case "tools/list" -> successResponse(id, listToolsResult());
                case "tools/call" -> successResponse(id, callTool(request.path("params")));
                default -> errorResponse(id, -32601, "Method not found: " + method, null);
            };
        } catch (IllegalArgumentException ex) {
            return errorResponse(id, -32602, "Invalid params: " + ex.getMessage(), null);
        } catch (Exception ex) {
            return errorResponse(id, -32603, "Internal error: " + ex.getMessage(), null);
        }
    }

    private ObjectNode initializeResult() {
        ObjectNode result = mapper.createObjectNode();
        result.put("protocolVersion", PROTOCOL_VERSION);

        ObjectNode capabilities = result.putObject("capabilities");
        capabilities.putObject("tools");

        ObjectNode serverInfo = result.putObject("serverInfo");
        serverInfo.put("name", SERVER_NAME);
        serverInfo.put("version", SERVER_VERSION);

        return result;
    }

    private ObjectNode listToolsResult() {
        ObjectNode result = mapper.createObjectNode();
        ArrayNode tools = result.putArray("tools");
        tools.add(toolDefinition());
        return result;
    }

    private ObjectNode toolDefinition() {
        ObjectNode tool = mapper.createObjectNode();
        tool.put("name", TOOL_NAME);
        tool.put("description", "Compile and run a Java source string that declares a public class with a main method.");

        ObjectNode schema = tool.putObject("inputSchema");
        schema.put("type", "object");
        ObjectNode properties = schema.putObject("properties");

        ObjectNode source = properties.putObject("source");
        source.put("type", "string");
        source.put("description", "Java source code to compile and run.");

        ObjectNode timeoutSeconds = properties.putObject("timeoutSeconds");
        timeoutSeconds.put("type", "integer");
        timeoutSeconds.put("minimum", 1);
        timeoutSeconds.put("default", defaultTimeout.toSeconds());

        ObjectNode outputDirectory = properties.putObject("outputDirectory");
        outputDirectory.put("type", "string");
        outputDirectory.put("description", "Directory where source and compiled classes are written.");
        outputDirectory.put("default", defaultOutputDirectory.toString());

        ObjectNode programArgs = properties.putObject("programArgs");
        programArgs.put("type", "array");
        ObjectNode argItems = programArgs.putObject("items");
        argItems.put("type", "string");

        ArrayNode required = schema.putArray("required");
        required.add("source");

        schema.put("additionalProperties", false);
        return tool;
    }

    private ObjectNode callTool(JsonNode params) throws IOException {
        String toolName = textValue(params, "name");
        if (!TOOL_NAME.equals(toolName)) {
            throw new IllegalArgumentException("Unknown tool: " + toolName);
        }

        JsonNode arguments = params.path("arguments");
        String source = firstText(arguments, "source", "code", "javaSource");
        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException("Missing required argument 'source'.");
        }

        Duration timeout = timeoutFrom(arguments.path("timeoutSeconds"));
        Path outputDirectory = pathFrom(arguments.path("outputDirectory"), defaultOutputDirectory);
        List<String> programArgs = listFrom(arguments.path("programArgs"));

        JavaCodeExecutor.ExecutionResult executionResult;
        try {
            executionResult = executor.compileAndRun(
                source,
                outputDirectory,
                timeout,
                programArgs
            );
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while compiling or running Java source.", ex);
        }

        ObjectNode payload = mapper.createObjectNode();
        payload.put("sourceFile", executionResult.sourceFile().toString());
        payload.put("classesDirectory", executionResult.classesDirectory().toString());
        payload.put("className", executionResult.className());
        payload.put("exitCode", executionResult.exitCode());
        payload.put("output", executionResult.output());

        ObjectNode result = mapper.createObjectNode();
        ArrayNode content = result.putArray("content");
        ObjectNode text = content.addObject();
        text.put("type", "text");
        text.put("text", mapper.writerWithDefaultPrettyPrinter().writeValueAsString(payload));
        result.put("isError", false);
        return result;
    }

    private JsonNode successResponse(JsonNode id, JsonNode result) {
        ObjectNode response = mapper.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.set("id", id == null ? mapper.nullNode() : id);
        response.set("result", result);
        return response;
    }

    private JsonNode errorResponse(JsonNode id, int code, String message, JsonNode data) {
        ObjectNode response = mapper.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.set("id", id == null ? mapper.nullNode() : id);
        ObjectNode error = response.putObject("error");
        error.put("code", code);
        error.put("message", message);
        if (data != null && !data.isNull()) {
            error.set("data", data);
        }
        return response;
    }

    private String textValue(JsonNode node, String fieldName) {
        JsonNode value = node == null ? null : node.get(fieldName);
        return value != null && value.isTextual() ? value.asText() : null;
    }

    private String firstText(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            String value = textValue(node, fieldName);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private Duration timeoutFrom(JsonNode node) {
        if (node != null && node.canConvertToLong()) {
            long seconds = node.asLong();
            if (seconds > 0) {
                return Duration.ofSeconds(seconds);
            }
        }
        return defaultTimeout;
    }

    private Path pathFrom(JsonNode node, Path fallback) {
        if (node != null && node.isTextual() && !node.asText().isBlank()) {
            return Path.of(node.asText());
        }
        return fallback;
    }

    private List<String> listFrom(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }

        List<String> values = new ArrayList<>();
        for (JsonNode item : node) {
            if (item.isTextual()) {
                values.add(item.asText());
            }
        }
        return values;
    }
}