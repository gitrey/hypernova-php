package com.example.hypernova;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;
// Removed redundant import java.util.HashMap;

/**
 * Represents the entire response from a Hypernova server for a batch of rendering jobs.
 * It includes a map of job results and an optional top-level error if the entire batch failed.
 */
public class HypernovaResponse {
    private HypernovaError error;
    private Map<String, JobResult> results;

    /**
     * Default constructor. Initializes an empty response with no top-level error and an empty results map.
     * This is used by {@link HypernovaRenderer} when creating responses internally, especially for fallback scenarios.
     */
    public HypernovaResponse() {
        this.results = new HashMap<>();
        this.error = null;
    }

    /**
     * Constructs a HypernovaResponse, typically used during deserialization from a server's JSON response.
     *
     * @param error        The top-level error object from the server. This can be a {@link Map}
     *                     that gets converted to {@link HypernovaError}, or null/false if no top-level error.
     * @param resultsData  A map where keys are job identifiers (e.g., UUIDs) and values are maps
     *                     representing the raw data for each {@link JobResult} from the server.
     * @param originalJobs A map of the original {@link Job} objects submitted in the request, keyed by their
     *                     identifiers. This is used to link each {@link JobResult} back to its original {@link Job}.
     *                     This map can be null if no original jobs are available or applicable for the context
     *                     (e.g. when deserializing a response where original job context is lost or not needed).
     */
    public HypernovaResponse(
            @JsonProperty("error") Object error, // Can be Boolean (false) or Map from JSON
            @JsonProperty("results") Map<String, Map<String, Object>> resultsData,
            Map<String, Job> originalJobs) {

        if (error instanceof Map) {
            ObjectMapper mapper = new ObjectMapper(); // Consider injecting or reusing
            this.error = mapper.convertValue(error, HypernovaError.class);
        } else {
            this.error = null; // Handles cases where error is null or boolean 'false'
        }

        this.results = new HashMap<>();
        if (resultsData != null) {
            for (Map.Entry<String, Map<String, Object>> entry : resultsData.entrySet()) {
                String jobIdentifier = entry.getKey();
                Map<String, Object> jobResultData = entry.getValue();
                Job currentOriginalJob = (originalJobs != null) ? originalJobs.get(jobIdentifier) : null;
                if (currentOriginalJob != null) {
                    this.results.put(jobIdentifier, JobResult.fromServerResult(jobResultData, currentOriginalJob));
                } else {
                    // If originalJob is not found, a JobResult cannot be properly constructed with its context by fromServerResult.
                    // Depending on strictness, could throw an error or create a JobResult with null originalJob.
                    // For robustness, log a warning and skip, or create a JobResult with null originalJob if fromServerResult supports it.
                    // Current JobResult.fromServerResult expects a non-null originalJob.
                    System.err.println("Warning: Original job not found for result key: " + jobIdentifier + ". This result will be skipped as it cannot be fully contextualized.");
                }
            }
        }
    }

    /**
     * Gets the top-level error for the entire batch request, if one occurred.
     * This is typically null if individual jobs have errors but the batch request itself was successful.
     * @return The {@link HypernovaError} if a top-level error occurred, or null otherwise.
     */
    public HypernovaError getError() {
        return error;
    }

    /**
     * Sets the top-level error for this response.
     * Used by {@link HypernovaRenderer} when a request-level error occurs (e.g., HTTP error or plugin failure).
     * @param error The {@link HypernovaError} to set.
     */
    public void setError(HypernovaError error) {
        this.error = error;
    }

    /**
     * Gets the map of job results.
     * The keys are the identifiers provided when jobs were added to the renderer.
     * @return A map of job identifiers to their corresponding {@link JobResult}.
     */
    public Map<String, JobResult> getResults() {
        return results;
    }

    /**
     * Sets the results map for this response.
     * Used by {@link HypernovaRenderer} to populate the response with processed job results.
     * @param results A map of job identifiers to {@link JobResult}.
     */
    public void setResults(Map<String, JobResult> results) {
        this.results = results;
    }
}
