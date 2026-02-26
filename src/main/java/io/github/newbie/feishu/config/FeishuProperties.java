package io.github.newbie.feishu.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@Data
@ConfigurationProperties(prefix = "feishu")
public class FeishuProperties {

    private String appId;
    private String appSecret;
    private Webhook webhook = new Webhook();

    @Data
    public static class Webhook {
        private List<String> urls = new ArrayList<>();
    }
}
