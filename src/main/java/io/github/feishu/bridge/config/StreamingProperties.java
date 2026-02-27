package io.github.feishu.bridge.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "streaming")
public class StreamingProperties {

    private boolean enabled = false;

    /**
     * openai | dify
     */
    private String provider = "openai";

    /**
     * 是否以"回复消息"形式发送（reply），false 则直接发送新消息。
     * 开启 memory 时会强制开启此选项。
     */
    private boolean replyMode = false;

    private OpenAi openai = new OpenAi();
    private Dify dify = new Dify();
    private Memory memory = new Memory();
    private Log log = new Log();

    @Data
    public static class OpenAi {
        private String apiUrl = "https://api.openai.com/v1/chat/completions";
        private String apiKey;
        private String model = "gpt-4o";
        private String systemPrompt;
    }

    @Data
    public static class Dify {
        private String apiUrl;
        private String apiKey;
        /**
         * chat | workflow
         */
        private String appType = "chat";
    }

    @Data
    public static class Memory {
        private boolean enabled = false;
        /**
         * 获取的最大历史消息条数，0 表示不限制
         */
        private int maxMessages = 0;
    }

    @Data
    public static class Log {
        private boolean enabled = false;
        /**
         * 日志文件存放目录
         */
        private String dir = "logs/conversations";
        /**
         * 保留最近几份日志文件，0 表示不限制
         */
        private int maxFiles = 100;
    }
}
