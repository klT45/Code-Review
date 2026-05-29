package com.codereview.assistant.review.ai;

import com.codereview.assistant.ai.AiModelConfigInput;
import com.codereview.assistant.ai.ModelConfigurationService;
import com.codereview.assistant.ai.ResolvedAiModelConfig;
import com.codereview.assistant.review.context.ReviewContext;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.stereotype.Service;

@Service
public class AiReviewService {

    private final ModelConfigurationService modelConfigurationService;
    private final AiReviewPromptFactory promptFactory;
    private final AiReviewResponseParser responseParser;

    public AiReviewService(
            ModelConfigurationService modelConfigurationService,
            AiReviewPromptFactory promptFactory,
            AiReviewResponseParser responseParser
    ) {
        this.modelConfigurationService = modelConfigurationService;
        this.promptFactory = promptFactory;
        this.responseParser = responseParser;
    }

    public AiReviewResult review(ReviewContext context, AiModelConfigInput input) {
        ResolvedAiModelConfig config = modelConfigurationService.resolve(input);
        if (!config.ready()) {
            return AiReviewResult.notConfigured(config.providerId(), config.modelId(), config.readinessMessage());
        }

        try {
            OpenAiChatModel chatModel = OpenAiChatModel.builder()
                    .openAiApi(OpenAiApi.builder()
                            .baseUrl(config.baseUrl())
                            .apiKey(config.apiKey())
                            .build())
                    .defaultOptions(OpenAiChatOptions.builder()
                            .model(config.modelId())
                            .temperature(0.2)
                            .build())
                    .build();

            String responseText = chatModel.call(promptFactory.build(context));
            return responseParser.parse(config.providerId(), config.modelId(), responseText);
        } catch (AiReviewGenerationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new AiReviewGenerationException("AI Review generation failed.", exception);
        }
    }
}
