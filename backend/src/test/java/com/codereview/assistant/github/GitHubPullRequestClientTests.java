package com.codereview.assistant.github;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class GitHubPullRequestClientTests {

    @Test
    void fetchesBasicPullRequestInformation() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GitHubPullRequestClient client = new GitHubPullRequestClient(builder);
        GitHubPullRequestUrl pullRequestUrl = new GitHubPullRequestUrl(
                "openai",
                "openai-java",
                42,
                "https://github.com/openai/openai-java/pull/42"
        );

        server.expect(once(), requestTo("https://api.github.com/repos/openai/openai-java/pulls/42"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.ACCEPT, "application/vnd.github+json"))
                .andRespond(withSuccess("""
                        {
                          "title": "Add review endpoint",
                          "state": "open",
                          "user": { "login": "octocat" },
                          "head": { "ref": "feature/review-endpoint" },
                          "base": { "ref": "main" },
                          "html_url": "https://github.com/openai/openai-java/pull/42",
                          "draft": false,
                          "merged": false,
                          "additions": 120,
                          "deletions": 15,
                          "changed_files": 4
                        }
                        """, MediaType.APPLICATION_JSON));

        GitHubPullRequestInfo info = client.fetch(pullRequestUrl);

        assertThat(info.title()).isEqualTo("Add review endpoint");
        assertThat(info.author()).isEqualTo("octocat");
        assertThat(info.state()).isEqualTo("open");
        assertThat(info.draft()).isFalse();
        assertThat(info.merged()).isFalse();
        assertThat(info.headBranch()).isEqualTo("feature/review-endpoint");
        assertThat(info.baseBranch()).isEqualTo("main");
        assertThat(info.additions()).isEqualTo(120);
        assertThat(info.deletions()).isEqualTo(15);
        assertThat(info.changedFiles()).isEqualTo(4);
        assertThat(info.htmlUrl()).isEqualTo("https://github.com/openai/openai-java/pull/42");
        server.verify();
    }

    @Test
    void sendsRequestTokenWhenProvided() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GitHubPullRequestClient client = new GitHubPullRequestClient(builder);
        GitHubPullRequestUrl pullRequestUrl = new GitHubPullRequestUrl(
                "openai",
                "openai-java",
                42,
                "https://github.com/openai/openai-java/pull/42"
        );

        server.expect(once(), requestTo("https://api.github.com/repos/openai/openai-java/pulls/42"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer request-token"))
                .andRespond(withSuccess("""
                        {
                          "title": "Add review endpoint",
                          "state": "open",
                          "user": { "login": "octocat" },
                          "head": { "ref": "feature/review-endpoint" },
                          "base": { "ref": "main" },
                          "html_url": "https://github.com/openai/openai-java/pull/42"
                        }
                        """, MediaType.APPLICATION_JSON));

        GitHubPullRequestInfo info = client.fetch(pullRequestUrl, " request-token ");

        assertThat(info.title()).isEqualTo("Add review endpoint");
        server.verify();
    }

    @Test
    void sendsRequestTokenForChangedFiles() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GitHubPullRequestClient client = new GitHubPullRequestClient(builder);
        GitHubPullRequestUrl pullRequestUrl = new GitHubPullRequestUrl(
                "openai",
                "openai-java",
                42,
                "https://github.com/openai/openai-java/pull/42"
        );

        server.expect(once(), requestTo("https://api.github.com/repos/openai/openai-java/pulls/42/files?per_page=100&page=1"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer request-token"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        assertThat(client.fetchFiles(pullRequestUrl, "request-token")).isEmpty();
        server.verify();
    }

    @Test
    void reportsNotFoundPullRequest() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GitHubPullRequestClient client = new GitHubPullRequestClient(builder);

        server.expect(requestTo("https://api.github.com/repos/openai/openai-java/pulls/404"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"message\":\"Not Found\"}"));

        assertThatThrownBy(() -> client.fetch(new GitHubPullRequestUrl(
                "openai",
                "openai-java",
                404,
                "https://github.com/openai/openai-java/pull/404"
        )))
                .isInstanceOf(GitHubApiException.class)
                .hasMessageContaining("not found");
        server.verify();
    }

    @Test
    void fetchesChangedFiles() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GitHubPullRequestClient client = new GitHubPullRequestClient(builder);
        GitHubPullRequestUrl pullRequestUrl = new GitHubPullRequestUrl(
                "openai",
                "openai-java",
                42,
                "https://github.com/openai/openai-java/pull/42"
        );

        server.expect(once(), requestTo("https://api.github.com/repos/openai/openai-java/pulls/42/files?per_page=100&page=1"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.ACCEPT, "application/vnd.github+json"))
                .andRespond(withSuccess("""
                        [
                          {
                            "filename": "src/main/java/ReviewService.java",
                            "status": "modified",
                            "additions": 25,
                            "deletions": 4,
                            "changes": 29,
                            "patch": "@@ -1,3 +1,4 @@",
                            "blob_url": "https://github.com/openai/openai-java/blob/main/src/main/java/ReviewService.java",
                            "raw_url": "https://github.com/openai/openai-java/raw/main/src/main/java/ReviewService.java",
                            "contents_url": "https://api.github.com/repos/openai/openai-java/contents/src/main/java/ReviewService.java"
                          },
                          {
                            "filename": "src/test/java/ReviewServiceTests.java",
                            "status": "added",
                            "additions": 80,
                            "deletions": 0,
                            "changes": 80
                          }
                        ]
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.fetchFiles(pullRequestUrl))
                .hasSize(2)
                .first()
                .satisfies(file -> {
                    assertThat(file.filename()).isEqualTo("src/main/java/ReviewService.java");
                    assertThat(file.status()).isEqualTo("modified");
                    assertThat(file.additions()).isEqualTo(25);
                    assertThat(file.deletions()).isEqualTo(4);
                    assertThat(file.changes()).isEqualTo(29);
                    assertThat(file.patch()).contains("@@");
                    assertThat(file.blobUrl()).contains("github.com");
                });
        server.verify();
    }

    @Test
    void fetchesAllChangedFilePages() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GitHubPullRequestClient client = new GitHubPullRequestClient(builder);
        GitHubPullRequestUrl pullRequestUrl = new GitHubPullRequestUrl(
                "openai",
                "openai-java",
                42,
                "https://github.com/openai/openai-java/pull/42"
        );

        String firstPage = IntStream.rangeClosed(1, 100)
                .mapToObj(index -> """
                        {
                          "filename": "file-%d.java",
                          "status": "modified",
                          "additions": 1,
                          "deletions": 0,
                          "changes": 1
                        }
                        """.formatted(index))
                .collect(Collectors.joining(",", "[", "]"));

        server.expect(once(), requestTo("https://api.github.com/repos/openai/openai-java/pulls/42/files?per_page=100&page=1"))
                .andRespond(withSuccess(firstPage, MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo("https://api.github.com/repos/openai/openai-java/pulls/42/files?per_page=100&page=2"))
                .andRespond(withSuccess("""
                        [
                          {
                            "filename": "file-101.java",
                            "status": "added",
                            "additions": 2,
                            "deletions": 0,
                            "changes": 2
                          }
                        ]
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.fetchFiles(pullRequestUrl))
                .hasSize(101)
                .last()
                .satisfies(file -> assertThat(file.filename()).isEqualTo("file-101.java"));
        server.verify();
    }

    @Test
    void reportsRateLimit() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GitHubPullRequestClient client = new GitHubPullRequestClient(builder);

        server.expect(requestTo("https://api.github.com/repos/openai/openai-java/pulls/42"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN)
                        .header("X-RateLimit-Remaining", "0")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"message\":\"API rate limit exceeded\"}"));

        assertThatThrownBy(() -> client.fetch(new GitHubPullRequestUrl(
                "openai",
                "openai-java",
                42,
                "https://github.com/openai/openai-java/pull/42"
        )))
                .isInstanceOf(GitHubApiException.class)
                .hasMessageContaining("rate limit");
        server.verify();
    }
}
