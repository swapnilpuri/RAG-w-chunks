# Chat UI - Angular Chat Interface

This is an Angular-based chat interface with a Claude.ai-style UI for your Spring REST service.

## Features

- Clean, modern chat interface similar to Claude.ai
- Multi-line text input with Enter to send, Shift+Enter for new line
- Conversation history with user and assistant messages
- Typing indicator while waiting for responses
- Responsive design that works on desktop and mobile
- Error handling with user-friendly messages

## Local Development

1. Install dependencies:
   ```bash
   npm install
   ```

2. Start the development server:
   ```bash
   npm start
   ```

3. Open your browser to http://localhost:4200/

## Configuration

The backend API URL is configured in `src/environments/environment.ts`:
- Default: `http://localhost:8080`
- Update this to point to your Spring REST service endpoint

The application expects the backend to have an endpoint at `/api/chat` that accepts POST requests with:
```json
{
  "message": "user message here"
}
```

And returns a response with:
```json
{
  "response": "assistant response here"
}
```

## Building for Production

```bash
npm run build
```

The build artifacts will be stored in the `dist/chat-ui/browser/` directory.

## Deployment

The application can be deployed to any static hosting service (Netlify, Vercel, AWS S3, etc.).

Make sure to update the `environment.prod.ts` file with your production backend URL before building.

## Project Structure

- `src/app/app.ts` - Main component with chat logic
- `src/app/app.html` - Chat interface template
- `src/app/app.css` - Chat interface styles
- `src/app/services/chat.ts` - Service for API communication
- `src/environments/` - Environment configuration files
