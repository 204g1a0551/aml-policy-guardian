import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { DocumentService } from '../../core/services/document.service';
import { AuthService } from '../../core/services/auth.service';
import { DocumentItem, DocumentType } from '../../core/models/document.models';

@Component({
  selector: 'app-documents',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="documents-page">
      <header class="page-header">
        <div>
          <h2>AML Compliance Document Repository</h2>
          <p class="subtitle">Authoritative banking policies, SAR operating procedures, and customer due diligence guidelines.</p>
        </div>
        <button class="btn-refresh" (click)="loadDocuments()" [disabled]="isLoading()">
          &#x21bb; Refresh Repository
        </button>
      </header>

      <!-- Admin Upload Section -->
      @if (authService.isAdmin()) {
        <section class="upload-section">
          <div class="upload-card">
            <h3>Upload Compliance Policy Document</h3>
            <p class="upload-hint">Supported formats: PDF, DOCX, TXT (Maximum 25 MB). Files undergo automated text extraction, chunking, and pgvector embedding generation.</p>

            <form (ngSubmit)="onUpload()" class="upload-form">
              <div class="form-row">
                <div class="form-group flex-2">
                  <label for="title">Document Title *</label>
                  <input 
                    id="title" 
                    type="text" 
                    [(ngModel)]="uploadTitle" 
                    name="title" 
                    placeholder="e.g. AML Transaction Monitoring SOP"
                    required
                  />
                </div>

                <div class="form-group flex-1">
                  <label for="type">Document Type *</label>
                  <select id="type" [(ngModel)]="uploadType" name="type" required>
                    <option value="POLICY">Policy</option>
                    <option value="PROCEDURE">Procedure / SOP</option>
                    <option value="GUIDELINE">Guideline</option>
                    <option value="REGULATORY">Regulatory Requirement</option>
                    <option value="TRAINING">Training Manual</option>
                  </select>
                </div>
              </div>

              <div class="form-row">
                <div class="form-group flex-1">
                  <label for="version">Version *</label>
                  <input 
                    id="version" 
                    type="text" 
                    [(ngModel)]="uploadVersion" 
                    name="version" 
                    placeholder="e.g. v2026.1"
                    required
                  />
                </div>

                <div class="form-group flex-2">
                  <label for="source">Issuing Authority / Source</label>
                  <input 
                    id="source" 
                    type="text" 
                    [(ngModel)]="uploadSource" 
                    name="source" 
                    placeholder="e.g. Global Financial Crimes & Sanctions Committee"
                  />
                </div>
              </div>

              <div class="form-group">
                <label for="file">Document File (PDF, DOCX, TXT) *</label>
                <input 
                  id="file" 
                  type="file" 
                  (change)="onFileSelected($event)" 
                  accept=".pdf,.docx,.txt"
                  class="file-input"
                  required
                />
                @if (selectedFile) {
                  <span class="file-name-pill">Selected: {{ selectedFile.name }} ({{ (selectedFile.size / 1024) | number:'1.0-1' }} KB)</span>
                }
              </div>

              @if (uploadError()) {
                <div class="alert-error">{{ uploadError() }}</div>
              }
              @if (uploadSuccess()) {
                <div class="alert-success">{{ uploadSuccess() }}</div>
              }

              <button type="submit" class="btn-primary" [disabled]="isUploading() || !selectedFile || !uploadTitle">
                @if (isUploading()) {
                  <span>Processing & Ingesting Chunks...</span>
                } @else {
                  <span>Ingest & Index Document</span>
                }
              </button>
            </form>
          </div>
        </section>
      }

      <!-- Repository Filters & List -->
      <section class="repository-section">
        <div class="repo-toolbar">
          <div class="filter-group">
            <input 
              type="text" 
              placeholder="Search documents by title or filename..." 
              [(ngModel)]="searchQuery" 
              class="search-input"
            />
            
            <select [(ngModel)]="selectedTypeFilter" class="filter-select">
              <option value="">All Document Types</option>
              <option value="POLICY">Policies</option>
              <option value="PROCEDURE">Procedures / SOPs</option>
              <option value="GUIDELINE">Guidelines</option>
              <option value="REGULATORY">Regulatory</option>
            </select>

            <select [(ngModel)]="selectedStatusFilter" class="filter-select">
              <option value="">All Statuses</option>
              <option value="READY">READY</option>
              <option value="PROCESSING">PROCESSING</option>
              <option value="FAILED">FAILED</option>
            </select>
          </div>

          <span class="count-badge">{{ filteredDocuments().length }} Documents</span>
        </div>

        @if (isLoading()) {
          <div class="loading-state">
            <div class="spinner"></div>
            <span>Fetching repository catalog...</span>
          </div>
        } @else if (filteredDocuments().length === 0) {
          <div class="empty-state">
            <div class="empty-icon">📄</div>
            <h3>No documents in catalog</h3>
            <p>No compliance policy documents match your criteria or none have been ingested yet.</p>
          </div>
        } @else {
          <div class="table-container">
            <table class="documents-table">
              <thead>
                <tr>
                  <th>Title & Classification</th>
                  <th>Type</th>
                  <th>Version</th>
                  <th>File Size</th>
                  <th>Ingestion Status</th>
                  <th>Uploaded</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                @for (doc of filteredDocuments(); track doc.id) {
                  <tr>
                    <td>
                      <div class="doc-title-cell">
                        <span class="doc-title">{{ doc.title }}</span>
                        <code class="doc-filename">{{ doc.filename }}</code>
                      </div>
                    </td>
                    <td>
                      <span class="badge-type">{{ doc.documentType }}</span>
                    </td>
                    <td>{{ doc.version || 'v1.0' }}</td>
                    <td>{{ (doc.fileSizeBytes / 1024) | number:'1.0-1' }} KB</td>
                    <td>
                      <span class="status-badge" [class]="'status-' + doc.status.toLowerCase()">
                        {{ doc.status }}
                      </span>
                    </td>
                    <td>
                      <span class="doc-date">{{ doc.createdAt | date:'short' }}</span>
                    </td>
                    <td>
                      <div class="action-buttons">
                        <button type="button" class="btn-inspect" (click)="openMetadataModal(doc)">
                          Metadata
                        </button>
                        @if (authService.isAdmin()) {
                          <button type="button" class="btn-delete" (click)="deleteDocument(doc.id, doc.title)">
                            Delete
                          </button>
                        }
                      </div>
                    </td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        }
      </section>

      <!-- Metadata Inspector Modal -->
      @if (selectedDoc()) {
        <div class="modal-backdrop" (click)="closeMetadataModal()">
          <div class="modal-dialog" (click)="$event.stopPropagation()">
            <div class="modal-header">
              <h3>Document Metadata & Governance</h3>
              <button class="btn-close" (click)="closeMetadataModal()">&times;</button>
            </div>
            <div class="modal-body">
              <div class="meta-row">
                <span class="meta-key">Document ID:</span>
                <code>{{ selectedDoc()?.id }}</code>
              </div>
              <div class="meta-row">
                <span class="meta-key">Filename:</span>
                <code>{{ selectedDoc()?.filename }}</code>
              </div>
              <div class="meta-row">
                <span class="meta-key">MIME Content Type:</span>
                <span>{{ selectedDoc()?.mimeType }}</span>
              </div>
              <div class="meta-row">
                <span class="meta-key">SHA-256 Hash:</span>
                <code class="hash-text">{{ selectedDoc()?.sha256Checksum }}</code>
              </div>
              <div class="meta-row">
                <span class="meta-key">Issuing Source:</span>
                <span>{{ selectedDoc()?.source || 'Internal Bank Compliance Repository' }}</span>
              </div>
              <div class="meta-row">
                <span class="meta-key">Ingestion Status:</span>
                <span class="status-badge" [class]="'status-' + (selectedDoc()?.status || '').toLowerCase()">
                  {{ selectedDoc()?.status }}
                </span>
              </div>
              @if (selectedDoc()?.errorMessage) {
                <div class="error-box">
                  <span class="meta-key">Error Details:</span>
                  <p>{{ selectedDoc()?.errorMessage }}</p>
                </div>
              }
            </div>
            <div class="modal-footer">
              <button class="btn-secondary" (click)="closeMetadataModal()">Close</button>
            </div>
          </div>
        </div>
      }
    </div>
  `,
  styles: [`
    .documents-page {
      max-width: 1200px;
      margin: 0 auto;
      padding: 2rem 1.5rem;
    }
    .page-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 2rem;
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

    /* Upload Card */
    .upload-card {
      background: #ffffff;
      border: 1px solid #e2e8f0;
      border-radius: 10px;
      padding: 1.5rem;
      margin-bottom: 2rem;
      box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
    }
    .upload-card h3 {
      margin: 0 0 0.35rem 0;
      font-size: 1.2rem;
      color: #0f172a;
    }
    .upload-hint {
      color: #64748b;
      font-size: 0.825rem;
      margin-bottom: 1.25rem;
    }
    .upload-form {
      display: flex;
      flex-direction: column;
      gap: 1rem;
    }
    .form-row {
      display: flex;
      gap: 1rem;
    }
    .flex-1 { flex: 1; }
    .flex-2 { flex: 2; }
    .form-group {
      display: flex;
      flex-direction: column;
    }
    .form-group label {
      font-size: 0.825rem;
      font-weight: 600;
      color: #334155;
      margin-bottom: 0.35rem;
    }
    .form-group input, .form-group select {
      padding: 0.55rem 0.75rem;
      border: 1px solid #cbd5e1;
      border-radius: 6px;
      font-size: 0.9rem;
      outline: none;
    }
    .file-input {
      padding: 0.4rem !important;
    }
    .file-name-pill {
      font-size: 0.75rem;
      color: #1e40af;
      margin-top: 0.35rem;
    }
    .btn-primary {
      background: #1e3a8a;
      color: #ffffff;
      border: none;
      padding: 0.65rem 1.25rem;
      border-radius: 6px;
      font-weight: 600;
      cursor: pointer;
      font-size: 0.9rem;
      align-self: flex-start;
      transition: background 0.15s;
    }
    .btn-primary:hover:not(:disabled) {
      background: #172554;
    }
    .btn-primary:disabled {
      background: #94a3b8;
      cursor: not-allowed;
    }

    /* Repo Toolbar */
    .repo-toolbar {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 1rem;
      gap: 1rem;
      flex-wrap: wrap;
    }
    .filter-group {
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
    }
    .filter-select {
      flex: 1;
      padding: 0.55rem 0.75rem;
      border: 1px solid #cbd5e1;
      border-radius: 6px;
      font-size: 0.85rem;
      background: #ffffff;
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
    .documents-table {
      width: 100%;
      border-collapse: collapse;
      text-align: left;
      font-size: 0.875rem;
    }
    .documents-table th {
      background: #f8fafc;
      padding: 0.75rem 1rem;
      font-weight: 600;
      color: #475569;
      border-bottom: 1px solid #e2e8f0;
      font-size: 0.8rem;
      text-transform: uppercase;
      letter-spacing: 0.04em;
    }
    .documents-table td {
      padding: 0.85rem 1rem;
      border-bottom: 1px solid #f1f5f9;
      color: #334155;
      vertical-align: middle;
    }
    .doc-title-cell {
      display: flex;
      flex-direction: column;
    }
    .doc-title {
      font-weight: 600;
      color: #0f172a;
    }
    .doc-filename {
      font-size: 0.75rem;
      color: #64748b;
      margin-top: 0.15rem;
    }
    .badge-type {
      background: #f1f5f9;
      color: #334155;
      padding: 0.2rem 0.5rem;
      border-radius: 4px;
      font-size: 0.75rem;
      font-weight: 600;
    }
    .status-badge {
      display: inline-block;
      padding: 0.2rem 0.55rem;
      border-radius: 4px;
      font-size: 0.75rem;
      font-weight: 700;
      text-transform: uppercase;
    }
    .status-ready { background: #dcfce7; color: #166534; }
    .status-processing { background: #fef3c7; color: #92400e; }
    .status-uploaded { background: #e0f2fe; color: #0369a1; }
    .status-failed { background: #fee2e2; color: #991b1b; }
    .status-deleted { background: #f1f5f9; color: #64748b; }

    .action-buttons {
      display: flex;
      gap: 0.5rem;
    }
    .btn-inspect {
      background: #eff6ff;
      color: #1d4ed8;
      border: 1px solid #bfdbfe;
      padding: 0.25rem 0.5rem;
      border-radius: 4px;
      font-size: 0.75rem;
      font-weight: 600;
      cursor: pointer;
    }
    .btn-delete {
      background: #fef2f2;
      color: #dc2626;
      border: 1px solid #fecaca;
      padding: 0.25rem 0.5rem;
      border-radius: 4px;
      font-size: 0.75rem;
      font-weight: 600;
      cursor: pointer;
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
      display: flex;
      flex-direction: column;
      gap: 0.75rem;
    }
    .meta-row {
      display: flex;
      justify-content: space-between;
      font-size: 0.85rem;
      gap: 1rem;
    }
    .meta-key {
      font-weight: 600;
      color: #475569;
    }
    .hash-text {
      word-break: break-all;
      font-size: 0.75rem;
      color: #0f172a;
    }
    .error-box {
      margin-top: 0.5rem;
      padding: 0.75rem;
      background: #fef2f2;
      border-radius: 6px;
      color: #991b1b;
      font-size: 0.85rem;
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
    .alert-error {
      background: #fee2e2;
      color: #991b1b;
      padding: 0.65rem 0.85rem;
      border-radius: 6px;
      font-size: 0.85rem;
    }
    .alert-success {
      background: #dcfce7;
      color: #166534;
      padding: 0.65rem 0.85rem;
      border-radius: 6px;
      font-size: 0.85rem;
    }
    .loading-state, .empty-state {
      text-align: center;
      padding: 3rem;
      color: #64748b;
      background: #ffffff;
      border: 1px dashed #cbd5e1;
      border-radius: 8px;
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
  `]
})
export class DocumentsComponent implements OnInit {
  documents = signal<DocumentItem[]>([]);
  isLoading = signal(true);
  isUploading = signal(false);
  uploadError = signal<string | null>(null);
  uploadSuccess = signal<string | null>(null);
  selectedDoc = signal<DocumentItem | null>(null);

  // Upload Form Fields
  uploadTitle = '';
  uploadType: DocumentType = 'POLICY';
  uploadVersion = 'v1.0';
  uploadSource = '';
  selectedFile: File | null = null;

  // Filters
  searchQuery = '';
  selectedTypeFilter = '';
  selectedStatusFilter = '';

  constructor(
    public authService: AuthService,
    private documentService: DocumentService
  ) {}

  ngOnInit(): void {
    this.loadDocuments();
  }

  loadDocuments(): void {
    this.isLoading.set(true);
    this.documentService.getDocuments().subscribe({
      next: (docs) => {
        this.documents.set(docs);
        this.isLoading.set(false);
      },
      error: (err) => {
        this.isLoading.set(false);
        console.error('Failed to load documents', err);
      }
    });
  }

  filteredDocuments(): DocumentItem[] {
    let list = this.documents();

    if (this.searchQuery.trim()) {
      const q = this.searchQuery.toLowerCase();
      list = list.filter(d => 
        d.title.toLowerCase().includes(q) || 
        d.filename.toLowerCase().includes(q)
      );
    }

    if (this.selectedTypeFilter) {
      list = list.filter(d => d.documentType === this.selectedTypeFilter);
    }

    if (this.selectedStatusFilter) {
      list = list.filter(d => d.status === this.selectedStatusFilter);
    }

    return list;
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (input.files && input.files.length > 0) {
      this.selectedFile = input.files[0];
      if (!this.uploadTitle) {
        this.uploadTitle = this.selectedFile.name.replace(/\.[^/.]+$/, '');
      }
    }
  }

  onUpload(): void {
    if (!this.selectedFile || !this.uploadTitle) {
      this.uploadError.set('Please select a file and provide a document title.');
      return;
    }

    this.isUploading.set(true);
    this.uploadError.set(null);
    this.uploadSuccess.set(null);

    this.documentService.uploadDocument(
      this.selectedFile,
      this.uploadTitle,
      this.uploadType,
      this.uploadVersion,
      this.uploadSource
    ).subscribe({
      next: (created) => {
        this.isUploading.set(false);
        this.uploadSuccess.set(`Document "${created.title}" successfully ingested and indexed!`);
        this.selectedFile = null;
        this.uploadTitle = '';
        this.uploadSource = '';
        this.loadDocuments();
      },
      error: (err) => {
        this.isUploading.set(false);
        this.uploadError.set(err?.error?.detail || err?.error?.message || 'Document upload failed.');
      }
    });
  }

  deleteDocument(id: string, title: string): void {
    if (!confirm(`Are you sure you want to permanently delete "${title}"?`)) return;

    this.documentService.deleteDocument(id).subscribe({
      next: () => {
        this.documents.update(list => list.filter(d => d.id !== id));
      },
      error: (err) => {
        alert(err?.error?.detail || 'Failed to delete document.');
      }
    });
  }

  openMetadataModal(doc: DocumentItem): void {
    this.selectedDoc.set(doc);
  }

  closeMetadataModal(): void {
    this.selectedDoc.set(null);
  }
}
