import { Component, OnInit, signal, ElementRef, ViewChild } from '@angular/core';
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
    <div class="workbench-layout">
      <!-- Left Panel: Investigation Case Sessions -->
      <aside class="case-sidebar">
        <div class="sidebar-top">
          <div class="sidebar-title-row">
            <span class="sidebar-heading">Investigation Cases</span>
            <span class="session-count-badge">{{ sessions().length }}</span>
          </div>
          <button 
            type="button" 
            class="btn-new-case" 
            (click)="createNewSession()" 
            [disabled]="isCreatingSession()"
          >
            <span>+ New Inquiry</span>
          </button>
        </div>

        <div class="search-filter-box">
          <input 
            type="text" 
            placeholder="Filter investigations..." 
            [(ngModel)]="searchQuery"
            class="filter-input"
          />
        </div>

        <div class="cases-list" role="list">
          @if (filteredSessions().length === 0) {
            <div class="no-cases">
              <span>No investigations found.</span>
            </div>
          } @else {
            @for (session of filteredSessions(); track session.id) {
              <div 
                class="case-item" 
                [class.active]="currentSession()?.id === session.id"
                (click)="selectSession(session)"
                role="listitem"
                tabindex="0"
                (keydown.enter)="selectSession(session)"
              >
                <div class="case-info">
                  <span class="case-title">{{ session.title }}</span>
                  <div class="case-meta">
                    <span class="case-date">{{ session.updatedAt | date:'shortDate' }}</span>
                    <span class="case-msg-count">{{ session.messageCount || 0 }} queries</span>
                  </div>
                </div>
                <button 
                  type="button" 
                  class="btn-close-case" 
                  title="Delete case session" 
                  (click)="deleteSession(session.id, $event)"
                  aria-label="Delete session"
                >
                  &times;
                </button>
              </div>
            }
          }
        </div>
      </aside>

      <!-- Main Workspace: Policy Research & Guidance -->
      <main class="workbench-main">
        <!-- Top Case Toolbar -->
        <div class="case-toolbar">
          <div class="toolbar-left">
            <h2 class="active-case-title">{{ currentSession()?.title || 'Policy Investigation Workspace' }}</h2>
            <div class="case-tags">
              <span class="pill-grounded">Strict Grounding Enforced</span>
              <span class="pill-meta">PostgreSQL 16 &bull; pgvector HNSW</span>
            </div>
          </div>

          <div class="toolbar-right">
            <button 
              type="button" 
              class="stream-toggle" 
              [class.stream-active]="useStreaming()"
              (click)="toggleStreaming()"
              title="Toggle streaming response delivery"
            >
              <span class="toggle-dot"></span>
              <span>Streaming: {{ useStreaming() ? 'Active' : 'Sync Fallback' }}</span>
            </button>
            <span class="msg-counter">{{ messages().length }} Records</span>
          </div>
        </div>

        <!-- Investigation Transcript Container -->
        <div class="transcript-viewport" #messagesViewport>
          @if (messages().length === 0 && !isLoadingMessages()) {
            <div class="empty-workbench-state">
              <div class="empty-header">
                <h3>AML Policy & Regulatory Guidance Workbench</h3>
                <p>Formulate queries grounded in authorized bank policies, SAR reporting procedures, and Customer Due Diligence thresholds.</p>
              </div>

              <div class="quick-inquiries-card">
                <span class="inquiries-label">Reference Compliance Inquiries:</span>
                <div class="inquiries-list">
                  <button 
                    type="button" 
                    class="inquiry-btn" 
                    (click)="useSuggestion('What is the mandatory threshold and timeframe for filing a Currency Transaction Report (CTR)?')"
                  >
                    <span class="inquiry-cat">CTR Reporting</span>
                    <span class="inquiry-text">What is the mandatory CTR threshold and filing deadline?</span>
                  </button>
                  <button 
                    type="button" 
                    class="inquiry-btn" 
                    (click)="useSuggestion('Explain Scenario TM-RULE-101 for structuring cash deposits.')"
                  >
                    <span class="inquiry-cat">Scenario TM-RULE-101</span>
                    <span class="inquiry-text">Explain Scenario TM-RULE-101 for structuring cash deposits.</span>
                  </button>
                  <button 
                    type="button" 
                    class="inquiry-btn" 
                    (click)="useSuggestion('What enhanced due diligence is required for Politically Exposed Persons (PEPs)?')"
                  >
                    <span class="inquiry-cat">PEP Guidance</span>
                    <span class="inquiry-text">What enhanced due diligence is required for Politically Exposed Persons (PEPs)?</span>
                  </button>
                </div>
              </div>
            </div>
          }

          @if (isLoadingMessages()) {
            <div class="loading-transcript">
              <div class="spinner"></div>
              <span>Retrieving compliance records...</span>
            </div>
          }

          <!-- Case Memorandum Transcript -->
          @for (msg of messages(); track msg.id) {
            <div class="memorandum-entry" [class.user-entry]="msg.role === 'USER'" [class.assistant-entry]="msg.role !== 'USER'">
              <!-- Header Bar of Entry -->
              <div class="entry-header">
                <div class="entry-origin">
                  @if (msg.role === 'USER') {
                    <span class="origin-label user-label">COMPLIANCE INVESTIGATOR QUERY</span>
                  } @else {
                    <span class="origin-label assistant-label">REGULATORY DETERMINATION & GUIDANCE</span>
                  }
                  <span class="entry-timestamp">{{ msg.createdAt | date:'mediumTime' }}</span>
                </div>

                @if (msg.role !== 'USER') {
                  <div class="entry-badge-wrap">
                    @if (isRefusalMessage(msg.content)) {
                      <span class="badge-status status-warning">GUARDRAIL: REFUSAL / OUT OF SCOPE</span>
                    } @else {
                      <span class="badge-status status-ready">GROUNDED IN APPROVED POLICY</span>
                    }
                  </div>
                }
              </div>

              <!-- Content Body -->
              <div class="entry-body">
                @if (!msg.content && isGeneratingResponse() && msg.role !== 'USER') {
                  <div class="processing-indicator">
                    <span class="pulse-indicator"></span>
                    <span>Retrieving policy sections and synthesizing guidance...</span>
                  </div>
                } @else {
                  <div class="entry-text">
                    {{ msg.content }}
                    @if (isGeneratingResponse() && isStreaming() && msg.id === activeAssistantMsgId) {
                      <span class="cursor-blink">|</span>
                    }
                  </div>
                }
              </div>

              <!-- Citations & Verified Sources Panel -->
              @if (msg.citations && msg.citations.length > 0) {
                <div class="citations-panel">
                  <div class="citations-header">
                    <span class="citations-caption">Verified Authoritative Sources ({{ msg.citations.length }}):</span>
                  </div>
                  <div class="citations-grid">
                    @for (cite of msg.citations; track ($index + '-' + cite.documentId)) {
                      <div class="citation-card" (click)="openSourceDetail(cite)">
                        <div class="cite-top">
                          <span class="cite-title">{{ cite.documentTitle }}</span>
                          <span class="cite-match">{{ ((cite.similarityScore || cite.similarity || 0) * 100) | number:'1.0-0' }}% Align</span>
                        </div>
                        <div class="cite-coords">
                          @if (cite.section) {
                            <span class="cite-sec">&sect; {{ cite.section }}</span>
                          }
                          @if (cite.pageNumber) {
                            <span class="cite-page">Page {{ cite.pageNumber }}</span>
                          }
                        </div>
                        @if (cite.chunkSnippet) {
                          <blockquote class="cite-snippet">"{{ cite.chunkSnippet }}"</blockquote>
                        }
                      </div>
                    }
                  </div>
                </div>
              }
            </div>
          }

          @if (isGeneratingResponse() && !isStreaming()) {
            <div class="memorandum-entry assistant-entry">
              <div class="entry-header">
                <div class="entry-origin">
                  <span class="origin-label assistant-label">REGULATORY DETERMINATION IN PROGRESS</span>
                </div>
              </div>
              <div class="entry-body">
                <div class="processing-indicator">
                  <span class="pulse-indicator"></span>
                  <span>Executing deterministic RAG pipeline & similarity search...</span>
                </div>
              </div>
            </div>
          }

          @if (streamingNotice()) {
            <div class="notice-strip info-strip">
              <span>{{ streamingNotice() }}</span>
              <button type="button" (click)="streamingNotice.set(null)" class="btn-dismiss">&times;</button>
            </div>
          }

          @if (chatError()) {
            <div class="notice-strip danger-strip">
              <span>{{ chatError() }}</span>
              <button type="button" (click)="chatError.set(null)" class="btn-dismiss">&times;</button>
            </div>
          }
        </div>

        <!-- Query Formulation Area -->
        <footer class="query-area">
          <form (ngSubmit)="sendMessage()" class="query-form">
            <div class="form-header-bar">
              <label for="queryInput" class="form-label">Compliance Inquiry or Transaction Scenario</label>
              <span class="key-hint">Enter to submit &bull; Shift+Enter for newline</span>
            </div>

            <textarea
              #queryInput
              id="queryInput"
              rows="2"
              placeholder="Formulate an AML policy or transaction inquiry (e.g. Under what criteria must a SAR be filed immediately?)..."
              [(ngModel)]="currentQuery"
              name="currentQuery"
              [disabled]="isGeneratingResponse()"
              (keydown)="onKeyDown($event)"
              class="query-textarea"
            ></textarea>

            <div class="form-action-bar">
              <div class="query-meta-stats">
                <span>{{ currentQuery.length }} characters</span>
              </div>

              <div class="action-buttons">
                @if (isGeneratingResponse()) {
                  <button 
                    type="button" 
                    class="btn-cancel" 
                    (click)="cancelGeneration()"
                    title="Halt response generation"
                  >
                    <span>Stop</span>
                  </button>
                }
                <button 
                  type="submit" 
                  class="btn-submit-inquiry" 
                  [disabled]="!currentQuery.trim() || isGeneratingResponse()"
                >
                  @if (isGeneratingResponse()) {
                    <span class="spinner-tiny"></span>
                    <span>Processing...</span>
                  } @else {
                    <span>Submit Inquiry</span>
                  }
                </button>
              </div>
            </div>
          </form>
        </footer>
      </main>

      <!-- Institutional Source Inspector Modal -->
      @if (selectedCitation()) {
        <div class="enterprise-modal-backdrop" (click)="closeSourceDetail()">
          <div class="enterprise-modal-dialog" (click)="$event.stopPropagation()">
            <div class="enterprise-modal-header">
              <h3>Authoritative Policy Source Document</h3>
              <button type="button" class="enterprise-modal-close" (click)="closeSourceDetail()">&times;</button>
            </div>
            <div class="enterprise-modal-body">
              <div class="modal-attr-grid">
                <div class="modal-attr">
                  <span class="attr-k">Document Title:</span>
                  <span class="attr-v font-bold">{{ selectedCitation()?.documentTitle }}</span>
                </div>
                <div class="modal-attr">
                  <span class="attr-k">File Reference:</span>
                  <code class="attr-code">{{ selectedCitation()?.documentFilename }}</code>
                </div>
                <div class="modal-attr">
                  <span class="attr-k">Section / Clause:</span>
                  <span class="attr-v">{{ selectedCitation()?.section || 'Entire Document' }}</span>
                </div>
                <div class="modal-attr">
                  <span class="attr-k">Page Reference:</span>
                  <span class="attr-v">{{ selectedCitation()?.pageNumber ? ('Page ' + selectedCitation()?.pageNumber) : 'N/A' }}</span>
                </div>
                <div class="modal-attr">
                  <span class="attr-k">Vector Alignment:</span>
                  <span class="badge-status status-ready">
                    {{ (((selectedCitation()?.similarityScore ?? selectedCitation()?.similarity) || 0) * 100) | number:'1.1-1' }}% Cosine Alignment
                  </span>
                </div>
              </div>

              <div class="excerpt-box">
                <div class="excerpt-heading">Verified Extracted Text Excerpt:</div>
                <div class="excerpt-content">{{ selectedCitation()?.chunkSnippet }}</div>
              </div>
            </div>
            <div class="enterprise-modal-footer">
              <button type="button" class="btn-secondary" (click)="closeSourceDetail()">Close</button>
            </div>
          </div>
        </div>
      }
    </div>
  `,
  styles: [`
    .workbench-layout {
      display: flex;
      height: calc(100vh - 56px);
      background-color: #f8fafc;
      overflow: hidden;
    }

    /* Left Panel: Investigation Case Sidebar */
    .case-sidebar {
      width: 290px;
      min-width: 260px;
      background-color: #ffffff;
      border-right: 1px solid #cbd5e1;
      display: flex;
      flex-direction: column;
    }

    .sidebar-top {
      padding: 0.85rem 1rem;
      border-bottom: 1px solid #e2e8f0;
      display: flex;
      flex-direction: column;
      gap: 0.65rem;
    }

    .sidebar-title-row {
      display: flex;
      justify-content: space-between;
      align-items: center;
    }

    .sidebar-heading {
      font-size: 0.825rem;
      font-weight: 700;
      color: #0f172a;
      text-transform: uppercase;
      letter-spacing: 0.04em;
    }

    .session-count-badge {
      background-color: #f1f5f9;
      color: #475569;
      font-size: 0.725rem;
      font-weight: 600;
      padding: 0.1rem 0.4rem;
      border-radius: 3px;
    }

    .btn-new-case {
      width: 100%;
      background-color: #1e3a8a;
      color: #ffffff;
      border: 1px solid #1e3a8a;
      border-radius: 4px;
      padding: 0.45rem;
      font-size: 0.8rem;
      font-weight: 600;
      cursor: pointer;
      transition: background-color 0.12s ease;
    }

    .btn-new-case:hover:not(:disabled) {
      background-color: #1d4ed8;
    }

    .search-filter-box {
      padding: 0.5rem 1rem;
      border-bottom: 1px solid #f1f5f9;
    }

    .filter-input {
      width: 100%;
      padding: 0.4rem 0.65rem;
      font-size: 0.8rem;
      border: 1px solid #cbd5e1;
      border-radius: 3px;
      outline: none;
      box-sizing: border-box;
    }

    .cases-list {
      flex: 1;
      overflow-y: auto;
      padding: 0.35rem 0.5rem;
    }

    .no-cases {
      padding: 2rem 1rem;
      text-align: center;
      color: #94a3b8;
      font-size: 0.8rem;
    }

    .case-item {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 0.6rem 0.65rem;
      border-radius: 4px;
      margin-bottom: 0.2rem;
      cursor: pointer;
      border: 1px solid transparent;
      transition: background-color 0.12s ease;
    }

    .case-item:hover {
      background-color: #f1f5f9;
    }

    .case-item.active {
      background-color: #eff6ff;
      border-color: #bfdbfe;
      border-left: 3px solid #1d4ed8;
    }

    .case-info {
      display: flex;
      flex-direction: column;
      overflow: hidden;
      margin-right: 0.5rem;
    }

    .case-title {
      font-size: 0.825rem;
      font-weight: 600;
      color: #0f172a;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }

    .case-meta {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      font-size: 0.7rem;
      color: #64748b;
      margin-top: 0.15rem;
    }

    .btn-close-case {
      background: transparent;
      border: none;
      color: #94a3b8;
      font-size: 1.1rem;
      cursor: pointer;
      line-height: 1;
      padding: 0.15rem;
    }

    .btn-close-case:hover {
      color: #ef4444;
    }

    /* Main Workspace */
    .workbench-main {
      flex: 1;
      display: flex;
      flex-direction: column;
      height: 100%;
      background-color: #ffffff;
    }

    .case-toolbar {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 0.75rem 1.5rem;
      background-color: #ffffff;
      border-bottom: 1px solid #cbd5e1;
    }

    .active-case-title {
      font-size: 1.05rem;
      font-weight: 700;
      color: #0f172a;
      margin-bottom: 0.2rem;
    }

    .case-tags {
      display: flex;
      align-items: center;
      gap: 0.5rem;
    }

    .pill-grounded {
      background-color: #ecfdf5;
      color: #065f46;
      border: 1px solid #a7f3d0;
      font-size: 0.68rem;
      font-weight: 700;
      padding: 0.1rem 0.35rem;
      border-radius: 2px;
    }

    .pill-meta {
      font-size: 0.7rem;
      color: #64748b;
    }

    .toolbar-right {
      display: flex;
      align-items: center;
      gap: 0.75rem;
    }

    .stream-toggle {
      display: inline-flex;
      align-items: center;
      gap: 0.4rem;
      background-color: #f8fafc;
      border: 1px solid #cbd5e1;
      border-radius: 4px;
      padding: 0.3rem 0.65rem;
      font-size: 0.75rem;
      font-weight: 600;
      color: #475569;
      cursor: pointer;
    }

    .stream-toggle.stream-active {
      background-color: #ecfdf5;
      border-color: #a7f3d0;
      color: #065f46;
    }

    .toggle-dot {
      width: 7px;
      height: 7px;
      border-radius: 50%;
      background-color: #94a3b8;
    }

    .stream-toggle.stream-active .toggle-dot {
      background-color: #10b981;
    }

    .msg-counter {
      font-size: 0.75rem;
      color: #64748b;
      background-color: #f1f5f9;
      padding: 0.25rem 0.5rem;
      border-radius: 3px;
    }

    /* Transcript Viewport */
    .transcript-viewport {
      flex: 1;
      overflow-y: auto;
      padding: 1.5rem 2rem;
      display: flex;
      flex-direction: column;
      gap: 1.25rem;
      background-color: #f8fafc;
    }

    .empty-workbench-state {
      max-width: 650px;
      margin: 2.5rem auto;
      background-color: #ffffff;
      border: 1px solid #cbd5e1;
      border-radius: 6px;
      padding: 1.75rem;
      box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
    }

    .empty-header h3 {
      font-size: 1.15rem;
      font-weight: 700;
      color: #0f172a;
      margin-bottom: 0.35rem;
    }

    .empty-header p {
      font-size: 0.85rem;
      color: #64748b;
      margin-bottom: 1.25rem;
    }

    .inquiries-label {
      display: block;
      font-size: 0.725rem;
      font-weight: 600;
      color: #475569;
      text-transform: uppercase;
      letter-spacing: 0.04em;
      margin-bottom: 0.5rem;
    }

    .inquiries-list {
      display: flex;
      flex-direction: column;
      gap: 0.45rem;
    }

    .inquiry-btn {
      display: flex;
      align-items: center;
      gap: 0.65rem;
      background-color: #f8fafc;
      border: 1px solid #e2e8f0;
      border-radius: 4px;
      padding: 0.55rem 0.75rem;
      text-align: left;
      cursor: pointer;
      transition: all 0.12s ease;
    }

    .inquiry-btn:hover {
      background-color: #eff6ff;
      border-color: #bfdbfe;
    }

    .inquiry-cat {
      font-size: 0.7rem;
      font-weight: 700;
      background-color: #e2e8f0;
      color: #1e293b;
      padding: 0.15rem 0.4rem;
      border-radius: 3px;
      flex-shrink: 0;
    }

    .inquiry-text {
      font-size: 0.825rem;
      color: #0f172a;
    }

    .loading-transcript {
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 0.65rem;
      padding: 2.5rem;
      color: #64748b;
      font-size: 0.85rem;
    }

    /* Memorandum Entry */
    .memorandum-entry {
      background-color: #ffffff;
      border: 1px solid #cbd5e1;
      border-radius: 4px;
      box-shadow: 0 1px 2px rgba(0, 0, 0, 0.03);
      overflow: hidden;
    }

    .memorandum-entry.user-entry {
      border-left: 4px solid #1e3a8a;
    }

    .memorandum-entry.assistant-entry {
      border-left: 4px solid #059669;
    }

    .entry-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 0.6rem 1rem;
      background-color: #f8fafc;
      border-bottom: 1px solid #e2e8f0;
    }

    .entry-origin {
      display: flex;
      align-items: center;
      gap: 0.75rem;
    }

    .origin-label {
      font-size: 0.725rem;
      font-weight: 700;
      letter-spacing: 0.04em;
    }

    .user-label {
      color: #1e3a8a;
    }

    .assistant-label {
      color: #065f46;
    }

    .entry-timestamp {
      font-size: 0.7rem;
      color: #94a3b8;
    }

    .entry-body {
      padding: 1rem 1.15rem;
    }

    .entry-text {
      font-size: 0.9rem;
      line-height: 1.6;
      color: #0f172a;
      white-space: pre-wrap;
      word-break: break-word;
    }

    .cursor-blink {
      display: inline-block;
      color: #1d4ed8;
      font-weight: bold;
      animation: blink 0.8s infinite;
      margin-left: 2px;
    }

    @keyframes blink {
      0%, 100% { opacity: 1; }
      50% { opacity: 0; }
    }

    .processing-indicator {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      color: #64748b;
      font-size: 0.85rem;
    }

    .pulse-indicator {
      width: 8px;
      height: 8px;
      background-color: #1d4ed8;
      border-radius: 50%;
      animation: pulse 1.2s infinite ease-in-out;
    }

    @keyframes pulse {
      0%, 100% { opacity: 0.4; transform: scale(0.85); }
      50% { opacity: 1; transform: scale(1.1); }
    }

    /* Citations Panel */
    .citations-panel {
      padding: 0.85rem 1.15rem;
      background-color: #f8fafc;
      border-top: 1px solid #e2e8f0;
    }

    .citations-caption {
      font-size: 0.725rem;
      font-weight: 700;
      color: #475569;
      text-transform: uppercase;
      letter-spacing: 0.04em;
    }

    .citations-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
      gap: 0.65rem;
      margin-top: 0.5rem;
    }

    .citation-card {
      background-color: #ffffff;
      border: 1px solid #cbd5e1;
      border-radius: 4px;
      padding: 0.65rem 0.75rem;
      cursor: pointer;
      transition: all 0.12s ease;
    }

    .citation-card:hover {
      border-color: #1d4ed8;
      box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);
    }

    .cite-top {
      display: flex;
      justify-content: space-between;
      align-items: flex-start;
      margin-bottom: 0.25rem;
      gap: 0.5rem;
    }

    .cite-title {
      font-size: 0.8rem;
      font-weight: 600;
      color: #0f172a;
      display: -webkit-box;
      -webkit-line-clamp: 1;
      -webkit-box-orient: vertical;
      overflow: hidden;
    }

    .cite-match {
      background-color: #ecfdf5;
      color: #065f46;
      border: 1px solid #a7f3d0;
      font-size: 0.68rem;
      font-weight: 700;
      padding: 0.1rem 0.35rem;
      border-radius: 2px;
      white-space: nowrap;
    }

    .cite-coords {
      display: flex;
      gap: 0.5rem;
      font-size: 0.725rem;
      color: #64748b;
      margin-bottom: 0.35rem;
    }

    .cite-snippet {
      font-size: 0.75rem;
      color: #475569;
      line-height: 1.35;
      display: -webkit-box;
      -webkit-line-clamp: 2;
      -webkit-box-orient: vertical;
      overflow: hidden;
      border-left: 2px solid #cbd5e1;
      padding-left: 0.4rem;
      margin: 0;
      font-style: italic;
    }

    /* Notice Strips */
    .notice-strip {
      padding: 0.65rem 1rem;
      border-radius: 4px;
      font-size: 0.8rem;
      display: flex;
      justify-content: space-between;
      align-items: center;
    }

    .info-strip {
      background-color: #eff6ff;
      border: 1px solid #bfdbfe;
      color: #1e40af;
    }

    .danger-strip {
      background-color: #fef2f2;
      border: 1px solid #fecaca;
      color: #991b1b;
    }

    .btn-dismiss {
      background: none;
      border: none;
      font-size: 1.1rem;
      cursor: pointer;
      color: inherit;
    }

    /* Query Area */
    .query-area {
      padding: 1rem 1.5rem;
      background-color: #ffffff;
      border-top: 1px solid #cbd5e1;
    }

    .query-form {
      display: flex;
      flex-direction: column;
      border: 1px solid #cbd5e1;
      border-radius: 4px;
      padding: 0.75rem;
      background-color: #ffffff;
      transition: border-color 0.15s ease;
    }

    .query-form:focus-within {
      border-color: #1d4ed8;
      box-shadow: 0 0 0 2px rgba(29, 78, 216, 0.1);
    }

    .form-header-bar {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 0.4rem;
    }

    .form-label {
      font-size: 0.775rem;
      font-weight: 600;
      color: #334155;
      text-transform: uppercase;
      letter-spacing: 0.03em;
    }

    .key-hint {
      font-size: 0.7rem;
      color: #94a3b8;
    }

    .query-textarea {
      width: 100%;
      border: none;
      outline: none;
      resize: vertical;
      font-family: inherit;
      font-size: 0.9rem;
      color: #0f172a;
      min-height: 48px;
    }

    .form-action-bar {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-top: 0.4rem;
      padding-top: 0.4rem;
      border-top: 1px solid #f1f5f9;
    }

    .query-meta-stats {
      font-size: 0.725rem;
      color: #94a3b8;
    }

    .action-buttons {
      display: flex;
      align-items: center;
      gap: 0.5rem;
    }

    .btn-cancel {
      background-color: #fef2f2;
      color: #991b1b;
      border: 1px solid #fecaca;
      border-radius: 3px;
      padding: 0.35rem 0.65rem;
      font-size: 0.775rem;
      font-weight: 600;
      cursor: pointer;
    }

    .btn-submit-inquiry {
      display: inline-flex;
      align-items: center;
      gap: 0.4rem;
      background-color: #1e3a8a;
      color: #ffffff;
      border: 1px solid #1e3a8a;
      border-radius: 3px;
      padding: 0.4rem 0.85rem;
      font-size: 0.825rem;
      font-weight: 600;
      cursor: pointer;
      transition: background-color 0.12s ease;
    }

    .btn-submit-inquiry:hover:not(:disabled) {
      background-color: #1d4ed8;
      border-color: #1d4ed8;
    }

    .btn-submit-inquiry:disabled {
      background-color: #cbd5e1;
      border-color: #cbd5e1;
      cursor: not-allowed;
    }

    /* Modal Attr Grid */
    .modal-attr-grid {
      display: flex;
      flex-direction: column;
      gap: 0.5rem;
      margin-bottom: 1rem;
    }

    .modal-attr {
      display: flex;
      justify-content: space-between;
      align-items: center;
      font-size: 0.825rem;
      padding-bottom: 0.35rem;
      border-bottom: 1px solid #f1f5f9;
    }

    .attr-k {
      font-weight: 600;
      color: #475569;
    }

    .attr-v {
      color: #0f172a;
    }

    .attr-code {
      font-family: monospace;
      font-size: 0.775rem;
      background-color: #f1f5f9;
      padding: 0.1rem 0.3rem;
      border-radius: 2px;
    }

    .font-bold {
      font-weight: 600;
    }

    .excerpt-box {
      background-color: #f8fafc;
      border: 1px solid #e2e8f0;
      border-radius: 4px;
      padding: 0.75rem;
    }

    .excerpt-heading {
      font-size: 0.725rem;
      font-weight: 600;
      color: #475569;
      text-transform: uppercase;
      margin-bottom: 0.35rem;
    }

    .excerpt-content {
      font-size: 0.825rem;
      line-height: 1.5;
      color: #1e293b;
      white-space: pre-wrap;
    }

    .spinner {
      width: 16px;
      height: 16px;
      border: 2px solid #cbd5e1;
      border-top-color: #1e3a8a;
      border-radius: 50%;
      animation: spin 0.8s linear infinite;
    }

    .spinner-tiny {
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
          const content = m.content ? `${m.content} [Stopped by investigator]` : '[Stopped by investigator]';
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
          onStart: () => {},
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

  isRefusalMessage(content?: string): boolean {
    if (!content) return false;
    const lower = content.toLowerCase();
    return lower.includes('cannot be found') ||
           lower.includes('not mentioned') ||
           lower.includes('no information') ||
           lower.includes('out of scope') ||
           lower.includes('unable to verify');
  }

  private scrollToBottom(): void {
    setTimeout(() => {
      if (this.messagesViewport) {
        this.messagesViewport.nativeElement.scrollTop = this.messagesViewport.nativeElement.scrollHeight;
      }
    }, 50);
  }
}
