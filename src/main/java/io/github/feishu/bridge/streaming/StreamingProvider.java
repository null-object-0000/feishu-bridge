package io.github.feishu.bridge.streaming;

import java.net.http.HttpRequest;

public interface StreamingProvider {

    HttpRequest buildRequest(String userQuery, String userId);

    /**
     * 从一行 SSE data（已去除 "data: " 前缀）中提取文本增量。
     * 返回 null 表示该行不包含内容。
     */
    String parseChunk(String sseData);

    /**
     * 从一行 SSE data 中提取模型推理/思考内容（如 DeepSeek 的 reasoning_content）。
     * 默认返回 null，表示该 provider 不支持或该行不含推理内容。
     */
    default String parseReasoningChunk(String sseData) {
        return null;
    }

    boolean isDone(String sseData);
}
