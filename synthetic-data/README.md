# Synthetic Data for RAG Evaluation

This folder contains a small synthetic corpus and evaluation set for testing the RAG pipeline without using private or customer data.

The goal is to make the project repeatable for demos, blog posts, and portfolio review:

- ingest known documents
- ask known questions
- check whether the right source is retrieved
- check whether the answer stays grounded
- check whether out-of-scope questions are refused

## Folder Structure

```text
synthetic-data/
  documents/        Synthetic Markdown documents to ingest
  evaluation/       JSON/JSONL evaluation artifacts
  scripts/          Prompts and notes used to generate or extend the dataset
```

## How To Use

1. Start PostgreSQL with pgvector.
2. Start `document-streaming-ingestion-service`.
3. Copy the Markdown files into the ingestion input folder:

```bash
copy synthetic-data\documents\*.md document-streaming-ingestion-service\input\
```

On macOS/Linux:

```bash
cp synthetic-data/documents/*.md document-streaming-ingestion-service/input/
```

4. Wait for the Camel route to archive the files.
5. Start `demorag`.
6. Ask questions from `evaluation/questions.jsonl` through `/api/rag/v1/ask` or `/api/rag/v1/ask-enhanced`.

## Evaluation Philosophy

The dataset intentionally includes:

- single-document factual questions
- cross-document comparison questions
- policy/procedure questions
- incident/root-cause questions
- out-of-scope questions

A good RAG response should:

- answer from the provided documents when evidence exists
- mention relevant source information when possible
- refuse or say information is not available for out-of-scope questions
- avoid using general LLM knowledge to fill gaps

## Extending The Dataset

When adding new synthetic documents, also add evaluation rows to `evaluation/questions.jsonl`.

Each question should include:

- `id`
- `question`
- `expected_sources`
- `expected_answer_contains`
- `should_answer`
- `difficulty`
- `category`

Keep the documents realistic enough to exercise retrieval, but synthetic enough that they are safe to publish.
