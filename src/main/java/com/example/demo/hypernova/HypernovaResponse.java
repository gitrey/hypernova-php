package com.example.demo.hypernova;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Represents the overall response from a Hypernova batch request.
 * It contains a map of job results and a potential top-level error.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true) // Allows for new fields from Hypernova service without breaking client
public class HypernovaResponse {
    /**
     * A map of job results. 
     * The keys are the client-side unique identifiers that were originally passed to {@link HypernovaRenderer#addJob(String, HypernovaJob)}.
     * The values are the corresponding {@link HypernovaJobResult} instances.
     */
    private Map<String, HypernovaJobResult> results;

    /**
     * An object detailing a top-level error if the entire batch request failed
     * (e.g., Hypernova service unreachable, malformed batch request).
     * Structure can vary; typically a Map from Hypernova.
     */
    private Object error;
}
