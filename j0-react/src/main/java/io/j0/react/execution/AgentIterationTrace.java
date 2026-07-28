package io.j0.react.execution;

import com.fasterxml.jackson.databind.JsonNode;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a single iteration in an agent's ReAct loop.
 */
@Getter
@Setter
public class AgentIterationTrace {
    private int iterationNumber;
    private String reasoning;
    private List<ToolCallTrace> toolCalls = new ArrayList<>();
    private JsonNode result;
    private long durationMs;
    private boolean isComplete;

    public AgentIterationTrace() {}

    public AgentIterationTrace(int iterationNumber) {
        this.iterationNumber = iterationNumber;
    }
}
