package io.github.feishu.bridge.service;

import com.lark.oapi.core.request.EventReq;
import com.lark.oapi.core.utils.Jsons;
import com.lark.oapi.event.CustomEventHandler;
import com.lark.oapi.event.EventDispatcher;
import com.lark.oapi.event.cardcallback.P2CardActionTriggerHandler;
import com.lark.oapi.event.cardcallback.model.CallBackToast;
import com.lark.oapi.event.cardcallback.model.P2CardActionTrigger;
import com.lark.oapi.event.cardcallback.model.P2CardActionTriggerResponse;
import io.github.feishu.bridge.config.FeishuProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Service;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class FeishuWsService implements SmartLifecycle {

    private final FeishuProperties feishuProperties;
    private final WebhookForwardService webhookForwardService;

    @Autowired(required = false)
    private StreamingReplyService streamingReplyService;

    private volatile com.lark.oapi.ws.Client wsClient;
    private volatile boolean running = false;

    @Override
    public void start() {
        if (feishuProperties.getAppId() == null || feishuProperties.getAppId().isBlank()) {
            log.warn("feishu.app-id 未配置，跳过 WebSocket 长连接");
            return;
        }

        CustomEventHandler catchAllHandler = new CustomEventHandler() {
            @Override
            @SuppressWarnings("unchecked")
            public void handle(EventReq event) throws Exception {
                String eventData = new String(event.getBody(), StandardCharsets.UTF_8);

                String eventType = "unknown";
                try {
                    Map<String, Object> parsed = Jsons.DEFAULT.fromJson(eventData, Map.class);
                    Map<String, Object> header = (Map<String, Object>) parsed.get("header");
                    if (header != null && header.get("event_type") != null) {
                        eventType = (String) header.get("event_type");
                    }
                } catch (Exception ignored) {
                }

                log.info("收到事件: type={}", eventType);
                Object payload = parseJsonSafely(eventData);
                webhookForwardService.forwardEvent(eventType, payload);

                if ("im.message.receive_v1".equals(eventType) && streamingReplyService != null) {
                    tryStreamingReply(eventData);
                }
            }
        };

        EventDispatcher eventDispatcher = EventDispatcher.newBuilder("", "")
                .onP2CardActionTrigger(new P2CardActionTriggerHandler() {
                    @Override
                    public P2CardActionTriggerResponse handle(P2CardActionTrigger event) throws Exception {
                        String eventJson = Jsons.DEFAULT.toJson(event.getEvent());
                        log.info("收到卡片回调: {}", eventJson);

                        Object payload = parseJsonSafely(eventJson);
                        webhookForwardService.forwardCardAction(payload);

                        P2CardActionTriggerResponse resp = new P2CardActionTriggerResponse();
                        CallBackToast toast = new CallBackToast();
                        toast.setType("info");
                        toast.setContent("已收到");
                        resp.setToast(toast);
                        return resp;
                    }
                })
                .build();

        installCatchAllHandler(eventDispatcher, catchAllHandler);

        wsClient = new com.lark.oapi.ws.Client.Builder(
                feishuProperties.getAppId(),
                feishuProperties.getAppSecret()
        ).eventHandler(eventDispatcher).build();

        wsClient.start();
        running = true;
        log.info("飞书 WebSocket 长连接已启动");
    }

    /**
     * SDK 的 EventDispatcher 不支持通配事件处理器，通过反射将内部的事件处理器 Map
     * 替换为一个带默认兜底的 Map，使任何未显式注册的事件类型都能被 catchAllHandler 捕获并转发。
     */
    @SuppressWarnings("unchecked")
    private void installCatchAllHandler(EventDispatcher dispatcher, CustomEventHandler fallback) {
        try {
            Field field = EventDispatcher.class.getDeclaredField("eventType2EventHandler");
            field.setAccessible(true);
            Map<String, Object> original = (Map<String, Object>) field.get(dispatcher);

            Map<String, Object> catchAllMap = new HashMap<>(original) {
                @Override
                public Object get(Object key) {
                    Object handler = super.get(key);
                    return handler != null ? handler : fallback;
                }
            };

            field.set(dispatcher, catchAllMap);
        } catch (Exception e) {
            log.warn("安装 catch-all 事件处理器失败，未注册的事件类型将无法被转发", e);
        }
    }

    @Override
    public void stop() {
        running = false;
        log.info("飞书 WebSocket 长连接已停止");
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE;
    }

    @SuppressWarnings("unchecked")
    private void tryStreamingReply(String eventData) {
        try {
            Map<String, Object> parsed = Jsons.DEFAULT.fromJson(eventData, Map.class);
            Map<String, Object> event = (Map<String, Object>) parsed.get("event");
            if (event == null) return;

            Map<String, Object> sender = (Map<String, Object>) event.get("sender");
            Map<String, Object> message = (Map<String, Object>) event.get("message");
            if (sender == null || message == null) return;

            Map<String, Object> senderId = (Map<String, Object>) sender.get("sender_id");
            if (senderId == null) return;
            String openId = (String) senderId.get("open_id");

            String msgType = (String) message.get("message_type");
            if (!"text".equals(msgType)) return;

            String contentJson = (String) message.get("content");
            Map<String, Object> content = Jsons.DEFAULT.fromJson(contentJson, Map.class);
            String text = (String) content.get("text");

            if (openId != null && text != null && !text.isBlank()) {
                streamingReplyService.handleMessage(openId, text);
            }
        } catch (Exception e) {
            log.warn("解析消息事件失败，跳过流式回复", e);
        }
    }

    @SuppressWarnings("unchecked")
    private Object parseJsonSafely(String json) {
        try {
            return Jsons.DEFAULT.fromJson(json, Map.class);
        } catch (Exception e) {
            return json;
        }
    }
}
