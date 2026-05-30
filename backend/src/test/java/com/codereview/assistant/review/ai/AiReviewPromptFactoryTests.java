package com.codereview.assistant.review.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.codereview.assistant.review.context.ReviewContext;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.prompt.Prompt;

class AiReviewPromptFactoryTests {

    private final AiReviewPromptFactory promptFactory = new AiReviewPromptFactory();

    @Test
    void buildsPromptWithContextAndStructuredFormatInstructions() {
        ReviewContext context = new ReviewContext(
                null,
                null,
                List.of(),
                List.of("src/App.tsx patch was truncated."),
                "PR REVIEW CONTEXT\nFILE: src/App.tsx"
        );

        Prompt prompt = promptFactory.build(context, "Return JSON that matches AiReviewPayload.");

        assertThat(prompt.getContents())
                .contains("src/App.tsx patch was truncated.")
                .contains("PR REVIEW CONTEXT")
                .contains("Return JSON that matches AiReviewPayload.")
                .contains("Write all human-facing fields in Chinese");
    }
}
