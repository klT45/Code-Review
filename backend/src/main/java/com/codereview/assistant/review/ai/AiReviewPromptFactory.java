package com.codereview.assistant.review.ai;

import com.codereview.assistant.review.context.ReviewContext;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class AiReviewPromptFactory {

    public String build(ReviewContext context) {
        return """
                You are an experienced code reviewer. Review the GitHub pull request context below.

                Return strict JSON only, with this schema:
                {
                  "summary": "short Chinese summary of the PR changes",
                  "riskItems": [
                    {
                      "severity": "high|medium|low",
                      "file": "path/to/file",
                      "title": "short issue title in Chinese",
                      "detail": "why this may be risky",
                      "recommendation": "concrete review suggestion"
                    }
                  ],
                  "suggestions": ["actionable review suggestion in Chinese"],
                  "markdown": "copy-ready Markdown review in Chinese"
                }

                Review rules:
                - Focus on changed code and likely production risks.
                - Do not invent files or code that are not present in the context.
                - If context is truncated or a patch is missing, mention that limitation in markdown.
                - Keep the output concise and practical for a pull request comment.

                Context limitations:
                %s

                Pull request context:
                %s
                """.formatted(formatNotes(context.truncationNotes()), context.promptText());
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
