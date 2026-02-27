package io.github.feishu.bridge.streaming;

import com.lark.oapi.core.utils.Jsons;
import io.github.feishu.bridge.config.StreamingProperties;
import lombok.RequiredArgsConstructor;

import java.net.URI;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RequiredArgsConstructor
public class DifyStreamingProvider implements StreamingProvider {

    private final StreamingProperties.Dify config;

    /** userId → Dify conversation_id，用于多轮对话 */
    private final ConcurrentHashMap<String, String> conversationIds = new ConcurrentHashMap<>();

    @Override
    public HttpRequest buildRequest(String userQuery, String userId, List<Map<String, String>> history) {
        boolean isWorkflow = "workflow".equalsIgnoreCase(config.getAppType());
        String apiPath = isWorkflow ? "/workflows/run" : "/chat-messages";

        Map<String, Object> body;
        if (isWorkflow) {
            body = new HashMap<>(Map.of(
                    "inputs", Map.of("query", userQuery),
                    "response_mode", "streaming",
                    "user", userId));
        } else {
            body = new HashMap<>(Map.of(
                    "inputs", Map.of(),
                    "query", userQuery,
                    "response_mode", "streaming",
                    "user", userId));
            String convId = conversationIds.get(userId);
            if (convId != null) {
                body.put("conversation_id", convId);
            }
        }

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
    public void onStreamData(String userId, String sseData) {
        if ("workflow".equalsIgnoreCase(config.getAppType())) return;
        try {
            var map = (Map<String, Object>) Jsons.DEFAULT.fromJson(sseData, Map.class);
            String convId = (String) map.get("conversation_id");
            if (convId != null && !convId.isEmpty()) {
                conversationIds.put(userId, convId);
            }
        } catch (Exception ignored) {
        }
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
