# RAG Pipeline with Apache Camel, pgvector, Gemini, and Angular

This repository demonstrates a full Retrieval-Augmented Generation (RAG) application built as a multi-module project. It includes a document ingestion service, a RAG query API, PostgreSQL vector storage with pgvector, and an Angular chat UI.

The project is designed for developers who want to understand how the parts of a RAG system fit together beyond a simple LLM API call: document ingestion, parsing, chunking, embedding, vector indexing, retrieval, prompt grounding, response generation, and a usable frontend.

## What This Project Shows

- File-based document ingestion with Apache Camel
- Text extraction from PDF, DOCX, XLSX, and text-like content
- Chunking with overlap and boundary-aware splitting
- Gemini embeddings using `gemini-embedding-001`
- PostgreSQL + pgvector storage with 3072-dimensional vectors
- HNSW vector index using inner-product search
- Basic and enhanced RAG retrieval flows
- Adjacent chunk enrichment for better context continuity
- Grounded answer generation with Google Gemini
- Angular chat UI connected to the Spring Boot RAG API

## Architecture

```text
                         Document Ingestion

  Input folder
      |
      v
  Apache Camel file route
      |
      v
  StreamingDocumentProcessor
      |
      v
  StreamingChunkingService
      |
      v
  Gemini EmbeddingService
      |
      v
  PostgreSQL + pgvector
  document_master + document_chunks


                         Query and Retrieval

  Angular Chat UI
      |
      v
  RagController
      |
      v
  Query embedding
      |
      v
  pgvector similarity search
      |
      v
  Context builder
      |
      v
  Gemini chat model
      |
      v
  Grounded response
```

## Modules

```text
RAG-w-chunks/
  pom.xml
  Readme.md
  MAVEN_STRUCTURE.md

  synthetic-data/
    documents/
    evaluation/
    scripts/

  document-streaming-ingestion-service/
    init.sql
    docker-compose.yaml
    .env.example
    knowledgebase/
    src/main/java/com/learning/rag/
      route/DocumentIngestionRoute.java
      processor/StreamingDocumentProcessor.java
      service/StreamingChunkingService.java
      service/StreamingIngestionService.java
      service/EmbeddingService.java
      entity/Document.java
      entity/DocumentChunk.java

  demorag/
    src/main/java/com/rag/learning/demorag/
      controller/RagController.java
      controller/DebugController.java
      service/RagService.java
      service/EnhancedRagService.java
      service/EmbeddingService.java
    src/main/resources/rag/system-prompt-template.st

  chat-ui/
    src/app/app.ts
    src/app/app.html
    src/app/services/chat.ts
```

## Key Design Choices

### 1. Separate Ingestion and Query Services

The project separates document ingestion from the query API:

- `document-streaming-ingestion-service` watches an input folder, extracts text, chunks documents, creates embeddings, and stores them.
- `demorag` serves the RAG API and focuses on retrieval, prompt construction, and answer generation.

This keeps the architecture closer to a real-world RAG system where ingestion and query workloads often scale differently.

### 2. Chunk-Oriented Document Processing

Documents are split into overlapping chunks before embedding. The current defaults are:

- Chunk size: `1000` characters
- Chunk overlap: `200` characters
- Buffer size: `8192`

Chunking improves retrieval precision because the vector search can match the most relevant document sections instead of searching only at whole-document level.

### 3. pgvector for Retrieval

The system stores embeddings in PostgreSQL using pgvector:

```sql
embedding vector(3072)
```

The current schema uses an HNSW index:

```sql
CREATE INDEX IF NOT EXISTS document_chunks_embedding_idx
ON document_chunks USING hnsw (embedding vector_ip_ops)
WITH (m = 16, ef_construction = 64);
```

The query service uses the pgvector `<#>` operator for negative inner product distance, matching the configured retrieval strategy.

### 4. Enhanced Retrieval With Adjacent Chunks

The enhanced RAG service starts with the top matching chunks, then optionally fetches neighboring chunks from the same source document. This helps preserve context when a relevant answer spans chunk boundaries.

The enhanced flow includes:

- Query embedding
- Top-K vector search
- Similarity threshold filtering
- Previous/next chunk enrichment
- Deduplication
- Context length control
- Prompt grounding

### 5. Prompt Grounding

The system prompt instructs the model to answer only from retrieved context:

```text
You are a helpful and professional assistant. Your task is to answer the user's question based ONLY on the provided context.
If the context does not contain the answer, state clearly that the answer cannot be found in the provided documents.
Do not use any external knowledge.
```

This is important for reducing hallucination and making out-of-scope behavior testable.

## Prerequisites

- Java 21 or higher
- Maven 3.8+
- Node.js and npm for the Angular UI
- Docker and Docker Compose
- Google Gemini API key

## Configuration

Do not commit real credentials. Use environment variables or a local `.env` file.

The ingestion service includes an example file:

```text
document-streaming-ingestion-service/.env.example
```

Example values:

```bash
GOOGLE_API_KEY=your-gemini-api-key
DB_USERNAME=rag_user
DB_PASSWORD=rag_password
```

Important configuration values:

```properties
embedding.dimension=3072
embedding.gemini.api-url=https://generativelanguage.googleapis.com/v1beta/models/gemini-embedding-001:embedContent
spring.ai.vectorstore.pgvector.distance-type=NEGATIVE_INNER_PRODUCT
spring.ai.vectorstore.pgvector.dimensions=3072
```

## Database Setup

Start PostgreSQL with pgvector from the ingestion service directory:

```bash
cd document-streaming-ingestion-service
docker compose up -d
```

The schema is defined in:

```text
document-streaming-ingestion-service/init.sql
```

The main tables are:

```sql
CREATE TABLE IF NOT EXISTS document_master (
    id TEXT PRIMARY KEY DEFAULT gen_random_uuid()::TEXT,
    filename VARCHAR(500) NOT NULL,
    file_type VARCHAR(10) NOT NULL,
    total_chunks INTEGER NOT NULL DEFAULT 0,
    metadata JSONB,
    ingested_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(filename)
);

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
```

## Build and Test

From the repository root:

```bash
mvn clean test
```

Build the Angular UI:

```bash
cd chat-ui
npm install
npm run build
```

## Running the Application

Run the ingestion service:

```bash
cd document-streaming-ingestion-service
mvn spring-boot:run
```

Run the RAG API:

```bash
cd demorag
mvn spring-boot:run
```

The RAG API runs on:

```text
http://localhost:8081
```

Run the Angular UI:

```bash
cd chat-ui
npm start
```

The UI runs on:

```text
http://localhost:4200
```

## Ingesting Documents

Place supported documents into the ingestion input folder:

```text
document-streaming-ingestion-service/input/
```

The Camel route will:

1. Detect supported files.
2. Move them into an in-progress state.
3. Extract text.
4. Split text into chunks.
5. Generate embeddings.
6. Store metadata and vectors in PostgreSQL.
7. Move successfully processed files to `archive`.
8. Move failed files to `failure`.

Supported file types include:

- PDF
- DOCX
- XLSX
- TXT
- Markdown (.md, .markdown)

## API Reference

### Health Check

```http
GET http://localhost:8081/api/rag/v1/health
```

Example response:

```json
{
  "status": "UP",
  "service": "RAG",
  "chunkedMode": true,
  "enhancedAvailable": true
}
```

### Basic RAG Query

```http
GET http://localhost:8081/api/rag/v1/ask?query=What%20is%20Apache%20Spark?
```

Example with curl:

```bash
curl -G "http://localhost:8081/api/rag/v1/ask" \
  --data-urlencode "query=What are the main components of Apache Spark?"
```

### Enhanced RAG Query

```http
GET http://localhost:8081/api/rag/v1/ask-enhanced?query=How%20does%20Kafka%20work?
```

Example with curl:

```bash
curl -G "http://localhost:8081/api/rag/v1/ask-enhanced" \
  --data-urlencode "query=Explain Kafka Streams and its key features"
```

### POST Query

```http
POST http://localhost:8081/api/rag/v1/ask
Content-Type: application/json
```

```json
{
  "query": "How can Kafka and Spark be used together?",
  "enhanced": "true"
}
```

Example response:

```json
{
  "query": "How can Kafka and Spark be used together?",
  "response": "Based on the provided documents...",
  "processingTimeMs": 3124,
  "service": "enhanced"
}
```

## Sample Questions

The repository includes sample question sets for validating retrieval and grounding behavior:

- `SampleQuestions.md`
- `SampleQuestions2.md`

Good test categories include:

- Single-document factual questions
- Cross-document questions
- Summary questions
- Out-of-scope questions that should not be answered from general model knowledge

Example:

```text
Question: What are the main components of Apache Spark and what does each component do?
Expected source: ApacheSparkCheatSheet.pdf
```

Out-of-scope example:

```text
Question: How do you implement OAuth 2.0 authentication in a microservices architecture?
Expected behavior: The answer should state that the information is not available in the provided documents.
```

## Synthetic Data and Evaluation Direction

This project includes a repeatable synthetic RAG evaluation corpus:

```text
synthetic-data/
  documents/
  evaluation/
    questions.jsonl
```

Each evaluation record can capture:

```json
{
  "id": "app-a-001",
  "question": "What payment methods does AppA Payment Gateway support?",
  "expected_source": "AppA_PaymentGateway.docx",
  "expected_answer_contains": ["credit card", "UPI", "net banking"],
  "should_answer": true,
  "difficulty": "easy",
  "category": "single-document"
}
```

This makes the project stronger as a portfolio piece because it demonstrates not only RAG implementation, but also evaluation thinking: source correctness, hallucination checks, latency, and answer grounding.

## Testing Strategy

The Java project includes:

- Spring Boot context-load tests for both services
- Unit tests for regular chunking
- Unit tests for streaming chunking

Recommended next tests:

- Retrieval query tests against a controlled pgvector dataset
- Controller tests for `/api/rag/v1/ask`
- Out-of-scope query tests
- Synthetic evaluation runner for repeatable RAG quality checks
- Angular component/service tests with mocked API responses

## Performance Notes

This project uses a chunk-oriented ingestion flow to avoid holding all chunks and embeddings in memory at once. Exact memory usage depends on file type and parser behavior:

- TXT can be streamed directly.
- PDF text is extracted page-by-page, but PDFBox still manages the PDF document internally.
- DOCX and XLSX parsing may involve library-level in-memory structures.

For a blog post or benchmark, prefer measured claims such as:

- file size
- number of pages/rows
- chunk count
- ingestion time
- peak heap usage
- query latency

## Troubleshooting

### No Chunks Retrieved

Check that chunks exist and have embeddings:

```sql
SELECT COUNT(*) FROM document_chunks WHERE embedding IS NOT NULL;
```

Lower the similarity threshold temporarily:

```properties
rag.retrieval.similarity-threshold=0.1
```

### Embedding Dimension Errors

Make sure these values are aligned:

- `embedding.dimension=3072`
- `spring.ai.vectorstore.pgvector.dimensions=3072`
- `document_chunks.embedding vector(3072)`
- `DocumentChunk` entity column definition

### Slow Similarity Search

Verify the HNSW index exists:

```sql
\d document_chunks
```

Confirm query plans:

```sql
EXPLAIN ANALYZE
SELECT *
FROM document_chunks
ORDER BY embedding <#> '[...]'::vector
LIMIT 5;
```

### UI Cannot Reach Backend

Confirm the RAG API is running:

```text
http://localhost:8081/api/rag/v1/health
```

Confirm the Angular environment points to:

```typescript
apiUrl: 'http://localhost:8081'
```

## Blog Post Outline

This project can support a strong technical blog post:

1. The problem: why naive LLM chat fails on private documents.
2. The architecture: ingestion service, vector database, query service, UI.
3. Document ingestion with Apache Camel.
4. Chunking strategy and overlap tradeoffs.
5. Embeddings and pgvector schema design.
6. Basic retrieval vs enhanced retrieval.
7. Prompt grounding and out-of-scope behavior.
8. Lessons learned from document formats and memory behavior.
9. How synthetic data can evaluate a RAG pipeline.
10. Roadmap: reranking, hybrid search, citations, evaluation runner, auth.

## Roadmap

- Add response citations with source filenames and chunk indexes
- Add a formal synthetic evaluation runner
- Add hybrid search with keyword + vector retrieval
- Add reranking after initial retrieval
- Add document upload API
- Add authentication and authorization
- Add document versioning
- Add observability for ingestion and query latency
- Add UI display for sources and retrieval metadata

## License

This project is licensed under the MIT License. See [LICENSE](LICENSE) for details.

## Acknowledgments

- Spring Boot
- Spring AI
- Apache Camel
- PostgreSQL and pgvector
- Google Gemini
- Apache PDFBox
- Apache POI
- Angular
