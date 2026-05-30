package com.codereview.assistant.review.ai;

import com.codereview.assistant.review.context.ReviewContext;
import com.codereview.assistant.review.context.ReviewContextStats;
import com.codereview.assistant.review.context.ReviewFileContext;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class AiReviewQualityGate {

    private static final Set<String> SEVERITIES = Set.of("high", "medium", "low");
    private static final Set<String> CONFIDENCES = Set.of("high", "medium", "low");

    public AiReviewResult refine(AiReviewResult review, ReviewContext context) {
        if (!review.generated()) {
            return review;
        }

        List<AiRiskItem> riskItems = review.riskItems().stream()
                .map(item -> refineRiskItem(item, context))
                .toList();
        List<String> limitations = mergeLimitations(review.limitations(), context);
        String markdown = enrichMarkdown(review.markdown(), limitations);

        return AiReviewResult.generated(
                review.providerId(),
                review.modelId(),
                fallbackSummary(review.summary(), riskItems),
                riskItems,
                review.requiredActions(),
                review.suggestions(),
                review.followUpItems(),
                limitations,
                markdown
        );
    }

    private AiRiskItem refineRiskItem(AiRiskItem item, ReviewContext context) {
        String file = safe(item.file());
        boolean knownFile = context.files().stream()
                .map(ReviewFileContext::filename)
                .anyMatch(file::equals);
        boolean hasEvidence = !safe(item.evidence()).isBlank();
        boolean needsHumanReview = item.needsHumanReview() || !knownFile || !hasEvidence;
        String confidence = normalize(item.confidence(), CONFIDENCES, "medium");
        if (!knownFile || !hasEvidence) {
            confidence = "low";
        }

        return new AiRiskItem(
                normalize(item.severity(), SEVERITIES, "medium"),
                file,
                safe(item.title()),
                safe(item.detail()),
                hasEvidence ? item.evidence() : "模型未提供可核验的变更证据。",
                safe(item.impact()),
                confidence,
                needsHumanReview,
                safe(item.recommendation())
        );
    }

    private static List<String> mergeLimitations(List<String> modelLimitations, ReviewContext context) {
        Set<String> limitations = new LinkedHashSet<>(modelLimitations);
        ReviewContextStats stats = context.stats();
        if (stats != null) {
            if (stats.truncatedFiles() > 0) {
                limitations.add("部分文件 patch 被截断，相关风险需要结合完整 diff 复核。");
            }
            if (stats.filesWithPatch() < stats.totalFiles()) {
                limitations.add("部分变更文件没有 patch 内容，AI 无法完整判断这些文件的风险。");
            }
            if (stats.promptCharacters() >= stats.maxPromptCharacters()) {
                limitations.add("Review 上下文达到最大长度，末尾内容可能未进入模型输入。");
            }
        }
        if (!context.truncationNotes().isEmpty()) {
            limitations.add("GitHub diff 上下文存在截断或缺失，结论应作为辅助建议。");
        }
        return limitations.stream()
                .map(AiReviewQualityGate::safe)
                .filter(value -> !value.isBlank())
                .toList();
    }

    private static String enrichMarkdown(String markdown, List<String> limitations) {
        String safeMarkdown = safe(markdown);
        if (limitations.isEmpty()) {
            return safeMarkdown;
        }
        if (safeMarkdown.contains("判断限制") || safeMarkdown.contains("局限")) {
            return safeMarkdown;
        }
        StringBuilder builder = new StringBuilder(safeMarkdown);
        if (!safeMarkdown.isBlank()) {
            builder.append("\n\n");
        }
        builder.append("### 判断限制\n");
        limitations.forEach(limitation -> builder.append("- ").append(limitation).append("\n"));
        return builder.toString().stripTrailing();
    }

    private static String fallbackSummary(String summary, List<AiRiskItem> riskItems) {
        String safeSummary = safe(summary);
        if (!safeSummary.isBlank()) {
            return safeSummary;
        }
        return riskItems.isEmpty()
                ? "未发现明确风险，建议结合完整 diff 进行人工确认。"
                : "发现 %d 个需要关注的风险点。".formatted(riskItems.size());
    }

    private static String normalize(String value, Set<String> allowedValues, String fallback) {
        String normalized = safe(value).toLowerCase(Locale.ROOT);
        return allowedValues.contains(normalized) ? normalized : fallback;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
