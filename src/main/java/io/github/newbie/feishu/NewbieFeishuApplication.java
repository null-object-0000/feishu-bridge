package io.github.newbie.feishu;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@SpringBootApplication
public class NewbieFeishuApplication {

    public static void main(String[] args) {
        SpringApplication.run(NewbieFeishuApplication.class, args);
    }

}
