package io.github.feishu.bridge.service;

import com.lark.oapi.Client;
import com.lark.oapi.core.utils.Jsons;
import com.lark.oapi.service.im.v1.model.*;
import io.github.feishu.bridge.config.FeishuProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
