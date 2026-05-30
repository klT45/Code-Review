package com.codereview.assistant.review.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.codereview.assistant.review.context.ReviewContext;
import com.codereview.assistant.review.context.ReviewContextStats;
import com.codereview.assistant.review.context.ReviewFileContext;
import java.util.List;
import org.junit.jupiter.api.Test;

class AiReviewQualityGateTests {

    private final AiReviewQualityGate qualityGate = new AiReviewQualityGate();

    @Test
    void marksWeakRiskItemsForHumanReview() {
        AiReviewResult review = AiReviewResult.generated(
                "deepseek",
                "deepseek-chat",
                "",
                List.of(new AiRiskItem(
                        "critical",
                        "src/Unknown.java",
                        "可能有风险",
                        "描述",
                        "",
                        "影响",
                        "certain",
                        false,
                        "建议"
                )),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                "## AI Review"
        );

        AiReviewResult result = qualityGate.refine(review, context());

        assertThat(result.summary()).contains("发现 1 个");
        assertThat(result.riskItems()).singleElement().satisfies(item -> {
            assertThat(item.severity()).isEqualTo("medium");
            assertThat(item.confidence()).isEqualTo("low");
            assertThat(item.needsHumanReview()).isTrue();
            assertThat(item.evidence()).contains("未提供可核验");
        });
    }

    @Test
    void appendsContextLimitationsToMarkdown() {
        AiReviewResult review = AiReviewResult.generated(
                "deepseek",
                "deepseek-chat",
                "摘要",
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of("仅基于 patch 判断。"),
                "## AI Review\n无明显风险。"
        );

        AiReviewResult result = qualityGate.refine(review, context());

        assertThat(result.limitations())
                .contains("仅基于 patch 判断。")
                .anySatisfy(item -> assertThat(item).contains("patch 被截断"))
                .anySatisfy(item -> assertThat(item).contains("没有 patch 内容"));
        assertThat(result.markdown())
                .contains("### 判断限制")
                .contains("仅基于 patch 判断。");
    }

    private static ReviewContext context() {
        return new ReviewContext(
                null,
                new ReviewContextStats(
                        2,
                        1,
                        1,
                        8_000,
                        4_000,
                        20_000,
                        4_000,
                        20_000
                ),
                List.of(new ReviewFileContext(
                        "src/App.java",
                        "modified",
                        10,
                        2,
                        12,
                        true,
                        true,
                        8_000,
                        "@@ -1 +1 @@"
                )),
                List.of("Patch for src/App.java was truncated from 8000 to 4000 characters."),
                "PR REVIEW CONTEXT"
        );
    }
}
