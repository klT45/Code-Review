# Design Notes

## Product Goal

AI PR Review Assistant helps developers inspect pull requests faster without replacing human review. The tool focuses on three practical review outputs:

- Change summary: explain what the PR changes.
- Risk identification: call out risky changed code with evidence.
- Review suggestions: separate required fixes, recommended improvements, and follow-up items.

The first version is optimized for GitHub pull requests and OpenAI-compatible chat models, with DeepSeek as the default provider profile.

## Model Choice

The backend uses Spring AI because the project is Java-first and Spring Boot is already the application framework. Spring AI gives the backend a consistent abstraction for chat models while still allowing request-time model configuration.

DeepSeek is the default documented provider because it exposes an OpenAI-compatible API and has strong code reasoning capability for the target use case. The implementation does not hard-code DeepSeek-only behavior. Users can provide:

- API key
- Base URL
- Model ID

This keeps the application compatible with DeepSeek, OpenAI-compatible gateways, local compatible services, and other providers that follow the same chat completion protocol.

## Context Acquisition

The backend receives a GitHub PR URL and parses:

- Owner
- Repository
- Pull request number

It then uses the GitHub REST API to fetch:

- PR title, author, state, branch information, additions, deletions, changed file count
- Changed file list
- File status
- File additions, deletions, changes
- Patch snippets when GitHub provides them

Public repositories work without a token. Private repositories and higher public rate limits are supported through either:

- Request-scoped GitHub token from the frontend
- `GITHUB_TOKEN` environment variable on the backend

Tokens are not written to source files or browser storage.

## Context Compression

Pull requests can contain large diffs, generated files, lock files, or files where GitHub does not return patch content. Sending everything directly to a model would be slow, expensive, and often less accurate.

The review context builder therefore prioritizes:

- PR metadata
- File list and change sizes
- Available patch snippets
- Per-file patch availability
- Truncation notes

Current limits:

- Maximum patch characters per file: `4000`
- Maximum prompt characters: `20000`

When a file patch is truncated or unavailable, the backend records a limitation note. The frontend exposes context statistics so users can see whether the model had full or partial diff context.

## Review Prompting

The AI prompt asks the model to focus on concrete production risks and changed code. It explicitly instructs the model to:

- Cite evidence from the provided context.
- Avoid inventing files or code.
- Treat weak evidence as low confidence.
- Mark uncertain findings as requiring human review.
- Separate blocking fixes, suggestions, and follow-up items.
- Mention context limitations when patches are missing or truncated.

The output contract is represented as Java records and converted with Spring AI `BeanOutputConverter`. This reduces drift between prompt format, parser expectations, and API response shape.

## False Positive and False Negative Control

The tool is intentionally conservative. It should help reviewers focus attention, not pretend to be a final authority.

Controls for false positives:

- Each risk item must include evidence.
- Risk items without evidence are downgraded to low confidence.
- Unknown or unclear files are marked as requiring human review.
- Generic style preferences are discouraged unless the diff shows concrete maintainability or correctness risk.
- Generated files, documentation-only changes, lock files, and styling-only changes are treated as lower risk unless evidence suggests otherwise.

Controls for false negatives:

- PR metadata and file-level change stats are always included, even when patches are missing.
- Truncation and missing patch notes are surfaced in the result.
- The model is asked to produce follow-up items when context is insufficient.
- The frontend keeps the changed file list visible so users can still inspect files outside the model context.

## Response Speed and User Experience

AI Review can take several seconds. To avoid a frozen interface, the frontend splits the workflow:

1. Start PR information fetching and AI Review generation together.
2. Show the PR information panel as soon as GitHub data is available.
3. Continue AI Review in the background.
4. Let the user open the AI Review tab when ready.

This gives immediate feedback, supports large PRs better, and makes configuration or GitHub API errors easier to understand.

## Error Handling

The backend returns clear API errors for:

- Invalid PR URL
- GitHub API failures
- GitHub rate limits
- Invalid model configuration
- AI Review generation failures

If an API key is missing, the application does not fabricate an AI report. It returns a not-configured result so the frontend can explain what the user needs to provide.

## Security Notes

The project avoids committing secrets. Runtime secrets should be provided through:

- Environment variables for local or deployed backend defaults
- Request-scoped fields for user-provided GitHub tokens or model credentials

Future persistent user profiles should only be added after choosing an encrypted storage strategy.

## Future Extensions

Planned directions:

- Deploy the backend to a cloud server so packaged clients can use built-in AI capability without exposing provider keys in the desktop app.
- Package the frontend as a desktop app with Electron or Tauri.
- Add a production profile with explicit CORS, logging, and secret configuration.
- Add GitHub comment drafting or review submission.
- Add per-file deep-dive review.
- Add model presets for more OpenAI-compatible providers.
- Add caching for GitHub PR data and repeated review requests.
- Add streaming AI Review output if the selected provider supports compatible streaming responses.

