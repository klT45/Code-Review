package com.codereview.assistant.github;

public record GitHubPullRequestFile(
        String filename,
        String status,
        int additions,
        int deletions,
        int changes,
        String patch,
        String blobUrl,
        String rawUrl,
        String contentsUrl,
        String previousFilename
) {
}
