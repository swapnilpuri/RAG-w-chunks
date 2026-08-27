# RAG Chat UI

Angular frontend for the RAG pipeline in this repository. The UI provides a simple chat experience for asking questions against the documents ingested by the backend services.

## Backend Contract

The UI calls the RAG API exposed by the `demorag` Spring Boot service:

```text
POST http://localhost:8081/api/rag/v1/ask
```

Request body:

```json
{
  "query": "What are the main components of Apache Spark?"
}
```

Response shape:

```json
{
  "query": "What are the main components of Apache Spark?",
  "response": "Based on the provided documents...",
  "processingTimeMs": 2341,
  "service": "basic"
}
```

## Configuration

The backend URL is configured in:

```text
src/environments/environment.ts
src/environments/environment.prod.ts
```

Default local configuration:

```typescript
export const environment = {
  production: false,
  apiUrl: 'http://localhost:8081'
};
```

## Local Development

Install dependencies:

```bash
npm install
```

Start the development server:

```bash
npm start
```

Open:

```text
http://localhost:4200
```

## Build

```bash
npm run build
```

The production build is written to:

```text
dist/chat-ui/browser/
```

## Test

```bash
npm test -- --watch=false
```

## Project Structure

```text
src/app/
  app.ts              Main chat component
  app.html            Chat template
  app.css             Chat styling
  services/chat.ts    API client for the RAG backend

src/environments/
  environment.ts
  environment.prod.ts
```

## Notes for Portfolio Reviewers

This UI is intentionally lightweight. Its purpose is to demonstrate the end-to-end RAG flow from a user question to a grounded backend response. Future improvements could include source citations, retrieval metadata, conversation reset, markdown rendering, and streaming token display.
