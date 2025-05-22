package com.example.demo.hypernova.plugin;

import com.example.demo.hypernova.HypernovaJob;
import com.example.demo.hypernova.HypernovaJobResult;

import java.util.List;
import java.util.Map;
import java.util.HashMap; // Added import for HashMap

/**
 * Abstract base class for {@link HypernovaPlugin} implementations.
 * This class provides default (no-op or pass-through) implementations for all
 * plugin lifecycle methods, allowing concrete plugin implementations to only
 * override the methods they are interested in.
 */
public abstract class BaseHypernovaPlugin implements HypernovaPlugin {

    /**
     * {@inheritDoc}
     * <p>
     * This default implementation returns a new {@link HypernovaJob} instance based on the provided
     * {@code originalJob} if it's not null (copying its name, data, and metadata).
     * If {@code originalJob} is null, it returns a new job with the given {@code viewName} and {@code data}.
     * Plugins should override this to implement custom logic for modifying job data or attributes.
     */
    @Override
    public HypernovaJob getViewData(String viewName, Map<String, Object> data, HypernovaJob originalJob) {
        if (originalJob != null) {
            // Create copies of data and metadata if they are not null
            Map<String, Object> jobData = originalJob.getData() != null ? new HashMap<>(originalJob.getData()) : null;
            Map<String, Object> jobMetadata = originalJob.getMetadata() != null ? new HashMap<>(originalJob.getMetadata()) : null;
            return new HypernovaJob(originalJob.getName(), jobData, jobMetadata);
        }
        // Ensure data map is copied if provided, or a new one if null
        Map<String, Object> newJobData = data != null ? new HashMap<>(data) : new HashMap<>();
        return new HypernovaJob(viewName, newJobData, null);
    }

    /**
     * {@inheritDoc}
     * <p>
     * This default implementation returns the provided list of jobs unmodified.
     */
    @Override
    public List<HypernovaJob> prepareRequest(List<HypernovaJob> jobs, List<HypernovaJob> originalJobs) {
        return jobs;
    }

    /**
     * {@inheritDoc}
     * <p>
     * This default implementation always returns {@code true}, indicating the request should be sent.
     */
    @Override
    public boolean shouldSendRequest(List<HypernovaJob> jobs) {
        return true;
    }

    /**
     * {@inheritDoc}
     * <p>
     * This default implementation is a no-op.
     */
    @Override
    public void willSendRequest(List<HypernovaJob> jobs) {
        // No-op
    }

    /**
     * {@inheritDoc}
     * <p>
     * This default implementation is a no-op.
     */
    @Override
    public void onSuccess(HypernovaJobResult jobResult) {
        // No-op
    }

    /**
     * {@inheritDoc}
     * <p>
     * This default implementation is a no-op.
     */
    @Override
    public void onError(Object error, List<HypernovaJobResult> jobResults, List<HypernovaJob> originalJobs) {
        // No-op
    }

    /**
     * {@inheritDoc}
     * <p>
     * This default implementation returns the provided map of job results unmodified.
     */
    @Override
    public Map<String, HypernovaJobResult> afterResponse(Map<String, HypernovaJobResult> jobResults) {
        return jobResults;
    }
}
