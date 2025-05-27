package com.example.hypernova;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A plugin that enhances error reporting during development.
 * If a job fails to render, this plugin wraps the original (fallback) HTML
 * with a prominent error message displaying the component name, error message,
 * and stack trace directly in the browser.
 */
public class DevModePlugin extends BasePlugin {

    /**
     * Modifies job results after the Hypernova server response is processed.
     * If a {@link JobResult} contains an error, this method calls {@link #wrapErrorOutput(JobResult)}
     * to prepend a detailed error message to the job's HTML.
     *
     * @param jobResults A map of job identifiers to their {@link JobResult}.
     * @return The modified map of job results, where errored jobs have enhanced HTML.
     */
    @Override
    public Map<String, JobResult> afterResponse(Map<String, JobResult> jobResults) {
        Map<String, JobResult> modifiedJobResults = new HashMap<>();
        for (Map.Entry<String, JobResult> entry : jobResults.entrySet()) {
            String key = entry.getKey();
            JobResult jobResult = entry.getValue();
            if (jobResult.getError() != null) {
                // If there's an error, wrap the output with detailed error information
                modifiedJobResults.put(key, wrapErrorOutput(jobResult));
            } else {
                modifiedJobResults.put(key, jobResult);
            }
        }
        return modifiedJobResults;
    }

    /**
     * Wraps the HTML of an errored {@link JobResult} with a development-friendly error message.
     * The error message includes the component name, error details, and stack trace,
     * styled to be clearly visible in the browser.
     *
     * @param jobResult The {@link JobResult} that contains an error.
     * @return A new {@link JobResult} instance with the modified HTML. The success status remains false.
     */
    private JobResult wrapErrorOutput(JobResult jobResult) {
        HypernovaError error = jobResult.getError();
        String originalJobName = jobResult.getOriginalJob().getName();

        String errorMessageListItem = error.getMessage() != null ? "<li><strong>" + escapeHtml(error.getMessage()) + "</strong></li>" : "";
        String stackTraceListItems = "";
        if (error.getStack() != null && !error.getStack().isEmpty()) {
            stackTraceListItems = error.getStack().stream()
                    .map(line -> "<li>" + escapeHtml(line) + "</li>")
                    .collect(Collectors.joining(""));
        }

        String newHtml = String.format(
                "<div style=\"background-color: #ff5a5f; color: #fff; padding: 12px; border: 1px solid #d00; margin-bottom: 10px;\">" +
                "<p style=\"margin: 0; font-weight: bold;\">" +
                "Development Warning: Hypernova Component Error" +
                "</p>" +
                "<p style=\"margin: 5px 0;\">" +
                "Component: <code>%s</code>" +
                "</p>" +
                "<p style=\"margin: 5px 0;\">Error Details:</p>" +
                "<ul style=\"padding: 0 20px; margin: 5px 0; list-style-type: disc;\">" +
                "%s" +  // Error message list item
                "%s" +  // Stack trace list items
                "</ul>" +
                "</div>" +
                "%s", // Original HTML (which is likely the fallback HTML)
                escapeHtml(originalJobName),
                errorMessageListItem,
                stackTraceListItems,
                jobResult.getHtml()
        );

        // Return a new JobResult with the enhanced HTML
        return new JobResult(
                jobResult.getError(),     // The original error object
                newHtml,                  // The new HTML with the error message prepended
                false,                    // Success is false because an error occurred
                jobResult.getOriginalJob(),// Reference to the original job
                jobResult.getMeta(),      // Original metadata
                jobResult.getDuration()   // Original duration
        );
    }

    /**
     * Escapes HTML special characters in a string to prevent XSS vulnerabilities
     * when embedding text content within HTML.
     * Specifically escapes '&amp;', '&lt;', and '&gt;'.
     *
     * @param text The string to escape. If null, returns an empty string.
     * @return The escaped string.
     */
    private String escapeHtml(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;");
    }
}
