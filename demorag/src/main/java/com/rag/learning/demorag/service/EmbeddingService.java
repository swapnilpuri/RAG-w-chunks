package com.rag.learning.demorag.service;

import com.rag.learning.demorag.config.EmbeddingConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class EmbeddingService {
    
    private final EmbeddingConfig config;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    
    public EmbeddingService(EmbeddingConfig config) {
        this.config = config;
        this.objectMapper = new ObjectMapper();
        
        // Build HTTP client with configured timeouts
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(config.getGemini().getConnectTimeout(), TimeUnit.MILLISECONDS)
                .readTimeout(config.getGemini().getReadTimeout(), TimeUnit.MILLISECONDS)
                .writeTimeout(config.getGemini().getWriteTimeout(), TimeUnit.MILLISECONDS)
                .build();
        
        log.info("EmbeddingService initialized with Gemini");
        log.info("Embedding dimension: {}", config.getDimension());
        log.info("API URL: {}", config.getGemini().getApiUrl());
    }
    
    /**
     * Generate embeddings using Gemini API
     */
    public float[] generateEmbedding(String text) throws IOException {
        log.debug("Generating embedding for text of length {}", text.length());
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("Text cannot be empty");
        }
        
        // Truncate text if too long
        String truncatedText = truncateText(text, config.getMaxInputLength());
        
        // Generate embedding with retry logic
        return generateGeminiEmbeddingWithRetry(truncatedText);
    }
    
    /**
     * Generate embedding with retry logic
     */
    private float[] generateGeminiEmbeddingWithRetry(String text) throws IOException {
        int retries = 0;
        Exception lastException = null;
        
        while (retries <= config.getGemini().getMaxRetries()) {
            try {
                return callGeminiAPI(text);
            } catch (Exception e) {
                lastException = e;
                retries++;
                
                if (retries <= config.getGemini().getMaxRetries()) {
                    log.warn("Gemini API call failed (attempt {}/{}): {}. Retrying...", 
                            retries, config.getGemini().getMaxRetries(), e.getMessage());
                    
                    try {
                        Thread.sleep(config.getGemini().getRetryDelayMs() * retries);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new IOException("Interrupted while retrying", ie);
                    }
                }
            }
        }
        
        throw new IOException("Failed to generate Gemini embedding after " + 
                config.getGemini().getMaxRetries() + " retries", lastException);
    }
    
    /**
     * Call Gemini Embedding API
     */
    private float[] callGeminiAPI(String text) throws IOException {
        // Build request body
        String requestBody = buildGeminiRequestBody(text);
        
        // Build HTTP request
        String urlWithKey = config.getGemini().getApiUrl() + "?key=" + config.getGemini().getApiKey();
        Request request = new Request.Builder()
                .url(urlWithKey)
                .post(RequestBody.create(requestBody, MediaType.get("application/json")))
                .addHeader("Content-Type", "application/json")
                .build();
        
        // Execute request
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "No error details";
                log.error("Gemini API error: {} - {}", response.code(), errorBody);
                throw new IOException("Gemini API request failed: " + response.code() + " - " + errorBody);
            }
            
            String responseBody = response.body().string();
            return parseGeminiResponse(responseBody);
        }
    }
    
    /**
     * Build JSON request body for Gemini API
     * Note: The model is not needed in the body since it's already in the URL
     */
    private String buildGeminiRequestBody(String text) throws IOException {
        String json = String.format(
            "{\"content\": {\"parts\": [{\"text\": %s}]}}",
            objectMapper.writeValueAsString(text)
        );
        return json;
    }
    
    /**
     * Parse embedding from Gemini API response
     */
    private float[] parseGeminiResponse(String responseBody) throws IOException {
        JsonNode root = objectMapper.readTree(responseBody);
        
        // Navigate to embedding values
        JsonNode embeddingNode = root.path("embedding").path("values");
        
        if (embeddingNode.isMissingNode() || !embeddingNode.isArray()) {
            throw new IOException("Invalid response format from Gemini API");
        }
        
        // Convert to float array
        List<Float> embeddingList = new ArrayList<>();
        for (JsonNode value : embeddingNode) {
            embeddingList.add((float) value.asDouble());
        }
        
        if (embeddingList.size() != config.getDimension()) {
            log.warn("Expected {} dimensions but got {}", config.getDimension(), embeddingList.size());
        }
        
        // Convert List to array
        float[] embedding = new float[embeddingList.size()];
        for (int i = 0; i < embeddingList.size(); i++) {
            embedding[i] = embeddingList.get(i);
        }
        
        log.debug("Generated Gemini embedding with {} dimensions", embedding.length);
        return embedding;
    }
    
    /**
     * Truncate text to avoid token limits
     */
    private String truncateText(String text, int maxChars) {
        if (text.length() <= maxChars) {
            return text;
        }
        log.warn("Truncating text from {} to {} characters", text.length(), maxChars);
        return text.substring(0, maxChars);
    }
    
    /**
     * Batch generate embeddings for multiple texts
     */
    public List<float[]> generateEmbeddings(List<String> texts) throws IOException {
        List<float[]> embeddings = new ArrayList<>();
        
        // Process in batches
        int batchSize = config.getBatchSize();
        for (int i = 0; i < texts.size(); i += batchSize) {
            int end = Math.min(i + batchSize, texts.size());
            List<String> batch = texts.subList(i, end);
            
            log.debug("Processing batch {}/{}", (i / batchSize) + 1, 
                    (texts.size() + batchSize - 1) / batchSize);
            
            for (String text : batch) {
                embeddings.add(generateEmbedding(text));
            }
        }
        
        return embeddings;
    }
    
    /**
     * Get current embedding configuration info
     */
    public String getConfigurationInfo() {
        return String.format(
            "Dimension: %d, Max Input: %d chars, API: %s",
            config.getDimension(),
            config.getMaxInputLength(),
            config.getGemini().getApiUrl()
        );
    }
}