package com.learning.rag.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learning.rag.entity.Document;
import com.learning.rag.entity.DocumentChunk;
import com.learning.rag.processor.StreamingDocumentProcessor;
import com.learning.rag.repository.DocumentChunkRepository;
import com.learning.rag.repository.DocumentRepository;
import com.pgvector.PGvector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@RequiredArgsConstructor
@Slf4j
public class StreamingIngestionService {
    
    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository chunkRepository;
    private final StreamingDocumentProcessor streamingProcessor;
    private final EmbeddingService embeddingService;
    private final StreamingChunkingService streamingChunkingService;
    private final ObjectMapper objectMapper;
    
    /**
     * Ingest document using streaming approach to avoid heap issues
     */
    @Transactional
    public void ingestDocumentStreaming(File file, String filename, String fileType) throws Exception {
        log.info("Starting streaming ingestion for file: {}", filename);
        
        // Check if document already exists
        if (documentRepository.existsByFilename(filename)) {
            log.warn("Document already exists: {}", filename);
            throw new IllegalStateException("Document already ingested: " + filename);
        }
        
        // Create document entity first (without knowing total chunks yet)
        Map<String, Object> docMetadata = new HashMap<>();
        docMetadata.put("fileSize", file.length());
        docMetadata.put("originalPath", file.getAbsolutePath().replace("\\", "/"));
        
        Document document = new Document();
        document.setFilename(filename);
        document.setFileType(fileType);
        document.setTotalChunks(0); // Will update later
        document.setMetadata(convertMapToJson(docMetadata));
        
        // Save document to get ID
        document = documentRepository.save(document);
        log.info("Created document with ID: {}", document.getId());
        
        final String documentId = document.getId();
        final AtomicInteger chunkCounter = new AtomicInteger(0);
        final AtomicInteger totalCharacters = new AtomicInteger(0);
        
        // Process document in streaming mode
        try (Reader reader = streamingProcessor.getDocumentReader(file, fileType)) {
            
            // Stream chunks and process each one immediately
            int totalChunks = streamingChunkingService.streamChunks(reader, chunk -> {
                try {
                    int chunkIndex = chunkCounter.getAndIncrement();
                    totalCharacters.addAndGet(chunk.length());
                    
                    // Process chunk in separate transaction to avoid holding large transaction
                    processAndSaveChunk(documentId, chunkIndex, chunk);
                    
                    log.debug("Processed chunk {}: {} characters", chunkIndex + 1, chunk.length());
                    
                    // Suggest GC every 50 chunks to help with memory
                    if ((chunkIndex + 1) % 50 == 0) {
                        System.gc();
                        log.debug("Suggested garbage collection after {} chunks", chunkIndex + 1);
                    }
                    
                } catch (Exception e) {
                    log.error("Error processing chunk {}", chunkCounter.get(), e);
                    throw new RuntimeException("Failed to process chunk: " + chunkCounter.get(), e);
                }
            });
            
            // Update document with final chunk count and content length
            updateDocumentMetadata(documentId, totalChunks, totalCharacters.get());
            
            log.info("Successfully ingested document: {} with {} chunks ({} total characters)", 
                    filename, totalChunks, totalCharacters.get());
            
        } catch (Exception e) {
            log.error("Error during streaming ingestion", e);
            // Optionally delete the document and all its chunks on failure
            documentRepository.deleteById(documentId);
            throw e;
        }
    }
    
    /**
     * Process and save individual chunk in separate transaction
     * This prevents holding a large transaction with all chunks
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processAndSaveChunk(String documentId, int chunkIndex, String chunkText) throws Exception {
        // Generate embedding
        float[] embeddingArray = embeddingService.generateEmbedding(chunkText);
        PGvector embedding = new PGvector(embeddingArray);
        
        // Create chunk metadata
        Map<String, Object> chunkMetadata = new HashMap<>();
        chunkMetadata.put("characterCount", chunkText.length());
        chunkMetadata.put("estimatedTokens", streamingChunkingService.estimateTokenCount(chunkText));
        
        // Create and save chunk
        DocumentChunk chunk = new DocumentChunk();
        
        // Set document reference using just the ID to avoid loading entire document
        Document docRef = new Document();
        docRef.setId(documentId);
        chunk.setDocument(docRef);
        
        chunk.setChunkIndex(chunkIndex);
        chunk.setContent(chunkText);
        chunk.setEmbedding(embedding);
        chunk.setTokenCount(streamingChunkingService.estimateTokenCount(chunkText));
        chunk.setMetadata(convertMapToJson(chunkMetadata));
        
        chunkRepository.save(chunk);
        
        // Clear persistence context to free memory
        chunkRepository.flush();
    }
    
    /**
     * Update document with final metadata
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateDocumentMetadata(String documentId, int totalChunks, int contentLength) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalStateException("Document not found: " + documentId));
        
        document.setTotalChunks(totalChunks);
        
        // Update metadata
        try {
            Map<String, Object> metadata = objectMapper.readValue(document.getMetadata(), Map.class);
            metadata.put("totalChunks", totalChunks);
            metadata.put("contentLength", contentLength);
            document.setMetadata(convertMapToJson(metadata));
        } catch (Exception e) {
            log.warn("Could not update document metadata", e);
        }
        
        documentRepository.save(document);
        log.info("Updated document {} with {} total chunks", documentId, totalChunks);
    }
    
    /**
     * Convert Map to JSON string
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