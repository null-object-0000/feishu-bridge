from collections.abc import Mapping
from typing import Any

from werkzeug import Request

from dify_plugin.entities.trigger import Variables
from dify_plugin.interfaces.trigger import Event


class MessageReceiveV1Event(Event):
    """Handle im.message.receive_v1 events from the Feishu Bridge gateway.

    The gateway forwards the complete Feishu event inside a wrapper:
    {type, event_type, timestamp, payload}

    The `payload` field contains the original Feishu event body with
    `header` and `event` sections.
    """

    def _on_event(
        self,
        request: Request,
        parameters: Mapping[str, Any],
        payload: Mapping[str, Any],
    ) -> Variables:
        body: dict = request.get_json(silent=True) or {}
        gateway_payload: dict = body.get("payload", {})

        event: dict = gateway_payload.get("event", gateway_payload)
        header: dict = gateway_payload.get("header", {})

        sender: dict = event.get("sender", {})
        message: dict = event.get("message", {})

        return Variables(
            variables={
                "sender_id": sender.get("sender_id", {}),
                "message_id": message.get("message_id", ""),
                "chat_id": message.get("chat_id", ""),
                "chat_type": message.get("chat_type", ""),
                "message_type": message.get("message_type", ""),
                "content": message.get("content", ""),
                "create_time": message.get("create_time", ""),
                "event_id": header.get("event_id", ""),
                "raw_payload": gateway_payload,
            }
        )
