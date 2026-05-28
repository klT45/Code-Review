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
            model = new Model("deepseek", List.of(defaultDeepSeekProvider()));
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
                providers.add(defaultDeepSeekProvider());
            }
        }
    }

    public record Provider(
            String id,
            String displayName,
            String baseUrl,
            String modelId
    ) {
    }

    private static Provider defaultDeepSeekProvider() {
        return new Provider(
                "deepseek",
                "DeepSeek",
                "https://api.deepseek.com",
                "deepseek-chat"
        );
    }
}
