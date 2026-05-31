package com.codereview.assistant.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        String name,
        GitHub github,
        Model model,
        Cors cors
) {

    public AppProperties {
        if (name == null || name.isBlank()) {
            name = "AI PR Review Assistant";
        }
        if (github == null) {
            github = new GitHub(false);
        }
        if (model == null) {
            model = new Model("deepseek", defaultProviders());
        }
        if (cors == null) {
            cors = new Cors(List.of("http://localhost:5173", "http://127.0.0.1:5173"));
        }
    }

    public record GitHub(boolean privateRepositorySupportEnabled) {
    }

    public record Cors(List<String> allowedOrigins) {

        public Cors {
            if (allowedOrigins == null || allowedOrigins.isEmpty()) {
                allowedOrigins = List.of("http://localhost:5173", "http://127.0.0.1:5173");
            }
        }
    }

    public record Model(String defaultProviderId, List<Provider> providers) {

        public Model {
            if (defaultProviderId == null || defaultProviderId.isBlank()) {
                defaultProviderId = "deepseek";
            }
            if (providers == null || providers.isEmpty()) {
                providers = new ArrayList<>();
                providers.addAll(defaultProviders());
            }
        }
    }

    public record Provider(
            String id,
            String displayName,
            String baseUrl,
            String modelId,
            String apiKeyEnv
    ) {
    }

    private static Provider defaultDeepSeekProvider() {
        return new Provider(
                "deepseek",
                "DeepSeek",
                "https://api.deepseek.com",
                "deepseek-chat",
                "DEEPSEEK_API_KEY"
        );
    }

    private static List<Provider> defaultProviders() {
        return List.of(
                defaultDeepSeekProvider(),
                new Provider(
                        "custom",
                        "Custom compatible model",
                        "https://api.deepseek.com",
                        "deepseek-chat",
                        "AI_MODEL_API_KEY"
                )
        );
    }
}
