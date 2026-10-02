import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AuditService } from '../../core/services/audit.service';
import { AuditLogItem } from '../../core/models/audit.models';

@Component({
  selector: 'app-audit',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="audit-container">
      <header class="page-header">
        <div>
          <h2>Compliance & Security Audit Trail</h2>
          <p class="subtitle">Cryptographically signed, immutable record of all compliance queries, prompt injection defenses, and document events.</p>
        </div>
        <button type="button" class="btn-secondary" (click)="loadAuditLogs()" [disabled]="isLoading()">
          <span>Refresh Audit Records</span>
        </button>
      </header>

      <!-- Filter Controls Toolbar -->
      <section class="toolbar-section">
        <div class="filter-controls">
          <input 
            type="text" 
            placeholder="Filter by user, resource, or request ID..." 
            [(ngModel)]="searchQuery"
            class="search-input"
          />

          <select [(ngModel)]="actionFilter" class="filter-select">
            <option value="">All Security & Audit Actions</option>
            <option value="PROMPT_INJECTION_BLOCKED">Prompt Injection Blocked</option>
            <option value="MALICIOUS_DOCUMENT_INJECTION_FLAGGED">Malicious Document Flagged</option>
            <option value="CROSS_USER_ACCESS_DENIED">Cross-User Access Denied</option>
            <option value="SECRET_EXTRACTION_ATTEMPT">Secret Extraction Attempt</option>
            <option value="RAG_QUERY_EXECUTED">RAG Query Executed</option>
            <option value="DOCUMENT_UPLOADED">Document Uploaded</option>
            <option value="DOCUMENT_DELETED">Document Deleted</option>
            <option value="LOGIN">User Authentication</option>
          </select>
        </div>

        <span class="count-badge">{{ filteredLogs().length }} Audit Records</span>
      </section>

      <!-- Audit Log Table -->
      @if (isLoading()) {
        <div class="state-box">
          <div class="spinner"></div>
          <span>Retrieving immutable audit trail...</span>
        </div>
      } @else if (errorMessage()) {
        <div class="alert-banner alert-danger">
          <span>{{ errorMessage() }}</span>
        </div>
      } @else if (filteredLogs().length === 0) {
        <div class="state-box empty-state">
          <h3>No audit records match your filters</h3>
          <p>Security alerts and compliance events appear here in chronological order.</p>
        </div>
      } @else {
        <div class="table-card">
          <table class="enterprise-table">
            <thead>
              <tr>
                <th>Timestamp (UTC)</th>
                <th>User Principal</th>
                <th>Security Action</th>
                <th>Target Resource</th>
                <th>Correlation Request ID</th>
                <th>Payload</th>
              </tr>
            </thead>
            <tbody>
              @for (log of filteredLogs(); track log.id) {
                <tr [class.security-alert-row]="isSecurityAlert(log.action)">
                  <td class="timestamp-cell font-mono">
                    {{ log.createdAt | date:'yyyy-MM-dd HH:mm:ss' }}
                  </td>
                  <td>
                    <span class="user-pill font-mono">{{ log.username || 'SYSTEM' }}</span>
                  </td>
                  <td>
                    <span class="badge-status" [class]="getActionBadgeClass(log.action)">
                      {{ log.action }}
                    </span>
                  </td>
                  <td>
                    <div class="resource-cell">
                      <span class="res-type">{{ log.resourceType }}</span>
                      @if (log.resourceId) {
                        <code class="res-id font-mono" [title]="log.resourceId">{{ log.resourceId }}</code>
                      }
                    </div>
                  </td>
                  <td>
                    <code class="request-id font-mono">{{ log.requestId || '-' }}</code>
                  </td>
                  <td>
                    @if (log.metadata) {
                      <button type="button" class="btn-inspect-meta" (click)="openMetadata(log)">
                        View Payload
                      </button>
                    } @else {
                      <span class="text-muted">None</span>
                    }
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      }

      <!-- Metadata Inspection Modal -->
      @if (selectedLog()) {
        <div class="enterprise-modal-backdrop" (click)="closeMetadata()">
          <div class="enterprise-modal-dialog" (click)="$event.stopPropagation()">
            <div class="enterprise-modal-header">
              <h3>Security Event Forensic Details</h3>
              <button type="button" class="enterprise-modal-close" (click)="closeMetadata()">&times;</button>
            </div>
            <div class="enterprise-modal-body">
              <div class="modal-attr-grid">
                <div class="modal-attr">
                  <span class="attr-k">Event ID:</span>
                  <code class="attr-v font-mono">{{ selectedLog()?.id }}</code>
                </div>
                <div class="modal-attr">
                  <span class="attr-k">Action:</span>
                  <span class="badge-status" [class]="getActionBadgeClass(selectedLog()?.action || '')">
                    {{ selectedLog()?.action }}
                  </span>
                </div>
                <div class="modal-attr">
                  <span class="attr-k">Timestamp:</span>
                  <span class="attr-v font-mono">{{ selectedLog()?.createdAt | date:'full' }}</span>
                </div>
              </div>

              <div class="json-box-wrapper">
                <div class="json-header">
                  <span>Structured Payload Metadata:</span>
                </div>
                <pre class="json-payload">{{ formatMetadata(selectedLog()?.metadata) }}</pre>
              </div>
            </div>
            <div class="enterprise-modal-footer">
              <button type="button" class="btn-secondary" (click)="closeMetadata()">Close</button>
            </div>
          </div>
        </div>
      }
    </div>
  `,
  styles: [`
    .audit-container {
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

    .toolbar-section {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 1.25rem;
      gap: 1rem;
      flex-wrap: wrap;
    }

    .filter-controls {
      display: flex;
      gap: 0.65rem;
      flex: 1;
      max-width: 780px;
    }

    .search-input {
      flex: 2;
      padding: 0.45rem 0.65rem;
      font-size: 0.825rem;
      border: 1px solid #cbd5e1;
      border-radius: 4px;
    }

    .filter-select {
      flex: 2;
      padding: 0.45rem 0.65rem;
      font-size: 0.825rem;
      border: 1px solid #cbd5e1;
      border-radius: 4px;
      background-color: #ffffff;
    }

    .count-badge {
      font-size: 0.775rem;
      background-color: #f1f5f9;
      color: #475569;
      padding: 0.3rem 0.6rem;
      border-radius: 3px;
      font-weight: 500;
    }

    .table-card {
      background-color: #ffffff;
      border: 1px solid #cbd5e1;
      border-radius: 4px;
      overflow-x: auto;
      box-shadow: 0 1px 2px rgba(0, 0, 0, 0.04);
    }

    .security-alert-row {
      background-color: #fef2f2 !important;
    }

    .timestamp-cell {
      white-space: nowrap;
      font-size: 0.775rem;
      color: #475569;
    }

    .user-pill {
      background-color: #f1f5f9;
      color: #1e293b;
      padding: 0.15rem 0.45rem;
      border-radius: 3px;
      font-size: 0.725rem;
      font-weight: 600;
    }

    .resource-cell {
      display: flex;
      flex-direction: column;
    }

    .res-type {
      font-weight: 600;
      color: #0f172a;
      font-size: 0.8rem;
    }

    .res-id {
      font-size: 0.7rem;
      color: #64748b;
      max-width: 180px;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    .request-id {
      font-size: 0.725rem;
      color: #64748b;
    }

    .btn-inspect-meta {
      background-color: #f8fafc;
      border: 1px solid #cbd5e1;
      color: #1d4ed8;
      padding: 0.2rem 0.5rem;
      border-radius: 3px;
      font-size: 0.75rem;
      font-weight: 600;
      cursor: pointer;
    }

    .btn-inspect-meta:hover {
      background-color: #eff6ff;
      border-color: #bfdbfe;
    }

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

    .json-box-wrapper {
      background-color: #0f172a;
      border-radius: 4px;
      overflow: hidden;
    }

    .json-header {
      padding: 0.5rem 0.75rem;
      background-color: #1e293b;
      font-size: 0.725rem;
      font-weight: 600;
      color: #94a3b8;
      text-transform: uppercase;
    }

    .json-payload {
      padding: 0.85rem;
      color: #38bdf8;
      font-size: 0.775rem;
      overflow-x: auto;
      white-space: pre-wrap;
      word-break: break-all;
      max-height: 300px;
    }

    .state-box {
      text-align: center;
      padding: 3rem;
      background-color: #ffffff;
      border: 1px solid #cbd5e1;
      border-radius: 4px;
      color: #64748b;
    }

    .empty-state h3 {
      font-size: 1.05rem;
      color: #0f172a;
      margin-bottom: 0.35rem;
    }

    .spinner {
      width: 18px;
      height: 18px;
      border: 2px solid #cbd5e1;
      border-top-color: #1e3a8a;
      border-radius: 50%;
      animation: spin 0.8s linear infinite;
      margin: 0 auto 0.5rem auto;
    }

    @keyframes spin {
      to { transform: rotate(360deg); }
    }
  `]
})
export class AuditComponent implements OnInit {
  logs = signal<AuditLogItem[]>([]);
  isLoading = signal(true);
  errorMessage = signal<string | null>(null);
  selectedLog = signal<AuditLogItem | null>(null);

  searchQuery = '';
  actionFilter = '';

  constructor(private auditService: AuditService) {}

  ngOnInit(): void {
    this.loadAuditLogs();
  }

  loadAuditLogs(): void {
    this.isLoading.set(true);
    this.errorMessage.set(null);

    this.auditService.getAuditLogs().subscribe({
      next: (data) => {
        this.logs.set(data);
        this.isLoading.set(false);
      },
      error: (err) => {
        this.isLoading.set(false);
        this.errorMessage.set(err?.error?.detail || 'Failed to load audit records.');
      }
    });
  }

  filteredLogs(): AuditLogItem[] {
    let list = this.logs();

    if (this.searchQuery.trim()) {
      const q = this.searchQuery.toLowerCase();
      list = list.filter(l => 
        (l.username && l.username.toLowerCase().includes(q)) ||
        (l.resourceType && l.resourceType.toLowerCase().includes(q)) ||
        (l.resourceId && l.resourceId.toLowerCase().includes(q)) ||
        (l.requestId && l.requestId.toLowerCase().includes(q))
      );
    }

    if (this.actionFilter) {
      list = list.filter(l => l.action === this.actionFilter);
    }

    return list;
  }

  isSecurityAlert(action: string): boolean {
    return [
      'PROMPT_INJECTION_BLOCKED',
      'MALICIOUS_DOCUMENT_INJECTION_FLAGGED',
      'CROSS_USER_ACCESS_DENIED',
      'SECRET_EXTRACTION_ATTEMPT',
      'CANARY_TOKEN_LEAK_PREVENTED'
    ].includes(action);
  }

  getActionBadgeClass(action: string): string {
    if (this.isSecurityAlert(action)) {
      return 'status-danger';
    }
    if (action.includes('DELETED') || action.includes('FAILED')) {
      return 'status-warning';
    }
    return 'status-info';
  }

  openMetadata(log: AuditLogItem): void {
    this.selectedLog.set(log);
  }

  closeMetadata(): void {
    this.selectedLog.set(null);
  }

  formatMetadata(metaStr?: string): string {
    if (!metaStr) return 'No payload metadata recorded.';
    try {
      const parsed = JSON.parse(metaStr);
      return JSON.stringify(parsed, null, 2);
    } catch {
      return metaStr;
    }
  }
}
