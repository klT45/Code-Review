package com.codereview.assistant.review.context;

public record ReviewPullRequest(
        String owner,
        String repository,
        int pullNumber,
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
