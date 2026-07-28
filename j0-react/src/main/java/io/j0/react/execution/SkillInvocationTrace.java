package io.j0.react.execution;

import lombok.Getter;
import lombok.Setter;

/**
 * Represents a single skill invocation during agent or pipeline execution.
 */
@Getter
@Setter
public class SkillInvocationTrace {
    private String skillName;
    private String input;
    private String output;
    private long durationMs;
    private boolean success;
    private String error;

    public SkillInvocationTrace() {}

    public SkillInvocationTrace(String skillName) {
        this.skillName = skillName;
    }
}
