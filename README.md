# AI PR Review 助手

AI PR Review 助手是一个基于 `Spring Boot + Spring AI + React` 的代码评审工作台，用来帮助开发者提升 GitHub Pull Request 的 Review 效率与质量。用户输入 GitHub PR 链接后，系统会自动获取 PR 信息、变更文件和 diff 上下文，再调用可配置的大模型生成结构化 Review 结果。

项目目标不是替代人工 Review，而是辅助开发者更快理解变更、发现风险、生成可复制的 Review 建议。

## 当前功能

- 支持输入公开 GitHub PR 链接进行分析。
- 支持通过 GitHub Token 获取私有仓库 PR。
- 通过 GitHub REST API 获取 PR 标题、作者、分支、变更规模、文件列表和 patch。
- 构建 Review 上下文，对超长 patch 做文件级截断，并记录上下文限制。
- 使用 Spring AI 接入 OpenAI-compatible 模型。
- 默认提供 DeepSeek 模型配置，同时支持用户输入 `API Key`、`Base URL`、`Model ID` 切换模型。
- 点击分析后先展示 PR 基础信息，同时以流式方式展示 AI Review 生成过程，减少页面长时间静止等待。
- 以模块化方式展示 AI Review：变更总结、风险代码识别、必须修改、建议优化、后续事项、判断限制和 Markdown。
- 支持复制 Markdown，方便粘贴到 GitHub Review 评论中。
- 当 API Key 缺失时给出清晰配置提示，不生成虚假的 AI 报告。

## 技术栈

- Java 17
- Spring Boot 3.5.14
- Spring AI 1.1.7
- React 19
- Vite 6
- TypeScript

## 架构概览

### 后端模块

后端是 Spring Boot 服务，按以下边界组织：

- `github`：解析 GitHub PR URL，通过 GitHub REST API 获取公开或 Token 授权的私有 PR 数据。
- `review.context`：从 PR 元信息和变更文件中构建模型上下文，并负责 patch 截断和上下文统计。
- `review.ai`：通过 Spring AI 调用模型，解析结构化 AI Review 结果，并进行质量校验。
- `ai`：解析 OpenAI-compatible 模型配置，包括提供方、API Key、Base URL 和 Model ID。
- `api`：向 React 工作台暴露 REST 接口。

### 主要接口

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/health` | 服务健康检查 |
| GET | `/api/model-config` | 获取模型提供方配置和 API Key 可用状态 |
| POST | `/api/model-config/resolve` | 校验用户输入的模型配置 |
| POST | `/api/pull-requests/summary/basic` | 获取 PR 基础信息、文件列表和上下文统计 |
| POST | `/api/pull-requests/review` | 基于 PR 上下文生成 AI Review |
| POST | `/api/pull-requests/review/stream` | 以 SSE 流式返回 AI Review 增量输出和最终结果 |
| POST | `/api/pull-requests/summary` | 一次性获取 PR 信息和 AI Review |

### AI Review 流程

1. 解析用户输入的 GitHub PR 链接。
2. 通过 GitHub REST API 获取 PR 元信息。
3. 获取变更文件列表和 patch 片段。
4. 构建 Review 上下文，控制单文件 patch 长度和整体 prompt 长度。
5. 解析当前请求使用的模型配置。
6. 使用 Spring AI 调用 OpenAI-compatible Chat Model。
7. 使用 Spring AI `BeanOutputConverter` 将模型输出约束为结构化结果。
8. 使用质量校验层补充限制信息、标准化风险等级和置信度。
9. 返回适合前端模块化展示和 Markdown 复制的响应。

### 前端结构

前端是 React 工作台，首页直接提供可用的 PR 分析流程：

- PR URL 输入。
- GitHub Token 设置，用于私有仓库或更高 API 限流额度。
- 模型设置，用于切换 API Key、Base URL、Model ID。
- 分析按钮、加载状态和错误提示。
- PR 信息弹窗，展示基础信息、变更文件和上下文统计。
- AI Review 视图，展示总结、风险项、必须修改、建议优化、后续事项、判断限制和 Markdown。

前端会同时发起 PR 基础信息请求和 AI Review 流式请求。PR 信息先返回并展示，AI Review 的增量文本会持续出现在生成面板中，最终结果返回后再切换为模块化 Review。

## 设计说明

### 模型选择

后端使用 Spring AI 接入大模型：

- 项目技术栈以 Java 和 Spring Boot 为主，Spring AI 与后端结构天然契合。
- Spring AI 提供模型调用、Prompt 组织和结构化输出相关能力，减少手写适配代码。
- 后续扩展其他模型时，可以保留统一的服务边界。

默认模型提供方是 DeepSeek：

- DeepSeek 提供 OpenAI-compatible API，容易接入 Spring AI 的 OpenAI 模型客户端。
- `deepseek-chat` 适合作为代码评审模型。
- 成本和可用性适合演示和个人开发场景。

同时，项目没有把能力绑定到 DeepSeek。前端模型设置面板允许用户填写 API Key、Base URL、Model ID，支持其他 OpenAI-compatible 模型服务。

### 上下文压缩策略

PR diff 可能非常大，直接全部发送给模型会导致响应慢、成本高、模型容易忽略重要变更。当前策略：

- 优先保留 PR 标题、作者、分支、变更规模。
- 保留完整文件列表和每个文件的变更统计。
- 对单文件 patch 设置最大字符数（默认 4000）。
- 对整体 prompt 设置最大字符数（默认 20000）。
- 对被截断或缺失 patch 的文件记录限制说明。

前端会展示上下文字符数、可用 patch 数量和截断文件数量，让用户了解 AI 的判断基础。

### AI Review 输出结构

AI Review 输出不是自由文本，而是结构化结果：

- `summary`：PR 变更总结。
- `riskItems`：风险项列表（含严重级别、文件路径、证据、影响、置信度、是否需人工确认）。
- `requiredActions`：合并前必须处理的问题。
- `suggestions`：建议优化项。
- `followUpItems`：后续可跟进事项。
- `limitations`：判断限制。
- `markdown`：可复制到 GitHub 的 Review 评论。

后端使用 Java record 定义输出结构，并通过 Spring AI `BeanOutputConverter` 生成结构化输出约束，减少 Prompt、解析器和接口响应之间的格式漂移。

### 误报与漏报控制

为了降低误报：

- Prompt 要求每个风险项必须引用上下文证据。
- 不允许模型编造文件或代码。
- 证据不足时置信度降为 low，文件路径不明确时标记为需人工确认。
- 对纯文档、样式、lock 文件、生成文件等变更默认降低风险判断。
- 后端质量校验层标准化严重级别和置信度，补充缺失证据的标记。

为了减少漏报：

- 即使 patch 缺失，也保留文件名、状态和变更规模。
- 被截断或缺失的上下文进入 `limitations`。
- 前端始终展示完整变更文件列表，方便人工继续检查。
- AI 可以输出后续事项，提醒 Reviewer 对上下文不足的部分做人工复核。

### 响应速度与使用体验

AI Review 生成可能需要数秒甚至更久。前端采用分阶段交互：

1. 用户点击分析。
2. 前端同时发起 PR 基础信息请求和 AI Review 流式请求。
3. PR 基础信息先返回时，立即弹出 PR 信息面板。
4. 用户可以先查看标题、作者、文件列表和上下文统计。
5. AI Review 在后台继续生成，模型增量输出实时展示。
6. 最终结构化结果返回后，页面自动切换为模块化 Review 展示。

### 错误处理

系统对常见错误给出明确反馈：PR URL 无效、PR 不存在或无法访问、GitHub API 限流、Token 缺失或无权限、模型配置不完整、AI Review 返回非结构化内容、AI Review 调用失败。当 API Key 缺失时，系统不会生成假报告，而是返回未配置状态。

### 安全设计

- DeepSeek API Key 推荐通过后端环境变量注入。
- GitHub Token 可以通过后端环境变量或前端请求级输入提供。
- 前端输入的 Token 和模型 Key 只保存在当前页面内存中，不写入仓库、日志或浏览器存储。
- 仓库文档只提供占位符，不包含真实 Key。
- 打包后的客户端默认连接云端后端，API Key 只存在于服务端，不会打进客户端包。

## 本地启动

### 后端

```bash
cd backend
mvn test        # 运行测试
mvn spring-boot:run      # 启动后端
```

启用默认 DeepSeek AI Review 能力：

```powershell
$env:DEEPSEEK_API_KEY="your_deepseek_api_key"
mvn spring-boot:run
```

提升 GitHub API 限流额度或访问私有仓库：

```powershell
$env:GITHUB_TOKEN="your_github_token"
mvn spring-boot:run
```

### 前端

```bash
cd frontend
npm install
npm run dev
```

打开 `http://localhost:5173`。Vite 开发服务器会将 `/api` 请求代理到 `http://localhost:8080`。

### 打包版本

生产构建默认连接云端后端：

```bash
cd frontend
npm run build
```

Windows 桌面便携版打包：

```bash
cd frontend
npm install
npm run package:win
```

生成文件位于 `frontend/release/win-unpacked`，启动 `AI PR Review 助手.exe` 即可使用。首次执行会下载 Electron runtime（约 180MB）和打包工具依赖，需要网络可达。

打包后的桌面应用默认使用云端 DeepSeek AI Review 能力，无需额外配置。用户只有在需要更换模型、Base URL 或 API Key 时，才需要打开模型设置面板进行修改。

## 模型配置

默认模型提供方是 DeepSeek：

- Base URL：`https://api.deepseek.com`
- Model ID：`deepseek-chat`
- API Key 环境变量：`DEEPSEEK_API_KEY`

页面中的模型设置面板支持切换 OpenAI-compatible 模型。用户可以填写 API Key、Base URL、Model ID。后端会按请求解析模型配置。如果没有可用 API Key，接口会返回未配置提示，前端会展示配置说明。

## 演示流程

1. 设置 `DEEPSEEK_API_KEY` 并启动后端。
2. 启动前端，打开 `http://localhost:5173`。
3. 输入一个公开 GitHub PR 链接，例如：
   `https://github.com/spring-projects/spring-ai/pull/3367`
4. 点击分析。
5. 先查看 PR 信息、变更文件和上下文统计。
6. 打开 AI Review 视图，查看风险项和 Review 建议。
7. 需要时复制 Markdown，粘贴到 GitHub Review 评论中。

## 验证命令

后端：

```bash
cd backend
mvn test
```

前端：

```bash
cd frontend
npm run build
```

## 后续扩展方向

- 桌面端打包完善：优化 Electron 打包流程，支持自动更新。
- GitHub 评论回写：将 Markdown Review 草稿提交到 PR 评论区。
- 文件级深度 Review：允许用户点击某个文件单独发起更细粒度分析。
- 多模型预设：增加更多 OpenAI-compatible 服务的默认配置。
- Review 历史：保存用户本地分析记录，需先设计安全存储策略。
