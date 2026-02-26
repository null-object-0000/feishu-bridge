package io.github.newbie.feishu.controller;

import com.lark.oapi.Client;
import com.lark.oapi.core.utils.Jsons;
import com.lark.oapi.service.auth.v3.model.InternalTenantAccessTokenReq;
import com.lark.oapi.service.auth.v3.model.InternalTenantAccessTokenReqBody;
import com.lark.oapi.service.auth.v3.model.InternalTenantAccessTokenResp;
import io.github.newbie.feishu.config.FeishuProperties;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/feishu")
@RequiredArgsConstructor
public class ProxyController {

    private static final String FEISHU_BASE_URL = "https://open.feishu.cn/open-apis";

    private final Client feishuClient;
    private final FeishuProperties feishuProperties;
    private final HttpClient httpClient;

    @PostMapping("/**")
    public ResponseEntity<String> proxy(@RequestBody String body, HttpServletRequest request) throws Exception {
        String feishuPath = request.getRequestURI().substring("/api/feishu".length());
        String queryString = request.getQueryString();
        String targetUrl = FEISHU_BASE_URL + feishuPath;
        if (queryString != null && !queryString.isEmpty()) {
            targetUrl += "?" + queryString;
        }

        String token = obtainTenantAccessToken();

        log.info("代理转发: POST {} -> {}", request.getRequestURI(), targetUrl);

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(targetUrl))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(Duration.ofSeconds(30))
                .build();
        HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(response.body());
    }

    @SuppressWarnings("unchecked")
    private String obtainTenantAccessToken() throws Exception {
        InternalTenantAccessTokenReq req = InternalTenantAccessTokenReq.newBuilder()
                .internalTenantAccessTokenReqBody(InternalTenantAccessTokenReqBody.newBuilder()
                        .appId(feishuProperties.getAppId())
                        .appSecret(feishuProperties.getAppSecret())
                        .build())
                .build();

        InternalTenantAccessTokenResp resp = feishuClient.auth().v3().tenantAccessToken().internal(req);
        if (!resp.success()) {
            throw new RuntimeException("获取 tenant_access_token 失败: code=" + resp.getCode() + ", msg=" + resp.getMsg());
        }

        String rawBody = new String(resp.getRawResponse().getBody(), StandardCharsets.UTF_8);
        Map<String, Object> parsed = Jsons.DEFAULT.fromJson(rawBody, Map.class);
        return (String) parsed.get("tenant_access_token");
    }
}
