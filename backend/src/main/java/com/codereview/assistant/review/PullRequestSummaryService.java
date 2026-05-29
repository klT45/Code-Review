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
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class PullRequestSummaryService {

    private final GitHubPullRequestUrlParser urlParser;
    private final GitHubPullRequestClient pullRequestClient;
    private final ReviewContextBuilder reviewContextBuilder;

    public PullRequestSummaryService(
            GitHubPullRequestUrlParser urlParser,
            GitHubPullRequestClient pullRequestClient,
            ReviewContextBuilder reviewContextBuilder
    ) {
        this.urlParser = urlParser;
        this.pullRequestClient = pullRequestClient;
        this.reviewContextBuilder = reviewContextBuilder;
    }

    public PullRequestSummaryResponse summarize(String rawPullRequestUrl) {
        GitHubPullRequestUrl pullRequestUrl = urlParser.parse(rawPullRequestUrl);
        GitHubPullRequestInfo info = pullRequestClient.fetch(pullRequestUrl);
        List<GitHubPullRequestFile> changedFiles = pullRequestClient.fetchFiles(pullRequestUrl);
        ReviewContext reviewContext = reviewContextBuilder.build(pullRequestUrl, info, changedFiles);
        List<PullRequestSummaryResponse.PullRequestFileResponse> files = changedFiles
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
                toResponse(reviewContext)
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
}
