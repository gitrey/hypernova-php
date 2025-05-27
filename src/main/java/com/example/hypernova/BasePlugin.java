package com.example.hypernova;

import java.util.List;
import java.util.Map;

/**
 * A base implementation of the {@link Plugin} interface, providing default no-op (no operation)
 * or pass-through implementations for all methods.
 * This class can be extended by custom plugins to override only the specific lifecycle methods
 * they need to customize.
 */
public class BasePlugin implements Plugin {

    /**
     * Default implementation returns the data map unchanged.
     * {@inheritDoc}
     */
    @Override
    public Map<String, Object> getViewData(String name, Map<String, Object> data) {
        return data;
    }

    /**
     * Default implementation returns the jobs map unchanged.
     * {@inheritDoc}
     */
    @Override
    public Map<String, Job> prepareRequest(Map<String, Job> jobs, Map<String, Job> originalJobs) {
        return jobs;
    }

    /**
     * Default implementation always returns `true`, allowing the request to proceed.
     * {@inheritDoc}
     */
    @Override
    public boolean shouldSendRequest(Map<String, Job> jobs) {
        return true;
    }

    /**
     * Default implementation is a no-op.
     * {@inheritDoc}
     */
    @Override
    public void willSendRequest(Map<String, Job> jobs) {
        // No-op
    }

    /**
     * Default implementation is a no-op.
     * {@inheritDoc}
     */
    @Override
    public void onError(HypernovaError error, List<Job> jobs) {
        // No-op
    }

    /**
     * Default implementation is a no-op.
     * {@inheritDoc}
     */
    @Override
    public void onSuccess(JobResult jobResult) {
        // No-op
    }

    /**
     * Default implementation returns the job results map unchanged.
     * {@inheritDoc}
     */
    @Override
    public Map<String, JobResult> afterResponse(Map<String, JobResult> jobResults) {
        return jobResults;
    }
}
