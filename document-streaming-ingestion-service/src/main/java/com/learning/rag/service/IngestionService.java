package com.learning.rag.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learning.rag.entity.Document;
import com.learning.rag.entity.DocumentChunk;
import com.learning.rag.processor.DocumentProcessor;
import com.learning.rag.repository.DocumentChunkRepository;
import com.learning.rag.repository.DocumentRepository;
import com.pgvector.PGvector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class IngestionService {
    
    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository chunkRepository;
    private final DocumentProcessor documentProcessor;
    private final EmbeddingService embeddingService;
    private final ChunkingService chunkingService;
    private final ObjectMapper objectMapper;
    
    @Transactional
    public void ingestDocument(File file, String filename, String fileType) throws Exception {
        log.info("Starting ingestion for file: {}", filename);
        
        // Check if document already exists
        if (documentRepository.existsByFilename(filename)) {
            log.warn("Document already exists: {}", filename);
            throw new IllegalStateException("Document already ingested: " + filename);
        }
        
        // Extract text from document
        log.debug("Extracting text from {}", filename);
        String content = documentProcessor.extractText(file, fileType);
        
        if (content == null || content.trim().isEmpty()) {
            throw new IllegalStateException("No content extracted from file: " + filename);
        }
        
        log.info("Extracted {} characters from {}", content.length(), filename);
        
        // Split content into chunks
        log.debug("Chunking document: {}", filename);
        List<String> chunks = chunkingService.chunkText(content);
        log.info("Created {} chunks for {}", chunks.size(), filename);
        
        // Create document metadata
        Map<String, Object> docMetadata = new HashMap<>();
        docMetadata.put("fileSize", file.length());
        docMetadata.put("originalPath", file.getAbsolutePath().replace("\\", "/"));
        docMetadata.put("contentLength", content.length());
        docMetadata.put("totalChunks", chunks.size());
        
        // Create document entity
        Document document = new Document();
        document.setFilename(filename);
        document.setFileType(fileType);
        document.setTotalChunks(chunks.size());
        document.setMetadata(convertMapToJson(docMetadata));
        
        // Save document first to get ID
        document = documentRepository.save(document);
        log.info("Saved document with ID: {}", document.getId());
        
        // Process each chunk
        for (int i = 0; i < chunks.size(); i++) {
            String chunkText = chunks.get(i);
            log.debug("Processing chunk {}/{} for {}", i + 1, chunks.size(), filename);
            
            // Generate embedding for chunk
            float[] embeddingArray = embeddingService.generateEmbedding(chunkText);
            PGvector embedding = new PGvector(embeddingArray);
            
            // Create chunk metadata
            Map<String, Object> chunkMetadata = new HashMap<>();
            chunkMetadata.put("characterCount", chunkText.length());
            chunkMetadata.put("estimatedTokens", chunkingService.estimateTokenCount(chunkText));
            
            // Create chunk entity
            DocumentChunk chunk = new DocumentChunk();
            chunk.setDocument(document);
            chunk.setChunkIndex(i);
            chunk.setContent(chunkText);
            chunk.setEmbedding(embedding);
            chunk.setTokenCount(chunkingService.estimateTokenCount(chunkText));
            chunk.setMetadata(convertMapToJson(chunkMetadata));
            
            // Save chunk
            chunkRepository.save(chunk);
            log.debug("Saved chunk {}/{}", i + 1, chunks.size());
        }
        
        log.info("Successfully ingested document: {} with {} chunks", filename, chunks.size());
    }
    
    /**
     * Convert Map to JSON string using Jackson ObjectMapper
     * This properly escapes special characters including Windows backslashes
     */
    private String convertMapToJson(Map<String, Object> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            log.error("Failed to convert metadata to JSON", e);
            throw new RuntimeException("Failed to serialize metadata", e);
        }
    }
}