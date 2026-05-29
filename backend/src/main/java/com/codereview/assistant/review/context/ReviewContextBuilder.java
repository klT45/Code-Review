package com.codereview.assistant.review.context;

import com.codereview.assistant.github.GitHubPullRequestFile;
import com.codereview.assistant.github.GitHubPullRequestInfo;
import com.codereview.assistant.github.GitHubPullRequestUrl;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ReviewContextBuilder {

    static final int DEFAULT_MAX_PATCH_CHARACTERS_PER_FILE = 4_000;
    static final int DEFAULT_MAX_PROMPT_CHARACTERS = 20_000;

    private final int maxPatchCharactersPerFile;
    private final int maxPromptCharacters;

    public ReviewContextBuilder() {
        this(DEFAULT_MAX_PATCH_CHARACTERS_PER_FILE, DEFAULT_MAX_PROMPT_CHARACTERS);
    }

    ReviewContextBuilder(int maxPatchCharactersPerFile, int maxPromptCharacters) {
        this.maxPatchCharactersPerFile = maxPatchCharactersPerFile;
        this.maxPromptCharacters = maxPromptCharacters;
    }

    public ReviewContext build(
            GitHubPullRequestUrl pullRequestUrl,
            GitHubPullRequestInfo info,
            List<GitHubPullRequestFile> changedFiles
    ) {
        List<String> truncationNotes = new ArrayList<>();
        List<ReviewFileContext> fileContexts = changedFiles.stream()
                .map(file -> toFileContext(file, truncationNotes))
                .toList();

        ReviewPullRequest pullRequest = new ReviewPullRequest(
                pullRequestUrl.owner(),
                pullRequestUrl.repository(),
                pullRequestUrl.pullNumber(),
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
                info.htmlUrl()
        );

        String promptText = buildPromptText(pullRequest, fileContexts, truncationNotes);
        if (promptText.length() > maxPromptCharacters) {
            promptText = promptText.substring(0, maxPromptCharacters);
            truncationNotes.add("Review context prompt was truncated to %d characters.".formatted(maxPromptCharacters));
        }

        int totalPatchCharacters = changedFiles.stream()
                .mapToInt(file -> safePatch(file).length())
                .sum();
        int includedPatchCharacters = fileContexts.stream()
                .mapToInt(file -> file.patchSnippet().length())
                .sum();
        int filesWithPatch = (int) fileContexts.stream()
                .filter(ReviewFileContext::patchAvailable)
                .count();
        int truncatedFiles = (int) fileContexts.stream()
                .filter(ReviewFileContext::truncated)
                .count();

        return new ReviewContext(
                pullRequest,
                new ReviewContextStats(
                        fileContexts.size(),
                        filesWithPatch,
                        truncatedFiles,
                        totalPatchCharacters,
                        includedPatchCharacters,
                        promptText.length(),
                        maxPatchCharactersPerFile,
                        maxPromptCharacters
                ),
                fileContexts,
                truncationNotes,
                promptText
        );
    }

    private ReviewFileContext toFileContext(GitHubPullRequestFile file, List<String> truncationNotes) {
        String patch = safePatch(file);
        boolean patchAvailable = !patch.isBlank();
        boolean truncated = patch.length() > maxPatchCharactersPerFile;
        String patchSnippet = truncated ? patch.substring(0, maxPatchCharactersPerFile) : patch;

        if (truncated) {
            truncationNotes.add("Patch for %s was truncated from %d to %d characters.".formatted(
                    file.filename(),
                    patch.length(),
                    maxPatchCharactersPerFile
            ));
        } else if (!patchAvailable) {
            truncationNotes.add("Patch for %s was not available from GitHub.".formatted(file.filename()));
        }

        return new ReviewFileContext(
                file.filename(),
                file.status(),
                file.additions(),
                file.deletions(),
                file.changes(),
                patchAvailable,
                truncated,
                patch.length(),
                patchSnippet
        );
    }

    private String buildPromptText(
            ReviewPullRequest pullRequest,
            List<ReviewFileContext> files,
            List<String> truncationNotes
    ) {
        StringBuilder builder = new StringBuilder();
        builder.append("PR REVIEW CONTEXT\n");
        builder.append("Repository: %s/%s%n".formatted(pullRequest.owner(), pullRequest.repository()));
        builder.append("Pull Request: #%d %s%n".formatted(pullRequest.pullNumber(), pullRequest.title()));
        builder.append("Author: %s%n".formatted(pullRequest.author()));
        builder.append("State: %s draft=%s merged=%s%n".formatted(
                pullRequest.state(),
                pullRequest.draft(),
                pullRequest.merged()
        ));
        builder.append("Branches: %s -> %s%n".formatted(pullRequest.headBranch(), pullRequest.baseBranch()));
        builder.append("Change size: +%d -%d across %d files%n%n".formatted(
                pullRequest.additions(),
                pullRequest.deletions(),
                pullRequest.changedFiles()
        ));

        builder.append("FILES\n");
        for (ReviewFileContext file : files) {
            builder.append("- %s [%s] +%d -%d changes=%d patchAvailable=%s truncated=%s%n".formatted(
                    file.filename(),
                    file.status(),
                    file.additions(),
                    file.deletions(),
                    file.changes(),
                    file.patchAvailable(),
                    file.truncated()
            ));
            if (file.patchAvailable()) {
                builder.append(file.patchSnippet()).append("\n");
            }
            builder.append("\n");
        }

        if (!truncationNotes.isEmpty()) {
            builder.append("CONTEXT NOTES\n");
            truncationNotes.forEach(note -> builder.append("- ").append(note).append("\n"));
        }

        return builder.toString();
    }

    private static String safePatch(GitHubPullRequestFile file) {
        return file.patch() == null ? "" : file.patch();
    }
}
