package com.codereview.assistant.github;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class GitHubPullRequestClient {

    private static final String GITHUB_API_BASE_URL = "https://api.github.com";
    private static final int FILES_PAGE_SIZE = 100;

    private final RestClient restClient;

    @Autowired
    public GitHubPullRequestClient(RestClient.Builder restClientBuilder) {
        this(restClientBuilder
                .baseUrl(GITHUB_API_BASE_URL)
                .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
                .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
                .defaultHeader(HttpHeaders.USER_AGENT, "ai-pr-review-assistant")
                .build());
    }

    GitHubPullRequestClient(RestClient restClient) {
        this.restClient = restClient;
    }

    public GitHubPullRequestInfo fetch(GitHubPullRequestUrl pullRequestUrl) {
        return fetch(pullRequestUrl, null);
    }

    public GitHubPullRequestInfo fetch(GitHubPullRequestUrl pullRequestUrl, String githubToken) {
        try {
            RestClient.RequestHeadersSpec<?> request = authenticatedRequest(
                    restClient.get()
                            .uri(
                                    "/repos/{owner}/{repo}/pulls/{pullNumber}",
                                    pullRequestUrl.owner(),
                                    pullRequestUrl.repository(),
                                    pullRequestUrl.pullNumber()
                            ),
                    githubToken
            );

            PullRequestResponse response = request
                    .retrieve()
                    .body(PullRequestResponse.class);

            if (response == null) {
                throw new GitHubApiException("GitHub returned an empty pull request response.");
            }

            return response.toInfo();
        } catch (RestClientResponseException exception) {
            throw toGitHubApiException(exception);
        }
    }

    public List<GitHubPullRequestFile> fetchFiles(GitHubPullRequestUrl pullRequestUrl) {
        return fetchFiles(pullRequestUrl, null);
    }

    public List<GitHubPullRequestFile> fetchFiles(GitHubPullRequestUrl pullRequestUrl, String githubToken) {
        try {
            List<GitHubPullRequestFile> files = new ArrayList<>();
            int page = 1;

            while (true) {
                RestClient.RequestHeadersSpec<?> request = authenticatedRequest(
                        restClient.get()
                                .uri(
                                        "/repos/{owner}/{repo}/pulls/{pullNumber}/files?per_page={perPage}&page={page}",
                                        pullRequestUrl.owner(),
                                        pullRequestUrl.repository(),
                                        pullRequestUrl.pullNumber(),
                                        FILES_PAGE_SIZE,
                                        page
                                ),
                        githubToken
                );

                PullRequestFileResponse[] response = request
                        .retrieve()
                        .body(PullRequestFileResponse[].class);

                if (response == null) {
                    throw new GitHubApiException("GitHub returned an empty pull request files response.");
                }

                files.addAll(Arrays.stream(response)
                        .map(PullRequestFileResponse::toFile)
                        .toList());

                if (response.length < FILES_PAGE_SIZE) {
                    return files;
                }
                page++;
            }
        } catch (RestClientResponseException exception) {
            throw toGitHubApiException(exception);
        }
    }

    private GitHubApiException toGitHubApiException(RestClientResponseException exception) {
        HttpStatus status = HttpStatus.resolve(exception.getStatusCode().value());
        if (status == HttpStatus.NOT_FOUND) {
            return new GitHubApiException("GitHub pull request was not found or is not public.", exception);
        }
        HttpHeaders responseHeaders = exception.getResponseHeaders();
        if (status == HttpStatus.FORBIDDEN
                && responseHeaders != null
                && "0".equals(responseHeaders.getFirst("X-RateLimit-Remaining"))) {
            return new GitHubApiException("GitHub API rate limit exceeded. Try again later or configure a token.", exception);
        }
        return new GitHubApiException(
                "GitHub API request failed with status %d.".formatted(exception.getStatusCode().value()),
                exception
        );
    }

    private RestClient.RequestHeadersSpec<?> authenticatedRequest(
            RestClient.RequestHeadersSpec<?> request,
            String requestToken
    ) {
        String token = firstNonBlank(requestToken, System.getenv("GITHUB_TOKEN"));
        if (token != null && !token.isBlank()) {
            return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return request;
    }

    private static String firstNonBlank(String preferred, String fallback) {
        if (preferred != null && !preferred.isBlank()) {
            return preferred.strip();
        }
        return fallback == null || fallback.isBlank() ? null : fallback.strip();
    }

    private record PullRequestResponse(
            String title,
            String state,
            User user,
            Ref head,
            Ref base,
            @JsonProperty("html_url") String htmlUrl,
            Boolean draft,
            Boolean merged,
            Integer additions,
            Integer deletions,
            @JsonProperty("changed_files") Integer changedFiles
    ) {

        private GitHubPullRequestInfo toInfo() {
            return new GitHubPullRequestInfo(
                    title,
                    user == null ? "" : user.login(),
                    state,
                    Boolean.TRUE.equals(draft),
                    Boolean.TRUE.equals(merged),
                    head == null ? "" : head.ref(),
                    base == null ? "" : base.ref(),
                    additions == null ? 0 : additions,
                    deletions == null ? 0 : deletions,
                    changedFiles == null ? 0 : changedFiles,
                    htmlUrl
            );
        }
    }

    private record User(String login) {
    }

    private record Ref(String ref) {
    }

    private record PullRequestFileResponse(
            String filename,
            String status,
            Integer additions,
            Integer deletions,
            Integer changes,
            String patch,
            @JsonProperty("blob_url") String blobUrl,
            @JsonProperty("raw_url") String rawUrl,
            @JsonProperty("contents_url") String contentsUrl,
            @JsonProperty("previous_filename") String previousFilename
    ) {

        private GitHubPullRequestFile toFile() {
            return new GitHubPullRequestFile(
                    filename,
                    status,
                    additions == null ? 0 : additions,
                    deletions == null ? 0 : deletions,
                    changes == null ? 0 : changes,
                    patch == null ? "" : patch,
                    blobUrl,
                    rawUrl,
                    contentsUrl,
                    previousFilename
            );
        }
    }
}
