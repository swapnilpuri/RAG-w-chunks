# Maven Multi-Module Structure

This repository uses a Maven parent project for the Java backend services. The Angular frontend remains an independent Node/Angular project under `chat-ui`.

## Structure

```text
RAG-w-chunks/
  pom.xml
  demorag/
    pom.xml
    src/
  document-streaming-ingestion-service/
    pom.xml
    src/
  chat-ui/
    package.json
    src/
```

## Parent POM

The root `pom.xml` defines shared build and dependency management for the Java modules.

Key settings:

- Group ID: `com.rag.learning`
- Artifact ID: `rag-parent`
- Packaging: `pom`
- Spring Boot parent: `3.5.6`
- Java version: `21`
- Spring AI version: `1.1.0-M3`
- Apache Camel version: `4.2.0`

## Java Modules

### `demorag`

Purpose: RAG query API.

Responsibilities:

- Accept user questions through REST endpoints
- Generate query embeddings
- Retrieve relevant chunks from pgvector
- Build grounded context
- Call Gemini for answer generation

Default port:

```text
8081
```

### `document-streaming-ingestion-service`

Purpose: document ingestion pipeline.

Responsibilities:

- Watch an input folder with Apache Camel
- Extract text from documents
- Split content into chunks
- Generate Gemini embeddings
- Store document metadata and chunk vectors in PostgreSQL

## Build Commands

Build and test all Java modules from the repository root:

```bash
mvn clean test
```

Package all Java modules:

```bash
mvn clean package
```

Build a single module:

```bash
mvn -pl demorag clean test
mvn -pl document-streaming-ingestion-service clean test
```

Build a module and its required dependencies:

```bash
mvn -pl demorag -am clean test
```

## Frontend

The Angular UI is not part of the Maven reactor. Build it separately:

```bash
cd chat-ui
npm install
npm run build
```

## Notes

- Keep common Java dependency versions in the parent POM when possible.
- Keep module-specific dependencies in the module POMs.
- Do not commit local `.env` files or credentials.
- Use the root README for end-to-end setup and the module READMEs for focused development notes.