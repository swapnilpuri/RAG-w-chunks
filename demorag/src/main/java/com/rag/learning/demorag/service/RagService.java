package com.rag.learning.demorag.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

/**
 * Service handling the Retrieval Augmented Generation (RAG) process with chunked documents.
 */
@Service
@Slf4j
public class RagService {

    private final GoogleGenAiChatModel chatModel;
    private final EmbeddingService embeddingService;
    private final ChunkRetrievalService chunkRetrievalService;

    @Value("classpath:/rag/system-prompt-template.st")
    private Resource systemPromptTemplate;

    @Value("${rag.retrieval.top-k:5}")
    private int topK;

    @Value("${rag.retrieval.similarity-threshold:0.3}")
    private double similarityThreshold;

    public RagService(GoogleGenAiChatModel chatModel, EmbeddingService embeddingService,
                       ChunkRetrievalService chunkRetrievalService) {
        this.chatModel = chatModel;
        this.embeddingService = embeddingService;
        this.chunkRetrievalService = chunkRetrievalService;
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

            // Query for similar chunks (NEGATIVE_INNER_PRODUCT distance, as configured
            // in application.properties)
            List<ChunkResult> chunks = chunkRetrievalService.searchSimilarChunks(queryEmbedding, topK);

            log.info("Found {} candidate chunks before filtering", chunks.size());

            if (chunks.isEmpty()) {
                // Debug: check if chunks exist
                int totalChunks = chunkRetrievalService.countChunksWithEmbeddings();
                log.error("Query returned 0 results, but {} chunks exist with embeddings!", totalChunks);
                return List.of();
            }

            // Filter by similarity threshold
            List<ChunkResult> filteredChunks = chunks.stream()
                .filter(chunk -> {
                    double similarity = chunk.getSimilarity();
                    boolean passes = similarity >= similarityThreshold;
                    log.debug("Chunk from {}, index {}: similarity={}, passes={}",
                        chunk.getFilename(), chunk.getChunkIndex(),
                        String.format("%.3f", similarity), passes);
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
                log.info("Including chunk {} from {} (similarity: {})",
                    chunk.getChunkIndex(), filename, String.format("%.3f", chunk.getSimilarity()));

                contextBuilder.append(chunk.getContent())
                    .append("\n\n");
            }

            contextBuilder.append("---\n\n");
        });

        return contextBuilder.toString();
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
}
