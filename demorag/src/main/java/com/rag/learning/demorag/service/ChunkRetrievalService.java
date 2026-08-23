package com.rag.learning.demorag.service;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.postgresql.util.PGobject;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

/**
 * Shared vector-similarity chunk retrieval against document_chunks /
 * document_master. Both {@link RagService} (basic retrieval) and
 * {@link EnhancedRagService} (adjacent-chunk enrichment) go through this
 * class instead of each keeping its own copy of the SQL and row mapping.
 */
@Service
@Slf4j
public class ChunkRetrievalService {

    private final JdbcTemplate jdbcTemplate;

    public ChunkRetrievalService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final String SEARCH_SQL = """
        SELECT
            dc.id,
            dc.document_id,
            dc.chunk_index,
            dc.content,
            dc.token_count,
            dc.metadata as chunk_metadata,
            dm.filename,
            dm.file_type,
            dm.total_chunks,
            dm.metadata as doc_metadata,
            (dc.embedding <#> ?::vector) AS distance
        FROM document_chunks dc
        JOIN document_master dm ON dc.document_id = dm.id
        WHERE dc.embedding IS NOT NULL
        ORDER BY dc.embedding <#> ?::vector
        LIMIT ?
        """;

    private static final String FIND_BY_INDEX_SQL = """
        SELECT
            dc.id,
            dc.document_id,
            dc.chunk_index,
            dc.content,
            dc.token_count,
            dc.metadata as chunk_metadata,
            dm.filename,
            dm.file_type,
            dm.total_chunks,
            dm.metadata as doc_metadata
        FROM document_chunks dc
        JOIN document_master dm ON dc.document_id = dm.id
        WHERE dc.document_id = ? AND dc.chunk_index = ?
        """;

    /**
     * Find the top-N most similar chunks (by NEGATIVE_INNER_PRODUCT distance)
     * to the given query embedding, across all documents. Results are not
     * filtered by similarity threshold -- callers apply their own cutoff.
     */
    public List<ChunkResult> searchSimilarChunks(float[] queryEmbedding, int limit) {
        PGobject pgVector = toPgVector(queryEmbedding);
        List<ChunkResult> chunks = jdbcTemplate.query(
            SEARCH_SQL,
            ps -> {
                ps.setObject(1, pgVector);
                ps.setObject(2, pgVector);
                ps.setInt(3, limit);
            },
            this::mapChunkRow
        );
        log.debug("Vector search returned {} candidate chunks (limit={})", chunks.size(), limit);
        return chunks;
    }

    /**
     * Fetch a specific chunk by document id + chunk index, e.g. to pull the
     * chunk immediately before/after a retrieved chunk for extra context.
     */
    public Optional<ChunkResult> findChunkByIndex(String documentId, int chunkIndex) {
        try {
            ChunkResult chunk = jdbcTemplate.queryForObject(
                FIND_BY_INDEX_SQL, this::mapChunkRow, documentId, chunkIndex);
            return Optional.ofNullable(chunk);
        } catch (Exception e) {
            log.debug("Chunk not found: doc={}, idx={}", documentId, chunkIndex);
            return Optional.empty();
        }
    }

    /** Count of chunks that have an embedding -- used for "why 0 results" diagnostics. */
    public int countChunksWithEmbeddings() {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM document_chunks WHERE embedding IS NOT NULL", Integer.class);
        return count == null ? 0 : count;
    }

    private ChunkResult mapChunkRow(ResultSet rs, int rowNum) throws SQLException {
        ChunkResult chunk = new ChunkResult();
        chunk.setId(rs.getString("id"));
        chunk.setDocumentId(rs.getString("document_id"));
        chunk.setChunkIndex(rs.getInt("chunk_index"));
        chunk.setContent(rs.getString("content"));
        chunk.setTokenCount((Integer) rs.getObject("token_count"));
        chunk.setChunkMetadata(rs.getString("chunk_metadata"));
        chunk.setFilename(rs.getString("filename"));
        chunk.setFileType(rs.getString("file_type"));
        chunk.setTotalChunks((Integer) rs.getObject("total_chunks"));
        chunk.setDocMetadata(rs.getString("doc_metadata"));
        if (hasColumn(rs, "distance")) {
            chunk.setDistance(rs.getDouble("distance"));
        }
        return chunk;
    }

    private boolean hasColumn(ResultSet rs, String columnName) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        for (int i = 1; i <= meta.getColumnCount(); i++) {
            if (meta.getColumnLabel(i).equalsIgnoreCase(columnName)) {
                return true;
            }
        }
        return false;
    }

    private PGobject toPgVector(float[] embedding) {
        String vectorString = "[" +
            IntStream.range(0, embedding.length)
                .mapToObj(i -> String.format("%.8f", embedding[i]))
                .collect(Collectors.joining(",")) +
            "]";
        PGobject pgVectorObject = new PGobject();
        try {
            pgVectorObject.setType("vector");
            pgVectorObject.setValue(vectorString);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to build pgvector literal", e);
        }
        return pgVectorObject;
    }
}
