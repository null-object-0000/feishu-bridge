# n8n-nodes-newbie-feishu

[newbie-feishu](https://github.com/null-object-0000/newbie-feishu) 网关的 n8n 触发器节点。

接收网关转发的飞书事件（消息、卡片回调等），触发 n8n 工作流。

## 节点

### Newbie Feishu Trigger

Webhook 触发器，接收 newbie-feishu 网关的事件推送。

**配置项：**

| 参数 | 说明 | 默认值 |
|------|------|--------|
| Path | Webhook 监听路径 | `feishu-webhook` |
| Event Type Filter | 按事件类型过滤 | `All Events` |
| Simplify Output | 是否提取关键字段到顶层 | `true` |

**输出字段（Simplify 开启时）：**

| 字段 | 类型 | 说明 |
|------|------|------|
| `type` | string | 事件类别：`event` / `card_action` |
| `event_type` | string | 事件类型，如 `im.message.receive_v1` |
| `timestamp` | number | 时间戳（毫秒） |
| `event_id` | string | 事件 ID（去重用） |
| `sender_id` | object | 发送者 ID（含 open_id、user_id 等） |
| `message_id` | string | 消息 ID |
| `chat_id` | string | 会话 ID |
| `chat_type` | string | 会话类型：`p2p` / `group` |
| `message_type` | string | 消息类型：`text`、`image` 等 |
| `content` | string | 消息内容（JSON 字符串） |
| `create_time` | string | 消息创建时间戳（毫秒） |
| `raw_payload` | object | 完整原始事件 |

## 本地开发

### 方式一：npm run dev（推荐）

```bash
npm install
npm run dev
```

自动启动 n8n 并加载插件，支持热重载。

### 方式二：Docker Compose

```bash
docker compose up --build
```

访问 http://localhost:5678 使用 n8n。

修改代码后重新构建：

```bash
docker compose up --build
```

## 使用方式

1. 在 n8n 中添加 **Feishu Event** 触发器节点
2. 配置 `Path`（默认 `feishu-webhook`）
3. 激活工作流后，将 n8n 的 Webhook 地址添加到 newbie-feishu 网关的 `FEISHU_WEBHOOK_URLS` 中（多个地址用逗号分隔）：

```
http://<n8n-host>:5678/webhook/feishu-webhook
```

## 测试

使用 curl 模拟网关推送：

```bash
curl -X POST http://localhost:5678/webhook-test/feishu-webhook \
  -H "Content-Type: application/json" \
  -d '{
    "type": "event",
    "event_type": "im.message.receive_v1",
    "timestamp": 1740000000000,
    "payload": {
      "header": {
        "event_id": "evt_test_001",
        "event_type": "im.message.receive_v1"
      },
      "event": {
        "sender": {
          "sender_id": {
            "open_id": "ou_test123",
            "user_id": "user_test123"
          }
        },
        "message": {
          "message_id": "msg_test_001",
          "chat_id": "oc_test_chat",
          "chat_type": "p2p",
          "message_type": "text",
          "content": "{\"text\":\"Hello from Feishu\"}",
          "create_time": "1740000000000"
        }
      }
    }
  }'
```
