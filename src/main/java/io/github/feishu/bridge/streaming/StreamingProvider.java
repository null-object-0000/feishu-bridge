package io.github.feishu.bridge.streaming;

import java.net.http.HttpRequest;
import java.util.List;
import java.util.Map;

public interface StreamingProvider {

    /**
     * 构建 LLM 请求，支持携带历史对话（memory 功能）。
     *
     * @param history 历史消息列表，每条包含 "role"("user"/"assistant") 和 "content"；
     *                为空列表时表示无历史或未启用 memory。
     */
    HttpRequest buildRequest(String userQuery, String userId, List<Map<String, String>> history);

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

    /**
     * SSE 流中每行 data 的回调，供 provider 提取元数据（如 Dify 的 conversation_id）。
     */
    default void onStreamData(String userId, String sseData) {
    }
}
