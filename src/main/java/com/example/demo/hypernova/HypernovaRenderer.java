package com.example.demo.hypernova;

import com.example.demo.hypernova.plugin.HypernovaPlugin;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service responsible for rendering Hypernova jobs.
 * It manages a batch of jobs, interacts with plugins throughout the rendering lifecycle,
 * sends requests to a Hypernova service, and processes the responses.
 */
@Slf4j
@Service
public class HypernovaRenderer {

    private final String hypernovaServiceUrl;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final List<HypernovaPlugin> plugins = new ArrayList<>();
    private final Map<String, HypernovaJob> incomingJobs = new LinkedHashMap<>();

    public HypernovaRenderer(
            @Value("${hypernova.service.url:http://localhost:3030/batch}") String hypernovaServiceUrl,
            RestTemplate restTemplate,
            ObjectMapper objectMapper) {
        this.hypernovaServiceUrl = hypernovaServiceUrl;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    public void addPlugin(HypernovaPlugin plugin) {
        if (plugin != null) {
            this.plugins.add(plugin);
            log.debug("Added plugin: {}", plugin.getClass().getName());
        }
    }

    public void addJob(String id, HypernovaJob job) {
        if (id == null || id.isEmpty() || job == null) {
            log.warn("Attempted to add a job with null/empty ID or null job. Skipping.");
            return;
        }
        this.incomingJobs.put(id, job);
        log.debug("Added job with id '{}': {}", id, job.getName());
    }

    public HypernovaResponse render() {
        if (this.incomingJobs.isEmpty()) {
            log.info("No jobs to render.");
            return new HypernovaResponse(new HashMap<>(), null);
        }

        Map<String, HypernovaJob> originalJobMap = new HashMap<>(this.incomingJobs);
        List<HypernovaJob> initialProcessedJobs = new ArrayList<>();

        for (Map.Entry<String, HypernovaJob> entry : originalJobMap.entrySet()) { // Iterate over a copy
            String clientJobId = entry.getKey();
            HypernovaJob originalJobForThisClient = entry.getValue();

            HypernovaJob processedJob = new HypernovaJob(
                originalJobForThisClient.getName(),
                originalJobForThisClient.getData() != null ? new HashMap<>(originalJobForThisClient.getData()) : new HashMap<>(),
                originalJobForThisClient.getMetadata() != null ? new HashMap<>(originalJobForThisClient.getMetadata()) : new HashMap<>()
            );
            if (processedJob.getMetadata() == null) processedJob.setMetadata(new HashMap<>());
            processedJob.getMetadata().put("_originalClientId", clientJobId);
            initialProcessedJobs.add(processedJob);
        }

        // Create the map HERE, using initialProcessedJobs
        Map<String, String> jobNameToOriginalClientIdMap = initialProcessedJobs.stream()
            .filter(job -> job.getName() != null && job.getMetadata() != null && job.getMetadata().containsKey("_originalClientId"))
            .collect(Collectors.toMap(
                HypernovaJob::getName,
                job -> (String) job.getMetadata().get("_originalClientId"),
                (id1, id2) -> {
                    log.warn("Duplicate job name '{}' found while creating _originalClientId map. Using the first encountered ID: {}.", id1, id2); 
                    return id1;
                }
            ));

        List<HypernovaJob> currentJobs = new ArrayList<>(); // This will be populated by the getViewData loop

        // Now, iterate over initialProcessedJobs for the getViewData loop
        for (HypernovaJob jobToProcessByViewData : initialProcessedJobs) {
            String clientJobIdForLoop = (String) jobToProcessByViewData.getMetadata().get("_originalClientId");
            HypernovaJob originalJobForThisClientInLoop = originalJobMap.get(clientJobIdForLoop);

            HypernovaJob processedByViewData = jobToProcessByViewData; 

            for (HypernovaPlugin plugin : plugins) {
                HypernovaJob jobStateBeforePlugin = processedByViewData;
                try {
                    HypernovaJob pluginInputJob = new HypernovaJob( 
                        processedByViewData.getName(),
                        processedByViewData.getData() != null ? new HashMap<>(processedByViewData.getData()) : new HashMap<>(),
                        processedByViewData.getMetadata() != null ? new HashMap<>(processedByViewData.getMetadata()) : new HashMap<>()
                    );
                    
                    processedByViewData = plugin.getViewData(pluginInputJob.getName(), pluginInputJob.getData(), originalJobForThisClientInLoop);
                    if (processedByViewData == null) {
                        log.warn("Plugin {} returned null from getViewData for job (client ID: {}, component: {}). Dropping job.",
                                 plugin.getClass().getSimpleName(), clientJobIdForLoop, (originalJobForThisClientInLoop != null ? originalJobForThisClientInLoop.getName() : "N/A"));
                        break;
                    }
                    // Ensure _originalClientId from jobToProcessByViewData is preserved
                    if (processedByViewData.getMetadata() == null) {
                        processedByViewData.setMetadata(new HashMap<>());
                    }
                    if (!processedByViewData.getMetadata().containsKey("_originalClientId") && 
                        jobToProcessByViewData.getMetadata() != null && 
                        jobToProcessByViewData.getMetadata().containsKey("_originalClientId")) {
                        processedByViewData.getMetadata().put("_originalClientId", jobToProcessByViewData.getMetadata().get("_originalClientId"));
                    }

                } catch (Exception e) {
                    log.error("Plugin {} threw an exception during getViewData for job (client ID: {}, component: {}): {}. Continuing with job state before this plugin.",
                              plugin.getClass().getSimpleName(), clientJobIdForLoop, (originalJobForThisClientInLoop != null ? originalJobForThisClientInLoop.getName() : "N/A"), e.getMessage(), e);
                    processedByViewData = jobStateBeforePlugin;
                }
            }
            if (processedByViewData != null) {
                 currentJobs.add(processedByViewData); 
            }
        }

        this.incomingJobs.clear();

        if (currentJobs.isEmpty()) {
            log.info("All jobs were dropped or no jobs were processed after getViewData. Returning empty response.");
            return new HypernovaResponse(new HashMap<>(), null);
        }
        
        // jobNameToOriginalClientIdMap is already created above

        List<HypernovaJob> jobsToPrepareHolder = new ArrayList<>(currentJobs);
        List<HypernovaJob> originalJobsForPrepare = Collections.unmodifiableList(new ArrayList<>(originalJobMap.values()));
        for (HypernovaPlugin plugin : plugins) {
            try {
                List<HypernovaJob> resultOfPrepare = plugin.prepareRequest(new ArrayList<>(jobsToPrepareHolder), originalJobsForPrepare);
                if (resultOfPrepare != null) { 
                    jobsToPrepareHolder = resultOfPrepare;
                } else {
                     log.warn("Plugin {} returned null from prepareRequest. Using job list from before this plugin.", plugin.getClass().getSimpleName());
                }
            } catch (Exception e) {
                log.error("Plugin {} threw an exception during prepareRequest: {}. Continuing with job list from before this plugin.", 
                          plugin.getClass().getSimpleName(), e.getMessage(), e);
            }
        }
        currentJobs = jobsToPrepareHolder;

        // After prepareRequest plugin loop, restore _originalClientId if missing
        for (HypernovaJob job : currentJobs) {
            if (job.getName() != null && (job.getMetadata() == null || !job.getMetadata().containsKey("_originalClientId"))) {
                String originalId = jobNameToOriginalClientIdMap.get(job.getName());
                if (originalId != null) {
                    if (job.getMetadata() == null) {
                        job.setMetadata(new HashMap<>());
                    }
                    job.getMetadata().put("_originalClientId", originalId);
                    log.trace("Restored _originalClientId for job '{}' to '{}' after prepareRequest calls.", job.getName(), originalId);
                } else {
                    log.warn("Could not restore _originalClientId for job '{}' after prepareRequest calls, as it was not found in the initial mapping (original name might have changed or job is new).", job.getName());
                }
            }
        }

        boolean shouldSend = true;
        for (HypernovaPlugin plugin : plugins) {
            try {
                if (!plugin.shouldSendRequest(Collections.unmodifiableList(new ArrayList<>(currentJobs)))) {
                    shouldSend = false;
                    log.info("Plugin {} indicated request should not be sent for jobs: {}", plugin.getClass().getSimpleName(), currentJobs.stream().map(HypernovaJob::getName).collect(Collectors.toList()));
                    break;
                }
            } catch (Exception e) {
                log.error("Plugin {} threw an exception during shouldSendRequest: {}. Assuming request should be sent.", 
                          plugin.getClass().getSimpleName(), e.getMessage(), e);
            }
        }

        if (!shouldSend) {
            return handleFallback(null, currentJobs, originalJobMap);
        }

        for (HypernovaPlugin plugin : plugins) {
            try {
                plugin.willSendRequest(Collections.unmodifiableList(new ArrayList<>(currentJobs)));
            } catch (Exception e) {
                log.error("Plugin {} threw an exception during willSendRequest: {}. Continuing with request.", 
                          plugin.getClass().getSimpleName(), e.getMessage(), e);
            }
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, HypernovaJob> jobsToRenderForHypernova = new HashMap<>();
        for (HypernovaJob job : currentJobs) {
            jobsToRenderForHypernova.put(job.getName(), job);
        }

        HttpEntity<String> entity;
        try {
            String requestBody = objectMapper.writeValueAsString(jobsToRenderForHypernova);
            log.debug("Hypernova request body: {}", requestBody);
            entity = new HttpEntity<>(requestBody, headers);
        } catch (JsonProcessingException e) {
            log.error("Error serializing jobs for Hypernova request body ({} jobs): {}. Details: {}", jobsToRenderForHypernova.size(), e.getMessage(), jobsToRenderForHypernova.keySet(), e);
            handlePluginOnError(e, null, currentJobs, originalJobMap);
            return handleFallback(e, currentJobs, originalJobMap);
        }

        ResponseEntity<String> responseEntity = null; 
        HypernovaResponse hypernovaResponse;

        try {
            log.info("Sending {} jobs to Hypernova at URL: {}", jobsToRenderForHypernova.size(), hypernovaServiceUrl);
            responseEntity = restTemplate.exchange(hypernovaServiceUrl, HttpMethod.POST, entity, String.class);
            String responseBody = responseEntity.getBody();
            log.debug("Received response body from Hypernova (first 500 chars): {}", responseBody != null ? responseBody.substring(0, Math.min(responseBody.length(), 500)) : "null");
            hypernovaResponse = objectMapper.readValue(responseBody, HypernovaResponse.class);

            if (hypernovaResponse.getError() != null) {
                log.warn("Hypernova service returned a top-level error: {}", hypernovaResponse.getError());
                handlePluginOnError(hypernovaResponse.getError(), 
                                    hypernovaResponse.getResults() != null ? new ArrayList<>(hypernovaResponse.getResults().values()) : null, 
                                    currentJobs, originalJobMap);
            }
            
            if (hypernovaResponse.getResults() != null) {
                for (Map.Entry<String, HypernovaJobResult> resEntry : hypernovaResponse.getResults().entrySet()) {
                    String hypernovaKey = resEntry.getKey(); 
                    HypernovaJobResult result = resEntry.getValue();
                    
                    HypernovaJob matchedProcessedJob = currentJobs.stream()
                        .filter(j -> j.getName().equals(hypernovaKey))
                        .findFirst().orElse(null);

                    if (matchedProcessedJob != null && matchedProcessedJob.getMetadata() != null) {
                        String originalClientId = (String) matchedProcessedJob.getMetadata().get("_originalClientId");
                        if (originalClientId != null && originalJobMap.containsKey(originalClientId)) {
                            Map<String, Object> meta = result.getMeta();
                            if (meta == null) {
                                meta = new HashMap<>();
                            } else {
                                // Ensure it's a mutable copy if it's not null
                                meta = new HashMap<>(meta); 
                            }
                            meta.put("_originalClientId", originalClientId);
                            result.setMeta(meta); // Set the potentially new map back
                            result.setOriginalJob(originalJobMap.get(originalClientId)); 
                        } else {
                             log.warn("Could not find _originalClientId or original job in originalJobMap for processed job named: {} (client ID from meta: {})", hypernovaKey, originalClientId);
                             result.setOriginalJob(new HypernovaJob(hypernovaKey, null, null)); 
                        }
                    } else {
                        log.warn("Could not find matching processed job (or its metadata) for Hypernova result key: {}. Client ID mapping might be lost.", hypernovaKey);
                        result.setOriginalJob(new HypernovaJob(hypernovaKey, null, null)); 
                    }
                }
            }

        } catch (RestClientException e) {
            log.error("HTTP error calling Hypernova service at {}: {}. Jobs: {}", hypernovaServiceUrl, e.getMessage(), jobsToRenderForHypernova.keySet(), e);
            handlePluginOnError(e, null, currentJobs, originalJobMap);
            return handleFallback(e, currentJobs, originalJobMap);
        } catch (JsonProcessingException e) {
            String responseBodySnippet = responseEntity != null && responseEntity.getBody() != null ? responseEntity.getBody().substring(0, Math.min(responseEntity.getBody().length(), 500)) : "[response body not available]";
            log.error("Error deserializing Hypernova response (snippet: {}): {}. Jobs: {}", responseBodySnippet, e.getMessage(), jobsToRenderForHypernova.keySet(), e);
            handlePluginOnError(e, null, currentJobs, originalJobMap);
            return handleFallback(e, currentJobs, originalJobMap);
        }

        Map<String, HypernovaJobResult> resultsFromHypernova = hypernovaResponse.getResults() != null ? new HashMap<>(hypernovaResponse.getResults()) : new HashMap<>();
        Map<String, HypernovaJobResult> clientKeyedResults = new HashMap<>();

        for (Map.Entry<String, HypernovaJobResult> resultEntry : resultsFromHypernova.entrySet()) { 
            HypernovaJobResult result = resultEntry.getValue();
            HypernovaJob originalClientJob = result.getOriginalJob(); 
            String clientJobId = result.getMeta() != null ? (String) result.getMeta().get("_originalClientId") : null;
            if (clientJobId == null && originalClientJob != null && originalClientJob.getMetadata() != null) {
                clientJobId = (String) originalClientJob.getMetadata().get("_originalClientId");
            }
            if (clientJobId == null) {
                // This case should be rare if the above logic correctly populates _originalClientId
                clientJobId = "unknown_client_id_" + UUID.randomUUID().toString();
                log.warn("Could not determine clientJobId for result of component '{}' (Hypernova key). Using generated key: {}", resultEntry.getKey(), clientJobId);
            }

            if (result.getError() != null) {
                handlePluginOnError(result.getError(), Collections.singletonList(result), 
                                    originalClientJob != null ? Collections.singletonList(originalClientJob) : Collections.emptyList(), 
                                    originalJobMap);
            } else {
                for (HypernovaPlugin plugin : plugins) {
                    try {
                        plugin.onSuccess(result); 
                    } catch (Exception e) {
                        log.error("Plugin {} threw an exception during onSuccess for job (client ID: {}): {}. Result will not be further processed by this plugin.", 
                                  plugin.getClass().getSimpleName(), clientJobId, e.getMessage(), e);
                    }
                }
            }
            clientKeyedResults.put(clientJobId, result); 
        }
        
        Map<String, HypernovaJobResult> resultsToPassToAfterResponse = new HashMap<>(clientKeyedResults);
        for (HypernovaPlugin plugin : plugins) {
            try {
                Map<String, HypernovaJobResult> pluginModifiedResults = plugin.afterResponse(new HashMap<>(resultsToPassToAfterResponse)); 
                if (pluginModifiedResults != null) { 
                    resultsToPassToAfterResponse = pluginModifiedResults;
                } else {
                    log.warn("Plugin {} returned null from afterResponse. Using results from before this plugin.", plugin.getClass().getSimpleName());
                }
            } catch (Exception e) {
                log.error("Plugin {} threw an exception during afterResponse: {}. Continuing with results from before this plugin.", 
                          plugin.getClass().getSimpleName(), e.getMessage(), e);
            }
        }
        hypernovaResponse.setResults(resultsToPassToAfterResponse);
        return hypernovaResponse;
    }

    private HypernovaResponse handleFallback(Exception topLevelError, List<HypernovaJob> jobsForFallback, Map<String, HypernovaJob> originalJobMap) {
        log.warn("Handling fallback for {} jobs. Top-level error: {}", jobsForFallback.size(), topLevelError != null ? topLevelError.getMessage() : "N/A (e.g. shouldSendRequest was false)");
        HypernovaResponse response = new HypernovaResponse();
        if (topLevelError != null) {
            Map<String, Object> errorDetails = new HashMap<>();
            errorDetails.put("message", topLevelError.getMessage());
            errorDetails.put("type", topLevelError.getClass().getSimpleName());
            response.setError(errorDetails);
        }
        Map<String, HypernovaJobResult> fallbackResults = new HashMap<>();

        for (HypernovaJob processedJob : jobsForFallback) {
            String originalClientId = processedJob.getMetadata() != null ? (String) processedJob.getMetadata().get("_originalClientId") : null;
            HypernovaJob originalJobContext = null; 

            if (originalClientId != null) {
                originalJobContext = originalJobMap.get(originalClientId);
                 if (originalJobContext == null) {
                    log.warn("Original job not found in originalJobMap for client ID '{}' during fallback. Using processed job as context.", originalClientId);
                    originalJobContext = processedJob; // Fallback to processed job if original mapping is somehow lost
                }
            } else {
                originalClientId = "fallback_id_" + UUID.randomUUID().toString();
                log.warn("Could not determine original client ID for fallback job named '{}'. Using generated ID: {}", processedJob.getName(), originalClientId);
                originalJobContext = processedJob; 
            }

            HypernovaJobResult jobResult = new HypernovaJobResult();
            String uuid = UUID.randomUUID().toString();
            jobResult.setHtml(generateFallbackHtml(processedJob.getName(), processedJob.getData(), uuid));
            Map<String, Object> meta = new HashMap<>();
            meta.put("fallback_uuid", uuid);
            meta.put("_originalClientId", originalClientId); 
            jobResult.setMeta(meta);
            jobResult.setSuccess(false);
            jobResult.setOriginalJob(originalJobContext); 
            jobResult.setError(topLevelError != null ? topLevelError.getMessage() : "Fallback due to unspecified error (e.g. shouldSendRequest returned false).");
            fallbackResults.put(originalClientId, jobResult);
        }
        response.setResults(fallbackResults);
        return response;
    }

    private String generateFallbackHtml(String componentName, Map<String, Object> data, String uuid) {
        String encodedData;
        try {
            encodedData = objectMapper.writeValueAsString(data != null ? data : Collections.emptyMap());
        } catch (JsonProcessingException e) {
            log.warn("Could not serialize data for fallback HTML of component {}: {}. Using empty JSON.", componentName, e.getMessage());
            encodedData = "{}";
        }
        String escapedComponentName = componentName != null ? componentName.replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;") : "UnknownComponent";
        String escapedData = encodedData.replace("-->", "--&gt;");

        return "<div data-hypernova-id=\"" + uuid + "\" data-hypernova-name=\"" + escapedComponentName + "\"></div>\n" +
               "<script type=\"application/json\" data-hypernova-id=\"" + uuid + "\" data-hypernova-name=\"" + escapedComponentName + "\">" +
               "<!--" + escapedData + "-->" +
               "</script>";
    }

    private void handlePluginOnError(Object error, List<HypernovaJobResult> jobResults, List<HypernovaJob> processedJobsContext, Map<String, HypernovaJob> originalJobMap) {
        List<HypernovaJob> originalJobsForPluginError = new ArrayList<>();
        Set<String> clientIdsProcessed = new HashSet<>();

        if (processedJobsContext != null) {
            for (HypernovaJob processedJob : processedJobsContext) {
                if (processedJob.getMetadata() != null && processedJob.getMetadata().containsKey("_originalClientId")) {
                    String clientId = (String) processedJob.getMetadata().get("_originalClientId");
                    if (clientId != null && originalJobMap.containsKey(clientId)) {
                        originalJobsForPluginError.add(originalJobMap.get(clientId));
                        clientIdsProcessed.add(clientId);
                    }
                }
            }
        }
        
        if (jobResults != null) {
            for (HypernovaJobResult jr : jobResults) {
                String clientId = null;
                if (jr.getMeta() != null && jr.getMeta().containsKey("_originalClientId")) {
                    clientId = (String) jr.getMeta().get("_originalClientId");
                } else if (jr.getOriginalJob() != null && jr.getOriginalJob().getMetadata() != null && jr.getOriginalJob().getMetadata().containsKey("_originalClientId")) {
                    clientId = (String) jr.getOriginalJob().getMetadata().get("_originalClientId");
                }
                if (clientId != null && !clientIdsProcessed.contains(clientId) && originalJobMap.containsKey(clientId)) {
                    originalJobsForPluginError.add(originalJobMap.get(clientId));
                    clientIdsProcessed.add(clientId); 
                }
            }
        }

        List<HypernovaJob> finalOriginalJobsForPlugin = Collections.unmodifiableList(originalJobsForPluginError.isEmpty() && processedJobsContext != null ? 
                                                                              new ArrayList<>(processedJobsContext) : 
                                                                              originalJobsForPluginError);
        List<HypernovaJobResult> finalJobResultsForPlugin = jobResults != null ? Collections.unmodifiableList(new ArrayList<>(jobResults)) : null;

        for (HypernovaPlugin plugin : plugins) {
            try {
                plugin.onError(error, finalJobResultsForPlugin, finalOriginalJobsForPlugin);
            } catch (Exception e) {
                log.error("Plugin {} threw an exception during its own onError handler: {}", 
                          plugin.getClass().getSimpleName(), e.getMessage(), e);
            }
        }
    }
}
