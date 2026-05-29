package com.codereview.assistant.review.ai;

import java.util.List;

public record AiReviewResult(
        boolean enabled,
        boolean generated,
        String providerId,
        String modelId,
        String summary,
        List<AiRiskItem> riskItems,
        List<String> suggestions,
        String markdown,
        String message
) {

    public static AiReviewResult notConfigured(String providerId, String modelId, String message) {
        return new AiReviewResult(
                false,
                false,
                providerId,
                modelId,
                "",
                List.of(),
                List.of(),
                "",
                message
        );
    }

    public static AiReviewResult generated(
            String providerId,
            String modelId,
            String summary,
            List<AiRiskItem> riskItems,
            List<String> suggestions,
            String markdown
    ) {
        return new AiReviewResult(
                true,
                true,
                providerId,
                modelId,
                summary,
                riskItems,
                suggestions,
                markdown,
                "AI Review generated."
        );
    }
}
