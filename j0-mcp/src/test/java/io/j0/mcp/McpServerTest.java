package io.j0.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Duration;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.j0.code.JavaCodeExecutor;

class McpServerTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void initialize_returns_server_info() throws Exception {
        McpServer server = new McpServer(mapper, new JavaCodeExecutor(), Path.of("build", "mcp-test"), Duration.ofSeconds(5));

        ObjectNode request = mapper.createObjectNode();
        request.put("jsonrpc", "2.0");
        request.put("id", 1);
        request.put("method", "initialize");

        JsonNode response = server.handleRequest(request);

        assertEquals("2.0", response.path("jsonrpc").asText());
        assertEquals(1, response.path("id").asInt());
        assertEquals(McpServer.PROTOCOL_VERSION, response.path("result").path("protocolVersion").asText());
        assertEquals(McpServer.SERVER_NAME, response.path("result").path("serverInfo").path("name").asText());
    }

    @Test
    void tools_list_exposes_java_compile_and_run() throws Exception {
        McpServer server = new McpServer(mapper, new JavaCodeExecutor(), Path.of("build", "mcp-test"), Duration.ofSeconds(5));

        ObjectNode request = mapper.createObjectNode();
        request.put("jsonrpc", "2.0");
        request.put("id", 2);
        request.put("method", "tools/list");

        JsonNode response = server.handleRequest(request);

        assertEquals(McpServer.TOOL_NAME, response.path("result").path("tools").get(0).path("name").asText());
        assertTrue(response.path("result").path("tools").get(0).path("inputSchema").path("required").isArray());
    }

    @Test
    void tools_call_compiles_and_runs_java_source() throws Exception {
        McpServer server = new McpServer(mapper, new JavaCodeExecutor(), Path.of("build", "mcp-test-run"), Duration.ofSeconds(10));

        ObjectNode arguments = mapper.createObjectNode();
        arguments.put("source", "public class HelloMcp { public static void main(String[] args) { System.out.println(\"hello mcp\"); } }");

        ObjectNode params = mapper.createObjectNode();
        params.put("name", McpServer.TOOL_NAME);
        params.set("arguments", arguments);

        ObjectNode request = mapper.createObjectNode();
        request.put("jsonrpc", "2.0");
        request.put("id", 3);
        request.put("method", "tools/call");
        request.set("params", params);

        JsonNode response = server.handleRequest(request);

        assertFalse(response.path("result").path("isError").asBoolean());
        String text = response.path("result").path("content").get(0).path("text").asText();
        assertTrue(text.contains("hello mcp"));
        assertTrue(text.contains("exitCode"));
    }
}