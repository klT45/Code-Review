package com.codereview.assistant.ai;

public record AiModelConfigInput(
        String providerId,
        String baseUrl,
        String modelId,
        String apiKey
) {
}
