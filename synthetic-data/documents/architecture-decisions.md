# Architecture Decision Records

## ADR-001: Use PostgreSQL with pgvector for Vector Storage

Status: Accepted

The RAG platform stores document embeddings in PostgreSQL using the pgvector extension. This was selected because the team already operates PostgreSQL, and it keeps document metadata and embeddings in one operational store.

Consequences:

- The system can join chunks with document metadata using SQL.
- The first production version can avoid a separate vector database.
- Index tuning is required as the corpus grows.

## ADR-002: Use Chunk-Level Retrieval Instead of Whole-Document Retrieval

Status: Accepted

The platform retrieves chunks instead of entire documents. Chunk-level retrieval improves precision for long documents and reduces prompt context size.

Consequences:

- Chunk size and overlap must be tuned.
- Adjacent chunk enrichment may be needed when an answer crosses chunk boundaries.
- Evaluation should track both answer quality and source chunk quality.

## ADR-003: Use Synthetic Data for Public Demos

Status: Accepted

Public demos and blog posts must use synthetic business documents rather than private internal documents.

Consequences:

- The project can be shared without exposing sensitive information.
- Evaluation can be repeated by other developers.
- Synthetic documents should be realistic enough to test retrieval behavior.
