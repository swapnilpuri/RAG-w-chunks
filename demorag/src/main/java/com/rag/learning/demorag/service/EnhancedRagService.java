package com.rag.learning.demorag.service;

import java.util.*;
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
 * Enhanced RAG Service with advanced chunk handling capabilities
 */
@Service
@Slf4j
public class EnhancedRagService {

    private final GoogleGenAiChatModel chatModel;
    private final EmbeddingService embeddingService;
    private final ChunkRetrievalService chunkRetrievalService;

    @Value("classpath:/rag/system-prompt-template.st")
    private Resource systemPromptTemplate;

    @Value("${rag.retrieval.top-k:5}")
    private int topK;

    @Value("${rag.retrieval.similarity-threshold:0.3}")
    private double similarityThreshold;

    @Value("${rag.retrieval.max-context-length:8000}")
    private int maxContextLength;

    @Value("${rag.retrieval.fetch-adjacent-chunks:true}")
    private boolean fetchAdjacentChunks;

    public EnhancedRagService(GoogleGenAiChatModel chatModel, EmbeddingService embeddingService,
                               ChunkRetrievalService chunkRetrievalService) {
        this.chatModel = chatModel;
        this.embeddingService = embeddingService;
        this.chunkRetrievalService = chunkRetrievalService;
    }

    /**
     * Enhanced RAG response with adjacent chunk retrieval
     */
    public String generateResponse(String query) {
        log.info("=== Enhanced RAG Processing Started ===");
        log.info("Query: {}", query);
        log.info("Config: topK={}, threshold={}, maxContext={}, adjacent={}",
            topK, similarityThreshold, maxContextLength, fetchAdjacentChunks);

        try {
            // 1. Retrieve relevant chunks
            List<ChunkResult> relevantChunks = retrieveRelevantChunks(query);

            if (relevantChunks.isEmpty()) {
                log.warn("No chunks found for query");
                return generateNoContextResponse(query);
            }

            // 2. Optionally fetch adjacent chunks for better context
            if (fetchAdjacentChunks) {
                relevantChunks = enrichWithAdjacentChunks(relevantChunks);
                log.info("After enrichment: {} total chunks", relevantChunks.size());
            }

            // 3. Remove duplicate chunks (same chunk retrieved multiple times)
            relevantChunks = deduplicateChunks(relevantChunks);
            log.info("After deduplication: {} unique chunks", relevantChunks.size());

            // 4. Build context with length constraints
            String context = buildOptimalContext(relevantChunks);

            if (context.trim().isEmpty()) {
                return generateNoContextResponse(query);
            }

            // 5. Generate response
            return generateLLMResponse(query, context);

        } catch (Exception e) {
            log.error("Error during RAG processing", e);
            return generateErrorResponse(query, e);
        }
    }

    /**
     * Retrieve relevant chunks via similarity search
     */
    private List<ChunkResult> retrieveRelevantChunks(String query) {
        try {
            float[] queryEmbedding = embeddingService.generateEmbedding(query);
            log.info("Query embedding generated: {} dimensions", queryEmbedding.length);

            // Fetch more than topK so there's still `topK` left after threshold filtering
            List<ChunkResult> chunks = chunkRetrievalService.searchSimilarChunks(queryEmbedding, topK * 2);

            // Filter by threshold
            List<ChunkResult> filtered = chunks.stream()
                .filter(chunk -> chunk.getSimilarity() >= similarityThreshold)
                .limit(topK)
                .collect(Collectors.toList());

            log.info("Retrieved {} chunks (filtered from {} candidates)",
                filtered.size(), chunks.size());

            filtered.forEach(chunk ->
                log.debug("Chunk: doc={}, idx={}, sim={}",
                    chunk.getFilename(), chunk.getChunkIndex(), String.format("%.3f", chunk.getSimilarity())));

            return filtered;

        } catch (Exception e) {
            log.error("Error retrieving chunks", e);
            return List.of();
        }
    }

    /**
     * Fetch adjacent chunks for better context continuity
     */
    private List<ChunkResult> enrichWithAdjacentChunks(List<ChunkResult> originalChunks) {
        if (originalChunks.isEmpty()) {
            return originalChunks;
        }

        log.info("Enriching with adjacent chunks...");
        Set<String> chunkIds = new HashSet<>();
        List<ChunkResult> enrichedChunks = new ArrayList<>();

        for (ChunkResult chunk : originalChunks) {
            // Add the original chunk
            if (chunkIds.add(chunk.getId())) {
                enrichedChunks.add(chunk);
            }

            // Fetch previous chunk if exists
            if (chunk.getChunkIndex() > 0) {
                chunkRetrievalService.findChunkByIndex(chunk.getDocumentId(), chunk.getChunkIndex() - 1)
                    .filter(prevChunk -> chunkIds.add(prevChunk.getId()))
                    .ifPresent(prevChunk -> {
                        enrichedChunks.add(prevChunk);
                        log.debug("Added previous chunk: idx={}", prevChunk.getChunkIndex());
                    });
            }

            // Fetch next chunk if exists
            if (chunk.getChunkIndex() < chunk.getTotalChunks() - 1) {
                chunkRetrievalService.findChunkByIndex(chunk.getDocumentId(), chunk.getChunkIndex() + 1)
                    .filter(nextChunk -> chunkIds.add(nextChunk.getId()))
                    .ifPresent(nextChunk -> {
                        enrichedChunks.add(nextChunk);
                        log.debug("Added next chunk: idx={}", nextChunk.getChunkIndex());
                    });
            }
        }

        return enrichedChunks;
    }

    /**
     * Remove duplicate chunks
     */
    private List<ChunkResult> deduplicateChunks(List<ChunkResult> chunks) {
        Map<String, ChunkResult> uniqueChunks = new LinkedHashMap<>();

        for (ChunkResult chunk : chunks) {
            String key = chunk.getDocumentId() + "_" + chunk.getChunkIndex();
            uniqueChunks.putIfAbsent(key, chunk);
        }

        return new ArrayList<>(uniqueChunks.values());
    }

    /**
     * Build context with optimal length, respecting maxContextLength
     */
    private String buildOptimalContext(List<ChunkResult> chunks) {
        // Group by document
        Map<String, List<ChunkResult>> chunksByDoc = chunks.stream()
            .collect(Collectors.groupingBy(ChunkResult::getDocumentId));

        StringBuilder contextBuilder = new StringBuilder();
        int currentLength = 0;

        // Sort documents by highest similarity score
        List<Map.Entry<String, List<ChunkResult>>> sortedDocs = chunksByDoc.entrySet().stream()
            .sorted((e1, e2) -> {
                double maxSim1 = e1.getValue().stream()
                    .mapToDouble(ChunkResult::getSimilarity)
                    .max().orElse(0);
                double maxSim2 = e2.getValue().stream()
                    .mapToDouble(ChunkResult::getSimilarity)
                    .max().orElse(0);
                return Double.compare(maxSim2, maxSim1);
            })
            .collect(Collectors.toList());

        for (Map.Entry<String, List<ChunkResult>> entry : sortedDocs) {
            List<ChunkResult> docChunks = entry.getValue();

            // Sort chunks by index for proper reading order
            docChunks.sort(Comparator.comparingInt(ChunkResult::getChunkIndex));

            String filename = docChunks.get(0).getFilename();
            String header = String.format("=== Source: %s ===\n\n", filename);

            if (currentLength + header.length() > maxContextLength) {
                log.warn("Context length limit reached, truncating at {} characters", currentLength);
                break;
            }

            contextBuilder.append(header);
            currentLength += header.length();

            for (ChunkResult chunk : docChunks) {
                String chunkContent = chunk.getContent() + "\n\n";

                if (currentLength + chunkContent.length() > maxContextLength) {
                    log.warn("Context length limit reached at chunk {}, truncating",
                        chunk.getChunkIndex());
                    break;
                }

                contextBuilder.append(chunkContent);
                currentLength += chunkContent.length();

                log.debug("Added chunk {} from {} (sim={}, length={})",
                    chunk.getChunkIndex(), filename, String.format("%.3f", chunk.getSimilarity()),
                    chunkContent.length());
            }

            contextBuilder.append("---\n\n");
            currentLength += 5;
        }

        log.info("Context built: {} chars from {} documents",
            currentLength, chunksByDoc.size());

        return contextBuilder.toString();
    }

    /**
     * Generate LLM response
     */
    private String generateLLMResponse(String query, String context) {
        SystemPromptTemplate systemPrompt = new SystemPromptTemplate(systemPromptTemplate);
        Message systemMessage = systemPrompt.createMessage(Map.of("context", context));
        UserMessage userMessage = new UserMessage(query);
        Prompt prompt = new Prompt(List.of(systemMessage, userMessage));

        log.info("Calling LLM with context length: {}", context.length());

        return chatModel.stream(prompt)
            .map(chatResponse -> chatResponse.getResult().getOutput().getText())
            .reduce("", (a, b) -> a + b)
            .block();
    }

    private String generateNoContextResponse(String query) {
        String message = "I apologize, but I couldn't find any relevant information " +
            "to answer your question: \"" + query + "\". " +
            "Please ensure documents are ingested or rephrase your question.";
        return chatModel.stream(new Prompt(List.of(new UserMessage(message))))
            .map(r -> r.getResult().getOutput().getText())
            .reduce("", String::concat)
            .block();
    }

    private String generateErrorResponse(String query, Exception e) {
        String message = "Error processing query: \"" + query + "\". " +
            "Error: " + e.getMessage();
        return chatModel.stream(new Prompt(List.of(new UserMessage(message))))
            .map(r -> r.getResult().getOutput().getText())
            .reduce("", String::concat)
            .block();
    }
}
