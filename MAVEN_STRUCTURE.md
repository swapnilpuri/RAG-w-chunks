# Maven Multi-Module Structure

This project has been restructured as a Maven multi-module project to enable unified build management across all Java components.

## Project Structure

```
/workspace (root)
├── pom.xml                                    # Parent POM
├── demorag/                                   # RAG Demo Service Module
│   ├── pom.xml                               # Child POM
│   └── src/
└── document-streaming-ingestion-service/     # Document Ingestion Service Module
    ├── pom.xml                               # Child POM
    └── src/
```

## Key Changes Made

### 1. Parent POM (`/workspace/pom.xml`)
- **GroupId**: `com.rag.learning`
- **ArtifactId**: `rag-parent`
- **Packaging**: `pom`
- **Parent**: `spring-boot-starter-parent:3.5.6`
- **Modules**: Both `demorag` and `document-streaming-ingestion-service`

### 2. Centralized Dependency Management
The parent POM now manages versions for common dependencies:
- Spring AI: `1.1.0-M3`
- Apache Camel: `4.2.0`
- PGVector: `0.1.4`
- OkHttp: `4.12.0`
- Apache POI: `5.2.5`
- PDFBox: `3.0.0`
- Google Cloud AI Platform: `3.34.0`

### 3. Standardized GroupId
Both modules now use the consistent groupId: `com.rag.learning`

## How to Build

### Build All Modules
From the root directory (`/workspace`):
```bash
# Using Maven (if installed)
mvn clean install

# Using Maven wrapper from any sub-module
./demorag/mvnw clean install
# or
./document-streaming-ingestion-service/mvnw clean install
```

### Build Individual Modules
From the specific module directory:
```bash
# Build only demorag
cd demorag
./mvnw clean install

# Build only document-streaming-ingestion-service
cd document-streaming-ingestion-service
./mvnw clean install
```

### Common Maven Commands
- `mvn clean` - Clean all modules
- `mvn compile` - Compile all modules
- `mvn test` - Run tests for all modules
- `mvn package` - Package all modules
- `mvn install` - Install all modules to local repository

## Benefits of This Structure

1. **Unified Build Process**: Single command builds all Java components
2. **Consistent Dependency Versions**: Centralized version management prevents conflicts
3. **Simplified CI/CD**: One build pipeline can handle all modules
4. **Better IDE Support**: IDEs can recognize the multi-module structure
5. **Shared Configuration**: Common build plugins and settings are inherited

## Module Details

### demorag
- **Purpose**: RAG (Retrieval-Augmented Generation) demo service
- **Key Dependencies**: Spring Boot, Spring AI, Google GenAI, PGVector
- **Port**: Configured in application properties

### document-streaming-ingestion-service
- **Purpose**: Document processing and ingestion service
- **Key Dependencies**: Spring Boot, Apache Camel, PDFBox, Apache POI, PGVector
- **Features**: File processing, document parsing, vector storage

## Notes

- The `chat-ui` Angular project remains independent and is not part of the Maven build
- Each module retains its individual Maven wrapper for standalone builds
- All existing functionality is preserved
- Docker Compose configurations remain unchanged