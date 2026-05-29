package com.codereview.assistant.review.context;

public record ReviewFileContext(
        String filename,
        String status,
        int additions,
        int deletions,
        int changes,
        boolean patchAvailable,
        boolean truncated,
        int originalPatchLength,
        String patchSnippet
) {
}
