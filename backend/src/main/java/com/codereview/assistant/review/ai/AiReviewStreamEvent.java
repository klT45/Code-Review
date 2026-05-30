package com.codereview.assistant.review.ai;

public record AiReviewStreamEvent(
        String type,
        String text,
        AiReviewResult review,
        String message
) {

    public static AiReviewStreamEvent chunk(String text) {
        return new AiReviewStreamEvent("chunk", text, null, null);
    }

    public static AiReviewStreamEvent result(AiReviewResult review) {
        return new AiReviewStreamEvent("result", null, review, null);
    }

    public static AiReviewStreamEvent error(String message) {
        return new AiReviewStreamEvent("error", null, null, message);
    }

    public static AiReviewStreamEvent done() {
        return new AiReviewStreamEvent("done", null, null, null);
    }
}
