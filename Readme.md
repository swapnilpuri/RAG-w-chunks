# RAG Pipeline with Chunked Documents & Streaming Ingestion

A production-ready Retrieval-Augmented Generation (RAG) system built with Spring Boot, Apache Camel, PostgreSQL with pgvector, and Google Gemini. Features streaming document ingestion to handle large files without memory issues, and intelligent chunked retrieval for optimal context generation.

[![Java](https://img.shields.io/badge/Java-17+-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-14+-blue.svg)](https://www.postgresql.org/)
[![Apache Camel](https://img.shields.io/badge/Apache%20Camel-4.x-red.svg)](https://camel.apache.org/)

## 📋 Table of Contents

- [Overview](#overview)
- [Architecture](#architecture)
- [Key Features](#key-features)
- [Prerequisites](#prerequisites)
- [Installation](#installation)
- [Configuration](#configuration)
- [Usage](#usage)
- [API Reference](#api-reference)
- [Sample Queries](#sample-queries)
- [Project Structure](#project-structure)
- [Performance Considerations](#performance-considerations)
- [Troubleshooting](#troubleshooting)
- [Contributing](#contributing)
- [License](#license)

## 🎯 Overview

This project implements a complete RAG (Retrieval-Augmented Generation) pipeline that:

1. **Ingests documents** (PDF, DOCX, TXT) using streaming to avoid memory issues
2. **Chunks documents** intelligently with configurable overlap
3. **Generates embeddings** using Google Gemini's text-embedding-004 model
4. **Stores vectors** in PostgreSQL with pgvector extension
5. **Retrieves relevant chunks** using similarity search
6. **Generates responses** with context-aware LLM (Google Gemini)

### Why This Architecture?

- ✅ **Handles large documents** (100MB+) without OOM errors
- ✅ **Better retrieval precision** through chunking
- ✅ **Scalable ingestion** with Apache Camel
- ✅ **Production-ready** with proper error handling and monitoring
- ✅ **Memory efficient** with streaming and immediate chunk persistence

## 🏗️ Architecture

### High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        DOCUMENT INGESTION                        │
├─────────────────────────────────────────────────────────────────┤
│                                                                   │
│  ┌──────────┐   ┌──────────────┐   ┌─────────────────────┐     │
│  │  Apache  │──▶│  Streaming   │──▶│  Chunking Service   │     │
│  │  Camel   │   │  Document    │   │  (1000 char chunks) │     │
│  │  Route   │   │  Processor   │   │  (200 char overlap) │     │
│  └──────────┘   └──────────────┘   └──────────┬──────────┘     │
│                                                 │                 │
│                                                 ▼                 │
│                        ┌─────────────────────────────┐           │
│                        │   Embedding Service         │           │
│                        │   (Google Gemini API)       │           │
│                        │   768-dimensional vectors   │           │
│                        └────────────┬────────────────┘           │
│                                     │                             │
│                                     ▼                             │
│                        ┌─────────────────────────────┐           │
│                        │   PostgreSQL + pgvector     │           │
│                        │   - document_master         │           │
│                        │   - document_chunks         │           │
│                        └─────────────────────────────┘           │
│                                                                   │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                         QUERY & RETRIEVAL                        │
├─────────────────────────────────────────────────────────────────┤
│                                                                   │
│  ┌──────────┐   ┌──────────────┐   ┌─────────────────────┐     │
│  │   User   │──▶│   RAG        │──▶│  Embedding Service  │     │
│  │  Query   │   │ Controller   │   │  (Query Embedding)  │     │
│  └──────────┘   └──────────────┘   └──────────┬──────────┘     │
│                                                 │                 │
│                                                 ▼                 │
│                        ┌─────────────────────────────┐           │
│                        │   Vector Similarity Search  │           │
│                        │   (PostgreSQL pgvector)     │           │
│                        │   NEGATIVE_INNER_PRODUCT    │           │
│                        └────────────┬────────────────┘           │
│                                     │                             │
│                                     ▼                             │
│                        ┌─────────────────────────────┐           │
│                        │   Context Builder           │           │
│                        │   - Group by document       │           │
│                        │   - Sort by chunk index     │           │
│                        │   - Add adjacent chunks     │           │
│                        └────────────┬────────────────┘           │
│                                     │                             │
│                                     ▼                             │
│                        ┌─────────────────────────────┐           │
│                        │   LLM (Google Gemini)       │           │
│                        │   Generate Response         │           │
│                        └─────────────────────────────┘           │
│                                                                   │
└─────────────────────────────────────────────────────────────────┘
```

### Component Overview

#### 1. **Ingestion Pipeline**

**Components:**
- `StreamingDocumentProcessor`: Reads documents as streams (page-by-page for PDFs)
- `StreamingChunkingService`: Creates overlapping chunks from streams
- `StreamingIngestionService`: Orchestrates the ingestion process
- `EmbeddingService`: Generates embeddings via Gemini API

**Flow:**
```
Document → Stream Reader → Chunker → Embedding → Database
         (page-by-page)  (1000 chars) (768-dim)  (immediate save)
```

**Key Features:**
- Constant memory usage (~10KB buffer)
- Processes chunks immediately (no accumulation)
- Separate transactions per chunk
- Handles documents of any size

#### 2. **Query & Retrieval Pipeline**

**Components:**
- `RagService`: Basic retrieval with chunk grouping
- `EnhancedRagService`: Advanced retrieval with adjacent chunks
- `EmbeddingService`: Generates query embeddings
- `RagController`: REST API endpoints

**Flow:**
```
Query → Embedding → Similarity Search → Chunk Retrieval
                                      ↓
                         Group by Document → Sort by Index
                                      ↓
                         Adjacent Chunks (optional) → Build Context
                                      ↓
                         LLM → Response
```

**Key Features:**
- Vector similarity search using pgvector
- Intelligent context building
- Optional adjacent chunk retrieval
- Configurable similarity thresholds

### Database Schema

```sql
-- Document metadata
CREATE TABLE document_master (
    id TEXT PRIMARY KEY,
    filename VARCHAR(500) NOT NULL UNIQUE,
    file_type VARCHAR(10) NOT NULL,
    total_chunks INTEGER NOT NULL DEFAULT 0,
    metadata JSONB,
    ingested_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Document chunks with embeddings
CREATE TABLE document_chunks (
    id TEXT PRIMARY KEY,
    document_id TEXT NOT NULL,
    chunk_index INTEGER NOT NULL,
    content TEXT NOT NULL,
    embedding vector(768),
    token_count INTEGER,
    metadata JSONB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY(document_id) REFERENCES document_master(id) ON DELETE CASCADE,
    UNIQUE(document_id, chunk_index)
);

-- Vector similarity index
CREATE INDEX chunks_embedding_idx ON document_chunks 
USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);
```

## ✨ Key Features

### Document Ingestion

- **Streaming Architecture**: Processes documents without loading entire content into memory
- **Multiple Formats**: Supports PDF, DOCX, and TXT files
- **Smart Chunking**: Configurable chunk size (default: 1000 chars) with overlap (default: 200 chars)
- **Sentence Boundary Detection**: Chunks at natural boundaries when possible
- **Apache Camel Integration**: File polling and batch processing capabilities
- **Memory Efficient**: Constant ~10KB memory usage regardless of document size

### Vector Search & Retrieval

- **High-Dimensional Vectors**: 768-dimensional embeddings from Gemini
- **Fast Similarity Search**: IVFFlat index for efficient vector queries
- **Configurable Retrieval**: Adjust top-K and similarity thresholds
- **Adjacent Chunk Fetching**: Retrieve surrounding chunks for better context
- **Smart Context Building**: Groups and orders chunks by document structure

### Query Processing

- **Dual RAG Modes**: Basic and Enhanced endpoints
- **Context Optimization**: Respects max context length limits
- **Chunk Deduplication**: Removes duplicate chunks from results
- **Source Attribution**: Tracks which documents contributed to responses
- **Streaming Responses**: Real-time LLM output

## 📦 Prerequisites

- **Java**: JDK 17 or higher
- **Maven**: 3.8+
- **PostgreSQL**: 14+ with pgvector extension
- **Google Cloud Account**: For Gemini API access

## 🚀 Installation

### 1. Clone the Repository

```bash
git clone https://github.com/swapnilpuri/RAG-w-chunks.git
cd RAG-w-chunks
```

### 2. Set Up PostgreSQL with pgvector

```bash
# Install PostgreSQL (Ubuntu/Debian)
sudo apt-get update
sudo apt-get install postgresql-14

# Install pgvector extension
cd /tmp
git clone https://github.com/pgvector/pgvector.git
cd pgvector
make
sudo make install

# Create database and enable extension
sudo -u postgres psql
CREATE DATABASE rag_db;
CREATE USER rag_user WITH PASSWORD 'rag_password';
GRANT ALL PRIVILEGES ON DATABASE rag_db TO rag_user;
\c rag_db
CREATE EXTENSION vector;
\q
```

### 3. Initialize Database Schema

```bash
psql -h localhost -U rag_user -d rag_db -f src/main/resources/init.sql
```

### 4. Configure Environment Variables

Create `.env` file or set environment variables:

```bash
export GOOGLE_API_KEY="your-gemini-api-key"
export DB_USERNAME="rag_user"
export DB_PASSWORD="rag_password"
```

### 5. Build the Project

```bash
mvn clean install
```

### 6. Run the Application

```bash
# With default configuration
mvn spring-boot:run

# With custom JVM settings for large documents
mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Xms512m -Xmx2g -XX:+UseG1GC"
```

The application will start on `http://localhost:8080`

## ⚙️ Configuration

### Application Properties

Key configurations in `application.properties`:

```properties
# Database Configuration
spring.datasource.url=jdbc:postgresql://localhost:5432/rag_db
spring.datasource.username=${DB_USERNAME:rag_user}
spring.datasource.password=${DB_PASSWORD:rag_password}

# PGVector Configuration
spring.ai.vectorstore.pgvector.dimension=768
spring.ai.vectorstore.pgvector.distance-type=NEGATIVE_INNER_PRODUCT
spring.ai.vectorstore.pgvector.table-name=document_chunks

# RAG Retrieval Configuration
rag.retrieval.top-k=5
rag.retrieval.similarity-threshold=0.3
rag.retrieval.max-context-length=8000
rag.retrieval.fetch-adjacent-chunks=true

# Embedding Configuration
embedding.dimension=768
embedding.gemini.api-key=${GOOGLE_API_KEY}
embedding.gemini.api-url=https://generativelanguage.googleapis.com/v1beta/models/text-embedding-004:embedContent

# Chunking Configuration
chunking.chunk-size=1000
chunking.chunk-overlap=200
```

### JVM Configuration

For production deployments:

```bash
java -Xms512m \
     -Xmx2g \
     -XX:+UseG1GC \
     -XX:MaxGCPauseMillis=200 \
     -XX:+UseStringDeduplication \
     -jar rag-pipeline.jar
```

## 📖 Usage

### Document Ingestion

#### Via Apache Camel (File Polling)

Place documents in the configured input directory:

```bash
cp your-document.pdf ./documents/input/
```

The Camel route will automatically:
1. Pick up the file
2. Process it through streaming ingestion
3. Chunk the content
4. Generate embeddings
5. Store in database

#### Programmatic Ingestion

```java
@Autowired
private StreamingIngestionService ingestionService;

public void ingestDocument(File file) {
    try {
        ingestionService.ingestDocumentStreaming(
            file,
            "document.pdf",
            "pdf"
        );
        System.out.println("Document ingested successfully!");
    } catch (Exception e) {
        e.printStackTrace();
    }
}
```

### Querying the System

#### Basic Query (GET)

```bash
curl -G "http://localhost:8080/api/rag/v1/ask" \
  --data-urlencode "query=What is machine learning?"
```

#### Enhanced Query (POST)

```bash
curl -X POST "http://localhost:8080/api/rag/v1/ask" \
  -H "Content-Type: application/json" \
  -d '{
    "query": "Explain neural networks in detail",
    "enhanced": "true"
  }'
```

## 🔌 API Reference

### Health Check

**Endpoint:** `GET /api/rag/v1/health`

**Response:**
```json
{
  "status": "UP",
  "service": "RAG",
  "chunkedMode": true,
  "enhancedAvailable": true
}
```

### Basic RAG Query

**Endpoint:** `GET /api/rag/v1/ask`

**Parameters:**
- `query` (required): The user's question

**Response:**
```json
{
  "query": "What is machine learning?",
  "response": "Machine learning is a subset of artificial intelligence...",
  "processingTimeMs": 2341,
  "service": "basic"
}
```

### Enhanced RAG Query

**Endpoint:** `GET /api/rag/v1/ask-enhanced`

**Parameters:**
- `query` (required): The user's question

**Response:**
```json
{
  "query": "Explain deep learning",
  "response": "Deep learning is...",
  "processingTimeMs": 3124,
  "service": "enhanced",
  "features": {
    "adjacentChunks": true,
    "contextOptimization": true,
    "deduplication": true
  }
}
```

### POST Query Endpoint

**Endpoint:** `POST /api/rag/v1/ask`

**Request Body:**
```json
{
  "query": "How does Kafka work with Spark?",
  "enhanced": "true"
}
```

**Response:** Same as GET endpoints

## 🧪 Sample Queries

Based on the ingested documents, here are test queries:

### 1. Credit Risk Assessment Query

```bash
curl -G "http://localhost:8080/api/rag/v1/ask" \
  --data-urlencode "query=What are the production issues with the credit risk assessment system?"
```

### 2. Apache Spark Components

```bash
curl -X POST "http://localhost:8080/api/rag/v1/ask" \
  -H "Content-Type: application/json" \
  -d '{
    "query": "What are the main components of Apache Spark and what does each do?",
    "enhanced": "true"
  }'
```

### 3. Git Branching

```bash
curl -G "http://localhost:8080/api/rag/v1/ask" \
  --data-urlencode "query=How do I create and merge branches in Git?"
```

### 4. Kafka Streams

```bash
curl -X POST "http://localhost:8080/api/rag/v1/ask" \
  -H "Content-Type: application/json" \
  -d '{
    "query": "Explain Kafka Streams and its key features"
  }'
```

### 5. Cross-Document Query

```bash
curl -X POST "http://localhost:8080/api/rag/v1/ask" \
  -H "Content-Type: application/json" \
  -d '{
    "query": "How can I use Kafka with Spark for real-time data processing? What are the integration steps?",
    "enhanced": "true"
  }'
```

### Additional Test Queries

```bash
# Spark MLlib capabilities
curl -G "http://localhost:8080/api/rag/v1/ask-enhanced" \
  --data-urlencode "query=What machine learning capabilities does Spark MLlib provide?"

# Git workflow
curl -G "http://localhost:8080/api/rag/v1/ask-enhanced" \
  --data-urlencode "query=What is the basic Git workflow for staging and committing changes?"

# Kafka Connect
curl -G "http://localhost:8080/api/rag/v1/ask" \
  --data-urlencode "query=What is the Kafka Connect framework and how do I use it?"
```

## 📁 Project Structure

This is a Maven multi-module project — see [MAVEN_STRUCTURE.md](MAVEN_STRUCTURE.md)
for the full build details. The two Java services each own their half of the
pipeline; the Angular app is an independent, standalone project.

```
RAG-w-chunks/
├── pom.xml                                    # Parent POM (shared dependency versions)
│
├── document-streaming-ingestion-service/      # INGESTION: watches a folder, chunks, embeds, stores
│   ├── src/main/java/com/learning/rag/
│   │   ├── config/            # EmbeddingConfig, HibernateConfig, PGVectorType
│   │   ├── entity/             # Document, DocumentChunk
│   │   ├── processor/         # StreamingDocumentProcessor (page-by-page PDF/DOCX/XLSX reads)
│   │   ├── repository/        # DocumentRepository, DocumentChunkRepository
│   │   ├── route/             # DocumentIngestionRoute (Apache Camel file-polling route)
│   │   └── service/           # ChunkingService, StreamingChunkingService,
│   │                           # StreamingIngestionService, EmbeddingService (Gemini)
│   ├── src/main/resources/application.properties
│   ├── init.sql
│   ├── docker-compose.yaml
│   └── knowledgebase/          # small curated sample docs (pdf/docx/xlsx)
│
├── demorag/                                   # QUERY & RETRIEVAL: the RAG API
│   ├── src/main/java/com/rag/learning/demorag/
│   │   ├── config/             # EmbeddingConfig, WebConfig, Config
│   │   ├── controller/         # RagController, DebugController
│   │   └── service/            # RagService (basic), EnhancedRagService (adjacent
│   │                            # chunks + dedup + context optimization), EmbeddingService
│   └── src/main/resources/
│       ├── application.properties
│       └── rag/system-prompt-template.st
│
└── chat-ui/                                   # Angular chat frontend (independent project)
    └── src/app/
        ├── services/chat.ts     # calls demorag's /api/rag/v1/ask
        └── app.ts / app.html
```

## ⚡ Performance Considerations

### Memory Usage

| Approach | Memory for 100MB Document |
|----------|---------------------------|
| **Original (Non-Streaming)** | ~220MB+ |
| **Streaming (This Project)** | ~12KB constant |

### Response Times

| Query Complexity | Basic RAG | Enhanced RAG |
|-----------------|-----------|--------------|
| Simple (3 chunks) | 2-4s | 3-5s |
| Medium (5 chunks) | 3-5s | 4-6s |
| Complex (10 chunks) | 4-7s | 5-8s |

### Optimization Tips

1. **Tune Similarity Threshold**:
   - Too low (0.1-0.2): May return irrelevant chunks
   - Balanced (0.3-0.5): Recommended starting point
   - Too high (0.6-0.8): May return too few results

2. **Adjust Top-K**:
   - Small (3-5): Faster, more focused
   - Medium (5-7): Balanced
   - Large (8-10): Slower, more comprehensive

3. **Context Length**:
   - Small (2000-4000): Faster LLM, focused
   - Medium (6000-8000): Balanced
   - Large (10000-16000): Slower, more comprehensive

4. **Database Indexing**:
   ```sql
   -- Increase lists for larger datasets
   DROP INDEX chunks_embedding_idx;
   CREATE INDEX chunks_embedding_idx ON document_chunks 
   USING ivfflat (embedding vector_cosine_ops) WITH (lists = 500);
   ```

## 🐛 Troubleshooting

### Common Issues

#### 1. No Chunks Retrieved

**Symptoms:**
```
INFO - Found 0 candidate chunks before filtering
```

**Solutions:**
- Check if chunks have embeddings: `SELECT COUNT(*) FROM document_chunks WHERE embedding IS NOT NULL`
- Verify embedding service is working
- Lower similarity threshold: `rag.retrieval.similarity-threshold=0.1`

#### 2. All Chunks Filtered Out

**Symptoms:**
```
INFO - Found 10 candidate chunks before filtering
INFO - After filtering: 0 chunks above similarity threshold
```

**Solutions:**
- Lower threshold in `application.properties`
- Check distance metric matches embeddings (NEGATIVE_INNER_PRODUCT)
- Verify embeddings are being generated correctly

#### 3. Slow Queries

**Diagnosis:**
```sql
EXPLAIN ANALYZE
SELECT * FROM document_chunks
ORDER BY embedding <#> '[...]'::vector
LIMIT 5;
```

**Solutions:**
- Ensure IVFFlat index exists
- Tune index parameters (increase lists)
- Optimize chunk size
- Add database connection pooling

#### 4. Out of Memory During Ingestion

**Solutions:**
- Verify using streaming services (not old ingestion service)
- Reduce chunk size: `chunking.chunk-size=500`
- Increase JVM heap: `-Xmx4g`
- Check if files are being properly streamed

### Debug Endpoints

```bash
# Check database state
curl "http://localhost:8080/api/rag/v1/stats"

# View memory usage
curl "http://localhost:8080/api/documents/health"
```

### Logging

Enable debug logging in `application.properties`:

```properties
logging.level.com.rag.learning.demorag=DEBUG
logging.level.org.springframework.ai.vectorstore=DEBUG
```

## 🤝 Contributing

Contributions are welcome! Please follow these steps:

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

### Development Guidelines

- Follow Java code conventions
- Add unit tests for new features
- Update documentation
- Ensure all tests pass before submitting PR

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🙏 Acknowledgments

- [Spring AI](https://docs.spring.io/spring-ai/reference/) for AI integration framework
- [pgvector](https://github.com/pgvector/pgvector) for PostgreSQL vector extension
- [Apache Camel](https://camel.apache.org/) for integration patterns
- [Google Gemini](https://ai.google.dev/) for embeddings and LLM
- [Apache PDFBox](https://pdfbox.apache.org/) for PDF processing
- [Apache POI](https://poi.apache.org/) for document processing

## 📞 Support

For questions or issues, open an issue in the [GitHub repository](https://github.com/swapnilpuri/RAG-w-chunks/issues).

## 🗺️ Roadmap

- [ ] Add support for more document formats (HTML, Markdown)
- [ ] Implement hybrid search (vector + keyword)
- [ ] Add reranking step after retrieval
- [ ] Support for multiple embedding models
- [ ] Add caching layer for frequent queries
- [ ] Implement batch query processing
- [ ] Add monitoring dashboard
- [ ] Support for multi-lingual documents
- [ ] Add document versioning
- [ ] Implement access control and authentication

---

**Built with ❤️ using Spring Boot, Apache Camel, PostgreSQL, and Google Gemini**