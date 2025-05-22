package com.example.demo.hypernova.plugin;

import com.example.demo.hypernova.HypernovaJob;
import com.example.demo.hypernova.HypernovaJobResult;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class DevModePlugin extends BaseHypernovaPlugin {

    @Override
    public Map<String, HypernovaJobResult> afterResponse(Map<String, HypernovaJobResult> jobResults) {
        if (jobResults == null) {
            return null;
        }
        for (HypernovaJobResult jobResult : jobResults.values()) {
            if (jobResult != null && jobResult.getError() != null) {
                formatErrorAndWrapHtml(jobResult);
            }
        }
        return jobResults;
    }

    private void formatErrorAndWrapHtml(HypernovaJobResult jobResult) {
        String componentName = "UnknownComponent";
        if (jobResult.getOriginalJob() != null && jobResult.getOriginalJob().getName() != null) {
            componentName = jobResult.getOriginalJob().getName();
        }

        String errorMessage = "Unknown error";
        String stackTraceHtml = "<li>No stack trace available.</li>";

        Object errorObj = jobResult.getError();
        if (errorObj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> errorMap = (Map<String, Object>) errorObj;
            errorMessage = (String) errorMap.getOrDefault("message", errorMessage);
            Object stackObj = errorMap.get("stack");
            if (stackObj instanceof List) {
                @SuppressWarnings("unchecked")
                List<String> stackList = (List<String>) stackObj;
                stackTraceHtml = stackList.stream()
                                          .map(line -> "<li>" + escapeHtml(line) + "</li>")
                                          .collect(Collectors.joining());
            } else if (stackObj instanceof String) {
                stackTraceHtml = "<li>" + escapeHtml((String) stackObj) + "</li>";
            }
        } else if (errorObj instanceof Throwable) {
            Throwable throwable = (Throwable) errorObj;
            errorMessage = throwable.getMessage();
            StringBuilder sb = new StringBuilder();
            for (StackTraceElement element : throwable.getStackTrace()) {
                sb.append("<li>").append(escapeHtml(element.toString())).append("</li>");
            }
            stackTraceHtml = sb.toString();
        } else if (errorObj != null) {
            errorMessage = errorObj.toString();
        }

        String errorHtml = "<div style=\"background-color: #ff5a5f; color: #fff; padding: 12px;\">" +
                           "<p style=\"margin: 0; font-weight: bold;\">" +
                           "<strong>Development Warning!</strong> " +
                           "The <code>" + escapeHtml(componentName) + "</code> component failed to render with Hypernova. Error stack:" +
                           "</p>" +
                           "<ul style=\"padding: 0 20px; margin-top: 5px; background-color: #f2dede; color: #a94442; border-radius: 4px;\">" +
                           "<li><strong>" + escapeHtml(errorMessage) + "</strong></li>" +
                           stackTraceHtml +
                           "</ul>" +
                           "</div>";

        String originalHtml = jobResult.getHtml() != null ? jobResult.getHtml() : "";
        jobResult.setHtml(errorHtml + originalHtml);
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&apos;");
    }
}
