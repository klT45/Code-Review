package com.codereview.assistant.review.ai;

public record AiRiskItem(
        String severity,
        String file,
        String title,
        String detail,
        String recommendation
) {
}
