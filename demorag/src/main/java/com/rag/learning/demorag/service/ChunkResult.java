package com.rag.learning.demorag.service;

import lombok.Data;

/**
 * A document chunk returned from a similarity search, together with the
 * metadata of its owning document. Shared between {@link RagService} and
 * {@link EnhancedRagService} so both retrieval flows build LLM context from
 * the same shape.
 */
@Data
public class ChunkResult {
    private String id;
    private String documentId;
    private Integer chunkIndex;
    private String content;
    private Integer tokenCount;
    private String chunkMetadata;
    private String filename;
    private String fileType;
    private Integer totalChunks;
    private String docMetadata;

    /**
     * NEGATIVE_INNER_PRODUCT distance from the query embedding, as configured
     * via spring.ai.vectorstore.pgvector.distance-type. Null when the chunk
     * wasn't retrieved via similarity search (e.g. an adjacent chunk fetched
     * by document id + index).
     */
    private Double distance;

    /**
     * Similarity score derived from distance (lower distance = more similar,
     * so similarity = -distance). Defaults to 0.0 when distance is unknown.
     */
    public double getSimilarity() {
        return distance == null ? 0.0 : -distance;
    }
}
