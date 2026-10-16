package com.tgnguyen.layoutlybe.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig {

    @Bean
    public WebMvcConfigurer corsConfigurer(@Value("${layoutly.auth.allowed-origins:http://localhost:5173,http://localhost:5174,http://127.0.0.1:5173,http://127.0.0.1:5174}") String accountOrigins) {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                // Vite changes Host while forwarding the browser's Origin. Account routes
                // need PATCH/DELETE and credentialed CORS restricted to trusted origins.
                registry.addMapping("/api/**")
                        .allowedOrigins(accountOrigins.split(","))
                        .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                        .allowedHeaders("Content-Type", "X-CSRF-TOKEN", "X-Figma-Token")
                        .allowCredentials(true);
            }
        };
    }
}
