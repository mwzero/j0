package io.j0.react.model;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;
import lombok.ToString;
/**
 * Represents a function call request from the LLM to execute a tool or artifact.
 * This is returned by models that support function calling.
 */
@Getter
@ToString
public class FunctionCall {

    private final String id;
    private final String name;      // references an artifact name
    private final JsonNode arguments;

    public FunctionCall(String id, String name, JsonNode arguments) {
        this.id = id;
        this.name = name;
        this.arguments = arguments;
    }

}
