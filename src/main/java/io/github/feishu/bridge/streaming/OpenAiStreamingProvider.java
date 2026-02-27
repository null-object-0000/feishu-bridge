package io.github.feishu.bridge.streaming;

import com.lark.oapi.core.utils.Jsons;
import io.github.feishu.bridge.config.StreamingProperties;
import lombok.RequiredArgsConstructor;

import java.net.URI;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
public class OpenAiStreamingProvider implements StreamingProvider {

    private final StreamingProperties.OpenAi config;

    @Override
    public HttpRequest buildRequest(String userQuery, String userId, List<Map<String, String>> history) {
        var messages = new ArrayList<Map<String, String>>();
        if (config.getSystemPrompt() != null && !config.getSystemPrompt().isBlank()) {
            messages.add(Map.of("role", "system", "content", config.getSystemPrompt()));
        }
        if (history != null) {
            messages.addAll(history);
        }
        messages.add(Map.of("role", "user", "content", userQuery));

        var body = Map.of(
                "model", config.getModel(),
                "messages", messages,
                "stream", true
        );

        return HttpRequest.newBuilder()
                .uri(URI.create(config.getApiUrl()))
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
            var choices = (List<Map<String, Object>>) map.get("choices");
            if (choices == null || choices.isEmpty()) return null;
            var delta = (Map<String, Object>) choices.getFirst().get("delta");
            if (delta == null) return null;
            return (String) delta.get("content");
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public String parseReasoningChunk(String sseData) {
        try {
            var map = (Map<String, Object>) Jsons.DEFAULT.fromJson(sseData, Map.class);
            var choices = (List<Map<String, Object>>) map.get("choices");
            if (choices == null || choices.isEmpty()) return null;
            var delta = (Map<String, Object>) choices.getFirst().get("delta");
            if (delta == null) return null;
            return (String) delta.get("reasoning_content");
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public boolean isDone(String sseData) {
        return "[DONE]".equals(sseData.trim());
    }
}
