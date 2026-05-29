package com.codereview.assistant.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        String name,
        GitHub github,
        Model model
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
    }

    public record GitHub(boolean privateRepositorySupportEnabled) {
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
