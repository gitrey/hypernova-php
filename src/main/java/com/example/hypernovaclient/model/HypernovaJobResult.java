package com.example.hypernovaclient.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.Map;

/**
 * Represents the result of rendering a single Hypernova job.
 * This class typically corresponds to one entry in the "results" map
 * of a Hypernova server response.
 */
public class HypernovaJobResult {

    /**
     * The rendered HTML content of the component.
     * Can be null if an error occurred and no fallback HTML was generated.
     */
    private String html;

    /**
     * An object detailing the error if one occurred during rendering.
     * The structure of this object (e.g., a Map with "message" and "stack" keys)
     * can depend on the Hypernova server implementation. Null if no error.
     */
    private Object error;

    /**
     * Metadata associated with the rendering process (e.g., cache hit status).
     */
    private Map<String, Object> meta;

    /**
     * A reference to the original {@link HypernovaJob} that produced this result.
     * This field is ignored during JSON deserialization of the Hypernova server response
     * and is intended to be populated by the client after parsing.
     */
    @JsonIgnore
    private HypernovaJob originalJob;

    /**
     * Indicates whether the job was rendered successfully.
     */
    private boolean success;

    /**
     * The duration of the rendering process on the server-side, in milliseconds.
     */
    private double duration;

    /**
     * The name of the view/component that was rendered.
     * Typically corresponds to the {@code name} field of the {@link HypernovaJob}.
     * This field is often named "view_name" in Hypernova JSON responses.
     */
    private String viewName;

    /**
     * Default constructor, often used by JSON deserialization libraries like Jackson.
     */
    public HypernovaJobResult() {
        // Default constructor for Jackson
    }

    /**
     * Gets the rendered HTML.
     * @return The HTML string, or null if not available.
     */
    public String getHtml() {
        return html;
    }

    /**
     * Sets the rendered HTML.
     * @param html The HTML string.
     */
    public void setHtml(String html) {
        this.html = html;
    }

    /**
     * Gets the error object.
     * @return The error object (e.g., Map, String), or null if no error.
     */
    public Object getError() {
        return error;
    }

    /**
     * Sets the error object.
     * @param error The error object.
     */
    public void setError(Object error) {
        this.error = error;
    }

    /**
     * Gets the metadata associated with the render.
     * @return A map of metadata.
     */
    public Map<String, Object> getMeta() {
        return meta;
    }

    /**
     * Sets the metadata.
     * @param meta A map of metadata.
     */
    public void setMeta(Map<String, Object> meta) {
        this.meta = meta;
    }

    /**
     * Gets the original job that this result corresponds to.
     * @return The {@link HypernovaJob}.
     */
    public HypernovaJob getOriginalJob() {
        return originalJob;
    }

    /**
     * Sets the original job. This is typically done by the client after
     * receiving and deserializing the response from the Hypernova server.
     * @param originalJob The {@link HypernovaJob}.
     */
    public void setOriginalJob(HypernovaJob originalJob) {
        this.originalJob = originalJob;
    }

    /**
     * Checks if the job rendering was successful.
     * @return True if successful, false otherwise.
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * Sets the success status of the job rendering.
     * @param success True if successful, false otherwise.
     */
    public void setSuccess(boolean success) {
        this.success = success;
    }

    /**
     * Gets the server-side duration of the rendering.
     * @return The duration in milliseconds.
     */
    public double getDuration() {
        return duration;
    }

    /**
     * Sets the server-side duration of the rendering.
     * @param duration The duration in milliseconds.
     */
    public void setDuration(double duration) {
        this.duration = duration;
    }

    /**
     * Gets the name of the view/component that was rendered.
     * @return The view name.
     */
    public String getViewName() {
        return viewName;
    }

    /**
     * Sets the name of the view/component that was rendered.
     * @param viewName The view name.
     */
    public void setViewName(String viewName) {
        this.viewName = viewName;
    }
}
