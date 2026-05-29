package com.codereview.assistant.api;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codereview.assistant.github.GitHubApiException;
import com.codereview.assistant.github.GitHubPullRequestClient;
import com.codereview.assistant.github.GitHubPullRequestFile;
import com.codereview.assistant.github.GitHubPullRequestInfo;
import com.codereview.assistant.github.GitHubPullRequestUrl;
import com.codereview.assistant.github.GitHubPullRequestUrlParser;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import com.codereview.assistant.review.PullRequestSummaryService;

@WebMvcTest(PullRequestSummaryController.class)
@Import({ApiExceptionHandler.class, PullRequestSummaryService.class, GitHubPullRequestUrlParser.class})
class PullRequestSummaryControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private GitHubPullRequestClient pullRequestClient;

    @Test
    void returnsPullRequestSummary() throws Exception {
        when(pullRequestClient.fetch(any(GitHubPullRequestUrl.class))).thenReturn(new GitHubPullRequestInfo(
                "Add summary endpoint",
                "octocat",
                "open",
                false,
                false,
                "feature/summary",
                "main",
                120,
                15,
                4,
                "https://github.com/openai/openai-java/pull/42"
        ));
        when(pullRequestClient.fetchFiles(any(GitHubPullRequestUrl.class))).thenReturn(List.of(
                new GitHubPullRequestFile(
                        "src/main/java/ReviewService.java",
                        "modified",
                        25,
                        4,
                        29,
                        "@@ -1,3 +1,4 @@",
                        "https://github.com/openai/openai-java/blob/main/src/main/java/ReviewService.java",
                        "https://github.com/openai/openai-java/raw/main/src/main/java/ReviewService.java",
                        "https://api.github.com/repos/openai/openai-java/contents/src/main/java/ReviewService.java",
                        null
                ),
                new GitHubPullRequestFile(
                        "src/test/java/ReviewServiceTests.java",
                        "added",
                        80,
                        0,
                        80,
                        "",
                        null,
                        null,
                        null,
                        null
                )
        ));

        mockMvc.perform(post("/api/pull-requests/summary")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"prUrl":"https://github.com/openai/openai-java/pull/42"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.owner").value("openai"))
                .andExpect(jsonPath("$.repository").value("openai-java"))
                .andExpect(jsonPath("$.pullNumber").value(42))
                .andExpect(jsonPath("$.title").value("Add summary endpoint"))
                .andExpect(jsonPath("$.author").value("octocat"))
                .andExpect(jsonPath("$.changedFiles").value(4))
                .andExpect(jsonPath("$.files[0].filename").value("src/main/java/ReviewService.java"))
                .andExpect(jsonPath("$.files[0].status").value("modified"))
                .andExpect(jsonPath("$.files[0].additions").value(25))
                .andExpect(jsonPath("$.files[0].deletions").value(4))
                .andExpect(jsonPath("$.files[0].patch").value("@@ -1,3 +1,4 @@"))
                .andExpect(jsonPath("$.files[1].filename").value("src/test/java/ReviewServiceTests.java"));
    }

    @Test
    void returnsBadRequestForInvalidUrl() throws Exception {
        mockMvc.perform(post("/api/pull-requests/summary")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"prUrl":"https://example.com/not/github/pull/1"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PR_URL"))
                .andExpect(jsonPath("$.message", containsString("GitHub")));
    }

    @Test
    void returnsBadGatewayForGitHubFailure() throws Exception {
        when(pullRequestClient.fetch(eq(new GitHubPullRequestUrl(
                "openai",
                "openai-java",
                404,
                "https://github.com/openai/openai-java/pull/404"
        )))).thenThrow(new GitHubApiException("GitHub pull request was not found or is not public."));

        mockMvc.perform(post("/api/pull-requests/summary")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"prUrl":"https://github.com/openai/openai-java/pull/404"}
                                """))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("GITHUB_API_ERROR"));
    }
}
