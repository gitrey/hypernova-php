package com.example.hypernova;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class JobTest {

    @Test
    void constructor_shouldSetFieldsCorrectly() {
        String name = "TestComponent";
        Map<String, Object> data = Map.of("key1", "value1", "key2", 123);
        Map<String, String> metadata = Map.of("metaKey", "metaValue");

        Job job = new Job(name, data, metadata);

        assertEquals(name, job.getName());
        assertEquals(data, job.getData());
        assertEquals(metadata, job.getMetadata());
    }

    @Test
    void constructor_shouldHandleNullDataAndMetadata() {
        String name = "TestComponent";
        Job job = new Job(name, null, null);

        assertEquals(name, job.getName());
        assertNull(job.getData());
        assertNull(job.getMetadata());
    }

    @Test
    void jsonSerialization_shouldProduceCorrectJson() throws JsonProcessingException {
        String name = "MyComponent";
        Map<String, Object> data = Map.of("title", "Hello World", "count", 5);
        Map<String, String> metadata = Map.of("request_id", "12345-abc");
        Job job = new Job(name, data, metadata);

        ObjectMapper objectMapper = new ObjectMapper();
        String json = objectMapper.writeValueAsString(job);

        // Basic checks for field presence and values
        assertTrue(json.contains("\"name\":\"MyComponent\""), "JSON should contain name");
        assertTrue(json.contains("\"data\":{"), "JSON should contain data object");
        assertTrue(json.contains("\"title\":\"Hello World\""), "JSON should contain data.title");
        assertTrue(json.contains("\"count\":5"), "JSON should contain data.count");
        assertTrue(json.contains("\"metadata\":{"), "JSON should contain metadata object");
        assertTrue(json.contains("\"request_id\":\"12345-abc\""), "JSON should contain metadata.request_id");

        // More robust check: Deserialize and compare
        // This requires Job to have a default constructor or for ObjectMapper to be configured,
        // or to parse into a Map. For simplicity, we'll parse to a Map.
        @SuppressWarnings("unchecked")
        Map<String, Object> parsedJson = objectMapper.readValue(json, Map.class);

        assertEquals(name, parsedJson.get("name"));
        assertNotNull(parsedJson.get("data"));
        assertTrue(parsedJson.get("data") instanceof Map);
        assertEquals(data, parsedJson.get("data"));

        assertNotNull(parsedJson.get("metadata"));
        assertTrue(parsedJson.get("metadata") instanceof Map);
        assertEquals(metadata, parsedJson.get("metadata"));
    }

    @Test
    void jsonSerialization_withEmptyDataAndMetadata_shouldProduceCorrectJson() throws JsonProcessingException {
        String name = "SimpleComponent";
        Job job = new Job(name, Collections.emptyMap(), Collections.emptyMap());

        ObjectMapper objectMapper = new ObjectMapper();
        String json = objectMapper.writeValueAsString(job);

        assertTrue(json.contains("\"name\":\"SimpleComponent\""));
        assertTrue(json.contains("\"data\":{}"));
        assertTrue(json.contains("\"metadata\":{}"));
    }
}
