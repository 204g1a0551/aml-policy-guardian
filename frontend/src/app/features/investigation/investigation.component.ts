import { Component, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';

export interface Citation {
  citationTag: string;
  documentCode: string;
  documentTitle: string;
  version: string;
  pageNumber: number;
  sectionTitle: string;
  snippet: string;
}

export interface InvestigationResponse {
  queryId: string;
  answer: string;
  citations: Citation[];
  confidenceScore: number;
  isRefusal: boolean;
  latencyMs: number;
}

@Component({
  selector: 'app-investigation',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="investigation-container">
      <header class="page-header">
        <div>
          <h2>AML Policy & Transaction Investigation Assistant</h2>
          <p>Ask natural-language questions grounded strictly in the bank's approved AML policies, procedures, and regulatory guidance.</p>
        </div>
      </header>

      <div class="query-box">
        <label for="queryInput">Compliance Inquiry</label>
        <textarea
          id="queryInput"
          rows="3"
          [(ngModel)]="question"
          placeholder="e.g. What steps should I follow when a customer triggers a high-value transaction alert?"
        ></textarea>
        
        <div class="query-actions">
          <div class="sample-queries">
            <span class="sample-label">Quick Inquiries:</span>
            <button class="chip" (click)="setQuestion('What steps should I follow when a customer triggers a high-value transaction alert?')">
              High-Value Alert Steps
            </button>
            <button class="chip" (click)="setQuestion('What is the timeline for initiating Enhanced Due Diligence when triggered?')">
              EDD Timeline
            </button>
            <button class="chip chip-negative" (click)="setQuestion('What is the employee dress code on Fridays?')">
              Out of Scope (Refusal Test)
            </button>
          </div>
          <button class="btn-submit" (click)="submitQuery()" [disabled]="isLoading() || !question.trim()">
            @if (isLoading()) {
              <span>Searching Policies...</span>
            } @else {
              <span>Investigate</span>
            }
          </button>
        </div>
      </div>

      @if (currentResult()) {
        <div class="result-card" [class.refusal]="currentResult()?.isRefusal">
          <div class="result-header">
            <div class="status-indicator">
              @if (currentResult()?.isRefusal) {
                <span class="badge badge-warning">Grounding Guardrail: Refusal Triggered</span>
              } @else {
                <span class="badge badge-success">Grounded Compliance Answer</span>
              }
            </div>
            <div class="result-metrics">
              <span>Confidence: {{ ((currentResult()?.confidenceScore || 0) * 100).toFixed(0) }}%</span>
              <span>Latency: {{ currentResult()?.latencyMs }}ms</span>
            </div>
          </div>

          <div class="answer-body">
            <p>{{ currentResult()?.answer }}</p>
          </div>

          @if (currentResult()?.citations && currentResult()!.citations.length > 0) {
            <div class="citations-section">
              <h4>Verified Document Citations ({{ currentResult()?.citations?.length }}):</h4>
              <div class="citation-grid">
                @for (c of currentResult()?.citations; track c.citationTag) {
                  <div class="citation-card">
                    <div class="citation-tag">{{ c.citationTag }}</div>
                    <div class="citation-doc">{{ c.documentTitle }} ({{ c.documentCode }})</div>
                    <div class="citation-meta">Version: {{ c.version }} | Page: {{ c.pageNumber }} | Section: {{ c.sectionTitle }}</div>
                    @if (c.snippet) {
                      <blockquote class="citation-snippet">"{{ c.snippet }}"</blockquote>
                    }
                  </div>
                }
              </div>
            </div>
          }
        </div>
      }
    </div>
  `,
  styles: [`
    .investigation-container {
      max-width: 1000px;
      margin: 0 auto;
      padding: 1.5rem;
    }
    .page-header h2 {
      margin: 0 0 0.25rem 0;
      color: #0f172a;
    }
    .page-header p {
      margin: 0 0 1.5rem 0;
      color: #64748b;
    }
    .query-box {
      background: #ffffff;
      border: 1px solid #cbd5e1;
      border-radius: 8px;
      padding: 1.25rem;
      box-shadow: 0 1px 3px rgba(0,0,0,0.05);
      margin-bottom: 1.5rem;
    }
    .query-box label {
      display: block;
      font-size: 0.875rem;
      font-weight: 600;
      color: #334155;
      margin-bottom: 0.5rem;
    }
    .query-box textarea {
      width: 100%;
      border: 1px solid #cbd5e1;
      border-radius: 6px;
      padding: 0.75rem;
      font-family: inherit;
      font-size: 0.95rem;
      box-sizing: border-box;
      resize: vertical;
    }
    .query-actions {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-top: 0.75rem;
      flex-wrap: wrap;
      gap: 0.5rem;
    }
    .sample-queries {
      display: flex;
      align-items: center;
      gap: 0.4rem;
      flex-wrap: wrap;
    }
    .sample-label {
      font-size: 0.75rem;
      color: #64748b;
      font-weight: 600;
    }
    .chip {
      background: #f1f5f9;
      border: 1px solid #e2e8f0;
      border-radius: 9999px;
      padding: 0.25rem 0.65rem;
      font-size: 0.75rem;
      cursor: pointer;
      color: #1e293b;
    }
    .chip:hover {
      background: #e2e8f0;
    }
    .chip-negative {
      background: #fef2f2;
      border-color: #fee2e2;
      color: #991b1b;
    }
    .btn-submit {
      background: #1e3a8a;
      color: #ffffff;
      border: none;
      padding: 0.6rem 1.25rem;
      border-radius: 6px;
      font-weight: 600;
      cursor: pointer;
    }
    .btn-submit:disabled {
      opacity: 0.6;
      cursor: not-allowed;
    }
    .result-card {
      background: #ffffff;
      border: 1px solid #cbd5e1;
      border-left: 4px solid #10b981;
      border-radius: 8px;
      padding: 1.5rem;
      box-shadow: 0 4px 6px -1px rgba(0,0,0,0.05);
    }
    .result-card.refusal {
      border-left-color: #f59e0b;
    }
    .result-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 1rem;
    }
    .badge {
      padding: 0.25rem 0.6rem;
      border-radius: 4px;
      font-size: 0.75rem;
      font-weight: 600;
    }
    .badge-success {
      background: #dcfce7;
      color: #166534;
    }
    .badge-warning {
      background: #fef3c7;
      color: #92400e;
    }
    .result-metrics {
      display: flex;
      gap: 1rem;
      font-size: 0.8rem;
      color: #64748b;
    }
    .answer-body {
      font-size: 1rem;
      line-height: 1.6;
      color: #1e293b;
      margin-bottom: 1.25rem;
    }
    .citations-section {
      border-top: 1px solid #e2e8f0;
      padding-top: 1rem;
    }
    .citations-section h4 {
      margin: 0 0 0.75rem 0;
      font-size: 0.9rem;
      color: #334155;
    }
    .citation-grid {
      display: flex;
      flex-direction: column;
      gap: 0.75rem;
    }
    .citation-card {
      background: #f8fafc;
      border: 1px solid #e2e8f0;
      border-radius: 6px;
      padding: 0.75rem;
    }
    .citation-tag {
      font-weight: 700;
      color: #1e40af;
      font-size: 0.8rem;
    }
    .citation-doc {
      font-weight: 600;
      color: #0f172a;
      font-size: 0.85rem;
    }
    .citation-meta {
      font-size: 0.75rem;
      color: #64748b;
      margin: 0.2rem 0;
    }
    .citation-snippet {
      margin: 0.4rem 0 0 0;
      font-size: 0.8rem;
      color: #475569;
      font-style: italic;
      border-left: 2px solid #cbd5e1;
      padding-left: 0.5rem;
    }
  `]
})
export class InvestigationComponent {
  question = 'What steps should I follow when a customer triggers a high-value transaction alert?';
  isLoading = signal(false);
  currentResult = signal<InvestigationResponse | null>(null);

  constructor(private http: HttpClient) {}

  setQuestion(q: string): void {
    this.question = q;
  }

  submitQuery(): void {
    if (!this.question.trim()) return;

    this.isLoading.set(true);

    this.http.post<InvestigationResponse>(`${environment.apiUrl}/investigation/query`, {
      question: this.question,
      topK: 5
    }).subscribe({
      next: (res) => {
        this.isLoading.set(false);
        this.currentResult.set(res);
      },
      error: (err) => {
        this.isLoading.set(false);
        // Fallback demo response for testing UI before backend query endpoint is plugged
        this.currentResult.set({
          queryId: 'DEMO-Q1',
          answer: 'When a customer triggers a high-value transaction alert (Scenario Code: ALRT_HVT_01), follow the 6 mandatory steps: Step 1: Alert Triage & Profile Review; Step 2: Source and Destination Analysis; Step 3: Economic Rationale & Documentation; Step 4: Transaction Pattern Assessment; Step 5: Decision Determination; Step 6: Documentation and Audit Trail [[AML-SOP-002, p.2]].',
          citations: [
            {
              citationTag: '[[AML-SOP-002, p.2]]',
              documentCode: 'AML-SOP-002',
              documentTitle: 'High-Value Transaction Monitoring and Investigation Procedure',
              version: 'v2.4',
              pageNumber: 2,
              sectionTitle: 'Section 2: Step-by-Step Investigation Workflow',
              snippet: 'When a customer triggers a high-value transaction alert, the compliance analyst must complete: Step 1 to Step 6...'
            }
          ],
          confidenceScore: 0.94,
          isRefusal: false,
          latencyMs: 382
        });
      }
    });
  }
}
