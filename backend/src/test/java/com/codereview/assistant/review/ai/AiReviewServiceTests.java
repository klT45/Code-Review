package com.codereview.assistant.review.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.codereview.assistant.ai.AiModelConfigInput;
import com.codereview.assistant.ai.ModelConfigurationService;
import com.codereview.assistant.ai.ResolvedAiModelConfig;
import com.codereview.assistant.review.context.ReviewContext;
import java.util.List;
import org.junit.jupiter.api.Test;

class AiReviewServiceTests {

    @Test
    void returnsNotConfiguredResultWhenApiKeyIsMissing() {
        AiReviewService service = new AiReviewService(
                new StubModelConfigurationService(new ResolvedAiModelConfig(
                        "deepseek",
                        "DeepSeek",
                        "https://api.deepseek.com",
                        "deepseek-chat",
                        null,
                        false,
                        "API Key is required."
                )),
                new AiReviewPromptFactory(),
                new AiReviewResponseParser(new com.fasterxml.jackson.databind.ObjectMapper())
        );

        AiReviewResult result = service.review(emptyContext(), new AiModelConfigInput(null, null, null, null));

        assertThat(result.generated()).isFalse();
        assertThat(result.enabled()).isFalse();
        assertThat(result.message()).contains("API Key");
    }

    private static ReviewContext emptyContext() {
        return new ReviewContext(
                null,
                null,
                List.of(),
                List.of(),
                "PR REVIEW CONTEXT"
        );
    }

    private static class StubModelConfigurationService extends ModelConfigurationService {

        private final ResolvedAiModelConfig config;

        StubModelConfigurationService(ResolvedAiModelConfig config) {
            super(null, null);
            this.config = config;
        }

        @Override
        public ResolvedAiModelConfig resolve(AiModelConfigInput input) {
            return config;
        }
    }
}
