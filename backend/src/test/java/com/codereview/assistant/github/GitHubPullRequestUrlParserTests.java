package com.codereview.assistant.github;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class GitHubPullRequestUrlParserTests {

    private final GitHubPullRequestUrlParser parser = new GitHubPullRequestUrlParser();

    @Test
    void parsesStandardPullRequestUrl() {
        GitHubPullRequestUrl result = parser.parse("https://github.com/spring-projects/spring-boot/pull/12345");

        assertThat(result.owner()).isEqualTo("spring-projects");
        assertThat(result.repository()).isEqualTo("spring-boot");
        assertThat(result.pullNumber()).isEqualTo(12345);
        assertThat(result.normalizedUrl()).isEqualTo("https://github.com/spring-projects/spring-boot/pull/12345");
    }

    @Test
    void ignoresTrailingSlashAndQueryString() {
        GitHubPullRequestUrl result = parser.parse("https://github.com/openai/openai-java/pull/42/?tab=files");

        assertThat(result.owner()).isEqualTo("openai");
        assertThat(result.repository()).isEqualTo("openai-java");
        assertThat(result.pullNumber()).isEqualTo(42);
        assertThat(result.normalizedUrl()).isEqualTo("https://github.com/openai/openai-java/pull/42");
    }

    @Test
    void rejectsNonGitHubHost() {
        assertThatThrownBy(() -> parser.parse("https://example.com/openai/openai-java/pull/42"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("GitHub");
    }

    @Test
    void rejectsMissingPullRequestNumber() {
        assertThatThrownBy(() -> parser.parse("https://github.com/openai/openai-java/pull"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pull request URL");
    }

    @Test
    void rejectsNonNumericPullRequestNumber() {
        assertThatThrownBy(() -> parser.parse("https://github.com/openai/openai-java/pull/latest"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("number");
    }

    @Test
    void rejectsBlankInput() {
        assertThatThrownBy(() -> parser.parse(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("required");
    }
}
