package org.hubbers.react.execution;

import com.fasterxml.jackson.databind.JsonNode;

import lombok.Getter;
import lombok.Setter;

/**
 * Represents a single step in a pipeline execution.
 */
@Getter
@Setter
public class PipelineStepTrace {
    private int stepNumber;
    private String stepName;
    private String artifactType; // "agent", "tool", "pipeline", "skill"
    private String artifactName;
    private ExecutionStatus status;
    private JsonNode input;
    private JsonNode output;
    private long startTime;
    private long endTime;
    private long durationMs;
    private String error;

    public PipelineStepTrace() {}

    public PipelineStepTrace(int stepNumber, String stepName, String artifactType, String artifactName) {
        this.stepNumber = stepNumber;
        this.stepName = stepName;
        this.artifactType = artifactType;
        this.artifactName = artifactName;
    }
}
