package com.learning.rag.repository;

import com.learning.rag.entity.DocumentChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, String> {
    
    /**
     * Find all chunks for a specific document
     */
    List<DocumentChunk> findByDocumentIdOrderByChunkIndex(String documentId);
    
    /**
     * Count chunks for a document
     */
    long countByDocumentId(String documentId);
    
    /**
     * Find similar chunks using cosine similarity
     * Note: This is a native query for vector similarity search
     */
    @Query(value = "SELECT * FROM document_chunks " +
                   "ORDER BY embedding <=> CAST(:queryEmbedding AS vector) " +
                   "LIMIT :limit", 
           nativeQuery = true)
    List<DocumentChunk> findSimilarChunks(
        @Param("queryEmbedding") String queryEmbedding,
        @Param("limit") int limit
    );
}