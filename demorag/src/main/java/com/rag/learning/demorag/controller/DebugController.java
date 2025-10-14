package com.rag.learning.demorag.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Debug controller to test VectorStore functionality
 */
@RestController
@RequestMapping("/api/debug")
@RequiredArgsConstructor
@Slf4j
public class DebugController {

    private final VectorStore vectorStore;

    /**
     * Test basic similarity search
     */
    @GetMapping("/search")
    public Map<String, Object> testSearch(@RequestParam String query) {
        log.info("Debug search for: {}", query);
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            // Try simple search first
            log.debug("Attempting simple similaritySearch...");
            List<Document> simpleResults = vectorStore.similaritySearch(query);
            result.put("simpleSearchCount", simpleResults.size());
            result.put("simpleSearchResults", simpleResults.stream()
                .map(doc -> Map.of(
                    "id", doc.getId(),
                    "contentPreview", doc.getText().substring(0, Math.min(200, doc.getText().length())),
                    "metadata", doc.getMetadata()
                ))
                .collect(Collectors.toList()));
            
            // Try with SearchRequest
            log.debug("Attempting SearchRequest with topK=5...");
            SearchRequest searchRequest = SearchRequest.builder()
                .query(query)
                .topK(5)
                .similarityThreshold(0.5)
                .build();
            
            List<Document> requestResults = vectorStore.similaritySearch(searchRequest);
            result.put("requestSearchCount", requestResults.size());
            result.put("requestSearchResults", requestResults.stream()
                .map(doc -> Map.of(
                    "id", doc.getId(),
                    "contentPreview", doc.getText().substring(0, Math.min(200, doc.getText().length())),
                    "metadata", doc.getMetadata()
                ))
                .collect(Collectors.toList()));
            
            result.put("success", true);
            
        } catch (Exception e) {
            log.error("Error during debug search", e);
            result.put("success", false);
            result.put("error", e.getMessage());
            result.put("stackTrace", e.toString());
        }
        
        return result;
    }

    /**
     * Add a test document to verify ingestion
     */
    @PostMapping("/add-test-document")
    public Map<String, Object> addTestDocument() {
        log.info("Adding test document...");
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            Document testDoc = new Document(
                "test-doc-123",
                "This is a test document about SAP integration. " +
                "The Payment Gateway system integrates with SAP for accounting. " +
                "It uses Kafka for data ingestion and machine learning models for fraud detection.",
                Map.of(
                    "filename", "test-document.txt",
                    "fileType", "txt",
                    "source", "debug-controller"
                )
            );
            
            vectorStore.add(List.of(testDoc));
            
            result.put("success", true);
            result.put("message", "Test document added successfully");
            result.put("documentId", testDoc.getId());
            
        } catch (Exception e) {
            log.error("Error adding test document", e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        
        return result;
    }

    /**
     * Check VectorStore configuration
     */
    @GetMapping("/config")
    public Map<String, Object> checkConfig() {
        Map<String, Object> result = new HashMap<>();
        
        result.put("vectorStoreClass", vectorStore.getClass().getName());
        result.put("vectorStoreString", vectorStore.toString());
        
        return result;
    }
}