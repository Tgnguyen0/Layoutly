package com.tgnguyen.layoutlybe.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Value("${figma.api.base-url}")
    private String baseUrl;

    @Value("${figma.api.max-in-memory-size-mb:100}")
    private int maxInMemorySizeMb;

    @Bean
    public WebClient figmaWebClient() {
        // File Figma co the rat lon, nen gioi han nay duoc cau hinh theo moi truong.
        int maxInMemorySizeBytes = Math.multiplyExact(maxInMemorySizeMb, 1024 * 1024);
        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(maxInMemorySizeBytes))
                .build();

        return WebClient.builder()
                .baseUrl(baseUrl)
                .exchangeStrategies(strategies)
                .build();
    }
}
