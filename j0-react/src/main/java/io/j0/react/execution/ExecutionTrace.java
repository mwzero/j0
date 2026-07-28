package io.j0.react.execution;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

/**
 * Root execution trace containing all execution details for pipelines, agents, and skills.
 * Provides a hierarchical view of execution flow for post-execution analysis.
 */
@Getter
@Setter
public class ExecutionTrace {
    private String executionType; // "agent", "tool", "skill"
    private List<AgentIterationTrace> iterations = new ArrayList<>();
    private List<SkillInvocationTrace> skillInvocations = new ArrayList<>();
    private int totalIterations;
    private int totalSteps;

    public ExecutionTrace() {}

    public ExecutionTrace(String executionType) {
        this.executionType = executionType;
    }

    public void addIteration(AgentIterationTrace iteration) {
        this.iterations.add(iteration);
        this.totalIterations = this.iterations.size();
    }

    public void addSkillInvocation(SkillInvocationTrace skill) {
        this.skillInvocations.add(skill);
    }


}
