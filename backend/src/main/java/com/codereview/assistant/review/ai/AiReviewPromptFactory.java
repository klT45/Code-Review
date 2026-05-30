package com.codereview.assistant.review.ai;

import com.codereview.assistant.review.context.ReviewContext;
import java.util.Map;
import java.util.List;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.stereotype.Component;

@Component
public class AiReviewPromptFactory {

    private static final String TEMPLATE = """
                You are an experienced code reviewer. Review the GitHub pull request context below.

                Review rules:
                - Focus on changed code and likely production risks.
                - Every risk item must cite evidence from the provided context. If evidence is weak, set confidence to low and needsHumanReview to true.
                - Separate blocking fixes, recommended improvements, and non-blocking follow-ups.
                - Do not invent files or code that are not present in the context.
                - If no clear risk is found, return an empty riskItems array and say so in summary and markdown.
                - If context is truncated or a patch is missing, mention that limitation in markdown.
                - Treat generated files, lock files, docs-only changes, and pure styling changes as lower risk unless the patch shows a concrete problem.
                - Keep the output concise and practical for a pull request comment.
                - Write all human-facing fields in Chinese.

                Context limitations:
                {limitations}

                Output format:
                {format}

                Pull request context:
                {context}
                """;

    public Prompt build(ReviewContext context, String formatInstructions) {
        return new PromptTemplate(TEMPLATE).create(Map.of(
                "limitations", formatNotes(context.truncationNotes()),
                "format", formatInstructions,
                "context", context.promptText()
        ));
    }

    private static String formatNotes(List<String> notes) {
        if (notes == null || notes.isEmpty()) {
            return "- No truncation notes.";
        }
        StringBuilder builder = new StringBuilder();
        notes.forEach(note -> builder.append("- ").append(note).append("\n"));
        return builder.toString();
    }
}
