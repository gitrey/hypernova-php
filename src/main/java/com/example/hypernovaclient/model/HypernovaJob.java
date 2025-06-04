package com.example.hypernovaclient.model;

import java.util.Map;

/**
 * Represents a single job to be rendered by Hypernova.
 * A job consists of a component name, data to pass to the component,
 * and optional metadata.
 */
public class HypernovaJob {

    private String name;
    private Map<String, Object> data;
    private Map<String, Object> metadata;

    /**
     * Constructs a new HypernovaJob.
     *
     * @param name The name of the component to render (e.g., "MyComponent.js").
     * @param data A map containing the data (props) to be passed to the component.
     * @param metadata A map containing metadata associated with this job. Can be null or empty.
     */
    public HypernovaJob(String name, Map<String, Object> data, Map<String, Object> metadata) {
        this.name = name;
        this.data = data;
        this.metadata = metadata;
    }

    /**
     * Gets the name of the component.
     * @return The component name.
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the name of the component.
     * @param name The new component name.
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Gets the data (props) for the component.
     * @return A map of data for the component.
     */
    public Map<String, Object> getData() {
        return data;
    }

    /**
     * Sets the data (props) for the component.
     * @param data A new map of data.
     */
    public void setData(Map<String, Object> data) {
        this.data = data;
    }

    /**
     * Gets the metadata for this job.
     * @return A map of metadata.
     */
    public Map<String, Object> getMetadata() {
        return metadata;
    }

    /**
     * Sets the metadata for this job.
     * @param metadata A new map of metadata.
     */
    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }
}
