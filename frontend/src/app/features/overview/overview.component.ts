import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterModule } from '@angular/router';
import { DocumentService } from '../../core/services/document.service';
import { ChatService } from '../../core/services/chat.service';
import { AuthService } from '../../core/services/auth.service';
import { DocumentItem } from '../../core/models/document.models';
import { ChatSession } from '../../core/models/chat.models';

@Component({
  selector: 'app-overview',
  standalone: true,
  imports: [CommonModule, RouterModule],
  template: `
    <div class="overview-container">
      <!-- Page Header -->
      <header class="page-header">
        <div>
          <h2>AML Compliance Operational Overview</h2>
          <p class="subtitle">Real-time status of authoritative policies, vector indexes, and active transaction investigations.</p>
        </div>
        <div class="header-actions">
          <button class="btn-primary" (click)="goToInvestigation()">
            <svg class="btn-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z"/>
            </svg>
            <span>Launch Investigation</span>
          </button>
        </div>
      </header>

      <!-- Key Metrics Row -->
      <section class="metrics-grid">
        <div class="metric-card">
          <div class="metric-header">
            <span class="metric-label">Approved Policy Documents</span>
            <span class="status-indicator ready">READY</span>
          </div>
          <div class="metric-value">{{ readyDocCount() }}</div>
          <div class="metric-meta">{{ totalDocCount() }} Total Registered &bull; {{ processingDocCount() }} Processing</div>
        </div>

        <div class="metric-card">
          <div class="metric-header">
            <span class="metric-label">Vector Store (pgvector)</span>
            <span class="status-indicator ready">CONNECTED</span>
          </div>
          <div class="metric-value">1,536d</div>
          <div class="metric-meta">Cosine Distance &bull; HNSW Indexing Enabled</div>
        </div>

        <div class="metric-card">
          <div class="metric-header">
            <span class="metric-label">Grounding Guardrails</span>
            <span class="status-indicator ready">ENFORCED</span>
          </div>
          <div class="metric-value">Strict</div>
          <div class="metric-meta">Anti-Prompt Injection &bull; PII Masking Active</div>
        </div>

        <div class="metric-card">
          <div class="metric-header">
            <span class="metric-label">Active Investigations</span>
            <span class="status-indicator info">RECORDED</span>
          </div>
          <div class="metric-value">{{ recentSessions().length }}</div>
          <div class="metric-meta">Full Audit Trail &bull; Session Isolation</div>
        </div>
      </section>

      <!-- Two-Column Operational Layout -->
      <div class="content-columns">
        <!-- Left: Recent Investigations -->
        <div class="column-left">
          <div class="panel-card">
            <div class="panel-header">
              <h3>Recent Compliance Inquiries</h3>
              <a routerLink="/history" class="panel-link">View All History &rarr;</a>
            </div>

            @if (isLoadingSessions()) {
              <div class="loading-state">
                <span>Loading investigation logs...</span>
              </div>
            } @else if (recentSessions().length === 0) {
              <div class="empty-panel">
                <p>No recent investigations found.</p>
                <button class="btn-secondary" (click)="goToInvestigation()">Start First Investigation</button>
              </div>
            } @else {
              <div class="table-wrapper">
                <table class="panel-table">
                  <thead>
                    <tr>
                      <th>Investigation Scenario</th>
                      <th>Inquiries</th>
                      <th>Last Updated</th>
                      <th>Action</th>
                    </tr>
                  </thead>
                  <tbody>
                    @for (session of recentSessions().slice(0, 5); track session.id) {
                      <tr>
                        <td class="scenario-cell">
                          <span class="scenario-title">{{ session.title }}</span>
                        </td>
                        <td>
                          <span class="badge-count">{{ session.messageCount }} messages</span>
                        </td>
                        <td class="date-cell">{{ session.updatedAt | date:'short' }}</td>
                        <td>
                          <button class="btn-table-action" (click)="resumeSession(session.id)">
                            Resume &rarr;
                          </button>
                        </td>
                      </tr>
                    }
                  </tbody>
                </table>
              </div>
            }
          </div>
        </div>

        <!-- Right: Governance & Ingestion Status -->
        <div class="column-right">
          <div class="panel-card">
            <div class="panel-header">
              <h3>System Governance & Compliance Controls</h3>
            </div>
            
            <div class="governance-list">
              <div class="gov-item">
                <div class="gov-key">Regulatory Framework</div>
                <div class="gov-val">BSA / FinCEN Anti-Money Laundering & OFAC Guidance</div>
              </div>
              <div class="gov-item">
                <div class="gov-key">RAG Retrieval Engine</div>
                <div class="gov-val">Spring AI VectorStore + pgvector Cosine Top-K</div>
              </div>
              <div class="gov-item">
                <div class="gov-key">Chunking Strategy</div>
                <div class="gov-val">500 Characters &bull; 50 Characters Overlap &bull; Page-Indexed</div>
              </div>
              <div class="gov-item">
                <div class="gov-key">Refusal Guardrail</div>
                <div class="gov-val">Mandatory Unknown/Refusal on Unverified Claims</div>
              </div>
              <div class="gov-item">
                <div class="gov-key">Audit & Forensics</div>
                <div class="gov-val">Immutable Cryptographic Audit Logging (Correlation IDs)</div>
              </div>
              <div class="gov-item">
                <div class="gov-key">Rate Limiting</div>
                <div class="gov-val">Active (Sliding Window: 15/min Auth, 60/min Chat)</div>
              </div>
            </div>

            <div class="quick-links">
              <span class="quick-title">Quick Compliance Actions:</span>
              <div class="quick-buttons">
                @if (authService.isAdmin()) {
                  <button class="btn-quick" routerLink="/documents">
                    <span>Repository Catalog</span>
                  </button>
                  <button class="btn-quick" routerLink="/admin/audit">
                    <span>Audit Trail Log</span>
                  </button>
                }
                <button class="btn-quick" routerLink="/chat">
                  <span>Policy Research</span>
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .overview-container {
      max-width: 1240px;
      margin: 0 auto;
      padding: 1.75rem 1.5rem;
    }

    .page-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 1.5rem;
      border-bottom: 1px solid #e2e8f0;
      padding-bottom: 1rem;
    }

    .page-header h2 {
      font-size: 1.35rem;
      font-weight: 700;
      color: #0f172a;
      margin-bottom: 0.25rem;
    }

    .subtitle {
      font-size: 0.85rem;
      color: #64748b;
    }

    .btn-icon {
      width: 15px;
      height: 15px;
    }

    /* Metrics Grid */
    .metrics-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
      gap: 1rem;
      margin-bottom: 1.75rem;
    }

    .metric-card {
      background-color: #ffffff;
      border: 1px solid #cbd5e1;
      border-radius: 4px;
      padding: 1rem 1.15rem;
      box-shadow: 0 1px 2px rgba(0, 0, 0, 0.04);
    }

    .metric-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 0.5rem;
    }

    .metric-label {
      font-size: 0.775rem;
      font-weight: 600;
      color: #475569;
      text-transform: uppercase;
      letter-spacing: 0.03em;
    }

    .status-indicator {
      font-size: 0.65rem;
      font-weight: 700;
      padding: 0.1rem 0.35rem;
      border-radius: 2px;
      letter-spacing: 0.04em;
    }

    .status-indicator.ready {
      background-color: #ecfdf5;
      color: #065f46;
      border: 1px solid #a7f3d0;
    }

    .status-indicator.info {
      background-color: #f0f9ff;
      color: #075985;
      border: 1px solid #bae6fd;
    }

    .metric-value {
      font-size: 1.6rem;
      font-weight: 700;
      color: #0f172a;
      line-height: 1.1;
      margin-bottom: 0.35rem;
    }

    .metric-meta {
      font-size: 0.725rem;
      color: #64748b;
    }

    /* Columns Layout */
    .content-columns {
      display: grid;
      grid-template-columns: 1.5fr 1fr;
      gap: 1.25rem;
    }

    @media (max-width: 900px) {
      .content-columns {
        grid-template-columns: 1fr;
      }
    }

    .panel-card {
      background-color: #ffffff;
      border: 1px solid #cbd5e1;
      border-radius: 4px;
      padding: 1.25rem;
      box-shadow: 0 1px 2px rgba(0, 0, 0, 0.04);
    }

    .panel-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 1rem;
      border-bottom: 1px solid #f1f5f9;
      padding-bottom: 0.65rem;
    }

    .panel-header h3 {
      font-size: 0.95rem;
      font-weight: 700;
      color: #0f172a;
    }

    .panel-link {
      font-size: 0.775rem;
      color: #1d4ed8;
      text-decoration: none;
      font-weight: 600;
    }

    .panel-link:hover {
      text-decoration: underline;
    }

    /* Table within panel */
    .table-wrapper {
      overflow-x: auto;
    }

    .panel-table {
      width: 100%;
      border-collapse: collapse;
      font-size: 0.825rem;
      text-align: left;
    }

    .panel-table th {
      padding: 0.5rem 0.65rem;
      font-size: 0.7rem;
      font-weight: 600;
      color: #64748b;
      text-transform: uppercase;
      letter-spacing: 0.04em;
      border-bottom: 1px solid #e2e8f0;
      background-color: #f8fafc;
    }

    .panel-table td {
      padding: 0.65rem;
      border-bottom: 1px solid #f1f5f9;
      color: #334155;
    }

    .scenario-title {
      font-weight: 600;
      color: #0f172a;
      display: -webkit-box;
      -webkit-line-clamp: 1;
      -webkit-box-orient: vertical;
      overflow: hidden;
      max-width: 250px;
    }

    .badge-count {
      background-color: #f1f5f9;
      color: #475569;
      padding: 0.15rem 0.4rem;
      border-radius: 3px;
      font-size: 0.725rem;
    }

    .date-cell {
      color: #64748b;
      font-size: 0.75rem;
      white-space: nowrap;
    }

    .btn-table-action {
      background: none;
      border: none;
      color: #1d4ed8;
      font-size: 0.775rem;
      font-weight: 600;
      cursor: pointer;
      padding: 0;
    }

    .btn-table-action:hover {
      text-decoration: underline;
    }

    /* Governance List */
    .governance-list {
      display: flex;
      flex-direction: column;
      gap: 0.75rem;
      margin-bottom: 1.25rem;
    }

    .gov-item {
      display: flex;
      flex-direction: column;
      gap: 0.15rem;
      padding-bottom: 0.5rem;
      border-bottom: 1px solid #f1f5f9;
    }

    .gov-key {
      font-size: 0.725rem;
      font-weight: 600;
      color: #64748b;
      text-transform: uppercase;
      letter-spacing: 0.03em;
    }

    .gov-val {
      font-size: 0.825rem;
      color: #0f172a;
      font-weight: 500;
    }

    .quick-links {
      background-color: #f8fafc;
      border: 1px solid #e2e8f0;
      border-radius: 4px;
      padding: 0.75rem;
    }

    .quick-title {
      display: block;
      font-size: 0.725rem;
      font-weight: 600;
      color: #475569;
      margin-bottom: 0.5rem;
      text-transform: uppercase;
    }

    .quick-buttons {
      display: flex;
      gap: 0.5rem;
      flex-wrap: wrap;
    }

    .btn-quick {
      background-color: #ffffff;
      border: 1px solid #cbd5e1;
      border-radius: 3px;
      padding: 0.35rem 0.65rem;
      font-size: 0.75rem;
      font-weight: 500;
      color: #1e3a8a;
      cursor: pointer;
    }

    .btn-quick:hover {
      background-color: #eff6ff;
      border-color: #bfdbfe;
    }

    .loading-state, .empty-panel {
      text-align: center;
      padding: 2rem;
      color: #64748b;
      font-size: 0.85rem;
    }
  `]
})
export class OverviewComponent implements OnInit {
  documents = signal<DocumentItem[]>([]);
  recentSessions = signal<ChatSession[]>([]);
  isLoadingSessions = signal(true);

  constructor(
    public authService: AuthService,
    private documentService: DocumentService,
    private chatService: ChatService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.loadData();
  }

  loadData(): void {
    this.documentService.getDocuments().subscribe({
      next: (docs) => this.documents.set(docs),
      error: () => {}
    });

    this.isLoadingSessions.set(true);
    this.chatService.getSessions().subscribe({
      next: (sessions) => {
        this.recentSessions.set(sessions);
        this.isLoadingSessions.set(false);
      },
      error: () => {
        this.isLoadingSessions.set(false);
      }
    });
  }

  readyDocCount(): number {
    return this.documents().filter(d => d.status === 'READY').length;
  }

  processingDocCount(): number {
    return this.documents().filter(d => d.status === 'PROCESSING').length;
  }

  totalDocCount(): number {
    return this.documents().length;
  }

  goToInvestigation(): void {
    this.router.navigate(['/chat']);
  }

  resumeSession(sessionId: string): void {
    this.router.navigate(['/chat'], { queryParams: { sessionId } });
  }
}
