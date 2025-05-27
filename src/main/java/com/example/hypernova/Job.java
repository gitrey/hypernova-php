package com.example.hypernova;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

/**
 * Represents a single rendering job to be sent to a Hypernova server.
 * A job consists of a component name, data to render it with, and optional metadata.
 */
public class Job {
    private final String name;
    private final Map<String, Object> data;
    private final Map<String, String> metadata;

    /**
     * Constructs a new Job.
     *
     * @param name     The name of the component to render (e.g., "MyComponent.js").
     * @param data     A map containing the data (props) to render the component.
     *                 The values can be any JSON-serializable objects.
     * @param metadata A map containing metadata for this job. Can be null or empty.
     */
    public Job(
            @JsonProperty("name") String name,
            @JsonProperty("data") Map<String, Object> data,
            @JsonProperty("metadata") Map<String, String> metadata) {
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
     * Gets the data for rendering the component.
     * @return A map of data for the component.
     */
    public Map<String, Object> getData() {
        return data;
    }

    /**
     * Gets the metadata associated with this job.
     * @return A map of metadata.
     */
    public Map<String, String> getMetadata() {
        return metadata;
    }
}
