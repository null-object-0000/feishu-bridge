package io.github.feishu.bridge.config;

import com.lark.oapi.Client;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
@EnableConfigurationProperties(FeishuProperties.class)
public class FeishuClientConfig {

    @Bean
    public Client feishuClient(FeishuProperties props) {
        return Client.newBuilder(props.getAppId(), props.getAppSecret()).build();
    }

    @Bean
    public HttpClient httpClient() {
        return HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }
}
