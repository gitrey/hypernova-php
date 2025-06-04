package com.example.hypernovaclient.plugin;

import com.example.hypernovaclient.model.HypernovaJob;
import com.example.hypernovaclient.model.HypernovaJobResult;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A {@link HypernovaPlugin} that enhances error visibility during development.
 * If a Hypernova job result contains an error, this plugin prepends a conspicuous
 * HTML warning block to the (potentially fallback) HTML of the job.
 * This makes it easier for developers to identify rendering failures directly on the page.
 * <p>
 * This plugin is typically only active in development environments.
 * </p>
 */
public class DevModePlugin extends BasePlugin {

    /**
     * {@inheritDoc}
     * <p>
     * This implementation checks each {@link HypernovaJobResult}. If a result indicates an error
     * and has an associated original job, it formats the error details into an HTML warning
     * and prepends it to the job's existing HTML content.
     * </p>
     */
    @Override
    @SuppressWarnings("unchecked") // For casting errorObj to Map and stack to List
    public Map<String, HypernovaJobResult> afterResponse(Map<String, HypernovaJobResult> jobResults) {
        if (jobResults == null) {
            return null;
        }

        for (HypernovaJobResult jobResult : jobResults.values()) {
            if (jobResult != null && jobResult.getError() != null && jobResult.getOriginalJob() != null) {
                String formattedErrorHtml = formatError(jobResult.getError());
                if (formattedErrorHtml != null && !formattedErrorHtml.isEmpty()) {
                    String originalHtml = jobResult.getHtml() == null ? "" : jobResult.getHtml();
                    String componentName = jobResult.getOriginalJob().getName();

                    // Using StringBuilder for clarity and efficiency
                    StringBuilder warningHtmlBuilder = new StringBuilder();
                    warningHtmlBuilder.append("<div style=\"background-color: #ff5a5f; color: #fff; padding: 12px; border: 1px solid #d00;\">");
                    warningHtmlBuilder.append("<p style=\"margin: 0 0 8px 0; font-weight: bold; font-size: 1.1em;\">");
                    warningHtmlBuilder.append("Development Warning: Component Rendering Failed");
                    warningHtmlBuilder.append("</p>");
                    warningHtmlBuilder.append("<p style=\"margin: 0 0 4px 0;\">");
                    warningHtmlBuilder.append("The component <code>")
                                      .append(escapeHtml(componentName)) // Basic HTML escaping for component name
                                      .append("</code> failed to render via Hypernova.");
                    warningHtmlBuilder.append("</p>");
                    warningHtmlBuilder.append("<p style=\"margin: 0 0 4px 0;\">Error details:</p>");
                    warningHtmlBuilder.append("<ul style=\"padding: 0 0 0 20px; margin: 0; list-style-type: disc;\">").append(formattedErrorHtml).append("</ul>");
                    warningHtmlBuilder.append("</div>");
                    warningHtmlBuilder.append(originalHtml); // Append original HTML (which might be a fallback)

                    jobResult.setHtml(warningHtmlBuilder.toString());
                }
            }
        }
        return jobResults;
    }

    /**
     * Formats an error object (typically from Hypernova) into an HTML string.
     * It handles errors that are Maps (with "message" and "stack" keys),
     * Strings, or Throwables.
     *
     * @param errorObj The error object to format.
     * @return An HTML string representing the error, or an empty string if formatting fails or error is trivial.
     */
    private String formatError(Object errorObj) {
        String messageHtml = "";
        String stackHtml = "";

        if (errorObj instanceof Map) {
            Map<String, Object> errorMap = (Map<String, Object>) errorObj;
            Object message = errorMap.get("message");
            if (message instanceof String && !((String) message).trim().isEmpty()) {
                messageHtml = "<li><strong>Message:</strong> " + escapeHtml((String) message) + "</li>";
            }

            Object stack = errorMap.get("stack");
            if (stack instanceof List) {
                List<?> stackList = (List<?>) stack;
                if (!stackList.isEmpty()) {
                    stackHtml = "<li><strong>Stack:</strong><ul><li>" + stackList.stream()
                                                 .map(item -> escapeHtml(String.valueOf(item)))
                                                 .collect(Collectors.joining("</li><li>")) + "</li></ul></li>";
                }
            } else if (stack instanceof String) {
                String stackString = (String) stack;
                if (!stackString.trim().isEmpty()) {
                    stackHtml = "<li><strong>Stack:</strong><ul><li>" + escapeHtml(stackString).replace("\n", "</li><li>") + "</li></ul></li>";
                }
            }
        } else if (errorObj instanceof String) {
             String errorString = (String) errorObj;
             if(!errorString.trim().isEmpty()){
                messageHtml = "<li><strong>Error:</strong> " + escapeHtml(errorString) + "</li>";
             }
        } else if (errorObj instanceof Throwable) {
            Throwable throwable = (Throwable) errorObj;
            if (throwable.getMessage() != null && !throwable.getMessage().trim().isEmpty()) {
                messageHtml = "<li><strong>Exception:</strong> " + escapeHtml(throwable.getClass().getName() + ": " + throwable.getMessage()) + "</li>";
            } else {
                 messageHtml = "<li><strong>Exception:</strong> " + escapeHtml(throwable.getClass().getName()) + "</li>";
            }
            StringBuilder sb = new StringBuilder();
            StackTraceElement[] stackTrace = throwable.getStackTrace();
            if (stackTrace != null && stackTrace.length > 0) {
                sb.append("<li><strong>Stack Trace:</strong><ul>");
                for (StackTraceElement element : stackTrace) {
                    sb.append("<li>").append(escapeHtml(element.toString())).append("</li>");
                }
                sb.append("</ul></li>");
                stackHtml = sb.toString();
            }
        }


        if (messageHtml.isEmpty() && stackHtml.isEmpty()) {
            return "";
        }
        return messageHtml + stackHtml;
    }

    /**
     * A basic HTML escaping utility to prevent simple XSS issues when displaying error content.
     *
     * @param text The text to escape.
     * @return The HTML-escaped string.
     */
    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#39;");
    }
}
