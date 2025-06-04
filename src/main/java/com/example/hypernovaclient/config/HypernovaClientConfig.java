package com.example.hypernovaclient.config;

import com.example.hypernovaclient.HypernovaRenderer;
import com.example.hypernovaclient.plugin.HypernovaPlugin;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Spring Boot configuration class for the Hypernova client.
 * This class is responsible for setting up the necessary beans, such as
 * {@link RestTemplate}, {@link ObjectMapper}, and {@link HypernovaRenderer},
 * and enabling the {@link HypernovaClientProperties}.
 */
@Configuration
@EnableConfigurationProperties(HypernovaClientProperties.class)
public class HypernovaClientConfig {

    private final HypernovaClientProperties properties;

    /**
     * Constructs the configuration with injected Hypernova client properties.
     * @param properties The {@link HypernovaClientProperties} containing client settings.
     */
    public HypernovaClientConfig(HypernovaClientProperties properties) {
        this.properties = properties;
    }

    /**
     * Creates a {@link RestTemplate} bean specifically configured for communication
     * with the Hypernova server. It uses timeouts defined in {@link HypernovaClientProperties}.
     *
     * @param builder A {@link RestTemplateBuilder} provided by Spring Boot for easy configuration.
     * @return A configured {@link RestTemplate} instance.
     */
    @Bean
    public RestTemplate hypernovaRestTemplate(RestTemplateBuilder builder) {
        return builder
            .setConnectTimeout(Duration.ofMillis(properties.getConnectTimeout()))
            .setReadTimeout(Duration.ofMillis(properties.getReadTimeout()))
            .build();
    }

    /**
     * Creates an {@link ObjectMapper} bean for JSON serialization and deserialization.
     * <p>
     * Spring Boot auto-configures a default {@code ObjectMapper}. This bean definition
     * provides a dedicated mapper for Hypernova, but in many applications, customizing
     * the default Spring Boot mapper or reusing it might be preferred.
     * </p>
     * @return A new {@link ObjectMapper} instance.
     */
    @Bean
    public ObjectMapper hypernovaObjectMapper() {
        // Spring Boot auto-configures an ObjectMapper.
        // We can inject and use that, or define a specific one here if customization is needed.
        // For now, returning a new one, but in a real app, you might customize Spring's default.
        return new ObjectMapper();
    }

    /**
     * Creates the main {@link HypernovaRenderer} bean.
     * This renderer is responsible for managing rendering jobs, interacting with plugins,
     * and communicating with the Hypernova server.
     *
     * @param hypernovaRestTemplate The configured {@link RestTemplate} for Hypernova communication.
     * @param hypernovaObjectMapper The {@link ObjectMapper} for JSON processing.
     * @param plugins An {@link Optional} list of {@link HypernovaPlugin} beans found in the
     *                application context. If no plugins are defined, an empty list is used.
     * @return A configured {@link HypernovaRenderer} instance.
     */
    @Bean
    public HypernovaRenderer hypernovaRenderer(
        RestTemplate hypernovaRestTemplate,
        ObjectMapper hypernovaObjectMapper,
        Optional<List<HypernovaPlugin>> plugins // Use Optional to allow no plugins to be defined
    ) {
        return new HypernovaRenderer(
            properties.getUrl(),
            plugins.orElse(List.of()), // Default to an empty list if no plugins are autowired
            hypernovaRestTemplate,
            hypernovaObjectMapper
        );
    }
}
