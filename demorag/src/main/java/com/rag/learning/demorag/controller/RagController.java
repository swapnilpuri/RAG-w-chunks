package com.rag.learning.demorag.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.rag.learning.demorag.service.EnhancedRagService;
import com.rag.learning.demorag.service.RagService;

import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/rag/v1")
@Slf4j
public class RagController {

    private final RagService ragService;
    private final EnhancedRagService enhancedRagService;

    @Autowired
    public RagController(RagService ragService, 
                        @Autowired(required = false) EnhancedRagService enhancedRagService) {
        this.ragService = ragService;
        this.enhancedRagService = enhancedRagService;
    }

    /**
     * Basic RAG query endpoint
     */
    @GetMapping("/ask")
    public ResponseEntity<Map<String, Object>> askDocument(
            @RequestParam(value = "query", defaultValue = "What is RAG?") String query) {
        
        log.info("Received query: {}", query);
        long startTime = System.currentTimeMillis();
        
        try {
            String response = ragService.generateResponse(query);
            long duration = System.currentTimeMillis() - startTime;
            
            Map<String, Object> result = new HashMap<>();
            result.put("query", query);
            result.put("response", response);
            result.put("processingTimeMs", duration);
            result.put("service", "basic");
            
            log.info("Query processed in {}ms", duration);
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("Error processing query", e);
            return ResponseEntity.internalServerError()
                .body(Map.of(
                    "error", "Failed to process query",
                    "message", e.getMessage()
                ));
        }
    }

    /**
     * Enhanced RAG query endpoint with adjacent chunks
     */
    @GetMapping("/ask-enhanced")
    public ResponseEntity<Map<String, Object>> askDocumentEnhanced(
            @RequestParam(value = "query", defaultValue = "What is RAG?") String query) {
        
        if (enhancedRagService == null) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "Enhanced RAG service not available"));
        }
        
        log.info("Received enhanced query: {}", query);
        long startTime = System.currentTimeMillis();
        
        try {
            String response = enhancedRagService.generateResponse(query);
            long duration = System.currentTimeMillis() - startTime;
            
            Map<String, Object> result = new HashMap<>();
            result.put("query", query);
            result.put("response", response);
            result.put("processingTimeMs", duration);
            result.put("service", "enhanced");
            result.put("features", Map.of(
                "adjacentChunks", true,
                "contextOptimization", true,
                "deduplication", true
            ));
            
            log.info("Enhanced query processed in {}ms", duration);
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("Error processing enhanced query", e);
            return ResponseEntity.internalServerError()
                .body(Map.of(
                    "error", "Failed to process enhanced query",
                    "message", e.getMessage()
                ));
        }
    }

    /**
     * POST endpoint for longer queries
     */
    @PostMapping("/ask")
    public ResponseEntity<Map<String, Object>> askDocumentPost(
            @RequestBody Map<String, String> request) {
        
        String query = request.getOrDefault("query", "");
        boolean enhanced = Boolean.parseBoolean(
            request.getOrDefault("enhanced", "false")
        );
        
        if (query.isEmpty()) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "Query cannot be empty"));
        }
        
        log.info("Received POST query (enhanced={}): {}", enhanced, query);
        long startTime = System.currentTimeMillis();
        
        try {
            String response;
            if (enhanced && enhancedRagService != null) {
                response = enhancedRagService.generateResponse(query);
            } else {
                response = ragService.generateResponse(query);
            }
            
            long duration = System.currentTimeMillis() - startTime;
            
            Map<String, Object> result = new HashMap<>();
            result.put("query", query);
            result.put("response", response);
            result.put("processingTimeMs", duration);
            result.put("service", enhanced ? "enhanced" : "basic");
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("Error processing POST query", e);
            return ResponseEntity.internalServerError()
                .body(Map.of(
                    "error", "Failed to process query",
                    "message", e.getMessage()
                ));
        }
    }

    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("service", "RAG");
        health.put("chunkedMode", true);
        health.put("enhancedAvailable", enhancedRagService != null);
        
        return ResponseEntity.ok(health);
    }
}