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
                      "evidence": "specific changed code or context that supports this finding",
                      "impact": "possible production, security, data, performance, or maintainability impact",
                      "confidence": "high|medium|low",
                      "needsHumanReview": true,
                      "recommendation": "concrete review suggestion"
                    }
                  ],
                  "requiredActions": ["must-fix item before merge in Chinese"],
                  "suggestions": ["recommended improvement in Chinese"],
                  "followUpItems": ["non-blocking follow-up item in Chinese"],
                  "limitations": ["context limitation that affects review confidence in Chinese"],
                  "markdown": "copy-ready Markdown review in Chinese"
                }

                Review rules:
                - Focus on changed code and likely production risks.
                - Every risk item must cite evidence from the provided context. If evidence is weak, set confidence to low and needsHumanReview to true.
                - Separate blocking fixes, recommended improvements, and non-blocking follow-ups.
                - Do not invent files or code that are not present in the context.
                - If no clear risk is found, return an empty riskItems array and say so in summary and markdown.
                - If context is truncated or a patch is missing, mention that limitation in markdown.
                - Treat generated files, lock files, docs-only changes, and pure styling changes as lower risk unless the patch shows a concrete problem.
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
