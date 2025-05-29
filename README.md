# Java Client for Hypernova

## Overview

Hypernova is a service that enables server-side rendering (SSR) of JavaScript views. This allows you to render your client-side components on the server, sending fully-formed HTML to the browser. This can improve performance, SEO, and user experience.

This library is a Java client designed to facilitate interaction with a Hypernova service from a Java-based application, particularly one built with Spring Boot.

## Features

*   **Batching:** Efficiently renders multiple components (jobs) in a single request to the Hypernova service.
*   **Extensible Plugin System:** Allows customization of the rendering lifecycle through plugins. Hook into various stages like job creation, request preparation, response processing, and error handling.
*   **Fallback Mechanism:** Provides a configurable way to generate client-side fallback HTML if server-side rendering fails, ensuring users still see content.
*   **Spring Boot Integration:** Designed to be easily integrated into Spring Boot applications, with autoconfiguration support for key components.

## Prerequisites

*   Java 17 or newer.
*   Apache Maven 3.6.x or newer.
*   A running Hypernova service instance accessible from your application.

## Installation/Setup (for a Spring Boot project)

This library would typically be packaged as a JAR and deployed to a Maven repository (like Maven Central or a private one).

**If it were deployed to a repository:**

You would add the following dependency to your `pom.xml`:

```xml
<dependency>
    <groupId>com.example.hypernova</groupId> <!-- Replace with actual groupId -->
    <artifactId>hypernova-java-client</artifactId> <!-- Replace with actual artifactId -->
    <version>1.0.0</version> <!-- Replace with actual version -->
</dependency>
```

**For local module usage (current setup):**

Since this project (`demo`) itself contains the Hypernova client code, you are already using it directly. If this client were a separate module, you would include it as a local module dependency in your `pom.xml`.

```xml
<!-- Example if hypernova-java-client were a local module named 'hypernova-client' -->
<!--
<dependency>
    <groupId>com.example</groupId>
    <artifactId>hypernova-client</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>
-->
```

## Configuration

The `HypernovaRenderer` bean is the main entry point for using the client. It is automatically configured as a Spring bean if you are using the provided Spring Boot setup (`@Service` annotation on `HypernovaRenderer`).

**Required Beans:**

Your Spring Boot application needs to provide the following beans for `HypernovaRenderer` to function:

1.  `RestTemplate`: For making HTTP requests to the Hypernova service.
2.  `ObjectMapper`: For serializing and deserializing JSON data.

Example configuration in your main application class or a `@Configuration` class:

```java
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;

@Configuration
public class AppConfig {

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    @Bean
    public ObjectMapper objectMapper() {
        // You can customize the ObjectMapper here if needed
        return new ObjectMapper();
    }
}
```
*Note: The `DemoApplication` in this project already includes a `RestTemplate` bean. An `ObjectMapper` bean is typically auto-configured by Spring Boot if Jackson is on the classpath.*

**Hypernova Service URL:**

Configure the URL of your Hypernova service in your `application.properties` or `application.yml` file:

`application.properties`:
```properties
hypernova.service.url=http://localhost:3030/batch
```

`application.yml`:
```yaml
hypernova:
  service:
    url: http://localhost:3030/batch
```
If this property is not set, it defaults to `http://localhost:3030/batch`.

## Basic Usage

1.  **Inject `HypernovaRenderer`:** Autowire the `HypernovaRenderer` into your service or component.

    ```java
    import com.example.demo.hypernova.HypernovaRenderer;
    import org.springframework.beans.factory.annotation.Autowired;
    import org.springframework.stereotype.Service;

    @Service
    public class MyRenderingService {
        private final HypernovaRenderer hypernovaRenderer;

        @Autowired
        public MyRenderingService(HypernovaRenderer hypernovaRenderer) {
            this.hypernovaRenderer = hypernovaRenderer;
        }
        // ...
    }
    ```

2.  **Create and add `HypernovaJob` instances:** A `HypernovaJob` represents a component to be rendered.

    ```java
    import com.example.demo.hypernova.HypernovaJob;
    import java.util.Map;
    import java.util.HashMap;

    // ... inside a method in MyRenderingService ...
    Map<String, Object> componentData = new HashMap<>();
    componentData.put("title", "Hello from Java!");
    componentData.put("message", "This component was rendered server-side.");

    HypernovaJob job1 = new HypernovaJob("MyReactComponent.js", componentData, null);
    // The first argument to addJob is a unique client-side identifier for this job
    hypernovaRenderer.addJob("uniqueJobId1", job1);

    HypernovaJob job2 = new HypernovaJob("AnotherComponent.vue", Map.of("user", "Alice"), null);
    hypernovaRenderer.addJob("uniqueJobId2", job2);
    ```

3.  **Call `renderer.render()`:** This sends the batched jobs to the Hypernova service.

    ```java
    import com.example.demo.hypernova.HypernovaResponse;

    HypernovaResponse response = hypernovaRenderer.render();
    ```

4.  **Access results:** The `HypernovaResponse` contains the rendering results.

    ```java
    import com.example.demo.hypernova.HypernovaJobResult;

    if (response.getError() != null) {
        // Handle top-level error (e.g., Hypernova service unreachable)
        System.err.println("Hypernova request failed: " + response.getError());
    }

    HypernovaJobResult result1 = response.getResults().get("uniqueJobId1");
    if (result1 != null && result1.isSuccess()) {
        System.out.println("Rendered HTML for job1: " + result1.getHtml());
    } else if (result1 != null) {
        System.err.println("Job1 rendering failed: " + result1.getError());
        // Fallback HTML might be in result1.getHtml() if DevModePlugin is active
    }
    ```

## Plugin System

The client features a plugin system that allows you to hook into various lifecycle stages of the rendering process.

*   **`HypernovaPlugin` Interface:** Defines methods corresponding to different lifecycle events.
*   **`BaseHypernovaPlugin`:** An abstract class providing no-op implementations for all `HypernovaPlugin` methods, making it convenient to extend and override only the methods you need.

**Key Lifecycle Methods:**

*   `getViewData(String viewName, Map<String, Object> data, HypernovaJob originalJob)`: Allows modification of job data before it's processed further.
*   `prepareRequest(List<HypernovaJob> jobs, List<HypernovaJob> originalJobs)`: Allows modification of the list of jobs just before sending to Hypernova.
*   `shouldSendRequest(List<HypernovaJob> jobs)`: Can prevent the request from being sent entirely.
*   `willSendRequest(List<HypernovaJob> jobs)`: Called just before the HTTP request is made.
*   `onSuccess(HypernovaJobResult jobResult)`: Called for each successfully rendered job.
*   `onError(Object error, List<HypernovaJobResult> jobResults, List<HypernovaJob> originalJobs)`: Called if there are errors during the process (either top-level or per-job).
*   `afterResponse(Map<String, HypernovaJobResult> jobResults)`: Allows modification of the results map after the response is received and processed.

**Creating a Custom Plugin:**

```java
package com.example.demo.plugins;

import com.example.demo.hypernova.HypernovaJob;
import com.example.demo.hypernova.HypernovaJobResult;
import com.example.demo.hypernova.plugin.BaseHypernovaPlugin;
import org.springframework.stereotype.Component;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component // Make it a Spring bean to be auto-detected or manually add it
public class MyCustomPlugin extends BaseHypernovaPlugin {
    private static final Logger LOGGER = LoggerFactory.getLogger(MyCustomPlugin.class);

    @Override
    public HypernovaJob getViewData(String viewName, Map<String, Object> data, HypernovaJob originalJob) {
        LOGGER.info("MyCustomPlugin: Processing view data for component: {}", viewName);
        // Example: Add a common piece of data to all jobs
        if (data != null) {
            data.put("commonPluginData", "Hello from plugin!");
        }
        return new HypernovaJob(viewName, data, originalJob != null ? originalJob.getMetadata() : null);
    }

    @Override
    public void onSuccess(HypernovaJobResult jobResult) {
        LOGGER.info("MyCustomPlugin: Job successfully rendered: {}", jobResult.getOriginalJob().getName());
    }
}
```

**Registering a Plugin:**

If your plugin is a Spring bean (annotated with `@Component` or defined via `@Bean`), you can inject it into your service where `HypernovaRenderer` is used and then add it:

```java
// In your service or configuration class
@Autowired
public MyRenderingService(HypernovaRenderer hypernovaRenderer, MyCustomPlugin myCustomPlugin) {
    this.hypernovaRenderer = hypernovaRenderer;
    this.hypernovaRenderer.addPlugin(myCustomPlugin);
    // Add other plugins if needed
}
```
Alternatively, you can directly instantiate and add plugins if they are not Spring-managed, though Spring management is recommended.

## Included Plugins

*   **`DevModePlugin`:** (Located in `com.example.demo.hypernova.plugin.DevModePlugin`)
    *   If a component fails to render and an error is present in the `HypernovaJobResult`, this plugin prepends a detailed error message (including component name, error message, and stack trace) wrapped in styled HTML to the original (often fallback) HTML. This is very useful during development to quickly identify issues with server-side rendering.
    *   It is automatically registered if it's a Spring bean in the application context and added to the `HypernovaRenderer` instance.

## Error Handling

*   **Top-level errors:** If the entire batch request to Hypernova fails (e.g., network issue, Hypernova service down), the `HypernovaResponse.getError()` method will return an error object.
*   **Per-job errors:** Individual jobs might fail to render even if the batch request itself succeeds. In such cases, the specific `HypernovaJobResult` for that job will have its `isSuccess()` method return `false`, and `getError()` will contain details about the error (often a map with `message` and `stack` keys).
*   **Plugin interaction:** Plugins can react to errors via the `onError` lifecycle method. The `DevModePlugin` is an example of this, formatting errors into user-visible messages.

## Contributing

Contributions are welcome! If you have suggestions, bug reports, or want to contribute code, please feel free to:

1.  Fork the repository.
2.  Create a new branch for your feature or fix.
3.  Make your changes.
4.  Add appropriate tests.
5.  Submit a pull request.

## License

This project is licensed under the MIT License. See the `LICENSE` file for details (assuming one would be added, mirroring common open-source practices).

