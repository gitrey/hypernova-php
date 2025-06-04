package com.example.hypernovaclient;

import com.example.hypernovaclient.model.HypernovaJob;
import com.example.hypernovaclient.model.HypernovaJobResult;
import com.example.hypernovaclient.model.HypernovaResponse;
import com.example.hypernovaclient.plugin.HypernovaPlugin;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.*;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Main class for rendering components via a Hypernova server.
 * <p>
 * The {@code HypernovaRenderer} is responsible for:
 * <ul>
 *     <li>Collecting jobs to be rendered (see {@link #addJob(String, HypernovaJob)}).</li>
 *     <li>Managing the lifecycle of rendering requests, including plugin interactions.</li>
 *     <li>Sending batch requests to the Hypernova server.</li>
 *     <li>Processing responses and associating results with original jobs.</li>
 *     <li>Generating fallback HTML for failed renders or in case of errors.</li>
 * </ul>
 * This class is typically configured and used as a Spring bean.
 * </p>
 * <p>
 * Plugin Lifecycle:
 * <ol>
 *     <li>{@link HypernovaPlugin#getViewData(String, Map)}: For each job, before any other processing.</li>
 *     <li>{@link HypernovaPlugin#prepareRequest(Map, Map)}: Once for the batch, before deciding to send.</li>
 *     <li>{@link HypernovaPlugin#shouldSendRequest(Map)}: Once for the batch. If any plugin returns false, request is aborted.</li>
 *     <li>{@link HypernovaPlugin#willSendRequest(Map)}: Once for the batch, just before HTTP call.</li>
 *     <li>HTTP Request to Hypernova server.</li>
 *     <li>On error during HTTP request (network, HTTP status):
 *         <ul><li>{@link HypernovaPlugin#onBatchError(Object, Map)} is called.</li></ul>
 *     </li>
 *     <li>On successful HTTP response:
 *         <ul>
 *             <li>If response JSON indicates a top-level batch error:
 *                 <ul><li>{@link HypernovaPlugin#onBatchError(Object, Map)} is called.</li></ul>
 *             </li>
 *             <li>For each job result from server:
 *                 <ul>
 *                     <li>If job result is an error: {@link HypernovaPlugin#onJobError(HypernovaJobResult)} is called.</li>
 *                     <li>If job result is successful: {@link HypernovaPlugin#onSuccess(HypernovaJobResult)} is called.</li>
 *                 </ul>
 *             </li>
 *         </ul>
 *     </li>
 *     <li>{@link HypernovaPlugin#afterResponse(Map)}: Once for the batch, after all results processed or fallbacks generated.</li>
 * </ol>
 * </p>
 */
public class HypernovaRenderer {

    private static final Logger logger = LoggerFactory.getLogger(HypernovaRenderer.class);

    private final String hypernovaUrl;
    private final List<HypernovaPlugin> plugins;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final Map<String, HypernovaJob> jobsToRender = new HashMap<>();

    /**
     * Constructs a new HypernovaRenderer.
     *
     * @param hypernovaUrl The URL of the Hypernova server's batch endpoint.
     * @param plugins A list of {@link HypernovaPlugin} instances to apply during rendering.
     *                Can be null or empty if no plugins are used. Plugins are executed in list order.
     * @param restTemplate The Spring {@link RestTemplate} to use for HTTP requests.
     * @param objectMapper The Jackson {@link ObjectMapper} for JSON serialization/deserialization.
     */
    public HypernovaRenderer(String hypernovaUrl, List<HypernovaPlugin> plugins, RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.hypernovaUrl = hypernovaUrl;
        this.plugins = plugins != null ? new ArrayList<>(plugins) : new ArrayList<>(); // Defensive copy
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Adds a job to the current batch to be rendered.
     * Jobs are collected until {@link #render()} is called.
     * After {@link #render()} is called, the internal list of jobs to render is cleared.
     *
     * @param id A unique identifier for this job within the current batch.
     *           This ID is used to retrieve the corresponding {@link HypernovaJobResult}
     *           from the {@link HypernovaResponse}.
     * @param job The {@link HypernovaJob} containing component name and data.
     */
    public void addJob(String id, HypernovaJob job) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("Job ID cannot be null or empty.");
        }
        if (job == null) {
            throw new IllegalArgumentException("HypernovaJob cannot be null.");
        }
        this.jobsToRender.put(id, job);
    }

    /**
     * Generates the standard Hypernova fallback HTML structure for a given component.
     * This includes a div with {@code data-hypernova-key} and {@code data-hypernova-id},
     * and a script tag containing the component's data as JSON.
     *
     * @param jobName The name of the component (e.g., "MyComponent").
     * @param data The data (props) for the component.
     * @return An HTML string representing the fallback structure.
     */
    private String generateFallbackHtml(String jobName, Map<String, Object> data) {
        String dataJson;
        try {
            dataJson = objectMapper.writeValueAsString(data != null ? data : Collections.emptyMap());
        } catch (JsonProcessingException e) {
            logger.error("Error serializing data for fallback HTML for job: {}", jobName, e);
            dataJson = "{}"; // Fallback to empty JSON if serialization fails
        }
        String uuid = UUID.randomUUID().toString();
        // Ensure consistent attribute quoting for HTML validity and easier parsing if needed.
        return String.format(
                "<div data-hypernova-key=\"%s\" data-hypernova-id=\"%s\"></div>" +
                "<script type=\"application/json\" data-hypernova-key=\"%s\" data-hypernova-id=\"%s\"><!--%s--></script>",
                escapeHtmlAttribute(jobName), escapeHtmlAttribute(uuid),
                escapeHtmlAttribute(jobName), escapeHtmlAttribute(uuid), dataJson
        );
    }

    /**
     * Helper to escape HTML attribute values.
     * A more comprehensive library might be preferred for full XSS safety if job names can be arbitrary.
     * @param value The string to escape.
     * @return The escaped string.
     */
    private String escapeHtmlAttribute(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;")
                    .replace("\"", "&quot;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;");
    }


    /**
     * Generates a {@link HypernovaResponse} where all provided jobs are marked as failed,
     * typically used when a batch-level error occurs (e.g., network error, request cancelled by plugin).
     * Each job result will contain the provided error and fallback HTML.
     *
     * @param jobs The map of jobs that were intended for rendering.
     * @param error The error (e.g., Exception, String message) that caused the fallback.
     * @return A {@link HypernovaResponse} with all jobs in a fallback state.
     */
    private HypernovaResponse generateFallbackResponse(Map<String, HypernovaJob> jobs, Throwable error) {
        HypernovaResponse response = new HypernovaResponse();
        // Use error.toString() or getMessage() to avoid sending complex objects as top-level error in simple fallback.
        // The actual Throwable is passed to onBatchError plugins.
        response.setError(error != null ? error.getClass().getName() + ": " + error.getMessage() : "Unknown error triggering fallback response");
        response.setResults(new HashMap<>());

        String jobErrorMessage = error != null ? "Job fallback due to batch error: " + error.getMessage() : "Job fallback due to batch error";

        for (Map.Entry<String, HypernovaJob> entry : jobs.entrySet()) {
            String id = entry.getKey();
            HypernovaJob job = entry.getValue();
            HypernovaJobResult jobResult = new HypernovaJobResult();
            jobResult.setOriginalJob(job);
            // For individual job errors in this context, a simpler representation of the batch error is fine.
            jobResult.setError(Map.of("message", jobErrorMessage, "isBatchFallback", true));
            jobResult.setHtml(generateFallbackHtml(job.getName(), job.getData()));
            jobResult.setSuccess(false);
            jobResult.setViewName(job.getName());
            response.getResults().put(id, jobResult);
        }

        // This specific method `generateFallbackResponse` is often called *after* onBatchError might have already
        // been triggered (e.g., in the catch blocks of render()).
        // However, if called due to shouldSendRequest=false, onBatchError needs to be invoked here.
        // To avoid double-triggering, onBatchError is primarily handled in the main render() catch blocks.
        // If this path is taken *before* such a catch block (e.g. shouldSendRequest=false), then call it.
        // For simplicity in current structure, onBatchError is called in main render loop's error paths.
        // Consider if this specific call to onBatchError is needed or if it's always covered.
        // It IS needed for the shouldSendRequest=false path.
        // Let's assume error parameter here is the primary one to report for this fallback path.
        Object errorToReportToPlugins = error != null ? error : response.getError();
        for (HypernovaPlugin plugin : plugins) {
            try {
                plugin.onBatchError(errorToReportToPlugins, jobs);
            } catch (Exception e_plugin) {
                logger.error("Error in plugin {} during onBatchError (fallback response generation): {}", plugin.getClass().getName(), e_plugin.getMessage(), e_plugin);
            }
        }
        return response;
    }

    /**
     * Renders the batch of jobs that have been added via {@link #addJob(String, HypernovaJob)}.
     * This method orchestrates the entire rendering lifecycle, including:
     * <ol>
     *     <li>Invoking {@code getViewData} plugin hook for each job.</li>
     *     <li>Invoking {@code prepareRequest} plugin hook for the batch.</li>
     *     <li>Invoking {@code shouldSendRequest} plugin hook. If any plugin cancels, it generates a fallback response.</li>
     *     <li>Invoking {@code willSendRequest} plugin hook.</li>
     *     <li>Sending the batch request to the Hypernova server.</li>
     *     <li>Processing the server's response:
     *         <ul>
     *             <li>Handling batch-level errors and invoking {@code onBatchError}.</li>
     *             <li>For each job result, invoking {@code onSuccess} or {@code onJobError}.</li>
     *             <li>Generating fallback HTML if a job failed and the server didn't provide HTML.</li>
     *         </ul>
     *     </li>
     *     <li>Handling exceptions during communication (HTTP errors, network issues) and invoking {@code onBatchError}.</li>
     *     <li>Invoking {@code afterResponse} plugin hook.</li>
     * </ol>
     * After this method completes (successfully or with an exception), the internal list of jobs to render is cleared.
     *
     * @return A {@link HypernovaResponse} containing the results (rendered HTML or error details) for each job.
     */
    public HypernovaResponse render() {
        if (jobsToRender.isEmpty()) {
            // Call afterResponse even for empty jobs, some plugins might want to inject default content.
            HypernovaResponse emptyResponse = new HypernovaResponse();
            emptyResponse.setResults(new HashMap<>()); // Ensure results map is not null for plugins
             for (HypernovaPlugin plugin : plugins) {
                 try {
                    emptyResponse.setResults(plugin.afterResponse(emptyResponse.getResults()));
                } catch (Exception e) {
                    logger.error("Error in plugin {} during afterResponse for empty job set: {}", plugin.getClass().getName(), e.getMessage(), e);
                }
            }
            return emptyResponse;
        }

        Map<String, HypernovaJob> currentJobs = new HashMap<>(this.jobsToRender);
        this.jobsToRender.clear(); // Clear for the next batch

        // Plugin Hook: getViewData
        for (HypernovaPlugin plugin : plugins) {
            for (Map.Entry<String, HypernovaJob> entry : currentJobs.entrySet()) {
                HypernovaJob job = entry.getValue();
                try {
                    Map<String, Object> newData = plugin.getViewData(job.getName(), job.getData());
                    job.setData(newData); // Update job with potentially modified data
                } catch (Exception e) {
                    logger.error("Error in plugin {} during getViewData for job {}: {}", plugin.getClass().getName(), job.getName(), e.getMessage(), e);
                    // Continue processing other jobs/plugins even if one fails
                }
            }
        }

        // Plugin Hook: prepareRequest
        // Create a deep copy for originalJobs to ensure plugins cannot modify this reference map.
        Map<String, HypernovaJob> originalJobsForPrepareRequest = new HashMap<>();
        for(Map.Entry<String, HypernovaJob> entry : currentJobs.entrySet()){
             originalJobsForPrepareRequest.put(entry.getKey(), new HypernovaJob(entry.getValue().getName(),
                 new HashMap<>(entry.getValue().getData()), // Shallow copy of data map
                 entry.getValue().getMetadata() != null ? new HashMap<>(entry.getValue().getMetadata()) : new HashMap<>() // Shallow copy of metadata
             ));
        }

        for (HypernovaPlugin plugin : plugins) {
            try {
                // Pass a defensive copy of currentJobs to prepareRequest, as plugins might return
                // a completely new map or modify the one passed.
                Map<String, HypernovaJob> jobsBeforePrepare = new HashMap<>(currentJobs);
                currentJobs = plugin.prepareRequest(jobsBeforePrepare, Collections.unmodifiableMap(originalJobsForPrepareRequest));
                if (currentJobs == null) { // Plugin might nullify jobs
                    currentJobs = new HashMap<>(); // Ensure it's an empty map, not null
                    logger.warn("Plugin {} returned null from prepareRequest. Assuming no jobs to render.", plugin.getClass().getName());
                }
            } catch (Exception e) {
                logger.error("Error in plugin {} during prepareRequest: {}. Current jobs may not reflect plugin changes.", plugin.getClass().getName(), e.getMessage(), e);
                // Continue with currentJobs as they are if plugin fails
            }
        }

        if (currentJobs.isEmpty()) {
            logger.warn("No jobs to render after plugin prepareRequest modifications or initial state.");
            HypernovaResponse emptyResponse = new HypernovaResponse();
            emptyResponse.setResults(new HashMap<>());
            for (HypernovaPlugin plugin : plugins) {
                 try {
                    emptyResponse.setResults(plugin.afterResponse(emptyResponse.getResults()));
                } catch (Exception e) {
                    logger.error("Error in plugin {} during afterResponse for empty job set (post-prepareRequest): {}", plugin.getClass().getName(), e.getMessage(), e);
                }
            }
            return emptyResponse;
        }

        // Plugin Hook: shouldSendRequest
        for (HypernovaPlugin plugin : plugins) {
            try {
                if (!plugin.shouldSendRequest(currentJobs)) {
                    logger.info("Request to Hypernova cancelled by plugin: {}", plugin.getClass().getName());
                    // Note: generateFallbackResponse now also calls onBatchError for this path.
                    return generateFallbackResponse(currentJobs, new RuntimeException("Request cancelled by plugin " + plugin.getClass().getName()));
                }
            } catch (Exception e) {
                logger.error("Error in plugin {} during shouldSendRequest: {}. Assuming request should not be sent.", plugin.getClass().getName(), e.getMessage(), e);
                 return generateFallbackResponse(currentJobs, e); // Treat plugin error as reason not to send
            }
        }

        HypernovaResponse hypernovaResponseFromServer;
        try {
            // Plugin Hook: willSendRequest
            for (HypernovaPlugin plugin : plugins) {
                try {
                    plugin.willSendRequest(currentJobs);
                } catch (Exception e) {
                    logger.error("Error in plugin {} during willSendRequest: {}", plugin.getClass().getName(), e.getMessage(), e);
                    // Continue with request even if this hook fails
                }
            }

            String requestBody = objectMapper.writeValueAsString(currentJobs);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> requestEntity = new HttpEntity<>(requestBody, headers);

            logger.debug("Sending request to Hypernova: URL: {}, Body: {}", hypernovaUrl, requestBody);
            String rawResponse = restTemplate.postForObject(hypernovaUrl, requestEntity, String.class);
            logger.debug("Received response from Hypernova: {}", rawResponse);

            if (rawResponse == null || rawResponse.trim().isEmpty()) {
                logger.error("Received null or empty response from Hypernova server at URL: {}", hypernovaUrl);
                throw new IllegalStateException("Received null or empty response from Hypernova server.");
            }
            hypernovaResponseFromServer = objectMapper.readValue(rawResponse, HypernovaResponse.class);

        } catch (HttpStatusCodeException e) { // Specific HTTP errors (4xx, 5xx)
            logger.error("HTTP error calling Hypernova: {} - {}. Response body: {}", e.getStatusCode(), e.getMessage(), e.getResponseBodyAsString(), e);
            for (HypernovaPlugin plugin : plugins) {
                try {
                    plugin.onBatchError(e, currentJobs);
                } catch (Exception pluginEx) {
                    logger.error("Error in plugin {} during onBatchError (HttpStatusCodeException): {}", plugin.getClass().getName(), pluginEx.getMessage(), pluginEx);
                }
            }
            return generateFallbackResponse(currentJobs, e);
        } catch (JsonProcessingException e) { // Errors in request/response JSON processing
            logger.error("JSON processing error during Hypernova request/response: {}", e.getMessage(), e);
             for (HypernovaPlugin plugin : plugins) {
                try {
                    plugin.onBatchError(e, currentJobs); // Pass the specific exception
                } catch (Exception pluginEx) {
                    logger.error("Error in plugin {} during onBatchError (JsonProcessingException): {}", plugin.getClass().getName(), pluginEx.getMessage(), pluginEx);
                }
            }
            return generateFallbackResponse(currentJobs, e);
        } catch (ResourceAccessException e) { // Network errors like connection refused, timeouts through RestTemplate
            logger.error("Network error calling Hypernova: {}", e.getMessage(), e);
            for (HypernovaPlugin plugin : plugins) {
                try {
                    plugin.onBatchError(e, currentJobs);
                } catch (Exception pluginEx) {
                    logger.error("Error in plugin {} during onBatchError (ResourceAccessException): {}", plugin.getClass().getName(), pluginEx.getMessage(), pluginEx);
                }
            }
            return generateFallbackResponse(currentJobs,e);
        }
         catch (Exception e) { // Catch other unexpected exceptions during request execution
            logger.error("Unexpected error during Hypernova request execution: {}", e.getMessage(), e);
             for (HypernovaPlugin plugin : plugins) {
                try {
                    plugin.onBatchError(e, currentJobs);
                } catch (Exception pluginEx) {
                    logger.error("Error in plugin {} during onBatchError (General Exception): {}", plugin.getClass().getName(), pluginEx.getMessage(), pluginEx);
                }
            }
            return generateFallbackResponse(currentJobs, e);
        }

        // Process Results
        HypernovaResponse finalResponse = new HypernovaResponse();
        finalResponse.setResults(new HashMap<>()); // Ensure results map is initialized

        if (hypernovaResponseFromServer == null) {
             // This case should ideally be caught by rawResponse check, but as a safeguard:
             logger.error("Hypernova response object (hypernovaResponseFromServer) is null after request, generating fallback.");
             // Call onBatchError plugins
             RuntimeException error = new IllegalStateException("Received null deserialized response from server");
             for (HypernovaPlugin plugin : plugins) {
                try {
                    plugin.onBatchError(error, currentJobs);
                } catch (Exception pluginEx) {
                    logger.error("Error in plugin {} during onBatchError (Null Deserialized Response): {}", plugin.getClass().getName(), pluginEx.getMessage(), pluginEx);
                }
            }
             return generateFallbackResponse(currentJobs, error);
        }

        finalResponse.setError(hypernovaResponseFromServer.getError());
        if (finalResponse.getError() != null) { // Top-level error from Hypernova server
            for (HypernovaPlugin plugin : plugins) {
                try {
                    // Pass the server's error object and the original jobs map
                    plugin.onBatchError(finalResponse.getError(), currentJobs);
                } catch (Exception e) {
                     logger.error("Error in plugin {} during onBatchError (server error object present): {}", plugin.getClass().getName(), e.getMessage(), e);
                }
            }
            // If there's a top-level error, all original jobs effectively failed at batch level.
            // Generate fallbacks for any job that doesn't have a specific result from the server (likely all of them).
            // This ensures consistency: if batch fails, all requested jobs get a fallback result.
            for (Map.Entry<String, HypernovaJob> jobEntry : currentJobs.entrySet()) {
                String jobId = jobEntry.getKey();
                if (hypernovaResponseFromServer.getResults() == null || !hypernovaResponseFromServer.getResults().containsKey(jobId)) {
                    HypernovaJob originalJob = jobEntry.getValue();
                    HypernovaJobResult fallbackResult = new HypernovaJobResult();
                    fallbackResult.setOriginalJob(originalJob);
                    fallbackResult.setError(finalResponse.getError()); // Attribute batch error to job
                    fallbackResult.setHtml(generateFallbackHtml(originalJob.getName(), originalJob.getData()));
                    fallbackResult.setSuccess(false);
                    fallbackResult.setViewName(originalJob.getName());
                    finalResponse.getResults().put(jobId, fallbackResult);
                     // Also call onJobError for these implicitly failed jobs due to batch error
                    for (HypernovaPlugin p : plugins) {
                        try { p.onJobError(fallbackResult); }
                        catch (Exception e) { logger.error("Error in plugin {} during onJobError (batch error fallback): {}", p.getClass().getName(), e.getMessage(), e); }
                    }
                }
            }
        }

        // Process individual job results if present
        if (hypernovaResponseFromServer.getResults() != null) {
             for (Map.Entry<String, HypernovaJobResult> entry : hypernovaResponseFromServer.getResults().entrySet()) {
                String id = entry.getKey();
                HypernovaJobResult jobResultFromServer = entry.getValue();
                HypernovaJob originalJob = currentJobs.get(id);

                if (originalJob == null) {
                    logger.warn("Received result for job ID '{}' but no such job was found in currentJobs (post-plugin modification). Skipping.", id);
                    continue;
                }

                jobResultFromServer.setOriginalJob(originalJob); // Associate original job with its result
                if (jobResultFromServer.getViewName() == null || jobResultFromServer.getViewName().isEmpty()){
                     jobResultFromServer.setViewName(originalJob.getName()); // Ensure viewName is set
                }


                if (jobResultFromServer.getError() != null) { // Job-specific error from server
                    for (HypernovaPlugin plugin : plugins) {
                        try {
                            plugin.onJobError(jobResultFromServer);
                        } catch (Exception e) {
                            logger.error("Error in plugin {} during onJobError for job {}: {}", plugin.getClass().getName(), id, e.getMessage(), e);
                        }
                    }
                    if (jobResultFromServer.getHtml() == null) { // No server-side fallback HTML for this error
                        jobResultFromServer.setHtml(generateFallbackHtml(originalJob.getName(), originalJob.getData()));
                    }
                } else if (jobResultFromServer.isSuccess()) { // Job success
                    for (HypernovaPlugin plugin : plugins) {
                        try {
                            plugin.onSuccess(jobResultFromServer);
                        } catch (Exception e) {
                            logger.error("Error in plugin {} during onSuccess for job {}: {}", plugin.getClass().getName(), id, e.getMessage(), e);
                        }
                    }
                } else { // Neither error nor success explicitly (e.g. malformed server response for this job)
                     logger.warn("Job {} was not successful and had no error object from server. HTML: {}. Generating client fallback.", id, jobResultFromServer.getHtml() != null ? "present" : "missing");
                    if (jobResultFromServer.getHtml() == null) { // If no HTML, treat as error and generate fallback
                         jobResultFromServer.setHtml(generateFallbackHtml(originalJob.getName(), originalJob.getData()));
                         if(jobResultFromServer.getError() == null) { // Ensure an error object exists for onJobError
                            jobResultFromServer.setError(Map.of("message", "Implicit failure: Job not marked success and no HTML provided by server.", "isClientFallback", true));
                         }
                         for (HypernovaPlugin plugin : plugins) { // Call onJobError for this implicit failure
                            try {
                                plugin.onJobError(jobResultFromServer);
                            } catch (Exception e) {
                                logger.error("Error in plugin {} during onJobError (implicit failure) for job {}: {}", plugin.getClass().getName(), id, e.getMessage(), e);
                            }
                        }
                    }
                    // If HTML is present but not success and no error, it's an ambiguous state.
                    // For now, we pass it through. Plugins in afterResponse can handle it.
                }
                finalResponse.getResults().put(id, jobResultFromServer); // Add processed result to final response
            }
        }


        // Plugin Hook: afterResponse - applied to the consolidated finalResponse.results
        for (HypernovaPlugin plugin : plugins) {
            try {
                Map<String, HypernovaJobResult> resultsBeforeHook = new HashMap<>(finalResponse.getResults());
                Map<String, HypernovaJobResult> resultsAfterHook = plugin.afterResponse(resultsBeforeHook);
                finalResponse.setResults(resultsAfterHook != null ? resultsAfterHook : new HashMap<>()); // Protect against null from plugin
            } catch (Exception e) {
                logger.error("Error in plugin {} during afterResponse: {}. Results may not reflect plugin changes.", plugin.getClass().getName(), e.getMessage(), e);
            }
        }

        if (finalResponse.getResults() == null) { // Should be handled by plugin loop, but as a final safeguard
            logger.error("finalResponse.results is null at the very end. Setting to empty map.");
            finalResponse.setResults(new HashMap<>());
        }

        return finalResponse;
    }
}
