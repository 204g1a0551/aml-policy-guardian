import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { ChatService } from '../../core/services/chat.service';
import { AuthService } from '../../core/services/auth.service';
import { ChatComponent } from './chat.component';
import { of, throwError } from 'rxjs';
import { ChatMessage, Citation } from '../../core/models/chat.models';

describe('Chat Streaming & Synchronous E2E Service and Component', () => {
  let chatService: ChatService;
  let authService: AuthService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [ChatComponent],
      providers: [
        provideHttpClient(),
        provideRouter([])
      ]
    });

    chatService = TestBed.inject(ChatService);
    authService = TestBed.inject(AuthService);
    authService.token.set('mock-analyst-jwt-token');
  });

  describe('ChatService.streamQuestion SSE Wire Protocol', () => {
    it('should parse streamed SSE events (START, CITATIONS, TOKEN, COMPLETE) correctly', async () => {
      const mockCitations: Citation[] = [{
        documentId: 'doc-1',
        documentTitle: 'AML Policy Manual',
        documentFilename: 'policy.pdf',
        chunkId: 'chunk-1',
        pageNumber: 4,
        section: 'SAR Escalation',
        similarityScore: 0.92,
        chunkSnippet: 'Escalate SAR within 30 days.'
      }];

      const ssePayload = [
        'event:START\ndata:{"type":"START","correlationId":"corr-123","sessionId":"sess-1"}\n\n',
        'event:CITATIONS\ndata:{"type":"CITATIONS","correlationId":"corr-123","citations":' + JSON.stringify(mockCitations) + '}\n\n',
        'event:TOKEN\ndata:{"type":"TOKEN","token":"SAR "}\n\n',
        'event:TOKEN\ndata:{"type":"TOKEN","token":"filing required."}\n\n',
        'event:COMPLETE\ndata:{"type":"COMPLETE","correlationId":"corr-123","messageId":"msg-999"}\n\n'
      ].join('');

      // Mock fetch ReadableStream
      const encoder = new TextEncoder();
      const stream = new ReadableStream({
        start(controller) {
          controller.enqueue(encoder.encode(ssePayload));
          controller.close();
        }
      });

      const mockResponse = new Response(stream, {
        status: 200,
        headers: { 'Content-Type': 'text/event-stream' }
      });

      vi.spyOn(globalThis, 'fetch').mockResolvedValue(mockResponse);

      let startCalled = false;
      let receivedCitations: Citation[] = [];
      let accumulatedTokens = '';
      let completeMessageId = '';

      await chatService.streamQuestion(
        'sess-1',
        'What is SAR filing deadline?',
        {
          onStart: (e) => { startCalled = true; },
          onCitations: (c) => { receivedCitations = c; },
          onToken: (t) => { accumulatedTokens += t; },
          onComplete: (e) => { completeMessageId = e.messageId || ''; }
        }
      );

      expect(startCalled).toBe(true);
      expect(receivedCitations.length).toBe(1);
      expect(receivedCitations[0].documentTitle).toBe('AML Policy Manual');
      expect(accumulatedTokens).toBe('SAR filing required.');
      expect(completeMessageId).toBe('msg-999');
    });

    it('should throw error when server returns HTTP error status', async () => {
      const errorResponse = new Response('Session unauthorized', {
        status: 403,
        statusText: 'Forbidden'
      });

      vi.spyOn(globalThis, 'fetch').mockResolvedValue(errorResponse);

      let errorReported: Error | null = null;
      await expect(
        chatService.streamQuestion(
          'sess-2',
          'Query',
          {
            onError: (err) => { errorReported = err; }
          }
        )
      ).rejects.toThrow();

      expect(errorReported).toBeDefined();
    });
  });

  describe('ChatComponent Streaming Controls & Fallback', () => {
    let fixture: any;
    let component: ChatComponent;

    beforeEach(() => {
      fixture = TestBed.createComponent(ChatComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();
    });

    it('should default useStreaming to true and allow toggling', () => {
      expect(component.useStreaming()).toBe(true);
      component.toggleStreaming();
      expect(component.useStreaming()).toBe(false);
      component.toggleStreaming();
      expect(component.useStreaming()).toBe(true);
    });

    it('should handle cancellation cleanly and mark transcript [Stopped by investigator]', () => {
      component.currentSession.set({
        id: 'sess-1',
        title: 'Active Session',
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
        messageCount: 0
      });

      component.isGeneratingResponse.set(true);
      component.isStreaming.set(true);
      component.activeAssistantMsgId = 'assistant-test-1';
      component.messages.set([
        {
          id: 'assistant-test-1',
          sessionId: 'sess-1',
          role: 'ASSISTANT',
          content: 'Partial generation in progress',
          createdAt: new Date().toISOString()
        }
      ]);

      component.cancelGeneration();

      expect(component.isGeneratingResponse()).toBe(false);
      expect(component.isStreaming()).toBe(false);
      expect(component.activeAssistantMsgId).toBeNull();
      const updatedMsg = component.messages().find(m => m.id === 'assistant-test-1');
      expect(updatedMsg?.content).toContain('[Stopped by investigator]');
    });

    it('should fall back to synchronous endpoint when streaming connection fails with 0 tokens', async () => {
      const syncSpy = vi.spyOn(chatService, 'askQuestion').mockReturnValue(
        of({
          id: 'msg-sync-1',
          sessionId: 'sess-1',
          role: 'ASSISTANT',
          content: 'Synchronous fallback compliance answer.',
          createdAt: new Date().toISOString(),
          citations: []
        })
      );

      vi.spyOn(chatService, 'streamQuestion').mockRejectedValue(new Error('Network connection failed'));

      component.currentSession.set({
        id: 'sess-1',
        title: 'Fallback Test Session',
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
        messageCount: 0
      });

      component.currentQuery = 'What are CTR thresholds?';
      component.sendMessage();

      // Wait microtasks
      await new Promise(resolve => setTimeout(resolve, 50));

      expect(syncSpy).toHaveBeenCalledWith('sess-1', {
        message: 'What are CTR thresholds?',
        query: 'What are CTR thresholds?'
      });

      expect(component.streamingNotice()).toContain('Automatically falling back to synchronous');
      const assistantMsg = component.messages().find(m => m.content === 'Synchronous fallback compliance answer.');
      expect(assistantMsg).toBeDefined();
    });
  });
});
