# DeepSeek 安全网关

安卓 APK 可被反编译，不能安全保管具有计费权限的 DeepSeek API Key。本目录提供一个可部署到 Cloudflare Workers 的轻量代理：手机只连接代理，真正的 Key 始终保存在 Cloudflare Secret 中。

## 部署

需要 Node.js、Cloudflare 账号和 DeepSeek API Key。不要把任何真实密钥写入文件、粘贴到应用或提交到 Git。

```bash
cd backend/deepseek-gateway
npm install
npx wrangler login
npx wrangler secret put DEEPSEEK_API_KEY
openssl rand -hex 32
npx wrangler secret put APP_ACCESS_TOKEN
npm run deploy
```

执行 `wrangler secret put` 后，命令行会隐藏输入内容。将 `openssl` 生成的随机字符串粘贴给 `APP_ACCESS_TOKEN`，并妥善保存；它用于阻止公开端点被随意调用，不是 DeepSeek API Key。

部署完成后，在应用“个人信息 → 真实模型”中填写：

- AI 网关完整地址：`https://你的-worker.workers.dev/v1/assistant`
- 应用访问令牌：刚才设置的 `APP_ACCESS_TOKEN`

可访问 `https://你的-worker.workers.dev/health` 检查服务是否在线。默认使用经济、快速的 `deepseek-v4-flash` 非思考模式；可在 `wrangler.toml` 中改为 `deepseek-v4-pro`。

## 密钥轮换

如果 API Key 曾出现在聊天、截图、日志或 Git 历史中，应在 DeepSeek 开放平台立即撤销，再创建新 Key，并重新执行：

```bash
npx wrangler secret put DEEPSEEK_API_KEY
```

## 隐私边界

应用只发送用户在“AI 数据权限”中允许的任务、日记和目标。网关不会主动记录请求正文。若要提供给其他人使用，应进一步加入每用户登录、限流、余额保护和审计能力。
