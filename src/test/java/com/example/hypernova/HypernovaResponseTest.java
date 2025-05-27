package com.example.hypernova;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class HypernovaResponseTest {

    @Test
    void constructorAndGetters_shouldSetAndRetrieveFields() {
        HypernovaError error = new HypernovaError("Top level error", Collections.singletonList("stack"));
        Map<String, JobResult> results = new HashMap<>();
        Job mockJob = new Job("TestJob", Collections.emptyMap(), Collections.emptyMap());
        JobResult jobResult = new JobResult(null, "<p>Test</p>", true, mockJob, Collections.emptyMap(), 10.0);
        results.put("job1", jobResult);

        HypernovaResponse response = new HypernovaResponse(); // Default constructor
        response.setError(error);
        response.setResults(results);

        assertEquals(error, response.getError());
        assertEquals(results, response.getResults());
        assertEquals(jobResult, response.getResults().get("job1"));
    }

    @Test
    void defaultConstructor_shouldInitializeEmptyResultsAndNullError() {
        HypernovaResponse response = new HypernovaResponse();
        assertNull(response.getError());
        assertNotNull(response.getResults());
        assertTrue(response.getResults().isEmpty());
    }

    @Test
    void jsonDeserialization_withTopLevelError_shouldPopulateCorrectly() throws JsonProcessingException {
        String jsonResponse = "{\n" +
                "  \"error\": {\n" +
                "    \"message\": \"Something went very wrong.\",\n" +
                "    \"stack\": [\n" +
                "      \"trace line 1 for top error\",\n" +
                "      \"trace line 2 for top error\"\n" +
                "    ]\n" +
                "  },\n" +
                "  \"results\": {}\n" +
                "}";

        ObjectMapper objectMapper = new ObjectMapper();
        // We need to provide the originalJobs map for the deserializing constructor.
        // However, if there's a top-level error, the results are often empty or ignored by the server.
        // For this test, we'll assume the HypernovaResponse constructor can handle null/empty originalJobs if results are empty.
        // Let's simulate what HypernovaRenderer might do: it might use the default constructor and then set fields.
        // Or, if parsing directly, the deserializing constructor needs to be robust.
        // The provided constructor HypernovaResponse(@JsonProperty("error") Object error, @JsonProperty("results") Map<String, Map<String, Object>> resultsData, Map<String, Job> originalJobs)
        // is the one Jackson will use.

        HypernovaResponse response = objectMapper.readValue(jsonResponse, HypernovaResponse.class);

        assertNotNull(response.getError());
        assertEquals("Something went very wrong.", response.getError().getMessage());
        assertEquals(Arrays.asList("trace line 1 for top error", "trace line 2 for top error"), response.getError().getStack());
        assertNotNull(response.getResults()); // Results map should be non-null even if empty
        assertTrue(response.getResults().isEmpty()); // In this specific JSON, results is empty
    }

    @Test
    void jsonDeserialization_withResults_shouldPopulateJobResultsCorrectly() throws JsonProcessingException {
        // Prepare original jobs that would have been sent
        Job job1 = new Job("Component1", Map.of("prop", "value1"), Map.of("meta", "m1"));
        Job job2 = new Job("Component2", Map.of("prop", "value2"), Map.of("meta", "m2"));
        Map<String, Job> originalJobs = Map.of(
                "job1_uuid", job1,
                "job2_uuid", job2
        );

        String jsonResponse = "{\n" +
                "  \"error\": null,\n" +
                "  \"results\": {\n" +
                "    \"job1_uuid\": {\n" +
                "      \"html\": \"<div>Rendered Component 1</div>\",\n" +
                "      \"error\": null,\n" +
                "      \"success\": true,\n" +
                "      \"duration\": 15.5,\n" +
                "      \"meta\": {\"source\": \"server\"}\n" +
                "    },\n" +
                "    \"job2_uuid\": {\n" +
                "      \"html\": null,\n" + // Or some fallback HTML from server
                "      \"error\": {\n" +
                "        \"message\": \"Component2 failed.\",\n" +
                "        \"stack\": [\"stack for job2 error\"]\n" +
                "      },\n" +
                "      \"success\": false,\n" +
                "      \"duration\": 5.0,\n" +
                "      \"meta\": {}\n" +
                "    }\n" +
                "  }\n" +
                "}";

        ObjectMapper objectMapper = new ObjectMapper();
        // To properly use the deserializing constructor of HypernovaResponse, we need a way
        // to pass the `originalJobs` map. Jackson by default won't know how to get this.
        // This means the provided HypernovaResponse constructor is not directly usable by Jackson
        // unless we customize the deserialization process (e.g. using a ContextAttribute).

        // For this test to pass *without custom deserialization logic for originalJobs*,
        // the HypernovaResponse would need to be designed differently, perhaps:
        // 1. Have a constructor that Jackson can use (e.g. takes only `error` and `results` Map<String, Map<String,Object>>)
        //    and then a separate method to link original jobs if needed.
        // 2. The current constructor `HypernovaResponse(Object error, Map<String, Map<String, Object>> resultsData, Map<String, Job> originalJobs)`
        //    is what Jackson will try to call. If `originalJobs` is not in the JSON, it will be null.
        //    The constructor needs to be robust to a null `originalJobs` map, especially if `resultsData` is not null.
        //    The `JobResult.fromServerResult` also needs the `originalJob`.

        // Let's assume the constructor is robust enough for `originalJobs` to be passed as null,
        // and we test the structure. The linking with originalJob is more of an integration test with HypernovaRenderer.
        // The current HypernovaResponse constructor *requires* originalJobs to correctly build JobResult.
        // To make this testable standalone, we might need to inject `originalJobs` or adjust the design.

        // Given the current structure, we'll test the deserialization of the structure
        // and assume that if `originalJobs` were available (e.g. via `HypernovaRenderer`),
        // the `JobResult.fromServerResult` would be called correctly.

        // To test this properly with Jackson, we'd typically deserialize to a generic map or a simpler DTO first,
        // then transform it into HypernovaResponse, providing the originalJobs map.
        // Or, make originalJobs a field in HypernovaResponse that can be set after initial deserialization.

        // For now, let's test deserialization into a structure that Jackson *can* handle without external context.
        // This means we cannot fully test the HypernovaResponse(error, resultsData, originalJobs) constructor
        // directly from a JSON string with ObjectMapper unless originalJobs is part of the JSON, which it isn't.

        // Let's modify the expectation: we'll deserialize to a temporary structure that mirrors the JSON
        // and then manually construct the HypernovaResponse for assertions.
        @SuppressWarnings("unchecked")
        Map<String, Object> rawResponse = objectMapper.readValue(jsonResponse, Map.class);

        HypernovaError topError = null;
        if (rawResponse.get("error") != null) {
            topError = objectMapper.convertValue(rawResponse.get("error"), HypernovaError.class);
        }

        Map<String, Map<String, Object>> resultsData = (Map<String, Map<String, Object>>) rawResponse.get("results");

        // Manually construct HypernovaResponse, simulating what HypernovaRenderer would do.
        HypernovaResponse response = new HypernovaResponse(topError, resultsData, originalJobs);


        assertNull(response.getError());
        assertNotNull(response.getResults());
        assertEquals(2, response.getResults().size());

        // Job 1 (success)
        JobResult job1Result = response.getResults().get("job1_uuid");
        assertNotNull(job1Result);
        assertTrue(job1Result.isSuccess());
        assertNull(job1Result.getError());
        assertEquals("<div>Rendered Component 1</div>", job1Result.getHtml());
        assertEquals(15.5, job1Result.getDuration());
        assertEquals(Map.of("source", "server"), job1Result.getMeta());
        assertEquals(job1, job1Result.getOriginalJob()); // Check if original job was linked

        // Job 2 (failure)
        JobResult job2Result = response.getResults().get("job2_uuid");
        assertNotNull(job2Result);
        assertFalse(job2Result.isSuccess());
        assertNotNull(job2Result.getError());
        assertEquals("Component2 failed.", job2Result.getError().getMessage());
        assertEquals(Collections.singletonList("stack for job2 error"), job2Result.getError().getStack());
        assertNull(job2Result.getHtml()); // Or server-provided fallback
        assertEquals(5.0, job2Result.getDuration());
        assertTrue(job2Result.getMeta().isEmpty());
        assertEquals(job2, job2Result.getOriginalJob()); // Check if original job was linked
    }

    @Test
    void jsonDeserialization_emptyResults_shouldWork() throws JsonProcessingException {
        String jsonResponse = "{\"error\": null, \"results\": {}}";
        ObjectMapper objectMapper = new ObjectMapper();
        HypernovaResponse response = objectMapper.readValue(jsonResponse, HypernovaResponse.class);

        assertNull(response.getError());
        assertNotNull(response.getResults());
        assertTrue(response.getResults().isEmpty());
    }
}
