package com.codereview.assistant.github;

public record GitHubPullRequestInfo(
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
