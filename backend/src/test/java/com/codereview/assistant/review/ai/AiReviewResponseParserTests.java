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
                      "recommendation": "在入口增加空值校验。"
                    }
                  ],
                  "suggestions": ["补充 token 为空的测试。"],
                  "markdown": "## Review\\n- 注意空指针风险。"
                }
                """);

        assertThat(result.generated()).isTrue();
        assertThat(result.providerId()).isEqualTo("deepseek");
        assertThat(result.summary()).isEqualTo("修复登录流程。");
        assertThat(result.riskItems()).singleElement().satisfies(item -> {
            assertThat(item.severity()).isEqualTo("high");
            assertThat(item.file()).isEqualTo("src/AuthService.java");
        });
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
    }

    @Test
    void rejectsNonJsonResponse() {
        assertThatThrownBy(() -> parser.parse("deepseek", "deepseek-chat", "not json"))
                .isInstanceOf(AiReviewGenerationException.class)
                .hasMessageContaining("valid JSON");
    }
}
