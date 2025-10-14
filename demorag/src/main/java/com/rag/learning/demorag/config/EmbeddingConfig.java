package com.rag.learning.demorag.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Configuration
@ConfigurationProperties(prefix = "embedding")
@Validated
@Data
public class EmbeddingConfig {
    
    /**
     * General embedding configuration
     */
    @Min(value = 1, message = "Dimension must be positive")
    private int dimension;
    
    @Min(value = 1, message = "Max input length must be positive")
    private int maxInputLength;
    
    @Min(value = 1, message = "Batch size must be positive")
    private int batchSize;
    
    /**
     * Gemini-specific configuration
     */
    @NotNull
    private GeminiConfig gemini;
    
    @Data
    public static class GeminiConfig {
        /**
         * Gemini API Key - set via GEMINI_API_KEY environment variable
         */
        @NotBlank(message = "Gemini API key must be provided")
        private String apiKey;
        
        /**
         * Gemini API URL (includes model name in the endpoint)
         * Example: https://generativelanguage.googleapis.com/v1beta/models/text-embedding-004:embedContent
         */
        @NotBlank(message = "Gemini API URL must be provided")
        private String apiUrl;
        
        @Min(value = 1000)
        private int connectTimeout;
        
        @Min(value = 1000)
        private int readTimeout;
        
        @Min(value = 1000)
        private int writeTimeout;
        
        @Min(value = 0)
        private int maxRetries;
        
        @Min(value = 100)
        private int retryDelayMs;
    }
}