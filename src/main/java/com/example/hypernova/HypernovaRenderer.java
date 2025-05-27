package com.example.hypernova;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

// Define Helper Records for Deserialization (package-private or private if not used outside)

/**
 * Represents the response structure from the Hypernova server.
 * Used internally by {@link HypernovaRenderer} for deserialization.
 * @param error The top-level error from the server, if any.
 * @param results A map of job results from the server.
 */
record HypernovaServerResponse(HypernovaError error, Map<String, HypernovaRenderer.HypernovaRawJobResult> results) {}

/**
 * Represents the raw structure of a single job's result from the Hypernova server.
 * Used internally by {@link HypernovaRenderer} for deserialization.
 * @param error The error specific to this job, if any.
 * @param html The rendered HTML for this job.
 * @param success Whether this job was rendered successfully.
 * @param meta Metadata associated with this job's result.
 * @param duration The duration of rendering for this job.
 */
record HypernovaRawJobResult(HypernovaError error, String html, boolean success, Map<String, String> meta, double duration) {}

/**
 * The main class for interacting with a Hypernova server.
 * It allows adding rendering jobs, processing them through a plugin lifecycle,
 * sending them to the server in a batch, and handling the response.
 */
public class HypernovaRenderer {
    private final String url;
    private final List<Plugin> plugins;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Map<String, Job> incomingJobs = new HashMap<>();

    /**
     * Constructs a HypernovaRenderer with a specific Hypernova server URL, a list of plugins,
     * an OkHttpClient instance, and an ObjectMapper instance.
     *
     * @param url        The URL of the Hypernova server's batch endpoint. Must not be null.
     * @param plugins    A list of {@link Plugin} instances to apply during the rendering lifecycle.
     *                   If null, an empty list will be used. The provided list is copied.
     * @param client     An {@link OkHttpClient} instance for making HTTP requests. Must not be null.
     * @param mapper     An {@link ObjectMapper} instance for JSON serialization/deserialization. Must not be null.
     * @throws NullPointerException if url, client, or mapper is null.
     */
    public HypernovaRenderer(String url, List<Plugin> plugins, OkHttpClient client, ObjectMapper mapper) {
        if (url == null) {
            throw new NullPointerException("url cannot be null");
        }
        if (client == null) {
            throw new NullPointerException("client cannot be null");
        }
        if (mapper == null) {
            throw new NullPointerException("mapper cannot be null");
        }
        this.url = url;
        this.plugins = plugins == null ? new ArrayList<>() : new ArrayList<>(plugins); // Defensive copy
        this.httpClient = client;
        this.objectMapper = mapper;
    }

    /**
     * Constructs a HypernovaRenderer with a specific Hypernova server URL.
     * Uses default instances for plugins (empty list), OkHttpClient, and ObjectMapper.
     *
     * @param url The URL of the Hypernova server's batch endpoint. Must not be null.
     * @throws NullPointerException if url is null.
     */
    public HypernovaRenderer(String url) {
        this(url, new ArrayList<>(), new OkHttpClient(), new ObjectMapper());
    }

    /**
     * Adds a rendering job to the current batch.
     * Jobs are stored internally and sent to the server when {@link #render()} is called.
     * If a job with the same ID already exists, it will be overwritten.
     *
     * @param id  A unique identifier for this job (e.g., a UUID or a descriptive name).
     *            Used to retrieve the specific {@link JobResult} from the {@link HypernovaResponse}.
     *            If null, the job will not be added.
     * @param job The {@link Job} object containing component name, data, and metadata.
     *            If null, the job will not be added.
     */
    public void addJob(String id, Job job) {
        if (id == null || job == null) {
            // Consider logging this situation or throwing IllegalArgumentException based on desired strictness.
            System.err.println("Warning: Attempted to add a job with null id or null job object. Skipping.");
            return;
        }
        synchronized (this.incomingJobs) {
            this.incomingJobs.put(id, job);
        }
    }

    /**
     * Convenience method to add a rendering job by specifying its components directly.
     * Creates a {@link Job} instance internally.
     * If a job with the same ID already exists, it will be overwritten.
     *
     * @param id            A unique identifier for this job. If null, the job will not be added.
     * @param componentName The name of the component to render.
     * @param data          A map of data (props) for the component.
     * @param metadata      A map of metadata for the job. Can be null.
     */
    public void addJob(String id, String componentName, Map<String, Object> data, Map<String, String> metadata) {
        if (id == null) { // componentName and data can be part of a valid Job (e.g. empty data)
            System.err.println("Warning: Attempted to add a job with null id. Skipping.");
            return;
        }
        this.addJob(id, new Job(componentName, data, metadata));
    }

    /**
     * Renders the current batch of accumulated jobs.
     * This method orchestrates the entire rendering lifecycle:
     * <ol>
     *     <li>Invokes {@link Plugin#getViewData(String, Map)} for each job.</li>
     *     <li>Invokes {@link Plugin#prepareRequest(Map, Map)} for the batch.</li>
     *     <li>Invokes {@link Plugin#shouldSendRequest(Map)} to determine if the request should proceed.</li>
     *     <li>If proceeding, invokes {@link Plugin#willSendRequest(Map)}.</li>
     *     <li>Makes an HTTP POST request to the Hypernova server.</li>
     *     <li>Processes the server's response, creating {@link JobResult} instances.</li>
     *     <li>Invokes {@link Plugin#onSuccess(JobResult)} or {@link Plugin#onError(HypernovaError, List)} for each job result.</li>
     *     <li>Invokes {@link Plugin#afterResponse(Map)} for the final batch of results.</li>
     *     <li>Clears the accumulated jobs for the next batch.</li>
     * </ol>
     * If {@link Plugin#shouldSendRequest(Map)} returns false from any plugin, or if an HTTP error occurs,
     * or if a plugin throws an unhandled exception during critical phases, this method will generate
     * fallback HTML for the affected jobs.
     * <p>
     * This method is synchronized to ensure thread-safe manipulation of the internal jobs collection and request processing.
     *
     * @return A {@link HypernovaResponse} containing the results of all processed jobs and any top-level errors.
     *         Returns an empty response if no jobs were added.
     */
    public synchronized HypernovaResponse render() {
        if (this.incomingJobs.isEmpty()) {
            return new HypernovaResponse(); // Return empty response
        }

        // Create a snapshot of original jobs for plugins that need it (e.g. prepareRequest)
        Map<String, Job> originalJobsSnapshot = Collections.unmodifiableMap(new HashMap<>(this.incomingJobs));

        // 1. Create Jobs (Plugin Hook getViewData)
        Map<String, Job> currentJobs = new HashMap<>();
        for (Map.Entry<String, Job> entry : this.incomingJobs.entrySet()) {
            Job job = entry.getValue();
            Map<String, Object> currentData = new HashMap<>(job.getData()); // Work on a copy
            for (Plugin plugin : this.plugins) {
                try {
                    currentData = plugin.getViewData(job.getName(), currentData);
                } catch (Exception e) {
                    // Log or handle plugin error, e.g., using a logging framework
                    System.err.println("Plugin error in getViewData for job '" + entry.getKey() + "': " + e.getMessage());
                    // Potentially mark this job as failed or use original data
                }
            }
            currentJobs.put(entry.getKey(), new Job(job.getName(), currentData, job.getMetadata()));
        }

        // 2. Prepare Request (Plugin Hook prepareRequest)
        Map<String, Job> jobsToProcess = currentJobs; // Start with jobs after getViewData
        for (Plugin plugin : this.plugins) {
            try {
                // Pass a defensive copy of jobsToProcess and the originalJobsSnapshot
                jobsToProcess = plugin.prepareRequest(new HashMap<>(jobsToProcess), originalJobsSnapshot);
            } catch (Exception e) {
                System.err.println("Plugin error in prepareRequest: " + e.getMessage());
                // Potentially fallback or skip request if prepareRequest critically fails
            }
        }

        // 3. Should Send Request (Plugin Hook shouldSendRequest)
        // Make jobsToProcess effectively final for lambda/anonymous class if needed by plugins, though not typical for this hook.
        final Map<String, Job> effectivelyFinalJobsToProcess = Collections.unmodifiableMap(jobsToProcess);
        for (Plugin plugin : this.plugins) {
            try {
                if (!plugin.shouldSendRequest(effectivelyFinalJobsToProcess)) {
                    System.err.println("Request aborted by plugin: " + plugin.getClass().getName());
                    return fallback(null, effectivelyFinalJobsToProcess); // Fallback with no top-level error
                }
            } catch (Exception e) {
                System.err.println("Plugin error in shouldSendRequest: " + e.getMessage());
                return fallback(createHypernovaErrorFromException(e), effectivelyFinalJobsToProcess); // Fallback with plugin error
            }
        }

        // 4. Make Request
        try {
            return makeRequest(effectivelyFinalJobsToProcess);
        } catch (Exception e) { // Catches IOException from makeRequest or other runtime exceptions
            System.err.println("Error making request to Hypernova: " + e.getMessage());
            return fallback(createHypernovaErrorFromException(e), effectivelyFinalJobsToProcess);
        } finally {
            this.incomingJobs.clear(); // Ensure jobs are cleared for the next batch
        }
    }

    /**
     * Executes the HTTP request to the Hypernova server and processes the response.
     * This method is intended for internal use by {@link #render()}.
     *
     * @param jobs The map of {@link Job} instances to send to the server.
     * @return A {@link HypernovaResponse} containing the results.
     * @throws IOException If an I/O error occurs during the HTTP request or response processing.
     */
    private HypernovaResponse makeRequest(Map<String, Job> jobs) throws IOException {
        // Invoke willSendRequest plugin hook
        for (Plugin plugin : this.plugins) {
            try {
                plugin.willSendRequest(Collections.unmodifiableMap(jobs)); // Pass unmodifiable view
            } catch (Exception e) {
                System.err.println("Plugin error in willSendRequest: " + e.getMessage());
                // Log and continue, as this is typically a non-critical notification hook
            }
        }

        // Perform the HTTP request and get results
        Map<String, JobResult> jobResults = doRequest(jobs);

        // Finalize response (includes onSuccess, onError for individual jobs, and afterResponse hooks)
        return finalizeResponse(jobResults, jobs); // Pass original jobs map for context
    }

    /**
     * Performs the actual HTTP POST request to the Hypernova server.
     * This method is intended for internal use.
     *
     * @param jobs The map of jobs to send.
     * @return A map of job identifiers to their {@link JobResult}.
     * @throws IOException If an I/O error occurs or the server returns an unsuccessful response.
     */
    private Map<String, JobResult> doRequest(Map<String, Job> jobs) throws IOException {
        String jsonPayload = objectMapper.writeValueAsString(jobs);
        RequestBody body = RequestBody.create(jsonPayload, MediaType.get("application/json; charset=utf-8"));
        Request request = new Request.Builder().url(this.url).post(body).build();

        try (Response okHttpResponse = httpClient.newCall(request).execute()) {
            String responseBodyString = okHttpResponse.body() != null ? okHttpResponse.body().string() : null;

            if (!okHttpResponse.isSuccessful()) {
                throw new IOException("Unexpected HTTP code " + okHttpResponse.code() + " " + okHttpResponse.message() +
                                      (responseBodyString != null ? " - " + responseBodyString : ""));
            }

            if (responseBodyString == null) {
                throw new IOException("Empty response body from Hypernova server.");
            }

            HypernovaServerResponse serverResponse = objectMapper.readValue(responseBodyString, HypernovaServerResponse.class);
            Map<String, JobResult> finalResults = new HashMap<>();

            if (serverResponse.error() != null) { // Top-level error from server for the whole batch
                List<Job> jobList = new ArrayList<>(jobs.values()); // All jobs in the batch are affected
                for (Plugin plugin : this.plugins) {
                    try {
                        plugin.onError(serverResponse.error(), Collections.unmodifiableList(jobList));
                    } catch (Exception e) {
                        System.err.println("Plugin error in onError (top-level server error): " + e.getMessage());
                    }
                }
                // Create fallback JobResult for each job, associating the top-level server error
                for (Map.Entry<String, Job> entry : jobs.entrySet()) {
                    Job originalJob = entry.getValue();
                    String uuid = UUID.randomUUID().toString(); // Unique ID for fallback HTML elements
                    String fallbackHtml = getFallbackHTML(originalJob.getName(), originalJob.getData(), uuid);
                    finalResults.put(entry.getKey(), new JobResult(serverResponse.error(), fallbackHtml, false, originalJob, Map.of("uuid", uuid), 0));
                }
                return finalResults; // These results will still go through afterResponse via finalizeResponse
            }

            if (serverResponse.results() == null) {
                 throw new IOException("Malformed response from Hypernova server: 'results' field is null. Response: " + responseBodyString);
            }

            // Process individual job results
            for (Map.Entry<String, HypernovaRawJobResult> entry : serverResponse.results().entrySet()) {
                String id = entry.getKey();
                HypernovaRawJobResult rawResult = entry.getValue();
                Job originalJob = jobs.get(id); // Get the original job for context
                if (originalJob != null) {
                    // Create JobResult using the factory method, no override error here as it's per-job
                    finalResults.put(id, JobResult.fromRawResult(rawResult, originalJob, null));
                } else {
                    System.err.println("Warning: Received result for unknown job ID: " + id + ". This result will be skipped.");
                }
            }
            return finalResults;
        }
    }

    /**
     * Finalizes the {@link HypernovaResponse} by running post-processing plugin hooks.
     * This includes {@link Plugin#onSuccess(JobResult)}, {@link Plugin#onError(HypernovaError, List)} for individual jobs,
     * and {@link Plugin#afterResponse(Map)}.
     * This method is intended for internal use.
     *
     * @param jobResults      The map of job results obtained from `doRequest` or generated during fallback.
     * @param originalJobsMap The original map of jobs sent in the request, used for context in plugins.
     * @return The finalized {@link HypernovaResponse}.
     */
    private HypernovaResponse finalizeResponse(Map<String, JobResult> jobResults, Map<String, Job> originalJobsMap) {
        HypernovaResponse hypernovaResponse = new HypernovaResponse(); // Default constructor
        Map<String, JobResult> processedResults = new HashMap<>(jobResults); // Work on a copy

        // Trigger onSuccess or onError for individual job results
        for (JobResult jobResult : processedResults.values()) {
            try {
                if (jobResult.isSuccess()) {
                    for (Plugin plugin : this.plugins) plugin.onSuccess(jobResult);
                } else if (jobResult.getError() != null) {
                    // For individual job errors, pass only that job to onError
                    for (Plugin plugin : this.plugins) plugin.onError(jobResult.getError(), Collections.singletonList(jobResult.getOriginalJob()));
                }
            } catch (Exception e) {
                System.err.println("Plugin error in onSuccess/onError for job '" + jobResult.getOriginalJob().getName() + "': " + e.getMessage());
            }
        }

        // Trigger afterResponse plugin hook
        processedResults = runAfterResponsePlugins(processedResults);
        hypernovaResponse.setResults(processedResults);

        // Note: The top-level error on HypernovaResponse (hypernovaResponse.error) is set by the `fallback` method
        // if the entire request fails before or during the HTTP call, or if `shouldSendRequest` is false.
        // If the server returns a top-level error for the batch (handled in `doRequest`),
        // that error is propagated to individual `JobResult` objects, but not automatically to `hypernovaResponse.error` here.
        return hypernovaResponse;
    }

    /**
     * Generates a fallback {@link HypernovaResponse} when the main rendering path cannot be completed.
     * This can happen if a plugin aborts the request, an HTTP error occurs, or other critical exceptions arise.
     * Fallback HTML is generated for each job.
     * This method is intended for internal use.
     *
     * @param topLevelError An optional {@link HypernovaError} representing a failure that affected the entire batch.
     *                      Can be null if fallback is due to a controlled abort (e.g., `shouldSendRequest` returning false).
     * @param jobs          The map of jobs for which fallback content needs to be generated.
     * @return A {@link HypernovaResponse} populated with fallback results.
     */
    private HypernovaResponse fallback(HypernovaError topLevelError, Map<String, Job> jobs) {
        HypernovaResponse response = new HypernovaResponse();
        if (topLevelError != null) {
            response.setError(topLevelError); // Set top-level error on the response object
        }

        Map<String, JobResult> fallbackResults = new HashMap<>();
        for (Map.Entry<String, Job> entry : jobs.entrySet()) {
            Job job = entry.getValue();
            String uuid = UUID.randomUUID().toString(); // Unique ID for fallback HTML elements
            String fallbackHtml = getFallbackHTML(job.getName(), job.getData(), uuid);
            // Each job result in fallback also gets the topLevelError (if any)
            JobResult fallbackResult = new JobResult(topLevelError, fallbackHtml, false, job, Map.of("uuid", uuid), 0);
            fallbackResults.put(entry.getKey(), fallbackResult);
        }

        // Run afterResponse plugins even for fallback results
        Map<String, JobResult> finalFallbackResults = runAfterResponsePlugins(fallbackResults);
        response.setResults(finalFallbackResults);

        // If there was a top-level error that triggered this fallback, invoke onError for all affected jobs.
        if (topLevelError != null) {
            List<Job> jobList = new ArrayList<>(jobs.values());
            for (Plugin plugin : this.plugins) {
                try {
                    plugin.onError(topLevelError, Collections.unmodifiableList(jobList));
                } catch (Exception e) {
                    System.err.println("Plugin error in onError (during fallback for topLevelError): " + e.getMessage());
                }
            }
        }
        // Note: incomingJobs are cleared in the `render` method's `finally` block.
        return response;
    }

    /**
     * Generates fallback HTML for a given component.
     * The HTML includes placeholders for the component and its data, allowing client-side hydration if configured.
     * This method is intended for internal use.
     *
     * @param moduleName The name of the component/module.
     * @param data       The data associated with the component.
     * @param uuid       A unique identifier for the DOM elements.
     * @return A string of HTML representing the fallback structure.
     */
    private String getFallbackHTML(String moduleName, Map<String, Object> data, String uuid) {
        try {
            String jsonData = objectMapper.writeValueAsString(data);
            String escapedJsonData = jsonData.replace("<", "\\u003C"); // Basic escaping for < to prevent XSS in HTML comments
            return String.format("<div data-hypernova-key=\"%s\" data-hypernova-id=\"%s\"></div>" +
                               "<script type=\"application/json\" data-hypernova-key=\"%s\" data-hypernova-id=\"%s\"><!--%s--></script>",
                               moduleName, uuid, moduleName, uuid, escapedJsonData);
        } catch (IOException e) {
            System.err.println("Error generating fallback HTML for component '" + moduleName + "': " + e.getMessage());
            // Return a simple error placeholder if JSON serialization fails
            return String.format("<div data-hypernova-key=\"%s\" data-hypernova-id=\"%s\" data-hypernova-error=\"true\"></div>", moduleName, uuid);
        }
    }

    /**
     * Converts a generic {@link Exception} into a {@link HypernovaError}.
     * This is used to wrap exceptions caught during plugin execution or HTTP requests.
     * This method is intended for internal use.
     *
     * @param e The exception to convert.
     * @return A {@link HypernovaError} instance.
     */
    private HypernovaError createHypernovaErrorFromException(Exception e) {
        List<String> stack = Arrays.stream(e.getStackTrace())
                                   .map(StackTraceElement::toString)
                                   .collect(Collectors.toList());
        String message = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        return new HypernovaError(message, stack);
    }

    /**
     * Runs the {@link Plugin#afterResponse(Map)} hook for all registered plugins.
     * This method is intended for internal use.
     *
     * @param results The current map of job results.
     * @return The potentially modified map of job results after all plugins have run.
     */
    private Map<String, JobResult> runAfterResponsePlugins(Map<String, JobResult> results) {
        Map<String, JobResult> currentResults = new HashMap<>(results); // Work on a copy
        for (Plugin plugin : this.plugins) {
            try {
                currentResults = plugin.afterResponse(currentResults);
            } catch (Exception e) {
                System.err.println("Plugin error in afterResponse: " + e.getMessage());
                // Log and continue with potentially unmodified results from this plugin onwards
            }
        }
        return currentResults;
    }
}
