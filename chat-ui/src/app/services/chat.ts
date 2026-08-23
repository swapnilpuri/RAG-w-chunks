import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export interface Message {
  role: 'user' | 'assistant';
  content: string;
  timestamp: Date;
}

/**
 * Response shape returned by demorag's /api/rag/v1/ask (and ask-enhanced)
 * endpoints. See the root README's "API Reference" section.
 */
export interface RagAskResponse {
  query: string;
  response: string;
  processingTimeMs: number;
  service: 'basic' | 'enhanced';
  features?: {
    adjacentChunks: boolean;
    contextOptimization: boolean;
    deduplication: boolean;
  };
}

@Injectable({
  providedIn: 'root'
})
export class Chat {
  private apiUrl = environment.apiUrl;

  constructor(private http: HttpClient) { }

  sendMessage(message: string): Observable<RagAskResponse> {
    return this.http.post<RagAskResponse>(`${this.apiUrl}/api/rag/v1/ask`, { query: message });
  }
}
