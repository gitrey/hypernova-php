package com.example.hypernovaclient.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class HypernovaModelTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    @Test
    void testHypernovaJobSerializationDeserialization() throws JsonProcessingException {
        Map<String, Object> data = Map.of("key", "value");
        Map<String, Object> metadata = Map.of("metaKey", "metaValue");
        HypernovaJob job = new HypernovaJob("MyComponent", data, metadata);

        String json = objectMapper.writeValueAsString(job);

        assertTrue(json.contains("\"name\":\"MyComponent\""));
        assertTrue(json.contains("\"data\":{\"key\":\"value\"}"));
        assertTrue(json.contains("\"metadata\":{\"metaKey\":\"metaValue\"}"));

        HypernovaJob deserializedJob = objectMapper.readValue(json, HypernovaJob.class);

        assertEquals("MyComponent", deserializedJob.getName());
        assertEquals(data, deserializedJob.getData());
        assertEquals(metadata, deserializedJob.getMetadata());
    }

    @Test
    void testHypernovaResponseDeserialization_Success() throws JsonProcessingException {
        String serverResponseJson = "{" +
                "\"results\": {" +
                "  \"job1\": {" +
                "    \"html\": \"<div>Hello</div>\"," +
                "    \"error\": null," +
                "    \"meta\": {\"source\": \"cache\"}," +
                "    \"success\": true," +
                "    \"duration\": 10.5," +
                "    \"view_name\": \"MyComponent\"" + // Note: view_name is typically "name" in the request
                "  }" +
                "}," +
                "\"error\": null" +
                "}";

        HypernovaResponse response = objectMapper.readValue(serverResponseJson, HypernovaResponse.class);

        assertNull(response.getError());
        assertNotNull(response.getResults());
        assertEquals(1, response.getResults().size());

        HypernovaJobResult jobResult = response.getResults().get("job1");
        assertNotNull(jobResult);
        assertEquals("<div>Hello</div>", jobResult.getHtml());
        assertNull(jobResult.getError());
        assertNotNull(jobResult.getMeta());
        assertEquals("cache", jobResult.getMeta().get("source"));
        assertTrue(jobResult.isSuccess());
        assertEquals(10.5, jobResult.getDuration());
        assertEquals("MyComponent", jobResult.getViewName()); // Jackson maps "view_name" to "viewName"
        assertNull(jobResult.getOriginalJob()); // Should be null as it's @JsonIgnore
    }

    @Test
    void testHypernovaResponseDeserialization_JobError() throws JsonProcessingException {
        String serverResponseJson = "{" +
                "\"results\": {" +
                "  \"jobError\": {" +
                "    \"html\": null," +
                "    \"error\": {\"message\": \"Component failed\", \"stack\": [\"line1\", \"line2\"]}," +
                "    \"meta\": {}," +
                "    \"success\": false," +
                "    \"duration\": 2.0," +
                "    \"view_name\": \"ErrorComponent\"" +
                "  }" +
                "}," +
                "\"error\": null" +
                "}";

        HypernovaResponse response = objectMapper.readValue(serverResponseJson, HypernovaResponse.class);

        assertNull(response.getError());
        assertNotNull(response.getResults());
        assertEquals(1, response.getResults().size());

        HypernovaJobResult jobResult = response.getResults().get("jobError");
        assertNotNull(jobResult);
        assertNull(jobResult.getHtml());
        assertNotNull(jobResult.getError());
        assertTrue(jobResult.getError() instanceof Map);
        Map<?,?> errorMap = (Map<?,?>) jobResult.getError();
        assertEquals("Component failed", errorMap.get("message"));
        assertTrue(errorMap.get("stack") instanceof java.util.List);
        assertEquals(2, ((java.util.List<?>)errorMap.get("stack")).size());
        assertFalse(jobResult.isSuccess());
        assertEquals(2.0, jobResult.getDuration());
        assertEquals("ErrorComponent", jobResult.getViewName());

        // Manually set and get originalJob
        HypernovaJob originalJob = new HypernovaJob("ErrorComponent", Collections.emptyMap(), Collections.emptyMap());
        jobResult.setOriginalJob(originalJob);
        assertEquals(originalJob, jobResult.getOriginalJob());
    }

     @Test
    void testHypernovaResponseDeserialization_BatchError() throws JsonProcessingException {
        String serverResponseJson = "{" +
                "\"results\": {}," +
                "\"error\": {\"message\": \"Batch request failed\", \"stack\": [\"batch line1\"]}" +
                "}";

        HypernovaResponse response = objectMapper.readValue(serverResponseJson, HypernovaResponse.class);

        assertNotNull(response.getError());
        assertTrue(response.getError() instanceof Map);
        Map<?,?> errorMap = (Map<?,?>) response.getError();
        assertEquals("Batch request failed", errorMap.get("message"));
        assertNotNull(response.getResults());
        assertTrue(response.getResults().isEmpty());
    }
}
