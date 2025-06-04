package com.example.hypernovaclient;

import com.example.hypernovaclient.model.HypernovaJob;
import com.example.hypernovaclient.model.HypernovaJobResult;
import com.example.hypernovaclient.model.HypernovaResponse;
import com.example.hypernovaclient.plugin.HypernovaPlugin;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class HypernovaRendererUnitTest {

    @Mock
    private RestTemplate mockRestTemplate;
    @Spy
    private ObjectMapper spyObjectMapper = new ObjectMapper(); // Use a spy for real JSON processing but allow verification
    @Mock
    private HypernovaPlugin mockPlugin1;
    @Mock
    private HypernovaPlugin mockPlugin2;

    private HypernovaRenderer hypernovaRenderer;
    private List<HypernovaPlugin> pluginsList;

    private final String DUMMY_URL = "http://dummy-hypernova.com/batch";

    @Captor
    private ArgumentCaptor<Map<String, HypernovaJob>> jobsMapCaptor;
    @Captor
    private ArgumentCaptor<HttpEntity<String>> httpEntityCaptor;
    @Captor
    private ArgumentCaptor<HypernovaJobResult> jobResultCaptor;
    @Captor
    private ArgumentCaptor<Object> errorCaptor;
     @Captor
    private ArgumentCaptor<Map<String, HypernovaJobResult>> jobResultsMapCaptor;


    @BeforeEach
    void setUp() {
        pluginsList = new ArrayList<>();
        // Default behavior for plugins that return a value, to avoid NPEs if not explicitly stubbed
        lenient().when(mockPlugin1.getViewData(anyString(), anyMap())).thenAnswer(invocation -> invocation.getArgument(1));
        lenient().when(mockPlugin1.prepareRequest(anyMap(), anyMap())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(mockPlugin1.shouldSendRequest(anyMap())).thenReturn(true);
        lenient().when(mockPlugin1.afterResponse(anyMap())).thenAnswer(invocation -> invocation.getArgument(0));

        lenient().when(mockPlugin2.getViewData(anyString(), anyMap())).thenAnswer(invocation -> invocation.getArgument(1));
        lenient().when(mockPlugin2.prepareRequest(anyMap(), anyMap())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(mockPlugin2.shouldSendRequest(anyMap())).thenReturn(true);
        lenient().when(mockPlugin2.afterResponse(anyMap())).thenAnswer(invocation -> invocation.getArgument(0));

        hypernovaRenderer = new HypernovaRenderer(DUMMY_URL, pluginsList, mockRestTemplate, spyObjectMapper);
    }

    private HypernovaJob createTestJob(String name, Map<String, Object> data) {
        return new HypernovaJob(name, data, new HashMap<>());
    }

    private String successfulResponseJson(String jobKey, String jobName, String html) throws JsonProcessingException {
        HypernovaJobResult jobResult = new HypernovaJobResult();
        jobResult.setHtml(html);
        jobResult.setError(null);
        jobResult.setMeta(new HashMap<>());
        jobResult.setSuccess(true);
        jobResult.setDuration(10.0);
        jobResult.setViewName(jobName);

        HypernovaResponse response = new HypernovaResponse();
        response.setResults(Collections.singletonMap(jobKey, jobResult));
        response.setError(null);
        return spyObjectMapper.writeValueAsString(response);
    }

    private String jobErrorResponseJson(String jobKey, String jobName, Map<String,Object> error) throws JsonProcessingException {
        HypernovaJobResult jobResult = new HypernovaJobResult();
        jobResult.setHtml(null); // Server might provide fallback, or null
        jobResult.setError(error);
        jobResult.setMeta(new HashMap<>());
        jobResult.setSuccess(false);
        jobResult.setDuration(5.0);
        jobResult.setViewName(jobName);

        HypernovaResponse response = new HypernovaResponse();
        response.setResults(Collections.singletonMap(jobKey, jobResult));
        response.setError(null);
        return spyObjectMapper.writeValueAsString(response);
    }


    @Test
    void testPlugin_getViewData() throws Exception {
        pluginsList.add(mockPlugin1);
        HypernovaJob job1 = createTestJob("Component1", Map.of("originalKey", "originalValue"));
        hypernovaRenderer.addJob("job1", job1);

        Map<String, Object> modifiedData = Map.of("modifiedKey", "modifiedValue");
        when(mockPlugin1.getViewData(eq("Component1"), eq(Map.of("originalKey", "originalValue"))))
                .thenReturn(modifiedData);

        when(mockRestTemplate.postForObject(eq(DUMMY_URL), any(HttpEntity.class), eq(String.class)))
                .thenReturn(successfulResponseJson("job1","Component1", "<div></div>"));

        hypernovaRenderer.render();

        verify(mockPlugin1).getViewData("Component1", Map.of("originalKey", "originalValue"));

        verify(mockRestTemplate).postForObject(eq(DUMMY_URL), httpEntityCaptor.capture(), eq(String.class));
        String requestBodyJson = httpEntityCaptor.getValue().getBody();
        assertTrue(requestBodyJson.contains("\"data\":{\"modifiedKey\":\"modifiedValue\"}"));
    }

    @Test
    void testPlugin_prepareRequest() throws Exception {
        pluginsList.add(mockPlugin1);
        HypernovaJob job1 = createTestJob("Component1", Map.of("id", 1));
        HypernovaJob job2 = createTestJob("Component2", Map.of("id", 2));
        hypernovaRenderer.addJob("job1", job1);
        hypernovaRenderer.addJob("job2", job2);

        // Plugin removes job1
        Map<String, HypernovaJob> modifiedJobs = new HashMap<>();
        modifiedJobs.put("job2", job2);
        when(mockPlugin1.prepareRequest(anyMap(), anyMap())).thenReturn(modifiedJobs);

        when(mockRestTemplate.postForObject(eq(DUMMY_URL), any(HttpEntity.class), eq(String.class)))
                .thenReturn(successfulResponseJson("job2","Component2", "<div></div>"));

        hypernovaRenderer.render();

        verify(mockPlugin1).prepareRequest(jobsMapCaptor.capture(), anyMap());
        assertEquals(2, jobsMapCaptor.getValue().size(), "Original jobs map before prepareRequest");

        verify(mockRestTemplate).postForObject(eq(DUMMY_URL), httpEntityCaptor.capture(), eq(String.class));
        String requestBodyJson = httpEntityCaptor.getValue().getBody();
        assertFalse(requestBodyJson.contains("Component1"));
        assertTrue(requestBodyJson.contains("Component2"));
    }

    @Test
    void testPlugin_shouldSendRequest_False() {
        pluginsList.add(mockPlugin1);
        HypernovaJob job1 = createTestJob("Component1", Map.of("id", 1));
        hypernovaRenderer.addJob("job1", job1);

        when(mockPlugin1.shouldSendRequest(anyMap())).thenReturn(false);

        HypernovaResponse response = hypernovaRenderer.render();

        verify(mockPlugin1).shouldSendRequest(jobsMapCaptor.capture());
        verifyNoInteractions(mockRestTemplate);
        assertNotNull(response.getResults().get("job1").getError()); // Fallback should be generated
        assertTrue(response.getResults().get("job1").getHtml().contains("data-hypernova-key=\"Component1\""));
    }

    @Test
    void testPlugin_willSendRequest() throws Exception {
        pluginsList.add(mockPlugin1);
        HypernovaJob job1 = createTestJob("Component1", Map.of("id", 1));
        hypernovaRenderer.addJob("job1", job1);

        when(mockRestTemplate.postForObject(eq(DUMMY_URL), any(HttpEntity.class), eq(String.class)))
                .thenReturn(successfulResponseJson("job1","Component1", "<div></div>"));

        hypernovaRenderer.render();
        verify(mockPlugin1).willSendRequest(jobsMapCaptor.capture());
        assertEquals("Component1", jobsMapCaptor.getValue().get("job1").getName());
        verify(mockRestTemplate).postForObject(anyString(), any(), any()); // Ensure request was made after
    }

    @Test
    void testPlugin_onSuccess() throws Exception {
        pluginsList.add(mockPlugin1);
        HypernovaJob job1 = createTestJob("Component1", Map.of("id", 1));
        hypernovaRenderer.addJob("job1", job1);

        when(mockRestTemplate.postForObject(eq(DUMMY_URL), any(HttpEntity.class), eq(String.class)))
                .thenReturn(successfulResponseJson("job1","Component1", "<div>Test HTML</div>"));

        hypernovaRenderer.render();

        verify(mockPlugin1).onSuccess(jobResultCaptor.capture());
        HypernovaJobResult capturedResult = jobResultCaptor.getValue();
        assertTrue(capturedResult.isSuccess());
        assertEquals("<div>Test HTML</div>", capturedResult.getHtml());
        assertEquals("Component1", capturedResult.getOriginalJob().getName());
    }

    @Test
    void testPlugin_onJobError() throws Exception {
        pluginsList.add(mockPlugin1);
        HypernovaJob job1 = createTestJob("ErrorComponent", Map.of("id", 1));
        hypernovaRenderer.addJob("job1", job1);

        Map<String, Object> errorDetails = Map.of("message", "Render failure", "stack", List.of("line 1"));
        when(mockRestTemplate.postForObject(eq(DUMMY_URL), any(HttpEntity.class), eq(String.class)))
                .thenReturn(jobErrorResponseJson("job1","ErrorComponent", errorDetails));

        hypernovaRenderer.render();

        verify(mockPlugin1).onJobError(jobResultCaptor.capture());
        HypernovaJobResult capturedResult = jobResultCaptor.getValue();
        assertFalse(capturedResult.isSuccess());
        assertEquals(errorDetails, capturedResult.getError());
        assertEquals("ErrorComponent", capturedResult.getOriginalJob().getName());
    }

    @Test
    void testPlugin_onBatchError_fromHttpError() {
        pluginsList.add(mockPlugin1);
        HypernovaJob job1 = createTestJob("Component1", Map.of("id", 1));
        hypernovaRenderer.addJob("job1", job1);

        HttpClientErrorException httpException = new HttpClientErrorException(HttpStatus.INTERNAL_SERVER_ERROR, "Server Error");
        when(mockRestTemplate.postForObject(eq(DUMMY_URL), any(HttpEntity.class), eq(String.class)))
                .thenThrow(httpException);

        hypernovaRenderer.render();

        verify(mockPlugin1).onBatchError(errorCaptor.capture(), jobsMapCaptor.capture());
        assertSame(httpException, errorCaptor.getValue());
        assertEquals(1, jobsMapCaptor.getValue().size());
    }

    @Test
    void testPlugin_onBatchError_fromResourceAccessException() {
        pluginsList.add(mockPlugin1);
        HypernovaJob job1 = createTestJob("Component1", Map.of("id", 1));
        hypernovaRenderer.addJob("job1", job1);

        ResourceAccessException networkException = new ResourceAccessException("I/O error");
        when(mockRestTemplate.postForObject(eq(DUMMY_URL), any(HttpEntity.class), eq(String.class)))
                .thenThrow(networkException);

        HypernovaResponse response = hypernovaRenderer.render();

        verify(mockPlugin1).onBatchError(errorCaptor.capture(), jobsMapCaptor.capture());
        assertSame(networkException, errorCaptor.getValue());
        assertEquals(1, jobsMapCaptor.getValue().size());

        // Check fallback response
        assertNotNull(response.getResults().get("job1").getError());
        assertTrue(response.getResults().get("job1").getHtml().contains("data-hypernova-key=\"Component1\""));
    }


    @Test
    void testPlugin_afterResponse() throws Exception {
        pluginsList.add(mockPlugin1);
        HypernovaJob job1 = createTestJob("Component1", Map.of("id", 1));
        hypernovaRenderer.addJob("job1", job1);

        when(mockRestTemplate.postForObject(eq(DUMMY_URL), any(HttpEntity.class), eq(String.class)))
                .thenReturn(successfulResponseJson("job1","Component1", "<div>Original HTML</div>"));

        Map<String, HypernovaJobResult> modifiedResults = new HashMap<>();
        HypernovaJobResult modifiedResult = new HypernovaJobResult();
        modifiedResult.setHtml("<div>Modified HTML by Plugin</div>");
        modifiedResult.setSuccess(true);
        modifiedResults.put("job1", modifiedResult);

        when(mockPlugin1.afterResponse(anyMap())).thenReturn(modifiedResults);

        HypernovaResponse finalResponse = hypernovaRenderer.render();

        verify(mockPlugin1).afterResponse(jobResultsMapCaptor.capture());
        assertEquals("<div>Original HTML</div>", jobResultsMapCaptor.getValue().get("job1").getHtml());

        assertNotNull(finalResponse.getResults().get("job1"));
        assertEquals("<div>Modified HTML by Plugin</div>", finalResponse.getResults().get("job1").getHtml());
    }

    @Test
    void testMultiplePlugins_ExecutionOrder() throws Exception {
        pluginsList.add(mockPlugin1);
        pluginsList.add(mockPlugin2); // mockPlugin2 added after mockPlugin1

        HypernovaJob job = createTestJob("TestComponent", Map.of("key", "val"));
        hypernovaRenderer.addJob("jobKey", job);

        // Mocking interactions to check order
        when(mockRestTemplate.postForObject(anyString(), any(), anyString()))
            .thenReturn(successfulResponseJson("jobKey", "TestComponent", "<div>Content</div>"));

        // For prepareRequest, the output of plugin1 becomes input of plugin2
        Map<String, HypernovaJob> jobsFromPlugin1 = new HashMap<>();
        jobsFromPlugin1.put("jobKey", createTestJob("AlteredByPlugin1", Map.of()));
        when(mockPlugin1.prepareRequest(anyMap(), anyMap())).thenReturn(jobsFromPlugin1);

        // For afterResponse, similar chaining
        Map<String, HypernovaJobResult> resultsFromPlugin1 = new HashMap<>();
        HypernovaJobResult resFromP1 = new HypernovaJobResult();
        resFromP1.setHtml("html_p1");
        resultsFromPlugin1.put("jobKey", resFromP1);
        when(mockPlugin1.afterResponse(anyMap())).thenReturn(resultsFromPlugin1);


        hypernovaRenderer.render();

        // Verify order of calls for hooks that modify data flow
        // prepareRequest: p1 then p2
        // afterResponse: p1 then p2
        // For other hooks, just verify they were called on both
        InOrder inOrder = inOrder(mockPlugin1, mockPlugin2, mockRestTemplate);

        inOrder.verify(mockPlugin1).getViewData(anyString(), anyMap());
        inOrder.verify(mockPlugin2).getViewData(anyString(), anyMap());

        inOrder.verify(mockPlugin1).prepareRequest(anyMap(), anyMap());
        inOrder.verify(mockPlugin2).prepareRequest(eq(jobsFromPlugin1), anyMap()); // p2 gets output from p1

        inOrder.verify(mockPlugin1).shouldSendRequest(anyMap());
        inOrder.verify(mockPlugin2).shouldSendRequest(anyMap());

        inOrder.verify(mockPlugin1).willSendRequest(anyMap());
        inOrder.verify(mockPlugin2).willSendRequest(anyMap());

        inOrder.verify(mockRestTemplate).postForObject(anyString(), any(HttpEntity.class), eq(String.class));

        inOrder.verify(mockPlugin1).onSuccess(any(HypernovaJobResult.class));
        inOrder.verify(mockPlugin2).onSuccess(any(HypernovaJobResult.class));

        inOrder.verify(mockPlugin1).afterResponse(anyMap());
        // Plugin2's afterResponse gets the result of Plugin1's afterResponse
        verify(mockPlugin2).afterResponse(jobResultsMapCaptor.capture());
        assertEquals("html_p1", jobResultsMapCaptor.getValue().get("jobKey").getHtml());


    }
}
