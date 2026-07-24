package org.hubbers.react.execution;

import com.fasterxml.jackson.databind.JsonNode;

import lombok.Getter;
import lombok.Setter;

/**
 * Represents a single tool invocation within an agent's execution.
 */
@Getter
@Setter
public class ToolCallTrace {
    
    private String toolName;
    private JsonNode input;
    private JsonNode output;
    private long durationMs;
    private boolean success;
    private String error;

    public ToolCallTrace() {}

    public ToolCallTrace(String toolName, JsonNode input, JsonNode output, long durationMs, boolean success) {
        this.toolName = toolName;
        this.input = input;
        this.output = output;
        this.durationMs = durationMs;
        this.success = success;
    }
}
