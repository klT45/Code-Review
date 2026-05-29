package com.codereview.assistant.github;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class GitHubPullRequestUrlParser {

    private static final Pattern PULL_REQUEST_PATH = Pattern.compile("^/([^/]+)/([^/]+)/pull/(\\d+)/?$");

    public GitHubPullRequestUrl parse(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new IllegalArgumentException("GitHub pull request URL is required.");
        }

        URI uri = toUri(rawUrl.trim());
        String host = uri.getHost();
        if (host == null || !host.toLowerCase(Locale.ROOT).equals("github.com")) {
            throw new IllegalArgumentException("Only GitHub pull request URLs are supported.");
        }

        Matcher matcher = PULL_REQUEST_PATH.matcher(uri.getPath());
        if (!matcher.matches()) {
            if (uri.getPath().contains("/pull/")) {
                throw new IllegalArgumentException("GitHub pull request number must be numeric.");
            }
            throw new IllegalArgumentException("Invalid GitHub pull request URL.");
        }

        String owner = matcher.group(1);
        String repository = matcher.group(2);
        int pullNumber = Integer.parseInt(matcher.group(3));
        String normalizedUrl = "https://github.com/%s/%s/pull/%d".formatted(owner, repository, pullNumber);

        return new GitHubPullRequestUrl(owner, repository, pullNumber, normalizedUrl);
    }

    private URI toUri(String rawUrl) {
        try {
            URI uri = new URI(rawUrl);
            if (uri.getScheme() == null || uri.getHost() == null) {
                throw new IllegalArgumentException("GitHub pull request URL must include https://github.com.");
            }
            if (!uri.getScheme().equalsIgnoreCase("https")) {
                throw new IllegalArgumentException("GitHub pull request URL must use HTTPS.");
            }
            return uri;
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException("Invalid GitHub pull request URL.", exception);
        }
    }
}
