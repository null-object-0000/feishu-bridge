package io.github.feishu.bridge.service;

import com.lark.oapi.Client;
import com.lark.oapi.core.utils.Jsons;
import com.lark.oapi.service.im.v1.model.*;
import io.github.feishu.bridge.config.FeishuProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class FeishuApiService {

    private static final String BASE_URL = "https://open.feishu.cn/open-apis";

    private final Client feishuClient;
    private final FeishuProperties feishuProperties;
    private final HttpClient httpClient;

    // ---- SDK 方法（内置 token 缓存，推荐使用）----

    /**
     * 发送消息（使用 SDK，自动管理 token）
     */
    public CreateMessageResp sendMessage(String receiveId, String receiveIdType,
                                          String msgType, String content) throws Exception {
        CreateMessageReq req = CreateMessageReq.newBuilder()
                .receiveIdType(receiveIdType)
                .createMessageReqBody(CreateMessageReqBody.newBuilder()
                        .receiveId(receiveId)
                        .msgType(msgType)
                        .content(content)
                        .build())
                .build();

        CreateMessageResp resp = feishuClient.im().v1().message().create(req);
        if (!resp.success()) {
            log.error("发送消息失败: code={}, msg={}", resp.getCode(), resp.getMsg());
        }
        return resp;
    }

    /**
     * 回复指定消息（使用 SDK，自动管理 token）
     */
    public ReplyMessageResp replyMessage(String messageId, String msgType, String content) throws Exception {
        ReplyMessageReq req = ReplyMessageReq.newBuilder()
                .messageId(messageId)
                .replyMessageReqBody(ReplyMessageReqBody.newBuilder()
                        .msgType(msgType)
                        .content(content)
                        .build())
                .build();

        ReplyMessageResp resp = feishuClient.im().v1().message().reply(req);
        if (!resp.success()) {
            log.error("回复消息失败: code={}, msg={}", resp.getCode(), resp.getMsg());
        }
        return resp;
    }

    /**
     * PATCH 更新已发送的卡片消息（使用 SDK，自动管理 token）
     */
    public void patchMessage(String messageId, String cardJson) throws Exception {
        PatchMessageReq req = PatchMessageReq.newBuilder()
                .messageId(messageId)
                .patchMessageReqBody(PatchMessageReqBody.newBuilder()
                        .content(cardJson)
                        .build())
                .build();

        PatchMessageResp resp = feishuClient.im().v1().message().patch(req);
        if (!resp.success()) {
            log.warn("更新卡片失败: code={}, msg={}, messageId={}", resp.getCode(), resp.getMsg(), messageId);
        }
    }

    /**
     * 构建一张 Markdown 内容的简易卡片 JSON
     */
    public static String buildMarkdownCard(String content) {
        return Jsons.DEFAULT.toJson(Map.of(
                "config", Map.of("wide_screen_mode", true),
                "elements", List.of(Map.of(
                        "tag", "markdown",
                        "content", content
                ))
        ));
    }

    /**
     * 获取单条消息详情
     */
    public Message getMessage(String messageId) throws Exception {
        GetMessageReq req = GetMessageReq.newBuilder()
                .messageId(messageId)
                .build();

        GetMessageResp resp = feishuClient.im().v1().message().get(req);
        if (!resp.success() || resp.getData().getItems() == null || resp.getData().getItems().length == 0) {
            log.warn("获取消息失败: code={}, msg={}, messageId={}", resp.getCode(), resp.getMsg(), messageId);
            return null;
        }
        return resp.getData().getItems()[0];
    }

    /**
     * 通过飞书 list messages API 获取话题（thread）内的历史消息，按时间正序排列。
     * 每条包含 "role"（user/assistant）和 "content"（文本）。
     */
    public List<Map<String, String>> fetchThreadHistory(String threadId, int maxMessages) {
        var history = new ArrayList<Map<String, String>>();
        String pageToken = null;

        outer:
        while (history.size() < maxMessages) {
            try {
                var reqBuilder = ListMessageReq.newBuilder()
                        .containerIdType("thread")
                        .containerId(threadId)
                        .sortType("ByCreateTimeAsc")
                        .pageSize(Math.min(maxMessages - history.size(), 50));
                if (pageToken != null) {
                    reqBuilder.pageToken(pageToken);
                }

                ListMessageResp resp = feishuClient.im().v1().message().list(reqBuilder.build());
                if (!resp.success() || resp.getData().getItems() == null) {
                    log.warn("[memory] 获取话题历史失败: code={}, msg={}, threadId={}",
                            resp.getCode(), resp.getMsg(), threadId);
                    break;
                }

                for (Message msg : resp.getData().getItems()) {
                    if (history.size() >= maxMessages) break outer;
                    String role = resolveRole(msg);
                    String text = extractTextContent(msg);
                    if (text != null && !text.isBlank()) {
                        history.add(Map.of("role", role, "content", text));
                    }
                }

                if (!Boolean.TRUE.equals(resp.getData().getHasMore())) break;
                pageToken = resp.getData().getPageToken();
            } catch (Exception e) {
                log.warn("[memory] 获取话题历史异常: threadId={}", threadId, e);
                break;
            }
        }

        return history;
    }

    /**
     * 沿回复链（parent_id）向上遍历，返回按时间正序排列的历史消息。
     * 每条包含 "role"（user/assistant）和 "content"（文本）。
     */
    public List<Map<String, String>> fetchReplyChainHistory(String startMessageId, int maxMessages) {
        var history = new ArrayList<Map<String, String>>();
        String currentId = startMessageId;

        while (currentId != null && history.size() < maxMessages) {
            try {
                Message msg = getMessage(currentId);
                if (msg == null) break;

                String role = resolveRole(msg);
                String text = extractTextContent(msg);
                if (text != null && !text.isBlank()) {
                    history.addFirst(Map.of("role", role, "content", text));
                }

                currentId = msg.getParentId();
            } catch (Exception e) {
                log.warn("[memory] 获取回复链消息失败: messageId={}", currentId, e);
                break;
            }
        }

        return history;
    }

    /**
     * 将飞书消息发送者映射为 LLM role。
     * 常见机器人类型为 bot/app，统一按 assistant 处理。
     */
    private String resolveRole(Message msg) {
        if (msg == null || msg.getSender() == null) return "user";
        String senderType = msg.getSender().getSenderType();
        if ("app".equals(senderType) || "bot".equals(senderType)) {
            return "assistant";
        }
        return "user";
    }

    /**
     * 从 Message 中提取纯文本内容。
     * 支持 text（普通文本）和 interactive（卡片）两种消息类型。
     * 卡片的 elements 可能是一维 [{tag,content}] 或二维 [[{tag,text}]]，两种都兼容。
     */
    @SuppressWarnings("unchecked")
    private String extractTextContent(Message msg) {
        if (msg.getBody() == null || msg.getBody().getContent() == null) return null;
        String content = msg.getBody().getContent();
        String msgType = msg.getMsgType();

        try {
            if ("text".equals(msgType)) {
                var parsed = (Map<String, Object>) Jsons.DEFAULT.fromJson(content, Map.class);
                return (String) parsed.get("text");
            } else if ("interactive".equals(msgType)) {
                var card = (Map<String, Object>) Jsons.DEFAULT.fromJson(content, Map.class);
                var elements = (List<?>) card.get("elements");
                if (elements == null) return null;

                var sb = new StringBuilder();
                for (var row : elements) {
                    if (row instanceof List<?> rowList) {
                        for (var cell : rowList) {
                            if (cell instanceof Map<?, ?> elem) {
                                appendElementText(elem, sb);
                            }
                        }
                    } else if (row instanceof Map<?, ?> elem) {
                        appendElementText(elem, sb);
                    }
                }
                return sb.isEmpty() ? null : sb.toString();
            }
        } catch (Exception e) {
            log.debug("[memory] 解析消息内容失败: msgType={}", msgType, e);
        }
        return null;
    }

    private void appendElementText(Map<?, ?> elem, StringBuilder sb) {
        String tag = (String) elem.get("tag");
        if ("markdown".equals(tag)) {
            Object v = elem.get("content");
            if (v != null) sb.append(v);
        } else if ("text".equals(tag)) {
            Object v = elem.get("text");
            if (v != null) sb.append(v);
        }
    }

    // ---- 通用代理请求（给 ProxyController 用）----

    @SuppressWarnings("unchecked")
    public String obtainTenantAccessToken() throws Exception {
        var tokenReq = com.lark.oapi.service.auth.v3.model.InternalTenantAccessTokenReq.newBuilder()
                .internalTenantAccessTokenReqBody(
                        com.lark.oapi.service.auth.v3.model.InternalTenantAccessTokenReqBody.newBuilder()
                                .appId(feishuProperties.getAppId())
                                .appSecret(feishuProperties.getAppSecret())
                                .build())
                .build();

        var resp = feishuClient.auth().v3().tenantAccessToken().internal(tokenReq);
        if (!resp.success()) {
            throw new RuntimeException("获取 tenant_access_token 失败: code=" + resp.getCode() + ", msg=" + resp.getMsg());
        }

        String rawBody = new String(resp.getRawResponse().getBody(), StandardCharsets.UTF_8);
        Map<String, Object> parsed = Jsons.DEFAULT.fromJson(rawBody, Map.class);
        return (String) parsed.get("tenant_access_token");
    }

    /**
     * 通用代理请求（给 ProxyController 转发用，需要手动获取 token）
     */
    public String proxyRequest(String method, String fullPath, String jsonBody) throws Exception {
        String url = BASE_URL + fullPath;
        String token = obtainTenantAccessToken();

        HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(30));

        String bodyContent = jsonBody != null ? jsonBody : "";
        HttpRequest req = switch (method.toUpperCase()) {
            case "PUT" -> reqBuilder.PUT(HttpRequest.BodyPublishers.ofString(bodyContent)).build();
            case "PATCH" -> reqBuilder.method("PATCH", HttpRequest.BodyPublishers.ofString(bodyContent)).build();
            case "DELETE" -> reqBuilder.method("DELETE", HttpRequest.BodyPublishers.noBody()).build();
            case "GET" -> reqBuilder.GET().build();
            default -> reqBuilder.POST(HttpRequest.BodyPublishers.ofString(bodyContent)).build();
        };

        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        return resp.body();
    }
}
