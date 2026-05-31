package com.codereview.assistant.review.ai;

import com.codereview.assistant.ai.AiModelConfigInput;
import com.codereview.assistant.ai.ModelConfigurationService;
import com.codereview.assistant.ai.ResolvedAiModelConfig;
import com.codereview.assistant.review.context.ReviewContext;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.openai.api.ResponseFormat;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
public class AiReviewService {

    private final ModelConfigurationService modelConfigurationService;
    private final AiReviewPromptFactory promptFactory;
    private final AiReviewResponseParser responseParser;
    private final AiReviewQualityGate qualityGate;

    public AiReviewService(
            ModelConfigurationService modelConfigurationService,
            AiReviewPromptFactory promptFactory,
            AiReviewResponseParser responseParser,
            AiReviewQualityGate qualityGate
    ) {
        this.modelConfigurationService = modelConfigurationService;
        this.promptFactory = promptFactory;
        this.responseParser = responseParser;
        this.qualityGate = qualityGate;
    }

    public AiReviewResult review(ReviewContext context, AiModelConfigInput input) {
        ResolvedAiModelConfig config = modelConfigurationService.resolve(input);
        if (!config.ready()) {
            return AiReviewResult.notConfigured(config.providerId(), config.modelId(), config.readinessMessage());
        }

        try {
            OpenAiChatModel chatModel = buildChatModel(config);

            Prompt prompt = promptFactory.build(context, responseParser.formatInstructions());
            ChatResponse response = chatModel.call(prompt);
            String responseText = response.getResult().getOutput().getText();
            return parseAndRefine(config, context, responseText);
        } catch (AiReviewGenerationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new AiReviewGenerationException("AI Review generation failed.", exception);
        }
    }

    public Flux<AiReviewStreamEvent> reviewStream(ReviewContext context, AiModelConfigInput input) {
        ResolvedAiModelConfig config = modelConfigurationService.resolve(input);
        if (!config.ready()) {
            return Flux.just(
                    AiReviewStreamEvent.result(AiReviewResult.notConfigured(
                            config.providerId(),
                            config.modelId(),
                            config.readinessMessage()
                    )),
                    AiReviewStreamEvent.done()
            );
        }

        try {
            OpenAiChatModel chatModel = buildChatModel(config);
            Prompt prompt = promptFactory.build(context, responseParser.formatInstructions());
            StringBuilder responseBuffer = new StringBuilder();

            Flux<AiReviewStreamEvent> chunks = chatModel.stream(prompt)
                    .map(this::extractText)
                    .filter(text -> !text.isEmpty())
                    .doOnNext(responseBuffer::append)
                    .map(AiReviewStreamEvent::chunk);

            Flux<AiReviewStreamEvent> result = Flux.defer(() -> Flux.just(
                    AiReviewStreamEvent.result(parseAndRefine(config, context, responseBuffer.toString())),
                    AiReviewStreamEvent.done()
            ));

            return chunks.concatWith(result)
                    .onErrorResume(exception -> Flux.just(AiReviewStreamEvent.error(
                            exception instanceof AiReviewGenerationException
                                    ? exception.getMessage()
                                    : "AI Review generation failed."
                    )));
        } catch (RuntimeException exception) {
            return Flux.just(AiReviewStreamEvent.error("AI Review generation failed."));
        }
    }

    private OpenAiChatModel buildChatModel(ResolvedAiModelConfig config) {
        return OpenAiChatModel.builder()
                .openAiApi(OpenAiApi.builder()
                        .baseUrl(config.baseUrl())
                        .apiKey(config.apiKey())
                        .build())
                .defaultOptions(OpenAiChatOptions.builder()
                        .model(config.modelId())
                        .temperature(0.2)
                        .responseFormat(ResponseFormat.builder()
                                .type(ResponseFormat.Type.JSON_OBJECT)
                                .build())
                        .build())
                .build();
    }

    private AiReviewResult parseAndRefine(
            ResolvedAiModelConfig config,
            ReviewContext context,
            String responseText
    ) {
        return qualityGate.refine(
                responseParser.parse(config.providerId(), config.modelId(), responseText),
                context
        );
    }

    private String extractText(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return "";
        }
        String text = response.getResult().getOutput().getText();
        return text == null ? "" : text;
    }
}
