package com.example.hypernova;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class JobResultTest {

    private Job mockJob;
    private HypernovaError mockError;
    private HypernovaRenderer.HypernovaRawJobResult rawResultSuccess;
    private HypernovaRenderer.HypernovaRawJobResult rawResultError;

    @BeforeEach
    void setUp() {
        mockJob = Mockito.mock(Job.class);
        Mockito.when(mockJob.getName()).thenReturn("TestComponent");
        Mockito.when(mockJob.getData()).thenReturn(Map.of("key", "value"));

        mockError = new HypernovaError("Test Error Message", Arrays.asList("stack line 1", "stack line 2"));

        rawResultSuccess = new HypernovaRenderer.HypernovaRawJobResult(
                null,
                "<p>Success HTML</p>",
                true,
                Map.of("metaKey", "metaValue"),
                123.45
        );

        rawResultError = new HypernovaRenderer.HypernovaRawJobResult(
                mockError,
                "<p>Error Fallback HTML</p>",
                false,
                Map.of("metaErrorKey", "metaErrorValue"),
                67.89
        );
    }

    @Test
    void fromRawResult_withSuccessfulRawResult_shouldPopulateFieldsCorrectly() {
        JobResult result = JobResult.fromRawResult(rawResultSuccess, mockJob, null);

        assertNull(result.getError());
        assertEquals("<p>Success HTML</p>", result.getHtml());
        assertTrue(result.isSuccess());
        assertEquals(mockJob, result.getOriginalJob());
        assertEquals(Map.of("metaKey", "metaValue"), result.getMeta());
        assertEquals(123.45, result.getDuration());
    }

    @Test
    void fromRawResult_withErrorInRawResult_shouldPopulateFieldsCorrectly() {
        JobResult result = JobResult.fromRawResult(rawResultError, mockJob, null);

        assertEquals(mockError, result.getError());
        assertEquals("Test Error Message", result.getError().getMessage());
        assertEquals("<p>Error Fallback HTML</p>", result.getHtml());
        assertFalse(result.isSuccess());
        assertEquals(mockJob, result.getOriginalJob());
        assertEquals(Map.of("metaErrorKey", "metaErrorValue"), result.getMeta());
        assertEquals(67.89, result.getDuration());
    }

    @Test
    void fromRawResult_withPotentialErrorOverride_shouldUseOverride() {
        HypernovaError overrideError = new HypernovaError("Override Error", Collections.singletonList("override stack"));
        JobResult result = JobResult.fromRawResult(rawResultSuccess, mockJob, overrideError); // rawResultSuccess has no error

        assertEquals(overrideError, result.getError());
        assertEquals("Override Error", result.getError().getMessage());
        assertEquals("<p>Success HTML</p>", result.getHtml()); // HTML from raw result
        assertTrue(result.isSuccess()); // Success from raw result
        assertEquals(mockJob, result.getOriginalJob());
        assertEquals(Map.of("metaKey", "metaValue"), result.getMeta());
        assertEquals(123.45, result.getDuration());
    }

    @Test
    void fromRawResult_withPotentialErrorOverride_andErrorInRawResult_shouldUseOverride() {
        HypernovaError overrideError = new HypernovaError("Override Error Two", Collections.singletonList("override stack two"));
        // rawResultError already has an error, but override should take precedence
        JobResult result = JobResult.fromRawResult(rawResultError, mockJob, overrideError);

        assertEquals(overrideError, result.getError());
        assertEquals("Override Error Two", result.getError().getMessage());
        assertEquals("<p>Error Fallback HTML</p>", result.getHtml()); // HTML from raw result
        assertFalse(result.isSuccess()); // Success from raw result
        assertEquals(mockJob, result.getOriginalJob());
        assertEquals(Map.of("metaErrorKey", "metaErrorValue"), result.getMeta());
        assertEquals(67.89, result.getDuration());
    }


    @Test
    void fallbackConstructor_shouldSetFieldsCorrectly() {
        String html = "<p>Fallback HTML</p>";
        Map<String, String> meta = Map.of("fallbackMeta", "value");
        double duration = 0.0;

        JobResult result = new JobResult(mockError, html, false, mockJob, meta, duration);

        assertEquals(mockError, result.getError());
        assertEquals(html, result.getHtml());
        assertFalse(result.isSuccess());
        assertEquals(mockJob, result.getOriginalJob());
        assertEquals(meta, result.getMeta());
        assertEquals(duration, result.getDuration());
    }
    
    @Test
    void fallbackConstructor_withNullError_shouldSetFieldsCorrectly() {
        String html = "<p>No Error Fallback HTML</p>";
        Map<String, String> meta = Map.of("fallbackMeta", "value");
        double duration = 10.5;

        JobResult result = new JobResult(null, html, true, mockJob, meta, duration);

        assertNull(result.getError());
        assertEquals(html, result.getHtml());
        assertTrue(result.isSuccess());
        assertEquals(mockJob, result.getOriginalJob());
        assertEquals(meta, result.getMeta());
        assertEquals(duration, result.getDuration());
    }

    @Test
    void toString_shouldReturnHtmlContent() {
        JobResult result = new JobResult(null, "<p>My HTML Content</p>", true, mockJob, Collections.emptyMap(), 0);
        assertEquals("<p>My HTML Content</p>", result.toString());
    }

    @Test
    void toString_withError_shouldStillReturnHtmlContent() {
        JobResult result = new JobResult(mockError, "<p>Error Fallback</p>", false, mockJob, Collections.emptyMap(), 0);
        assertEquals("<p>Error Fallback</p>", result.toString());
    }
}
