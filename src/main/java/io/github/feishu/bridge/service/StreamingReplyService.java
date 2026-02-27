package io.github.feishu.bridge.service;

import io.github.feishu.bridge.streaming.StreamingProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
@ConditionalOnProperty(name = "streaming.enabled", havingValue = "true")
public class StreamingReplyService {

    private static final long LOG_INTERVAL_MS = 3000;
    private static final long CARD_UPDATE_INTERVAL_MS = 200;

    private final StreamingProvider streamingProvider;
    private final FeishuApiService feishuApi;
    private final HttpClient httpClient;

    @Autowired
    public StreamingReplyService(StreamingProvider streamingProvider,
                                  FeishuApiService feishuApi,
                                  HttpClient httpClient) {
        this.streamingProvider = streamingProvider;
        this.feishuApi = feishuApi;
        this.httpClient = httpClient;
    }

    @Async
    public void handleMessage(String openId, String userQuery) {
        long startTime = System.currentTimeMillis();
        long firstContentTime = 0;
        int contentChunks = 0;
        int contentChars = 0;
        int reasoningChunks = 0;
        int reasoningChars = 0;
        int sseLineCount = 0;
        CompletableFuture<String> messageIdFuture = null;

        long lastChunkTime = 0;
        int burstChunks = 0;
        int gapChunks = 0;
        long maxGapMs = 0;

        try {
            var request = streamingProvider.buildRequest(userQuery, openId);
            log.info("[streaming] 开始请求: openId={}, query={}",
                    openId, truncate(userQuery, 80));

            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            long apiResponseTime = System.currentTimeMillis();
            log.info("[streaming] API 响应: status={}, 耗时={}ms",
                    response.statusCode(), apiResponseTime - startTime);

            int statusCode = response.statusCode();
            if (statusCode != 200) {
                String errorBody;
                try (var is = response.body()) {
                    errorBody = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                }
                log.error("[streaming] API 返回非 200: status={}, body={}", statusCode, errorBody);
                var errorCard = FeishuApiService.buildMarkdownCard(
                        "调用 AI 服务失败 (HTTP " + statusCode + ")");
                feishuApi.sendMessage(openId, "open_id", "interactive", errorCard);
                return;
            }

            var accumulated = new StringBuilder();
            long lastCardUpdate = System.currentTimeMillis();
            long lastLogTime = System.currentTimeMillis();
            int lastLogChars = 0;
            var cardUpdating = new AtomicBoolean(false);

            try (var reader = new BufferedReader(
                    new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sseLineCount++;

                    if (sseLineCount <= 3) {
                        log.debug("[streaming] SSE 原始行 [{}]: {}", sseLineCount, line);
                    }

                    if (!line.startsWith("data:")) continue;

                    String data = line.substring(line.startsWith("data: ") ? 6 : 5).trim();
                    if (data.isEmpty()) continue;

                    if (streamingProvider.isDone(data)) {
                        log.debug("[streaming] SSE 流结束信号");
                        break;
                    }

                    // --- 推理/思考内容 ---
                    String reasoning = streamingProvider.parseReasoningChunk(data);
                    if (reasoning != null && !reasoning.isEmpty()) {
                        reasoningChunks++;
                        reasoningChars += reasoning.length();
                        long now = System.currentTimeMillis();
                        if (now - lastLogTime >= LOG_INTERVAL_MS) {
                            log.info("[streaming] 模型思考中: reasoning_chunks={}, reasoning_chars={}, 已耗时={}ms",
                                    reasoningChunks, reasoningChars, now - startTime);
                            lastLogTime = now;
                        }
                    }

                    // --- 正文内容 ---
                    String chunk = streamingProvider.parseChunk(data);
                    if (chunk == null || chunk.isEmpty()) continue;

                    long chunkTime = System.currentTimeMillis();
                    contentChunks++;
                    contentChars += chunk.length();

                    if (lastChunkTime > 0) {
                        long gap = chunkTime - lastChunkTime;
                        if (gap < 50) {
                            burstChunks++;
                        } else {
                            gapChunks++;
                            if (gap > maxGapMs) maxGapMs = gap;
                        }
                    }
                    lastChunkTime = chunkTime;

                    if (firstContentTime == 0) {
                        firstContentTime = chunkTime;
                        log.info("[streaming] 首个内容到达: TTFT={}ms, 思考阶段 reasoning_chunks={}, reasoning_chars={}",
                                firstContentTime - startTime, reasoningChunks, reasoningChars);
                    }

                    accumulated.append(chunk);

                    // 实时统计日志
                    if (chunkTime - lastLogTime >= LOG_INTERVAL_MS) {
                        double elapsedSec = (chunkTime - firstContentTime) / 1000.0;
                        double speed = elapsedSec > 0 ? contentChars / elapsedSec : 0;
                        int newChars = contentChars - lastLogChars;
                        log.info("[streaming] 实时: chunks={}, chars={} (+{}), 速度={} 字/秒, 已持续 {}s, 突发/间隔={}/{}",
                                contentChunks, contentChars, newChars,
                                String.format("%.1f", speed),
                                String.format("%.1f", elapsedSec),
                                burstChunks, gapChunks);
                        lastLogTime = chunkTime;
                        lastLogChars = contentChars;
                    }

                    // 首次有内容时异步创建卡片，之后异步更新（读取线程永不阻塞）
                    if (messageIdFuture == null) {
                        String firstContent = accumulated.toString();
                        String theOpenId = openId;
                        messageIdFuture = CompletableFuture.supplyAsync(() -> {
                            try {
                                var card = FeishuApiService.buildMarkdownCard(firstContent);
                                var createResp = feishuApi.sendMessage(theOpenId, "open_id", "interactive", card);
                                if (createResp.success()) {
                                    return createResp.getData().getMessageId();
                                }
                                log.error("[streaming] 创建卡片失败: code={}, msg={}",
                                        createResp.getCode(), createResp.getMsg());
                            } catch (Exception e) {
                                log.error("[streaming] 创建卡片异常", e);
                            }
                            return null;
                        });
                        lastCardUpdate = chunkTime;
                    } else if (messageIdFuture.isDone()
                            && chunkTime - lastCardUpdate >= CARD_UPDATE_INTERVAL_MS
                            && !cardUpdating.get()) {
                        String msgId = messageIdFuture.getNow(null);
                        if (msgId != null) {
                            String content = accumulated.toString();
                            cardUpdating.set(true);
                            CompletableFuture.runAsync(() -> {
                                safeUpdateCard(msgId, content);
                                cardUpdating.set(false);
                            });
                            lastCardUpdate = chunkTime;
                        }
                    }
                }
            }

            // --- 最终更新 ---
            if (messageIdFuture != null) {
                String msgId = messageIdFuture.get(10, TimeUnit.SECONDS);
                if (msgId != null && !accumulated.isEmpty()) {
                    for (int i = 0; i < 100 && cardUpdating.get(); i++) {
                        Thread.sleep(20);
                    }
                    safeUpdateCard(msgId, accumulated.toString());
                }
            } else {
                log.warn("[streaming] 流式响应无内容，共读取 {} 行 SSE 数据", sseLineCount);
                var errorCard = FeishuApiService.buildMarkdownCard("AI 未返回有效内容");
                feishuApi.sendMessage(openId, "open_id", "interactive", errorCard);
            }

            // --- 最终统计 ---
            long endTime = System.currentTimeMillis();
            long totalMs = endTime - startTime;
            long ttft = firstContentTime > 0 ? firstContentTime - startTime : -1;
            double totalSec = totalMs / 1000.0;
            double contentDurationSec = (firstContentTime > 0 && endTime > firstContentTime)
                    ? (endTime - firstContentTime) / 1000.0 : 0;
            double overallSpeed = totalSec > 0 ? contentChars / totalSec : 0;
            double contentSpeed = contentDurationSec > 0 ? contentChars / contentDurationSec : 0;

            log.info("[streaming] ====== 完成统计 ======");
            log.info("[streaming]   openId        : {}", openId);
            log.info("[streaming]   总耗时        : {}ms ({}s)", totalMs, String.format("%.1f", totalSec));
            log.info("[streaming]   首token延迟   : {}ms", ttft);
            log.info("[streaming]   内容chunks    : {}", contentChunks);
            log.info("[streaming]   内容字符数    : {}", contentChars);
            log.info("[streaming]   思考chunks    : {}", reasoningChunks);
            log.info("[streaming]   思考字符数    : {}", reasoningChars);
            log.info("[streaming]   SSE总行数     : {}", sseLineCount);
            log.info("[streaming]   整体速度      : {} 字/秒 (含TTFT)", String.format("%.1f", overallSpeed));
            log.info("[streaming]   输出速度      : {} 字/秒 (不含TTFT)", String.format("%.1f", contentSpeed));
            log.info("[streaming]   突发chunks    : {} (间隔<50ms)", burstChunks);
            log.info("[streaming]   间隔chunks    : {} (间隔≥50ms, 最大间隔={}ms)", gapChunks, maxGapMs);
            log.info("[streaming] =======================");

        } catch (Exception e) {
            log.error("[streaming] 回复失败: openId={}", openId, e);
        }
    }

    private void safeUpdateCard(String messageId, String content) {
        try {
            feishuApi.patchMessage(messageId, FeishuApiService.buildMarkdownCard(content));
        } catch (Exception e) {
            log.warn("[streaming] 卡片更新失败: messageId={}", messageId, e);
        }
    }

    private static String truncate(String s, int maxLen) {
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }
}
