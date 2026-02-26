from werkzeug import Request, Response

from dify_plugin.entities.trigger import EventDispatch, Subscription
from dify_plugin.interfaces.trigger import Trigger

EVENT_TYPE_MAP = {
    "im.message.receive_v1": "message_receive_v1",
}


class FeishuBridgeTrigger(Trigger):
    def _dispatch_event(self, subscription: Subscription, request: Request) -> EventDispatch:
        body: dict = request.get_json(silent=True) or {}
        event_type = body.get("event_type", "")

        event_name = EVENT_TYPE_MAP.get(event_type)
        if not event_name:
            event_name = event_type.rsplit(".", 1)[-1] if "." in event_type else event_type

        return EventDispatch(
            events=[event_name] if event_name else [],
            response=Response('{"status":"ok"}', status=200, mimetype="application/json"),
        )
