# newbie-feishu

飞书开放平台网关服务 —— 提供 token 获取、API 代理转发、事件/回调 webhook 转发能力。

## 功能特性

- **获取 tenant_access_token** — `GET /api/auth/tenant_access_token`，SDK 内置缓存，自动续期
- **飞书 API 代理转发** — `POST /api/feishu/**`，自动注入 `Authorization` 请求头，无需管理 token
- **WebSocket 长连接** — 基于飞书 SDK 长连接机制接收事件订阅和卡片回调
- **Webhook 转发** — 将接收到的事件和回调以 JSON 格式异步 POST 到配置的目标 URL
- **Docker 一键部署** — 通过环境变量配置，`docker compose up -d` 即可启动

## 快速开始

### 前置条件

1. 在 [飞书开放平台](https://open.feishu.cn/) 创建一个**个人/企业自建应用**
2. 获取应用的 **App ID** 和 **App Secret**
3. 在应用的「事件订阅」中选择**使用长连接接收事件**

### Docker 部署

1. 克隆项目：

```bash
git clone https://github.com/null-object-0000/newbie-feishu.git
cd newbie-feishu
```

2. 创建 `.env` 文件：

```env
FEISHU_APP_ID=cli_xxxxxxxxxxxx
FEISHU_APP_SECRET=xxxxxxxxxxxxxxxxxxxxxxxx
FEISHU_WEBHOOK_URL=http://your-target-webhook-url
```

3. 启动服务：

```bash
docker compose up -d
```

### 源码运行

```bash
# 设置环境变量
export FEISHU_APP_ID=cli_xxxxxxxxxxxx
export FEISHU_APP_SECRET=xxxxxxxxxxxxxxxxxxxxxxxx
export FEISHU_WEBHOOK_URL=http://your-target-webhook-url

# 编译运行
./mvnw spring-boot:run
```

## API 文档

### 获取 tenant_access_token

```
GET /api/auth/tenant_access_token
```

响应示例：

```json
{
  "tenant_access_token": "t-g1xxxxxxxxxxxxxxxxxxxxxxxxxxxxx",
  "expire": 7200
}
```

### 代理转发飞书 API

```
POST /api/feishu/{飞书 API 路径}
```

本服务会将请求转发到 `https://open.feishu.cn/open-apis/{飞书 API 路径}`，自动注入 `Authorization: Bearer {tenant_access_token}` 请求头。暂时仅支持 POST + JSON 模式。

调用示例 — 发送消息：

```bash
curl -X POST http://localhost:8080/api/feishu/im/v1/messages?receive_id_type=open_id \
  -H "Content-Type: application/json" \
  -d '{
    "receive_id": "ou_xxxxxxxxxxxxxxxxxxxxxxxxxx",
    "msg_type": "text",
    "content": "{\"text\": \"Hello from newbie-feishu!\"}"
  }'
```

等价于直接调用飞书 API：

```
POST https://open.feishu.cn/open-apis/im/v1/messages?receive_id_type=open_id
Authorization: Bearer t-g1xxxxxxxxxxxxxxxxxxxxxxxxxxxxx
```

## Webhook 转发格式

当通过长连接收到飞书事件或卡片回调时，服务会将数据 POST 到 `FEISHU_WEBHOOK_URL`。

### 事件转发

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

### 卡片回调转发

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

## 使用场景：Dify 接入

newbie-feishu 可以作为飞书与 [Dify](https://dify.ai) 之间的消息桥梁，实现飞书机器人对接 Dify 的 AI 工作流。

### 架构

```
飞书用户发消息
    ↓
飞书服务端 ──WebSocket长连接──→ newbie-feishu
    ↓
newbie-feishu ──webhook POST──→ Dify Workflow（接收消息、调用 AI）
    ↓
Dify ──POST /api/feishu/im/v1/messages──→ newbie-feishu（自动注入 token）
    ↓
newbie-feishu ──转发──→ 飞书服务端
    ↓
飞书用户收到 AI 回复
```

### 配置步骤

1. **部署 newbie-feishu**，配置飞书应用的 App ID / App Secret

2. **在 Dify 中创建 Workflow**，添加一个 HTTP 触发器（Webhook），获取 Webhook URL

3. **设置 `FEISHU_WEBHOOK_URL`** 指向 Dify 的 Webhook URL：

```env
FEISHU_WEBHOOK_URL=https://api.dify.ai/v1/workflows/run
```

4. **在 Dify Workflow 中添加 HTTP 请求节点**，用于回复飞书用户。请求配置：

   - URL: `http://newbie-feishu:8080/api/feishu/im/v1/messages?receive_id_type=open_id`
   - Method: POST
   - Body:

```json
{
  "receive_id": "{{从 webhook 事件中提取的 open_id}}",
  "msg_type": "text",
  "content": "{\"text\": \"{{AI 生成的回复}}\"}"
}
```

Dify 无需管理飞书的 access_token，newbie-feishu 会自动注入认证信息。

## 注意事项

- 长连接模式下消息处理需在 3 秒内完成，本服务通过异步转发避免超时
- 长连接为集群模式，多实例部署时只有一个随机实例收到消息
- 每个应用最多建立 50 个长连接
- 仅支持**个人/企业自建应用**

## License

[MIT](LICENSE)
