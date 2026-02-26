package io.github.feishu.bridge.service;

import com.lark.oapi.core.utils.Jsons;
import io.github.feishu.bridge.config.FeishuProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookForwardService {

    private final FeishuProperties feishuProperties;
    private final HttpClient httpClient;

    @Async
    public void forwardEvent(String eventType, Object payload) {
        forward("event", eventType, payload);
    }

    @Async
    public void forwardCardAction(Object payload) {
        forward("card_action", "card_action_trigger", payload);
    }

    private void forward(String type, String eventType, Object payload) {
        List<String> urls = feishuProperties.getWebhook().getUrls();
        if (urls == null || urls.isEmpty()) {
            log.warn("webhook URL 未配置，跳过转发: type={}", type);
            return;
        }

        Map<String, Object> body = Map.of(
                "type", type,
                "event_type", eventType,
                "timestamp", Instant.now().toEpochMilli(),
                "payload", payload
        );

        String json = Jsons.DEFAULT.toJson(body);

        for (String url : urls) {
            if (url == null || url.isBlank()) {
                continue;
            }
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json))
                        .timeout(Duration.ofSeconds(30))
                        .build();
                httpClient.send(request, HttpResponse.BodyHandlers.discarding());
                log.info("webhook 转发成功: type={}, event_type={}, url={}", type, eventType, url);
            } catch (Exception e) {
                log.error("webhook 转发失败: type={}, url={}", type, url, e);
            }
        }
    }
}
