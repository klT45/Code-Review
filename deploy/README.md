# 后端部署文件

`ai-pr-review.service` 是云端后端的 systemd 服务模板。

部署约定：

- 应用目录：`/opt/ai-pr-review`
- Jar 文件：`/opt/ai-pr-review/backend.jar`
- 环境变量：`/etc/ai-pr-review/ai-pr-review.env`
- 默认端口：`19080`

环境变量文件示例：

```bash
DEEPSEEK_API_KEY=your_deepseek_api_key
AI_MODEL_API_KEY=
SERVER_PORT=19080
APP_CORS_ALLOWED_ORIGINS=http://localhost:5173,http://127.0.0.1:5173,http://101.35.244.21
```

API Key 必须只放在服务器环境变量文件中，不提交到 Git。
