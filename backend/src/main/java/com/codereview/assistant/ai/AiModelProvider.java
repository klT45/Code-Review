package com.codereview.assistant.ai;

public record AiModelProvider(
        String id,
        String displayName,
        String baseUrl,
        String modelId,
        String apiKeyEnv,
        boolean apiKeyAvailable
) {
}
