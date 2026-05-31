# 云端后端与打包说明

本项目支持将 Spring Boot 后端部署在云服务器上，前端打包后的应用通过固定 API 地址访问云端服务。

## 前端 API 地址

开发环境默认使用 Vite 代理：

```powershell
npm run dev
```

打包为连接云端后端的版本时，在 `frontend` 目录配置：

```powershell
$env:VITE_API_BASE_URL="http://101.35.244.21:8080"
npm run build
```

构建产物会将 `/api/...` 请求发送到 `http://101.35.244.21:8080/api/...`。

## 后端环境变量

后端部署时不要把 API Key 写入仓库文件，使用环境变量注入：

```bash
export DEEPSEEK_API_KEY="your_deepseek_api_key"
export APP_CORS_ALLOWED_ORIGINS="http://localhost:5173,http://127.0.0.1:5173,http://101.35.244.21"
java -jar backend-0.1.0-SNAPSHOT.jar
```

`APP_CORS_ALLOWED_ORIGINS` 用于允许打包后的前端或本地开发页面访问云端 API。

## 健康检查

后端启动后可以访问：

```text
http://101.35.244.21:8080/api/health
```

如果返回 `UP`，说明服务已经可用。
