package com.example.demo.hypernova.plugin;

import com.example.demo.hypernova.HypernovaJob;
import com.example.demo.hypernova.HypernovaJobResult;

import java.util.List;
import java.util.Map;

/**
 * Interface for plugins that can hook into the Hypernova rendering lifecycle.
 * Plugins allow for customization of job data, request preparation, response processing,
 * and error handling.
 */
public interface HypernovaPlugin {

    /**
     * Called after a job is created but before it is added to the batch for processing.
     * Allows modification of the job's name, data, or metadata.
     * The originalJob parameter provides the job as it was initially added by the client.
     *
     * @param viewName The name of the view/component for the current job being processed.
     * @param data The data map for the current job. This map is mutable and changes will be
     *             reflected in the job passed to subsequent plugins or to Hypernova, unless
     *             a new HypernovaJob is returned.
     * @param originalJob The original HypernovaJob instance as added by the client code.
     *                    This should generally be treated as read-only for context.
     * @return The processed HypernovaJob, which might be a new instance or the modified input job.
     *         If null is returned, the job will be dropped from the current rendering batch.
     */
    HypernovaJob getViewData(String viewName, Map<String, Object> data, HypernovaJob originalJob);

    /**
     * Called before the list of jobs is sent to the Hypernova service.
     * Allows for modification of the entire list of jobs (e.g., reordering, filtering, adding new jobs).
     *
     * @param jobs The current list of HypernovaJob instances to be sent. This list is mutable if the plugin
     *             chooses to modify it directly, though returning a new list is often safer.
     * @param originalJobs An unmodifiable list of the initial HypernovaJob instances (as added by client) for context.
     * @return The list of jobs to be sent to Hypernova. If null is returned, the job list
     *         from before this plugin call will be used by the renderer.
     */
    List<HypernovaJob> prepareRequest(List<HypernovaJob> jobs, List<HypernovaJob> originalJobs);

    /**
     * Determines whether the batch request to Hypernova should be sent.
     * If any plugin returns false, the request is skipped, and a fallback is attempted for all jobs.
     *
     * @param jobs An unmodifiable list of HypernovaJob instances that would be sent.
     * @return True if the request should be sent, false otherwise.
     */
    boolean shouldSendRequest(List<HypernovaJob> jobs);

    /**
     * Called just before the HTTP request is made to the Hypernova service.
     *
     * @param jobs An unmodifiable list of HypernovaJob instances being sent.
     */
    void willSendRequest(List<HypernovaJob> jobs);

    /**
     * Called for each job that was successfully rendered by Hypernova.
     *
     * @param jobResult The HypernovaJobResult for the successfully rendered job.
     *                  Plugins can modify this result (e.g., add metadata) if the passed jobResult is mutable.
     */
    void onSuccess(HypernovaJobResult jobResult);

    /**
     * Called when an error occurs during the rendering process.
     * This can be a top-level error (e.g., Hypernova service unreachable) or errors
     * specific to individual jobs if the service returned per-job error details.
     *
     * @param error The error object. Can be an Exception or an error structure from Hypernova.
     * @param jobResults An unmodifiable list of job results. This may be null if the error occurred before
     *                   results were available. For per-job errors, this list might contain
     *                   the specific job that failed.
     * @param originalJobs An unmodifiable list of the original HypernovaJob instances related to the error context.
     *                     This could be the specific jobs that failed or all jobs in the batch
     *                     if the error is top-level.
     */
    void onError(Object error, List<HypernovaJobResult> jobResults, List<HypernovaJob> originalJobs);

    /**
     * Called after the response from Hypernova has been processed (including per-job onSuccess/onError calls)
     * but before the final HypernovaResponse is returned to the caller.
     * Allows for modification of the map of job results. Keys are client-side job IDs.
     *
     * @param jobResults The current map of job results, keyed by their original client-side identifiers.
     *                   This map is mutable if the plugin chooses to modify it directly.
     * @return The final map of job results. If null is returned, the results map
     *         from before this plugin call will be used by the renderer.
     */
    Map<String, HypernovaJobResult> afterResponse(Map<String, HypernovaJobResult> jobResults);
}
