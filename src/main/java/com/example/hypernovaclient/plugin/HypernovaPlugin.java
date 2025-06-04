package com.example.hypernovaclient.plugin;

import com.example.hypernovaclient.model.HypernovaJob;
import com.example.hypernovaclient.model.HypernovaJobResult;

import java.util.Map;

/**
 * Interface for Hypernova client plugins.
 * Plugins allow developers to hook into various stages of the rendering lifecycle
 * to modify data, handle events, or control request flow.
 */
public interface HypernovaPlugin {

    /**
     * Modifies the data for a specific job before it's prepared for the batch request.
     * This hook is called for each job individually.
     *
     * @param jobName The name of the component for the current job.
     * @param data The current data map for the job.
     * @return A potentially modified data map for the job.
     */
    Map<String, Object> getViewData(String jobName, Map<String, Object> data);

    /**
     * Prepares the entire map of jobs before it is sent to the Hypernova server.
     * This allows for modification of the job collection, such as adding, removing,
     * or altering jobs.
     *
     * @param jobs A mutable map of job IDs to {@link HypernovaJob} objects that will be sent.
     *             Plugins can modify this map.
     * @param originalJobs A read-only map of the jobs as they were initially added or after
     *                     the {@code getViewData} hook, for reference.
     * @return The (potentially modified) map of jobs to be sent.
     */
    Map<String, HypernovaJob> prepareRequest(Map<String, HypernovaJob> jobs, Map<String, HypernovaJob> originalJobs);

    /**
     * Determines whether the batch request should be sent to the Hypernova server.
     * If any plugin returns {@code false}, the request is cancelled, and a fallback
     * response is generated for all jobs.
     *
     * @param jobs The map of jobs that would be sent.
     * @return {@code true} to proceed with the request, {@code false} to cancel.
     */
    boolean shouldSendRequest(Map<String, HypernovaJob> jobs);

    /**
     * Called just before the batch request is sent to the Hypernova server.
     * This is a notification hook and does not modify the request.
     *
     * @param jobs The final map of jobs being sent.
     */
    void willSendRequest(Map<String, HypernovaJob> jobs);

    /**
     * Called when a single job in a batch successfully renders.
     *
     * @param jobResult The {@link HypernovaJobResult} for the successful job.
     *                  The {@code originalJob} field within {@code jobResult} will be populated.
     */
    void onSuccess(HypernovaJobResult jobResult);

    /**
     * Called when a single job in a batch fails to render on the server
     * (i.e., the job result contains an error object).
     *
     * @param jobResult The {@link HypernovaJobResult} for the failed job.
     *                  The {@code originalJob} field within {@code jobResult} will be populated.
     *                  The {@code error} field will contain details of the failure.
     */
    void onJobError(HypernovaJobResult jobResult);

    /**
     * Called when an error occurs that affects the entire batch request.
     * This could be due to an HTTP error, a network issue, or a top-level error
     * object returned by the Hypernova server in its response.
     *
     * @param error The error object or exception that occurred.
     * @param jobs The map of jobs that were part of the failed batch request.
     */
    void onBatchError(Object error, Map<String, HypernovaJob> jobs);

    /**
     * Called after the Hypernova server response has been processed (or a fallback generated)
     * but before the final {@link HypernovaResponse} is returned to the caller.
     * This allows plugins to modify the job results map (e.g., add custom fallbacks,
     * transform HTML, etc.).
     *
     * @param jobResults A mutable map of job IDs to {@link HypernovaJobResult} objects.
     *                   Plugins can modify this map.
     * @return The (potentially modified) map of job results.
     */
    Map<String, HypernovaJobResult> afterResponse(Map<String, HypernovaJobResult> jobResults);
}
