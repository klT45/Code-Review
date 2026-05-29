package com.codereview.assistant.review.context;

import java.util.List;

public record ReviewContext(
        ReviewPullRequest pullRequest,
        ReviewContextStats stats,
        List<ReviewFileContext> files,
        List<String> truncationNotes,
        String promptText
) {
}
