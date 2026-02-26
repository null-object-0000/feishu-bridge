package io.github.newbie.feishu.config;

import com.lark.oapi.Client;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(FeishuProperties.class)
public class FeishuClientConfig {

    @Bean
    public Client feishuClient(FeishuProperties props) {
        return Client.newBuilder(props.getAppId(), props.getAppSecret()).build();
    }

    @Bean
    public RestClient restClient() {
        return RestClient.create();
    }
}
