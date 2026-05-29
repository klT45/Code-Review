package com.codereview.assistant.api;

import com.codereview.assistant.review.PullRequestSummaryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/pull-requests")
public class PullRequestSummaryController {

    private final PullRequestSummaryService summaryService;

    public PullRequestSummaryController(PullRequestSummaryService summaryService) {
        this.summaryService = summaryService;
    }

    @PostMapping("/summary")
    public PullRequestSummaryResponse summarize(@Valid @RequestBody PullRequestSummaryRequest request) {
        return summaryService.summarize(request.prUrl());
    }

    public record PullRequestSummaryRequest(
            @NotBlank(message = "GitHub PR URL is required.") String prUrl
    ) {
    }
}
