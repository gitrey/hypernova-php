package com.example.hypernovaclient.model;

import java.util.Map;

/**
 * Represents the overall response from a Hypernova server for a batch of jobs.
 * It contains a map of job results, keyed by the original job ID, and an optional
 * top-level error object if the entire batch request failed.
 */
public class HypernovaResponse {

    /**
     * A map where keys are job IDs (as provided by the client in the request)
     * and values are {@link HypernovaJobResult} objects detailing the outcome of each job.
     */
    private Map<String, HypernovaJobResult> results;

    /**
     * An object detailing a top-level error that might have occurred,
     * affecting the entire batch request (e.g., server unable to process the batch).
     * Null if no batch-level error occurred. Individual job errors are found within
     * their respective {@link HypernovaJobResult} in the {@code results} map.
     */
    private Object error;

    /**
     * Default constructor, often used by JSON deserialization libraries like Jackson.
     */
    public HypernovaResponse() {
        // Default constructor for Jackson
    }

    /**
     * Gets the map of job results.
     * @return A map of job IDs to {@link HypernovaJobResult} objects.
     */
    public Map<String, HypernovaJobResult> getResults() {
        return results;
    }

    /**
     * Sets the map of job results.
     * @param results A map of job IDs to {@link HypernovaJobResult} objects.
     */
    public void setResults(Map<String, HypernovaJobResult> results) {
        this.results = results;
    }

    /**
     * Gets the top-level error object for the batch request.
     * @return The error object (e.g., Map, String), or null if no batch-level error.
     */
    public Object getError() {
        return error;
    }

    /**
     * Sets the top-level error object for the batch request.
     * @param error The error object.
     */
    public void setError(Object error) {
        this.error = error;
    }
}
