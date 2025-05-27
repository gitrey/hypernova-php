package com.example.hypernova;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

/**
 * Represents the result of a single Hypernova rendering job.
 * It includes the rendered HTML (if successful), any errors, metadata, and performance information.
 */
public class JobResult {
    private final HypernovaError error;
    private final String html;
    private final boolean success;
    @JsonIgnore // Prevents Jackson from trying to serialize/deserialize this directly from the main payload
    private final Job originalJob;
    private final Map<String, String> meta;
    private final double duration;

    /**
     * Constructs a new JobResult.
     * This constructor is typically used for creating fallback job results or for direct instantiation.
     *
     * @param error       The error encountered during rendering, or null if successful.
     * @param html        The rendered HTML content, or fallback HTML in case of an error.
     * @param success     A boolean indicating whether the rendering was successful.
     * @param originalJob The original {@link Job} this result corresponds to.
     * @param meta        A map of metadata associated with this job result.
     * @param duration    The duration of the rendering process in milliseconds.
     */
    public JobResult(HypernovaError error, String html, boolean success, Job originalJob, Map<String, String> meta, double duration) {
        this.error = error;
        this.html = html;
        this.success = success;
        this.originalJob = originalJob;
        this.meta = meta;
        this.duration = duration;
    }

    /**
     * Creates a {@link JobResult} from a map of data typically received from the Hypernova server.
     * This method is used by {@link HypernovaResponse} during deserialization.
     *
     * @param serverResultData A map containing the raw job result data from the server.
     * @param originalJob The original {@link Job} this result corresponds to.
     * @return A new {@link JobResult} instance.
     */
    @SuppressWarnings("unchecked")
    public static JobResult fromServerResult(Map<String, Object> serverResultData, Job originalJob) {
        HypernovaError error = null;
        if (serverResultData.get("error") != null) {
            ObjectMapper mapper = new ObjectMapper(); // Consider injecting or reusing ObjectMapper
            error = mapper.convertValue(serverResultData.get("error"), HypernovaError.class);
        }
        String html = (String) serverResultData.get("html");
        boolean success = (Boolean) serverResultData.get("success");
        Map<String, String> meta = (Map<String, String>) serverResultData.get("meta");
        double duration = ((Number) serverResultData.get("duration")).doubleValue();

        return new JobResult(error, html, success, originalJob, meta, duration);
    }

    /**
     * Creates a {@link JobResult} from a {@link HypernovaRenderer.HypernovaRawJobResult}.
     * This factory method is used by the {@link HypernovaRenderer} to process results from the server.
     *
     * @param rawResult The raw job result data object.
     * @param originalJob The original {@link Job} this result corresponds to.
     * @param potentialErrorOverride An optional {@link HypernovaError} that can override any error in the rawResult.
     *                               This is used, for example, when a top-level request error occurs.
     * @return A new {@link JobResult} instance.
     */
    public static JobResult fromRawResult(HypernovaRenderer.HypernovaRawJobResult rawResult, Job originalJob, HypernovaError potentialErrorOverride) {
        HypernovaError finalError = potentialErrorOverride != null ? potentialErrorOverride : rawResult.error();
        return new JobResult(
                finalError,
                rawResult.html(),
                rawResult.success(),
                originalJob,
                rawResult.meta(),
                rawResult.duration()
        );
    }

    /**
     * Gets the error associated with this job result.
     * @return The {@link HypernovaError} if an error occurred, or null otherwise.
     */
    public HypernovaError getError() {
        return error;
    }

    /**
     * Gets the rendered HTML content.
     * If rendering failed, this might be fallback HTML or null.
     * @return The HTML string.
     */
    public String getHtml() {
        return html;
    }

    /**
     * Checks if the rendering was successful.
     * @return True if successful, false otherwise.
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * Gets the original {@link Job} that this result corresponds to.
     * This is useful for context, especially in plugins or when processing results.
     * @return The original {@link Job}.
     */
    public Job getOriginalJob() {
        return originalJob;
    }

    /**
     * Gets the metadata associated with this job result.
     * This metadata can come from the server or be added by plugins.
     * @return A map of metadata.
     */
    public Map<String, String> getMeta() {
        return meta;
    }

    /**
     * Gets the duration of the rendering process on the server-side.
     * @return The duration in milliseconds.
     */
    public double getDuration() {
        return duration;
    }

    /**
     * Returns the HTML content of this job result.
     * This provides a simple way to get the rendered output, often used for direct embedding.
     * @return The HTML string.
     */
    @Override
    public String toString() {
        return html;
    }
}
