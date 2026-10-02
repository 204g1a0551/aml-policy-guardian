import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AskQuestionRequest, ChatMessage, ChatSession, ChatStreamEvent, Citation, CreateChatSessionRequest } from '../models/chat.models';
import { AuthService } from './auth.service';

@Injectable({
  providedIn: 'root'
})
export class ChatService {
  private readonly baseUrl = `${environment.apiUrl}/chat`;

  constructor(
    private http: HttpClient,
    private authService: AuthService
  ) {}

  getSessions(): Observable<ChatSession[]> {
    return this.http.get<ChatSession[]>(`${this.baseUrl}/sessions`);
  }

  createSession(request: CreateChatSessionRequest): Observable<ChatSession> {
    return this.http.post<ChatSession>(`${this.baseUrl}/sessions`, request);
  }

  deleteSession(sessionId: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/sessions/${sessionId}`);
  }

  getMessages(sessionId: string): Observable<ChatMessage[]> {
    return this.http.get<ChatMessage[]>(`${this.baseUrl}/sessions/${sessionId}/messages`);
  }

  askQuestion(sessionId: string, request: AskQuestionRequest): Observable<ChatMessage> {
    return this.http.post<ChatMessage>(`${this.baseUrl}/sessions/${sessionId}/messages`, request);
  }

  async streamQuestion(
    sessionId: string,
    message: string,
    callbacks: {
      onStart?: (event: ChatStreamEvent) => void;
      onCitations?: (citations: Citation[]) => void;
      onToken?: (token: string) => void;
      onComplete?: (event: ChatStreamEvent) => void;
      onError?: (error: Error) => void;
    },
    abortSignal?: AbortSignal,
    correlationId?: string
  ): Promise<void> {
    const token = this.authService.getAccessToken();
    const corrId = correlationId || crypto.randomUUID();

    const response = await fetch(`${this.baseUrl}/stream`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Accept': 'text/event-stream',
        'Authorization': `Bearer ${token || ''}`,
        'X-Correlation-ID': corrId
      },
      body: JSON.stringify({ sessionId, message }),
      signal: abortSignal
    });

    if (!response.ok) {
      const errorText = await response.text().catch(() => response.statusText);
      throw new Error(`Streaming request failed [${response.status}]: ${errorText || response.statusText}`);
    }

    if (!response.body) {
      throw new Error('ReadableStream response body is not supported');
    }

    const reader = response.body.getReader();
    const decoder = new TextDecoder('utf-8');
    let buffer = '';

    const processBlock = (block: string) => {
      if (!block.trim()) return;
      let dataStr = '';
      const lines = block.split('\n');
      for (const line of lines) {
        if (line.startsWith('data:')) {
          dataStr += line.substring(5).trim();
        }
      }

      if (dataStr) {
        try {
          const event: ChatStreamEvent = JSON.parse(dataStr);
          switch (event.type) {
            case 'START':
              callbacks.onStart?.(event);
              break;
            case 'CITATIONS':
              if (event.citations) {
                callbacks.onCitations?.(event.citations);
              }
              break;
            case 'TOKEN':
              if (event.token) {
                callbacks.onToken?.(event.token);
              }
              break;
            case 'COMPLETE':
              callbacks.onComplete?.(event);
              break;
            case 'ERROR':
              callbacks.onError?.(new Error(event.error || 'Server streaming error'));
              break;
          }
        } catch (e) {
          console.warn('Failed to parse SSE event payload:', dataStr, e);
        }
      }
    };

    try {
      while (true) {
        const { done, value } = await reader.read();
        if (done) break;

        buffer += decoder.decode(value, { stream: true });
        const parts = buffer.split('\n\n');
        buffer = parts.pop() || '';

        for (const part of parts) {
          processBlock(part);
        }
      }

      if (buffer.trim()) {
        processBlock(buffer);
      }
    } catch (err: any) {
      if (err.name === 'AbortError') {
        // Clean cancellation via AbortSignal
        return;
      }
      callbacks.onError?.(err);
      throw err;
    } finally {
      reader.releaseLock();
    }
  }
}
