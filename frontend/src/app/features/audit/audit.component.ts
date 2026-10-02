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
    <div class="audit-page">
      <header class="page-header">
        <div>
          <h2>Compliance & Security Audit Log</h2>
          <p class="subtitle">Immutable audit trail of all compliance queries, prompt injection defenses, and document lifecycle events.</p>
        </div>
        <button class="btn-refresh" (click)="loadAuditLogs()" [disabled]="isLoading()">
          &#x21bb; Refresh Logs
        </button>
      </header>

      <!-- Filter Controls -->
      <section class="toolbar">
        <div class="filters">
          <input 
            type="text" 
            placeholder="Filter by user, resource, or request ID..." 
            [(ngModel)]="searchQuery"
            class="search-input"
          />

          <select [(ngModel)]="actionFilter" class="filter-select">
            <option value="">All Security & Compliance Actions</option>
            <option value="PROMPT_INJECTION_BLOCKED">Prompt Injection Blocked</option>
            <option value="MALICIOUS_DOCUMENT_INJECTION_FLAGGED">Malicious Document Flagged</option>
            <option value="CROSS_USER_ACCESS_DENIED">Cross-User Access Denied</option>
            <option value="SECRET_EXTRACTION_ATTEMPT">Secret Extraction Attempt</option>
            <option value="RAG_QUERY_EXECUTED">RAG Query Executed</option>
            <option value="DOCUMENT_UPLOADED">Document Uploaded</option>
            <option value="DOCUMENT_DELETED">Document Deleted</option>
            <option value="LOGIN">User Login</option>
          </select>
        </div>

        <span class="count-badge">{{ filteredLogs().length }} Audit Events</span>
      </section>

      <!-- Audit Table -->
      @if (isLoading()) {
        <div class="loading-state">
          <div class="spinner"></div>
          <span>Loading immutable security audit records...</span>
        </div>
      } @else if (errorMessage()) {
        <div class="alert-error">
          {{ errorMessage() }}
        </div>
      } @else if (filteredLogs().length === 0) {
        <div class="empty-state">
          <div class="empty-icon">🛡️</div>
          <h3>No audit logs found</h3>
          <p>No compliance audit events match your selected filters.</p>
        </div>
      } @else {
        <div class="table-container">
          <table class="audit-table">
            <thead>
              <tr>
                <th>Timestamp</th>
                <th>User Principal</th>
                <th>Security / Action</th>
                <th>Target Resource</th>
                <th>Request ID</th>
                <th>Metadata</th>
              </tr>
            </thead>
            <tbody>
              @for (log of filteredLogs(); track log.id) {
                <tr [class.security-alert-row]="isSecurityAlert(log.action)">
                  <td class="timestamp-cell">
                    {{ log.createdAt | date:'yyyy-MM-dd HH:mm:ss' }}
                  </td>
                  <td>
                    <span class="user-pill">{{ log.username || 'SYSTEM' }}</span>
                  </td>
                  <td>
                    <span class="action-badge" [class]="getActionBadgeClass(log.action)">
                      {{ log.action }}
                    </span>
                  </td>
                  <td>
                    <div class="resource-cell">
                      <span class="res-type">{{ log.resourceType }}</span>
                      @if (log.resourceId) {
                        <code class="res-id" [title]="log.resourceId">{{ log.resourceId }}</code>
                      }
                    </div>
                  </td>
                  <td>
                    <code class="request-id">{{ log.requestId || '-' }}</code>
                  </td>
                  <td>
                    @if (log.metadata) {
                      <button type="button" class="btn-view-meta" (click)="openMetadata(log)">
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

      <!-- Metadata Modal -->
      @if (selectedLog()) {
        <div class="modal-backdrop" (click)="closeMetadata()">
          <div class="modal-dialog" (click)="$event.stopPropagation()">
            <div class="modal-header">
              <h3>Security Event Metadata</h3>
              <button class="btn-close" (click)="closeMetadata()">&times;</button>
            </div>
            <div class="modal-body">
              <div class="meta-row">
                <span class="meta-key">Event ID:</span>
                <code>{{ selectedLog()?.id }}</code>
              </div>
              <div class="meta-row">
                <span class="meta-key">Action:</span>
                <span class="action-badge" [class]="getActionBadgeClass(selectedLog()?.action || '')">
                  {{ selectedLog()?.action }}
                </span>
              </div>
              <div class="meta-row">
                <span class="meta-key">Recorded At:</span>
                <span>{{ selectedLog()?.createdAt | date:'full' }}</span>
              </div>

              <div class="meta-box">
                <span class="meta-key">Parsed Security Details:</span>
                <pre class="json-box">{{ formatMetadata(selectedLog()?.metadata) }}</pre>
              </div>
            </div>
            <div class="modal-footer">
              <button type="button" class="btn-secondary" (click)="closeMetadata()">Close</button>
            </div>
          </div>
        </div>
      }
    </div>
  `,
  styles: [`
    .audit-page {
      max-width: 1200px;
      margin: 0 auto;
      padding: 2rem 1.5rem;
    }
    .page-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 1.5rem;
    }
    .page-header h2 {
      margin: 0 0 0.25rem 0;
      font-size: 1.6rem;
      color: #0f172a;
    }
    .subtitle {
      color: #64748b;
      margin: 0;
      font-size: 0.9rem;
    }
    .btn-refresh {
      background: #f1f5f9;
      border: 1px solid #cbd5e1;
      padding: 0.5rem 0.85rem;
      border-radius: 6px;
      font-size: 0.85rem;
      font-weight: 500;
      cursor: pointer;
      color: #334155;
    }
    .btn-refresh:hover:not(:disabled) {
      background: #e2e8f0;
    }

    /* Toolbar */
    .toolbar {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 1.25rem;
      gap: 1rem;
      flex-wrap: wrap;
    }
    .filters {
      display: flex;
      gap: 0.75rem;
      flex: 1;
      max-width: 750px;
    }
    .search-input {
      flex: 2;
      padding: 0.55rem 0.75rem;
      border: 1px solid #cbd5e1;
      border-radius: 6px;
      font-size: 0.85rem;
      outline: none;
    }
    .filter-select {
      flex: 2;
      padding: 0.55rem 0.75rem;
      border: 1px solid #cbd5e1;
      border-radius: 6px;
      font-size: 0.85rem;
      background: #ffffff;
      outline: none;
    }
    .count-badge {
      font-size: 0.8rem;
      background: #f1f5f9;
      color: #475569;
      padding: 0.35rem 0.65rem;
      border-radius: 4px;
      font-weight: 500;
    }

    /* Table */
    .table-container {
      background: #ffffff;
      border: 1px solid #e2e8f0;
      border-radius: 8px;
      overflow-x: auto;
      box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
    }
    .audit-table {
      width: 100%;
      border-collapse: collapse;
      text-align: left;
      font-size: 0.85rem;
    }
    .audit-table th {
      background: #f8fafc;
      padding: 0.75rem 1rem;
      font-weight: 600;
      color: #475569;
      border-bottom: 1px solid #e2e8f0;
      font-size: 0.75rem;
      text-transform: uppercase;
      letter-spacing: 0.04em;
    }
    .audit-table td {
      padding: 0.75rem 1rem;
      border-bottom: 1px solid #f1f5f9;
      color: #334155;
      vertical-align: middle;
    }
    .security-alert-row {
      background: #fff5f5;
    }
    .timestamp-cell {
      white-space: nowrap;
      color: #64748b;
      font-family: monospace;
      font-size: 0.8rem;
    }
    .user-pill {
      background: #e2e8f0;
      color: #1e293b;
      padding: 0.2rem 0.5rem;
      border-radius: 4px;
      font-weight: 600;
      font-size: 0.75rem;
    }
    .action-badge {
      display: inline-block;
      padding: 0.25rem 0.5rem;
      border-radius: 4px;
      font-weight: 700;
      font-size: 0.725rem;
      letter-spacing: 0.02em;
    }
    .badge-threat {
      background: #fee2e2;
      color: #991b1b;
      border: 1px solid #fecaca;
    }
    .badge-warn {
      background: #fef3c7;
      color: #92400e;
      border: 1px solid #fde68a;
    }
    .badge-info {
      background: #eff6ff;
      color: #1e40af;
      border: 1px solid #bfdbfe;
    }
    .resource-cell {
      display: flex;
      flex-direction: column;
    }
    .res-type {
      font-weight: 600;
      color: #0f172a;
    }
    .res-id {
      font-size: 0.725rem;
      color: #64748b;
      max-width: 200px;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
    .request-id {
      font-size: 0.75rem;
      color: #64748b;
    }
    .btn-view-meta {
      background: #f8fafc;
      border: 1px solid #cbd5e1;
      padding: 0.2rem 0.5rem;
      border-radius: 4px;
      font-size: 0.75rem;
      cursor: pointer;
      color: #2563eb;
      font-weight: 500;
    }
    .btn-view-meta:hover {
      background: #eff6ff;
    }
    .text-muted {
      color: #94a3b8;
      font-size: 0.75rem;
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
      padding: 1.5rem;
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
    .meta-row {
      display: flex;
      justify-content: space-between;
      font-size: 0.85rem;
    }
    .meta-key {
      font-weight: 600;
      color: #475569;
    }
    .meta-box {
      margin-top: 0.5rem;
    }
    .json-box {
      background: #0f172a;
      color: #38bdf8;
      padding: 1rem;
      border-radius: 6px;
      font-size: 0.8rem;
      overflow-x: auto;
      white-space: pre-wrap;
      word-break: break-all;
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
      cursor: pointer;
      color: #334155;
    }

    .loading-state, .empty-state {
      text-align: center;
      padding: 3rem;
      color: #64748b;
      background: #ffffff;
      border: 1px dashed #cbd5e1;
      border-radius: 8px;
    }
    .empty-icon {
      font-size: 2.5rem;
      margin-bottom: 0.5rem;
    }
    .spinner {
      width: 20px;
      height: 20px;
      border: 2px solid #cbd5e1;
      border-top-color: #1e3a8a;
      border-radius: 50%;
      animation: spin 0.8s linear infinite;
      margin: 0 auto 0.5rem auto;
    }
    @keyframes spin {
      to { transform: rotate(360deg); }
    }
    .alert-error {
      background: #fee2e2;
      color: #991b1b;
      padding: 0.75rem 1rem;
      border-radius: 6px;
      font-size: 0.875rem;
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
        this.errorMessage.set(err?.error?.detail || 'Failed to load audit logs.');
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
      return 'badge-threat';
    }
    if (action.includes('DELETED') || action.includes('FAILED')) {
      return 'badge-warn';
    }
    return 'badge-info';
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
