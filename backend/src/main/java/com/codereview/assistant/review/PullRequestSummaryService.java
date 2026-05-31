package com.codereview.assistant.review;

import com.codereview.assistant.api.PullRequestSummaryResponse;
import com.codereview.assistant.github.GitHubPullRequestClient;
import com.codereview.assistant.github.GitHubPullRequestFile;
import com.codereview.assistant.github.GitHubPullRequestInfo;
import com.codereview.assistant.github.GitHubPullRequestUrl;
import com.codereview.assistant.github.GitHubPullRequestUrlParser;
import com.codereview.assistant.review.context.ReviewContext;
import com.codereview.assistant.review.context.ReviewContextBuilder;
import com.codereview.assistant.review.context.ReviewFileContext;
import com.codereview.assistant.review.ai.AiReviewRequest;
import com.codereview.assistant.review.ai.AiReviewResult;
import com.codereview.assistant.review.ai.AiReviewService;
import com.codereview.assistant.review.ai.AiReviewStreamEvent;
import com.codereview.assistant.review.ai.AiRiskItem;
import com.codereview.assistant.review.ai.AiReviewResult.FileExplanation;
import java.util.List;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
public class PullRequestSummaryService {

    private final GitHubPullRequestUrlParser urlParser;
    private final GitHubPullRequestClient pullRequestClient;
    private final ReviewContextBuilder reviewContextBuilder;
    private final AiReviewService aiReviewService;

    public PullRequestSummaryService(
            GitHubPullRequestUrlParser urlParser,
            GitHubPullRequestClient pullRequestClient,
            ReviewContextBuilder reviewContextBuilder,
            AiReviewService aiReviewService
    ) {
        this.urlParser = urlParser;
        this.pullRequestClient = pullRequestClient;
        this.reviewContextBuilder = reviewContextBuilder;
        this.aiReviewService = aiReviewService;
    }

    public PullRequestSummaryResponse summarize(String rawPullRequestUrl) {
        return summarize(rawPullRequestUrl, new AiReviewRequest(null), null);
    }

    public PullRequestSummaryResponse summarize(String rawPullRequestUrl, AiReviewRequest aiReviewRequest) {
        return summarize(rawPullRequestUrl, aiReviewRequest, null);
    }

    public PullRequestSummaryResponse summarize(
            String rawPullRequestUrl,
            AiReviewRequest aiReviewRequest,
            String githubToken
    ) {
        PullRequestData pullRequestData = fetchPullRequestData(rawPullRequestUrl, githubToken);
        AiReviewResult aiReview = aiReviewService.review(
                pullRequestData.reviewContext(),
                aiReviewRequest.modelConfig()
        );
        return toSummaryResponse(pullRequestData, aiReview);
    }

    public PullRequestSummaryResponse summarizeBasic(String rawPullRequestUrl, String githubToken) {
        PullRequestData pullRequestData = fetchPullRequestData(rawPullRequestUrl, githubToken);
        return toSummaryResponse(pullRequestData, null);
    }

    public PullRequestSummaryResponse.AiReviewResponse review(
            String rawPullRequestUrl,
            AiReviewRequest aiReviewRequest,
            String githubToken
    ) {
        PullRequestData pullRequestData = fetchPullRequestData(rawPullRequestUrl, githubToken);
        AiReviewResult aiReview = aiReviewService.review(
                pullRequestData.reviewContext(),
                aiReviewRequest.modelConfig()
        );
        return toResponse(aiReview);
    }

    public Flux<AiReviewStreamEvent> reviewStream(
            String rawPullRequestUrl,
            AiReviewRequest aiReviewRequest,
            String githubToken
    ) {
        PullRequestData pullRequestData = fetchPullRequestData(rawPullRequestUrl, githubToken);
        return aiReviewService.reviewStream(
                pullRequestData.reviewContext(),
                aiReviewRequest.modelConfig()
        );
    }

    private PullRequestData fetchPullRequestData(String rawPullRequestUrl, String githubToken) {
        GitHubPullRequestUrl pullRequestUrl = urlParser.parse(rawPullRequestUrl);
        GitHubPullRequestInfo info = pullRequestClient.fetch(pullRequestUrl, githubToken);
        List<GitHubPullRequestFile> changedFiles = pullRequestClient.fetchFiles(pullRequestUrl, githubToken);
        ReviewContext reviewContext = reviewContextBuilder.build(pullRequestUrl, info, changedFiles);
        return new PullRequestData(pullRequestUrl, info, changedFiles, reviewContext);
    }

    private static PullRequestSummaryResponse toSummaryResponse(
            PullRequestData pullRequestData,
            AiReviewResult aiReview
    ) {
        GitHubPullRequestUrl pullRequestUrl = pullRequestData.pullRequestUrl();
        GitHubPullRequestInfo info = pullRequestData.info();
        List<PullRequestSummaryResponse.PullRequestFileResponse> files = pullRequestData.changedFiles()
                .stream()
                .map(PullRequestSummaryService::toResponse)
                .toList();

        return new PullRequestSummaryResponse(
                pullRequestUrl.owner(),
                pullRequestUrl.repository(),
                pullRequestUrl.pullNumber(),
                pullRequestUrl.normalizedUrl(),
                info.title(),
                info.author(),
                info.state(),
                info.draft(),
                info.merged(),
                info.headBranch(),
                info.baseBranch(),
                info.additions(),
                info.deletions(),
                info.changedFiles(),
                info.htmlUrl(),
                files,
                toResponse(pullRequestData.reviewContext()),
                aiReview == null ? null : toResponse(aiReview)
        );
    }

    private static PullRequestSummaryResponse.PullRequestFileResponse toResponse(GitHubPullRequestFile file) {
        return new PullRequestSummaryResponse.PullRequestFileResponse(
                file.filename(),
                file.status(),
                file.additions(),
                file.deletions(),
                file.changes(),
                file.patch(),
                file.blobUrl(),
                file.rawUrl(),
                file.contentsUrl(),
                file.previousFilename()
        );
    }

    private static PullRequestSummaryResponse.ReviewContextResponse toResponse(ReviewContext context) {
        return new PullRequestSummaryResponse.ReviewContextResponse(
                new PullRequestSummaryResponse.ReviewContextStatsResponse(
                        context.stats().totalFiles(),
                        context.stats().filesWithPatch(),
                        context.stats().truncatedFiles(),
                        context.stats().totalPatchCharacters(),
                        context.stats().includedPatchCharacters(),
                        context.stats().promptCharacters(),
                        context.stats().maxPatchCharactersPerFile(),
                        context.stats().maxPromptCharacters()
                ),
                context.files().stream()
                        .map(PullRequestSummaryService::toResponse)
                        .toList(),
                context.truncationNotes(),
                context.promptText()
        );
    }

    private static PullRequestSummaryResponse.ReviewFileContextResponse toResponse(ReviewFileContext file) {
        return new PullRequestSummaryResponse.ReviewFileContextResponse(
                file.filename(),
                file.status(),
                file.additions(),
                file.deletions(),
                file.changes(),
                file.patchAvailable(),
                file.truncated(),
                file.originalPatchLength()
        );
    }

    private static PullRequestSummaryResponse.AiReviewResponse toResponse(AiReviewResult review) {
        return new PullRequestSummaryResponse.AiReviewResponse(
                review.enabled(),
                review.generated(),
                review.providerId(),
                review.modelId(),
                review.summary(),
                review.riskItems().stream()
                        .map(PullRequestSummaryService::toResponse)
                        .toList(),
                review.fileExplanations().stream()
                        .map(PullRequestSummaryService::toResponse)
                        .toList(),
                review.requiredActions(),
                review.suggestions(),
                review.followUpItems(),
                review.limitations(),
                review.markdown(),
                review.message()
        );
    }

    private static PullRequestSummaryResponse.AiRiskItemResponse toResponse(AiRiskItem item) {
        return new PullRequestSummaryResponse.AiRiskItemResponse(
                item.severity(),
                item.file(),
                item.title(),
                item.detail(),
                item.evidence(),
                item.impact(),
                item.confidence(),
                item.needsHumanReview(),
                item.recommendation()
        );
    }

    private static PullRequestSummaryResponse.FileExplanationResponse toResponse(FileExplanation item) {
        return new PullRequestSummaryResponse.FileExplanationResponse(
                item.filename(),
                item.explanation()
        );
    }

    private record PullRequestData(
            GitHubPullRequestUrl pullRequestUrl,
            GitHubPullRequestInfo info,
            List<GitHubPullRequestFile> changedFiles,
            ReviewContext reviewContext
    ) {
    }
}
