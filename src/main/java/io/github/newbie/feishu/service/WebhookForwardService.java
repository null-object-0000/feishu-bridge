package io.github.newbie.feishu.service;

import io.github.newbie.feishu.config.FeishuProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookForwardService {

    private final FeishuProperties feishuProperties;
    private final RestClient restClient;

    @Async
    public void forwardEvent(String eventType, Object payload) {
        forward("event", eventType, payload);
    }

    @Async
    public void forwardCardAction(Object payload) {
        forward("card_action", "card_action_trigger", payload);
    }

    private void forward(String type, String eventType, Object payload) {
        String url = feishuProperties.getWebhook().getUrl();
        if (url == null || url.isBlank()) {
            log.warn("webhook URL 未配置，跳过转发: type={}", type);
            return;
        }

        Map<String, Object> body = Map.of(
                "type", type,
                "event_type", eventType,
                "timestamp", Instant.now().toEpochMilli(),
                "payload", payload
        );

        try {
            restClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
            log.info("webhook 转发成功: type={}, event_type={}, url={}", type, eventType, url);
        } catch (Exception e) {
            log.error("webhook 转发失败: type={}, url={}", type, url, e);
        }
    }
}
