package com.codereview.assistant.ai;

import java.util.List;

public record AiModelProviderOptions(
        String defaultProviderId,
        List<AiModelProvider> providers
) {
}
