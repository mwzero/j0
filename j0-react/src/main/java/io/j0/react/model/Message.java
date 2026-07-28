package io.j0.react.model;

import java.util.List;

import lombok.Getter;
import lombok.ToString;

/**
 * Represents a message in a conversation.
 * Used for multi-turn conversations and conversation history.
 */
@ToString
@Getter
public class Message {

    private final String role;       // "system", "user", "assistant", "tool"
    private final String content;
    private final String toolCallId; // for tool-response messages
    private final String name;       // for tool messages
    private final List<FunctionCall> toolCalls; // for assistant messages with function calls

    private Message(String role, String content, String toolCallId, String name, List<FunctionCall> toolCalls) {
        this.role = role;
        this.content = content;
        this.toolCallId = toolCallId;
        this.name = name;
        this.toolCalls = toolCalls;
    }

    public static Message system(String content) {
        return new Message("system", content, null, null, null);
    }

    public static Message user(String content) {
        return new Message("user", content, null, null, null);
    }

    public static Message assistant(String content) {
        return new Message("assistant", content, null, null, null);
    }

    /**
     * Creates an assistant message that includes tool/function calls.
     * Required by Ollama/OpenAI APIs to maintain proper conversation flow.
     *
     * @param content   the assistant's text content (may be empty)
     * @param toolCalls the function calls made by the assistant
     * @return assistant message with tool calls
     */
    public static Message assistantWithToolCalls(String content, List<FunctionCall> toolCalls) {
        return new Message("assistant", content, null, null, toolCalls);
    }

    public static Message tool(String toolCallId, String name, String content) {
        return new Message("tool", content, toolCallId, name, null);
    }
}
