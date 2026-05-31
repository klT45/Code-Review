# 云端后端与打包说明

本项目支持将 Spring Boot 后端部署在云服务器上，前端打包后的应用通过固定 API 地址访问云端服务。

## 前端 API 地址

开发环境默认使用 Vite 代理：

```powershell
npm run dev
```

生产构建默认连接云端后端：

```powershell
npm run build
```

如果需要临时覆盖 API 地址，可以在 `frontend` 目录配置：

```powershell
$env:VITE_API_BASE_URL="http://101.35.244.21/ai-pr-review-api"
npm run build
```

构建产物会将 `/api/...` 请求发送到 `http://101.35.244.21/ai-pr-review-api/api/...`。

## Windows 桌面打包

前端已提供 Electron 打包脚手架：

```powershell
cd frontend
npm install
npm run package:win
```

生成文件位于 `frontend/release`。首次执行会下载 Electron runtime 和打包工具依赖，需要网络能够访问 npm/Electron 下载源。

打包后的桌面应用默认使用云端后端，因此用户打开应用后可以直接使用内置 DeepSeek AI Review 能力；只有需要更换模型、Base URL 或 API Key 时，才需要进入模型设置面板。

## 后端环境变量

后端部署时不要把 API Key 写入仓库文件，使用环境变量注入：

```bash
export DEEPSEEK_API_KEY="your_deepseek_api_key"
export APP_CORS_ALLOWED_ORIGINS="http://localhost:5173,http://127.0.0.1:5173,http://101.35.244.21"
export SERVER_PORT=19080
java -jar backend-0.1.0-SNAPSHOT.jar
```

`APP_CORS_ALLOWED_ORIGINS` 用于允许打包后的前端或本地开发页面访问云端 API。

当前服务器上后端监听 `19080`，公网通过 nginx 路径转发访问：

```nginx
location /ai-pr-review-api/ {
    proxy_pass http://127.0.0.1:19080/;
    proxy_buffering off;
    proxy_cache off;
    proxy_read_timeout 300s;
}
```

## 健康检查

后端启动后可以访问：

```text
http://101.35.244.21/ai-pr-review-api/api/health
```

如果返回 `UP`，说明服务已经可用。
