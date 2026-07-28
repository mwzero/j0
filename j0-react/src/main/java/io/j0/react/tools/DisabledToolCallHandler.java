package io.j0.react.tools;

import com.fasterxml.jackson.databind.JsonNode;

import io.j0.react.execution.RunResult;

/**
 * Fallback handler used when tool_library is not configured.
 * Any tool invocation is rejected with a clear runtime error.
 */
public final class DisabledToolCallHandler implements ToolCallHandler {

    @Override
    public RunResult handle(String artifactName, JsonNode arguments) {
        return RunResult.failed("Tool execution is disabled: tool_library is not configured in agent.yaml");
    }
}
