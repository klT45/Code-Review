# 架构说明

## 项目目标

AI PR Review 助手面向 GitHub Pull Request 评审场景。用户输入 PR 链接后，系统自动获取代码变更上下文，并通过 AI 生成结构化 Review 结果，帮助开发者快速理解变更、识别风险代码、整理 Review 建议。

架构设计围绕 `1.txt` 中的核心要求展开：

- 支持 PR 变更总结。
- 支持风险代码识别。
- 支持 Review 建议生成。
- 关注上下文理解、误报与漏报控制、响应速度和使用体验。
- 使用 Java 后端技术栈，并充分利用 Spring AI 接入模型。

## 后端模块

后端是 Spring Boot 服务，目前按以下边界组织：

- `github`：解析 GitHub PR URL，通过 GitHub REST API 获取公开或 Token 授权的私有 PR 数据。
- `review.context`：从 PR 元信息和变更文件中构建模型上下文，并负责 patch 截断和上下文统计。
- `review.ai`：通过 Spring AI 调用模型，解析结构化 AI Review 结果，并进行质量校验。
- `ai`：解析 OpenAI-compatible 模型配置，包括提供方、API Key、Base URL 和 Model ID。
- `api`：向 React 工作台暴露 REST 接口。

## 主要接口

- `GET /api/health`：服务健康检查。
- `GET /api/model-config`：获取模型提供方配置和 API Key 可用状态。
- `POST /api/model-config/resolve`：校验用户输入的模型配置。
- `POST /api/pull-requests/summary/basic`：只获取 PR 基础信息、文件列表和上下文统计，不等待 AI。
- `POST /api/pull-requests/review`：基于 PR 上下文生成 AI Review。
- `POST /api/pull-requests/review/stream`：以 `text/event-stream` 返回 AI Review 增量输出和最终结构化结果。
- `POST /api/pull-requests/summary`：一次性获取 PR 信息和 AI Review。

## AI Review 流程

1. 解析用户输入的 GitHub PR 链接。
2. 通过 GitHub REST API 获取 PR 元信息。
3. 获取变更文件列表和 patch 片段。
4. 构建 Review 上下文，控制单文件 patch 长度和整体 prompt 长度。
5. 解析当前请求使用的模型配置。
6. 使用 Spring AI 调用 OpenAI-compatible Chat Model。
7. 使用 Spring AI `BeanOutputConverter` 将模型输出约束为结构化结果。
8. 使用质量校验层补充限制信息、标准化风险等级和置信度。
9. 返回适合前端模块化展示和 Markdown 复制的响应。

## 前端结构

前端是 React 工作台，而不是营销页。首页直接提供可用的 PR 分析流程：

- PR URL 输入。
- GitHub Token 设置，用于私有仓库或更高 API 限流额度。
- 模型设置，用于切换 API Key、Base URL、Model ID。
- 分析按钮、加载状态和错误提示。
- PR 信息弹窗，展示基础信息、变更文件和上下文统计。
- AI Review 视图，展示总结、风险项、必须修改、建议优化、后续事项、判断限制和 Markdown。

前端会同时发起 PR 基础信息请求和 AI Review 流式请求。PR 信息先返回并展示，AI Review 的增量文本会持续出现在生成面板中，最终结果返回后再切换为模块化 Review。

## 安全与凭据

项目不提交任何真实密钥。当前支持两类凭据输入：

- 后端环境变量，例如 `DEEPSEEK_API_KEY`、`GITHUB_TOKEN`。
- 前端请求级输入，例如单次分析使用的 GitHub Token 或模型 API Key。

前端输入的 Token 和 API Key 只保存在页面内存中，不写入浏览器存储。后续如果增加用户配置持久化，需要先设计加密存储方案。

## PR 开发策略

项目按小 PR 迭代：

- 每个 PR 只做一个明确功能或文档改动。
- 主分支每次合并后保持可运行状态。
- PR 描述包含标题、功能描述、实现思路和测试方式。
- `1.txt`、计划文件、本地日志和构建产物不进入 PR。
