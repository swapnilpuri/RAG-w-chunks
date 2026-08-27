# Document Streaming Ingestion Service

Spring Boot + Apache Camel service for ingesting documents into the RAG knowledge base. It watches an input folder, extracts text, chunks content, generates Gemini embeddings, and stores document chunks in PostgreSQL with pgvector.

## Supported Formats

The Camel route currently picks up:

- PDF (`.pdf`)
- DOCX (`.docx`)
- XLSX (`.xlsx`)
- plain text (`.txt`)
- Markdown (`.md`, `.markdown`)

Markdown and text files are read as UTF-8 text. PDF, DOCX, and XLSX are parsed with Apache PDFBox and Apache POI.

## Runtime Flow

```text
input folder
  -> Apache Camel file route
  -> StreamingDocumentProcessor
  -> StreamingChunkingService
  -> EmbeddingService
  -> PostgreSQL document_master + document_chunks
  -> archive or failure folder
```

## Configuration

Set credentials through environment variables or a local `.env` file. Do not commit real keys.

```bash
GOOGLE_API_KEY=your-gemini-api-key
DB_USERNAME=rag_user
DB_PASSWORD=rag_password
```

Important settings in `src/main/resources/application.properties`:

```properties
document.input.path=./input
document.archive.path=./archive
document.failure.path=./failure
document.poll-interval=5000

embedding.dimension=3072
embedding.gemini.api-key=${GOOGLE_API_KEY}
embedding.gemini.api-url=https://generativelanguage.googleapis.com/v1beta/models/gemini-embedding-001:embedContent
```

## Database

Start PostgreSQL with pgvector:

```bash
docker compose up -d
```

The schema is defined in `init.sql` and uses:

```sql
embedding vector(3072)
```

The vector index is HNSW with `vector_ip_ops`, matching the query service's negative inner product search.

## Running

From this module directory:

```bash
mvn spring-boot:run
```

The service will watch `./input` and move processed files to `./archive` or `./failure`.

## Ingesting Synthetic Markdown Documents

From the repository root, copy the synthetic Markdown corpus into the input folder:

```powershell
Copy-Item synthetic-data\documents\*.md document-streaming-ingestion-service\input\
```

On macOS/Linux:

```bash
cp synthetic-data/documents/*.md document-streaming-ingestion-service/input/
```

The route accepts `.md` and `.markdown`, so the synthetic corpus can be ingested without conversion to PDF or DOCX.

## Tests

Run all module tests:

```bash
mvn test
```

The tests include coverage for chunking and Markdown/text extraction.

## Operational Notes

- Duplicate filenames are rejected to prevent accidental re-ingestion.
- Successfully processed files are archived.
- Failed files are moved to the failure folder.
- Text and Markdown files stream directly as UTF-8.
- PDF/DOCX/XLSX memory behavior depends on parser internals, so benchmark before making strict memory claims.