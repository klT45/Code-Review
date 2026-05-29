package com.codereview.assistant.api;

public record PullRequestSummaryResponse(
        String owner,
        String repository,
        int pullNumber,
        String normalizedUrl,
        String title,
        String author,
        String state,
        boolean draft,
        boolean merged,
        String headBranch,
        String baseBranch,
        int additions,
        int deletions,
        int changedFiles,
        String htmlUrl
) {
}
