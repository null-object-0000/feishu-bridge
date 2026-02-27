package io.github.feishu.bridge.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.github.feishu.bridge.config.StreamingProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@Slf4j
@Service
@ConditionalOnProperty(name = "streaming.log.enabled", havingValue = "true")
public class ConversationLogService {

    private static final DateTimeFormatter FILE_TS = DateTimeFormatter
            .ofPattern("yyyyMMdd_HHmmss_SSS")
            .withZone(ZoneId.systemDefault());

    private final Path logDir;
    private final int maxFiles;
    private final ObjectMapper mapper;

    public ConversationLogService(StreamingProperties props) {
        this.logDir = Path.of(props.getLog().getDir());
        this.maxFiles = props.getLog().getMaxFiles();
        this.mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        try {
            Files.createDirectories(logDir);
            log.info("[conv-log] 对话日志已启用，目录={}，保留最近 {} 份（0=不限）",
                    logDir.toAbsolutePath(), maxFiles);
        } catch (IOException e) {
            log.error("[conv-log] 创建日志目录失败: {}", logDir, e);
        }
    }

    public void save(Map<String, Object> logData) {
        String ts = FILE_TS.format(Instant.now());
        String openId = String.valueOf(logData.getOrDefault("openId", "unknown"));
        String suffix = openId.length() > 6 ? openId.substring(openId.length() - 6) : openId;
        String filename = ts + "_" + suffix + ".json";

        try {
            Path file = logDir.resolve(filename);
            mapper.writeValue(file.toFile(), logData);
            log.debug("[conv-log] 对话日志已保存: {}", file);
        } catch (IOException e) {
            log.error("[conv-log] 写入对话日志失败: {}", filename, e);
        }

        cleanup();
    }

    private void cleanup() {
        if (maxFiles <= 0) return;
        try (Stream<Path> files = Files.list(logDir)) {
            List<Path> sorted = files
                    .filter(p -> p.toString().endsWith(".json"))
                    .sorted()
                    .toList();

            if (sorted.size() > maxFiles) {
                for (int i = 0; i < sorted.size() - maxFiles; i++) {
                    Files.deleteIfExists(sorted.get(i));
                    log.debug("[conv-log] 清理旧日志: {}", sorted.get(i).getFileName());
                }
            }
        } catch (IOException e) {
            log.warn("[conv-log] 清理旧日志失败", e);
        }
    }
}
