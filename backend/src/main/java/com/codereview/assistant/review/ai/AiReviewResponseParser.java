package com.codereview.assistant.review.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class AiReviewResponseParser {

    private final ObjectMapper objectMapper;

    public AiReviewResponseParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public AiReviewResult parse(String providerId, String modelId, String responseText) {
        try {
            AiReviewPayload payload = objectMapper.readValue(extractJson(responseText), AiReviewPayload.class);
            return AiReviewResult.generated(
                    providerId,
                    modelId,
                    safe(payload.summary()),
                    safeList(payload.riskItems()),
                    safeList(payload.suggestions()),
                    safe(payload.markdown())
            );
        } catch (JsonProcessingException exception) {
            throw new AiReviewGenerationException("AI Review response was not valid JSON.", exception);
        }
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

    private record AiReviewPayload(
            String summary,
            List<AiRiskItem> riskItems,
            List<String> suggestions,
            String markdown
    ) {
    }
}
