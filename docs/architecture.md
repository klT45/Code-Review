# Architecture Notes

## Goal

The application helps developers review GitHub pull requests by combining code-change context with AI-assisted analysis. The user enters a GitHub PR URL, then the system fetches PR metadata and diffs, compresses the context, sends it to a selected model, and returns a structured review report.

## Backend Shape

The backend is a Spring Boot service organized around these boundaries:

- `github`: parse PR URLs and fetch public or token-authorized private PR data.
- `review.context`: build bounded review context from PR metadata and changed files.
- `review.ai`: generate and refine structured AI Review results.
- `ai`: resolve OpenAI-compatible model provider configuration.
- `api`: expose REST endpoints for the React workbench.

Important API endpoints:

- `GET /api/health`: service health check.
- `GET /api/model-config`: available model profiles and API key readiness.
- `POST /api/model-config/resolve`: validate request-time model configuration.
- `POST /api/pull-requests/summary/basic`: fetch PR information without waiting for AI.
- `POST /api/pull-requests/review`: generate AI Review for a PR.
- `POST /api/pull-requests/summary`: fetch PR information and AI Review together.

## AI Review Flow

1. Parse the GitHub pull request URL.
2. Fetch PR metadata through GitHub REST API.
3. Fetch changed files and patch snippets.
4. Build a review context with size limits and truncation notes.
5. Resolve the model provider, API key, base URL, and model ID.
6. Use Spring AI to call the OpenAI-compatible chat model.
7. Parse the model output into structured Java records.
8. Apply a quality gate that normalizes severity, confidence, and context limitations.
9. Return a response optimized for modular frontend rendering and Markdown copying.

## Frontend Shape

The frontend is a React workbench, not a landing page. Its first screen focuses on the review workflow:

- PR URL input.
- GitHub access settings for private repository token input.
- Model settings for provider, API key, base URL, and model ID.
- Analysis state and error handling.
- PR information modal with changed file list and context statistics.
- AI Review module with summary, risks, required actions, suggestions, follow-ups, limitations, and Markdown copy.

The frontend starts basic PR information fetching and AI Review generation together. This lets users inspect PR data while the model works in the background.

## Security Direction

User secrets such as GitHub tokens and model API keys must not be committed or logged. The current implementation supports:

- Backend environment variables such as `DEEPSEEK_API_KEY` and `GITHUB_TOKEN`.
- Request-scoped frontend inputs that are kept in memory for the current analysis.

Persistent user profiles should only be added with explicit encryption and storage decisions.

## PR Strategy

The project follows small PR-based development:

- Each PR focuses on one capability.
- Main branch should remain runnable after every merge.
- PR descriptions should include title, feature description, implementation approach, and test method.
- Local-only files such as `1.txt` and planning notes should not be included in PRs.
