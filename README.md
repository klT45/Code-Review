# AI PR Review Assistant

AI PR Review Assistant is a Spring Boot + Spring AI + React workbench for reviewing GitHub pull requests with configurable AI models. A user enters a GitHub PR URL, the backend fetches PR metadata and changed files through the GitHub API, builds a compressed review context, and asks an OpenAI-compatible model to generate a structured review.

## Features

- Analyze public GitHub pull requests by URL.
- Analyze private repository pull requests with a user-provided GitHub token.
- Fetch PR metadata, changed files, patch snippets, and change statistics.
- Build a bounded review context with per-file patch truncation and context limitation notes.
- Use Spring AI with OpenAI-compatible model endpoints.
- Default model profile for DeepSeek, with custom `apiKey`, `baseUrl`, and `modelId` support.
- Show PR information first while AI Review runs in the background.
- Display AI Review as structured modules: summary, risk items, required actions, suggestions, follow-ups, limitations, and Markdown.
- Copy a Markdown review comment for GitHub.

## Tech Stack

- Java 17
- Spring Boot 3.5.14
- Spring AI 1.1.7
- React 19
- Vite 6
- TypeScript

## Local Development

### Backend

Run backend tests:

```bash
cd backend
mvn test
```

Run the backend:

```bash
cd backend
mvn spring-boot:run
```

To enable AI Review with the default DeepSeek profile, set an environment variable before starting the backend:

```bash
set DEEPSEEK_API_KEY=your_deepseek_api_key
mvn spring-boot:run
```

On PowerShell:

```powershell
$env:DEEPSEEK_API_KEY="your_deepseek_api_key"
mvn spring-boot:run
```

Optional GitHub API token for higher public rate limits or private repositories:

```powershell
$env:GITHUB_TOKEN="your_github_token"
mvn spring-boot:run
```

The frontend also supports entering a GitHub token for a single analysis request. Request-scoped tokens are kept in page memory and are not written to the repository or browser storage.

### Frontend

Install dependencies and start the workbench:

```bash
cd frontend
npm install
npm run dev
```

Open:

```text
http://localhost:5173
```

The Vite development server proxies `/api` requests to `http://localhost:8080`.

## Model Configuration

The default provider is DeepSeek:

- Base URL: `https://api.deepseek.com`
- Model ID: `deepseek-chat`
- API key env: `DEEPSEEK_API_KEY`

Users can switch to another OpenAI-compatible model by providing:

- API Key
- Base URL
- Model ID

The backend resolves model settings per request. If no API key is available, the application returns a clear configuration message instead of generating a fake review.

## Suggested Demo Flow

1. Start the backend with `DEEPSEEK_API_KEY`.
2. Start the frontend with `npm run dev`.
3. Open `http://localhost:5173`.
4. Paste a public GitHub PR URL, for example:

```text
https://github.com/spring-projects/spring-ai/pull/3367
```

5. Click analyze.
6. Review the PR information panel while AI Review runs in the background.
7. Open the AI Review tab and copy the Markdown result if needed.

## Verification

Backend:

```bash
cd backend
mvn test
```

Frontend:

```bash
cd frontend
npm run build
```

## Documentation

- [Architecture Notes](docs/architecture.md)
- [Design Notes](docs/design.md)
