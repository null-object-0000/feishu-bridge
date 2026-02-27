from collections.abc import Mapping
from typing import Any

from werkzeug import Request

from dify_plugin.entities.trigger import Variables
from dify_plugin.interfaces.trigger import Event
from dify_plugin.errors.trigger import EventIgnoreError


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
        message: dict = event.get("message", {})

        container_filter = parameters.get("container_type", "")
        if container_filter:
            has_thread = bool(message.get("thread_id"))
            chat_type = message.get("chat_type", "")
            if container_filter == "thread" and not has_thread:
                raise EventIgnoreError()
            elif container_filter == "chat-p2p" and (chat_type != "p2p" or has_thread):
                raise EventIgnoreError()
            elif container_filter == "chat-group" and (chat_type != "group" or has_thread):
                raise EventIgnoreError()

        scene_filter = parameters.get("message_scene", "")
        if scene_filter:
            is_reply = bool(message.get("parent_id"))
            scene = "reply" if is_reply else "normal"
            if scene != scene_filter:
                raise EventIgnoreError()

        return Variables(
            variables={
                "sender": event.get("sender", {}),
                "message": message,
            }
        )
