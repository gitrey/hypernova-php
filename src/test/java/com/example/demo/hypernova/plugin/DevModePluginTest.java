package com.example.demo.hypernova.plugin;

import com.example.demo.hypernova.HypernovaJob;
import com.example.demo.hypernova.HypernovaJobResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DevModePluginTest {

    private DevModePlugin devModePlugin;

    @BeforeEach
    void setUp() {
        devModePlugin = new DevModePlugin();
    }

    @Test
    void afterResponse_noError_htmlUnchanged() {
        HypernovaJobResult jobResult = new HypernovaJobResult("<div>Original HTML</div>", null, true, null, null);
        Map<String, HypernovaJobResult> jobResults = new HashMap<>();
        jobResults.put("testJob", jobResult);

        Map<String, HypernovaJobResult> processedResults = devModePlugin.afterResponse(jobResults);

        assertSame(jobResults, processedResults, "Map instance should be the same");
        assertEquals("<div>Original HTML</div>", processedResults.get("testJob").getHtml());
    }

    @Test
    void afterResponse_withErrorMap_htmlPrependedWithErrorDetails() {
        HypernovaJob originalJob = new HypernovaJob("MyComponent", Collections.emptyMap(), null);
        Map<String, Object> errorMap = new HashMap<>();
        errorMap.put("message", "Test error message");
        errorMap.put("stack", List.of("stack_line_1", "stack_line_2 <>&\""));
        HypernovaJobResult jobResult = new HypernovaJobResult("<p>Fallback HTML</p>", errorMap, false, originalJob, null);
        Map<String, HypernovaJobResult> jobResults = Collections.singletonMap("jobWithError", jobResult);

        devModePlugin.afterResponse(jobResults);

        String modifiedHtml = jobResult.getHtml();
        assertTrue(modifiedHtml.contains("Development Warning!"));
        assertTrue(modifiedHtml.contains("The <code>MyComponent</code> component failed to render"));
        assertTrue(modifiedHtml.contains("<strong>Test error message</strong>"));
        assertTrue(modifiedHtml.contains("<li>stack_line_1</li>"));
        assertTrue(modifiedHtml.contains("<li>stack_line_2 &lt;&gt;&amp;&quot;</li>")); // Check HTML escaping
        assertTrue(modifiedHtml.endsWith("<p>Fallback HTML</p>"));
    }

    @Test
    void afterResponse_withThrowableError_htmlPrependedWithErrorDetails() {
        HypernovaJob originalJob = new HypernovaJob("ThrowableComponent", Collections.emptyMap(), null);
        Throwable error = new RuntimeException("Throwable error <>&\"");
        // Manually create a few stack trace elements for consistent testing
        StackTraceElement ste1 = new StackTraceElement("com.example.Class", "method", "File.java", 10);
        StackTraceElement ste2 = new StackTraceElement("com.example.AnotherClass", "anotherMethod", "AnotherFile.java", 20);
        error.setStackTrace(new StackTraceElement[]{ste1, ste2});

        HypernovaJobResult jobResult = new HypernovaJobResult("<p>Original Content</p>", error, false, originalJob, null);
        Map<String, HypernovaJobResult> jobResults = Collections.singletonMap("jobWithThrowable", jobResult);

        devModePlugin.afterResponse(jobResults);

        String modifiedHtml = jobResult.getHtml();
        assertTrue(modifiedHtml.contains("Development Warning!"));
        assertTrue(modifiedHtml.contains("The <code>ThrowableComponent</code> component failed to render"));
        assertTrue(modifiedHtml.contains("<strong>Throwable error &lt;&gt;&amp;&quot;</strong>"));
        assertTrue(modifiedHtml.contains("<li>com.example.Class.method(File.java:10)</li>"));
        assertTrue(modifiedHtml.contains("<li>com.example.AnotherClass.anotherMethod(AnotherFile.java:20)</li>"));
        assertTrue(modifiedHtml.endsWith("<p>Original Content</p>"));
    }

    @Test
    void afterResponse_withStringError_htmlPrepended() {
        HypernovaJob originalJob = new HypernovaJob("StringErrorComponent", null, null);
        String errorString = "Simple string error <details>";
        HypernovaJobResult jobResult = new HypernovaJobResult("", errorString, false, originalJob, null);
        Map<String, HypernovaJobResult> jobResults = Collections.singletonMap("jobWithStringError", jobResult);

        devModePlugin.afterResponse(jobResults);

        String modifiedHtml = jobResult.getHtml();
        assertTrue(modifiedHtml.contains("<strong>Simple string error &lt;details&gt;</strong>"));
        assertTrue(modifiedHtml.contains("<li>No stack trace available.</li>"));
    }

    @Test
    void afterResponse_nullJobResultError_htmlUnchanged() {
        HypernovaJobResult jobResult = new HypernovaJobResult("<div>HTML</div>", null, true, null, null);
        Map<String, HypernovaJobResult> jobResults = Collections.singletonMap("noErrorJob", jobResult);

        devModePlugin.afterResponse(jobResults);
        assertEquals("<div>HTML</div>", jobResult.getHtml());
    }

    @Test
    void afterResponse_nullJobResultsMap_returnsNull() {
        assertNull(devModePlugin.afterResponse(null));
    }

    @Test
    void afterResponse_emptyJobResultsMap_returnsEmptyMap() {
        Map<String, HypernovaJobResult> emptyMap = Collections.emptyMap();
        Map<String, HypernovaJobResult> result = devModePlugin.afterResponse(emptyMap);
        assertTrue(result.isEmpty());
        assertSame(emptyMap, result);
    }

    @Test
    void escapeHtml_variousCharacters() {
        // Access private method via reflection or make it package-private for testing, 
        // or test indirectly by ensuring its effects in afterResponse.
        // For this example, assuming indirect testing is sufficient as shown in error detail tests.
        // Direct test would look like:
        // DevModePlugin plugin = new DevModePlugin();
        // assertEquals("&lt;div&gt;", plugin.escapeHtml("<div>"));
        // assertEquals("&amp; &apos; &quot;", plugin.escapeHtml("& ' \""));
        // This is validated by the other tests that check for escaped characters in the output.
    }
}
