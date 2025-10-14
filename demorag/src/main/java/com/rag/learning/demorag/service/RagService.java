package com.rag.learning.demorag.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

/**
 * Service handling the Retrieval Augmented Generation (RAG) process with chunked documents.
 */
@Service
@Slf4j
public class RagService {

    private final VectorStore vectorStore;
    private final GoogleGenAiChatModel chatModel;
    private final EmbeddingService embeddingService;
    private final JdbcTemplate jdbcTemplate;

    @Value("classpath:/rag/system-prompt-template.st")
    private Resource systemPromptTemplate;

    @Value("${rag.retrieval.top-k:5}")
    private int topK;

    @Value("${rag.retrieval.similarity-threshold:0.3}")
    private double similarityThreshold;

    public RagService(VectorStore vectorStore, GoogleGenAiChatModel chatModel, 
                     EmbeddingService embeddingService, JdbcTemplate jdbcTemplate) {
        this.vectorStore = vectorStore;
        this.chatModel = chatModel;
        this.embeddingService = embeddingService;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Executes the RAG flow to generate a grounded response using chunked documents.
     */
    public String generateResponse(String query) {
        log.info("=== RAG Processing Started (Chunked Documents) ===");
        log.info("Query: {}", query);
        log.info("Top-K: {}, Similarity Threshold: {}", topK, similarityThreshold);
        
        try {
            // 1. RETRIEVAL: Get relevant chunks from database
            List<ChunkResult> relevantChunks = retrieveRelevantChunks(query);
            
            log.info("Retrieved {} relevant chunks for query", relevantChunks.size());
            
            if (relevantChunks.isEmpty()) {
                log.warn("No chunks found for query: {}", query);
                return generateNoContextResponse(query);
            }

            // 2. AUGMENTATION: Build context from chunks
            String context = buildContextFromChunks(relevantChunks);
            
            log.info("Context built: {} characters from {} chunks across {} documents", 
                context.length(), relevantChunks.size(), 
                relevantChunks.stream().map(c -> c.getDocumentId()).distinct().count());
            
            if (context.trim().isEmpty()) {
                log.warn("Context is empty after extraction");
                return generateNoContextResponse(query);
            }

            // 3. PROMPT CONSTRUCTION
            SystemPromptTemplate systemPrompt = new SystemPromptTemplate(systemPromptTemplate);
            Message systemMessage = systemPrompt.createMessage(Map.of("context", context));
            UserMessage userMessage = new UserMessage(query);
            Prompt prompt = new Prompt(List.of(systemMessage, userMessage));
            
            log.info("Prompt created with {} context chars, calling LLM...", context.length());
            
            // 4. GENERATION
            return chatModel.stream(prompt)
                   .map(chatResponse -> chatResponse.getResult().getOutput().getText())
                   .reduce("", (a, b) -> a + b)
                   .block();
            
        } catch (Exception e) {
            log.error("Error during RAG processing for query: {}", query, e);
            return generateErrorResponse(query, e);
        }
    }

    /**
     * Retrieve relevant chunks using vector similarity search
     */
    private List<ChunkResult> retrieveRelevantChunks(String query) {
        try {
            // Generate query embedding
            log.info("Generating query embedding with Gemini...");
            float[] queryEmbedding = embeddingService.generateEmbedding(query);
            log.info("Query embedding generated: {} dimensions", queryEmbedding.length);
            
            // Convert embedding to PostgreSQL vector format
            String vectorString = "[" + 
                java.util.stream.IntStream.range(0, queryEmbedding.length)
                    .mapToObj(i -> String.format("%.8f", queryEmbedding[i]))
                    .collect(Collectors.joining(",")) + 
                "]";

            // Query for similar chunks using the distance operator matching your config
            // Using <#> for NEGATIVE_INNER_PRODUCT as configured in application.properties
            String sql = """
                SELECT 
                    dc.id,
                    dc.document_id,
                    dc.chunk_index,
                    dc.content,
                    dc.metadata as chunk_metadata,
                    dm.filename,
                    dm.file_type,
                    dm.metadata as doc_metadata,
                    (dc.embedding <#> ?::vector) AS distance
                FROM document_chunks dc
                JOIN document_master dm ON dc.document_id = dm.id
                WHERE dc.embedding IS NOT NULL
                ORDER BY dc.embedding <#> ?::vector
                LIMIT ?
                """;

            org.postgresql.util.PGobject pgVectorObject = new org.postgresql.util.PGobject();
            pgVectorObject.setType("vector");
            pgVectorObject.setValue(vectorString);

            List<ChunkResult> chunks = jdbcTemplate.query(
                sql,
                ps -> {
                    ps.setObject(1, pgVectorObject);
                    ps.setObject(2, pgVectorObject);
                    ps.setInt(3, topK);
                },
                (rs, rowNum) -> {
                    ChunkResult chunk = new ChunkResult();
                    chunk.setId(rs.getString("id"));
                    chunk.setDocumentId(rs.getString("document_id"));
                    chunk.setChunkIndex(rs.getInt("chunk_index"));
                    chunk.setContent(rs.getString("content"));
                    chunk.setChunkMetadata(rs.getString("chunk_metadata"));
                    chunk.setFilename(rs.getString("filename"));
                    chunk.setFileType(rs.getString("file_type"));
                    chunk.setDocMetadata(rs.getString("doc_metadata"));
                    chunk.setDistance(rs.getDouble("distance"));
                    return chunk;
                }
            );

            log.info("Found {} candidate chunks before filtering", chunks.size());
            
            if (chunks.isEmpty()) {
                // Debug: check if chunks exist
                Integer totalChunks = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM document_chunks WHERE embedding IS NOT NULL", 
                    Integer.class
                );
                log.error("Query returned 0 results, but {} chunks exist with embeddings!", totalChunks);
                return List.of();
            }

            // Filter by similarity threshold
            // For NEGATIVE_INNER_PRODUCT, lower distance = more similar
            // Convert to similarity score for filtering
            List<ChunkResult> filteredChunks = chunks.stream()
                .filter(chunk -> {
                    // For negative inner product, negate the distance to get similarity
                    double similarity = -chunk.getDistance();
                    boolean passes = similarity >= similarityThreshold;
                    log.debug("Chunk from {}, index {}: similarity={:.3f}, passes={}", 
                        chunk.getFilename(), chunk.getChunkIndex(), similarity, passes);
                    return passes;
                })
                .collect(Collectors.toList());

            log.info("After filtering: {} chunks above similarity threshold {}", 
                filteredChunks.size(), similarityThreshold);

            return filteredChunks;

        } catch (Exception e) {
            log.error("Error retrieving chunks", e);
            return List.of();
        }
    }

    /**
     * Build context string from retrieved chunks
     * Groups chunks by document and maintains chunk order
     */
    private String buildContextFromChunks(List<ChunkResult> chunks) {
        // Group chunks by document
        Map<String, List<ChunkResult>> chunksByDocument = chunks.stream()
            .collect(Collectors.groupingBy(ChunkResult::getDocumentId));

        StringBuilder contextBuilder = new StringBuilder();

        // Process each document
        chunksByDocument.forEach((docId, docChunks) -> {
            // Sort chunks by index to maintain document order
            docChunks.sort((a, b) -> Integer.compare(a.getChunkIndex(), b.getChunkIndex()));
            
            String filename = docChunks.get(0).getFilename();
            String fileType = docChunks.get(0).getFileType();
            
            contextBuilder.append("=== Source: ")
                .append(filename)
                .append(" (")
                .append(fileType)
                .append(") ===\n\n");
            
            // Add chunks
            for (ChunkResult chunk : docChunks) {
                double similarity = -chunk.getDistance(); // Convert to similarity
                log.info("Including chunk {} from {} (similarity: {:.3f})", 
                    chunk.getChunkIndex(), filename, similarity);
                
                contextBuilder.append(chunk.getContent())
                    .append("\n\n");
            }
            
            contextBuilder.append("---\n\n");
        });

        return contextBuilder.toString();
    }

    /**
     * Alternative method: Try using VectorStore (if it supports chunked structure)
     */
    private List<Document> retrieveDocumentsViaVectorStore(String query) {
        log.debug("Attempting VectorStore retrieval...");

        try {
            // Strategy 1: SearchRequest with parameters
            log.debug("Strategy 1: Using SearchRequest.builder()");
            SearchRequest searchRequest = SearchRequest.builder()
                    .query(query)
                    .topK(topK)
                    .similarityThreshold(similarityThreshold)
                    .build();
            
            List<Document> documents = vectorStore.similaritySearch(searchRequest);
            
            if (!documents.isEmpty()) {
                log.info("VectorStore Strategy 1 succeeded: Found {} documents", documents.size());
                return documents;
            }
            
        } catch (Exception e) {
            log.warn("VectorStore Strategy 1 failed: {}", e.getMessage());
        }
        
        try {
            // Strategy 2: Simple search
            log.debug("Strategy 2: Using simple similaritySearch()");
            List<Document> documents = vectorStore.similaritySearch(query);
            
            if (!documents.isEmpty()) {
                log.info("VectorStore Strategy 2 succeeded: Found {} documents", documents.size());
                return documents;
            }
            
        } catch (Exception e) {
            log.warn("VectorStore Strategy 2 failed: {}", e.getMessage());
        }
        
        log.warn("VectorStore retrieval failed, using direct database query");
        return List.of();
    }

    /**
     * Generate response when no context is found
     */
    private String generateNoContextResponse(String query) {
        log.info("Generating no-context response");
        String noContextMessage = "I apologize, but I couldn't find any relevant information " +
            "in the document database to answer your question: \"" + query + "\". " +
            "Please ensure documents are properly ingested or try rephrasing your question.";
        UserMessage userMessage = new UserMessage(noContextMessage);
        Prompt prompt = new Prompt(List.of(userMessage));
        return chatModel.stream(prompt)
            .map(chatResponse -> chatResponse.getResult().getOutput().getText())
            .reduce("", (a, b) -> a + b)
            .block();
    }
    
    /**
     * Generate response when an error occurs
     */
    private String generateErrorResponse(String query, Exception e) {
        log.error("Generating error response due to exception");
        String errorMessage = "I encountered an error while processing your query: \"" + query + "\". " +
            "Error: " + e.getMessage() + ". Please try again or contact support.";
        UserMessage userMessage = new UserMessage(errorMessage);
        Prompt prompt = new Prompt(List.of(userMessage));
        return chatModel.stream(prompt)
            .map(chatResponse -> chatResponse.getResult().getOutput().getText())
            .reduce("", (a, b) -> a + b)
            .block();
    }

    /**
     * Inner class to hold chunk retrieval results
     */
    private static class ChunkResult {
        private String id;
        private String documentId;
        private Integer chunkIndex;
        private String content;
        private String chunkMetadata;
        private String filename;
        private String fileType;
        private String docMetadata;
        private Double distance;

        // Getters and setters
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        
        public String getDocumentId() { return documentId; }
        public void setDocumentId(String documentId) { this.documentId = documentId; }
        
        public Integer getChunkIndex() { return chunkIndex; }
        public void setChunkIndex(Integer chunkIndex) { this.chunkIndex = chunkIndex; }
        
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        
        public String getChunkMetadata() { return chunkMetadata; }
        public void setChunkMetadata(String chunkMetadata) { this.chunkMetadata = chunkMetadata; }
        
        public String getFilename() { return filename; }
        public void setFilename(String filename) { this.filename = filename; }
        
        public String getFileType() { return fileType; }
        public void setFileType(String fileType) { this.fileType = fileType; }
        
        public String getDocMetadata() { return docMetadata; }
        public void setDocMetadata(String docMetadata) { this.docMetadata = docMetadata; }
        
        public Double getDistance() { return distance; }
        public void setDistance(Double distance) { this.distance = distance; }
    }
}