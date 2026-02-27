# Feishu Bridge

飞书机器人桥接服务。通过飞书 WebSocket 长连接接收消息，可以转发给外部系统，也可以直接对接 AI 模型流式回复。

## 两种使用模式

### 模式一：Webhook 转发

把飞书收到的消息/事件转发到你指定的 URL，适合对接 n8n、Dify Workflow 等外部自动化平台。同时提供飞书 API 代理，自动注入 token，外部系统回调时不需要自己管理认证。

```
飞书用户发消息 → 飞书服务端 → [WebSocket] → Feishu Bridge → [HTTP POST] → 你的 Webhook
```

### 模式二：Streaming AI 自动回复

直接对接 AI 模型 API，收到飞书消息后自动调用模型，流式输出回复到飞书卡片。支持所有 OpenAI 兼容接口（OpenAI、DeepSeek、Moonshot、Ollama 等）和 Dify。

```
飞书用户发消息 → Feishu Bridge → AI 模型 API → 流式回复 → 飞书卡片实时更新
```

两种模式可以同时开启。

## 快速开始

### 1. 创建飞书应用

1. 打开 [飞书开放平台](https://open.feishu.cn/)，创建一个**企业自建应用**
2. 记下 **App ID** 和 **App Secret**
3. 进入「事件订阅」，选择 **使用长连接接收事件**
4. 如果要用 AI 自动回复，还需要在「权限管理」中开通 **消息读写** 相关权限

### 2. 配置 `.env`

```env
# 必填 —— 飞书应用凭证
FEISHU_APP_ID=cli_xxxxxxxxxxxx
FEISHU_APP_SECRET=xxxxxxxxxxxxxxxxxxxxxxxx

# 模式一：Webhook 转发（不用可以留空）
FEISHU_WEBHOOK_URLS=http://your-webhook-url

# 模式二：Streaming AI 自动回复（不用可以不写）
STREAMING_ENABLED=true
STREAMING_PROVIDER=openai
STREAMING_OPENAI_API_URL=https://api.openai.com/v1/chat/completions
STREAMING_OPENAI_API_KEY=sk-xxxx
STREAMING_OPENAI_MODEL=gpt-4o
STREAMING_OPENAI_SYSTEM_PROMPT=你是一个有帮助的助手
```

### 3. 启动

**Docker（推荐）：**

```bash
git clone https://github.com/null-object-0000/feishu-bridge.git
cd feishu-bridge
# 编辑 .env 填入配置
docker compose up -d
```

**源码运行：**

```bash
# 设置环境变量（或创建 .env）
./mvnw spring-boot:run
```

启动后看到 `飞书 WebSocket 长连接已启动` 就可以在飞书里给机器人发消息了。

## 环境变量一览

| 变量 | 必填 | 默认值 | 说明 |
|------|------|--------|------|
| `FEISHU_APP_ID` | 是 | | 飞书应用 App ID |
| `FEISHU_APP_SECRET` | 是 | | 飞书应用 App Secret |
| `FEISHU_WEBHOOK_URLS` | 否 | | Webhook 目标地址，多个用逗号分隔 |
| `STREAMING_ENABLED` | 否 | `false` | 是否开启 AI 流式自动回复 |
| `STREAMING_PROVIDER` | 否 | `openai` | AI 提供商：`openai` 或 `dify` |
| `STREAMING_OPENAI_API_URL` | 否 | OpenAI 官方地址 | OpenAI 兼容接口地址 |
| `STREAMING_OPENAI_API_KEY` | 否 | | API Key |
| `STREAMING_OPENAI_MODEL` | 否 | `gpt-4o` | 模型名称 |
| `STREAMING_OPENAI_SYSTEM_PROMPT` | 否 | | 系统提示词 |
| `STREAMING_DIFY_API_URL` | 否 | | Dify API 地址 |
| `STREAMING_DIFY_API_KEY` | 否 | | Dify API Key |
| `STREAMING_DIFY_APP_TYPE` | 否 | `chat` | Dify 应用类型：`chat` 或 `workflow` |

## Webhook 转发格式

收到飞书事件后，会 POST 以下 JSON 到你配置的所有 Webhook URL：

**消息事件：**

```json
{
  "type": "event",
  "event_type": "im.message.receive_v1",
  "timestamp": 1740000000000,
  "payload": {
    "sender": { "sender_id": { "open_id": "ou_xxx" } },
    "message": { "message_id": "om_xxx", "content": "{\"text\":\"你好\"}" }
  }
}
```

**卡片回调：**

```json
{
  "type": "card_action",
  "event_type": "card_action_trigger",
  "timestamp": 1740000000000,
  "payload": {
    "operator": { "open_id": "ou_xxx" },
    "action": { "value": { "key": "value" }, "tag": "button" }
  }
}
```

## API 代理

提供飞书 API 代理，自动注入认证 token，方便 Webhook 下游系统回调飞书：

```
POST http://localhost:9811/api/feishu/{飞书 API 路径}
```

例如发送消息：

```bash
curl -X POST 'http://localhost:9811/api/feishu/im/v1/messages?receive_id_type=open_id' \
  -H 'Content-Type: application/json' \
  -d '{"receive_id":"ou_xxx","msg_type":"text","content":"{\"text\":\"Hello!\"}"}'
```

不需要传 Authorization，Feishu Bridge 会自动处理。

## 集成插件

| 平台 | 插件 | 说明 |
|------|------|------|
| n8n | [Feishu Bridge Trigger](integrations/n8n-plugin/) | 接收飞书事件，触发 n8n 工作流 |
| Dify | [Feishu Bridge](integrations/dify-plugin/) | 接收飞书事件，触发 Dify 工作流 |

## 注意事项

- 长连接模式下消息处理需在 3 秒内完成，本服务通过异步处理避免超时
- 多实例部署时只有一个随机实例收到消息
- 每个应用最多建立 50 个长连接
- 仅支持**企业自建应用**

## License

[MIT](LICENSE)
