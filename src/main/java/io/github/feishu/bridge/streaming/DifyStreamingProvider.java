package io.github.feishu.bridge.streaming;

import com.lark.oapi.core.utils.Jsons;
import io.github.feishu.bridge.config.StreamingProperties;
import lombok.RequiredArgsConstructor;

import java.net.URI;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.Map;

@RequiredArgsConstructor
public class DifyStreamingProvider implements StreamingProvider {

    private final StreamingProperties.Dify config;

    @Override
    public HttpRequest buildRequest(String userQuery, String userId) {
        boolean isWorkflow = "workflow".equalsIgnoreCase(config.getAppType());
        String apiPath = isWorkflow ? "/workflows/run" : "/chat-messages";

        Object body = isWorkflow
                ? Map.of(
                    "inputs", Map.of("query", userQuery),
                    "response_mode", "streaming",
                    "user", userId)
                : Map.of(
                    "inputs", Map.of(),
                    "query", userQuery,
                    "response_mode", "streaming",
                    "user", userId);

        return HttpRequest.newBuilder()
                .uri(URI.create(config.getApiUrl() + apiPath))
                .header("Authorization", "Bearer " + config.getApiKey())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(Jsons.DEFAULT.toJson(body)))
                .timeout(Duration.ofSeconds(120))
                .build();
    }

    @Override
    @SuppressWarnings("unchecked")
    public String parseChunk(String sseData) {
        try {
            var map = (Map<String, Object>) Jsons.DEFAULT.fromJson(sseData, Map.class);
            String event = (String) map.getOrDefault("event", "");
            return switch (event) {
                case "message", "agent_message" -> (String) map.get("answer");
                case "text_chunk" -> {
                    var data = (Map<String, Object>) map.get("data");
                    yield data != null ? (String) data.get("text") : null;
                }
                default -> null;
            };
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean isDone(String sseData) {
        try {
            var map = (Map<String, Object>) Jsons.DEFAULT.fromJson(sseData, Map.class);
            String event = (String) map.getOrDefault("event", "");
            return "message_end".equals(event) || "workflow_finished".equals(event);
        } catch (Exception e) {
            return false;
        }
    }
}
