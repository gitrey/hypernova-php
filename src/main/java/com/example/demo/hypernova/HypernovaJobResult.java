package com.example.demo.hypernova;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Represents the result of a Hypernova rendering job for a single component.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true) // Allows for new fields from Hypernova service without breaking client
public class HypernovaJobResult {
    /**
     * The rendered HTML string for the component. Can be null if rendering failed.
     * If rendering failed and a fallback mechanism is active (e.g., via DevModePlugin),
     * this HTML might contain the fallback UI.
     */
    private String html;

    /**
     * An object detailing the error if rendering failed. Structure can vary.
     * Typically a Map containing "message" and "stack" (list of strings).
     * Can also be a simple string or other JSON type if the error originates outside Hypernova's typical structure.
     */
    private Object error;

    /**
     * Indicates whether the rendering was successful.
     */
    private boolean success;

    /**
     * The original HypernovaJob that this result corresponds to.
     * This is enriched by the HypernovaRenderer to include the client-side job details.
     */
    private HypernovaJob originalJob;

    /**
     * Optional metadata associated with this job result.
     * Can be used by plugins or for tracking. The HypernovaRenderer stores the original client-side job ID
     * here under the key "_originalClientId".
     */
    private Map<String, Object> meta;
}
