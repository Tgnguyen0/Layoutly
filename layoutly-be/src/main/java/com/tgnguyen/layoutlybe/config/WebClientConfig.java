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

    @Bean
    public WebClient figmaWebClient() {
        // File Figma co the rat lon (nhieu MB JSON), tang gioi han buffer
        // mac dinh cua WebClient (256KB) len 20MB de tranh loi DataBufferLimitException
        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(20 * 1024 * 1024))
                .build();

        return WebClient.builder()
                .baseUrl(baseUrl)
                .exchangeStrategies(strategies)
                .build();
    }
}
