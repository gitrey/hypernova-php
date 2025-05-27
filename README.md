# Hypernova Client for Java

A Java client library for interacting with a Hypernova server for Server-Side Rendering (SSR) of UI components.

This library is a Java 17 rewrite of the original PHP client, providing similar features and a plugin architecture.

## Features

*   Batching of rendering jobs
*   Plugin system for extending functionality (e.g., `DevModePlugin` for enhanced error reporting)
*   Server-Side Rendering via Hypernova
*   Fallback HTML generation on errors
*   Configurable HTTP client (OkHttp)

## Requirements

*   Java 17 or later
*   Hypernova server accessible via HTTP

## Installation

To use this library in your project, add the following dependency to your build configuration.

**(Assuming a group ID `com.example` and artifact ID `hypernova-java-client`, version `1.0.0`. Replace with actual coordinates when published.)**

**Maven:**
```xml
<dependency>
    <groupId>com.example</groupId>
    <artifactId>hypernova-java-client</artifactId>
    <version>1.0.0</version>
</dependency>
```

**Gradle:**
```groovy
implementation 'com.example:hypernova-java-client:1.0.0'
```

## Basic Usage

Here's a simple example of how to use the `HypernovaRenderer`:

```java
// main method or relevant part of your application
import com.example.hypernova.DevModePlugin;
import com.example.hypernova.HypernovaRenderer;
import com.example.hypernova.HypernovaResponse;
import com.example.hypernova.Job;
import com.example.hypernova.JobResult;
import com.example.hypernova.Plugin;
import okhttp3.OkHttpClient; // For custom client example
import com.fasterxml.jackson.databind.ObjectMapper; // For custom mapper example

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class Main {
    public static void main(String[] args) {
        // 1. Create a HypernovaRenderer instance
        // Replace "http://localhost:3030/batch" with your Hypernova server URL
        
        // Basic renderer:
        // HypernovaRenderer renderer = new HypernovaRenderer("http://localhost:3030/batch");

        // Renderer with plugins:
        List<Plugin> plugins = new ArrayList<>();
        plugins.add(new DevModePlugin()); 
        // plugins.add(new LoggingPlugin()); // Assuming LoggingPlugin is defined as per example below
        
        HypernovaRenderer renderer = new HypernovaRenderer(
            "http://localhost:3030/batch", // Hypernova server URL
            plugins,                       // List of plugins
            new OkHttpClient(),            // Default or custom OkHttpClient
            new ObjectMapper()             // Default or custom ObjectMapper
        );

        // 2. Add jobs
        Map<String, Object> componentData1 = Map.of("title", "My Component 1");
        Job job1 = new Job("MyComponent.js", componentData1, Collections.emptyMap());
        renderer.addJob("component1", job1);

        Map<String, Object> componentData2 = Map.of("message", "Hello from Hypernova!");
        // Or use the convenience method:
        renderer.addJob("component2", "OtherComponent.js", componentData2, Collections.emptyMap());

        // 3. Render the jobs
        HypernovaResponse response = renderer.render();

        // 4. Process the response
        if (response.getError() != null) {
            System.err.println("Top-level error rendering jobs: " + response.getError().getMessage());
            if (response.getError().getStack() != null) {
                response.getError().getStack().forEach(line -> System.err.println("  " + line));
            }
        }

        for (Map.Entry<String, JobResult> entry : response.getResults().entrySet()) {
            String id = entry.getKey();
            JobResult result = entry.getValue();

            if (result.isSuccess()) {
                System.out.println("Rendered HTML for " + id + " (" + result.getOriginalJob().getName() + "):");
                System.out.println(result.getHtml());
            } else {
                System.err.println("Error rendering " + id + " (" + result.getOriginalJob().getName() + "): " + 
                                   (result.getError() != null ? result.getError().getMessage() : "Unknown error"));
                if (result.getError() != null && result.getError().getStack() != null) {
                    result.getError().getStack().forEach(line -> System.err.println("  " + line));
                }
                System.err.println("Fallback HTML for " + id + ":");
                System.err.println(result.getHtml()); // This will be fallback HTML, possibly wrapped by DevModePlugin
            }
        }
    }
}
```

## Plugins

The Hypernova client features a plugin system that allows you to hook into various stages of the rendering lifecycle. This enables customization for logging, modifying job data, error handling, and more.

To create a plugin, implement the `com.example.hypernova.Plugin` interface or extend the `com.example.hypernova.BasePlugin` class (which provides default no-op implementations).

Key methods in the `Plugin` interface:
*   `getViewData(String name, Map<String, Object> data)`: Modify data for a specific job before it's prepared for the request.
*   `prepareRequest(Map<String, Job> jobs, Map<String, Job> originalJobs)`: Modify the entire batch of jobs before sending.
*   `shouldSendRequest(Map<String, Job> jobs)`: Decide if the request should be sent to the server. Returning `false` aborts the request and triggers fallback.
*   `willSendRequest(Map<String, Job> jobs)`: Called just before the HTTP request is made. Useful for logging.
*   `onError(HypernovaError error, List<Job> jobs)`: Handles errors, either top-level or for individual jobs.
*   `onSuccess(JobResult jobResult)`: Called for each successfully rendered job.
*   `afterResponse(Map<String, JobResult> jobResults)`: Process or modify the final map of job results.

**Example Custom Plugin:**
A simple plugin that logs information before a request is sent.

```java
package com.example.hypernova.plugins; // Example package for your custom plugins

import com.example.hypernova.BasePlugin;
import com.example.hypernova.Job;
import java.util.Map;
import java.util.List; // Required for other plugin methods if overridden

public class LoggingPlugin extends BasePlugin {
    @Override
    public void willSendRequest(Map<String, Job> jobs) {
        System.out.println("Preparing to send " + jobs.size() + " jobs to Hypernova:");
        for (Map.Entry<String, Job> entry : jobs.entrySet()) {
            System.out.println(" - Job ID: " + entry.getKey() + ", Component: " + entry.getValue().getName());
        }
    }
}
```

**Using Plugins:**
Plugins are passed to the `HypernovaRenderer` constructor as a list.

```java
import com.example.hypernova.HypernovaRenderer;
import com.example.hypernova.Plugin;
import com.example.hypernova.DevModePlugin;
// Assuming LoggingPlugin is in com.example.hypernova.plugins
// import com.example.hypernova.plugins.LoggingPlugin; 
import okhttp3.OkHttpClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;

// ... inside your application setup
List<Plugin> myPlugins = new ArrayList<>();
// myPlugins.add(new LoggingPlugin()); // Add your custom plugin
myPlugins.add(new DevModePlugin());   // Add built-in dev mode plugin

HypernovaRenderer renderer = new HypernovaRenderer(
    "http://localhost:3030/batch", // Your Hypernova server URL
    myPlugins,
    new OkHttpClient(), // Default or custom OkHttpClient
    new ObjectMapper()  // Default or custom ObjectMapper
);
// ...
```

## Configuration

The `HypernovaRenderer` can be configured by passing instances to its constructor:

*   **Hypernova Server URL**: The first parameter of the constructor (e.g., `http://localhost:3030/batch`).
*   **Plugins**: A `List<Plugin>` can be passed to the constructor. Plugins are executed in the order they appear in the list.
*   **OkHttpClient**: A custom `OkHttpClient` instance can be provided for advanced HTTP configuration (e.g., timeouts, interceptors, connection pooling).
*   **ObjectMapper**: A custom `ObjectMapper` (from Jackson) instance can be provided for specialized JSON serialization/deserialization needs (e.g., custom date formats, naming strategies).

Example with custom configuration:
```java
import com.example.hypernova.DevModePlugin;
import com.example.hypernova.HypernovaRenderer;
import com.example.hypernova.Plugin;
import okhttp3.OkHttpClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import java.util.concurrent.TimeUnit;
import java.util.List;
import java.util.ArrayList;
// ... other imports if you have more custom plugins

// Custom OkHttpClient
OkHttpClient customHttpClient = new OkHttpClient.Builder()
    .connectTimeout(10, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .writeTimeout(15, TimeUnit.SECONDS)
    // Add any other OkHttp configurations like interceptors, connection pool, etc.
    .build();

// Custom ObjectMapper
ObjectMapper customObjectMapper = new ObjectMapper();
// Example customization: use SNAKE_CASE for JSON properties if your components expect that
// customObjectMapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE); 

List<Plugin> appPlugins = new ArrayList<>();
// appPlugins.add(new MyCustomAnalyticsPlugin()); // Add your own plugins
appPlugins.add(new DevModePlugin());          // Include built-in plugins as needed

HypernovaRenderer renderer = new HypernovaRenderer(
    "https://my.hypernova.server/batch", // Production Hypernova server URL
    appPlugins,
    customHttpClient,
    customObjectMapper
);
```

## Development

*   **Build the project:**
    This project uses Maven. To build the project and install artifacts into your local Maven repository:
    ```bash
    mvn clean install
    ```
*   **Run tests:**
    To execute the unit tests:
    ```bash
    mvn test
    ```

## Contributing

Contributions are welcome! Please follow these general guidelines:
1.  Fork the repository.
2.  Create a new branch for your feature or bug fix (e.g., `feature/my-new-feature` or `fix/issue-123`).
3.  Make your changes, including appropriate tests and Javadoc documentation.
4.  Ensure all tests pass (`mvn test`).
5.  Submit a pull request against the `main` branch.

## License

This project is licensed under the MIT License. See the `LICENSE` file for details.
```
