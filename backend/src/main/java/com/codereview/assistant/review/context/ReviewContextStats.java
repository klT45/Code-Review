package com.codereview.assistant.review.context;

public record ReviewContextStats(
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
