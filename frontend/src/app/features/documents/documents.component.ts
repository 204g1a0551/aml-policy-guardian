import { Component, OnInit, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { DocumentService } from '../../core/services/document.service';
import { AuthService } from '../../core/services/auth.service';
import {
  DocumentItem,
  DocumentType,
  PolicyFamilySummary,
  PolicyVersionDetail,
  PolicyComparisonReport,
  SectionDiff,
  ComplianceNotification
} from '../../core/models/document.models';

@Component({
  selector: 'app-documents',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="documents-container">
      <header class="page-header">
        <div>
          <h2>AML Compliance Governance & Policy Intelligence</h2>
          <p class="subtitle">Authoritative banking policies, version lineage, statutory diff intelligence, and audit-governed approvals.</p>
        </div>
        <div class="header-actions">
          <button type="button" class="btn-secondary" (click)="refreshCurrentView()" [disabled]="isLoading()">
            <span>Refresh</span>
          </button>
          @if (authService.isAdmin()) {
            <button type="button" class="btn-primary" (click)="toggleUploadModal(true)">
              <span>+ Ingest Policy Document</span>
            </button>
          }
        </div>
      </header>

      <!-- View Navigation Tabs -->
      <nav class="view-tabs">
        <button 
          type="button" 
          class="tab-btn" 
          [class.active]="activeTab() === 'catalog'" 
          (click)="setTab('catalog')">
          <span class="tab-icon">📄</span> Document Catalog
          <span class="tab-badge">{{ documents().length }}</span>
        </button>
        <button 
          type="button" 
          class="tab-btn" 
          [class.active]="activeTab() === 'versioning'" 
          (click)="setTab('versioning')">
          <span class="tab-icon">⚖️</span> Regulatory Policy Versioning & Diff
          @if (policyFamilies().length > 0) {
            <span class="tab-badge">{{ policyFamilies().length }} Families</span>
          }
        </button>
        <button 
          type="button" 
          class="tab-btn" 
          [class.active]="activeTab() === 'notifications'" 
          (click)="setTab('notifications')">
          <span class="tab-icon">🔔</span> Compliance Notifications
          @if (unreadCount() > 0) {
            <span class="tab-badge badge-unread">{{ unreadCount() }} new</span>
          }
        </button>
      </nav>

      <!-- Feedback Banners -->
      @if (actionSuccess()) {
        <div class="alert-banner alert-success">
          <span>{{ actionSuccess() }}</span>
          <button type="button" class="btn-dismiss" (click)="actionSuccess.set(null)">&times;</button>
        </div>
      }
      @if (actionError()) {
        <div class="alert-banner alert-danger">
          <span>{{ actionError() }}</span>
          <button type="button" class="btn-dismiss" (click)="actionError.set(null)">&times;</button>
        </div>
      }

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

      <!-- TAB 1: Document Catalog -->
      @if (activeTab() === 'catalog') {
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
                    <th>Policy Code</th>
                    <th>Version & Approval</th>
                    <th>Ingestion Status</th>
                    <th>Effective</th>
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
                      <td>
                        @if (doc.policyCode) {
                          <span class="badge-code">{{ doc.policyCode }}</span>
                        } @else {
                          <span class="text-muted">—</span>
                        }
                      </td>
                      <td>
                        <div class="version-cell">
                          <span class="badge-version">{{ doc.version || 'v1.0' }}</span>
                          <span class="badge-approval" [class]="'approval-' + (doc.approvalStatus || 'APPROVED').toLowerCase()">
                            {{ doc.approvalStatus || 'APPROVED' }}
                          </span>
                        </div>
                      </td>
                      <td>
                        @if (doc.status === 'READY') {
                          <span class="badge-status-ready">✓ READY</span>
                        } @else if (doc.status === 'PROCESSING' || doc.status === 'UPLOADED') {
                          <span class="badge-status-processing">
                            <span class="spinner-inline"></span> PROCESSING
                          </span>
                        } @else if (doc.status === 'FAILED') {
                          <span class="badge-status-failed" [title]="doc.errorMessage || 'Ingestion pipeline error'">
                            ⚠ FAILED
                          </span>
                        } @else {
                          <span class="badge-status-default">{{ doc.status }}</span>
                        }
                      </td>
                      <td>
                        @if (doc.isEffective) {
                          <span class="badge-effective-active">Active Effective</span>
                        } @else if (doc.supersededBy) {
                          <span class="badge-effective-superseded">Superseded</span>
                        } @else {
                          <span class="badge-effective-draft">Draft</span>
                        }
                      </td>
                      <td>
                        <div class="row-actions">
                          <button type="button" class="btn-row" (click)="openMetadataModal(doc)">
                            Metadata
                          </button>
                          @if (doc.status === 'FAILED' && authService.isAdmin()) {
                            <button type="button" class="btn-row btn-row-warning" (click)="retryIngestion(doc.id, doc.title)">
                              ↻ Retry
                            </button>
                          }
                          @if (doc.policyCode) {
                            <button type="button" class="btn-row btn-row-info" (click)="jumpToVersioning(doc.policyCode)">
                              Compare
                            </button>
                          }
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
      }

      <!-- TAB 2: Regulatory Policy Versioning & Diff -->
      @if (activeTab() === 'versioning') {
        <section class="versioning-container">
          <!-- Policy Families Grid -->
          <div class="policy-families-panel">
            <h3 class="section-title">Governed Policy Families</h3>
            <div class="family-cards-grid">
              @for (family of policyFamilies(); track family.policyCode) {
                <div 
                  class="family-card" 
                  [class.selected]="selectedPolicyCode() === family.policyCode"
                  (click)="selectPolicyFamily(family.policyCode)">
                  <div class="family-card-header">
                    <span class="family-code">{{ family.policyCode }}</span>
                    <span class="badge-jurisdiction">{{ family.jurisdiction || 'GLOBAL' }}</span>
                  </div>
                  <div class="family-card-body">
                    <div class="family-stat">
                      <span class="stat-label">Effective Version:</span>
                      <span class="stat-value font-mono">{{ family.activeEffectiveVersion || 'None Active' }}</span>
                    </div>
                    <div class="family-stat">
                      <span class="stat-label">Latest Version:</span>
                      <span class="stat-value font-mono">{{ family.latestVersion }}</span>
                    </div>
                    <div class="family-stat">
                      <span class="stat-label">Total Versions:</span>
                      <span class="stat-value">{{ family.totalVersions }}</span>
                    </div>
                  </div>
                  @if (family.hasPendingApproval) {
                    <div class="family-card-footer">
                      <span class="badge-pending">⚠️ Pending Approval</span>
                    </div>
                  }
                </div>
              }
              @if (policyFamilies().length === 0) {
                <div class="empty-family-state">
                  <p>No policy families identified with policy codes. Ingest documents with a Policy Code to track versions.</p>
                </div>
              }
            </div>
          </div>

          <!-- Version Comparison Studio -->
          @if (selectedPolicyCode()) {
            <div class="diff-studio-panel">
              <div class="studio-header">
                <div>
                  <h3 class="section-title">Version Comparison Studio: <span class="highlight-code">{{ selectedPolicyCode() }}</span></h3>
                  <p class="subtitle">Select base and target policy revisions to compute semantic section-level diffs, detect statutory threshold shifts, and review compliance obligations.</p>
                </div>
              </div>

              <!-- Version Selection Form -->
              <div class="comparison-bar">
                <div class="compare-selector">
                  <label for="baseVersionSelect">Base Version (Prior/Baseline):</label>
                  <select id="baseVersionSelect" [(ngModel)]="baseDocId" class="form-select">
                    <option value="">Select Base Document</option>
                    @for (v of policyVersions(); track v.id) {
                      <option [value]="v.id">
                        {{ v.version }} - {{ v.title }} ({{ v.approvalStatus }}) {{ v.isEffective ? '[CURRENT ACTIVE]' : '' }}
                      </option>
                    }
                  </select>
                </div>

                <div class="compare-arrow">➔</div>

                <div class="compare-selector">
                  <label for="targetVersionSelect">Target Version (New/Candidate):</label>
                  <select id="targetVersionSelect" [(ngModel)]="targetDocId" class="form-select">
                    <option value="">Select Target Document</option>
                    @for (v of policyVersions(); track v.id) {
                      <option [value]="v.id">
                        {{ v.version }} - {{ v.title }} ({{ v.approvalStatus }}) {{ v.isEffective ? '[CURRENT ACTIVE]' : '' }}
                      </option>
                    }
                  </select>
                </div>

                <button 
                  type="button" 
                  class="btn-primary" 
                  [disabled]="!baseDocId() || !targetDocId() || baseDocId() === targetDocId() || isComparing()"
                  (click)="executeComparison()">
                  @if (isComparing()) {
                    <span>Analyzing Diffs...</span>
                  } @else {
                    <span>🔍 Compare Revisions</span>
                  }
                </button>
              </div>

              <!-- Comparison Report View -->
              @if (comparisonReport()) {
                <div class="report-container">
                  <!-- Metrics Ribbon -->
                  <div class="metrics-ribbon">
                    <div class="metric-item">
                      <span class="metric-value">{{ comparisonReport()?.totalSections }}</span>
                      <span class="metric-label">Total Sections</span>
                    </div>
                    <div class="metric-item metric-addition">
                      <span class="metric-value">+{{ comparisonReport()?.additions }}</span>
                      <span class="metric-label">Additions</span>
                    </div>
                    <div class="metric-item metric-deletion">
                      <span class="metric-value">-{{ comparisonReport()?.deletions }}</span>
                      <span class="metric-label">Deletions</span>
                    </div>
                    <div class="metric-item metric-obligation">
                      <span class="metric-value">Δ {{ comparisonReport()?.modifiedObligations }}</span>
                      <span class="metric-label">Modified Obligations</span>
                    </div>
                    <div class="metric-item metric-unchanged">
                      <span class="metric-value">{{ comparisonReport()?.unchanged }}</span>
                      <span class="metric-label">Unchanged</span>
                    </div>
                    <div class="metric-item" [class.metric-validated]="comparisonReport()?.allHumanValidated" [class.metric-pending]="!comparisonReport()?.allHumanValidated">
                      <span class="metric-value">{{ comparisonReport()?.allHumanValidated ? 'VALIDATED' : 'REVIEW NEEDED' }}</span>
                      <span class="metric-label">Human Validation</span>
                    </div>
                  </div>

                  <!-- Admin Version Approval Banner -->
                  @if (authService.isAdmin() && isTargetApprovable()) {
                    <div class="approval-card">
                      <div class="approval-info">
                        <h4>Authoritative Policy Activation</h4>
                        <p>Approving this version will mark it active, supersede prior revisions, re-index chunks into the retrieval pipeline, and generate forensic audit records.</p>
                      </div>
                      <button 
                        type="button" 
                        class="btn-approve" 
                        (click)="approveSelectedTargetVersion()">
                        🛡️ Approve & Activate Version {{ comparisonReport()?.targetVersion }}
                      </button>
                    </div>
                  }

                  <!-- Side-by-Side Section Diffs -->
                  <div class="diff-list">
                    @for (diff of comparisonReport()?.diffs; track diff.id) {
                      <div class="diff-card" [class]="'diff-card-' + diff.changeClassification.toLowerCase()">
                        <div class="diff-card-header">
                          <div class="diff-header-left">
                            <span class="section-badge font-mono">{{ diff.sectionNumber || 'Section' }}</span>
                            <span class="section-title-text">{{ diff.sectionTitle }}</span>
                          </div>
                          <div class="diff-header-right">
                            <span class="badge-diff-type" [class]="'badge-' + diff.changeClassification.toLowerCase()">
                              {{ diff.changeClassification }}
                            </span>
                            <span class="badge-chunks font-mono">{{ diff.affectedChunkCount }} Chunks Affected</span>
                          </div>
                        </div>

                        <!-- Side by Side Content -->
                        <div class="side-by-side-grid">
                          <div class="diff-panel base-panel">
                            <div class="panel-header">Base Version ({{ comparisonReport()?.baseVersion }})</div>
                            <div class="panel-body font-mono">
                              {{ diff.baseContent || '[Section did not exist in base version]' }}
                            </div>
                          </div>
                          <div class="diff-panel target-panel">
                            <div class="panel-header">Target Version ({{ comparisonReport()?.targetVersion }})</div>
                            <div class="panel-body font-mono">
                              {{ diff.targetContent || '[Section removed in target version]' }}
                            </div>
                          </div>
                        </div>

                        <!-- Human Obligation Validation Box -->
                        @if (diff.requiresHumanValidation) {
                          <div class="validation-box">
                            <div class="validation-box-header">
                              <span class="val-icon">⚖️</span>
                              <div>
                                <strong>Compliance Obligation Human Validation Required</strong>
                                <p class="val-sub">This section alters regulatory duties, deadlines, or statutory thresholds. Mark validated or rejected after compliance review.</p>
                              </div>
                              <span class="badge-val-status" [class]="'val-' + diff.humanValidationStatus.toLowerCase()">
                                Status: {{ diff.humanValidationStatus }}
                              </span>
                            </div>

                            @if (diff.validationNotes) {
                              <div class="existing-notes">
                                <strong>Analyst Notes (by {{ diff.validatedBy }} at {{ diff.validatedAt | date:'short' }}):</strong>
                                <p>{{ diff.validationNotes }}</p>
                              </div>
                            }

                            <!-- Validation Actions -->
                            <div class="val-actions-row">
                              <input 
                                type="text" 
                                placeholder="Add analyst validation rationale / regulatory reference..."
                                [(ngModel)]="getDiffNoteModel()[diff.id]" 
                                class="val-notes-input"
                              />
                              <button 
                                type="button" 
                                class="btn-val-approve" 
                                (click)="validateDiffItem(diff.id, 'VALIDATED')">
                                Validate Change
                              </button>
                              <button 
                                type="button" 
                                class="btn-val-reject" 
                                (click)="validateDiffItem(diff.id, 'REJECTED')">
                                Reject Interpretation
                              </button>
                            </div>
                          </div>
                        }
                      </div>
                    }
                  </div>
                </div>
              }
            </div>
          }
        </section>
      }

      <!-- TAB 3: Compliance Notifications -->
      @if (activeTab() === 'notifications') {
        <section class="notifications-panel">
          <div class="notifications-header">
            <h3>Compliance Change Notifications</h3>
            <p class="subtitle">Broadcasts of regulatory policy activations, superseded versions, and required compliance reviews.</p>
          </div>

          <div class="notifications-list">
            @for (notif of notifications(); track notif.id) {
              <div class="notification-card" [class.unread]="!notif.read">
                <div class="notif-indicator"></div>
                <div class="notif-content">
                  <div class="notif-top">
                    <span class="notif-title">{{ notif.title }}</span>
                    <span class="notif-date">{{ notif.createdAt | date:'short' }}</span>
                  </div>
                  <p class="notif-msg">{{ notif.message }}</p>
                  <div class="notif-footer">
                    @if (notif.policyCode) {
                      <span class="badge-code">{{ notif.policyCode }}</span>
                    }
                    <span class="badge-role">Target Role: {{ notif.recipientRole }}</span>
                    @if (!notif.read) {
                      <button type="button" class="btn-read" (click)="markAsRead(notif.id)">
                        Mark as Read
                      </button>
                    }
                  </div>
                </div>
              </div>
            }
            @if (notifications().length === 0) {
              <div class="empty-notif-box">
                <p>No notifications on record.</p>
              </div>
            }
          </div>
        </section>
      }

      <!-- Ingest Document Modal -->
      @if (showUploadModal()) {
        <div class="enterprise-modal-backdrop" (click)="toggleUploadModal(false)">
          <div class="enterprise-modal-dialog" (click)="$event.stopPropagation()">
            <div class="enterprise-modal-header">
              <h3>Ingest Policy Document & Register Version Lineage</h3>
              <button type="button" class="enterprise-modal-close" (click)="toggleUploadModal(false)">&times;</button>
            </div>
            <form (ngSubmit)="onUpload()">
              <div class="enterprise-modal-body">
                <p class="modal-instruction">
                  Supported formats: PDF, DOCX, TXT. Ingestion pipeline extracts text, calculates SHA-256 fingerprint, performs semantic chunking, and persists pgvector embeddings.
                </p>

                <div class="form-grid">
                  <div class="form-col full">
                    <label for="title">Document Title *</label>
                    <input 
                      id="title" 
                      type="text" 
                      [(ngModel)]="uploadTitle" 
                      name="title" 
                      placeholder="e.g. BSA Currency Transaction Reporting Policy"
                      required
                    />
                  </div>

                  <div class="form-col">
                    <label for="policyCode">Policy Code (Family Key)</label>
                    <input 
                      id="policyCode" 
                      type="text" 
                      [(ngModel)]="uploadPolicyCode" 
                      name="policyCode" 
                      placeholder="e.g. BSA-POL-001"
                    />
                  </div>

                  <div class="form-col">
                    <label for="version">Policy Version *</label>
                    <input 
                      id="version" 
                      type="text" 
                      [(ngModel)]="uploadVersion" 
                      name="version" 
                      placeholder="e.g. v2.0"
                      required
                    />
                  </div>

                  <div class="form-col">
                    <label for="type">Classification *</label>
                    <select id="type" [(ngModel)]="uploadType" name="type" required>
                      <option value="POLICY">Policy</option>
                      <option value="PROCEDURE">Procedure / SOP</option>
                      <option value="GUIDELINE">Guideline</option>
                      <option value="REGULATORY">Regulatory Requirement</option>
                      <option value="TRAINING">Training Manual</option>
                    </select>
                  </div>

                  <div class="form-col">
                    <label for="jurisdiction">Jurisdiction</label>
                    <input 
                      id="jurisdiction" 
                      type="text" 
                      [(ngModel)]="uploadJurisdiction" 
                      name="jurisdiction" 
                      placeholder="e.g. US, EU, GLOBAL"
                    />
                  </div>

                  <div class="form-col full">
                    <label for="source">Issuing Source / Authority</label>
                    <input 
                      id="source" 
                      type="text" 
                      [(ngModel)]="uploadSource" 
                      name="source" 
                      placeholder="e.g. FinCEN / OCC Compliance Oversight"
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
                  <span class="meta-attr-k">Policy Code:</span>
                  <span class="meta-attr-v">{{ selectedDoc()?.policyCode || '—' }}</span>
                </div>
                <div class="meta-attr-row">
                  <span class="meta-attr-k">Version:</span>
                  <span class="meta-attr-v">{{ selectedDoc()?.version }}</span>
                </div>
                <div class="meta-attr-row">
                  <span class="meta-attr-k">Approval Status:</span>
                  <span class="meta-attr-v">{{ selectedDoc()?.approvalStatus || 'APPROVED' }}</span>
                </div>
                <div class="meta-attr-row">
                  <span class="meta-attr-k">Effective Status:</span>
                  <span class="meta-attr-v">{{ selectedDoc()?.isEffective ? 'ACTIVE EFFECTIVE' : (selectedDoc()?.supersededBy ? 'SUPERSEDED' : 'DRAFT') }}</span>
                </div>
                <div class="meta-attr-row">
                  <span class="meta-attr-k">Jurisdiction:</span>
                  <span class="meta-attr-v">{{ selectedDoc()?.jurisdiction || 'GLOBAL' }}</span>
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
      max-width: 1280px;
      margin: 0 auto;
      padding: 1.75rem 1.5rem;
    }

    .page-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 1.25rem;
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

    /* Tabs */
    .view-tabs {
      display: flex;
      gap: 0.5rem;
      border-bottom: 2px solid #e2e8f0;
      margin-bottom: 1.5rem;
    }

    .tab-btn {
      background: none;
      border: none;
      padding: 0.75rem 1.25rem;
      font-size: 0.9rem;
      font-weight: 600;
      color: #64748b;
      cursor: pointer;
      display: flex;
      align-items: center;
      gap: 0.5rem;
      border-bottom: 2px solid transparent;
      margin-bottom: -2px;
      transition: all 0.15s ease-in-out;
    }

    .tab-btn:hover {
      color: #1e293b;
    }

    .tab-btn.active {
      color: #0284c7;
      border-bottom-color: #0284c7;
    }

    .tab-badge {
      font-size: 0.75rem;
      background: #f1f5f9;
      color: #475569;
      padding: 0.15rem 0.5rem;
      border-radius: 9999px;
    }

    .badge-unread {
      background: #fee2e2;
      color: #b91c1c;
      font-weight: 700;
    }

    /* Banners */
    .alert-banner {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 0.75rem 1rem;
      border-radius: 6px;
      margin-bottom: 1.25rem;
      font-size: 0.9rem;
    }

    .alert-success {
      background-color: #ecfdf5;
      color: #065f46;
      border: 1px solid #a7f3d0;
    }

    .alert-danger {
      background-color: #fef2f2;
      color: #991b1b;
      border: 1px solid #fecaca;
    }

    .btn-dismiss {
      background: none;
      border: none;
      font-size: 1.25rem;
      cursor: pointer;
      color: inherit;
    }

    /* Toolbar */
    .toolbar-section {
      display: flex;
      justify-content: space-between;
      align-items: center;
      background: #ffffff;
      padding: 0.85rem 1rem;
      border: 1px solid #e2e8f0;
      border-radius: 8px;
      margin-bottom: 1.25rem;
      box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
    }

    .filter-controls {
      display: flex;
      gap: 0.75rem;
      flex: 1;
      max-width: 800px;
    }

    .search-input {
      flex: 2;
      padding: 0.45rem 0.75rem;
      border: 1px solid #cbd5e1;
      border-radius: 6px;
      font-size: 0.85rem;
    }

    .filter-select, .form-select {
      flex: 1;
      padding: 0.45rem 0.75rem;
      border: 1px solid #cbd5e1;
      border-radius: 6px;
      font-size: 0.85rem;
      background-color: #ffffff;
    }

    .count-badge {
      font-size: 0.8rem;
      color: #64748b;
      font-weight: 600;
    }

    /* Table */
    .table-card {
      background: #ffffff;
      border: 1px solid #e2e8f0;
      border-radius: 8px;
      overflow: hidden;
      box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
    }

    .table-responsive {
      overflow-x: auto;
    }

    .enterprise-table {
      width: 100%;
      border-collapse: collapse;
      text-align: left;
      font-size: 0.85rem;
    }

    .enterprise-table th {
      background: #f8fafc;
      color: #475569;
      font-weight: 600;
      padding: 0.75rem 1rem;
      border-bottom: 1px solid #e2e8f0;
      white-space: nowrap;
    }

    .enterprise-table td {
      padding: 0.75rem 1rem;
      border-bottom: 1px solid #f1f5f9;
      color: #1e293b;
      vertical-align: middle;
    }

    .enterprise-table tr:hover {
      background-color: #f8fafc;
    }

    .title-cell {
      display: flex;
      flex-direction: column;
      gap: 0.2rem;
    }

    .doc-title-text {
      font-weight: 600;
      color: #0f172a;
    }

    .doc-filename-text {
      font-size: 0.75rem;
      color: #64748b;
    }

    /* Badges */
    .badge-type {
      background: #f1f5f9;
      color: #334155;
      padding: 0.2rem 0.5rem;
      border-radius: 4px;
      font-size: 0.75rem;
      font-weight: 600;
    }

    .badge-code {
      background: #e0f2fe;
      color: #0369a1;
      padding: 0.2rem 0.5rem;
      border-radius: 4px;
      font-size: 0.75rem;
      font-weight: 600;
      font-family: monospace;
    }

    .badge-version {
      font-family: monospace;
      font-weight: 600;
      color: #334155;
      margin-right: 0.35rem;
    }

    .badge-approval {
      font-size: 0.7rem;
      padding: 0.15rem 0.4rem;
      border-radius: 4px;
      font-weight: 600;
    }

    .approval-approved {
      background: #ecfdf5;
      color: #047857;
    }

    .approval-draft {
      background: #fef9c3;
      color: #854d0e;
    }

    .badge-effective-active {
      background: #ecfdf5;
      color: #047857;
      font-size: 0.75rem;
      padding: 0.2rem 0.5rem;
      border-radius: 9999px;
      font-weight: 600;
    }

    .badge-effective-superseded {
      background: #f1f5f9;
      color: #94a3b8;
      font-size: 0.75rem;
      padding: 0.2rem 0.5rem;
      border-radius: 9999px;
    }

    .badge-effective-draft {
      background: #fff7ed;
      color: #c2410c;
      font-size: 0.75rem;
      padding: 0.2rem 0.5rem;
      border-radius: 9999px;
    }

    .badge-status-ready {
      background: #ecfdf5;
      color: #047857;
      font-size: 0.72rem;
      padding: 0.2rem 0.5rem;
      border-radius: 9999px;
      font-weight: 600;
      display: inline-flex;
      align-items: center;
      gap: 0.25rem;
    }

    .badge-status-processing {
      background: #eff6ff;
      color: #1d4ed8;
      font-size: 0.72rem;
      padding: 0.2rem 0.5rem;
      border-radius: 9999px;
      font-weight: 600;
      display: inline-flex;
      align-items: center;
      gap: 0.35rem;
    }

    .badge-status-failed {
      background: #fef2f2;
      color: #b91c1c;
      font-size: 0.72rem;
      padding: 0.2rem 0.5rem;
      border-radius: 9999px;
      font-weight: 600;
      cursor: help;
    }

    .badge-status-default {
      background: #f1f5f9;
      color: #64748b;
      font-size: 0.72rem;
      padding: 0.2rem 0.5rem;
      border-radius: 9999px;
    }

    .spinner-inline {
      width: 0.7rem;
      height: 0.7rem;
      border: 2px solid #bfdbfe;
      border-top-color: #1d4ed8;
      border-radius: 50%;
      animation: spin 0.8s linear infinite;
      display: inline-block;
    }

    .btn-row-warning {
      background: #fef3c7;
      color: #92400e;
      border: 1px solid #fde68a;
    }

    .btn-row-warning:hover {
      background: #fde68a;
    }

    .badge-status {
      font-size: 0.75rem;
      padding: 0.2rem 0.5rem;
      border-radius: 4px;
      font-weight: 600;
    }

    .status-ready {
      background: #ecfdf5;
      color: #047857;
    }

    .status-processing {
      background: #eff6ff;
      color: #1d4ed8;
    }

    .status-failed {
      background: #fef2f2;
      color: #b91c1c;
    }

    .row-actions {
      display: flex;
      gap: 0.35rem;
    }

    .btn-row {
      background: #ffffff;
      border: 1px solid #cbd5e1;
      padding: 0.3rem 0.6rem;
      font-size: 0.75rem;
      border-radius: 4px;
      cursor: pointer;
      color: #334155;
    }

    .btn-row:hover {
      background: #f8fafc;
    }

    .btn-row-info {
      color: #0284c7;
      border-color: #bae6fd;
    }

    .btn-row-danger {
      color: #dc2626;
      border-color: #fecaca;
    }

    /* Buttons */
    .btn-primary {
      background: #0284c7;
      color: #ffffff;
      border: none;
      padding: 0.5rem 1rem;
      font-size: 0.85rem;
      font-weight: 600;
      border-radius: 6px;
      cursor: pointer;
    }

    .btn-primary:hover:not(:disabled) {
      background: #0369a1;
    }

    .btn-primary:disabled {
      opacity: 0.6;
      cursor: not-allowed;
    }

    .btn-secondary {
      background: #ffffff;
      color: #334155;
      border: 1px solid #cbd5e1;
      padding: 0.5rem 1rem;
      font-size: 0.85rem;
      font-weight: 600;
      border-radius: 6px;
      cursor: pointer;
    }

    .btn-secondary:hover:not(:disabled) {
      background: #f8fafc;
    }

    /* Tab 2: Policy Versioning Styles */
    .versioning-container {
      display: flex;
      flex-direction: column;
      gap: 1.5rem;
    }

    .section-title {
      font-size: 1.1rem;
      font-weight: 700;
      color: #0f172a;
      margin-bottom: 0.75rem;
    }

    .family-cards-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
      gap: 1rem;
    }

    .family-card {
      background: #ffffff;
      border: 1px solid #e2e8f0;
      border-radius: 8px;
      padding: 1rem;
      cursor: pointer;
      transition: all 0.15s ease-in-out;
    }

    .family-card:hover {
      border-color: #38bdf8;
      box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.05);
    }

    .family-card.selected {
      border-color: #0284c7;
      background-color: #f0f9ff;
    }

    .family-card-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 0.75rem;
    }

    .family-code {
      font-weight: 700;
      font-family: monospace;
      color: #0f172a;
    }

    .badge-jurisdiction {
      background: #f1f5f9;
      color: #475569;
      font-size: 0.7rem;
      padding: 0.15rem 0.4rem;
      border-radius: 4px;
      font-weight: 600;
    }

    .family-stat {
      display: flex;
      justify-content: space-between;
      font-size: 0.8rem;
      margin-bottom: 0.35rem;
    }

    .stat-label {
      color: #64748b;
    }

    .stat-value {
      color: #0f172a;
      font-weight: 600;
    }

    .family-card-footer {
      margin-top: 0.75rem;
      padding-top: 0.5rem;
      border-top: 1px solid #e2e8f0;
    }

    .badge-pending {
      color: #b45309;
      font-size: 0.75rem;
      font-weight: 600;
    }

    .empty-family-state {
      grid-column: 1 / -1;
      padding: 2rem;
      text-align: center;
      background: #f8fafc;
      border: 1px dashed #cbd5e1;
      border-radius: 8px;
      color: #64748b;
    }

    /* Diff Studio */
    .diff-studio-panel {
      background: #ffffff;
      border: 1px solid #e2e8f0;
      border-radius: 8px;
      padding: 1.5rem;
      box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
    }

    .highlight-code {
      color: #0284c7;
      font-family: monospace;
    }

    .comparison-bar {
      display: flex;
      align-items: flex-end;
      gap: 1rem;
      margin-top: 1rem;
      margin-bottom: 1.5rem;
      padding-bottom: 1.5rem;
      border-bottom: 1px solid #e2e8f0;
    }

    .compare-selector {
      flex: 1;
      display: flex;
      flex-direction: column;
      gap: 0.35rem;
    }

    .compare-selector label {
      font-size: 0.8rem;
      font-weight: 600;
      color: #475569;
    }

    .compare-arrow {
      font-size: 1.25rem;
      color: #94a3b8;
      padding-bottom: 0.4rem;
    }

    /* Metrics Ribbon */
    .metrics-ribbon {
      display: grid;
      grid-template-columns: repeat(6, 1fr);
      gap: 0.75rem;
      margin-bottom: 1.5rem;
    }

    .metric-item {
      background: #f8fafc;
      border: 1px solid #e2e8f0;
      border-radius: 6px;
      padding: 0.75rem;
      text-align: center;
    }

    .metric-value {
      display: block;
      font-size: 1.35rem;
      font-weight: 700;
      color: #0f172a;
    }

    .metric-label {
      font-size: 0.7rem;
      text-transform: uppercase;
      font-weight: 600;
      color: #64748b;
      letter-spacing: 0.025em;
    }

    .metric-addition .metric-value { color: #059669; }
    .metric-deletion .metric-value { color: #dc2626; }
    .metric-obligation .metric-value { color: #d97706; }
    .metric-unchanged .metric-value { color: #64748b; }
    .metric-validated .metric-value { color: #059669; font-size: 1rem; }
    .metric-pending .metric-value { color: #d97706; font-size: 1rem; }

    /* Approval Card */
    .approval-card {
      display: flex;
      justify-content: space-between;
      align-items: center;
      background: #eff6ff;
      border: 1px solid #bfdbfe;
      border-radius: 8px;
      padding: 1rem 1.25rem;
      margin-bottom: 1.5rem;
    }

    .approval-info h4 {
      font-size: 0.95rem;
      color: #1e3a8a;
      margin-bottom: 0.25rem;
    }

    .approval-info p {
      font-size: 0.8rem;
      color: #3b82f6;
    }

    .btn-approve {
      background: #2563eb;
      color: #ffffff;
      border: none;
      padding: 0.6rem 1.2rem;
      border-radius: 6px;
      font-size: 0.85rem;
      font-weight: 600;
      cursor: pointer;
      white-space: nowrap;
    }

    .btn-approve:hover {
      background: #1d4ed8;
    }

    /* Diff Cards */
    .diff-list {
      display: flex;
      flex-direction: column;
      gap: 1.25rem;
    }

    .diff-card {
      border: 1px solid #e2e8f0;
      border-radius: 8px;
      overflow: hidden;
    }

    .diff-card-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 0.75rem 1rem;
      background: #f8fafc;
      border-bottom: 1px solid #e2e8f0;
    }

    .diff-header-left {
      display: flex;
      align-items: center;
      gap: 0.5rem;
    }

    .section-badge {
      background: #e2e8f0;
      color: #334155;
      padding: 0.2rem 0.5rem;
      border-radius: 4px;
      font-size: 0.75rem;
    }

    .section-title-text {
      font-weight: 600;
      color: #0f172a;
      font-size: 0.9rem;
    }

    .diff-header-right {
      display: flex;
      align-items: center;
      gap: 0.5rem;
    }

    .badge-diff-type {
      font-size: 0.75rem;
      padding: 0.2rem 0.5rem;
      border-radius: 4px;
      font-weight: 700;
    }

    .badge-addition { background: #dcfce7; color: #15803d; }
    .badge-deletion { background: #fee2e2; color: #b91c1c; }
    .badge-modified_obligation { background: #fef3c7; color: #b45309; }
    .badge-unchanged { background: #f1f5f9; color: #64748b; }

    .badge-chunks {
      font-size: 0.7rem;
      color: #64748b;
    }

    .side-by-side-grid {
      display: grid;
      grid-template-columns: 1fr 1fr;
      divide-x: 1px solid #e2e8f0;
    }

    .diff-panel {
      padding: 0.75rem 1rem;
      background: #ffffff;
      min-height: 80px;
    }

    .panel-header {
      font-size: 0.7rem;
      text-transform: uppercase;
      font-weight: 700;
      color: #94a3b8;
      margin-bottom: 0.5rem;
    }

    .panel-body {
      font-size: 0.8rem;
      line-height: 1.5;
      color: #1e293b;
      white-space: pre-wrap;
      word-break: break-word;
    }

    .base-panel {
      border-right: 1px solid #e2e8f0;
      background: #fffcfc;
    }

    .target-panel {
      background: #fcfffc;
    }

    /* Validation Box */
    .validation-box {
      background: #fffbeb;
      border-top: 1px solid #fef3c7;
      padding: 0.85rem 1rem;
    }

    .validation-box-header {
      display: flex;
      align-items: flex-start;
      gap: 0.5rem;
      margin-bottom: 0.75rem;
    }

    .val-icon {
      font-size: 1.25rem;
    }

    .val-sub {
      font-size: 0.75rem;
      color: #92400e;
      margin-top: 0.15rem;
    }

    .badge-val-status {
      margin-left: auto;
      font-size: 0.75rem;
      padding: 0.2rem 0.5rem;
      border-radius: 4px;
      font-weight: 600;
    }

    .val-pending { background: #fef3c7; color: #b45309; }
    .val-validated { background: #dcfce7; color: #15803d; }
    .val-rejected { background: #fee2e2; color: #b91c1c; }

    .existing-notes {
      font-size: 0.8rem;
      color: #78350f;
      background: #fef3c7;
      padding: 0.4rem 0.6rem;
      border-radius: 4px;
      margin-bottom: 0.75rem;
    }

    .val-actions-row {
      display: flex;
      gap: 0.5rem;
    }

    .val-notes-input {
      flex: 1;
      padding: 0.4rem 0.75rem;
      border: 1px solid #fde68a;
      border-radius: 4px;
      font-size: 0.8rem;
      background: #ffffff;
    }

    .btn-val-approve {
      background: #059669;
      color: #ffffff;
      border: none;
      padding: 0.4rem 0.85rem;
      border-radius: 4px;
      font-size: 0.8rem;
      font-weight: 600;
      cursor: pointer;
    }

    .btn-val-reject {
      background: #dc2626;
      color: #ffffff;
      border: none;
      padding: 0.4rem 0.85rem;
      border-radius: 4px;
      font-size: 0.8rem;
      font-weight: 600;
      cursor: pointer;
    }

    /* Tab 3: Notifications */
    .notifications-panel {
      background: #ffffff;
      border: 1px solid #e2e8f0;
      border-radius: 8px;
      padding: 1.5rem;
      box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
    }

    .notifications-header {
      margin-bottom: 1.25rem;
      border-bottom: 1px solid #e2e8f0;
      padding-bottom: 0.75rem;
    }

    .notifications-header h3 {
      font-size: 1.15rem;
      font-weight: 700;
      color: #0f172a;
    }

    .notifications-list {
      display: flex;
      flex-direction: column;
      gap: 0.75rem;
    }

    .notification-card {
      display: flex;
      border: 1px solid #e2e8f0;
      border-radius: 6px;
      padding: 0.85rem 1rem;
      background: #ffffff;
      transition: background-color 0.15s ease-in-out;
    }

    .notification-card.unread {
      background: #f0fdf4;
      border-color: #bbf7d0;
    }

    .notif-indicator {
      width: 4px;
      border-radius: 2px;
      margin-right: 0.75rem;
      background: #cbd5e1;
    }

    .notification-card.unread .notif-indicator {
      background: #16a34a;
    }

    .notif-content {
      flex: 1;
    }

    .notif-top {
      display: flex;
      justify-content: space-between;
      margin-bottom: 0.25rem;
    }

    .notif-title {
      font-weight: 700;
      color: #0f172a;
      font-size: 0.85rem;
    }

    .notif-date {
      font-size: 0.75rem;
      color: #94a3b8;
    }

    .notif-msg {
      font-size: 0.8rem;
      color: #334155;
      margin-bottom: 0.5rem;
    }

    .notif-footer {
      display: flex;
      align-items: center;
      gap: 0.5rem;
    }

    .badge-role {
      font-size: 0.7rem;
      background: #f1f5f9;
      color: #475569;
      padding: 0.15rem 0.4rem;
      border-radius: 4px;
    }

    .btn-read {
      margin-left: auto;
      background: #ffffff;
      border: 1px solid #cbd5e1;
      font-size: 0.75rem;
      padding: 0.2rem 0.5rem;
      border-radius: 4px;
      cursor: pointer;
    }

    .empty-notif-box {
      text-align: center;
      padding: 2rem;
      color: #64748b;
    }

    /* Modals */
    .enterprise-modal-backdrop {
      position: fixed;
      inset: 0;
      background: rgba(15, 23, 42, 0.5);
      backdrop-filter: blur(2px);
      display: flex;
      justify-content: center;
      align-items: center;
      z-index: 1000;
    }

    .enterprise-modal-dialog {
      background: #ffffff;
      border-radius: 10px;
      width: 90%;
      max-width: 600px;
      max-height: 90vh;
      overflow-y: auto;
      box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.1);
    }

    .enterprise-modal-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 1rem 1.25rem;
      border-bottom: 1px solid #e2e8f0;
    }

    .enterprise-modal-header h3 {
      font-size: 1.1rem;
      font-weight: 700;
      color: #0f172a;
    }

    .enterprise-modal-close {
      background: none;
      border: none;
      font-size: 1.5rem;
      color: #94a3b8;
      cursor: pointer;
    }

    .enterprise-modal-body {
      padding: 1.25rem;
    }

    .modal-instruction {
      font-size: 0.8rem;
      color: #64748b;
      margin-bottom: 1rem;
      line-height: 1.4;
    }

    .form-grid {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 1rem;
    }

    .form-col.full {
      grid-column: 1 / -1;
    }

    .form-grid label {
      display: block;
      font-size: 0.8rem;
      font-weight: 600;
      color: #475569;
      margin-bottom: 0.35rem;
    }

    .form-grid input, .form-grid select {
      width: 100%;
      padding: 0.5rem 0.75rem;
      border: 1px solid #cbd5e1;
      border-radius: 6px;
      font-size: 0.85rem;
      box-sizing: border-box;
    }

    .file-control {
      padding: 0.4rem;
    }

    .file-summary {
      font-size: 0.75rem;
      color: #0284c7;
      margin-top: 0.25rem;
    }

    .enterprise-modal-footer {
      display: flex;
      justify-content: flex-end;
      gap: 0.5rem;
      padding: 1rem 1.25rem;
      border-top: 1px solid #e2e8f0;
      background: #f8fafc;
    }

    /* Metadata details */
    .meta-attr-list {
      display: flex;
      flex-direction: column;
      gap: 0.6rem;
    }

    .meta-attr-row {
      display: flex;
      font-size: 0.85rem;
    }

    .meta-attr-k {
      width: 150px;
      color: #64748b;
      font-weight: 600;
    }

    .meta-attr-v {
      flex: 1;
      color: #0f172a;
    }

    .text-break {
      word-break: break-all;
    }

    .font-mono {
      font-family: monospace;
    }

    .error-detail-box {
      margin-top: 0.75rem;
      background: #fef2f2;
      border: 1px solid #fecaca;
      border-radius: 6px;
      padding: 0.75rem;
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

    /* Pipeline progress */
    .pipeline-progress-banner {
      background: #f0fdf4;
      border: 1px solid #bbf7d0;
      border-radius: 8px;
      padding: 1rem;
      margin-bottom: 1.25rem;
    }

    .pipeline-header {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      margin-bottom: 0.75rem;
    }

    .pipeline-title {
      font-size: 0.85rem;
      font-weight: 700;
      color: #166534;
    }

    .spinner-small {
      width: 14px;
      height: 14px;
      border: 2px solid #bbf7d0;
      border-top-color: #16a34a;
      border-radius: 50%;
      animation: spin 0.8s linear infinite;
    }

    .pipeline-stages {
      display: flex;
      align-items: center;
      gap: 0.5rem;
    }

    .stage-step {
      display: flex;
      align-items: center;
      gap: 0.35rem;
      font-size: 0.75rem;
      color: #94a3b8;
    }

    .stage-step.active {
      color: #16a34a;
      font-weight: 700;
    }

    .stage-step.complete {
      color: #059669;
    }

    .step-num {
      width: 18px;
      height: 18px;
      border-radius: 50%;
      background: #e2e8f0;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 0.7rem;
    }

    .stage-step.active .step-num, .stage-step.complete .step-num {
      background: #16a34a;
      color: #ffffff;
    }

    .stage-divider {
      flex: 1;
      height: 1px;
      background: #cbd5e1;
    }

    .state-box {
      display: flex;
      flex-direction: column;
      align-items: center;
      padding: 3rem 1rem;
      color: #64748b;
    }

    .spinner {
      width: 32px;
      height: 32px;
      border: 3px solid #e2e8f0;
      border-top-color: #0284c7;
      border-radius: 50%;
      animation: spin 0.8s linear infinite;
      margin-bottom: 0.75rem;
    }

    @keyframes spin {
      to { transform: rotate(360deg); }
    }
  `]
})
export class DocumentsComponent implements OnInit {
  activeTab = signal<'catalog' | 'versioning' | 'notifications'>('catalog');

  // Documents Catalog state
  documents = signal<DocumentItem[]>([]);
  isLoading = signal<boolean>(false);
  selectedDoc = signal<DocumentItem | null>(null);
  searchQuery = '';
  selectedTypeFilter = '';
  selectedStatusFilter = '';

  // Upload modal state
  showUploadModal = signal<boolean>(false);
  isUploading = signal<boolean>(false);
  uploadStage = signal<number>(1);
  uploadTitle = '';
  uploadType: DocumentType = 'POLICY';
  uploadVersion = 'v1.0';
  uploadSource = '';
  uploadPolicyCode = '';
  uploadJurisdiction = 'US';
  selectedFile: File | null = null;

  // Alerts
  actionSuccess = signal<string | null>(null);
  actionError = signal<string | null>(null);

  // Regulatory Versioning state
  policyFamilies = signal<PolicyFamilySummary[]>([]);
  selectedPolicyCode = signal<string>('');
  policyVersions = signal<PolicyVersionDetail[]>([]);
  baseDocId = signal<string>('');
  targetDocId = signal<string>('');
  comparisonReport = signal<PolicyComparisonReport | null>(null);
  isComparing = signal<boolean>(false);
  diffNotesMap: { [diffId: string]: string } = {};

  // Notifications state
  notifications = signal<ComplianceNotification[]>([]);
  unreadCount = computed(() => this.notifications().filter(n => !n.read).length);

  constructor(
    public authService: AuthService,
    private documentService: DocumentService
  ) {}

  ngOnInit(): void {
    this.loadDocuments();
    this.loadPolicyFamilies();
    this.loadNotifications();
  }

  setTab(tab: 'catalog' | 'versioning' | 'notifications'): void {
    this.activeTab.set(tab);
    if (tab === 'catalog') {
      this.loadDocuments();
    } else if (tab === 'versioning') {
      this.loadPolicyFamilies();
    } else if (tab === 'notifications') {
      this.loadNotifications();
    }
  }

  refreshCurrentView(): void {
    if (this.activeTab() === 'catalog') {
      this.loadDocuments();
    } else if (this.activeTab() === 'versioning') {
      this.loadPolicyFamilies();
      if (this.selectedPolicyCode()) {
        this.selectPolicyFamily(this.selectedPolicyCode());
      }
    } else if (this.activeTab() === 'notifications') {
      this.loadNotifications();
    }
  }

  // --- Document Catalog Methods ---
  loadDocuments(): void {
    this.isLoading.set(true);
    this.documentService.getDocuments().subscribe({
      next: (data) => {
        this.documents.set(data);
        this.isLoading.set(false);
      },
      error: (err) => {
        this.isLoading.set(false);
        this.actionError.set(err?.error?.detail || 'Failed to load documents catalog.');
      }
    });
  }

  filteredDocuments(): DocumentItem[] {
    let list = this.documents();

    if (this.searchQuery.trim()) {
      const q = this.searchQuery.toLowerCase();
      list = list.filter(d => 
        d.title.toLowerCase().includes(q) || 
        d.filename.toLowerCase().includes(q) ||
        (d.policyCode && d.policyCode.toLowerCase().includes(q))
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

  toggleUploadModal(show: boolean): void {
    this.showUploadModal.set(show);
    if (!show) {
      this.actionError.set(null);
    }
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
      this.actionError.set('Please select a file and provide a document title.');
      return;
    }

    this.isUploading.set(true);
    this.uploadStage.set(1);
    this.actionError.set(null);
    this.actionSuccess.set(null);
    this.showUploadModal.set(false);

    const stageTimer1 = setTimeout(() => this.uploadStage.set(2), 600);
    const stageTimer2 = setTimeout(() => this.uploadStage.set(3), 1300);
    const stageTimer3 = setTimeout(() => this.uploadStage.set(4), 2100);

    this.documentService.uploadDocument(
      this.selectedFile,
      this.uploadTitle,
      this.uploadType,
      this.uploadVersion,
      this.uploadSource,
      this.uploadPolicyCode ? this.uploadPolicyCode.trim().toUpperCase() : undefined,
      this.uploadJurisdiction ? this.uploadJurisdiction.trim().toUpperCase() : undefined
    ).subscribe({
      next: (created) => {
        clearTimeout(stageTimer1);
        clearTimeout(stageTimer2);
        clearTimeout(stageTimer3);
        this.isUploading.set(false);
        this.actionSuccess.set(`Document "${created.title}" successfully ingested and indexed into vector repository!`);
        this.selectedFile = null;
        this.uploadTitle = '';
        this.uploadSource = '';
        this.uploadPolicyCode = '';
        this.loadDocuments();
        this.loadPolicyFamilies();
      },
      error: (err) => {
        clearTimeout(stageTimer1);
        clearTimeout(stageTimer2);
        clearTimeout(stageTimer3);
        this.isUploading.set(false);
        this.actionError.set(err?.error?.detail || err?.error?.message || 'Document ingestion failed.');
      }
    });
  }

  deleteDocument(id: string, title: string): void {
    if (!confirm(`Are you sure you want to permanently delete compliance document "${title}"?`)) return;

    this.documentService.deleteDocument(id).subscribe({
      next: () => {
        this.documents.update(list => list.filter(d => d.id !== id));
        this.actionSuccess.set(`Deleted document "${title}".`);
        this.loadPolicyFamilies();
      },
      error: (err) => {
        this.actionError.set(err?.error?.detail || 'Failed to delete document.');
      }
    });
  }

  retryIngestion(id: string, title: string): void {
    this.actionError.set(null);
    this.actionSuccess.set(null);
    this.documentService.retryIngestion(id).subscribe({
      next: (job) => {
        this.actionSuccess.set(`Re-ingestion triggered for "${title}" (Job ID: ${job.id}, Status: ${job.status})`);
        this.loadDocuments();
      },
      error: (err) => {
        this.actionError.set(err?.error?.detail || err?.error?.message || 'Failed to retry ingestion');
      }
    });
  }

  openMetadataModal(doc: DocumentItem): void {
    this.selectedDoc.set(doc);
  }

  closeMetadataModal(): void {
    this.selectedDoc.set(null);
  }

  jumpToVersioning(policyCode: string): void {
    this.activeTab.set('versioning');
    this.selectPolicyFamily(policyCode);
  }

  // --- Regulatory Versioning Methods ---
  loadPolicyFamilies(): void {
    this.documentService.getPolicyFamilies().subscribe({
      next: (families) => {
        this.policyFamilies.set(families);
        if (families.length > 0 && !this.selectedPolicyCode()) {
          this.selectPolicyFamily(families[0].policyCode);
        }
      },
      error: (err) => {
        this.actionError.set(err?.error?.detail || 'Failed to load policy families.');
      }
    });
  }

  selectPolicyFamily(code: string): void {
    this.selectedPolicyCode.set(code);
    this.comparisonReport.set(null);
    this.baseDocId.set('');
    this.targetDocId.set('');

    this.documentService.getPolicyVersions(code).subscribe({
      next: (versions) => {
        this.policyVersions.set(versions);
        if (versions.length >= 2) {
          // Pre-select the latest two versions for convenience
          this.targetDocId.set(versions[0].id);
          this.baseDocId.set(versions[1].id);
        } else if (versions.length === 1) {
          this.targetDocId.set(versions[0].id);
        }
      },
      error: (err) => {
        this.actionError.set(err?.error?.detail || 'Failed to load policy versions.');
      }
    });
  }

  executeComparison(): void {
    if (!this.baseDocId() || !this.targetDocId()) return;

    this.isComparing.set(true);
    this.actionError.set(null);

    this.documentService.comparePolicies(this.baseDocId(), this.targetDocId()).subscribe({
      next: (report) => {
        this.comparisonReport.set(report);
        this.isComparing.set(false);
      },
      error: (err) => {
        this.isComparing.set(false);
        this.actionError.set(err?.error?.detail || 'Failed to generate policy comparison.');
      }
    });
  }

  getDiffNoteModel(): { [diffId: string]: string } {
    return this.diffNotesMap;
  }

  validateDiffItem(diffId: string, status: 'VALIDATED' | 'REJECTED'): void {
    const notes = this.diffNotesMap[diffId] || '';
    this.documentService.validateDiff(diffId, { status, notes }).subscribe({
      next: (updatedDiff) => {
        const currentReport = this.comparisonReport();
        if (currentReport) {
          const updatedDiffs = currentReport.diffs.map(d => d.id === diffId ? updatedDiff : d);
          const allValidated = updatedDiffs
            .filter(d => d.requiresHumanValidation)
            .every(d => d.humanValidationStatus === 'VALIDATED');
          this.comparisonReport.set({
            ...currentReport,
            diffs: updatedDiffs,
            allHumanValidated: allValidated
          });
        }
        this.actionSuccess.set(`Obligation diff marked ${status}.`);
      },
      error: (err) => {
        this.actionError.set(err?.error?.detail || 'Failed to record human validation.');
      }
    });
  }

  isTargetApprovable(): boolean {
    const rep = this.comparisonReport();
    if (!rep) return false;
    const target = this.policyVersions().find(v => v.id === rep.targetDocumentId);
    return target ? target.approvalStatus !== 'APPROVED' : false;
  }

  approveSelectedTargetVersion(): void {
    const rep = this.comparisonReport();
    if (!rep) return;

    if (!confirm(`Are you sure you want to approve version ${rep.targetVersion} of ${rep.policyCode}? This will mark it as active and supersede earlier versions.`)) {
      return;
    }

    this.documentService.approvePolicyVersion(rep.targetDocumentId).subscribe({
      next: (approved) => {
        this.actionSuccess.set(`Policy version ${approved.version} (${approved.policyCode}) approved and marked active!`);
        this.selectPolicyFamily(rep.policyCode);
        this.loadPolicyFamilies();
        this.loadNotifications();
      },
      error: (err) => {
        this.actionError.set(err?.error?.detail || 'Failed to approve policy version.');
      }
    });
  }

  // --- Notifications Methods ---
  loadNotifications(): void {
    this.documentService.getNotifications().subscribe({
      next: (notifs) => {
        this.notifications.set(notifs);
      },
      error: () => {}
    });
  }

  markAsRead(id: string): void {
    this.documentService.markNotificationRead(id).subscribe({
      next: () => {
        this.notifications.update(list => list.map(n => n.id === id ? { ...n, read: true } : n));
      }
    });
  }
}
