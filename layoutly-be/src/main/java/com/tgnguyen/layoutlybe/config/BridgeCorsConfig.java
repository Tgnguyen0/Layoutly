package com.tgnguyen.layoutlybe.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class BridgeCorsConfig implements WebMvcConfigurer {
    private final String[] allowedOrigins;

    public BridgeCorsConfig(@Value("${layoutly.bridge.allowed-origins:https://www.figma.com,http://localhost:5173,null}")
                            String allowedOrigins) {
        this.allowedOrigins = java.util.Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toArray(String[]::new);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/bridge/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "OPTIONS")
                .allowedHeaders("Content-Type")
                .maxAge(3600);
    }
}
