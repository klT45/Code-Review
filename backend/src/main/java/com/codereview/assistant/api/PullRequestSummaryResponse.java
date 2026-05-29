package com.codereview.assistant.api;

import java.util.List;

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
        String htmlUrl,
        List<PullRequestFileResponse> files,
        ReviewContextResponse reviewContext
) {

    public record PullRequestFileResponse(
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

    public record ReviewContextResponse(
            ReviewContextStatsResponse stats,
            List<ReviewFileContextResponse> files,
            List<String> truncationNotes,
            String promptText
    ) {
    }

    public record ReviewContextStatsResponse(
            int totalFiles,
            int filesWithPatch,
            int truncatedFiles,
            int totalPatchCharacters,
            int includedPatchCharacters,
            int promptCharacters,
            int maxPatchCharactersPerFile,
            int maxPromptCharacters
    ) {
    }

    public record ReviewFileContextResponse(
            String filename,
            String status,
            int additions,
            int deletions,
            int changes,
            boolean patchAvailable,
            boolean truncated,
            int originalPatchLength
    ) {
    }
}
