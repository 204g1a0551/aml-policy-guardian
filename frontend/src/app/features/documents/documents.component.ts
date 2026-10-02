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
    <div class="documents-container">
      <header class="page-header">
        <div>
          <h2>AML Compliance Document Repository</h2>
          <p class="subtitle">Authoritative banking policies, SAR operating procedures, and customer due diligence guidelines.</p>
        </div>
        <div class="header-actions">
          <button type="button" class="btn-secondary" (click)="loadDocuments()" [disabled]="isLoading()">
            <span>Refresh Catalog</span>
          </button>
          @if (authService.isAdmin()) {
            <button type="button" class="btn-primary" (click)="toggleUploadModal(true)">
              <span>+ Ingest Policy Document</span>
            </button>
          }
        </div>
      </header>

      <!-- Repository Filter Toolbar -->
      <section class="toolbar-section">
        <div class="filter-controls">
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
            <option value="REGULATORY">Regulatory Requirements</option>
            <option value="TRAINING">Training Manuals</option>
          </select>

          <select [(ngModel)]="selectedStatusFilter" class="filter-select">
            <option value="">All Ingestion Statuses</option>
            <option value="READY">READY</option>
            <option value="PROCESSING">PROCESSING</option>
            <option value="FAILED">FAILED</option>
          </select>
        </div>

        <span class="count-badge">{{ filteredDocuments().length }} Registered Documents</span>
      </section>

      <!-- Ingestion Pipeline Stage Banner (When Uploading) -->
      @if (isUploading()) {
        <div class="pipeline-progress-banner">
          <div class="pipeline-header">
            <span class="pipeline-title">INGESTION PIPELINE IN PROGRESS: {{ uploadTitle }}</span>
            <span class="spinner-small"></span>
          </div>
          <div class="pipeline-stages">
            <div class="stage-step" [class.active]="uploadStage() >= 1" [class.complete]="uploadStage() > 1">
              <span class="step-num">1</span>
              <span class="step-label">File Transfer</span>
            </div>
            <div class="stage-divider"></div>
            <div class="stage-step" [class.active]="uploadStage() >= 2" [class.complete]="uploadStage() > 2">
              <span class="step-num">2</span>
              <span class="step-label">Apache Tika Extraction</span>
            </div>
            <div class="stage-divider"></div>
            <div class="stage-step" [class.active]="uploadStage() >= 3" [class.complete]="uploadStage() > 3">
              <span class="step-num">3</span>
              <span class="step-label">Semantic Chunking</span>
            </div>
            <div class="stage-divider"></div>
            <div class="stage-step" [class.active]="uploadStage() >= 4" [class.complete]="uploadStage() > 4">
              <span class="step-num">4</span>
              <span class="step-label">pgvector Embeddings</span>
            </div>
          </div>
        </div>
      }

      @if (uploadSuccess()) {
        <div class="alert-banner alert-success">
          <span>{{ uploadSuccess() }}</span>
          <button type="button" class="btn-dismiss" (click)="uploadSuccess.set(null)">&times;</button>
        </div>
      }

      @if (uploadError()) {
        <div class="alert-banner alert-danger">
          <span>{{ uploadError() }}</span>
          <button type="button" class="btn-dismiss" (click)="uploadError.set(null)">&times;</button>
        </div>
      }

      <!-- Enterprise Table View -->
      <section class="table-card">
        @if (isLoading()) {
          <div class="state-box">
            <div class="spinner"></div>
            <span>Fetching repository catalog from database...</span>
          </div>
        } @else if (filteredDocuments().length === 0) {
          <div class="state-box empty-state">
            <h3>No compliance documents match your criteria</h3>
            <p>Ensure documents are ingested and marked READY for vector similarity retrieval.</p>
          </div>
        } @else {
          <div class="table-responsive">
            <table class="enterprise-table">
              <thead>
                <tr>
                  <th>Title & Filename</th>
                  <th>Classification</th>
                  <th>Version</th>
                  <th>File Size</th>
                  <th>Ingestion Status</th>
                  <th>Ingestion Date</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                @for (doc of filteredDocuments(); track doc.id) {
                  <tr>
                    <td>
                      <div class="title-cell">
                        <span class="doc-title-text">{{ doc.title }}</span>
                        <code class="doc-filename-text">{{ doc.filename }}</code>
                      </div>
                    </td>
                    <td>
                      <span class="badge-type">{{ doc.documentType }}</span>
                    </td>
                    <td>{{ doc.version || 'v1.0' }}</td>
                    <td>{{ (doc.fileSizeBytes / 1024) | number:'1.0-1' }} KB</td>
                    <td>
                      <span class="badge-status" [class]="'status-' + doc.status.toLowerCase()">
                        {{ doc.status }}
                      </span>
                    </td>
                    <td class="date-cell">{{ doc.createdAt | date:'short' }}</td>
                    <td>
                      <div class="row-actions">
                        <button type="button" class="btn-row" (click)="openMetadataModal(doc)">
                          Metadata
                        </button>
                        @if (authService.isAdmin()) {
                          <button type="button" class="btn-row btn-row-danger" (click)="deleteDocument(doc.id, doc.title)">
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

      <!-- Upload Policy Document Modal -->
      @if (showUploadModal()) {
        <div class="enterprise-modal-backdrop" (click)="toggleUploadModal(false)">
          <div class="enterprise-modal-dialog" (click)="$event.stopPropagation()">
            <div class="enterprise-modal-header">
              <h3>Ingest Compliance Policy Document</h3>
              <button type="button" class="enterprise-modal-close" (click)="toggleUploadModal(false)">&times;</button>
            </div>
            <form (ngSubmit)="onUpload()">
              <div class="enterprise-modal-body">
                <p class="modal-instruction">
                  Supported formats: PDF, DOCX, TXT (Maximum 25 MB). Files undergo automated text extraction, chunking (500 chars / 50 overlap), and pgvector embedding generation.
                </p>

                <div class="form-grid">
                  <div class="form-col full">
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

                  <div class="form-col">
                    <label for="type">Document Classification *</label>
                    <select id="type" [(ngModel)]="uploadType" name="type" required>
                      <option value="POLICY">Policy</option>
                      <option value="PROCEDURE">Procedure / SOP</option>
                      <option value="GUIDELINE">Guideline</option>
                      <option value="REGULATORY">Regulatory Requirement</option>
                      <option value="TRAINING">Training Manual</option>
                    </select>
                  </div>

                  <div class="form-col">
                    <label for="version">Policy Version *</label>
                    <input 
                      id="version" 
                      type="text" 
                      [(ngModel)]="uploadVersion" 
                      name="version" 
                      placeholder="e.g. v2026.1"
                      required
                    />
                  </div>

                  <div class="form-col full">
                    <label for="source">Issuing Authority / Oversight Committee</label>
                    <input 
                      id="source" 
                      type="text" 
                      [(ngModel)]="uploadSource" 
                      name="source" 
                      placeholder="e.g. Global Financial Crimes & Sanctions Committee"
                    />
                  </div>

                  <div class="form-col full">
                    <label for="file">Document File *</label>
                    <input 
                      id="file" 
                      type="file" 
                      (change)="onFileSelected($event)" 
                      accept=".pdf,.docx,.txt"
                      class="file-control"
                      required
                    />
                    @if (selectedFile) {
                      <div class="file-summary">Selected: {{ selectedFile.name }} ({{ (selectedFile.size / 1024) | number:'1.0-1' }} KB)</div>
                    }
                  </div>
                </div>
              </div>

              <div class="enterprise-modal-footer">
                <button type="button" class="btn-secondary" (click)="toggleUploadModal(false)" [disabled]="isUploading()">Cancel</button>
                <button type="submit" class="btn-primary" [disabled]="isUploading() || !selectedFile || !uploadTitle">
                  @if (isUploading()) {
                    <span>Ingesting Document...</span>
                  } @else {
                    <span>Start Ingestion & Indexing</span>
                  }
                </button>
              </div>
            </form>
          </div>
        </div>
      }

      <!-- Metadata Inspector Modal -->
      @if (selectedDoc()) {
        <div class="enterprise-modal-backdrop" (click)="closeMetadataModal()">
          <div class="enterprise-modal-dialog" (click)="$event.stopPropagation()">
            <div class="enterprise-modal-header">
              <h3>Document Metadata & Governance Record</h3>
              <button type="button" class="enterprise-modal-close" (click)="closeMetadataModal()">&times;</button>
            </div>
            <div class="enterprise-modal-body">
              <div class="meta-attr-list">
                <div class="meta-attr-row">
                  <span class="meta-attr-k">Document ID:</span>
                  <code class="meta-attr-v font-mono">{{ selectedDoc()?.id }}</code>
                </div>
                <div class="meta-attr-row">
                  <span class="meta-attr-k">System Filename:</span>
                  <code class="meta-attr-v font-mono">{{ selectedDoc()?.filename }}</code>
                </div>
                <div class="meta-attr-row">
                  <span class="meta-attr-k">MIME Type:</span>
                  <span class="meta-attr-v">{{ selectedDoc()?.mimeType }}</span>
                </div>
                <div class="meta-attr-row">
                  <span class="meta-attr-k">SHA-256 Checksum:</span>
                  <code class="meta-attr-v font-mono text-break">{{ selectedDoc()?.sha256Checksum }}</code>
                </div>
                <div class="meta-attr-row">
                  <span class="meta-attr-k">Issuing Source:</span>
                  <span class="meta-attr-v">{{ selectedDoc()?.source || 'Internal Bank Compliance Repository' }}</span>
                </div>
                <div class="meta-attr-row">
                  <span class="meta-attr-k">Ingestion Status:</span>
                  <span class="badge-status" [class]="'status-' + (selectedDoc()?.status || '').toLowerCase()">
                    {{ selectedDoc()?.status }}
                  </span>
                </div>
                @if (selectedDoc()?.errorMessage) {
                  <div class="error-detail-box">
                    <span class="error-heading">Ingestion Failure Details:</span>
                    <p class="error-text">{{ selectedDoc()?.errorMessage }}</p>
                  </div>
                }
              </div>
            </div>
            <div class="enterprise-modal-footer">
              <button type="button" class="btn-secondary" (click)="closeMetadataModal()">Close</button>
            </div>
          </div>
        </div>
      }
    </div>
  `,
  styles: [`
    .documents-container {
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

    .header-actions {
      display: flex;
      gap: 0.5rem;
    }

    /* Toolbar */
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
      flex: 1;
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

    /* Pipeline Banner */
    .pipeline-progress-banner {
      background-color: #f0fdf4;
      border: 1px solid #bbf7d0;
      border-radius: 4px;
      padding: 1rem;
      margin-bottom: 1.25rem;
    }

    .pipeline-header {
      display: flex;
      align-items: center;
      gap: 0.65rem;
      margin-bottom: 0.75rem;
    }

    .pipeline-title {
      font-size: 0.775rem;
      font-weight: 700;
      color: #166534;
      letter-spacing: 0.04em;
    }

    .pipeline-stages {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      flex-wrap: wrap;
    }

    .stage-step {
      display: flex;
      align-items: center;
      gap: 0.4rem;
      opacity: 0.4;
      font-size: 0.75rem;
      font-weight: 600;
    }

    .stage-step.active {
      opacity: 1;
      color: #15803d;
    }

    .stage-step.complete {
      opacity: 0.8;
      color: #166534;
    }

    .step-num {
      width: 18px;
      height: 18px;
      border-radius: 50%;
      background-color: #e2e8f0;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 0.7rem;
    }

    .stage-step.active .step-num {
      background-color: #16a34a;
      color: #ffffff;
    }

    .stage-divider {
      width: 24px;
      height: 1px;
      background-color: #bbf7d0;
    }

    /* Table Card */
    .table-card {
      background-color: #ffffff;
      border: 1px solid #cbd5e1;
      border-radius: 4px;
      overflow: hidden;
      box-shadow: 0 1px 2px rgba(0, 0, 0, 0.04);
    }

    .table-responsive {
      overflow-x: auto;
    }

    .title-cell {
      display: flex;
      flex-direction: column;
    }

    .doc-title-text {
      font-weight: 600;
      color: #0f172a;
    }

    .doc-filename-text {
      font-size: 0.725rem;
      color: #64748b;
      margin-top: 0.15rem;
    }

    .badge-type {
      background-color: #f1f5f9;
      color: #334155;
      padding: 0.15rem 0.45rem;
      border-radius: 3px;
      font-size: 0.725rem;
      font-weight: 600;
    }

    .date-cell {
      white-space: nowrap;
      font-size: 0.775rem;
      color: #64748b;
    }

    .row-actions {
      display: flex;
      gap: 0.4rem;
    }

    .btn-row {
      background-color: #f8fafc;
      border: 1px solid #cbd5e1;
      color: #1e3a8a;
      padding: 0.25rem 0.5rem;
      border-radius: 3px;
      font-size: 0.75rem;
      font-weight: 600;
      cursor: pointer;
    }

    .btn-row:hover {
      background-color: #eff6ff;
      border-color: #bfdbfe;
    }

    .btn-row-danger {
      color: #dc2626;
      border-color: #fca5a5;
    }

    .btn-row-danger:hover {
      background-color: #fee2e2;
    }

    /* Modal Form */
    .modal-instruction {
      font-size: 0.8rem;
      color: #64748b;
      margin-bottom: 1rem;
      line-height: 1.4;
    }

    .form-grid {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 0.85rem;
    }

    .form-col {
      display: flex;
      flex-direction: column;
      gap: 0.3rem;
    }

    .form-col.full {
      grid-column: 1 / -1;
    }

    .form-col label {
      font-size: 0.775rem;
      font-weight: 600;
      color: #334155;
    }

    .file-control {
      padding: 0.35rem !important;
      font-size: 0.8rem;
    }

    .file-summary {
      font-size: 0.75rem;
      color: #1e40af;
      margin-top: 0.25rem;
    }

    /* Meta Modal List */
    .meta-attr-list {
      display: flex;
      flex-direction: column;
      gap: 0.65rem;
    }

    .meta-attr-row {
      display: flex;
      justify-content: space-between;
      align-items: center;
      font-size: 0.825rem;
      padding-bottom: 0.45rem;
      border-bottom: 1px solid #f1f5f9;
    }

    .meta-attr-k {
      font-weight: 600;
      color: #475569;
    }

    .text-break {
      word-break: break-all;
      font-size: 0.725rem;
    }

    .error-detail-box {
      margin-top: 0.5rem;
      padding: 0.65rem;
      background-color: #fef2f2;
      border: 1px solid #fecaca;
      border-radius: 4px;
    }

    .error-heading {
      font-size: 0.75rem;
      font-weight: 700;
      color: #991b1b;
      display: block;
      margin-bottom: 0.25rem;
    }

    .error-text {
      font-size: 0.8rem;
      color: #b91c1c;
    }

    .state-box {
      text-align: center;
      padding: 3rem;
      color: #64748b;
      font-size: 0.85rem;
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

    .spinner-small {
      width: 14px;
      height: 14px;
      border: 2px solid #bbf7d0;
      border-top-color: #16a34a;
      border-radius: 50%;
      animation: spin 0.8s linear infinite;
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
  uploadStage = signal<number>(1);
  uploadError = signal<string | null>(null);
  uploadSuccess = signal<string | null>(null);
  selectedDoc = signal<DocumentItem | null>(null);
  showUploadModal = signal(false);

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
      error: () => {
        this.isLoading.set(false);
      }
    });
  }

  toggleUploadModal(show: boolean): void {
    this.showUploadModal.set(show);
    if (!show) {
      this.uploadError.set(null);
    }
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
    this.uploadStage.set(1);
    this.uploadError.set(null);
    this.uploadSuccess.set(null);
    this.showUploadModal.set(false);

    // Simulate realistic ingestion pipeline stages
    const stageTimer1 = setTimeout(() => this.uploadStage.set(2), 600);
    const stageTimer2 = setTimeout(() => this.uploadStage.set(3), 1300);
    const stageTimer3 = setTimeout(() => this.uploadStage.set(4), 2100);

    this.documentService.uploadDocument(
      this.selectedFile,
      this.uploadTitle,
      this.uploadType,
      this.uploadVersion,
      this.uploadSource
    ).subscribe({
      next: (created) => {
        clearTimeout(stageTimer1);
        clearTimeout(stageTimer2);
        clearTimeout(stageTimer3);
        this.isUploading.set(false);
        this.uploadSuccess.set(`Document "${created.title}" successfully ingested and indexed into vector repository!`);
        this.selectedFile = null;
        this.uploadTitle = '';
        this.uploadSource = '';
        this.loadDocuments();
      },
      error: (err) => {
        clearTimeout(stageTimer1);
        clearTimeout(stageTimer2);
        clearTimeout(stageTimer3);
        this.isUploading.set(false);
        this.uploadError.set(err?.error?.detail || err?.error?.message || 'Document ingestion failed.');
      }
    });
  }

  deleteDocument(id: string, title: string): void {
    if (!confirm(`Are you sure you want to permanently delete compliance document "${title}"?`)) return;

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
