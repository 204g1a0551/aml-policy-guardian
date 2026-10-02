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
    <div class="history-page">
      <header class="page-header">
        <div>
          <h2>Investigation History</h2>
          <p class="subtitle">Review, resume, or audit your previous AML policy investigations.</p>
        </div>
        <button class="btn-primary" (click)="startNewInvestigation()">
          + New Investigation
        </button>
      </header>

      <div class="toolbar">
        <div class="search-box">
          <input 
            type="text" 
            placeholder="Search investigations by title..." 
            [(ngModel)]="searchTerm"
          />
        </div>
        <span class="count-badge">{{ filteredSessions().length }} Investigations</span>
      </div>

      @if (isLoading()) {
        <div class="loading-state">
          <div class="spinner"></div>
          <span>Loading historical investigations...</span>
        </div>
      } @else if (errorMessage()) {
        <div class="alert-error">
          {{ errorMessage() }}
        </div>
      } @else if (filteredSessions().length === 0) {
        <div class="empty-state">
          <div class="empty-icon">📁</div>
          <h3>No investigations found</h3>
          <p>You haven't conducted any AML policy investigations yet or no results matched your search.</p>
          <button class="btn-primary" (click)="startNewInvestigation()">
            Start First Investigation
          </button>
        </div>
      } @else {
        <div class="sessions-grid">
          @for (session of filteredSessions(); track session.id) {
            <div class="session-card" (click)="openSession(session.id)">
              <div class="card-header">
                <h3 class="card-title">{{ session.title }}</h3>
                <span class="status-pill">{{ session.messageCount }} Messages</span>
              </div>
              
              <div class="card-meta">
                <div class="meta-item">
                  <span class="meta-label">Started:</span>
                  <span class="meta-value">{{ session.createdAt | date:'medium' }}</span>
                </div>
                <div class="meta-item">
                  <span class="meta-label">Last Updated:</span>
                  <span class="meta-value">{{ session.updatedAt | date:'medium' }}</span>
                </div>
              </div>

              <div class="card-actions">
                <button 
                  type="button" 
                  class="btn-resume"
                  (click)="openSession(session.id)"
                >
                  Resume Investigation &rarr;
                </button>
                <button 
                  type="button" 
                  class="btn-delete"
                  title="Delete Session"
                  (click)="deleteSession(session.id, $event)"
                >
                  Delete
                </button>
              </div>
            </div>
          }
        </div>
      }
    </div>
  `,
  styles: [`
    .history-page {
      max-width: 1100px;
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
    .toolbar {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 1.5rem;
      gap: 1rem;
    }
    .search-box {
      flex: 1;
      max-width: 450px;
    }
    .search-box input {
      width: 100%;
      padding: 0.65rem 0.85rem;
      border: 1px solid #cbd5e1;
      border-radius: 6px;
      font-size: 0.9rem;
      outline: none;
      box-sizing: border-box;
    }
    .search-box input:focus {
      border-color: #2563eb;
      box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.1);
    }
    .count-badge {
      font-size: 0.8rem;
      background: #f1f5f9;
      color: #475569;
      padding: 0.35rem 0.65rem;
      border-radius: 4px;
      font-weight: 500;
    }
    .btn-primary {
      background: #1e3a8a;
      color: #ffffff;
      border: none;
      padding: 0.65rem 1.15rem;
      border-radius: 6px;
      font-weight: 600;
      font-size: 0.875rem;
      cursor: pointer;
      transition: background 0.15s;
    }
    .btn-primary:hover {
      background: #172554;
    }
    .sessions-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
      gap: 1.25rem;
    }
    .session-card {
      background: #ffffff;
      border: 1px solid #e2e8f0;
      border-radius: 8px;
      padding: 1.25rem;
      cursor: pointer;
      display: flex;
      flex-direction: column;
      justify-content: space-between;
      box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
      transition: all 0.15s;
    }
    .session-card:hover {
      border-color: #3b82f6;
      box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.08);
      transform: translateY(-1px);
    }
    .card-header {
      display: flex;
      justify-content: space-between;
      align-items: flex-start;
      margin-bottom: 0.75rem;
      gap: 0.5rem;
    }
    .card-title {
      font-size: 1rem;
      color: #0f172a;
      margin: 0;
      line-height: 1.35;
      font-weight: 600;
    }
    .status-pill {
      background: #eff6ff;
      color: #1d4ed8;
      font-size: 0.75rem;
      font-weight: 600;
      padding: 0.2rem 0.5rem;
      border-radius: 4px;
      white-space: nowrap;
    }
    .card-meta {
      font-size: 0.8rem;
      color: #64748b;
      margin-bottom: 1.25rem;
      display: flex;
      flex-direction: column;
      gap: 0.35rem;
    }
    .meta-item {
      display: flex;
      justify-content: space-between;
    }
    .meta-label {
      font-weight: 500;
      color: #94a3b8;
    }
    .meta-value {
      color: #334155;
    }
    .card-actions {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding-top: 0.75rem;
      border-top: 1px solid #f1f5f9;
    }
    .btn-resume {
      background: transparent;
      border: none;
      color: #2563eb;
      font-weight: 600;
      font-size: 0.85rem;
      cursor: pointer;
      padding: 0;
    }
    .btn-resume:hover {
      text-decoration: underline;
    }
    .btn-delete {
      background: transparent;
      border: none;
      color: #ef4444;
      font-size: 0.8rem;
      cursor: pointer;
    }
    .btn-delete:hover {
      text-decoration: underline;
    }
    .empty-state {
      text-align: center;
      padding: 3rem 1.5rem;
      background: #ffffff;
      border: 1px dashed #cbd5e1;
      border-radius: 10px;
    }
    .empty-icon {
      font-size: 2.5rem;
      margin-bottom: 0.5rem;
    }
    .empty-state h3 {
      font-size: 1.25rem;
      color: #0f172a;
      margin-bottom: 0.5rem;
    }
    .empty-state p {
      color: #64748b;
      font-size: 0.9rem;
      margin-bottom: 1.5rem;
    }
    .loading-state {
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 0.75rem;
      padding: 3rem;
      color: #64748b;
      font-size: 0.9rem;
    }
    .spinner {
      width: 20px;
      height: 20px;
      border: 2px solid #cbd5e1;
      border-top-color: #1e3a8a;
      border-radius: 50%;
      animation: spin 0.8s linear infinite;
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
    if (!confirm('Are you sure you want to delete this investigation?')) return;

    this.chatService.deleteSession(sessionId).subscribe({
      next: () => {
        this.sessions.update(list => list.filter(s => s.id !== sessionId));
      },
      error: (err) => {
        alert(err?.error?.detail || 'Failed to delete session.');
      }
    });
  }
}
