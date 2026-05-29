package com.codereview.assistant.ai;

public record ResolvedAiModelConfig(
        String providerId,
        String displayName,
        String baseUrl,
        String modelId,
        String apiKey,
        boolean apiKeyConfigured,
        String readinessMessage
) {

    public boolean ready() {
        return apiKeyConfigured;
    }
}
