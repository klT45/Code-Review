package com.codereview.assistant.review.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Component;

@Component
public class AiReviewResponseParser {

    private final BeanOutputConverter<AiReviewPayload> outputConverter;

    public AiReviewResponseParser(ObjectMapper objectMapper) {
        this.outputConverter = new BeanOutputConverter<>(AiReviewPayload.class, objectMapper);
    }

    public String formatInstructions() {
        return outputConverter.getFormat();
    }

    public String jsonSchema() {
        return outputConverter.getJsonSchema();
    }

    public AiReviewResult parse(String providerId, String modelId, String responseText) {
        try {
            return toResult(providerId, modelId, outputConverter.convert(extractJson(responseText)));
        } catch (AiReviewGenerationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new AiReviewGenerationException("AI Review response was not valid JSON.", exception);
        }
    }

    AiReviewResult toResult(String providerId, String modelId, AiReviewPayload payload) {
        if (payload == null) {
            throw new AiReviewGenerationException("AI Review response was empty.", null);
        }
        return AiReviewResult.generated(
                providerId,
                modelId,
                safe(payload.summary()),
                safeRiskItems(payload.riskItems()),
                safeFileExplanations(payload.fileExplanations()),
                safeList(payload.requiredActions()),
                safeList(payload.suggestions()),
                safeList(payload.followUpItems()),
                safeList(payload.limitations()),
                safe(payload.markdown())
        );
    }

    private static String extractJson(String responseText) {
        if (responseText == null || responseText.isBlank()) {
            throw new AiReviewGenerationException("AI Review response was empty.", null);
        }

        String trimmed = responseText.strip();
        if (trimmed.startsWith("```")) {
            int firstNewLine = trimmed.indexOf('\n');
            int lastFence = trimmed.lastIndexOf("```");
            if (firstNewLine >= 0 && lastFence > firstNewLine) {
                return trimmed.substring(firstNewLine + 1, lastFence).strip();
            }
        }
        return trimmed;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    private static List<AiRiskItem> safeRiskItems(List<AiRiskItem> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .map(item -> new AiRiskItem(
                        safe(item.severity()),
                        safe(item.file()),
                        safe(item.title()),
                        safe(item.detail()),
                        safe(item.evidence()),
                        safe(item.impact()),
                        safe(item.confidence()),
                        item.needsHumanReview(),
                        safe(item.recommendation())
                ))
                .toList();
    }

    private static List<AiReviewResult.FileExplanation> safeFileExplanations(
            List<AiReviewPayload.FileExplanationPayload> values
    ) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .map(item -> new AiReviewResult.FileExplanation(
                        safe(item.filename()),
                        safe(item.explanation())
                ))
                .toList();
    }
}
