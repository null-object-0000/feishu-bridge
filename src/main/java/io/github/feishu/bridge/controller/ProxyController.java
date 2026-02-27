package io.github.feishu.bridge.controller;

import io.github.feishu.bridge.service.FeishuApiService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/feishu")
@RequiredArgsConstructor
public class ProxyController {

    private final FeishuApiService feishuApi;

    @GetMapping("/**")
    public ResponseEntity<String> proxyGet(HttpServletRequest request) throws Exception {
        return doProxy("GET", null, request);
    }

    @PostMapping("/**")
    public ResponseEntity<String> proxyPost(@RequestBody String body, HttpServletRequest request) throws Exception {
        return doProxy("POST", body, request);
    }

    @PutMapping("/**")
    public ResponseEntity<String> proxyPut(@RequestBody String body, HttpServletRequest request) throws Exception {
        return doProxy("PUT", body, request);
    }

    @PatchMapping("/**")
    public ResponseEntity<String> proxyPatch(@RequestBody String body, HttpServletRequest request) throws Exception {
        return doProxy("PATCH", body, request);
    }

    @DeleteMapping("/**")
    public ResponseEntity<String> proxyDelete(HttpServletRequest request) throws Exception {
        return doProxy("DELETE", null, request);
    }

    private ResponseEntity<String> doProxy(String method, String body, HttpServletRequest request) throws Exception {
        String feishuPath = request.getRequestURI().substring("/api/feishu".length());
        String queryString = request.getQueryString();

        String fullPath = feishuPath;
        if (queryString != null && !queryString.isEmpty()) {
            fullPath += "?" + queryString;
        }

        log.info("代理转发: {} {} -> /open-apis{}", method, request.getRequestURI(), fullPath);

        String resp = feishuApi.proxyRequest(method, fullPath, body);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(resp);
    }
}
