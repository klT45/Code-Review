package com.codereview.assistant.review.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class AiReviewResponseParserTests {

    private final AiReviewResponseParser parser = new AiReviewResponseParser(new ObjectMapper());

    @Test
    void parsesStrictJsonReviewResponse() {
        AiReviewResult result = parser.parse("deepseek", "deepseek-chat", """
                {
                  "summary": "修复登录流程。",
                  "riskItems": [
                    {
                      "severity": "high",
                      "file": "src/AuthService.java",
                      "title": "空指针风险",
                      "detail": "token 为空时会继续访问字段。",
                      "evidence": "新增代码直接读取 token.value。",
                      "impact": "登录请求可能返回 500。",
                      "confidence": "high",
                      "needsHumanReview": false,
                      "recommendation": "在入口增加空值校验。"
                    }
                  ],
                  "requiredActions": ["合并前补充空值保护。"],
                  "suggestions": ["补充 token 为空的测试。"],
                  "followUpItems": ["后续统一登录错误码。"],
                  "limitations": ["仅基于 PR patch 判断。"],
                  "markdown": "## Review\\n- 注意空指针风险。"
                }
                """);

        assertThat(result.generated()).isTrue();
        assertThat(result.providerId()).isEqualTo("deepseek");
        assertThat(result.summary()).isEqualTo("修复登录流程。");
        assertThat(result.riskItems()).singleElement().satisfies(item -> {
            assertThat(item.severity()).isEqualTo("high");
            assertThat(item.file()).isEqualTo("src/AuthService.java");
            assertThat(item.evidence()).contains("token.value");
            assertThat(item.impact()).contains("500");
            assertThat(item.confidence()).isEqualTo("high");
            assertThat(item.needsHumanReview()).isFalse();
        });
        assertThat(result.requiredActions()).containsExactly("合并前补充空值保护。");
        assertThat(result.suggestions()).containsExactly("补充 token 为空的测试。");
        assertThat(result.followUpItems()).containsExactly("后续统一登录错误码。");
        assertThat(result.limitations()).containsExactly("仅基于 PR patch 判断。");
        assertThat(result.markdown()).contains("Review");
    }

    @Test
    void parsesJsonWrappedInMarkdownFence() {
        AiReviewResult result = parser.parse("deepseek", "deepseek-chat", """
                ```json
                {
                  "summary": "更新文档。",
                  "riskItems": [],
                  "suggestions": [],
                  "markdown": "无明显风险。"
                }
                ```
                """);

        assertThat(result.summary()).isEqualTo("更新文档。");
        assertThat(result.riskItems()).isEmpty();
        assertThat(result.requiredActions()).isEmpty();
        assertThat(result.followUpItems()).isEmpty();
        assertThat(result.limitations()).isEmpty();
    }

    @Test
    void rejectsNonJsonResponse() {
        assertThatThrownBy(() -> parser.parse("deepseek", "deepseek-chat", "not json"))
                .isInstanceOf(AiReviewGenerationException.class)
                .hasMessageContaining("valid JSON");
    }
}
