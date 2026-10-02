import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AskQuestionRequest, ChatMessage, ChatSession, CreateChatSessionRequest } from '../models/chat.models';

@Injectable({
  providedIn: 'root'
})
export class ChatService {
  private readonly baseUrl = `${environment.apiUrl}/chat`;

  constructor(private http: HttpClient) {}

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
}
