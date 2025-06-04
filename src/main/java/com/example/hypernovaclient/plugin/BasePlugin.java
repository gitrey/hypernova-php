package com.example.hypernovaclient.plugin;

import com.example.hypernovaclient.model.HypernovaJob;
import com.example.hypernovaclient.model.HypernovaJobResult;

import java.util.Map;

/**
 * A base implementation of {@link HypernovaPlugin} that provides no-op (no operation)
 * default implementations for all plugin methods.
 * <p>
 * This class serves as a convenient adapter for creating custom plugins.
 * Developers can extend {@code BasePlugin} and override only the methods
 * for the lifecycle events they are interested in.
 * </p>
 */
public abstract class BasePlugin implements HypernovaPlugin {

    /**
     * {@inheritDoc}
     * <p>Default implementation returns the input data map unchanged.</p>
     */
    @Override
    public Map<String, Object> getViewData(String jobName, Map<String, Object> data) {
        return data;
    }

    /**
     * {@inheritDoc}
     * <p>Default implementation returns the input jobs map unchanged.</p>
     */
    @Override
    public Map<String, HypernovaJob> prepareRequest(Map<String, HypernovaJob> jobs, Map<String, HypernovaJob> originalJobs) {
        return jobs;
    }

    /**
     * {@inheritDoc}
     * <p>Default implementation always returns {@code true}, allowing the request to proceed.</p>
     */
    @Override
    public boolean shouldSendRequest(Map<String, HypernovaJob> jobs) {
        return true;
    }

    /**
     * {@inheritDoc}
     * <p>Default implementation is a no-op.</p>
     */
    @Override
    public void willSendRequest(Map<String, HypernovaJob> jobs) {
        // No-op
    }

    /**
     * {@inheritDoc}
     * <p>Default implementation is a no-op.</p>
     */
    @Override
    public void onSuccess(HypernovaJobResult jobResult) {
        // No-op
    }

    /**
     * {@inheritDoc}
     * <p>Default implementation is a no-op.</p>
     */
    @Override
    public void onJobError(HypernovaJobResult jobResult) {
        // No-op
    }

    /**
     * {@inheritDoc}
     * <p>Default implementation is a no-op.</p>
     */
    @Override
    public void onBatchError(Object error, Map<String, HypernovaJob> jobs) {
        // No-op
    }

    /**
     * {@inheritDoc}
     * <p>Default implementation returns the input jobResults map unchanged.</p>
     */
    @Override
    public Map<String, HypernovaJobResult> afterResponse(Map<String, HypernovaJobResult> jobResults) {
        return jobResults;
    }
}
