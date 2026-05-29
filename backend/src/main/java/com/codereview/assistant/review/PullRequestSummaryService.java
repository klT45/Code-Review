package com.codereview.assistant.review;

import com.codereview.assistant.api.PullRequestSummaryResponse;
import com.codereview.assistant.github.GitHubPullRequestClient;
import com.codereview.assistant.github.GitHubPullRequestInfo;
import com.codereview.assistant.github.GitHubPullRequestUrl;
import com.codereview.assistant.github.GitHubPullRequestUrlParser;
import org.springframework.stereotype.Service;

@Service
public class PullRequestSummaryService {

    private final GitHubPullRequestUrlParser urlParser;
    private final GitHubPullRequestClient pullRequestClient;

    public PullRequestSummaryService(
            GitHubPullRequestUrlParser urlParser,
            GitHubPullRequestClient pullRequestClient
    ) {
        this.urlParser = urlParser;
        this.pullRequestClient = pullRequestClient;
    }

    public PullRequestSummaryResponse summarize(String rawPullRequestUrl) {
        GitHubPullRequestUrl pullRequestUrl = urlParser.parse(rawPullRequestUrl);
        GitHubPullRequestInfo info = pullRequestClient.fetch(pullRequestUrl);

        return new PullRequestSummaryResponse(
                pullRequestUrl.owner(),
                pullRequestUrl.repository(),
                pullRequestUrl.pullNumber(),
                pullRequestUrl.normalizedUrl(),
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
    }
}
