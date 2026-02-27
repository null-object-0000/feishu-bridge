package io.github.feishu.bridge.streaming;

import io.github.feishu.bridge.config.StreamingProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "streaming.enabled", havingValue = "true")
public class StreamingProviderConfig {

    @Bean
    public StreamingProvider streamingProvider(StreamingProperties props) {
        return switch (props.getProvider().toLowerCase()) {
            case "dify" -> new DifyStreamingProvider(props.getDify());
            default -> new OpenAiStreamingProvider(props.getOpenai());
        };
    }
}
