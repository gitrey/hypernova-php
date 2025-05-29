package com.example.demo.hypernova;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Represents a job to be rendered by Hypernova.
 * A job consists of a component name and the data to be passed to it.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL) // Ensures that null fields are not included in JSON output
public class HypernovaJob {
    /**
     * The name of the component to be rendered (e.g., "MyComponent.js").
     */
    private String name;

    /**
     * The data to be passed to the component for rendering.
     * Keys are property names, and values are their corresponding values.
     */
    private Map<String, Object> data;

    /**
     * Optional metadata associated with this job.
     * This can be used by plugins or for tracking purposes.
     * The client-side unique ID for this job is stored here by HypernovaRenderer under the key "_originalClientId".
     */
    private Map<String, Object> metadata;
}
