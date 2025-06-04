package com.example.hypernovaclient.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;

/**
 * Configuration properties for the Hypernova client.
 * Allows customization of the Hypernova server URL and connection timeouts
 * through application properties (e.g., in {@code application.properties} or {@code application.yml}).
 * <p>
 * Properties are prefixed with {@code hypernova.client}.
 * Example:
 * <pre>
 * hypernova.client.url=http://localhost:8080/batch
 * hypernova.client.connect-timeout=5000
 * hypernova.client.read-timeout=5000
 * </pre>
 * </p>
 */
@ConfigurationProperties(prefix = "hypernova.client")
public class HypernovaClientProperties {

    /**
     * The URL of the Hypernova server's batch endpoint.
     * This property is mandatory.
     */
    @NotBlank(message = "Hypernova client URL must be configured.")
    private String url;

    /**
     * Connection timeout in milliseconds for requests to the Hypernova server.
     * A value of 0 means infinite timeout. Default is 5000ms (5 seconds).
     */
    @Min(value = 0, message = "Connect timeout must be non-negative.")
    private int connectTimeout = 5000; // Default 5 seconds

    /**
     * Read timeout in milliseconds for requests to the Hypernova server.
     * A value of 0 means infinite timeout. Default is 5000ms (5 seconds).
     */
    @Min(value = 0, message = "Read timeout must be non-negative.")
    private int readTimeout = 5000;    // Default 5 seconds

    /**
     * Gets the configured Hypernova server URL.
     * @return The server URL string.
     */
    public String getUrl() {
        return url;
    }

    /**
     * Sets the Hypernova server URL.
     * @param url The server URL string.
     */
    public void setUrl(String url) {
        this.url = url;
    }

    /**
     * Gets the connection timeout in milliseconds.
     * @return The connection timeout.
     */
    public int getConnectTimeout() {
        return connectTimeout;
    }

    /**
     * Sets the connection timeout in milliseconds.
     * @param connectTimeout The connection timeout.
     */
    public void setConnectTimeout(int connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    /**
     * Gets the read timeout in milliseconds.
     * @return The read timeout.
     */
    public int getReadTimeout() {
        return readTimeout;
    }

    /**
     * Sets the read timeout in milliseconds.
     * @param readTimeout The read timeout.
     */
    public void setReadTimeout(int readTimeout) {
        this.readTimeout = readTimeout;
    }
}
