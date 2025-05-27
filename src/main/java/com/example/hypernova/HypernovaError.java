package com.example.hypernova;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Represents an error object, typically from a Hypernova server response or a client-side issue.
 * It includes an error message and an optional stack trace.
 */
public class HypernovaError {
    private final String message;
    private final List<String> stack;

    /**
     * Constructs a new HypernovaError.
     *
     * @param message The error message.
     * @param stack   A list of strings representing the stack trace. Can be null or empty.
     */
    public HypernovaError(
            @JsonProperty("message") String message,
            @JsonProperty("stack") List<String> stack) {
        this.message = message;
        this.stack = stack;
    }

    /**
     * Gets the error message.
     * @return The error message.
     */
    public String getMessage() {
        return message;
    }

    /**
     * Gets the stack trace associated with the error.
     * @return A list of strings representing the stack trace, or null if not available.
     */
    public List<String> getStack() {
        return stack;
    }
}
