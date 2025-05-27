package com.example.hypernova;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class HypernovaErrorTest {

    @Test
    void constructor_shouldSetFieldsCorrectly() {
        String message = "Something went wrong";
        List<String> stack = Arrays.asList("stacktrace line 1", "stacktrace line 2");

        HypernovaError error = new HypernovaError(message, stack);

        assertEquals(message, error.getMessage());
        assertEquals(stack, error.getStack());
    }

    @Test
    void constructor_shouldHandleNullStack() {
        String message = "Error without stack";
        HypernovaError error = new HypernovaError(message, null);

        assertEquals(message, error.getMessage());
        assertNull(error.getStack());
    }

    @Test
    void jsonSerializationAndDeserialization_shouldWorkCorrectly() throws JsonProcessingException {
        String message = "Detailed error message";
        List<String> stack = Arrays.asList("at com.example.Class.method(Class.java:10)", "at com.example.AnotherClass.anotherMethod(AnotherClass.java:20)");
        HypernovaError originalError = new HypernovaError(message, stack);

        ObjectMapper objectMapper = new ObjectMapper();

        // Serialize
        String json = objectMapper.writeValueAsString(originalError);

        // Basic checks for field presence and values
        assertTrue(json.contains("\"message\":\"Detailed error message\""), "JSON should contain message");
        assertTrue(json.contains("\"stack\":["), "JSON should contain stack array");
        assertTrue(json.contains("\"at com.example.Class.method(Class.java:10)\""), "JSON should contain stack trace line 1");
        assertTrue(json.contains("\"at com.example.AnotherClass.anotherMethod(AnotherClass.java:20)\""), "JSON should contain stack trace line 2");

        // Deserialize
        HypernovaError deserializedError = objectMapper.readValue(json, HypernovaError.class);

        // Assert fields are equal
        assertEquals(originalError.getMessage(), deserializedError.getMessage());
        assertEquals(originalError.getStack(), deserializedError.getStack());
    }

    @Test
    void jsonDeserialization_withNullStack_shouldWorkCorrectly() throws JsonProcessingException {
        String message = "Error with null stack in JSON";
        // Note: In Jackson, a null List might be serialized as `null` or omitted.
        // If omitted, the field in the deserialized object will be null.
        // If serialized as `null`, it will also be deserialized as null.
        String jsonInput = "{\"message\":\"Error with null stack in JSON\", \"stack\":null}";
        HypernovaError originalError = new HypernovaError(message, null);


        ObjectMapper objectMapper = new ObjectMapper();
        HypernovaError deserializedError = objectMapper.readValue(jsonInput, HypernovaError.class);

        assertEquals(message, deserializedError.getMessage());
        assertNull(deserializedError.getStack());
    }

     @Test
    void jsonDeserialization_withMissingStack_shouldResultInNullStack() throws JsonProcessingException {
        String message = "Error with missing stack in JSON";
        String jsonInput = "{\"message\":\"Error with missing stack in JSON\"}";

        ObjectMapper objectMapper = new ObjectMapper();
        HypernovaError deserializedError = objectMapper.readValue(jsonInput, HypernovaError.class);

        assertEquals(message, deserializedError.getMessage());
        assertNull(deserializedError.getStack(), "Stack should be null if missing in JSON");
    }
}
