package com.codereview.assistant.ai;

import com.codereview.assistant.config.AppProperties;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

@Service
public class ModelConfigurationService {

    private final AppProperties properties;
    private final Environment environment;

    public ModelConfigurationService(AppProperties properties, Environment environment) {
        this.properties = properties;
        this.environment = environment;
    }

    public AiModelProviderOptions options() {
        return new AiModelProviderOptions(
                properties.model().defaultProviderId(),
                properties.model().providers().stream()
                        .map(this::toProvider)
                        .toList()
        );
    }

    public ResolvedAiModelConfig resolve(AiModelConfigInput input) {
        AppProperties.Provider provider = findProvider(providerId(input));
        String baseUrl = firstNonBlank(valueOrNull(input, AiModelConfigInput::baseUrl), provider.baseUrl());
        String modelId = firstNonBlank(valueOrNull(input, AiModelConfigInput::modelId), provider.modelId());
        String apiKey = firstNonBlank(valueOrNull(input, AiModelConfigInput::apiKey), readApiKey(provider.apiKeyEnv()));

        validateBaseUrl(baseUrl);
        if (modelId.isBlank()) {
            throw new InvalidModelConfigurationException("Model ID is required.");
        }

        boolean apiKeyConfigured = apiKey != null && !apiKey.isBlank();
        String readinessMessage = apiKeyConfigured
                ? "Model configuration is ready."
                : "API Key is required. Provide one in the request or set %s.".formatted(provider.apiKeyEnv());

        return new ResolvedAiModelConfig(
                provider.id(),
                provider.displayName(),
                normalizeUrl(baseUrl),
                modelId,
                apiKey,
                apiKeyConfigured,
                readinessMessage
        );
    }

    private AiModelProvider toProvider(AppProperties.Provider provider) {
        return new AiModelProvider(
                provider.id(),
                provider.displayName(),
                normalizeUrl(provider.baseUrl()),
                provider.modelId(),
                provider.apiKeyEnv(),
                readApiKey(provider.apiKeyEnv()) != null
        );
    }

    private AppProperties.Provider findProvider(String providerId) {
        return properties.model().providers().stream()
                .filter(provider -> provider.id().equals(providerId))
                .findFirst()
                .orElseThrow(() -> new InvalidModelConfigurationException(
                        "Unknown model provider: %s.".formatted(providerId)
                ));
    }

    private String providerId(AiModelConfigInput input) {
        return firstNonBlank(valueOrNull(input, AiModelConfigInput::providerId), properties.model().defaultProviderId());
    }

    private String readApiKey(String envName) {
        if (envName == null || envName.isBlank()) {
            return null;
        }
        String value = environment.getProperty(envName);
        return value == null || value.isBlank() ? null : value;
    }

    private static String normalizeUrl(String rawUrl) {
        URI uri = validateBaseUrl(rawUrl);
        return uri.toString();
    }

    private static URI validateBaseUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new InvalidModelConfigurationException("Base URL is required.");
        }
        try {
            URI uri = new URI(rawUrl.strip());
            if (!uri.isAbsolute() || uri.getScheme() == null || uri.getHost() == null) {
                throw new InvalidModelConfigurationException("Base URL must be an absolute HTTP URL.");
            }
            if (!List.of("http", "https").contains(uri.getScheme().toLowerCase())) {
                throw new InvalidModelConfigurationException("Base URL must use HTTP or HTTPS.");
            }
            return uri;
        } catch (URISyntaxException exception) {
            throw new InvalidModelConfigurationException("Base URL is invalid.");
        }
    }

    private static String firstNonBlank(String preferred, String fallback) {
        return preferred == null || preferred.isBlank() ? fallback : preferred.strip();
    }

    private static <T> String valueOrNull(T value, ValueReader<T> reader) {
        return value == null ? null : reader.read(value);
    }

    @FunctionalInterface
    private interface ValueReader<T> {
        String read(T value);
    }
}
