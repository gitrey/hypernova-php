package com.example.hypernovaclient.plugin;

import com.example.hypernovaclient.model.HypernovaJob;
import com.example.hypernovaclient.model.HypernovaJobResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class DevModePluginTest {

    private DevModePlugin devModePlugin;

    @BeforeEach
    void setUp() {
        devModePlugin = new DevModePlugin();
    }

    @Test
    void testAfterResponse_NoError() {
        HypernovaJobResult jobResult = new HypernovaJobResult();
        jobResult.setHtml("<div>No Error</div>");
        jobResult.setOriginalJob(new HypernovaJob("TestComponent", Collections.emptyMap(), Collections.emptyMap()));
        jobResult.setError(null);

        Map<String, HypernovaJobResult> jobResults = new HashMap<>();
        jobResults.put("job1", jobResult);

        Map<String, HypernovaJobResult> processedResults = devModePlugin.afterResponse(jobResults);

        assertSame(jobResults, processedResults, "Map instance should be the same");
        assertEquals("<div>No Error</div>", processedResults.get("job1").getHtml());
    }

    @Test
    void testAfterResponse_WithErrorMap() {
        HypernovaJobResult jobResult = new HypernovaJobResult();
        jobResult.setHtml("<!-- Fallback for MyComponent -->"); // Original HTML might be a fallback
        jobResult.setOriginalJob(new HypernovaJob("MyComponent", Collections.emptyMap(), Collections.emptyMap()));
        Map<String, Object> errorMap = new HashMap<>();
        errorMap.put("message", "Render failed");
        errorMap.put("stack", List.of("stacktrace line 1", "another line"));
        jobResult.setError(errorMap);

        Map<String, HypernovaJobResult> jobResults = Collections.singletonMap("job1", jobResult);
        Map<String, HypernovaJobResult> processedResults = devModePlugin.afterResponse(jobResults);

        String newHtml = processedResults.get("job1").getHtml();
        assertNotNull(newHtml);
        assertTrue(newHtml.startsWith("<div style=\"background-color: #ff5a5f;"));
        assertTrue(newHtml.contains("<strong>Development Warning!</strong> The <code>MyComponent</code> component failed to render"));
        assertTrue(newHtml.contains("<li><strong>Render failed</strong></li>"));
        assertTrue(newHtml.contains("<li>stacktrace line 1</li><li>another line</li>"));
        assertTrue(newHtml.endsWith("<!-- Fallback for MyComponent -->"));
    }

    @Test
    void testAfterResponse_WithErrorString() {
        HypernovaJobResult jobResult = new HypernovaJobResult();
        jobResult.setHtml(""); // Empty original HTML
        jobResult.setOriginalJob(new HypernovaJob("StringErrorComponent", Collections.emptyMap(), Collections.emptyMap()));
        jobResult.setError("Simple error string");

        Map<String, HypernovaJobResult> jobResults = Collections.singletonMap("job1", jobResult);
        Map<String, HypernovaJobResult> processedResults = devModePlugin.afterResponse(jobResults);

        String newHtml = processedResults.get("job1").getHtml();
        assertTrue(newHtml.contains("<strong>Development Warning!</strong>"));
        assertTrue(newHtml.contains("<code>StringErrorComponent</code>"));
        assertTrue(newHtml.contains("<li><strong>Simple error string</strong></li>"));
    }

    @Test
    void testAfterResponse_WithErrorThrowable() {
        HypernovaJobResult jobResult = new HypernovaJobResult();
        jobResult.setHtml("");
        jobResult.setOriginalJob(new HypernovaJob("ThrowableComponent", Collections.emptyMap(), Collections.emptyMap()));
        try {
            throw new RuntimeException("Something went wrong");
        } catch (RuntimeException e) {
            jobResult.setError(e);
        }

        Map<String, HypernovaJobResult> jobResults = Collections.singletonMap("job1", jobResult);
        Map<String, HypernovaJobResult> processedResults = devModePlugin.afterResponse(jobResults);

        String newHtml = processedResults.get("job1").getHtml();
        assertTrue(newHtml.contains("<strong>Development Warning!</strong>"));
        assertTrue(newHtml.contains("<code>ThrowableComponent</code>"));
        assertTrue(newHtml.contains("<li><strong>Something went wrong</strong></li>")); // Message
        assertTrue(newHtml.contains("<li>java.lang.RuntimeException: Something went wrong</li>")); // Stack
    }


    @Test
    void testAfterResponse_NullOriginalJob() {
        HypernovaJobResult jobResult = new HypernovaJobResult();
        jobResult.setHtml("<div>Content</div>");
        jobResult.setOriginalJob(null); // Original job is null
        jobResult.setError(Map.of("message", "Error occurred"));

        Map<String, HypernovaJobResult> jobResults = Collections.singletonMap("job1", jobResult);
        Map<String, HypernovaJobResult> processedResults = devModePlugin.afterResponse(jobResults);

        // HTML should remain unchanged because originalJob is needed for component name
        assertEquals("<div>Content</div>", processedResults.get("job1").getHtml());
    }

    @Test
    void testAfterResponse_NullError() {
        HypernovaJobResult jobResult = new HypernovaJobResult();
        jobResult.setHtml("<div>No Error</div>");
        jobResult.setOriginalJob(new HypernovaJob("TestComponent", Collections.emptyMap(), Collections.emptyMap()));
        jobResult.setError(null); // Error is null

        Map<String, HypernovaJobResult> jobResults = Collections.singletonMap("job1", jobResult);
        Map<String, HypernovaJobResult> processedResults = devModePlugin.afterResponse(jobResults);
        assertEquals("<div>No Error</div>", processedResults.get("job1").getHtml());
    }

    @Test
    void testAfterResponse_EmptyErrorMap() {
        HypernovaJobResult jobResult = new HypernovaJobResult();
        jobResult.setHtml("<div>Fallback</div>");
        jobResult.setOriginalJob(new HypernovaJob("EmptyErrorMapComponent", Collections.emptyMap(), Collections.emptyMap()));
        jobResult.setError(Collections.emptyMap()); // Error is an empty map

        Map<String, HypernovaJobResult> jobResults = Collections.singletonMap("job1", jobResult);
        Map<String, HypernovaJobResult> processedResults = devModePlugin.afterResponse(jobResults);

        // Should not prepend warning if formattedErrorHtml is empty
        assertEquals("<div>Fallback</div>", processedResults.get("job1").getHtml());
    }

    @Test
    void testFormatError_NullInput() {
        // This test is for the private method formatError, indirectly tested via afterResponse.
        // If direct testing was needed, formatError would be package-private or public.
        // Here, we ensure afterResponse handles it gracefully.
        HypernovaJobResult jobResult = new HypernovaJobResult();
        jobResult.setHtml("Initial HTML");
        jobResult.setOriginalJob(new HypernovaJob("Test", Map.of(), Map.of()));
        jobResult.setError(null); // Error object itself is null

        Map<String, HypernovaJobResult> results = devModePlugin.afterResponse(Collections.singletonMap("job1", jobResult));
        assertEquals("Initial HTML", results.get("job1").getHtml());
    }
}
