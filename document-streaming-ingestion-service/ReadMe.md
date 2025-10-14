# Document Ingestion Service for RAG Application

A microservice built with Apache Camel and Spring Boot that monitors a folder for documents (PDF, XLSX, DOCX), extracts text content, generates embeddings, and stores them in PostgreSQL with pgvector for RAG (Retrieval-Augmented Generation) applications.

## Features

- 📁 **Automatic File Monitoring**: Watches a specific folder for new documents
- 📄 **Multi-Format Support**: Handles PDF, XLSX, and DOCX files
- 🔄 **Automatic Processing**: Ingests documents into pgvector database
- 📦 **Smart File Management**: Moves processed files to archive or failure folders
- 🎯 **Vector Embeddings**: Generates embeddings for semantic search
- 🗄️ **PostgreSQL + pgvector**: Stores documents with vector embeddings
- ⚡ **Apache Camel**: Robust integration framework for file processing

## Prerequisites

- Java 17 or higher
- Maven 3.6+
- Docker and Docker Compose

## Project Structure

```
document-ingestion-service/
├── src/main/java/com/example/
│   ├── DocumentIngestionApplication.java
│   ├── entity/
│   │   └── Document.java
│   ├── repository/
│   │   └── DocumentRepository.java
│   ├── processor/
│   │   └── DocumentProcessor.java
│   ├── service/
│   │   ├── EmbeddingService.java
│   │   └── IngestionService.java
│   └── route/
│       └── DocumentIngestionRoute.java
├── src/main/resources/
│   └── application.properties
├── docker-compose.yml
├── init.sql
└── pom.xml
```

## Setup Instructions

### 1. Start PostgreSQL with pgvector

```bash
# Start the database
docker-compose up -d

# Verify it's running
docker ps
```

The database will be initialized with the schema defined in `init.sql`.

### 2. Build the Application

```bash
mvn clean install
```

### 3. Run the Application

```bash
mvn spring-boot:run
```

The application will:
- Create `input`, `archive`, and `failure` folders automatically
- Start monitoring the `input` folder for new documents
- Process files every 5 seconds

## Usage

### Adding Documents for Ingestion

1. **Copy or move files** to the `./input` folder
2. **Supported formats**: PDF, XLSX, DOCX
3. **The service will automatically**:
   - Detect the new file
   - Extract text content
   - Generate vector embeddings
   - Store in the PostgreSQL database
   - Move the file to `./archive` (success) or `./failure` (error)

### Example Workflow

```bash
# Add a document to process
cp /path/to/your/document.pdf ./input/

# Check logs to see processing
tail -f logs/application.log

# Successful files will be in ./archive
ls ./archive/

# Failed files will be in ./failure
ls ./failure/
```

## Configuration

Edit `application.properties` to customize settings:

```properties
# Folder paths
file.monitor.input-folder=./input
file.monitor.archive-folder=./archive
file.monitor.failure-folder=./failure

# Polling interval (milliseconds)
file.monitor.poll-interval=5000

# Database connection
spring.datasource.url=jdbc:postgresql://localhost:5432/rag_db
spring.datasource.username=rag_user
spring.datasource.password=rag_password
```

## Database Schema

The `documents` table includes:

- `id`: Primary key
- `filename`: Original filename (unique)
- `file_type`: File extension (pdf, xlsx, docx)
- `content`: Extracted text content
- `embedding`: Vector embedding (384 dimensions)
- `metadata`: JSON metadata (file size, path, etc.)
- `ingested_at`: Timestamp of ingestion

## Vector Search Example

Once documents are ingested, you can perform similarity searches:

```sql
-- Find similar documents using cosine similarity
SELECT 
    filename, 
    content,
    1 - (embedding <=> '[0.1, 0.2, ...]'::vector) as similarity
FROM documents
ORDER BY embedding <=> '[0.1, 0.2, ...]'::vector
LIMIT 5;
```

## Important Notes

### Embedding Service

⚠️ **The current `EmbeddingService` uses a simple hash-based approach for demonstration.**

For production use, replace it with a real embedding model:

#### Option 1: OpenAI Embeddings API
```java
// Add OpenAI Java SDK dependency
// Call OpenAI API to generate embeddings
```

#### Option 2: Local Model (Sentence-BERT)
```java
// Use ONNX Runtime or DJL
// Load a sentence-transformer model locally
```

#### Option 3: Hugging Face API
```java
// Call Hugging Face Inference API
// Use models like 'sentence-transformers/all-MiniLM-L6-v2'
```

### Customizing Vector Dimensions

If you change the embedding dimension:

1. Update `EmbeddingService.generateEmbedding()` to return correct size
2. Update `init.sql`: Change `vector(384)` to your dimension
3. Update `Document.java` entity if needed

## Monitoring and Logs

The application logs provide detailed information:

```
INFO - New file detected: document.pdf
INFO - Processing file: document.pdf of type: pdf
DEBUG - Extracting text from document.pdf
DEBUG - Generating embeddings for document.pdf
INFO - Successfully ingested document: document.pdf
INFO - Moving file to archive: document.pdf
```

## Error Handling

Files fail to process if:
- Document already exists (duplicate filename)
- No content can be extracted
- File format is unsupported
- Database connection issues

Failed files are automatically moved to the `./failure` folder.

## Stopping the Service

```bash
# Stop the application
Ctrl+C

# Stop the database
docker-compose down

# Stop and remove volumes (clears all data)
docker-compose down -v
```

## Testing

To test the service:

1. **Add a test PDF**:
```bash
echo "Test content" > test.txt
# Convert to PDF or use any existing PDF
cp sample.pdf ./input/
```

2. **Check database**:
```bash
docker exec -it pgvector-db psql -U rag_user -d rag_db

# Query documents
SELECT filename, file_type, LENGTH(content), ingested_at FROM documents;
```

3. **Verify file movement**:
```bash
ls ./archive/  # Should contain successfully processed files
ls ./failure/  # Should contain failed files
```

## Troubleshooting

### Database Connection Issues
```bash
# Check if database is running
docker ps | grep pgvector

# Check database logs
docker logs pgvector-db

# Test connection
docker exec -it pgvector-db psql -U rag_user -d rag_db
```

### Files Not Being Processed
- Check folder permissions
- Verify file extensions (must be .pdf, .xlsx, or .docx)
- Check application logs for errors
- Ensure polling interval has elapsed

### Out of Memory Errors
- Increase JVM heap size: `-Xmx2g`
- Process files in smaller batches
- Consider chunking large documents

## Advanced Configuration

### Adjust Polling Interval
```properties
# Check every 10 seconds instead of 5
file.monitor.poll-interval=10000
```

### Change File Patterns
Edit `DocumentIngestionRoute.java`:
```java
from("file:" + inputFolder 
    + "?include=.*\\.(pdf|txt|doc)"  // Add more extensions
    + "...")
```

### Enable Parallel Processing
```java
from("file:...")
    .threads(5)  // Process 5 files in parallel
    .to("direct:processDocument");
```

## Integration with RAG Application

This service provides the ingestion layer. To complete your RAG application:

1. **Query Service**: Build an API to search documents using vector similarity
2. **Retrieval Logic**: Fetch top-k similar documents for a query
3. **LLM Integration**: Send retrieved context to your LLM for generation

Example query service endpoint:
```java
@GetMapping("/search")
public List<Document> search(@RequestParam String query) {
    float[] queryEmbedding = embeddingService.generateEmbedding(query);
    // Perform vector similarity search
    return documentRepository.findSimilar(queryEmbedding, limit);
}
```

## License

This project is provided as-is for educational and development purposes.

## Support

For issues or questions, please refer to:
- Apache Camel Documentation: https://camel.apache.org/
- pgvector Documentation: https://github.com/pgvector/pgvector
- Spring Boot Documentation: https://spring.io/projects/spring-boot