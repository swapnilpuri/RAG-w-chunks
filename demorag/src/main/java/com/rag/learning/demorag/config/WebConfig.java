package com.rag.learning.demorag.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig {
    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/rag/v1/ask")
                        .allowedOrigins("http://localhost:4200") // Angular dev server
                        .allowedMethods("POST")
                        .allowedHeaders("*")
                        .allowCredentials(true);
            }
        };
    }
}