package com.codereview.assistant.review.ai;

import com.codereview.assistant.ai.AiModelConfigInput;

public record AiReviewRequest(
        AiModelConfigInput modelConfig
) {
}
