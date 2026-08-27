# Chat UI Deployment

This Angular application can be deployed to any static hosting platform, such as Netlify, Vercel, AWS S3 + CloudFront, Azure Static Web Apps, or GitHub Pages.

## Build Command

```bash
npm install
npm run build
```

Build output:

```text
dist/chat-ui/browser/
```

## Backend URL

Before building for production, update:

```text
src/environments/environment.prod.ts
```

Example:

```typescript
export const environment = {
  production: true,
  apiUrl: 'https://your-rag-api.example.com'
};
```

The deployed backend must expose:

```text
POST /api/rag/v1/ask
GET  /api/rag/v1/health
```

The UI sends:

```json
{
  "query": "user question"
}
```

The UI expects:

```json
{
  "query": "user question",
  "response": "assistant response",
  "processingTimeMs": 1234,
  "service": "basic"
}
```

## CORS

If the UI is hosted on a different domain from the Spring Boot API, configure CORS in the backend to allow the deployed frontend origin.

For local development, the frontend runs on:

```text
http://localhost:4200
```

The backend runs on:

```text
http://localhost:8081
```

## Deployment Checklist

- Set the production API URL in `environment.prod.ts`.
- Build with `npm run build`.
- Upload `dist/chat-ui/browser/` to the static host.
- Verify `GET /api/rag/v1/health` from the deployed environment.
- Confirm the backend allows CORS from the frontend domain.
- Ask a known sample question and verify the response is grounded in ingested documents.
