package com.codereview.assistant.review.ai;

public record AiRiskItem(
        String severity,
        String file,
        String title,
        String detail,
        String evidence,
        String impact,
        String confidence,
        boolean needsHumanReview,
        String recommendation
) {
}
