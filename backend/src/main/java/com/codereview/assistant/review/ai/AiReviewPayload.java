package com.codereview.assistant.review.ai;

import java.util.List;

public record AiReviewPayload(
        String summary,
        List<AiRiskItem> riskItems,
        List<FileExplanationPayload> fileExplanations,
        List<String> requiredActions,
        List<String> suggestions,
        List<String> followUpItems,
        List<String> limitations,
        String markdown
) {

    public record FileExplanationPayload(
            String filename,
            String explanation
    ) {
    }
}
