from werkzeug import Request, Response

from dify_plugin.entities.trigger import EventDispatch, Subscription
from dify_plugin.interfaces.trigger import Trigger

# newbie-feishu 网关转发的事件类型到插件事件名的映射
# 飞书事件类型格式: im.message.receive_v1 -> 插件事件名: message_receive_v1
EVENT_TYPE_MAP = {
    "im.message.receive_v1": "message_receive_v1",
}


class NewbieFeishuTrigger(Trigger):
    def _dispatch_event(self, subscription: Subscription, request: Request) -> EventDispatch:
        body: dict = request.get_json(silent=True) or {}
        event_type = body.get("event_type", "")

        event_name = EVENT_TYPE_MAP.get(event_type)
        if not event_name:
            # 尝试自动推导: a.b.c -> c, 直接字符串 -> 原样
            event_name = event_type.rsplit(".", 1)[-1] if "." in event_type else event_type

        return EventDispatch(
            events=[event_name] if event_name else [],
            response=Response('{"status":"ok"}', status=200, mimetype="application/json"),
        )
