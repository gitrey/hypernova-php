package com.example.hypernovaclient;

import com.example.hypernovaclient.config.HypernovaClientProperties;
import com.example.hypernovaclient.model.HypernovaJob;
import com.example.hypernovaclient.model.HypernovaJobResult;
import com.example.hypernovaclient.model.HypernovaResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

@SpringBootTest(properties = {
    "hypernova.client.url=http://localhost:9000/test-batch" // Configure a test URL
})
public class HypernovaRendererTest {

    @Autowired
    private HypernovaRenderer hypernovaRenderer;

    @Autowired
    private RestTemplate hypernovaRestTemplate; // The RestTemplate bean used by HypernovaRenderer

    @Autowired
    private ObjectMapper hypernovaObjectMapper; // The ObjectMapper bean

    @Autowired
    private HypernovaClientProperties properties; // To get the configured URL

    private MockRestServiceServer mockServer;

    @BeforeEach
    void setUp() {
        // Create a mock server for the RestTemplate that is autowired and used by HypernovaRenderer
        mockServer = MockRestServiceServer.createServer(hypernovaRestTemplate);
    }

    @Test
    void testRender_SuccessfulScenario() throws JsonProcessingException {
        String jobKey = "job1";
        HypernovaJob job = new HypernovaJob("MyTestComponent", Map.of("name", "Tester"), Collections.emptyMap());
        hypernovaRenderer.addJob(jobKey, job);

        Map<String, HypernovaJob> expectedRequestPayload = Map.of(jobKey, job);
        String expectedRequestJson = hypernovaObjectMapper.writeValueAsString(expectedRequestPayload);

        String successfulJsonResponse = "{" +
                "\"results\": {" +
                "  \"" + jobKey + "\": {" +
                "    \"html\": \"<div>Hello Tester</div>\"," +
                "    \"error\": null," +
                "    \"meta\": {}," +
                "    \"success\": true," +
                "    \"duration\": 12.0," +
                "    \"view_name\": \"MyTestComponent\"" +
                "  }" +
                "}," +
                "\"error\": null" +
                "}";

        mockServer.expect(ExpectedCount.once(), requestTo(properties.getUrl()))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json(expectedRequestJson))
                .andRespond(withSuccess(successfulJsonResponse, MediaType.APPLICATION_JSON));

        HypernovaResponse response = hypernovaRenderer.render();

        assertNotNull(response);
        assertNull(response.getError(), "Top-level error should be null");
        assertNotNull(response.getResults());
        assertEquals(1, response.getResults().size());

        HypernovaJobResult jobResult = response.getResults().get(jobKey);
        assertNotNull(jobResult);
        assertTrue(jobResult.isSuccess());
        assertEquals("<div>Hello Tester</div>", jobResult.getHtml());
        assertNull(jobResult.getError());
        assertEquals("MyTestComponent", jobResult.getViewName());
        assertNotNull(jobResult.getOriginalJob());
        assertEquals("MyTestComponent", jobResult.getOriginalJob().getName());

        mockServer.verify();
    }

    @Test
    void testRender_JobWithErrorFromServer() throws JsonProcessingException {
        String jobKey = "errorJob1";
        HypernovaJob job = new HypernovaJob("FailingComponent", Map.of("id", 123), Collections.emptyMap());
        hypernovaRenderer.addJob(jobKey, job);

        Map<String, HypernovaJob> expectedRequestPayload = Map.of(jobKey, job);
        String expectedRequestJson = hypernovaObjectMapper.writeValueAsString(expectedRequestPayload);

        Map<String, Object> errorDetailsMap = Map.of("message", "Component failed to render", "stack", List.of("trace line 1"));

        // Constructing the JSON string for the job result part
        HypernovaJobResult errorJobResultFromServer = new HypernovaJobResult();
        errorJobResultFromServer.setHtml(null); // Or some server-side fallback HTML
        errorJobResultFromServer.setError(errorDetailsMap);
        errorJobResultFromServer.setMeta(Collections.emptyMap());
        errorJobResultFromServer.setSuccess(false);
        errorJobResultFromServer.setDuration(5.5);
        errorJobResultFromServer.setViewName("FailingComponent"); // Server might still provide view_name

        Map<String, HypernovaJobResult> resultsMap = Map.of(jobKey, errorJobResultFromServer);
        HypernovaResponse serverErrorResponse = new HypernovaResponse();
        serverErrorResponse.setResults(resultsMap);
        serverErrorResponse.setError(null);
        String errorJsonResponse = hypernovaObjectMapper.writeValueAsString(serverErrorResponse);


        mockServer.expect(ExpectedCount.once(), requestTo(properties.getUrl()))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json(expectedRequestJson))
                .andRespond(withSuccess(errorJsonResponse, MediaType.APPLICATION_JSON));

        HypernovaResponse response = hypernovaRenderer.render();

        assertNotNull(response);
        assertNull(response.getError());
        assertNotNull(response.getResults());
        assertEquals(1, response.getResults().size());

        HypernovaJobResult jobResult = response.getResults().get(jobKey);
        assertNotNull(jobResult);
        assertFalse(jobResult.isSuccess());
        assertNotNull(jobResult.getError());
        assertTrue(jobResult.getError() instanceof Map);
        assertEquals(errorDetailsMap, jobResult.getError());
        // Fallback HTML should be generated by HypernovaRenderer if server sends null HTML for an error
        // (and no plugin like DevModePlugin is active to change it, which it isn't here by default)
        assertTrue(jobResult.getHtml().contains("data-hypernova-key=\"FailingComponent\""), "Fallback HTML expected");
        assertEquals("FailingComponent", jobResult.getViewName());
        assertNotNull(jobResult.getOriginalJob());

        mockServer.verify();
    }

    @Test
    void testRender_Http500Error() throws JsonProcessingException {
        String jobKey = "httpErrorJob";
        HypernovaJob job = new HypernovaJob("SomeComponent", Map.of("data", "value"), Collections.emptyMap());
        hypernovaRenderer.addJob(jobKey, job);

        Map<String, HypernovaJob> expectedRequestPayload = Map.of(jobKey, job);
        String expectedRequestJson = hypernovaObjectMapper.writeValueAsString(expectedRequestPayload);

        mockServer.expect(ExpectedCount.once(), requestTo(properties.getUrl()))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json(expectedRequestJson))
                .andRespond(withServerError().body("Internal Server Error").contentType(MediaType.TEXT_PLAIN));

        HypernovaResponse response = hypernovaRenderer.render();

        assertNotNull(response);
        assertNotNull(response.getError(), "Top-level error should be populated for HTTP 500");
        assertTrue(response.getError().toString().contains("500"), "Error message should reflect HTTP 500 status");

        assertNotNull(response.getResults());
        assertEquals(1, response.getResults().size());

        HypernovaJobResult jobResult = response.getResults().get(jobKey);
        assertNotNull(jobResult);
        assertFalse(jobResult.isSuccess());
        assertNotNull(jobResult.getError()); // Job-level error should also be populated
        assertTrue(jobResult.getHtml().contains("data-hypernova-key=\"SomeComponent\""), "Fallback HTML expected for job");
        assertEquals("SomeComponent", jobResult.getViewName());
        assertNotNull(jobResult.getOriginalJob());

        mockServer.verify();
    }

    @Test
    void testRender_BatchErrorInJsonResponse() throws JsonProcessingException {
        String jobKey = "jobWithBatchError";
        HypernovaJob job = new HypernovaJob("AnyComponent", Map.of("test", "data"), Collections.emptyMap());
        hypernovaRenderer.addJob(jobKey, job); // Add a job so a request is made

        Map<String, Object> batchErrorDetails = Map.of("message", "A top-level batch error occurred", "code", "BATCH_FAILURE");
        HypernovaResponse batchErrorResponse = new HypernovaResponse();
        batchErrorResponse.setResults(new HashMap<>()); // Typically empty or partial if some succeeded before error
        batchErrorResponse.setError(batchErrorDetails);

        String batchErrorResponseJson = hypernovaObjectMapper.writeValueAsString(batchErrorResponse);

        mockServer.expect(ExpectedCount.once(), requestTo(properties.getUrl()))
                .andExpect(method(HttpMethod.POST))
                // Content check might be omitted if not crucial for this specific error test
                .andRespond(withSuccess(batchErrorResponseJson, MediaType.APPLICATION_JSON));

        HypernovaResponse response = hypernovaRenderer.render();

        assertNotNull(response);
        assertNotNull(response.getError(), "Top-level error should be populated from JSON response");
        assertEquals(batchErrorDetails, response.getError());

        assertNotNull(response.getResults());
        // Even with a batch error, individual job results might be present if the server processes that way,
        // or they might be generated as fallbacks by the client.
        // HypernovaRenderer generates fallbacks for jobs that were part of the batch if a top-level error occurs.
        assertTrue(response.getResults().containsKey(jobKey), "Job should have a fallback result due to batch error");
        HypernovaJobResult jobResult = response.getResults().get(jobKey);
        assertNotNull(jobResult);
        assertFalse(jobResult.isSuccess());
        assertNotNull(jobResult.getError(), "Job result should indicate an error due to the batch error");
        assertTrue(jobResult.getHtml().contains("data-hypernova-key=\"AnyComponent\""), "Fallback HTML expected for job");

        // TODO: Add verification for onBatchError plugin hook if a test plugin is registered
        // This would require autowiring a mock/spy plugin or using a ApplicationContextRunner approach.

        mockServer.verify();
    }

    @Test
    void testRender_EmptyJobsList() {
        // Ensure jobsToRender is empty by not calling addJob or by calling clear (render clears it)
        // If render was just called, jobsToRender is already empty.
        // HypernovaResponse previousResponse = hypernovaRenderer.render(); // to clear any existing jobs if necessary

        HypernovaResponse response = hypernovaRenderer.render(); // Call render with no jobs

        assertNotNull(response);
        assertNull(response.getError(), "No error should occur for an empty job list");
        assertTrue(response.getResults() == null || response.getResults().isEmpty(), "Results should be null or empty");

        // Verify that no HTTP call was made
        mockServer.verify(); // Verifies all expectations were met (i.e., no unexpected calls)
                             // and since no expectation was set, it means no call was made.
    }

    // Previous unit tests for addJob and generateFallbackHtml can be adapted or kept if they don't need Spring context.
    // For simplicity, I'm focusing on the integration tests as requested for Phase 2.
    // If those unit tests are still valuable, they might be moved to a different non-@SpringBootTest class
    // or adjusted to work within this context if appropriate.
}
