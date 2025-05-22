package com.example.demo.hypernova;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HypernovaPojoTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    @Test
    void hypernovaJob_serializationDeserialization() throws JsonProcessingException {
        Map<String, Object> data = new HashMap<>();
        data.put("key1", "value1");
        data.put("key2", 123);
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("metaKey", "metaValue");

        HypernovaJob job = new HypernovaJob("TestComponent", data, metadata);

        String json = objectMapper.writeValueAsString(job);

        // Check @JsonInclude(JsonInclude.Include.NON_NULL) is somewhat tested by having values
        assertTrue(json.contains("TestComponent"));
        assertTrue(json.contains("key1"));
        assertTrue(json.contains("metaValue"));

        HypernovaJob deserializedJob = objectMapper.readValue(json, HypernovaJob.class);

        assertEquals(job.getName(), deserializedJob.getName());
        assertEquals(job.getData(), deserializedJob.getData());
        assertEquals(job.getMetadata(), deserializedJob.getMetadata());
        assertEquals(job, deserializedJob);
    }

    @Test
    void hypernovaJob_serialization_withNullFields() throws JsonProcessingException {
        HypernovaJob job = new HypernovaJob("TestComponent", null, null);
        String json = objectMapper.writeValueAsString(job);

        // Due to @JsonInclude(JsonInclude.Include.NON_NULL), 'data' and 'metadata' should be absent
        assertTrue(json.contains("TestComponent"));
        assertFalse(json.contains("\"data\":"));
        assertFalse(json.contains("\"metadata\":"));

        HypernovaJob deserializedJob = objectMapper.readValue(json, HypernovaJob.class);
        assertEquals("TestComponent", deserializedJob.getName());
        assertNull(deserializedJob.getData());
        assertNull(deserializedJob.getMetadata());
    }

    @Test
    void hypernovaJobResult_serializationDeserialization() throws JsonProcessingException {
        HypernovaJob originalJob = new HypernovaJob("Original", Collections.singletonMap("origKey", "origVal"), null);
        Map<String, Object> meta = Collections.singletonMap("resultMeta", "resultMetaVal");
        Map<String, Object> errorMap = new HashMap<>();
        errorMap.put("message", "An error occurred");
        errorMap.put("code", 500);

        HypernovaJobResult result = new HypernovaJobResult("<div>Rendered HTML</div>", errorMap, false, originalJob, meta);

        String json = objectMapper.writeValueAsString(result);
        HypernovaJobResult deserializedResult = objectMapper.readValue(json, HypernovaJobResult.class);

        assertEquals(result.getHtml(), deserializedResult.getHtml());
        assertNotNull(deserializedResult.getError());
        assertTrue(deserializedResult.getError() instanceof Map);
        assertEquals(errorMap, deserializedResult.getError());
        assertEquals(result.isSuccess(), deserializedResult.isSuccess());
        assertEquals(originalJob, deserializedResult.getOriginalJob());
        assertEquals(meta, deserializedResult.getMeta());
        assertEquals(result, deserializedResult);
    }

    @Test
    void hypernovaJobResult_withUnknownProperties() throws JsonProcessingException {
        String jsonWithUnknown = "{\"html\":\"Test\",\"success\":true,\"unknownField\":\"ignoreMe\"}";
        HypernovaJobResult deserializedResult = objectMapper.readValue(jsonWithUnknown, HypernovaJobResult.class);
        
        assertEquals("Test", deserializedResult.getHtml());
        assertTrue(deserializedResult.isSuccess());
        // No error should be thrown due to @JsonIgnoreProperties(ignoreUnknown = true)
    }

    @Test
    void hypernovaResponse_serializationDeserialization_withResultsAndError() throws JsonProcessingException {
        HypernovaJob originalJob1 = new HypernovaJob("Comp1", null, null);
        HypernovaJobResult jobResult1 = new HypernovaJobResult("<div>Comp1 HTML</div>", null, true, originalJob1, null);

        HypernovaJob originalJob2 = new HypernovaJob("Comp2", null, null);
        Map<String, Object> errorMap2 = Collections.singletonMap("msg", "Comp2 failed");
        HypernovaJobResult jobResult2 = new HypernovaJobResult("", errorMap2, false, originalJob2, null);

        Map<String, HypernovaJobResult> resultsMap = new HashMap<>();
        resultsMap.put("job1", jobResult1);
        resultsMap.put("job2", jobResult2);

        Map<String, Object> topLevelError = Collections.singletonMap("message", "Batch failed");
        HypernovaResponse response = new HypernovaResponse(resultsMap, topLevelError);

        String json = objectMapper.writeValueAsString(response);
        HypernovaResponse deserializedResponse = objectMapper.readValue(json, HypernovaResponse.class);

        assertNotNull(deserializedResponse.getResults());
        assertEquals(2, deserializedResponse.getResults().size());
        assertEquals(jobResult1, deserializedResponse.getResults().get("job1"));
        assertEquals(jobResult2, deserializedResponse.getResults().get("job2"));
        assertEquals(originalJob1, deserializedResponse.getResults().get("job1").getOriginalJob());

        assertNotNull(deserializedResponse.getError());
        assertTrue(deserializedResponse.getError() instanceof Map);
        assertEquals(topLevelError, deserializedResponse.getError());
        assertEquals(response, deserializedResponse);
    }

    @Test
    void hypernovaResponse_serializationDeserialization_emptyResultsNullError() throws JsonProcessingException {
        HypernovaResponse response = new HypernovaResponse(Collections.emptyMap(), null);
        String json = objectMapper.writeValueAsString(response);
        HypernovaResponse deserializedResponse = objectMapper.readValue(json, HypernovaResponse.class);

        assertNotNull(deserializedResponse.getResults());
        assertTrue(deserializedResponse.getResults().isEmpty());
        assertNull(deserializedResponse.getError());
        assertEquals(response, deserializedResponse);
    }
}
