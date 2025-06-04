# Hypernova Client for Java

## Overview

[Hypernova](https://github.com/airbnb/hypernova) is a service for server-side rendering of JavaScript views. This project provides a Java client for interacting with a Hypernova server, allowing Java-based applications to easily delegate the rendering of JavaScript components (e.g., React, Vue, Angular) to a Hypernova instance.

This client is built using Spring Boot, providing autoconfiguration and easy integration into Spring applications. It includes a flexible plugin system to hook into various stages of the rendering lifecycle.

## Features

*   **Spring Boot Integration**: Auto-configures necessary beans like `RestTemplate`, `ObjectMapper`, and the core `HypernovaRenderer`.
*   **Configurable**: Easily configure Hypernova server URL and HTTP client timeouts via `application.properties`.
*   **Batch Rendering**: Supports sending multiple rendering jobs in a single batch request to Hypernova.
*   **Plugin System**: Extensible plugin architecture (`HypernovaPlugin`) to modify job data, alter requests/responses, and handle events (e.g., success, error).
*   **`DevModePlugin`**: An optional plugin that enhances error visibility directly in the browser during development by prepending detailed error information to the fallback HTML of failed components.
*   **Fallback HTML**: Generates standard Hypernova-compatible fallback HTML if a component fails to render or an error occurs.

## Requirements

*   Java 21+
*   Spring Boot 3.x+
*   A running Hypernova server instance.

## Getting Started

This client is designed to be used as a module within a Spring Boot application.

### Maven Dependency

To include this client in your Maven project, add the following dependency (assuming it's deployed to a Maven repository; for local builds, ensure it's installed/available in your local .m2 repository):

```xml
<dependency>
    <groupId>com.example</groupId>
    <artifactId>hypernova-client</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>
```

You will also need Spring Boot starters, typically `spring-boot-starter-web`. If using validation annotations in configuration properties, include `spring-boot-starter-validation`.

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
</dependency>
```

## Configuration

Configure the Hypernova client using your Spring Boot `application.properties` (or `application.yml`) file.

Key properties:

*   `hypernova.client.url`: **(Required)** The full URL to your Hypernova server's batch endpoint.
*   `hypernova.client.connectTimeout`: Connection timeout in milliseconds. Default: `5000` (5 seconds).
*   `hypernova.client.readTimeout`: Read timeout in milliseconds. Default: `5000` (5 seconds).

**Example `application.properties`:**

```properties
hypernova.client.url=http://your-hypernova-server.example.com/batch
hypernova.client.connectTimeout=3000
hypernova.client.readTimeout=5000
```

## Usage

Autowire the `HypernovaRenderer` bean into your Spring components (e.g., Services, Controllers).

```java
import com.example.hypernovaclient.HypernovaRenderer;
import com.example.hypernovaclient.model.HypernovaJob;
import com.example.hypernovaclient.model.HypernovaResponse;
import com.example.hypernovaclient.model.HypernovaJobResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashMap;
import java.util.List; // Added for List.of example
import java.util.Map;

@Service
public class MyRenderService {

    private final HypernovaRenderer hypernovaRenderer;

    @Autowired
    public MyRenderService(HypernovaRenderer hypernovaRenderer) {
        this.hypernovaRenderer = hypernovaRenderer;
    }

    public String renderMyPageComponents() {
        // Prepare data for your components
        Map<String, Object> component1Data = new HashMap<>();
        component1Data.put("title", "Welcome User!");
        component1Data.put("items", List.of("Item 1", "Item 2"));

        Map<String, Object> component2Data = Collections.singletonMap("message", "Another Component");

        // Add jobs to the renderer
        // The first argument is a unique ID for this job within this batch.
        hypernovaRenderer.addJob("HeaderComponent_1", new HypernovaJob("Header.js", component1Data, null));
        hypernovaRenderer.addJob("SidebarComponent_1", new HypernovaJob("Sidebar.js", component2Data, null));

        // Render all added jobs
        HypernovaResponse response = hypernovaRenderer.render();

        StringBuilder pageHtml = new StringBuilder();

        // Process results
        if (response.getError() != null) {
            // Handle batch-level error (e.g., Hypernova server down)
            pageHtml.append("<p>Error rendering page components: ")
                    .append(response.getError().toString())
                    .append("</p>");
            // You might want to append fallback HTML for all jobs here
             response.getResults().forEach((id, jobResult) -> {
                pageHtml.append("<!-- Fallback for ").append(id).append(" -->");
                pageHtml.append(jobResult.getHtml()); // This will be the client-generated fallback
            });

        } else if (response.getResults() != null) { // Check if results map is not null
            HypernovaJobResult headerResult = response.getResults().get("HeaderComponent_1");
            if (headerResult != null) {
                pageHtml.append(headerResult.getHtml());
                if (headerResult.getError() != null) {
                    // Log individual component error: headerResult.getError()
                    System.err.println("Error in HeaderComponent_1: " + headerResult.getError());
                }
            }

            HypernovaJobResult sidebarResult = response.getResults().get("SidebarComponent_1");
            if (sidebarResult != null) {
                pageHtml.append(sidebarResult.getHtml());
                 if (sidebarResult.getError() != null) {
                    // Log individual component error: sidebarResult.getError()
                    System.err.println("Error in SidebarComponent_1: " + sidebarResult.getError());
                }
            }
        }
        return pageHtml.toString();
    }
}
```

## Plugin System

The client features a plugin system that allows you to hook into various stages of the rendering lifecycle. This is useful for:
*   Modifying job data before sending it.
*   Adding common metadata to jobs.
*   Altering the request or response structure (advanced).
*   Logging or metrics collection.
*   Custom error handling or fallback strategies.

To create a plugin, implement the `com.example.hypernovaclient.plugin.HypernovaPlugin` interface or extend the `com.example.hypernovaclient.plugin.BasePlugin` adapter class (which provides no-op default methods).

**Plugin Interface Methods:**
*   `getViewData`: Modify data for a single job.
*   `prepareRequest`: Modify the entire batch of jobs before sending.
*   `shouldSendRequest`: Conditionally cancel the request.
*   `willSendRequest`: Notification before the request is sent.
*   `onSuccess`: Called for each successfully rendered job.
*   `onJobError`: Called for each job that fails on the server.
*   `onBatchError`: Called if the entire batch request fails (e.g., network error, server error).
*   `afterResponse`: Modify the final results map before it's returned.

**Example: A Simple Metadata Plugin**

```java
import com.example.hypernovaclient.model.HypernovaJob;
import com.example.hypernovaclient.plugin.BasePlugin;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID; // For a hypothetical request ID

@Component // Register as a Spring bean to be auto-detected
public class CommonMetadataPlugin extends BasePlugin {

    // Hypothetical way to get a request ID, replace with your actual mechanism
    private String getCurrentRequestId() {
        // In a web application, this might come from a ThreadLocal managed by a filter
        return UUID.randomUUID().toString().substring(0, 8);
    }

    @Override
    public Map<String, Object> getViewData(String jobName, Map<String, Object> data) {
        Map<String, Object> augmentedData = new HashMap<>(data);
        Map<String, Object> commonPluginData = new HashMap<>();
        commonPluginData.put("plugin_request_id", getCurrentRequestId());
        commonPluginData.put("plugin_user_locale", "en-US");

        // Example: Add these as a nested map under 'pluginCommonData' in the job's data
        augmentedData.put("pluginCommonData", commonPluginData);
        return augmentedData;
    }

    // Alternative: If you wanted to modify HypernovaJob.metadata instead:
    /*
    @Override
    public Map<String, HypernovaJob> prepareRequest(Map<String, HypernovaJob> jobs, Map<String, HypernovaJob> originalJobs) {
        for (HypernovaJob job : jobs.values()) {
            Map<String, Object> metadata = job.getMetadata() != null ? new HashMap<>(job.getMetadata()) : new HashMap<>();
            metadata.put("plugin_request_id", getCurrentRequestId());
            job.setMetadata(metadata);
        }
        return jobs;
    }
    */
}
```

Plugins registered as Spring beans (e.g., annotated with `@Component`) will be automatically discovered and injected into the `HypernovaRenderer`.

### `DevModePlugin`

This client includes an optional `DevModePlugin`. When active (e.g., in a development Spring profile), if a component fails to render, this plugin prepends a highly visible HTML block to the component's output, showing the error message and stack trace directly on the page. This aids in quicker debugging during development.

To activate it, ensure `DevModePlugin` is available as a bean in your application context for the desired profiles.
```java
import com.example.hypernovaclient.plugin.DevModePlugin;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
public class MyPluginConfig {

    @Bean
    @Profile("dev") // Only active in 'dev' Spring profile
    public DevModePlugin devModePlugin() {
        return new DevModePlugin();
    }
}
```

## Error Handling

*   **Batch Errors**: If the entire request to Hypernova fails (e.g., network issue, server 500 error, or a top-level error in the JSON response), the `HypernovaResponse.getError()` field will be populated. Plugin `onBatchError` hooks will also be triggered. In such cases, individual jobs will typically receive client-generated fallback HTML.
*   **Job-Specific Errors**: If a specific component fails to render on the server, its corresponding `HypernovaJobResult.getError()` field will contain error details (often a map with "message" and "stack" keys). The `HypernovaJobResult.getHtml()` might contain fallback HTML provided by the server, or if null, the client will generate its standard fallback. Plugin `onJobError` hooks are triggered.
*   **Fallback HTML**: When a job cannot be rendered successfully, the client generates a standard Hypernova fallback:
    ```html
    <div data-hypernova-key="MyComponent.js" data-hypernova-id="[UUID]"></div>
    <script type="application/json" data-hypernova-key="MyComponent.js" data-hypernova-id="[UUID]"><!-- {"prop":"value"} --></script>
    ```
    This allows client-side JavaScript to potentially re-initialize the component or handle the failure gracefully.

## License

This project is licensed under the BSD-2-Clause License.
A copy of the license should be included in a `LICENSE` file in the repository.
(Note: A `LICENSE` file with the BSD-2-Clause text would need to be added to the repository).
