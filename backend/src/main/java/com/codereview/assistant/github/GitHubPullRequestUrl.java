package com.codereview.assistant.github;

public record GitHubPullRequestUrl(
        String owner,
        String repository,
        int pullNumber,
        String normalizedUrl
) {
}
