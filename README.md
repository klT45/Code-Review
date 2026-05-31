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

## 本地启动

### 后端

运行后端测试：

```bash
cd backend
mvn test
```

启动后端：

```bash
cd backend
mvn spring-boot:run
```

如果需要启用默认 DeepSeek AI Review 能力，请在启动后端前设置环境变量：

```bash
set DEEPSEEK_API_KEY=your_deepseek_api_key
mvn spring-boot:run
```

PowerShell 示例：

```powershell
$env:DEEPSEEK_API_KEY="your_deepseek_api_key"
mvn spring-boot:run
```

如果需要提升 GitHub API 限流额度，或通过后端默认 Token 访问私有仓库，可以设置：

```powershell
$env:GITHUB_TOKEN="your_github_token"
mvn spring-boot:run
```

前端也支持在页面中输入 GitHub Token，用于单次 PR 分析请求。该 Token 只保存在当前页面内存中，不会写入仓库、日志或浏览器存储。

### 前端

安装依赖并启动工作台：

```bash
cd frontend
npm install
npm run dev
```

打开：

```text
http://localhost:5173
```

Vite 开发服务器会将 `/api` 请求代理到 `http://localhost:8080`。

### 打包版本

生产构建默认连接云端后端：

```bash
cd frontend
npm run build
```

Windows 桌面打包命令：

```bash
cd frontend
npm run package:win
```

打包后的应用默认使用云端 DeepSeek AI Review 能力。用户只有在需要更换模型、Base URL 或 API Key 时，才需要进入模型设置面板。

## 模型配置

默认模型提供方是 DeepSeek：

- Base URL：`https://api.deepseek.com`
- Model ID：`deepseek-chat`
- API Key 环境变量：`DEEPSEEK_API_KEY`

页面中的模型设置面板支持切换 OpenAI-compatible 模型。用户可以填写：

- API Key
- Base URL
- Model ID

后端会按请求解析模型配置。如果没有可用 API Key，接口会返回未配置提示，前端会展示配置说明。

## 演示流程

1. 设置 `DEEPSEEK_API_KEY` 并启动后端。
2. 启动前端。
3. 打开 `http://localhost:5173`。
4. 输入一个公开 GitHub PR 链接，例如：

```text
https://github.com/spring-projects/spring-ai/pull/3367
```

5. 点击分析。
6. 先查看 PR 信息、变更文件和上下文统计。
7. 打开 AI Review 视图，查看风险项和 Review 建议。
8. 需要时复制 Markdown，粘贴到 GitHub Review 评论中。

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

## 设计文档

- [架构说明](docs/architecture.md)
- [设计说明](docs/design.md)
- [云端后端与打包说明](docs/deployment.md)
