package com.codereview.assistant;

import com.codereview.assistant.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableConfigurationProperties(AppProperties.class)
public class AiPrReviewAssistantApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiPrReviewAssistantApplication.class, args);
    }
}
