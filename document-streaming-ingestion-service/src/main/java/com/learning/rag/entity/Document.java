package com.learning.rag.entity;

import com.learning.rag.config.PGVectorType;
import com.pgvector.PGvector;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "document_master")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Document {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private String id;
    
     @Column(nullable = false, length = 500, unique = true)
    private String filename;
    
    @Column(name = "file_type", nullable = false, length = 10)
    private String fileType;
    
    @Column(name = "total_chunks", nullable = false)
    private Integer totalChunks = 0;
    
    @Column(columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String metadata;
    
    @Column(name = "ingested_at")
    private LocalDateTime ingestedAt;
    
    @OneToMany(mappedBy = "document", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DocumentChunk> chunks = new ArrayList<>();
    
    public void addChunk(DocumentChunk chunk) {
        chunks.add(chunk);
        chunk.setDocument(this);
        this.totalChunks = chunks.size();
    }

     @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
        if (ingestedAt == null) {
            ingestedAt = LocalDateTime.now();
        }
    }
}
