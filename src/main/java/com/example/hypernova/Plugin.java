package com.example.hypernova;

import java.util.List;
import java.util.Map;

/**
 * Defines the contract for plugins that can hook into various stages of the Hypernova rendering lifecycle.
 * Plugins allow for modifying job data, altering request/response flows, and custom logging or error handling.
 */
public interface Plugin {

    /**
     * Called before a job's data is finalized, allowing modification of the data sent to the component.
     * This hook is called for each job individually.
     *
     * @param name The name of the component associated with the job.
     * @param data The current data map for the job.
     * @return The (potentially modified) data map for the job.
     */
    Map<String, Object> getViewData(String name, Map<String, Object> data);

    /**
     * Called before a batch of jobs is sent to the Hypernova server, after all jobs have been processed by `getViewData`.
     * This allows for modification of the entire batch of jobs, e.g., adding common data or filtering jobs.
     *
     * @param jobs         A mutable map of jobs to be sent, keyed by their unique identifiers.
     *                     Plugins can modify this map (e.g., add, remove, or change jobs).
     * @param originalJobs A read-only map of the original jobs as they were added to the renderer,
     *                     keyed by their unique identifiers. This is for reference and should not be modified.
     * @return The (potentially modified) map of jobs to be sent to the server.
     */
    Map<String, Job> prepareRequest(Map<String, Job> jobs, Map<String, Job> originalJobs);

    /**
     * Determines whether the current batch of jobs should be sent to the Hypernova server.
     * If any plugin returns `false`, the request is aborted, and fallback HTML is generated for all jobs.
     *
     * @param jobs A read-only map of jobs that are about to be sent.
     * @return `true` if the request should proceed, `false` to abort.
     */
    boolean shouldSendRequest(Map<String, Job> jobs);

    /**
     * Called just before the HTTP request is made to the Hypernova server.
     * This is a good place for logging the outgoing request or performing last-minute checks.
     *
     * @param jobs A read-only map of jobs that will be sent.
     */
    void willSendRequest(Map<String, Job> jobs);

    /**
     * Called when an error occurs, either at the top level (e.g., HTTP error, entire batch failed on server)
     * or for individual jobs that failed to render.
     *
     * @param error The {@link HypernovaError} that occurred.
     * @param jobs  A list of {@link Job} instances that were affected by this error. In the case of a top-level
     *              error, this list might contain all jobs in the batch. For individual job errors, it might
     *              contain a single job.
     */
    void onError(HypernovaError error, List<Job> jobs);

    /**
     * Called when a single job has been successfully rendered by the Hypernova server.
     *
     * @param jobResult The {@link JobResult} for the successfully rendered job.
     */
    void onSuccess(JobResult jobResult);

    /**
     * Called after the entire batch response has been processed (including successful and failed jobs)
     * and individual `onSuccess` or `onError` hooks have been triggered.
     * This allows for modification of the final map of job results before it's returned to the caller.
     *
     * @param jobResults A mutable map of job results, keyed by their unique identifiers.
     *                   Plugins can modify this map.
     * @return The (potentially modified) map of job results.
     */
    Map<String, JobResult> afterResponse(Map<String, JobResult> jobResults);
}
