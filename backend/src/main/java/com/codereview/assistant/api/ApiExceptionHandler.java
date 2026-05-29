package com.codereview.assistant.api;

import com.codereview.assistant.github.GitHubApiException;
import com.codereview.assistant.ai.InvalidModelConfigurationException;
import com.codereview.assistant.review.ai.AiReviewGenerationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationError(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getDefaultMessage())
                .orElse("Request validation failed.");
        return ResponseEntity.badRequest().body(ApiErrorResponse.of("VALIDATION_ERROR", message));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(ApiErrorResponse.of("INVALID_PR_URL", exception.getMessage()));
    }

    @ExceptionHandler(GitHubApiException.class)
    public ResponseEntity<ApiErrorResponse> handleGitHubError(GitHubApiException exception) {
        HttpStatus status = exception.getMessage().contains("rate limit")
                ? HttpStatus.TOO_MANY_REQUESTS
                : HttpStatus.BAD_GATEWAY;
        return ResponseEntity.status(status).body(ApiErrorResponse.of("GITHUB_API_ERROR", exception.getMessage()));
    }

    @ExceptionHandler(InvalidModelConfigurationException.class)
    public ResponseEntity<ApiErrorResponse> handleModelConfigurationError(InvalidModelConfigurationException exception) {
        return ResponseEntity.badRequest().body(ApiErrorResponse.of("INVALID_MODEL_CONFIG", exception.getMessage()));
    }

    @ExceptionHandler(AiReviewGenerationException.class)
    public ResponseEntity<ApiErrorResponse> handleAiReviewError(AiReviewGenerationException exception) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(ApiErrorResponse.of("AI_REVIEW_ERROR", exception.getMessage()));
    }
}
