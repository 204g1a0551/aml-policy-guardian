import { Component, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { AuthService } from '../../core/services/auth.service';

@Component({
  selector: 'app-settings',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="settings-container">
      <header class="page-header">
        <div>
          <h2>System Governance & Investigator Profile</h2>
          <p class="subtitle">Compliance infrastructure parameters, security posture, and active session attributes.</p>
        </div>
      </header>

      <div class="settings-grid">
        <!-- Section 1: Investigator Identity -->
        <section class="settings-card">
          <div class="card-header">
            <h3>Investigator Profile</h3>
            <span class="badge-role">{{ authService.isAdmin() ? 'ADMINISTRATOR' : 'COMPLIANCE ANALYST' }}</span>
          </div>

          <div class="attr-table">
            <div class="attr-row">
              <span class="attr-label">Principal User ID:</span>
              <span class="attr-value">{{ authService.currentUser()?.username || 'analyst' }}</span>
            </div>
            <div class="attr-row">
              <span class="attr-label">Full Name:</span>
              <span class="attr-value">{{ authService.currentUser()?.fullName || 'FIU Investigator' }}</span>
            </div>
            <div class="attr-row">
              <span class="attr-label">Department:</span>
              <span class="attr-value">Financial Intelligence Unit (FIU) &bull; AML Operations</span>
            </div>
            <div class="attr-row">
              <span class="attr-label">Email:</span>
              <span class="attr-value font-mono">{{ authService.currentUser()?.email || 'analyst@bank.internal' }}</span>
            </div>
            <div class="attr-row">
              <span class="attr-label">Assigned Roles:</span>
              <span class="attr-value font-mono">{{ authService.currentUser()?.roles?.join(', ') || 'ROLE_ANALYST' }}</span>
            </div>
            <div class="attr-row">
              <span class="attr-label">Session Idle Timeout:</span>
              <span class="attr-value">30 Minutes (Enforced)</span>
            </div>
          </div>
        </section>

        <!-- Section 2: Security & Defense Guardrails -->
        <section class="settings-card">
          <div class="card-header">
            <h3>Active Compliance Guardrails</h3>
            <span class="badge-active">ACTIVE & ENFORCED</span>
          </div>

          <div class="guardrails-list">
            <div class="guardrail-item">
              <div class="guard-title">Automated PII Redaction</div>
              <div class="guard-desc">Regex-based redaction of US Social Security Numbers (SSNs) and Credit Card PANs before output generation.</div>
            </div>

            <div class="guardrail-item">
              <div class="guard-title">Anti-Prompt Injection Filters</div>
              <div class="guard-desc">Deterministic input scanning blocks direct and indirect prompt overrides, delimiter attacks, and role hijacking.</div>
            </div>

            <div class="guardrail-item">
              <div class="guard-title">Tenant & IDOR Access Isolation</div>
              <div class="guard-desc">Cross-user chat session and document access prohibited with cryptographic token validation.</div>
            </div>

            <div class="guardrail-item">
              <div class="guard-title">Sliding Window Rate Limiter</div>
              <div class="guard-desc">15 requests/minute on authentication endpoints; 60 requests/minute on RAG investigation endpoints.</div>
            </div>
          </div>
        </section>

        <!-- Section 3: RAG Retrieval & Model Parameters -->
        <section class="settings-card full-width">
          <div class="card-header">
            <h3>RAG Retrieval Architecture Specifications</h3>
            <span class="badge-config">TIER-1 SPECIFICATION</span>
          </div>

          <div class="specs-grid">
            <div class="spec-block">
              <span class="spec-key">Inference Framework</span>
              <span class="spec-val">Spring AI ChatClient 1.0.0</span>
              <span class="spec-hint">Deterministic Temperature 0.0</span>
            </div>

            <div class="spec-block">
              <span class="spec-key">Vector Database</span>
              <span class="spec-val">PostgreSQL 16 + pgvector</span>
              <span class="spec-hint">HNSW Cosine Distance Indexing</span>
            </div>

            <div class="spec-block">
              <span class="spec-key">Embeddings Dimension</span>
              <span class="spec-val">1,536 Dimensions</span>
              <span class="spec-hint">OpenAI text-embedding-3-small</span>
            </div>

            <div class="spec-block">
              <span class="spec-key">Document Ingestion</span>
              <span class="spec-val">500 Chars / 50 Overlap</span>
              <span class="spec-hint">Apache Tika Parser Pipeline</span>
            </div>

            <div class="spec-block">
              <span class="spec-key">Relevance Floor</span>
              <span class="spec-val">0.70 Cosine Similarity</span>
              <span class="spec-hint">Strict Grounding Filter</span>
            </div>

            <div class="spec-block">
              <span class="spec-key">Stream Wire Protocol</span>
              <span class="spec-val">Server-Sent Events (SSE)</span>
              <span class="spec-hint">START &bull; CITATIONS &bull; TOKEN &bull; COMPLETE</span>
            </div>
          </div>
        </section>
      </div>
    </div>
  `,
  styles: [`
    .settings-container {
      max-width: 1200px;
      margin: 0 auto;
      padding: 1.75rem 1.5rem;
    }

    .page-header {
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

    .settings-grid {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 1.25rem;
    }

    @media (max-width: 850px) {
      .settings-grid {
        grid-template-columns: 1fr;
      }
    }

    .full-width {
      grid-column: 1 / -1;
    }

    .settings-card {
      background-color: #ffffff;
      border: 1px solid #cbd5e1;
      border-radius: 4px;
      padding: 1.25rem;
      box-shadow: 0 1px 2px rgba(0, 0, 0, 0.04);
    }

    .card-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 1rem;
      border-bottom: 1px solid #f1f5f9;
      padding-bottom: 0.65rem;
    }

    .card-header h3 {
      font-size: 0.95rem;
      font-weight: 700;
      color: #0f172a;
    }

    .badge-role {
      background-color: #eff6ff;
      color: #1e40af;
      border: 1px solid #bfdbfe;
      font-size: 0.68rem;
      font-weight: 700;
      padding: 0.15rem 0.45rem;
      border-radius: 3px;
    }

    .badge-active {
      background-color: #ecfdf5;
      color: #065f46;
      border: 1px solid #a7f3d0;
      font-size: 0.68rem;
      font-weight: 700;
      padding: 0.15rem 0.45rem;
      border-radius: 3px;
    }

    .badge-config {
      background-color: #f8fafc;
      color: #475569;
      border: 1px solid #cbd5e1;
      font-size: 0.68rem;
      font-weight: 700;
      padding: 0.15rem 0.45rem;
      border-radius: 3px;
    }

    /* Attributes Table */
    .attr-table {
      display: flex;
      flex-direction: column;
      gap: 0.65rem;
    }

    .attr-row {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding-bottom: 0.5rem;
      border-bottom: 1px solid #f1f5f9;
      font-size: 0.825rem;
    }

    .attr-label {
      font-weight: 600;
      color: #475569;
    }

    .attr-value {
      color: #0f172a;
      font-weight: 500;
    }

    /* Guardrails */
    .guardrails-list {
      display: flex;
      flex-direction: column;
      gap: 0.75rem;
    }

    .guardrail-item {
      padding: 0.65rem 0.75rem;
      background-color: #f8fafc;
      border: 1px solid #e2e8f0;
      border-radius: 4px;
    }

    .guard-title {
      font-size: 0.825rem;
      font-weight: 600;
      color: #0f172a;
      margin-bottom: 0.2rem;
    }

    .guard-desc {
      font-size: 0.75rem;
      color: #64748b;
      line-height: 1.4;
    }

    /* Specs Grid */
    .specs-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
      gap: 1rem;
    }

    .spec-block {
      background-color: #f8fafc;
      border: 1px solid #e2e8f0;
      border-radius: 4px;
      padding: 0.85rem;
      display: flex;
      flex-direction: column;
      gap: 0.25rem;
    }

    .spec-key {
      font-size: 0.7rem;
      font-weight: 600;
      color: #64748b;
      text-transform: uppercase;
      letter-spacing: 0.03em;
    }

    .spec-val {
      font-size: 0.95rem;
      font-weight: 700;
      color: #0f172a;
    }

    .spec-hint {
      font-size: 0.725rem;
      color: #1e40af;
    }
  `]
})
export class SettingsComponent {
  constructor(public authService: AuthService) {}
}
