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

    private OpenAi openai = new OpenAi();
    private Dify dify = new Dify();

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
}
