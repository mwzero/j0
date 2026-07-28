package io.j0.react.execution;

import lombok.Getter;
import lombok.Setter;

/**
 * Metadata captured during artifact execution, including timing and token usage.
 */
@Getter
@Setter
public class ExecutionMetadata {
    private long startedAt;
    private long endedAt;
    private String details;
    private long promptTokens;
    private long completionTokens;
    private long totalTokens;


    /**
     * Adds token counts from a model response to the running total.
     *
     * @param prompt     prompt tokens used
     * @param completion completion tokens used
     */
    public void addTokenUsage(long prompt, long completion) {
        this.promptTokens += prompt;
        this.completionTokens += completion;
        this.totalTokens += (prompt + completion);
    }
}
