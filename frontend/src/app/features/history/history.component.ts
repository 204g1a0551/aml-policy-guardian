import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { ChatService } from '../../core/services/chat.service';
import { ChatSession } from '../../core/models/chat.models';

@Component({
  selector: 'app-history',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="history-container">
      <header class="page-header">
        <div>
          <h2>AML Investigation Case History</h2>
          <p class="subtitle">Complete chronological record of all compliance inquiries, scenario analyses, and policy cross-references.</p>
        </div>
        <button type="button" class="btn-primary" (click)="startNewInvestigation()">
          + New Case Inquiry
        </button>
      </header>

      <section class="toolbar-section">
        <div class="search-box">
          <input 
            type="text" 
            placeholder="Search investigation cases by title..." 
            [(ngModel)]="searchTerm"
            class="search-input"
          />
        </div>
        <span class="count-badge">{{ filteredSessions().length }} Recorded Cases</span>
      </section>

      @if (isLoading()) {
        <div class="state-box">
          <div class="spinner"></div>
          <span>Loading historical compliance cases...</span>
        </div>
      } @else if (errorMessage()) {
        <div class="alert-banner alert-danger">
          <span>{{ errorMessage() }}</span>
        </div>
      } @else if (filteredSessions().length === 0) {
        <div class="state-box empty-state">
          <h3>No investigation records found</h3>
          <p>No historical policy inquiries match your search criteria.</p>
          <button type="button" class="btn-secondary" (click)="startNewInvestigation()">
            Start First Investigation
          </button>
        </div>
      } @else {
        <div class="table-card">
          <table class="enterprise-table">
            <thead>
              <tr>
                <th>Investigation Title / Scenario</th>
                <th>Inquiry Count</th>
                <th>Case Initiated</th>
                <th>Last Updated</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              @for (session of filteredSessions(); track session.id) {
                <tr>
                  <td class="scenario-cell" (click)="openSession(session.id)">
                    <span class="scenario-title">{{ session.title }}</span>
                    <span class="scenario-id font-mono">ID: {{ session.id }}</span>
                  </td>
                  <td>
                    <span class="badge-count">{{ session.messageCount }} Inquiries</span>
                  </td>
                  <td class="date-cell">{{ session.createdAt | date:'medium' }}</td>
                  <td class="date-cell">{{ session.updatedAt | date:'medium' }}</td>
                  <td>
                    <div class="action-buttons">
                      <button 
                        type="button" 
                        class="btn-row-action"
                        (click)="openSession(session.id)"
                      >
                        Resume &rarr;
                      </button>
                      <button 
                        type="button" 
                        class="btn-row-delete"
                        title="Delete investigation record"
                        (click)="deleteSession(session.id, $event)"
                      >
                        Delete
                      </button>
                    </div>
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      }
    </div>
  `,
  styles: [`
    .history-container {
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
    }

    .search-box {
      flex: 1;
      max-width: 480px;
    }

    .search-input {
      width: 100%;
      padding: 0.45rem 0.65rem;
      font-size: 0.825rem;
      border: 1px solid #cbd5e1;
      border-radius: 4px;
      box-sizing: border-box;
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

    .scenario-cell {
      cursor: pointer;
      display: flex;
      flex-direction: column;
      gap: 0.15rem;
    }

    .scenario-title {
      font-weight: 600;
      color: #0f172a;
    }

    .scenario-cell:hover .scenario-title {
      color: #1d4ed8;
      text-decoration: underline;
    }

    .scenario-id {
      font-size: 0.7rem;
      color: #94a3b8;
    }

    .badge-count {
      background-color: #f1f5f9;
      color: #475569;
      padding: 0.15rem 0.45rem;
      border-radius: 3px;
      font-size: 0.725rem;
      font-weight: 500;
    }

    .date-cell {
      white-space: nowrap;
      font-size: 0.775rem;
      color: #64748b;
    }

    .action-buttons {
      display: flex;
      gap: 0.45rem;
      align-items: center;
    }

    .btn-row-action {
      background: none;
      border: none;
      color: #1d4ed8;
      font-size: 0.775rem;
      font-weight: 600;
      cursor: pointer;
      padding: 0;
    }

    .btn-row-action:hover {
      text-decoration: underline;
    }

    .btn-row-delete {
      background: none;
      border: none;
      color: #dc2626;
      font-size: 0.75rem;
      cursor: pointer;
      padding: 0 0.25rem;
    }

    .btn-row-delete:hover {
      text-decoration: underline;
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

    .empty-state p {
      font-size: 0.85rem;
      margin-bottom: 1rem;
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
export class HistoryComponent implements OnInit {
  sessions = signal<ChatSession[]>([]);
  isLoading = signal(true);
  errorMessage = signal<string | null>(null);
  searchTerm = '';

  constructor(private chatService: ChatService, private router: Router) {}

  ngOnInit(): void {
    this.loadHistory();
  }

  loadHistory(): void {
    this.isLoading.set(true);
    this.errorMessage.set(null);

    this.chatService.getSessions().subscribe({
      next: (data) => {
        this.sessions.set(data);
        this.isLoading.set(false);
      },
      error: (err) => {
        this.isLoading.set(false);
        this.errorMessage.set(err?.error?.detail || 'Failed to load investigation history.');
      }
    });
  }

  filteredSessions(): ChatSession[] {
    if (!this.searchTerm.trim()) {
      return this.sessions();
    }
    const q = this.searchTerm.toLowerCase();
    return this.sessions().filter(s => s.title.toLowerCase().includes(q));
  }

  openSession(sessionId: string): void {
    this.router.navigate(['/chat'], { queryParams: { sessionId } });
  }

  startNewInvestigation(): void {
    this.router.navigate(['/chat']);
  }

  deleteSession(sessionId: string, event: Event): void {
    event.stopPropagation();
    if (!confirm('Are you sure you want to delete this investigation case?')) return;

    this.chatService.deleteSession(sessionId).subscribe({
      next: () => {
        this.sessions.update(list => list.filter(s => s.id !== sessionId));
      },
      error: (err) => {
        alert(err?.error?.detail || 'Failed to delete case.');
      }
    });
  }
}
