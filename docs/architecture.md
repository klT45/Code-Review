# Architecture Notes

## Goal

The application helps developers review GitHub pull requests by combining code-change context with AI-assisted analysis. The user enters a GitHub PR URL, then the system fetches PR metadata and diffs, compresses the context, sends it to a selected model, and returns a structured review report.

## Backend Shape

The backend is a Spring Boot service organized around four future boundaries:

- `github`: parse PR URLs and fetch public or token-authorized private PR data.
- `review`: build review context and coordinate analysis.
- `model`: manage OpenAI-compatible model provider profiles.
- `api`: expose REST endpoints for the React workbench.

Spring AI will be used for model integration. DeepSeek should be the default documented provider, while the implementation should keep provider configuration generic enough for any OpenAI-compatible model.

## Frontend Shape

The frontend is a React workbench, not a landing page. Its first screen should focus on the review workflow:

- PR URL input.
- GitHub access mode.
- Model provider selector.
- Analysis state and structured report display.
- Markdown copy action for GitHub review comments.

## Security Direction

User secrets such as GitHub tokens and model API keys must not be logged. Later PRs should prefer in-memory request-scoped credentials first. Persistent user profiles should only be added with explicit encryption and storage decisions.

## PR Strategy

After this baseline, each PR should add one small capability:

- GitHub PR URL parsing.
- Public PR metadata fetching.
- Private repository token support.
- Model provider configuration.
- DeepSeek-compatible Spring AI client.
- Review context compression.
- Structured report generation.
- Frontend workflow integration.
