package com.codereview.assistant.review.ai;

import java.util.List;

public record AiReviewPayload(
        String summary,
        List<AiRiskItem> riskItems,
        List<String> requiredActions,
        List<String> suggestions,
        List<String> followUpItems,
        List<String> limitations,
        String markdown
) {
}
