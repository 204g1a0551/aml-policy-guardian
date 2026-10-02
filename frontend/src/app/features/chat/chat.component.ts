import { Component, OnInit, signal, effect, ElementRef, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { ChatService } from '../../core/services/chat.service';
import { ChatMessage, ChatSession, Citation } from '../../core/models/chat.models';

@Component({
  selector: 'app-chat',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="chat-container">
      <!-- Sidebar / Conversation List -->
      <aside class="sidebar">
        <div class="sidebar-header">
          <button class="btn-new-chat" (click)="createNewSession()" [disabled]="isCreatingSession()">
            <span>+ New Investigation</span>
          </button>
        </div>

        <div class="session-search">
          <input 
            type="text" 
            placeholder="Search sessions..." 
            [(ngModel)]="searchQuery"
          />
        </div>

        <div class="session-list">
          @if (filteredSessions().length === 0) {
            <div class="empty-sessions">
              <p>No investigations found.</p>
            </div>
          } @else {
            @for (session of filteredSessions(); track session.id) {
              <div 
                class="session-item" 
                [class.active]="currentSession()?.id === session.id"
                (click)="selectSession(session)"
              >
                <div class="session-info">
                  <span class="session-title">{{ session.title }}</span>
                  <span class="session-date">{{ session.updatedAt | date:'shortDate' }}</span>
                </div>
                <button 
                  class="btn-delete-session" 
                  title="Delete Session" 
                  (click)="deleteSession(session.id, $event)"
                >
                  &times;
                </button>
              </div>
            }
          }
        </div>
      </aside>

      <!-- Main Chat Area -->
      <section class="chat-main">
        <!-- Chat Header -->
        <header class="chat-header">
          <div class="header-info">
            <h2>{{ currentSession()?.title || 'AML Policy Investigation' }}</h2>
            <span class="header-badge">Grounded RAG &bull; Tier-1 Banking Compliance</span>
          </div>
          @if (currentSession()) {
            <div class="header-actions">
              <button 
                type="button" 
                class="stream-toggle-btn" 
                [class.streaming-on]="useStreaming()"
                (click)="toggleStreaming()"
                title="Toggle streaming SSE responses"
              >
                <span class="stream-dot"></span>
                Streaming: {{ useStreaming() ? 'ON' : 'OFF' }}
              </button>
              <span class="message-counter">{{ messages().length }} Messages</span>
            </div>
          }
        </header>

        <!-- Messages Viewport -->
        <div class="messages-viewport" #messagesViewport>
          @if (messages().length === 0 && !isLoadingMessages()) {
            <div class="empty-chat-state">
              <div class="compliance-icon">⚖️</div>
              <h3>AML Investigation Assistant</h3>
              <p>Ask questions grounded directly in authorized banking AML policies, SAR escalation procedures, and customer risk frameworks.</p>
              
              <div class="prompt-suggestions">
                <span class="suggestion-label">Suggested Compliance Inquiries:</span>
                <div class="suggestion-chips">
                  <button type="button" class="chip" (click)="useSuggestion('What is the mandatory threshold and timeframe for filing a Currency Transaction Report (CTR)?')">
                    What is the mandatory CTR threshold and filing deadline?
                  </button>
                  <button type="button" class="chip" (click)="useSuggestion('Explain Scenario TM-RULE-101 for structuring cash deposits.')">
                    Explain Scenario TM-RULE-101 for structuring cash deposits.
                  </button>
                  <button type="button" class="chip" (click)="useSuggestion('What enhanced due diligence is required for Politically Exposed Persons (PEPs)?')">
                    What enhanced due diligence is required for PEPs?
                  </button>
                </div>
              </div>
            </div>
          }

          @if (isLoadingMessages()) {
            <div class="loading-messages">
              <div class="spinner"></div>
              <span>Loading investigation transcript...</span>
            </div>
          }

          @for (msg of messages(); track msg.id) {
            <div class="message-row" [class.user-row]="msg.role === 'USER'" [class.assistant-row]="msg.role !== 'USER'">
              <div class="message-bubble" [class.user-bubble]="msg.role === 'USER'" [class.assistant-bubble]="msg.role !== 'USER'">
                <div class="bubble-header">
                  <span class="sender-name">
                    @if (msg.role === 'USER') {
                      Compliance Investigator
                    } @else {
                      AML Policy Guardian
                    }
                  </span>
                  <span class="message-time">{{ msg.createdAt | date:'shortTime' }}</span>
                </div>

                <div class="bubble-content">
                  @if (!msg.content && isGeneratingResponse() && msg.role !== 'USER') {
                    <div class="reasoning-indicator">
                      <span class="dot"></span>
                      <span class="dot"></span>
                      <span class="dot"></span>
                      <span class="status-text">Grounded Compliance Reasoning in progress...</span>
                    </div>
                  } @else {
                    {{ msg.content }}
                    @if (isGeneratingResponse() && isStreaming() && msg.id === activeAssistantMsgId) {
                      <span class="streaming-cursor">|</span>
                    }
                  }
                </div>

                <!-- Citations & Source Badges -->
                @if (msg.citations && msg.citations.length > 0) {
                  <div class="citations-container">
                    <span class="citation-title">Grounded Compliance Citations:</span>
                    <div class="citation-badges">
                      @for (cite of msg.citations; track cite.chunkId) {
                        <button 
                          type="button" 
                          class="citation-badge"
                          (click)="openSourceDetail(cite)"
                        >
                          <span class="cite-doc">{{ cite.documentTitle }}</span>
                          @if (cite.section) {
                            <span class="cite-sec">&sect; {{ cite.section }}</span>
                          }
                          @if (cite.pageNumber) {
                            <span class="cite-page">p. {{ cite.pageNumber }}</span>
                          }
                          <span class="cite-score">{{ (cite.similarityScore * 100) | number:'1.0-0' }}% Match</span>
                        </button>
                      }
                    </div>
                  </div>
                }
              </div>
            </div>
          }

          @if (isGeneratingResponse() && !isStreaming()) {
            <div class="message-row assistant-row">
              <div class="message-bubble assistant-bubble generating">
                <div class="reasoning-indicator">
                  <span class="dot"></span>
                  <span class="dot"></span>
                  <span class="dot"></span>
                  <span class="status-text">Grounded Compliance Reasoning in progress...</span>
                </div>
              </div>
            </div>
          }

          @if (streamingNotice()) {
            <div class="chat-notice-banner">
              <span>ℹ️ {{ streamingNotice() }}</span>
              <button (click)="streamingNotice.set(null)" class="btn-dismiss">&times;</button>
            </div>
          }

          @if (chatError()) {
            <div class="chat-error-banner">
              <span>⚠️ {{ chatError() }}</span>
              <button (click)="chatError.set(null)" class="btn-dismiss">&times;</button>
            </div>
          }
        </div>

        <!-- Input Box -->
        <footer class="chat-input-area">
          <form (ngSubmit)="sendMessage()" class="input-form">
            <textarea
              #queryInput
              rows="2"
              placeholder="Ask an AML policy or transaction investigation question... (Press Enter to submit, Shift+Enter for newline)"
              [(ngModel)]="currentQuery"
              name="query"
              [disabled]="isGeneratingResponse()"
              (keydown)="onKeyDown($event)"
            ></textarea>

            <div class="input-controls">
              <span class="char-count">{{ currentQuery.length }} chars</span>
              <div class="input-buttons">
                @if (isGeneratingResponse()) {
                  <button 
                    type="button" 
                    class="btn-stop" 
                    (click)="cancelGeneration()"
                    title="Stop generating response"
                  >
                    ⏹ Stop
                  </button>
                }
                <button 
                  type="submit" 
                  class="btn-send" 
                  [disabled]="!currentQuery.trim() || isGeneratingResponse()"
                >
                  @if (isGeneratingResponse()) {
                    <span class="spinner-sm"></span>
                  } @else {
                    <span>Send</span>
                  }
                </button>
              </div>
            </div>
          </form>
        </footer>
      </section>

      <!-- Expandable Source Details Modal -->
      @if (selectedCitation()) {
        <div class="modal-backdrop" (click)="closeSourceDetail()">
          <div class="modal-dialog" (click)="$event.stopPropagation()">
            <div class="modal-header">
              <h3>Source Document Detail</h3>
              <button class="btn-close" (click)="closeSourceDetail()">&times;</button>
            </div>
            <div class="modal-body">
              <div class="detail-row">
                <label>Document Title:</label>
                <span>{{ selectedCitation()?.documentTitle }}</span>
              </div>
              <div class="detail-row">
                <label>File Reference:</label>
                <code>{{ selectedCitation()?.documentFilename }}</code>
              </div>
              <div class="detail-row">
                <label>Section:</label>
                <span>{{ selectedCitation()?.section || 'Full Document' }}</span>
              </div>
              <div class="detail-row">
                <label>Page Number:</label>
                <span>{{ selectedCitation()?.pageNumber || 'N/A' }}</span>
              </div>
              <div class="detail-row">
                <label>Similarity Score:</label>
                <span class="score-pill">{{ ((selectedCitation()?.similarityScore || 0) * 100) | number:'1.1-1' }}% Cosine Alignment</span>
              </div>
              <div class="detail-box">
                <label>Verified Policy Excerpt:</label>
                <div class="snippet-content">{{ selectedCitation()?.chunkSnippet }}</div>
              </div>
            </div>
            <div class="modal-footer">
              <button type="button" class="btn-secondary" (click)="closeSourceDetail()">Close</button>
            </div>
          </div>
        </div>
      }
    </div>
  `,
  styles: [`
    .chat-container {
      display: flex;
      height: calc(100vh - 65px);
      background: #f8fafc;
      overflow: hidden;
    }

    /* Sidebar */
    .sidebar {
      width: 290px;
      min-width: 260px;
      background: #0f172a;
      color: #f1f5f9;
      display: flex;
      flex-direction: column;
      border-right: 1px solid #1e293b;
    }
    .sidebar-header {
      padding: 1rem;
      border-bottom: 1px solid #1e293b;
    }
    .btn-new-chat {
      width: 100%;
      padding: 0.65rem;
      background: #2563eb;
      color: #ffffff;
      border: none;
      border-radius: 6px;
      font-weight: 600;
      font-size: 0.875rem;
      cursor: pointer;
      transition: background 0.15s;
    }
    .btn-new-chat:hover:not(:disabled) {
      background: #1d4ed8;
    }
    .session-search {
      padding: 0.75rem 1rem 0.25rem 1rem;
    }
    .session-search input {
      width: 100%;
      padding: 0.45rem 0.65rem;
      background: #1e293b;
      border: 1px solid #334155;
      border-radius: 4px;
      color: #f1f5f9;
      font-size: 0.8rem;
      box-sizing: border-box;
      outline: none;
    }
    .session-list {
      flex: 1;
      overflow-y: auto;
      padding: 0.5rem 0.75rem;
    }
    .session-item {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 0.65rem 0.75rem;
      border-radius: 6px;
      margin-bottom: 0.25rem;
      cursor: pointer;
      transition: background 0.15s;
      border: 1px solid transparent;
    }
    .session-item:hover {
      background: #1e293b;
    }
    .session-item.active {
      background: #1e3a8a;
      border-color: #3b82f6;
    }
    .session-info {
      display: flex;
      flex-direction: column;
      overflow: hidden;
      margin-right: 0.5rem;
    }
    .session-title {
      font-size: 0.85rem;
      font-weight: 500;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
      color: #f8fafc;
    }
    .session-date {
      font-size: 0.7rem;
      color: #94a3b8;
      margin-top: 0.15rem;
    }
    .btn-delete-session {
      background: transparent;
      border: none;
      color: #64748b;
      font-size: 1.1rem;
      cursor: pointer;
      padding: 0 0.25rem;
      line-height: 1;
    }
    .btn-delete-session:hover {
      color: #ef4444;
    }
    .empty-sessions {
      padding: 1.5rem 0.5rem;
      text-align: center;
      color: #64748b;
      font-size: 0.8rem;
    }

    /* Main Chat */
    .chat-main {
      flex: 1;
      display: flex;
      flex-direction: column;
      height: 100%;
      background: #ffffff;
    }
    .chat-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 0.85rem 1.5rem;
      background: #ffffff;
      border-bottom: 1px solid #e2e8f0;
    }
    .header-info h2 {
      margin: 0;
      font-size: 1.15rem;
      color: #0f172a;
    }
    .header-badge {
      font-size: 0.75rem;
      color: #059669;
      font-weight: 500;
      display: inline-block;
      margin-top: 0.15rem;
    }
    .message-counter {
      font-size: 0.75rem;
      background: #f1f5f9;
      color: #475569;
      padding: 0.25rem 0.5rem;
      border-radius: 4px;
      font-weight: 500;
    }

    /* Messages Viewport */
    .messages-viewport {
      flex: 1;
      overflow-y: auto;
      padding: 1.5rem;
      display: flex;
      flex-direction: column;
      gap: 1rem;
    }
    .empty-chat-state {
      max-width: 600px;
      margin: 3rem auto;
      text-align: center;
      color: #475569;
    }
    .compliance-icon {
      font-size: 2.5rem;
      margin-bottom: 0.5rem;
    }
    .empty-chat-state h3 {
      font-size: 1.35rem;
      color: #0f172a;
      margin-bottom: 0.5rem;
    }
    .empty-chat-state p {
      font-size: 0.9rem;
      line-height: 1.5;
      color: #64748b;
      margin-bottom: 1.5rem;
    }
    .prompt-suggestions {
      text-align: left;
      background: #f8fafc;
      border: 1px solid #e2e8f0;
      border-radius: 8px;
      padding: 1rem;
    }
    .suggestion-label {
      font-size: 0.75rem;
      font-weight: 600;
      text-transform: uppercase;
      color: #64748b;
      letter-spacing: 0.05em;
    }
    .suggestion-chips {
      display: flex;
      flex-direction: column;
      gap: 0.5rem;
      margin-top: 0.5rem;
    }
    .chip {
      background: #ffffff;
      border: 1px solid #cbd5e1;
      border-radius: 6px;
      padding: 0.5rem 0.75rem;
      font-size: 0.85rem;
      text-align: left;
      color: #1e3a8a;
      cursor: pointer;
      transition: all 0.15s;
    }
    .chip:hover {
      background: #eff6ff;
      border-color: #3b82f6;
    }

    .loading-messages {
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 0.5rem;
      padding: 2rem;
      color: #64748b;
      font-size: 0.85rem;
    }
    .message-row {
      display: flex;
      width: 100%;
    }
    .user-row {
      justify-content: flex-end;
    }
    .assistant-row {
      justify-content: flex-start;
    }
    .message-bubble {
      max-width: 78%;
      padding: 1rem 1.15rem;
      border-radius: 10px;
      box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
      line-height: 1.5;
      font-size: 0.925rem;
    }
    .user-bubble {
      background: #1e3a8a;
      color: #ffffff;
      border-bottom-right-radius: 2px;
    }
    .assistant-bubble {
      background: #f8fafc;
      color: #0f172a;
      border: 1px solid #e2e8f0;
      border-bottom-left-radius: 2px;
    }
    .bubble-header {
      display: flex;
      justify-content: space-between;
      gap: 1rem;
      font-size: 0.75rem;
      margin-bottom: 0.4rem;
      opacity: 0.85;
    }
    .user-bubble .sender-name {
      color: #93c5fd;
      font-weight: 600;
    }
    .assistant-bubble .sender-name {
      color: #1e40af;
      font-weight: 600;
    }
    .bubble-content {
      white-space: pre-wrap;
      word-break: break-word;
    }

    /* Citations */
    .citations-container {
      margin-top: 0.85rem;
      padding-top: 0.65rem;
      border-top: 1px solid #e2e8f0;
    }
    .citation-title {
      display: block;
      font-size: 0.725rem;
      font-weight: 600;
      color: #475569;
      text-transform: uppercase;
      letter-spacing: 0.05em;
      margin-bottom: 0.35rem;
    }
    .citation-badges {
      display: flex;
      flex-wrap: wrap;
      gap: 0.35rem;
    }
    .citation-badge {
      display: inline-flex;
      align-items: center;
      gap: 0.35rem;
      background: #ffffff;
      border: 1px solid #cbd5e1;
      border-radius: 4px;
      padding: 0.25rem 0.5rem;
      font-size: 0.75rem;
      color: #1e293b;
      cursor: pointer;
      transition: all 0.15s;
    }
    .citation-badge:hover {
      background: #f1f5f9;
      border-color: #2563eb;
    }
    .cite-doc {
      font-weight: 600;
      color: #1e40af;
    }
    .cite-score {
      background: #dcfce7;
      color: #166534;
      font-weight: 600;
      padding: 0.1rem 0.3rem;
      border-radius: 3px;
      font-size: 0.7rem;
    }

    /* Generating Animation */
    .reasoning-indicator {
      display: flex;
      align-items: center;
      gap: 0.4rem;
      color: #475569;
      font-size: 0.85rem;
    }
    .dot {
      width: 7px;
      height: 7px;
      background: #2563eb;
      border-radius: 50%;
      animation: pulse 1.4s infinite ease-in-out both;
    }
    .dot:nth-child(1) { animation-delay: -0.32s; }
    .dot:nth-child(2) { animation-delay: -0.16s; }
    @keyframes pulse {
      0%, 80%, 100% { transform: scale(0); }
      40% { transform: scale(1); }
    }

    .chat-error-banner {
      background: #fee2e2;
      border: 1px solid #f87171;
      color: #991b1b;
      padding: 0.65rem 0.85rem;
      border-radius: 6px;
      font-size: 0.85rem;
      display: flex;
      justify-content: space-between;
      align-items: center;
    }
    .btn-dismiss {
      background: transparent;
      border: none;
      color: #991b1b;
      font-size: 1.1rem;
      cursor: pointer;
    }

    /* Input Area */
    .chat-input-area {
      padding: 1rem 1.5rem;
      background: #ffffff;
      border-top: 1px solid #e2e8f0;
    }
    .input-form {
      display: flex;
      flex-direction: column;
      border: 1px solid #cbd5e1;
      border-radius: 8px;
      padding: 0.65rem 0.75rem;
      background: #ffffff;
      transition: border-color 0.15s;
    }
    .input-form:focus-within {
      border-color: #2563eb;
      box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.1);
    }
    .input-form textarea {
      width: 100%;
      border: none;
      outline: none;
      resize: none;
      font-family: inherit;
      font-size: 0.95rem;
      color: #0f172a;
    }
    .input-controls {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-top: 0.35rem;
      padding-top: 0.35rem;
      border-top: 1px solid #f1f5f9;
    }
    .header-actions {
      display: flex;
      align-items: center;
      gap: 0.75rem;
    }
    .stream-toggle-btn {
      display: inline-flex;
      align-items: center;
      gap: 0.4rem;
      background: #f1f5f9;
      border: 1px solid #cbd5e1;
      border-radius: 20px;
      padding: 0.25rem 0.65rem;
      font-size: 0.75rem;
      font-weight: 600;
      color: #64748b;
      cursor: pointer;
      transition: all 0.2s;
    }
    .stream-toggle-btn.streaming-on {
      background: #ecfdf5;
      border-color: #a7f3d0;
      color: #065f46;
    }
    .stream-dot {
      width: 8px;
      height: 8px;
      border-radius: 50%;
      background: #94a3b8;
    }
    .stream-toggle-btn.streaming-on .stream-dot {
      background: #10b981;
      box-shadow: 0 0 6px rgba(16, 185, 129, 0.6);
      animation: pulse 1.5s infinite;
    }
    @keyframes pulse {
      0%, 100% { opacity: 1; transform: scale(1); }
      50% { opacity: 0.5; transform: scale(0.85); }
    }
    .streaming-cursor {
      display: inline-block;
      color: #2563eb;
      font-weight: bold;
      animation: blink 0.8s infinite;
      margin-left: 2px;
    }
    @keyframes blink {
      0%, 100% { opacity: 1; }
      50% { opacity: 0; }
    }
    .input-buttons {
      display: flex;
      align-items: center;
      gap: 0.5rem;
    }
    .btn-stop {
      background: #fee2e2;
      color: #b91c1c;
      border: 1px solid #fca5a5;
      border-radius: 5px;
      padding: 0.4rem 0.85rem;
      font-weight: 600;
      font-size: 0.82rem;
      cursor: pointer;
      display: flex;
      align-items: center;
      gap: 0.3rem;
      transition: background 0.15s;
    }
    .btn-stop:hover {
      background: #fecaca;
    }
    .chat-notice-banner {
      background: #eff6ff;
      border: 1px solid #bfdbfe;
      color: #1e40af;
      padding: 0.6rem 1rem;
      border-radius: 6px;
      margin-bottom: 0.75rem;
      font-size: 0.85rem;
      display: flex;
      justify-content: space-between;
      align-items: center;
    }

    .char-count {
      font-size: 0.75rem;
      color: #94a3b8;
    }
    .btn-send {
      background: #1e3a8a;
      color: #ffffff;
      border: none;
      border-radius: 5px;
      padding: 0.4rem 1rem;
      font-weight: 600;
      font-size: 0.85rem;
      cursor: pointer;
      display: flex;
      align-items: center;
      justify-content: center;
      transition: background 0.15s;
    }
    .btn-send:hover:not(:disabled) {
      background: #1e40af;
    }
    .btn-send:disabled {
      background: #cbd5e1;
      cursor: not-allowed;
    }

    /* Spinners */
    .spinner {
      width: 16px;
      height: 16px;
      border: 2px solid #cbd5e1;
      border-top-color: #2563eb;
      border-radius: 50%;
      animation: spin 0.8s linear infinite;
    }
    .spinner-sm {
      width: 12px;
      height: 12px;
      border: 2px solid rgba(255, 255, 255, 0.4);
      border-top-color: #ffffff;
      border-radius: 50%;
      animation: spin 0.8s linear infinite;
    }
    @keyframes spin {
      to { transform: rotate(360deg); }
    }

    /* Modal */
    .modal-backdrop {
      position: fixed;
      inset: 0;
      background: rgba(15, 23, 42, 0.6);
      display: flex;
      align-items: center;
      justify-content: center;
      z-index: 1000;
      padding: 1rem;
    }
    .modal-dialog {
      background: #ffffff;
      border-radius: 10px;
      width: 100%;
      max-width: 600px;
      max-height: 85vh;
      display: flex;
      flex-direction: column;
      box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.1);
    }
    .modal-header {
      padding: 1rem 1.25rem;
      border-bottom: 1px solid #e2e8f0;
      display: flex;
      justify-content: space-between;
      align-items: center;
    }
    .modal-header h3 {
      margin: 0;
      font-size: 1.15rem;
      color: #0f172a;
    }
    .btn-close {
      background: transparent;
      border: none;
      font-size: 1.35rem;
      color: #64748b;
      cursor: pointer;
    }
    .modal-body {
      padding: 1.25rem;
      overflow-y: auto;
      display: flex;
      flex-direction: column;
      gap: 0.75rem;
    }
    .detail-row {
      display: flex;
      font-size: 0.85rem;
      gap: 0.5rem;
    }
    .detail-row label {
      font-weight: 600;
      width: 130px;
      color: #475569;
    }
    .score-pill {
      background: #dcfce7;
      color: #166534;
      font-weight: 600;
      padding: 0.15rem 0.45rem;
      border-radius: 4px;
      font-size: 0.8rem;
    }
    .detail-box {
      margin-top: 0.5rem;
    }
    .detail-box label {
      display: block;
      font-weight: 600;
      font-size: 0.85rem;
      color: #475569;
      margin-bottom: 0.35rem;
    }
    .snippet-content {
      background: #f8fafc;
      border: 1px solid #e2e8f0;
      border-radius: 6px;
      padding: 0.85rem;
      font-size: 0.85rem;
      line-height: 1.5;
      color: #1e293b;
      white-space: pre-wrap;
    }
    .modal-footer {
      padding: 0.75rem 1.25rem;
      border-top: 1px solid #e2e8f0;
      display: flex;
      justify-content: flex-end;
    }
    .btn-secondary {
      background: #f1f5f9;
      border: 1px solid #cbd5e1;
      padding: 0.4rem 0.85rem;
      border-radius: 5px;
      font-weight: 500;
      cursor: pointer;
      color: #334155;
    }
  `]
})
export class ChatComponent implements OnInit {
  @ViewChild('messagesViewport') private messagesViewport!: ElementRef;

  sessions = signal<ChatSession[]>([]);
  currentSession = signal<ChatSession | null>(null);
  messages = signal<ChatMessage[]>([]);
  selectedCitation = signal<Citation | null>(null);

  searchQuery = '';
  currentQuery = '';

  isCreatingSession = signal(false);
  isLoadingMessages = signal(false);
  isGeneratingResponse = signal(false);
  chatError = signal<string | null>(null);

  useStreaming = signal(true);
  isStreaming = signal(false);
  streamingNotice = signal<string | null>(null);
  activeAssistantMsgId: string | null = null;
  private abortController: AbortController | null = null;
  private streamTimeoutId: any = null;

  constructor(
    private chatService: ChatService,
    private route: ActivatedRoute,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.loadSessions();
  }

  toggleStreaming(): void {
    this.useStreaming.update(v => !v);
  }

  cancelGeneration(): void {
    if (this.abortController) {
      this.abortController.abort();
      this.abortController = null;
    }
    if (this.streamTimeoutId) {
      clearTimeout(this.streamTimeoutId);
      this.streamTimeoutId = null;
    }
    if (this.activeAssistantMsgId) {
      this.messages.update(msgs => msgs.map(m => {
        if (m.id === this.activeAssistantMsgId) {
          const content = m.content ? `${m.content} [Stopped by investigator]` : '[Generation stopped by investigator]';
          return { ...m, content };
        }
        return m;
      }));
    }
    this.isGeneratingResponse.set(false);
    this.isStreaming.set(false);
    this.activeAssistantMsgId = null;
  }

  loadSessions(): void {
    this.chatService.getSessions().subscribe({
      next: (sessions) => {
        this.sessions.set(sessions);

        // Check if query param sessionId is present
        const querySessionId = this.route.snapshot.queryParams['sessionId'];
        if (querySessionId) {
          const match = sessions.find(s => s.id === querySessionId);
          if (match) {
            this.selectSession(match);
            return;
          }
        }

        // If sessions exist and none selected, select the first
        if (sessions.length > 0 && !this.currentSession()) {
          this.selectSession(sessions[0]);
        }
      },
      error: (err) => {
        this.chatError.set(err?.error?.detail || 'Failed to load investigation sessions.');
      }
    });
  }

  filteredSessions(): ChatSession[] {
    if (!this.searchQuery.trim()) {
      return this.sessions();
    }
    const q = this.searchQuery.toLowerCase();
    return this.sessions().filter(s => s.title.toLowerCase().includes(q));
  }

  createNewSession(): void {
    this.isCreatingSession.set(true);
    const title = `Investigation ${new Date().toLocaleDateString()} ${new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}`;
    
    this.chatService.createSession({ title }).subscribe({
      next: (newSession) => {
        this.isCreatingSession.set(false);
        this.sessions.update(list => [newSession, ...list]);
        this.selectSession(newSession);
      },
      error: (err) => {
        this.isCreatingSession.set(false);
        this.chatError.set(err?.error?.detail || 'Failed to create new session.');
      }
    });
  }

  selectSession(session: ChatSession): void {
    this.currentSession.set(session);
    this.chatError.set(null);
    this.streamingNotice.set(null);
    this.isLoadingMessages.set(true);
    this.messages.set([]);

    this.chatService.getMessages(session.id).subscribe({
      next: (msgs) => {
        this.messages.set(msgs);
        this.isLoadingMessages.set(false);
        this.scrollToBottom();
      },
      error: (err) => {
        this.isLoadingMessages.set(false);
        this.chatError.set(err?.error?.detail || 'Failed to retrieve messages.');
      }
    });
  }

  deleteSession(sessionId: string, event: Event): void {
    event.stopPropagation();
    if (!confirm('Are you sure you want to delete this investigation session?')) return;

    this.chatService.deleteSession(sessionId).subscribe({
      next: () => {
        this.sessions.update(list => list.filter(s => s.id !== sessionId));
        if (this.currentSession()?.id === sessionId) {
          const remaining = this.sessions();
          if (remaining.length > 0) {
            this.selectSession(remaining[0]);
          } else {
            this.currentSession.set(null);
            this.messages.set([]);
          }
        }
      },
      error: (err) => {
        this.chatError.set(err?.error?.detail || 'Failed to delete session.');
      }
    });
  }

  useSuggestion(promptText: string): void {
    this.currentQuery = promptText;
    this.sendMessage();
  }

  sendMessage(): void {
    const q = this.currentQuery.trim();
    if (!q || this.isGeneratingResponse()) return;

    // If no session exists, create one first
    if (!this.currentSession()) {
      this.isGeneratingResponse.set(true);
      const title = q.length > 40 ? q.substring(0, 40) + '...' : q;
      this.chatService.createSession({ title }).subscribe({
        next: (session) => {
          this.sessions.update(list => [session, ...list]);
          this.currentSession.set(session);
          this.executeSendMessage(session.id, q);
        },
        error: (err) => {
          this.isGeneratingResponse.set(false);
          this.chatError.set(err?.error?.detail || 'Failed to initialize session.');
        }
      });
      return;
    }

    this.executeSendMessage(this.currentSession()!.id, q);
  }

  private executeSendMessage(sessionId: string, queryText: string): void {
    this.isGeneratingResponse.set(true);
    this.chatError.set(null);
    this.streamingNotice.set(null);

    // Optimistically add user query to transcript
    const userMsg: ChatMessage = {
      id: 'temp-' + Date.now(),
      sessionId: sessionId,
      role: 'USER',
      content: queryText,
      createdAt: new Date().toISOString()
    };
    this.messages.update(msgs => [...msgs, userMsg]);
    this.currentQuery = '';
    this.scrollToBottom();

    if (!this.useStreaming()) {
      this.executeSynchronous(sessionId, queryText);
      return;
    }

    this.executeStreaming(sessionId, queryText);
  }

  private executeSynchronous(sessionId: string, queryText: string): void {
    this.isStreaming.set(false);
    this.chatService.askQuestion(sessionId, { message: queryText, query: queryText }).subscribe({
      next: (responseMsg) => {
        this.isGeneratingResponse.set(false);
        this.messages.update(msgs => [...msgs, responseMsg]);
        this.scrollToBottom();
        this.updateSessionStats(sessionId);
      },
      error: (err) => {
        this.isGeneratingResponse.set(false);
        this.chatError.set(err?.error?.detail || err?.message || 'Failed to generate compliance response.');
      }
    });
  }

  private async executeStreaming(sessionId: string, queryText: string): Promise<void> {
    this.isStreaming.set(true);
    const assistantMsgId = 'assistant-' + Date.now();
    this.activeAssistantMsgId = assistantMsgId;

    const placeholderAssistant: ChatMessage = {
      id: assistantMsgId,
      sessionId: sessionId,
      role: 'ASSISTANT',
      content: '',
      createdAt: new Date().toISOString(),
      citations: []
    };
    this.messages.update(msgs => [...msgs, placeholderAssistant]);
    this.scrollToBottom();

    this.abortController = new AbortController();
    let tokensReceived = 0;

    // Client-side 60s timeout handling
    this.streamTimeoutId = setTimeout(() => {
      if (this.isGeneratingResponse() && this.activeAssistantMsgId === assistantMsgId) {
        this.abortController?.abort();
        this.chatError.set('Response generation timed out after 60 seconds.');
        this.cancelGeneration();
      }
    }, 60000);

    try {
      await this.chatService.streamQuestion(
        sessionId,
        queryText,
        {
          onStart: () => {
            // Stream initiated
          },
          onCitations: (citations) => {
            this.messages.update(msgs => msgs.map(m => {
              if (m.id === assistantMsgId) {
                return { ...m, citations };
              }
              return m;
            }));
            this.scrollToBottom();
          },
          onToken: (token) => {
            tokensReceived++;
            this.messages.update(msgs => msgs.map(m => {
              if (m.id === assistantMsgId) {
                return { ...m, content: m.content + token };
              }
              return m;
            }));
            this.scrollToBottom();
          },
          onComplete: (event) => {
            if (this.streamTimeoutId) clearTimeout(this.streamTimeoutId);
            this.isGeneratingResponse.set(false);
            this.isStreaming.set(false);
            this.abortController = null;
            if (event.messageId) {
              this.messages.update(msgs => msgs.map(m => {
                if (m.id === assistantMsgId) {
                  return { ...m, id: event.messageId! };
                }
                return m;
              }));
            }
            this.activeAssistantMsgId = null;
            this.updateSessionStats(sessionId);
          },
          onError: (err) => {
            if (this.streamTimeoutId) clearTimeout(this.streamTimeoutId);
            this.handleStreamingError(sessionId, queryText, assistantMsgId, tokensReceived, err);
          }
        },
        this.abortController.signal
      );
    } catch (err: any) {
      if (this.streamTimeoutId) clearTimeout(this.streamTimeoutId);
      if (err.name !== 'AbortError') {
        this.handleStreamingError(sessionId, queryText, assistantMsgId, tokensReceived, err);
      }
    }
  }

  private handleStreamingError(
    sessionId: string,
    queryText: string,
    assistantMsgId: string,
    tokensReceived: number,
    err: any
  ): void {
    if (this.abortController === null && !this.isGeneratingResponse()) {
      return;
    }
    this.abortController = null;

    if (tokensReceived === 0) {
      // No tokens received: gracefully fall back to synchronous endpoint
      this.messages.update(msgs => msgs.filter(m => m.id !== assistantMsgId));
      this.activeAssistantMsgId = null;
      this.streamingNotice.set('Streaming connection unavailable. Automatically falling back to synchronous compliance engine...');
      this.executeSynchronous(sessionId, queryText);
    } else {
      // Partial tokens received: preserve tokens and mark interrupted
      this.isGeneratingResponse.set(false);
      this.isStreaming.set(false);
      this.activeAssistantMsgId = null;
      this.messages.update(msgs => msgs.map(m => {
        if (m.id === assistantMsgId) {
          return { ...m, content: m.content + ' [Stream interrupted: ' + (err.message || 'connection lost') + ']' };
        }
        return m;
      }));
      this.chatError.set('Streaming connection lost: ' + (err.message || 'unknown error'));
    }
  }

  private updateSessionStats(sessionId: string): void {
    this.sessions.update(list => list.map(s => {
      if (s.id === sessionId) {
        return { ...s, updatedAt: new Date().toISOString(), messageCount: (s.messageCount || 0) + 2 };
      }
      return s;
    }));
  }

  onKeyDown(event: KeyboardEvent): void {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      this.sendMessage();
    }
  }

  openSourceDetail(citation: Citation): void {
    this.selectedCitation.set(citation);
  }

  closeSourceDetail(): void {
    this.selectedCitation.set(null);
  }

  private scrollToBottom(): void {
    setTimeout(() => {
      if (this.messagesViewport) {
        this.messagesViewport.nativeElement.scrollTop = this.messagesViewport.nativeElement.scrollHeight;
      }
    }, 50);
  }
}
