package io.github.newbie.feishu.controller;

import com.lark.oapi.Client;
import com.lark.oapi.core.utils.Jsons;
import com.lark.oapi.service.auth.v3.model.InternalTenantAccessTokenReq;
import com.lark.oapi.service.auth.v3.model.InternalTenantAccessTokenReqBody;
import com.lark.oapi.service.auth.v3.model.InternalTenantAccessTokenResp;
import io.github.newbie.feishu.config.FeishuProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final Client feishuClient;
    private final FeishuProperties feishuProperties;

    @GetMapping("/tenant_access_token")
    public ResponseEntity<String> getTenantAccessToken() throws Exception {
        InternalTenantAccessTokenReq req = InternalTenantAccessTokenReq.newBuilder()
                .internalTenantAccessTokenReqBody(InternalTenantAccessTokenReqBody.newBuilder()
                        .appId(feishuProperties.getAppId())
                        .appSecret(feishuProperties.getAppSecret())
                        .build())
                .build();

        InternalTenantAccessTokenResp resp = feishuClient.auth().v3().tenantAccessToken().internal(req);

        if (!resp.success()) {
            log.error("获取 tenant_access_token 失败: code={}, msg={}", resp.getCode(), resp.getMsg());
            return ResponseEntity.status(502)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Jsons.DEFAULT.toJson(Map.of("code", resp.getCode(), "msg", resp.getMsg())));
        }

        // tenant_access_token 和 expire 在原始响应顶层，不在 data 中
        String rawBody = new String(resp.getRawResponse().getBody(), StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(rawBody);
    }
}
