package com.example.demo.hypernova;

import com.example.demo.hypernova.plugin.HypernovaPlugin;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.*;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HypernovaRendererTest {

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private HypernovaPlugin mockPlugin1;

    @Mock
    private HypernovaPlugin mockPlugin2;

    @Captor
    private ArgumentCaptor<HttpEntity<String>> httpEntityCaptor;

    @Captor
    private ArgumentCaptor<List<HypernovaJob>> jobListCaptor;

    @Captor
    private ArgumentCaptor<HypernovaJobResult> jobResultCaptor;

    @Captor
    private ArgumentCaptor<Map<String, HypernovaJobResult>> jobResultMapCaptor;

    private HypernovaRenderer hypernovaRenderer;

    private final String DEFAULT_SERVICE_URL = "http://localhost:3030/batch";

    @BeforeEach
    void setUp() {
        hypernovaRenderer = new HypernovaRenderer(DEFAULT_SERVICE_URL, restTemplate, objectMapper);
        // Default behavior for plugins to prevent NPEs if not specifically mocked in a test
        lenient().when(mockPlugin1.getViewData(anyString(), anyMap(), any())).thenAnswer(inv -> new HypernovaJob(inv.getArgument(0), inv.getArgument(1), null));
        lenient().when(mockPlugin1.prepareRequest(anyList(), anyList())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(mockPlugin1.shouldSendRequest(anyList())).thenReturn(true);
        lenient().when(mockPlugin1.afterResponse(anyMap())).thenAnswer(inv -> inv.getArgument(0));

        lenient().when(mockPlugin2.getViewData(anyString(), anyMap(), any())).thenAnswer(inv -> new HypernovaJob(inv.getArgument(0), inv.getArgument(1), null));
        lenient().when(mockPlugin2.prepareRequest(anyList(), anyList())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(mockPlugin2.shouldSendRequest(anyList())).thenReturn(true);
        lenient().when(mockPlugin2.afterResponse(anyMap())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @SuppressWarnings("unchecked")
    void addJob_addsJobToIncomingJobs() throws JsonProcessingException {
        HypernovaJob job = new HypernovaJob("TestComponent", new HashMap<>(), null);
        hypernovaRenderer.addJob("job1", job);
        
        when(objectMapper.writeValueAsString(anyMap())).thenReturn("{}");
        HypernovaResponse mockHypernovaResponse = new HypernovaResponse(new HashMap<>(), null);
        when(objectMapper.readValue(anyString(), eq(HypernovaResponse.class))).thenReturn(mockHypernovaResponse);

        ResponseEntity<String> responseEntity = new ResponseEntity<>("{\"results\":{}}", HttpStatus.OK);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(responseEntity);

        hypernovaRenderer.render();

        verify(objectMapper).writeValueAsString(argThat(map -> ((Map<String, HypernovaJob>)map).containsKey("TestComponent")));
    }

    @Test
    void addPlugin_addsPluginToList() throws JsonProcessingException {
        hypernovaRenderer.addPlugin(mockPlugin1);
        HypernovaJob jobToReturn = new HypernovaJob("Comp", Collections.emptyMap(), null);
        hypernovaRenderer.addJob("job1", jobToReturn); 
        
        when(mockPlugin1.getViewData(eq("Comp"), anyMap(), any(HypernovaJob.class))).thenReturn(jobToReturn);

        when(objectMapper.writeValueAsString(anyMap())).thenReturn("{}");
        HypernovaResponse mockHypernovaResponse = new HypernovaResponse(new HashMap<>(), null);
        when(objectMapper.readValue(anyString(), eq(HypernovaResponse.class))).thenReturn(mockHypernovaResponse);
        ResponseEntity<String> responseEntity = new ResponseEntity<>("{\"results\":{}}", HttpStatus.OK);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(responseEntity);

        hypernovaRenderer.render();
        verify(mockPlugin1).getViewData(eq("Comp"), anyMap(), any(HypernovaJob.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void render_happyPath_callsPluginsAndReturnsResponse() throws JsonProcessingException {
        HypernovaJob job1 = new HypernovaJob("Component1", Map.of("key", "value"), null);
        hypernovaRenderer.addJob("clientJobId1", job1);
        hypernovaRenderer.addPlugin(mockPlugin1);

        when(mockPlugin1.getViewData(eq("Component1"), eq(Map.of("key", "value")), any(HypernovaJob.class))).thenReturn(job1);

        String jobJson = "{\"Component1\":{\"name\":\"Component1\",\"data\":{\"key\":\"value\"}}}";
        when(objectMapper.writeValueAsString(argThat(map -> ((Map<String, HypernovaJob>)map).containsKey("Component1")))).thenReturn(jobJson);

        HypernovaJobResult jobResultFromServer = new HypernovaJobResult("<div>Rendered</div>", null, true, null, null); // originalJob is set by renderer
        Map<String, HypernovaJobResult> resultsMapFromServer = Map.of("Component1", jobResultFromServer);
        HypernovaResponse hypernovaResponseFromServer = new HypernovaResponse(resultsMapFromServer, null);
        String serverResponseJson = "{\"results\":{\"Component1\":{\"html\":\"<div>Rendered</div>\",\"success\":true}}}";
        ResponseEntity<String> responseEntity = new ResponseEntity<>(serverResponseJson, HttpStatus.OK);
        when(restTemplate.exchange(eq(DEFAULT_SERVICE_URL), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(responseEntity);
        when(objectMapper.readValue(serverResponseJson, HypernovaResponse.class)).thenReturn(hypernovaResponseFromServer);

        HypernovaResponse finalResponse = hypernovaRenderer.render();

        InOrder inOrder = Mockito.inOrder(mockPlugin1, restTemplate, objectMapper);
        inOrder.verify(mockPlugin1).getViewData(eq("Component1"), eq(Map.of("key", "value")), any(HypernovaJob.class));
        inOrder.verify(mockPlugin1).prepareRequest(jobListCaptor.capture(), anyList());
        assertEquals("Component1", jobListCaptor.getValue().get(0).getName());
        inOrder.verify(mockPlugin1).shouldSendRequest(jobListCaptor.capture());
        assertEquals("Component1", jobListCaptor.getValue().get(0).getName());
        inOrder.verify(mockPlugin1).willSendRequest(jobListCaptor.capture());
        assertEquals("Component1", jobListCaptor.getValue().get(0).getName());

        verify(objectMapper).writeValueAsString(argThat(map -> ((Map<String, HypernovaJob>)map).get("Component1").getName().equals("Component1")));
        inOrder.verify(restTemplate).exchange(eq(DEFAULT_SERVICE_URL), eq(HttpMethod.POST), httpEntityCaptor.capture(), eq(String.class));
        assertEquals(jobJson, httpEntityCaptor.getValue().getBody());
        verify(objectMapper).readValue(serverResponseJson, HypernovaResponse.class);

        inOrder.verify(mockPlugin1).onSuccess(jobResultCaptor.capture());
        assertEquals("<div>Rendered</div>", jobResultCaptor.getValue().getHtml());
        inOrder.verify(mockPlugin1).afterResponse(jobResultMapCaptor.capture());
        assertTrue(jobResultMapCaptor.getValue().containsKey("clientJobId1")); // Keyed by client ID now

        assertNotNull(finalResponse);
        assertNull(finalResponse.getError());
        assertTrue(finalResponse.getResults().containsKey("clientJobId1"));
        assertEquals("<div>Rendered</div>", finalResponse.getResults().get("clientJobId1").getHtml());
        assertEquals("clientJobId1", finalResponse.getResults().get("clientJobId1").getMeta().get("_originalClientId"));
        assertSame(job1, finalResponse.getResults().get("clientJobId1").getOriginalJob());
    }

    @Test
    @SuppressWarnings("unchecked")
    void render_httpError_callsOnErrorAndGeneratesFallback() throws JsonProcessingException {
        HypernovaJob job1 = new HypernovaJob("ErrorComponent", Map.of("id", "1"), null);
        hypernovaRenderer.addJob("errorJob1", job1);
        hypernovaRenderer.addPlugin(mockPlugin1);

        when(mockPlugin1.getViewData(anyString(), anyMap(), any(HypernovaJob.class))).thenReturn(job1);
        when(objectMapper.writeValueAsString(anyMap())).thenReturn("{\"ErrorComponent\":{}}");

        RestClientException restClientException = new RestClientException("Network Error");
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenThrow(restClientException);

        when(objectMapper.writeValueAsString(Map.of("id", "1"))).thenReturn("{\"id\":\"1\"}");

        HypernovaResponse finalResponse = hypernovaRenderer.render();

        verify(mockPlugin1).onError(eq(restClientException), isNull(), jobListCaptor.capture());
        assertEquals("ErrorComponent", jobListCaptor.getValue().get(0).getName());

        assertNotNull(finalResponse.getResults().get("errorJob1"));
        HypernovaJobResult fallbackResult = finalResponse.getResults().get("errorJob1");
        assertTrue(fallbackResult.getHtml().contains("data-hypernova-name=\"ErrorComponent\""));
        assertTrue(fallbackResult.getHtml().contains("<!--{\"id\":\"1\"}-->"));
        assertFalse(fallbackResult.isSuccess());
        assertNotNull(fallbackResult.getError());
    }

    @Test
    void render_shouldSendRequestFalse_generatesFallback() throws JsonProcessingException {
        HypernovaJob job1 = new HypernovaJob("NoSendComponent", Map.of("data", "test"), null);
        hypernovaRenderer.addJob("noSendJob1", job1);
        hypernovaRenderer.addPlugin(mockPlugin1);

        when(mockPlugin1.getViewData(anyString(), anyMap(), any(HypernovaJob.class))).thenReturn(job1);
        when(mockPlugin1.shouldSendRequest(anyList())).thenReturn(false);
        when(objectMapper.writeValueAsString(Map.of("data", "test"))).thenReturn("{\"data\":\"test\"}");

        HypernovaResponse finalResponse = hypernovaRenderer.render();

        verifyNoInteractions(restTemplate);
        verify(mockPlugin1, never()).willSendRequest(anyList());
        verify(mockPlugin1, never()).onSuccess(any());
        verify(mockPlugin1, never()).onError(any(RestClientException.class), anyList(), anyList());

        assertNotNull(finalResponse.getResults().get("noSendJob1"));
        HypernovaJobResult fallbackResult = finalResponse.getResults().get("noSendJob1");
        assertTrue(fallbackResult.getHtml().contains("data-hypernova-name=\"NoSendComponent\""));
        assertTrue(fallbackResult.getHtml().contains("<!--{\"data\":\"test\"}-->"));
        assertFalse(fallbackResult.isSuccess());
    }

    @Test
    @SuppressWarnings("unchecked")
    void render_pluginGetViewDataModification() throws JsonProcessingException {
        HypernovaJob originalJob = new HypernovaJob("MyComponent", Map.of("originalKey", "originalValue"), null);
        HypernovaJob modifiedJob = new HypernovaJob("MyComponent", Map.of("modifiedKey", "modifiedValue"), Map.of("_originalClientId", "job1"));
        hypernovaRenderer.addJob("job1", originalJob);
        hypernovaRenderer.addPlugin(mockPlugin1);

        when(mockPlugin1.getViewData(eq("MyComponent"), anyMap(), same(originalJob))).thenReturn(modifiedJob);

        String modifiedJobJson = "{\"MyComponent\":{\"name\":\"MyComponent\",\"data\":{\"modifiedKey\":\"modifiedValue\"}}}";
        when(objectMapper.writeValueAsString(argThat(map -> ((Map<String, HypernovaJob>)map).get("MyComponent").getData().containsKey("modifiedKey"))))
            .thenReturn(modifiedJobJson);

        HypernovaResponse mockResp = new HypernovaResponse(Map.of("MyComponent", new HypernovaJobResult("html", null, true, modifiedJob, Map.of("_originalClientId", "job1"))), null);
        String mockRespJson = "{\"results\":{\"MyComponent\":{\"html\":\"html\",\"success\":true}}}";
        when(objectMapper.readValue(mockRespJson, HypernovaResponse.class)).thenReturn(mockResp);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
            .thenReturn(new ResponseEntity<>(mockRespJson, HttpStatus.OK));

        hypernovaRenderer.render();

        verify(objectMapper).writeValueAsString(argThat(map -> 
            ((Map<String, HypernovaJob>)map).get("MyComponent").getData().equals(Map.of("modifiedKey", "modifiedValue"))
        ));
        verify(restTemplate).exchange(anyString(), any(), httpEntityCaptor.capture(), eq(String.class));
        assertEquals(modifiedJobJson, httpEntityCaptor.getValue().getBody());
    }
    
    @Test
    void render_pluginOrderVerification() throws JsonProcessingException {
        HypernovaJob job = new HypernovaJob("TestOrder", Collections.emptyMap(), null);
        hypernovaRenderer.addJob("jobOrder", job);
        hypernovaRenderer.addPlugin(mockPlugin1);
        hypernovaRenderer.addPlugin(mockPlugin2);

        when(mockPlugin1.getViewData(anyString(), anyMap(), any(HypernovaJob.class))).thenReturn(job);
        when(mockPlugin2.getViewData(anyString(), anyMap(), any(HypernovaJob.class))).thenReturn(job);
        
        when(objectMapper.writeValueAsString(anyMap())).thenReturn("{\"TestOrder\":{}}");
        String serverResponseJson = "{\"results\":{\"TestOrder\":{\"html\":\"Test HTML\",\"success\":true}}}";
        HypernovaJobResult jobResultFromServer = new HypernovaJobResult("Test HTML", null, true, null, Map.of("_originalClientId", "jobOrder"));
        HypernovaResponse mockHypernovaResponse = new HypernovaResponse(Map.of("TestOrder", jobResultFromServer), null);

        when(objectMapper.readValue(serverResponseJson, HypernovaResponse.class)).thenReturn(mockHypernovaResponse);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
            .thenReturn(new ResponseEntity<>(serverResponseJson, HttpStatus.OK));

        hypernovaRenderer.render();

        InOrder inOrder = Mockito.inOrder(mockPlugin1, mockPlugin2, restTemplate);

        inOrder.verify(mockPlugin1).getViewData(anyString(), anyMap(), any(HypernovaJob.class));
        inOrder.verify(mockPlugin2).getViewData(anyString(), anyMap(), any(HypernovaJob.class));
        inOrder.verify(mockPlugin1).prepareRequest(anyList(), anyList());
        inOrder.verify(mockPlugin2).prepareRequest(anyList(), anyList());
        inOrder.verify(mockPlugin1).shouldSendRequest(anyList());
        inOrder.verify(mockPlugin2).shouldSendRequest(anyList());
        inOrder.verify(mockPlugin1).willSendRequest(anyList());
        inOrder.verify(mockPlugin2).willSendRequest(anyList());
        inOrder.verify(restTemplate).exchange(anyString(), any(), any(HttpEntity.class), eq(String.class));
        inOrder.verify(mockPlugin1).onSuccess(any(HypernovaJobResult.class));
        inOrder.verify(mockPlugin2).onSuccess(any(HypernovaJobResult.class));
        inOrder.verify(mockPlugin1).afterResponse(anyMap());
        inOrder.verify(mockPlugin2).afterResponse(anyMap());
    }

    @Test
    @SuppressWarnings("unchecked")
    void render_fallbackHtmlGenerationStructureCheck() throws JsonProcessingException {
        HypernovaJob job = new HypernovaJob("FallbackTestComponent", Map.of("sample", "data"), null);
        hypernovaRenderer.addJob("fbJob", job);
        
        when(objectMapper.writeValueAsString(argThat(map -> ((Map<String, HypernovaJob>)map).containsKey("FallbackTestComponent")))).thenReturn("{\"FallbackTestComponent\":{}}");
        when(objectMapper.writeValueAsString(Map.of("sample", "data"))).thenReturn("{\"sample\":\"data\"}");
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new RestClientException("Simulated HTTP error for fallback test"));

        HypernovaResponse response = hypernovaRenderer.render();

        assertNotNull(response.getResults().get("fbJob"));
        HypernovaJobResult result = response.getResults().get("fbJob");
        String html = result.getHtml();
        assertTrue(html.startsWith("<div data-hypernova-id="));
        assertTrue(html.contains("data-hypernova-name=\"FallbackTestComponent\""));
        assertTrue(html.endsWith("</script>"));
        assertTrue(html.contains("<!--{\"sample\":\"data\"}-->"));
    }
}
