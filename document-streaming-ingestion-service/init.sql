-- Enable pgvector extension
CREATE EXTENSION IF NOT EXISTS vector;

-- Documents master table (used in RagService JOIN: document_master dm)
CREATE TABLE IF NOT EXISTS document_master (
     id TEXT PRIMARY KEY DEFAULT gen_random_uuid()::TEXT, 
    filename VARCHAR(500) NOT NULL,
    file_type VARCHAR(10) NOT NULL,
    total_chunks INTEGER NOT NULL DEFAULT 0,
    metadata JSONB,
    ingested_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(filename)
);

-- Document chunks table
CREATE TABLE IF NOT EXISTS document_chunks (
     id TEXT PRIMARY KEY DEFAULT gen_random_uuid()::TEXT,
    document_id TEXT NOT NULL,
    chunk_index INTEGER NOT NULL,
    content TEXT NOT NULL,
    embedding vector(3072),
    token_count INTEGER,
    metadata JSONB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_document
        FOREIGN KEY(document_id) 
        REFERENCES document_master(id)
        ON DELETE CASCADE,
    UNIQUE(document_id, chunk_index)
);

-- HNSW index with vector_ip_ops to match <#> operator used in RagService.java
-- ivfflat cannot be used as it has a 2000 dimension limit
CREATE INDEX IF NOT EXISTS document_chunks_embedding_idx 
ON document_chunks USING hnsw (embedding vector_ip_ops)
WITH (m = 16, ef_construction = 64);

-- Create index for document lookup
CREATE INDEX IF NOT EXISTS chunks_document_id_idx ON document_chunks(document_id);

-- Create index for filename lookup
CREATE INDEX IF NOT EXISTS document_master_filename_idx ON document_master(filename);