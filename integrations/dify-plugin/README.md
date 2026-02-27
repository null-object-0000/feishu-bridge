## Feishu Bridge

**Author:** null-object-0000
**Version:** 0.0.1
**Type:** trigger

### Description

飞书桥接器触发插件，配合 [Feishu Bridge](../../README.md) Java 网关使用，将飞书事件转化为 Dify 工作流的输入。

### 事件列表

#### 接收消息（im.message.receive_v1）

机器人接收到用户发送的消息后触发此事件。

> 飞书官方文档：https://open.feishu.cn/document/uAjLw4CM/ukTMukTMukTM/reference/im-v1/message/events/receive

**事件参数**

| 参数 | 说明 | 可选值 | 默认值 |
|------|------|--------|--------|
| 容器类型过滤 | 按消息所在容器类型筛选 | 全部 / 单聊（chat-p2p）/ 群组（chat-group）/ 话题（thread） | 全部 |
| 消息场景过滤 | 按消息场景筛选 | 全部 / 回复 / 普通 | 全部 |

**容器类型判定规则**

- **话题（thread）**：消息包含 `thread_id`，优先级最高
- **单聊（chat-p2p）**：`chat_type` 为 `p2p` 且不包含 `thread_id`
- **群组（chat-group）**：`chat_type` 为 `group` 且不包含 `thread_id`

**消息场景判定规则**

- **回复**：消息包含 `parent_id`
- **普通**：不包含 `parent_id`
