package com.codereview.assistant.api;

import com.codereview.assistant.ai.AiModelConfigInput;
import com.codereview.assistant.review.ai.AiReviewRequest;
import com.codereview.assistant.review.PullRequestSummaryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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
        return summaryService.summarize(
                request.prUrl(),
                new AiReviewRequest(request.modelConfig()),
                request.githubToken()
        );
    }

    @PostMapping("/summary/basic")
    public PullRequestSummaryResponse summarizeBasic(@Valid @RequestBody PullRequestSummaryRequest request) {
        return summaryService.summarizeBasic(request.prUrl(), request.githubToken());
    }

    @PostMapping("/review")
    public PullRequestSummaryResponse.AiReviewResponse review(@Valid @RequestBody PullRequestSummaryRequest request) {
        return summaryService.review(
                request.prUrl(),
                new AiReviewRequest(request.modelConfig()),
                request.githubToken()
        );
    }

    public record PullRequestSummaryRequest(
            @NotBlank(message = "GitHub PR URL is required.") String prUrl,
            AiModelConfigInput modelConfig,
            @Size(max = 500, message = "GitHub Token is too long.") String githubToken
    ) {
    }
}
