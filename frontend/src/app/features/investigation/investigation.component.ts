import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { InvestigationService } from '../../core/services/investigation.service';
import { AuthService } from '../../core/services/auth.service';
import {
  AmlAlert,
  AmlInvestigationDetail,
  InvestigationSummary,
  InvestigationExport,
  CreateAlertRequest
} from '../../core/models/investigation.models';

@Component({
  selector: 'app-investigation',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="investigation-container">
      <!-- Page Header -->
      <header class="page-header">
        <div>
          <div class="header-title-row">
            <h2>AML Investigation & Human Review Workspace</h2>
            <span class="badge badge-workspace">PHASE 5 COMPLIANT</span>
          </div>
          <p class="subtitle">
            Synthetic transaction monitoring surveillance, evidence-backed policy retrieval, AI draft assistance, and human-in-the-loop compliance decision governance.
          </p>
        </div>
        <div class="header-actions">
          <button type="button" class="btn-primary" (click)="openCreateAlertModal()">
            <span>+ Generate Synthetic Alert</span>
          </button>
          <button type="button" class="btn-secondary" (click)="refreshCurrentView()" [disabled]="isLoading()">
            <span>Refresh Workspace</span>
          </button>
        </div>
      </header>

      <!-- Legal Guardrail Banner -->
      <div class="legal-disclaimer-banner">
        <span class="banner-icon">⚖️</span>
        <div class="banner-text">
          <strong>Mandatory Compliance Guardrail:</strong>
          AI output serves strictly as analyst investigative assistance and does NOT constitute an automated SAR filing decision. Final regulatory reporting and account actions remain under authorized human control.
        </div>
      </div>

      <!-- Navigation Tabs -->
      <div class="tabs-nav">
        <button type="button" class="tab-btn" [class.active]="activeTab() === 'alerts'" (click)="setTab('alerts')">
          <span>Alerts Surveillance Queue</span>
          <span class="tab-counter">{{ alerts().length }}</span>
        </button>
        <button type="button" class="tab-btn" [class.active]="activeTab() === 'workspace'" (click)="setTab('workspace')" [disabled]="!selectedInvestigation() && !selectedAlert()">
          <span>Active Case Dossier</span>
          @if (selectedInvestigation()) {
            <span class="badge-status-pill" [class]="'status-' + selectedInvestigation()?.status?.toLowerCase()">
              {{ selectedInvestigation()?.status }}
            </span>
          }
        </button>
        <button type="button" class="tab-btn" [class.active]="activeTab() === 'history'" (click)="setTab('history')">
          <span>Investigation Registry</span>
          <span class="tab-counter">{{ investigations().length }}</span>
        </button>
      </div>

      <!-- Feedback / Error messages -->
      @if (errorMessage()) {
        <div class="toast-error">
          <span>{{ errorMessage() }}</span>
          <button type="button" class="btn-dismiss" (click)="errorMessage.set(null)">✕</button>
        </div>
      }
      @if (successMessage()) {
        <div class="toast-success">
          <span>{{ successMessage() }}</span>
          <button type="button" class="btn-dismiss" (click)="successMessage.set(null)">✕</button>
        </div>
      }

      <!-- ================================================================= -->
      <!-- TAB 1: ALERTS SURVEILLANCE QUEUE                                  -->
      <!-- ================================================================= -->
      @if (activeTab() === 'alerts') {
        <div class="alerts-tab-content">
          <div class="filters-bar">
            <input
              type="text"
              class="search-input"
              [(ngModel)]="searchQuery"
              placeholder="Search by customer, account, or alert reference..."
              (keyup.enter)="loadAlerts()"
            />
            <select class="filter-select" [(ngModel)]="filterSeverity" (change)="loadAlerts()">
              <option value="">All Severities</option>
              <option value="CRITICAL">Critical</option>
              <option value="HIGH">High</option>
              <option value="MEDIUM">Medium</option>
              <option value="LOW">Low</option>
            </select>
            <select class="filter-select" [(ngModel)]="filterStatus" (change)="loadAlerts()">
              <option value="">All Statuses</option>
              <option value="NEW">New</option>
              <option value="DRAFT_GENERATED">Draft Generated</option>
              <option value="IN_INVESTIGATION">In Investigation</option>
              <option value="APPROVED_FOR_FILING">Approved for Filing</option>
              <option value="CLOSED_FALSE_POSITIVE">Closed (False Positive)</option>
            </select>
            <button type="button" class="btn-filter" (click)="loadAlerts()">Filter</button>
          </div>

          <div class="table-card">
            <table class="data-table">
              <thead>
                <tr>
                  <th>Alert Reference</th>
                  <th>Scenario Code</th>
                  <th>Customer & Account</th>
                  <th>Total Amount</th>
                  <th>Severity</th>
                  <th>Status</th>
                  <th>Triggered Date</th>
                  <th>Action</th>
                </tr>
              </thead>
              <tbody>
                @for (alert of alerts(); track alert.id) {
                  <tr>
                    <td>
                      <code class="ref-code">{{ alert.alertReference }}</code>
                    </td>
                    <td>
                      <div class="scenario-cell">
                        <span class="scenario-code">{{ alert.scenarioCode }}</span>
                        <span class="scenario-title">{{ alert.scenarioTitle }}</span>
                      </div>
                    </td>
                    <td>
                      <div class="cust-cell">
                        <span class="cust-name">{{ alert.customerName }}</span>
                        <span class="cust-acc">{{ alert.accountNumber }} • Risk: <strong>{{ alert.customerRiskLevel }}</strong></span>
                      </div>
                    </td>
                    <td>
                      <span class="amount-val">{{ alert.totalAlertAmount | currency:alert.currency:'symbol':'1.2-2' }}</span>
                    </td>
                    <td>
                      <span class="badge-severity" [class]="'sev-' + alert.severity.toLowerCase()">{{ alert.severity }}</span>
                    </td>
                    <td>
                      <span class="badge-status" [class]="'st-' + alert.status.toLowerCase()">{{ alert.status }}</span>
                    </td>
                    <td>
                      <span class="date-txt">{{ alert.triggeredAt | date:'medium' }}</span>
                    </td>
                    <td>
                      @if (alert.investigationId) {
                        <button type="button" class="btn-table-action btn-open-inv" (click)="openInvestigation(alert.investigationId)">
                          Open Dossier
                        </button>
                      } @else {
                        <button type="button" class="btn-table-action btn-analyze" (click)="startInvestigation(alert.id)" [disabled]="isAnalyzing()">
                          @if (isAnalyzing() && analyzingAlertId === alert.id) {
                            <span>Analyzing...</span>
                          } @else {
                            <span>Investigate</span>
                          }
                        </button>
                      }
                    </td>
                  </tr>
                } @empty {
                  <tr>
                    <td colspan="8" class="text-center empty-cell">No transaction monitoring alerts found matching current filters.</td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        </div>
      }

      <!-- ================================================================= -->
      <!-- TAB 2: ACTIVE INVESTIGATION DOSSIER & WORKSPACE                   -->
      <!-- ================================================================= -->
      @if (activeTab() === 'workspace' && selectedInvestigation()) {
        <div class="workspace-grid">
          <!-- Left Column: Case Information & Evidence -->
          <div class="case-meta-column">
            <!-- Subject Card -->
            <div class="card subject-card">
              <div class="card-header">
                <h3>Subject & Account Profile</h3>
                <span class="badge-status" [class]="'st-' + selectedInvestigation()?.status?.toLowerCase()">
                  {{ selectedInvestigation()?.status }}
                </span>
              </div>
              <div class="profile-details">
                <div class="profile-row">
                  <span class="lbl">Customer:</span>
                  <strong class="val">{{ selectedInvestigation()?.alert?.customerName }}</strong>
                </div>
                <div class="profile-row">
                  <span class="lbl">Customer ID:</span>
                  <code class="val">{{ selectedInvestigation()?.alert?.customerId }}</code>
                </div>
                <div class="profile-row">
                  <span class="lbl">Account Number:</span>
                  <code class="val">{{ selectedInvestigation()?.alert?.accountNumber }}</code>
                </div>
                <div class="profile-row">
                  <span class="lbl">Risk Classification:</span>
                  <span class="badge-risk" [class]="'risk-' + selectedInvestigation()?.alert?.customerRiskLevel?.toLowerCase()">
                    {{ selectedInvestigation()?.alert?.customerRiskLevel }}
                  </span>
                </div>
                <div class="profile-row">
                  <span class="lbl">Flagged Volume:</span>
                  <span class="amount-val-highlight">
                    {{ selectedInvestigation()?.alert?.totalAlertAmount | currency:selectedInvestigation()?.alert?.currency:'symbol':'1.2-2' }}
                  </span>
                </div>
                <div class="profile-row">
                  <span class="lbl">Assigned Analyst:</span>
                  <span class="val">{{ selectedInvestigation()?.assignedAnalyst || 'Unassigned' }}</span>
                </div>
                <div class="profile-row">
                  <span class="lbl">Reviewer:</span>
                  <span class="val">{{ selectedInvestigation()?.reviewer || 'Pending' }}</span>
                </div>
              </div>
            </div>

            <!-- Transaction History Card -->
            <div class="card txns-card">
              <div class="card-header">
                <h3>Flagged Transaction Surveillance ({{ selectedInvestigation()?.alert?.transactions?.length || 0 }})</h3>
              </div>
              <div class="txn-table-wrapper">
                <table class="mini-table">
                  <thead>
                    <tr>
                      <th>Ref & Date</th>
                      <th>Type & Counterparty</th>
                      <th>Amount</th>
                    </tr>
                  </thead>
                  <tbody>
                    @for (t of selectedInvestigation()?.alert?.transactions; track t.id) {
                      <tr [class.suspicious-row]="t.suspiciousIndicator">
                        <td>
                          <code>{{ t.transactionReference }}</code>
                          <div class="sub-date">{{ t.transactionDate | date:'short' }}</div>
                        </td>
                        <td>
                          <div class="txn-type">{{ t.transactionType }}</div>
                          <div class="counterparty">{{ t.counterpartyName }} ({{ t.counterpartyCountry }})</div>
                          @if (t.narrative) {
                            <div class="memo-txt">"{{ t.narrative }}"</div>
                          }
                        </td>
                        <td>
                          <strong [class.text-danger]="t.suspiciousIndicator">
                            {{ t.amount | currency:t.currency:'symbol':'1.2-2' }}
                          </strong>
                          @if (t.suspiciousIndicator) {
                            <span class="flag-tag">FLAGGED</span>
                          }
                        </td>
                      </tr>
                    }
                  </tbody>
                </table>
              </div>
            </div>

            <!-- Supporting Policy Citations -->
            <div class="card policies-card">
              <div class="card-header">
                <h3>Applicable Bank AML Policy Citations</h3>
                <span class="evidence-quality-badge" [class]="'ev-' + selectedInvestigation()?.evidenceLevel?.toLowerCase()">
                  {{ selectedInvestigation()?.evidenceLevel }} CONFIDENCE ({{ ((selectedInvestigation()?.evidenceQualityScore || 0) * 100).toFixed(0) }}%)
                </span>
              </div>
              <div class="policies-list">
                @for (pol of selectedInvestigation()?.linkedPolicies; track pol.section) {
                  <div class="policy-citation-box">
                    <div class="pol-top">
                      <span class="pol-code">{{ pol.policyCode }}</span>
                      <span class="pol-title">{{ pol.documentTitle }}</span>
                    </div>
                    <div class="pol-sec">{{ pol.section }}</div>
                    <p class="pol-snip">{{ pol.snippet }}</p>
                  </div>
                }
              </div>
            </div>
          </div>

          <!-- Right Column: Narrative Draft, Decisions, Timeline -->
          <div class="dossier-actions-column">
            <!-- AI Evidence Summary -->
            <div class="card ai-summary-card">
              <div class="card-header">
                <h3>Evidence-Backed Investigation Summary</h3>
                <div class="recommendation-pill" [class]="'rec-' + selectedInvestigation()?.decisionRecommendation?.toLowerCase()">
                  AI REC: {{ selectedInvestigation()?.decisionRecommendation }}
                </div>
              </div>
              <p class="summary-p">{{ selectedInvestigation()?.aiSummary }}</p>

              @if (selectedInvestigation()?.policyRelevanceExplanation) {
                <div class="red-flags-block">
                  <div class="pre-markdown">{{ selectedInvestigation()?.policyRelevanceExplanation }}</div>
                </div>
              }
            </div>

            <!-- Editable SAR Narrative Draft Card -->
            <div class="card draft-card">
              <div class="card-header">
                <h3>Suspicious Activity Report (SAR) Narrative Draft</h3>
                <span class="badge-draft">HUMAN-IN-THE-LOOP EDITABLE</span>
              </div>
              <p class="section-guidance">
                Review and amend the 5-section narrative draft below. Edits are recorded in the non-repudiation audit trail.
              </p>
              <textarea
                class="narrative-textarea"
                rows="14"
                [(ngModel)]="editableDraft"
                placeholder="SAR narrative draft content..."
              ></textarea>

              <div class="draft-action-row">
                <button type="button" class="btn-save-draft" (click)="saveDraftEdits()" [disabled]="isSavingDraft()">
                  <span>Save Narrative Amendments</span>
                </button>
                <button type="button" class="btn-export-dossier" (click)="exportDossierReport()">
                  <span>Export Investigation Dossier</span>
                </button>
              </div>
            </div>

            <!-- Analyst Review & Decision Panel -->
            <div class="card analyst-panel-card">
              <div class="card-header">
                <h3>Compliance Analyst Decision Disposition</h3>
                @if (selectedInvestigation()?.analystDecision) {
                  <span class="badge-decision">{{ selectedInvestigation()?.analystDecision }}</span>
                }
              </div>
              <div class="analyst-inputs">
                <label for="analystNotes">Analyst Review Notes & Findings:</label>
                <textarea
                  id="analystNotes"
                  rows="3"
                  class="notes-textarea"
                  [(ngModel)]="analystNotes"
                  placeholder="Record your investigative rationale, branch inquiries, UBO verification notes, or reason for disposition..."
                ></textarea>

                <div class="analyst-actions-grid">
                  <button type="button" class="btn-decision btn-accept" (click)="submitAnalystDecision('ACCEPTED')">
                    Accept & Submit for Review
                  </button>
                  <button type="button" class="btn-decision btn-edit-submit" (click)="submitAnalystDecision('EDITED')">
                    Submit Edited Draft for Review
                  </button>
                  <button type="button" class="btn-decision btn-reject" (click)="submitAnalystDecision('REJECTED')">
                    Close as False Positive
                  </button>
                  <button type="button" class="btn-decision btn-regen" (click)="submitAnalystDecision('REGENERATE_REQUESTED')">
                    Request AI Re-Analysis
                  </button>
                </div>
              </div>
            </div>

            <!-- Reviewer Approval & Escalation Panel (For ADMIN / Reviewer) -->
            @if (canReview()) {
              <div class="card reviewer-panel-card">
                <div class="card-header">
                  <h3>Compliance Officer / Reviewer Authorization</h3>
                  @if (selectedInvestigation()?.reviewerDecision) {
                    <span class="badge-decision">{{ selectedInvestigation()?.reviewerDecision }}</span>
                  }
                </div>
                <div class="reviewer-inputs">
                  <label for="reviewerNotes">Reviewing Officer Concurrence Notes:</label>
                  <textarea
                    id="reviewerNotes"
                    rows="2"
                    class="notes-textarea"
                    [(ngModel)]="reviewerNotes"
                    placeholder="Reviewer concurrence, filing authorization rationale, or return instructions..."
                  ></textarea>

                  <label for="escalationReason">Escalation Rationale (Required for MLRO Escalation):</label>
                  <input
                    id="escalationReason"
                    type="text"
                    class="single-input"
                    [(ngModel)]="escalationReason"
                    placeholder="e.g. Involves foreign politically exposed person requiring executive clearance"
                  />

                  <div class="reviewer-actions-grid">
                    <button type="button" class="btn-rev btn-rev-approve" (click)="submitReviewerDecision('APPROVED')">
                      Approve for Regulatory Filing
                    </button>
                    <button type="button" class="btn-rev btn-rev-return" (click)="submitReviewerDecision('RETURNED_FOR_REVISION')">
                      Return for Analyst Revision
                    </button>
                    <button type="button" class="btn-rev btn-rev-escalate" (click)="submitReviewerDecision('ESCALATED_TO_MLRO')">
                      Escalate to MLRO
                    </button>
                    <button type="button" class="btn-rev btn-rev-reject" (click)="submitReviewerDecision('REJECTED')">
                      Reject & Close Case
                    </button>
                  </div>
                </div>
              </div>
            }

            <!-- Investigation Timeline & Cryptographic Audit Hashes -->
            <div class="card timeline-card">
              <div class="card-header">
                <h3>Case Audit Trail & Decision History</h3>
              </div>
              <div class="timeline-list">
                @for (entry of selectedInvestigation()?.timeline; track entry.id) {
                  <div class="timeline-item">
                    <div class="tl-bullet"></div>
                    <div class="tl-content">
                      <div class="tl-header">
                        <span class="tl-action">{{ entry.action }}</span>
                        <span class="tl-by">by {{ entry.performedBy }}</span>
                        <span class="tl-time">{{ entry.recordedAt | date:'medium' }}</span>
                      </div>
                      @if (entry.notes) {
                        <p class="tl-notes">{{ entry.notes }}</p>
                      }
                      @if (entry.previousStatus && entry.newStatus) {
                        <div class="tl-status-shift">
                          Status: <code>{{ entry.previousStatus }}</code> ➔ <code>{{ entry.newStatus }}</code>
                        </div>
                      }
                    </div>
                  </div>
                }
              </div>

              @if (selectedInvestigation()?.linkedAuditHashes && (selectedInvestigation()?.linkedAuditHashes)!.length > 0) {
                <div class="audit-hashes-box">
                  <h4>Cryptographic SHA-256 Audit Hashes Linked:</h4>
                  @for (hash of selectedInvestigation()?.linkedAuditHashes; track hash) {
                    <div class="hash-item">
                      <code>{{ hash }}</code>
                    </div>
                  }
                </div>
              }
            </div>
          </div>
        </div>
      }

      <!-- ================================================================= -->
      <!-- TAB 3: INVESTIGATION REGISTRY                                     -->
      <!-- ================================================================= -->
      @if (activeTab() === 'history') {
        <div class="history-tab-content">
          <div class="table-card">
            <table class="data-table">
              <thead>
                <tr>
                  <th>Case Ref</th>
                  <th>Alert Reference</th>
                  <th>Customer Name</th>
                  <th>Flagged Amount</th>
                  <th>Status</th>
                  <th>Analyst</th>
                  <th>AI Recommendation</th>
                  <th>Analyst Decision</th>
                  <th>Reviewer Disposition</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                @for (inv of investigations(); track inv.id) {
                  <tr>
                    <td><code>{{ inv.investigationReference }}</code></td>
                    <td><code>{{ inv.alertReference }}</code></td>
                    <td><strong>{{ inv.customerName }}</strong></td>
                    <td>{{ inv.totalAlertAmount | currency:'USD':'symbol':'1.2-2' }}</td>
                    <td>
                      <span class="badge-status" [class]="'st-' + inv.status.toLowerCase()">{{ inv.status }}</span>
                    </td>
                    <td>{{ inv.assignedAnalyst || 'Unassigned' }}</td>
                    <td>
                      <span class="rec-tag">{{ inv.decisionRecommendation }}</span>
                    </td>
                    <td>{{ inv.analystDecision || 'Pending' }}</td>
                    <td>{{ inv.reviewerDecision || 'Pending' }}</td>
                    <td>
                      <button type="button" class="btn-table-action btn-open-inv" (click)="openInvestigation(inv.id)">
                        View Dossier
                      </button>
                    </td>
                  </tr>
                } @empty {
                  <tr>
                    <td colspan="10" class="text-center empty-cell">No completed or recorded investigations found.</td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        </div>
      }

      <!-- ================================================================= -->
      <!-- MODAL: GENERATE SYNTHETIC ALERT                                   -->
      <!-- ================================================================= -->
      @if (isCreateModalOpen()) {
        <div class="modal-backdrop" (click)="closeCreateAlertModal()">
          <div class="modal-dialog" (click)="$event.stopPropagation()">
            <div class="modal-header">
              <h3>Generate Synthetic Transaction Monitoring Alert</h3>
              <button type="button" class="btn-close" (click)="closeCreateAlertModal()">✕</button>
            </div>
            <div class="modal-body">
              <div class="form-group">
                <label>Scenario Template:</label>
                <select class="form-control" [(ngModel)]="newAlert.scenarioCode" (change)="onScenarioChange()">
                  <option value="TM-RULE-101">Structuring Cash Deposits Below CTR Threshold (TM-RULE-101)</option>
                  <option value="ALRT_HVT_01">High-Value Outbound Offshore Wire Transfer (ALRT_HVT_01)</option>
                  <option value="SANCTION-PEP-03">Rapid Velocity Funds Involving PEP Enterprise (SANCTION-PEP-03)</option>
                  <option value="TM-RULE-204">Sudden Velocity & Volume Surge on Retail Account (TM-RULE-204)</option>
                </select>
              </div>

              <div class="form-group">
                <label>Scenario Title:</label>
                <input type="text" class="form-control" [(ngModel)]="newAlert.scenarioTitle" />
              </div>

              <div class="form-row">
                <div class="form-group col">
                  <label>Customer Name:</label>
                  <input type="text" class="form-control" [(ngModel)]="newAlert.customerName" />
                </div>
                <div class="form-group col">
                  <label>Customer ID:</label>
                  <input type="text" class="form-control" [(ngModel)]="newAlert.customerId" />
                </div>
              </div>

              <div class="form-row">
                <div class="form-group col">
                  <label>Account Number:</label>
                  <input type="text" class="form-control" [(ngModel)]="newAlert.accountNumber" />
                </div>
                <div class="form-group col">
                  <label>Customer Risk Level:</label>
                  <select class="form-control" [(ngModel)]="newAlert.customerRiskLevel">
                    <option value="HIGH">High</option>
                    <option value="MEDIUM">Medium</option>
                    <option value="LOW">Low</option>
                  </select>
                </div>
              </div>

              <div class="form-row">
                <div class="form-group col">
                  <label>Total Flagged Amount (USD):</label>
                  <input type="number" class="form-control" [(ngModel)]="newAlert.totalAlertAmount" />
                </div>
                <div class="form-group col">
                  <label>Severity:</label>
                  <select class="form-control" [(ngModel)]="newAlert.severity">
                    <option value="CRITICAL">Critical</option>
                    <option value="HIGH">High</option>
                    <option value="MEDIUM">Medium</option>
                  </select>
                </div>
              </div>
            </div>
            <div class="modal-footer">
              <button type="button" class="btn-secondary" (click)="closeCreateAlertModal()">Cancel</button>
              <button type="button" class="btn-primary" (click)="submitCreateAlert()" [disabled]="isCreatingAlert()">
                <span>Create Synthetic Alert</span>
              </button>
            </div>
          </div>
        </div>
      }

      <!-- ================================================================= -->
      <!-- MODAL: EXPORT DOSSIER REPORT                                      -->
      <!-- ================================================================= -->
      @if (exportData()) {
        <div class="modal-backdrop" (click)="exportData.set(null)">
          <div class="modal-dialog modal-lg" (click)="$event.stopPropagation()">
            <div class="modal-header">
              <h3>Investigation Compliance Report Dossier</h3>
              <button type="button" class="btn-close" (click)="exportData.set(null)">✕</button>
            </div>
            <div class="modal-body">
              <div class="export-meta-strip">
                <span>Case: <code>{{ exportData()?.investigationReference }}</code></span>
                <span>Exported By: <strong>{{ exportData()?.exportedBy }}</strong></span>
                <span>Timestamp: {{ exportData()?.exportedAt | date:'medium' }}</span>
              </div>
              <pre class="export-preview">{{ exportData()?.markdownContent }}</pre>
            </div>
            <div class="modal-footer">
              <button type="button" class="btn-secondary" (click)="downloadReportMarkdown()">Download .md Dossier</button>
              <button type="button" class="btn-primary" (click)="exportData.set(null)">Close</button>
            </div>
          </div>
        </div>
      }
    </div>
  `,
  styles: [`
    .investigation-container {
      padding: 1.5rem;
      max-width: 1440px;
      margin: 0 auto;
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
      color: #0f172a;
    }
    .page-header {
      display: flex;
      justify-content: space-between;
      align-items: flex-start;
      margin-bottom: 1.25rem;
    }
    .header-title-row {
      display: flex;
      align-items: center;
      gap: 0.75rem;
    }
    .header-title-row h2 {
      margin: 0;
      font-size: 1.5rem;
      font-weight: 700;
      color: #0f172a;
    }
    .badge-workspace {
      background: #e0f2fe;
      color: #0369a1;
      padding: 0.25rem 0.5rem;
      border-radius: 4px;
      font-size: 0.75rem;
      font-weight: 700;
    }
    .subtitle {
      margin: 0.25rem 0 0;
      color: #64748b;
      font-size: 0.875rem;
    }
    .header-actions {
      display: flex;
      gap: 0.5rem;
    }
    .btn-primary {
      background: #1e3a8a;
      color: #ffffff;
      border: none;
      padding: 0.5rem 1rem;
      border-radius: 6px;
      font-weight: 600;
      font-size: 0.875rem;
      cursor: pointer;
    }
    .btn-primary:hover {
      background: #1e40af;
    }
    .btn-secondary {
      background: #ffffff;
      color: #334155;
      border: 1px solid #cbd5e1;
      padding: 0.5rem 1rem;
      border-radius: 6px;
      font-weight: 600;
      font-size: 0.875rem;
      cursor: pointer;
    }
    .btn-secondary:hover {
      background: #f8fafc;
    }

    /* Legal Banner */
    .legal-disclaimer-banner {
      display: flex;
      align-items: center;
      gap: 0.75rem;
      background: #fef3c7;
      border-left: 4px solid #f59e0b;
      padding: 0.75rem 1rem;
      border-radius: 4px;
      margin-bottom: 1.25rem;
      font-size: 0.8125rem;
      color: #78350f;
    }
    .banner-icon {
      font-size: 1.25rem;
    }

    /* Tabs */
    .tabs-nav {
      display: flex;
      gap: 0.5rem;
      border-bottom: 2px solid #e2e8f0;
      margin-bottom: 1.25rem;
    }
    .tab-btn {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      background: transparent;
      border: none;
      border-bottom: 2px solid transparent;
      margin-bottom: -2px;
      padding: 0.75rem 1rem;
      font-size: 0.875rem;
      font-weight: 600;
      color: #64748b;
      cursor: pointer;
    }
    .tab-btn.active {
      color: #1e3a8a;
      border-bottom-color: #1e3a8a;
    }
    .tab-counter {
      background: #e2e8f0;
      color: #334155;
      padding: 0.125rem 0.375rem;
      border-radius: 9999px;
      font-size: 0.75rem;
    }
    .badge-status-pill {
      font-size: 0.7rem;
      padding: 0.125rem 0.375rem;
      border-radius: 4px;
      text-transform: uppercase;
      font-weight: 700;
      background: #f1f5f9;
      color: #475569;
    }
    .badge-status-pill.status-approved {
      background: #dcfce7;
      color: #166534;
    }
    .badge-status-pill.status-draft_ready {
      background: #dbeafe;
      color: #1e40af;
    }

    /* Toast */
    .toast-error {
      background: #fee2e2;
      color: #991b1b;
      padding: 0.75rem 1rem;
      border-radius: 6px;
      margin-bottom: 1rem;
      display: flex;
      justify-content: space-between;
      align-items: center;
    }
    .toast-success {
      background: #dcfce7;
      color: #166534;
      padding: 0.75rem 1rem;
      border-radius: 6px;
      margin-bottom: 1rem;
      display: flex;
      justify-content: space-between;
      align-items: center;
    }
    .btn-dismiss {
      background: none;
      border: none;
      color: inherit;
      cursor: pointer;
      font-size: 1rem;
    }

    /* Filters */
    .filters-bar {
      display: flex;
      gap: 0.5rem;
      margin-bottom: 1rem;
    }
    .search-input {
      flex: 1;
      padding: 0.5rem 0.75rem;
      border: 1px solid #cbd5e1;
      border-radius: 6px;
      font-size: 0.875rem;
    }
    .filter-select {
      padding: 0.5rem 0.75rem;
      border: 1px solid #cbd5e1;
      border-radius: 6px;
      font-size: 0.875rem;
      background: #ffffff;
    }
    .btn-filter {
      padding: 0.5rem 1rem;
      background: #334155;
      color: #ffffff;
      border: none;
      border-radius: 6px;
      font-weight: 600;
      font-size: 0.875rem;
      cursor: pointer;
    }

    /* Tables */
    .table-card {
      background: #ffffff;
      border: 1px solid #e2e8f0;
      border-radius: 8px;
      overflow-x: auto;
    }
    .data-table {
      width: 100%;
      border-collapse: collapse;
      text-align: left;
      font-size: 0.875rem;
    }
    .data-table th {
      background: #f8fafc;
      padding: 0.75rem 1rem;
      font-weight: 600;
      color: #475569;
      border-bottom: 1px solid #e2e8f0;
    }
    .data-table td {
      padding: 0.75rem 1rem;
      border-bottom: 1px solid #f1f5f9;
      vertical-align: middle;
    }
    .empty-cell {
      padding: 2rem !important;
      color: #64748b;
    }
    .ref-code {
      background: #f1f5f9;
      padding: 0.2rem 0.4rem;
      border-radius: 4px;
      color: #1e3a8a;
      font-weight: 600;
    }
    .scenario-cell {
      display: flex;
      flex-direction: column;
    }
    .scenario-code {
      font-weight: 600;
      color: #0f172a;
    }
    .scenario-title {
      font-size: 0.75rem;
      color: #64748b;
    }
    .cust-cell {
      display: flex;
      flex-direction: column;
    }
    .cust-name {
      font-weight: 600;
      color: #0f172a;
    }
    .cust-acc {
      font-size: 0.75rem;
      color: #64748b;
    }
    .amount-val {
      font-weight: 700;
      color: #0f172a;
    }
    .badge-severity {
      padding: 0.2rem 0.5rem;
      border-radius: 4px;
      font-size: 0.75rem;
      font-weight: 700;
      text-transform: uppercase;
    }
    .sev-critical { background: #fee2e2; color: #991b1b; }
    .sev-high { background: #ffedd5; color: #9a3412; }
    .sev-medium { background: #fef3c7; color: #b45309; }
    .sev-low { background: #f1f5f9; color: #475569; }

    .badge-status {
      padding: 0.2rem 0.5rem;
      border-radius: 4px;
      font-size: 0.75rem;
      font-weight: 600;
    }
    .st-new { background: #e0e7ff; color: #3730a3; }
    .st-draft_ready { background: #dbeafe; color: #1e40af; }
    .st-in_investigation { background: #fef3c7; color: #b45309; }
    .st-approved_for_filing, .st-approved { background: #dcfce7; color: #166534; }
    .st-escalated { background: #fae8ff; color: #86198f; }
    .st-closed_false_positive, .st-rejected { background: #f1f5f9; color: #475569; }

    .btn-table-action {
      padding: 0.35rem 0.75rem;
      border-radius: 4px;
      font-size: 0.75rem;
      font-weight: 600;
      cursor: pointer;
      border: 1px solid transparent;
    }
    .btn-analyze {
      background: #1e3a8a;
      color: #ffffff;
    }
    .btn-open-inv {
      background: #f8fafc;
      border-color: #cbd5e1;
      color: #1e3a8a;
    }

    /* Workspace 2-Column Grid */
    .workspace-grid {
      display: grid;
      grid-template-columns: 420px 1fr;
      gap: 1.25rem;
    }
    .case-meta-column, .dossier-actions-column {
      display: flex;
      flex-direction: column;
      gap: 1.25rem;
    }
    .card {
      background: #ffffff;
      border: 1px solid #e2e8f0;
      border-radius: 8px;
      padding: 1.25rem;
    }
    .card-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 1rem;
    }
    .card-header h3 {
      margin: 0;
      font-size: 1rem;
      font-weight: 700;
      color: #0f172a;
    }

    /* Profile Details */
    .profile-details {
      display: flex;
      flex-direction: column;
      gap: 0.5rem;
      font-size: 0.875rem;
    }
    .profile-row {
      display: flex;
      justify-content: space-between;
      border-bottom: 1px dashed #f1f5f9;
      padding-bottom: 0.35rem;
    }
    .profile-row .lbl { color: #64748b; }
    .amount-val-highlight { font-size: 1.125rem; font-weight: 800; color: #047857; }
    .badge-risk {
      padding: 0.15rem 0.4rem;
      border-radius: 4px;
      font-size: 0.75rem;
      font-weight: 700;
    }
    .risk-high { background: #fee2e2; color: #991b1b; }
    .risk-medium { background: #fef3c7; color: #92400e; }
    .risk-low { background: #ecfdf5; color: #065f46; }

    /* Transactions Mini Table */
    .txn-table-wrapper {
      max-height: 280px;
      overflow-y: auto;
    }
    .mini-table {
      width: 100%;
      border-collapse: collapse;
      font-size: 0.75rem;
    }
    .mini-table th { background: #f8fafc; padding: 0.5rem; text-align: left; }
    .mini-table td { padding: 0.5rem; border-bottom: 1px solid #f1f5f9; }
    .suspicious-row { background: #fffbeb; }
    .sub-date { color: #64748b; font-size: 0.7rem; }
    .counterparty { font-weight: 600; color: #0f172a; }
    .memo-txt { color: #64748b; font-style: italic; }
    .flag-tag {
      background: #ef4444;
      color: #ffffff;
      padding: 0.1rem 0.25rem;
      border-radius: 2px;
      font-size: 0.65rem;
      margin-left: 0.25rem;
    }

    /* Policy Citations */
    .policies-list {
      display: flex;
      flex-direction: column;
      gap: 0.75rem;
    }
    .policy-citation-box {
      background: #f8fafc;
      border-left: 3px solid #1e3a8a;
      padding: 0.75rem;
      border-radius: 4px;
    }
    .pol-top { display: flex; gap: 0.5rem; font-weight: 700; font-size: 0.8125rem; }
    .pol-code { color: #1e3a8a; }
    .pol-sec { font-size: 0.75rem; color: #475569; font-weight: 600; margin: 0.25rem 0; }
    .pol-snip { font-size: 0.75rem; color: #64748b; margin: 0; }
    .evidence-quality-badge {
      font-size: 0.75rem;
      padding: 0.2rem 0.5rem;
      border-radius: 4px;
      font-weight: 700;
    }
    .ev-high { background: #dcfce7; color: #166534; }
    .ev-medium { background: #fef3c7; color: #b45309; }
    .ev-low { background: #fee2e2; color: #991b1b; }

    /* AI Summary & Narrative */
    .recommendation-pill {
      font-size: 0.75rem;
      padding: 0.25rem 0.6rem;
      border-radius: 4px;
      font-weight: 700;
    }
    .rec-file_sar { background: #fee2e2; color: #991b1b; }
    .rec-request_edd { background: #fef3c7; color: #92400e; }
    .rec-close_no_sar { background: #ecfdf5; color: #065f46; }

    .summary-p { font-size: 0.875rem; line-height: 1.5; color: #334155; margin-bottom: 0.75rem; }
    .red-flags-block {
      background: #fffbeb;
      border: 1px solid #fef3c7;
      border-radius: 6px;
      padding: 0.75rem;
      font-size: 0.8125rem;
    }
    .pre-markdown { white-space: pre-line; line-height: 1.4; color: #78350f; }

    .badge-draft {
      background: #e2e8f0;
      color: #334155;
      padding: 0.2rem 0.5rem;
      border-radius: 4px;
      font-size: 0.75rem;
      font-weight: 700;
    }
    .section-guidance { font-size: 0.8125rem; color: #64748b; margin-bottom: 0.5rem; }
    .narrative-textarea {
      width: 100%;
      box-sizing: border-box;
      border: 1px solid #cbd5e1;
      border-radius: 6px;
      padding: 0.75rem;
      font-family: ui-monospace, Menlo, Monaco, Consolas, monospace;
      font-size: 0.8125rem;
      line-height: 1.45;
      background: #fafafa;
    }
    .draft-action-row {
      display: flex;
      justify-content: space-between;
      margin-top: 0.75rem;
    }
    .btn-save-draft {
      background: #0284c7;
      color: #ffffff;
      border: none;
      padding: 0.5rem 1rem;
      border-radius: 6px;
      font-weight: 600;
      font-size: 0.875rem;
      cursor: pointer;
    }
    .btn-export-dossier {
      background: #334155;
      color: #ffffff;
      border: none;
      padding: 0.5rem 1rem;
      border-radius: 6px;
      font-weight: 600;
      font-size: 0.875rem;
      cursor: pointer;
    }

    /* Analyst & Reviewer Panels */
    .analyst-inputs, .reviewer-inputs {
      display: flex;
      flex-direction: column;
      gap: 0.5rem;
    }
    .notes-textarea {
      width: 100%;
      box-sizing: border-box;
      border: 1px solid #cbd5e1;
      border-radius: 6px;
      padding: 0.5rem;
      font-size: 0.875rem;
    }
    .single-input {
      width: 100%;
      box-sizing: border-box;
      border: 1px solid #cbd5e1;
      border-radius: 6px;
      padding: 0.5rem;
      font-size: 0.875rem;
    }
    .analyst-actions-grid, .reviewer-actions-grid {
      display: grid;
      grid-template-columns: repeat(2, 1fr);
      gap: 0.5rem;
      margin-top: 0.5rem;
    }
    .btn-decision, .btn-rev {
      padding: 0.6rem 1rem;
      border: none;
      border-radius: 6px;
      font-size: 0.8125rem;
      font-weight: 600;
      cursor: pointer;
    }
    .btn-accept { background: #166534; color: #ffffff; }
    .btn-edit-submit { background: #0284c7; color: #ffffff; }
    .btn-reject { background: #475569; color: #ffffff; }
    .btn-regen { background: #e2e8f0; color: #0f172a; }

    .btn-rev-approve { background: #15803d; color: #ffffff; }
    .btn-rev-return { background: #d97706; color: #ffffff; }
    .btn-rev-escalate { background: #7e22ce; color: #ffffff; }
    .btn-rev-reject { background: #dc2626; color: #ffffff; }

    /* Timeline */
    .timeline-list {
      display: flex;
      flex-direction: column;
      gap: 0.75rem;
    }
    .timeline-item {
      display: flex;
      gap: 0.75rem;
      font-size: 0.8125rem;
    }
    .tl-bullet {
      width: 10px;
      height: 10px;
      border-radius: 50%;
      background: #1e3a8a;
      margin-top: 0.25rem;
      flex-shrink: 0;
    }
    .tl-content { flex: 1; }
    .tl-header { display: flex; gap: 0.5rem; font-weight: 600; }
    .tl-action { color: #1e3a8a; }
    .tl-by { color: #64748b; }
    .tl-time { color: #94a3b8; font-size: 0.75rem; margin-left: auto; }
    .tl-notes { margin: 0.25rem 0; color: #334155; }
    .tl-status-shift code { background: #f1f5f9; padding: 0.1rem 0.3rem; border-radius: 2px; }

    .audit-hashes-box {
      margin-top: 1rem;
      padding-top: 0.75rem;
      border-top: 1px dashed #e2e8f0;
    }
    .audit-hashes-box h4 { margin: 0 0 0.5rem; font-size: 0.8125rem; color: #475569; }
    .hash-item code {
      font-size: 0.7rem;
      color: #0369a1;
      display: block;
      margin-bottom: 0.25rem;
      word-break: break-all;
    }

    /* Modals */
    .modal-backdrop {
      position: fixed;
      inset: 0;
      background: rgba(15, 23, 42, 0.6);
      display: flex;
      align-items: center;
      justify-content: center;
      z-index: 9999;
    }
    .modal-dialog {
      background: #ffffff;
      border-radius: 8px;
      width: 90%;
      max-width: 650px;
      max-height: 90vh;
      overflow-y: auto;
      box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.1);
    }
    .modal-lg { max-width: 900px; }
    .modal-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 1rem 1.25rem;
      border-bottom: 1px solid #e2e8f0;
    }
    .modal-header h3 { margin: 0; font-size: 1.125rem; font-weight: 700; }
    .btn-close { background: none; border: none; font-size: 1.25rem; cursor: pointer; color: #64748b; }
    .modal-body { padding: 1.25rem; font-size: 0.875rem; }
    .modal-footer {
      display: flex;
      justify-content: flex-end;
      gap: 0.5rem;
      padding: 1rem 1.25rem;
      border-top: 1px solid #e2e8f0;
      background: #f8fafc;
    }
    .form-group { margin-bottom: 0.75rem; display: flex; flex-direction: column; gap: 0.25rem; }
    .form-group label { font-weight: 600; color: #334155; font-size: 0.8125rem; }
    .form-control {
      padding: 0.5rem;
      border: 1px solid #cbd5e1;
      border-radius: 6px;
      font-size: 0.875rem;
    }
    .form-row { display: flex; gap: 0.75rem; }
    .form-row .col { flex: 1; }
    .export-preview {
      background: #f8fafc;
      border: 1px solid #e2e8f0;
      padding: 1rem;
      border-radius: 6px;
      font-family: ui-monospace, Menlo, Monaco, Consolas, monospace;
      font-size: 0.75rem;
      white-space: pre-wrap;
      max-height: 400px;
      overflow-y: auto;
    }
    .export-meta-strip {
      display: flex;
      gap: 1.5rem;
      margin-bottom: 1rem;
      color: #64748b;
      font-size: 0.8125rem;
    }
  `]
})
export class InvestigationComponent implements OnInit {
  activeTab = signal<'alerts' | 'workspace' | 'history'>('alerts');
  isLoading = signal<boolean>(false);
  isAnalyzing = signal<boolean>(false);
  isSavingDraft = signal<boolean>(false);
  isCreatingAlert = signal<boolean>(false);
  isCreateModalOpen = signal<boolean>(false);

  errorMessage = signal<string | null>(null);
  successMessage = signal<string | null>(null);

  alerts = signal<AmlAlert[]>([]);
  investigations = signal<InvestigationSummary[]>([]);
  selectedAlert = signal<AmlAlert | null>(null);
  selectedInvestigation = signal<AmlInvestigationDetail | null>(null);
  exportData = signal<InvestigationExport | null>(null);

  // Filters
  searchQuery = '';
  filterSeverity = '';
  filterStatus = '';
  analyzingAlertId: string | null = null;

  // Workspace form fields
  editableDraft = '';
  analystNotes = '';
  reviewerNotes = '';
  escalationReason = '';

  // New alert form model
  newAlert: CreateAlertRequest = {
    scenarioCode: 'TM-RULE-101',
    scenarioTitle: 'Multiple Cash Deposits Below Statutory CTR Threshold (Structuring)',
    customerId: 'CUST-88192',
    customerName: 'Meridian Global Enterprises LLC',
    customerRiskLevel: 'HIGH',
    accountNumber: 'ACCT-99214081',
    totalAlertAmount: 28500,
    currency: 'USD',
    severity: 'HIGH'
  };

  constructor(
    private investigationService: InvestigationService,
    private authService: AuthService
  ) {}

  ngOnInit(): void {
    this.loadAlerts();
    this.loadInvestigations();
  }

  setTab(tab: 'alerts' | 'workspace' | 'history'): void {
    this.activeTab.set(tab);
    if (tab === 'alerts') this.loadAlerts();
    if (tab === 'history') this.loadInvestigations();
  }

  refreshCurrentView(): void {
    if (this.activeTab() === 'alerts') this.loadAlerts();
    else if (this.activeTab() === 'history') this.loadInvestigations();
    else if (this.activeTab() === 'workspace' && this.selectedInvestigation()) {
      this.openInvestigation(this.selectedInvestigation()!.id);
    }
  }

  canReview(): boolean {
    const user = this.authService.currentUser();
    return !!user?.roles?.some((r: string | any) => r === 'ROLE_ADMIN' || r === 'ADMIN');
  }

  loadAlerts(): void {
    this.isLoading.set(true);
    this.errorMessage.set(null);
    this.investigationService.getAlerts(
      this.filterStatus || undefined,
      this.filterSeverity || undefined,
      undefined,
      this.searchQuery || undefined
    ).subscribe({
      next: (page) => {
        this.alerts.set(page.content);
        this.isLoading.set(false);
      },
      error: (err) => {
        this.errorMessage.set('Failed to load transaction monitoring alerts: ' + (err.error?.detail || err.message));
        this.isLoading.set(false);
      }
    });
  }

  loadInvestigations(): void {
    this.investigationService.getInvestigations().subscribe({
      next: (page) => {
        this.investigations.set(page.content);
      },
      error: (err) => {
        console.error('Error fetching investigations:', err);
      }
    });
  }

  startInvestigation(alertId: string): void {
    this.isAnalyzing.set(true);
    this.analyzingAlertId = alertId;
    this.errorMessage.set(null);

    this.investigationService.analyzeAlert(alertId).subscribe({
      next: (inv) => {
        this.isAnalyzing.set(false);
        this.analyzingAlertId = null;
        this.loadInvestigationIntoWorkspace(inv);
        this.setTab('workspace');
        this.successMessage.set(`Investigation dossier prepared for alert ${inv.alert.alertReference}.`);
      },
      error: (err) => {
        this.isAnalyzing.set(false);
        this.analyzingAlertId = null;
        this.errorMessage.set('AI investigation analysis failed: ' + (err.error?.detail || err.message));
      }
    });
  }

  openInvestigation(invId: string): void {
    this.isLoading.set(true);
    this.investigationService.getInvestigation(invId).subscribe({
      next: (inv) => {
        this.isLoading.set(false);
        this.loadInvestigationIntoWorkspace(inv);
        this.setTab('workspace');
      },
      error: (err) => {
        this.isLoading.set(false);
        this.errorMessage.set('Failed to load investigation details: ' + (err.error?.detail || err.message));
      }
    });
  }

  private loadInvestigationIntoWorkspace(inv: AmlInvestigationDetail): void {
    this.selectedInvestigation.set(inv);
    this.selectedAlert.set(inv.alert);
    this.editableDraft = inv.sarNarrativeDraft || '';
    this.analystNotes = inv.analystNotes || '';
    this.reviewerNotes = inv.reviewerNotes || '';
    this.escalationReason = inv.escalationReason || '';
  }

  saveDraftEdits(): void {
    const inv = this.selectedInvestigation();
    if (!inv) return;

    this.isSavingDraft.set(true);
    this.investigationService.updateDraft(inv.id, {
      sarNarrativeDraft: this.editableDraft,
      analystNotes: this.analystNotes
    }).subscribe({
      next: (updated) => {
        this.isSavingDraft.set(false);
        this.loadInvestigationIntoWorkspace(updated);
        this.successMessage.set('SAR narrative amendments saved and hashed to audit chain.');
      },
      error: (err) => {
        this.isSavingDraft.set(false);
        this.errorMessage.set('Failed to save draft edits: ' + (err.error?.detail || err.message));
      }
    });
  }

  submitAnalystDecision(decision: 'ACCEPTED' | 'EDITED' | 'REJECTED' | 'REGENERATE_REQUESTED'): void {
    const inv = this.selectedInvestigation();
    if (!inv) return;

    this.investigationService.submitAnalystDecision(inv.id, {
      decision,
      updatedDraft: this.editableDraft,
      notes: this.analystNotes
    }).subscribe({
      next: (updated) => {
        this.loadInvestigationIntoWorkspace(updated);
        this.successMessage.set(`Analyst decision [${decision}] recorded successfully.`);
        this.loadAlerts();
      },
      error: (err) => {
        this.errorMessage.set('Failed to record analyst decision: ' + (err.error?.detail || err.message));
      }
    });
  }

  submitReviewerDecision(decision: 'APPROVED' | 'RETURNED_FOR_REVISION' | 'ESCALATED_TO_MLRO' | 'REJECTED'): void {
    const inv = this.selectedInvestigation();
    if (!inv) return;

    this.investigationService.submitReviewerDecision(inv.id, {
      decision,
      notes: this.reviewerNotes,
      escalationReason: this.escalationReason
    }).subscribe({
      next: (updated) => {
        this.loadInvestigationIntoWorkspace(updated);
        this.successMessage.set(`Reviewer decision [${decision}] authorized successfully.`);
        this.loadAlerts();
      },
      error: (err) => {
        this.errorMessage.set('Reviewer decision failed: ' + (err.error?.detail || err.message));
      }
    });
  }

  exportDossierReport(): void {
    const inv = this.selectedInvestigation();
    if (!inv) return;

    this.investigationService.exportReport(inv.id).subscribe({
      next: (data) => {
        this.exportData.set(data);
      },
      error: (err) => {
        this.errorMessage.set('Failed to export dossier report: ' + (err.error?.detail || err.message));
      }
    });
  }

  downloadReportMarkdown(): void {
    const data = this.exportData();
    if (!data) return;

    const blob = new Blob([data.markdownContent], { type: 'text/markdown;charset=utf-8' });
    const url = window.URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `${data.investigationReference}_dossier.md`;
    a.click();
    window.URL.revokeObjectURL(url);
  }

  openCreateAlertModal(): void {
    this.isCreateModalOpen.set(true);
  }

  closeCreateAlertModal(): void {
    this.isCreateModalOpen.set(false);
  }

  onScenarioChange(): void {
    if (this.newAlert.scenarioCode === 'TM-RULE-101') {
      this.newAlert.scenarioTitle = 'Multiple Cash Deposits Below Statutory CTR Threshold (Structuring)';
      this.newAlert.totalAlertAmount = 28500;
      this.newAlert.severity = 'HIGH';
    } else if (this.newAlert.scenarioCode === 'ALRT_HVT_01') {
      this.newAlert.scenarioTitle = 'High-Value Outbound Offshore Wire Transfer (ALRT_HVT_01)';
      this.newAlert.totalAlertAmount = 185000;
      this.newAlert.severity = 'CRITICAL';
    } else if (this.newAlert.scenarioCode === 'SANCTION-PEP-03') {
      this.newAlert.scenarioTitle = 'Rapid Velocity Funds Involving PEP Enterprise (SANCTION-PEP-03)';
      this.newAlert.totalAlertAmount = 450000;
      this.newAlert.severity = 'CRITICAL';
    } else {
      this.newAlert.scenarioTitle = 'Sudden Velocity & Volume Surge on Retail Account (TM-RULE-204)';
      this.newAlert.totalAlertAmount = 89500;
      this.newAlert.severity = 'MEDIUM';
    }
  }

  submitCreateAlert(): void {
    this.isCreatingAlert.set(true);
    this.investigationService.generateSyntheticAlert(this.newAlert).subscribe({
      next: (created) => {
        this.isCreatingAlert.set(false);
        this.closeCreateAlertModal();
        this.successMessage.set(`Synthetic alert ${created.alertReference} created with 3 transactions.`);
        this.loadAlerts();
      },
      error: (err) => {
        this.isCreatingAlert.set(false);
        this.errorMessage.set('Failed to create synthetic alert: ' + (err.error?.detail || err.message));
      }
    });
  }
}
