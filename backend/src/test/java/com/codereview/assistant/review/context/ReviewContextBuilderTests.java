package com.codereview.assistant.review.context;

import static org.assertj.core.api.Assertions.assertThat;

import com.codereview.assistant.github.GitHubPullRequestFile;
import com.codereview.assistant.github.GitHubPullRequestInfo;
import com.codereview.assistant.github.GitHubPullRequestUrl;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReviewContextBuilderTests {

    private static final GitHubPullRequestUrl PULL_REQUEST_URL = new GitHubPullRequestUrl(
            "openai",
            "openai-java",
            42,
            "https://github.com/openai/openai-java/pull/42"
    );
    private static final GitHubPullRequestInfo PULL_REQUEST_INFO = new GitHubPullRequestInfo(
            "Add review endpoint",
            "octocat",
            "open",
            false,
            false,
            "feature/review",
            "main",
            120,
            15,
            2,
            "https://github.com/openai/openai-java/pull/42"
    );

    @Test
    void buildsContextWithoutTruncatingSmallPullRequest() {
        ReviewContextBuilder builder = new ReviewContextBuilder(200, 2_000);

        ReviewContext context = builder.build(PULL_REQUEST_URL, PULL_REQUEST_INFO, List.of(
                file("src/main/java/ReviewService.java", "modified", "@@ -1 +1 @@\n+review();")
        ));

        assertThat(context.pullRequest().title()).isEqualTo("Add review endpoint");
        assertThat(context.stats().totalFiles()).isEqualTo(1);
        assertThat(context.stats().filesWithPatch()).isEqualTo(1);
        assertThat(context.stats().truncatedFiles()).isZero();
        assertThat(context.files().get(0).patchSnippet()).contains("review();");
        assertThat(context.truncationNotes()).isEmpty();
        assertThat(context.promptText()).contains("PR REVIEW CONTEXT", "ReviewService.java");
    }

    @Test
    void truncatesLongFilePatchAndRecordsNote() {
        ReviewContextBuilder builder = new ReviewContextBuilder(12, 2_000);

        ReviewContext context = builder.build(PULL_REQUEST_URL, PULL_REQUEST_INFO, List.of(
                file("src/main/java/LargeService.java", "modified", "0123456789abcdef")
        ));

        ReviewFileContext file = context.files().get(0);
        assertThat(file.truncated()).isTrue();
        assertThat(file.originalPatchLength()).isEqualTo(16);
        assertThat(file.patchSnippet()).isEqualTo("0123456789ab");
        assertThat(context.stats().truncatedFiles()).isEqualTo(1);
        assertThat(context.truncationNotes())
                .anySatisfy(note -> assertThat(note).contains("LargeService.java", "truncated"));
    }

    @Test
    void recordsMissingPatchWithoutDroppingFileMetadata() {
        ReviewContextBuilder builder = new ReviewContextBuilder(100, 2_000);

        ReviewContext context = builder.build(PULL_REQUEST_URL, PULL_REQUEST_INFO, List.of(
                file("docs/README.md", "removed", "")
        ));

        ReviewFileContext file = context.files().get(0);
        assertThat(file.filename()).isEqualTo("docs/README.md");
        assertThat(file.patchAvailable()).isFalse();
        assertThat(file.patchSnippet()).isEmpty();
        assertThat(context.stats().filesWithPatch()).isZero();
        assertThat(context.truncationNotes())
                .anySatisfy(note -> assertThat(note).contains("not available"));
    }

    @Test
    void truncatesPromptWhenTotalContextIsTooLarge() {
        ReviewContextBuilder builder = new ReviewContextBuilder(100, 160);

        ReviewContext context = builder.build(PULL_REQUEST_URL, PULL_REQUEST_INFO, List.of(
                file("src/main/java/First.java", "modified", "first patch content"),
                file("src/main/java/Second.java", "modified", "second patch content")
        ));

        assertThat(context.promptText()).hasSize(160);
        assertThat(context.stats().promptCharacters()).isEqualTo(160);
        assertThat(context.truncationNotes())
                .anySatisfy(note -> assertThat(note).contains("prompt was truncated"));
    }

    private static GitHubPullRequestFile file(String filename, String status, String patch) {
        return new GitHubPullRequestFile(
                filename,
                status,
                10,
                2,
                12,
                patch,
                "https://github.com/openai/openai-java/blob/main/" + filename,
                null,
                null,
                null
        );
    }
}
