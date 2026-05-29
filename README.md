# AI PR Review Assistant

AI PR Review Assistant is a Spring Boot + React application for analyzing GitHub pull requests with configurable AI models.

## Current Scope

This first project baseline provides:

- Spring Boot backend skeleton with a health endpoint.
- React + Vite frontend skeleton for the review workbench.
- Configuration placeholders for GitHub access and OpenAI-compatible model providers.
- A development roadmap for small PR-based implementation.

Business features such as GitHub PR fetching, private repository token support, DeepSeek review generation, and model switching will be added in separate small PRs.

## Tech Stack

- Java 17
- Spring Boot 3.5.14
- Spring AI 1.1.7
- React
- Vite
- TypeScript

## Local Development

Run backend tests:

```bash
mvn test
```

Run the backend:

```bash
mvn -pl backend spring-boot:run
```

Optional: set `GITHUB_TOKEN` before running the backend to increase GitHub API limits for public PR lookups.

Run the frontend:

```bash
cd frontend
npm install
npm run dev
```

The frontend development server proxies `/api` requests to `http://localhost:8080`.

## Configuration Direction

Private repositories will be supported through a user-provided GitHub token in a later PR. AI providers will be configured as OpenAI-compatible endpoints with `apiKey`, `baseUrl`, and `modelId`, with DeepSeek as the recommended default provider.
