import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { EvaluationService } from '../../core/services/evaluation.service';
import {
  EvaluationMetrics,
  BenchmarkRun,
  RegressionAlert,
  BenchmarkComparison
} from '../../core/models/evaluation.models';

@Component({
  selector: 'app-quality',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="quality-container">
      <header class="page-header">
        <div>
          <div class="header-title-row">
            <h2>AML AI Observability & Quality Assurance Dashboard</h2>
            @if (metrics()?.systemHealth) {
              <span class="health-pill" [class]="'health-' + metrics()?.systemHealth?.toLowerCase()">
                {{ metrics()?.systemHealth }} STATUS
              </span>
            }
          </div>
          <p class="subtitle">
            Micrometer telemetry, p50/p95/p99 request latencies, token consumption, inference cost tracking, and synthetic AML benchmark regression analytics.
          </p>
        </div>
        <div class="header-actions">
          <button type="button" class="btn-secondary" (click)="loadAll()" [disabled]="isLoading()">
            <span>Refresh Telemetry</span>
          </button>
          <a href="http://localhost:3000" target="_blank" rel="noopener" class="btn-grafana">
            <span>Grafana Dashboard</span>
          </a>
          <a href="http://localhost:9090" target="_blank" rel="noopener" class="btn-prometheus">
            <span>Prometheus</span>
          </a>
        </div>
      </header>

      <!-- Active Alerts Banner -->
      @if (metrics()?.activeAlerts && (metrics()?.activeAlerts)!.length > 0) {
        <div class="alert-section">
          @for (alert of metrics()?.activeAlerts; track alert.id) {
            <div class="regression-alert-banner" [class]="'alert-' + alert.severity.toLowerCase()">
              <div class="alert-icon">⚠️</div>
              <div class="alert-body">
                <div class="alert-top">
                  <span class="alert-type">{{ alert.alertType }}</span>
                  <span class="badge-severity" [class]="'sev-' + alert.severity.toLowerCase()">{{ alert.severity }}</span>
                </div>
                <p class="alert-msg">{{ alert.message }}</p>
                <div class="alert-meta">
                  <span>Metric: <code>{{ alert.metricName }}</code></span>
                  <span>Observed: <strong>{{ alert.currentValue | number:'1.1-2' }}</strong></span>
                  <span>Threshold: <strong>{{ alert.threshold | number:'1.1-2' }}</strong></span>
                </div>
              </div>
            </div>
          }
        </div>
      }

      <!-- Top Executive Metrics Grid -->
      <section class="kpi-grid">
        <div class="kpi-card">
          <span class="kpi-label">P50 Request Latency</span>
          <span class="kpi-val font-mono">{{ (metrics()?.performanceSnapshot?.p50TotalLatencyMs || 0) | number:'1.1-1' }} ms</span>
          <span class="kpi-sub">Median turnaround</span>
        </div>
        <div class="kpi-card">
          <span class="kpi-label">P95 Request Latency</span>
          <span class="kpi-val font-mono">{{ (metrics()?.performanceSnapshot?.p95TotalLatencyMs || 0) | number:'1.1-1' }} ms</span>
          <span class="kpi-sub">SLA target: &lt; 1500 ms</span>
        </div>
        <div class="kpi-card">
          <span class="kpi-label">Time to First Token (TTFT)</span>
          <span class="kpi-val font-mono">{{ (metrics()?.performanceSnapshot?.p95TimeToFirstTokenMs || 0) | number:'1.1-1' }} ms</span>
          <span class="kpi-sub">P95 streaming launch</span>
        </div>
        <div class="kpi-card">
          <span class="kpi-label">Total Cumulative Tokens</span>
          <span class="kpi-val font-mono">{{ ((metrics()?.performanceSnapshot?.totalPromptTokens || 0) + (metrics()?.performanceSnapshot?.totalCompletionTokens || 0)) | number }}</span>
          <span class="kpi-sub">
            {{ metrics()?.performanceSnapshot?.totalPromptTokens | number }} in / {{ metrics()?.performanceSnapshot?.totalCompletionTokens | number }} out
          </span>
        </div>
        <div class="kpi-card">
          <span class="kpi-label">Estimated Inference Cost</span>
          <span class="kpi-val text-cost font-mono">\${{ (metrics()?.performanceSnapshot?.totalEstimatedCostUsd || 0) | number:'1.4-4' }}</span>
          <span class="kpi-sub">Standard compliance tier</span>
        </div>
        <div class="kpi-card">
          <span class="kpi-label">Response Success Rate</span>
          <span class="kpi-val text-success font-mono">{{ (metrics()?.performanceSnapshot?.successRatePercentage || 100) | number:'1.1-1' }}%</span>
          <span class="kpi-sub">Refusals: {{ metrics()?.performanceSnapshot?.refusals || 0 }}</span>
        </div>
      </section>

      <!-- Latency & Evidence Analytics Columns -->
      <div class="analytics-row">
        <!-- Pipeline Latencies Breakdown -->
        <div class="panel-card">
          <h3 class="panel-title">RAG Pipeline Latency Breakdown</h3>
          <p class="panel-sub">Component latencies measured via Micrometer Timers and OpenTelemetry Spans.</p>
          
          <div class="latency-bars-list">
            <div class="latency-row">
              <span class="latency-label">Retrieval (pgvector + tsvector):</span>
              <div class="latency-bar-track">
                <div class="latency-bar-fill bar-retrieval" [style.width.%]="calcBarWidth(metrics()?.performanceSnapshot?.p95RetrievalLatencyMs, 500)"></div>
              </div>
              <span class="latency-num font-mono">{{ (metrics()?.performanceSnapshot?.p95RetrievalLatencyMs || 0) | number:'1.1-1' }} ms (p95)</span>
            </div>

            <div class="latency-row">
              <span class="latency-label">LLM Generation:</span>
              <div class="latency-bar-track">
                <div class="latency-bar-fill bar-generation" [style.width.%]="calcBarWidth(metrics()?.performanceSnapshot?.p95GenerationLatencyMs, 1000)"></div>
              </div>
              <span class="latency-num font-mono">{{ (metrics()?.performanceSnapshot?.p95GenerationLatencyMs || 0) | number:'1.1-1' }} ms (p95)</span>
            </div>

            <div class="latency-row">
              <span class="latency-label">Time to First Token (TTFT):</span>
              <div class="latency-bar-track">
                <div class="latency-bar-fill bar-ttft" [style.width.%]="calcBarWidth(metrics()?.performanceSnapshot?.p95TimeToFirstTokenMs, 500)"></div>
              </div>
              <span class="latency-num font-mono">{{ (metrics()?.performanceSnapshot?.p95TimeToFirstTokenMs || 0) | number:'1.1-1' }} ms (p95)</span>
            </div>

            <div class="latency-row">
              <span class="latency-label">Total Turnaround (p99):</span>
              <div class="latency-bar-track">
                <div class="latency-bar-fill bar-p99" [style.width.%]="calcBarWidth(metrics()?.performanceSnapshot?.p99TotalLatencyMs, 1500)"></div>
              </div>
              <span class="latency-num font-mono">{{ (metrics()?.performanceSnapshot?.p99TotalLatencyMs || 0) | number:'1.1-1' }} ms (p99)</span>
            </div>
          </div>
        </div>

        <!-- Evidence Quality Distribution -->
        <div class="panel-card">
          <h3 class="panel-title">Calibrated Evidence Quality Distribution</h3>
          <p class="panel-sub">Multi-factor grounding scores computed by EvidenceQualityEvaluator.</p>

          <div class="quality-distribution-grid">
            <div class="quality-box q-high">
              <span class="quality-count font-mono">{{ metrics()?.performanceSnapshot?.highQualityCount || 0 }}</span>
              <span class="quality-tag">HIGH QUALITY</span>
              <span class="quality-range">&ge; 0.75 Score</span>
            </div>
            <div class="quality-box q-medium">
              <span class="quality-count font-mono">{{ metrics()?.performanceSnapshot?.mediumQualityCount || 0 }}</span>
              <span class="quality-tag">MEDIUM QUALITY</span>
              <span class="quality-range">0.50 - 0.74</span>
            </div>
            <div class="quality-box q-low">
              <span class="quality-count font-mono">{{ metrics()?.performanceSnapshot?.lowQualityCount || 0 }}</span>
              <span class="quality-tag">LOW QUALITY</span>
              <span class="quality-range">0.30 - 0.49</span>
            </div>
            <div class="quality-box q-insufficient">
              <span class="quality-count font-mono">{{ metrics()?.performanceSnapshot?.insufficientQualityCount || 0 }}</span>
              <span class="quality-tag">INSUFFICIENT</span>
              <span class="quality-range">&lt; 0.30 (Refused)</span>
            </div>
          </div>
        </div>
      </div>

      <!-- Synthetic AML Benchmark History -->
      <section class="benchmark-section">
        <div class="benchmark-header">
          <div>
            <h3 class="panel-title">Synthetic AML Compliance Benchmark Regression Matrix</h3>
            <p class="panel-sub">Automated regression comparisons measuring Groundedness, Retrieval Recall, Citation Correctness, and Refusal Quality.</p>
          </div>
        </div>

        <!-- Comparison Bar -->
        @if (benchmarkHistory().length >= 2) {
          <div class="comparison-bar">
            <div class="compare-group">
              <label for="baselineSelect">Baseline Run:</label>
              <select id="baselineSelect" [(ngModel)]="selectedBaselineId" class="select-control">
                @for (run of benchmarkHistory(); track run.id) {
                  <option [value]="run.id">{{ run.id }} ({{ run.passRatePercentage }}%)</option>
                }
              </select>
            </div>
            <span class="compare-vs">vs</span>
            <div class="compare-group">
              <label for="candidateSelect">Candidate Run:</label>
              <select id="candidateSelect" [(ngModel)]="selectedCandidateId" class="select-control">
                @for (run of benchmarkHistory(); track run.id) {
                  <option [value]="run.id">{{ run.id }} ({{ run.passRatePercentage }}%)</option>
                }
              </select>
            </div>
            <button type="button" class="btn-primary" (click)="compareRuns()">
              Compare Benchmark Runs
            </button>
          </div>
        }

        <!-- Comparison Result Card -->
        @if (comparison()) {
          <div class="comparison-result-card" [class.regressed]="comparison()?.isRegression">
            <div class="comp-header">
              <span class="comp-verdict font-mono">{{ comparison()?.summaryVerdict }}</span>
              <span class="comp-badge" [class.badge-pass]="!comparison()?.isRegression" [class.badge-fail]="comparison()?.isRegression">
                {{ comparison()?.isRegression ? 'REGRESSION' : 'MEETS SPEC' }}
              </span>
            </div>
            <div class="comp-deltas-grid">
              <div class="delta-box">
                <span class="delta-label">Pass Rate Delta:</span>
                <span class="delta-val font-mono" [class.text-red]="(comparison()?.passRateDelta || 0) < 0">
                  {{ (comparison()?.passRateDelta || 0) > 0 ? '+' : '' }}{{ comparison()?.passRateDelta | number:'1.2-2' }}%
                </span>
              </div>
              <div class="delta-box">
                <span class="delta-label">Groundedness Delta:</span>
                <span class="delta-val font-mono" [class.text-red]="(comparison()?.groundednessDelta || 0) < 0">
                  {{ (comparison()?.groundednessDelta || 0) > 0 ? '+' : '' }}{{ comparison()?.groundednessDelta | number:'1.2-2' }}%
                </span>
              </div>
              <div class="delta-box">
                <span class="delta-label">Recall Delta:</span>
                <span class="delta-val font-mono" [class.text-red]="(comparison()?.recallDelta || 0) < 0">
                  {{ (comparison()?.recallDelta || 0) > 0 ? '+' : '' }}{{ comparison()?.recallDelta | number:'1.2-2' }}%
                </span>
              </div>
              <div class="delta-box">
                <span class="delta-label">P95 Latency Delta:</span>
                <span class="delta-val font-mono" [class.text-red]="(comparison()?.p95LatencyDeltaMs || 0) > 200">
                  {{ (comparison()?.p95LatencyDeltaMs || 0) > 0 ? '+' : '' }}{{ comparison()?.p95LatencyDeltaMs | number:'1.1-1' }} ms
                </span>
              </div>
            </div>
          </div>
        }

        <!-- Benchmark Runs Table -->
        <div class="table-responsive">
          <table class="benchmark-table">
            <thead>
              <tr>
                <th>Run Identifier</th>
                <th>Benchmark Version</th>
                <th>Scenarios</th>
                <th>Pass Rate</th>
                <th>Groundedness</th>
                <th>Retrieval Recall</th>
                <th>Citation Acc.</th>
                <th>Refusal Acc.</th>
                <th>P95 Latency</th>
                <th>Status</th>
              </tr>
            </thead>
            <tbody>
              @for (run of benchmarkHistory(); track run.id) {
                <tr>
                  <td>
                    <code class="run-id font-mono">{{ run.id }}</code>
                  </td>
                  <td>
                    <span class="badge-version">{{ run.benchmarkVersion }}</span>
                  </td>
                  <td>{{ run.passedInquiries }} / {{ run.totalInquiries }}</td>
                  <td>
                    <strong class="text-success font-mono">{{ run.passRatePercentage | number:'1.1-1' }}%</strong>
                  </td>
                  <td class="font-mono">{{ run.groundednessRate | number:'1.1-1' }}%</td>
                  <td class="font-mono">{{ run.retrievalRecallRate | number:'1.1-1' }}%</td>
                  <td class="font-mono">{{ run.citationCorrectnessRate | number:'1.1-1' }}%</td>
                  <td class="font-mono">{{ run.refusalQualityRate | number:'1.1-1' }}%</td>
                  <td class="font-mono">{{ run.p95LatencyMs | number:'1.1-1' }} ms</td>
                  <td>
                    <span class="badge-status-pass">{{ run.status }}</span>
                  </td>
                </tr>
              }
              @if (benchmarkHistory().length === 0) {
                <tr>
                  <td colspan="10" class="empty-cell">No benchmark runs recorded yet. Execute <code>python3 evaluation/run_eval.py</code>.</td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      </section>
    </div>
  `,
  styles: [`
    .quality-container {
      max-width: 1280px;
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

    .header-title-row {
      display: flex;
      align-items: center;
      gap: 0.75rem;
    }

    .header-title-row h2 {
      font-size: 1.35rem;
      font-weight: 700;
      color: #0f172a;
      margin-bottom: 0.25rem;
    }

    .health-pill {
      font-size: 0.75rem;
      font-weight: 700;
      padding: 0.2rem 0.6rem;
      border-radius: 9999px;
      letter-spacing: 0.05em;
    }

    .health-optimal {
      background: #ecfdf5;
      color: #059669;
      border: 1px solid #a7f3d0;
    }

    .health-degraded {
      background: #fef9c3;
      color: #b45309;
      border: 1px solid #fde047;
    }

    .health-critical {
      background: #fee2e2;
      color: #dc2626;
      border: 1px solid #fca5a5;
    }

    .subtitle {
      font-size: 0.85rem;
      color: #64748b;
    }

    .header-actions {
      display: flex;
      gap: 0.5rem;
    }

    .btn-secondary {
      background: #ffffff;
      color: #334155;
      border: 1px solid #cbd5e1;
      padding: 0.5rem 0.85rem;
      font-size: 0.8rem;
      font-weight: 600;
      border-radius: 6px;
      cursor: pointer;
    }

    .btn-grafana {
      background: #f97316;
      color: #ffffff;
      text-decoration: none;
      padding: 0.5rem 0.85rem;
      font-size: 0.8rem;
      font-weight: 600;
      border-radius: 6px;
    }

    .btn-prometheus {
      background: #e11d48;
      color: #ffffff;
      text-decoration: none;
      padding: 0.5rem 0.85rem;
      font-size: 0.8rem;
      font-weight: 600;
      border-radius: 6px;
    }

    .alert-section {
      display: flex;
      flex-direction: column;
      gap: 0.75rem;
      margin-bottom: 1.5rem;
    }

    .regression-alert-banner {
      display: flex;
      gap: 1rem;
      padding: 1rem;
      border-radius: 8px;
      border: 1px solid #fca5a5;
      background: #fff5f5;
    }

    .alert-critical {
      border-color: #ef4444;
      background: #fef2f2;
    }

    .alert-warning {
      border-color: #f59e0b;
      background: #fffbeb;
    }

    .alert-icon {
      font-size: 1.5rem;
    }

    .alert-body {
      flex: 1;
    }

    .alert-top {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      margin-bottom: 0.25rem;
    }

    .alert-type {
      font-weight: 700;
      color: #991b1b;
      font-size: 0.85rem;
    }

    .badge-severity {
      font-size: 0.7rem;
      font-weight: 700;
      padding: 0.15rem 0.4rem;
      border-radius: 4px;
    }

    .sev-critical {
      background: #dc2626;
      color: #ffffff;
    }

    .sev-warning {
      background: #d97706;
      color: #ffffff;
    }

    .alert-msg {
      font-size: 0.8rem;
      color: #450a0a;
      margin-bottom: 0.5rem;
    }

    .alert-meta {
      display: flex;
      gap: 1rem;
      font-size: 0.75rem;
      color: #7f1d1d;
    }

    /* KPI Grid */
    .kpi-grid {
      display: grid;
      grid-template-columns: repeat(6, 1fr);
      gap: 0.85rem;
      margin-bottom: 1.5rem;
    }

    .kpi-card {
      background: #ffffff;
      border: 1px solid #e2e8f0;
      border-radius: 8px;
      padding: 1rem 0.85rem;
      box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
    }

    .kpi-label {
      display: block;
      font-size: 0.7rem;
      text-transform: uppercase;
      font-weight: 700;
      color: #64748b;
      margin-bottom: 0.35rem;
    }

    .kpi-val {
      display: block;
      font-size: 1.35rem;
      font-weight: 700;
      color: #0f172a;
      margin-bottom: 0.2rem;
    }

    .kpi-sub {
      font-size: 0.7rem;
      color: #94a3b8;
    }

    .text-cost { color: #0284c7; }
    .text-success { color: #059669; }
    .font-mono { font-family: monospace; }

    /* Analytics row */
    .analytics-row {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 1.25rem;
      margin-bottom: 1.5rem;
    }

    .panel-card {
      background: #ffffff;
      border: 1px solid #e2e8f0;
      border-radius: 8px;
      padding: 1.25rem;
      box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
    }

    .panel-title {
      font-size: 1rem;
      font-weight: 700;
      color: #0f172a;
      margin-bottom: 0.25rem;
    }

    .panel-sub {
      font-size: 0.75rem;
      color: #64748b;
      margin-bottom: 1rem;
    }

    .latency-bars-list {
      display: flex;
      flex-direction: column;
      gap: 0.85rem;
    }

    .latency-row {
      display: flex;
      align-items: center;
      gap: 0.75rem;
    }

    .latency-label {
      width: 190px;
      font-size: 0.8rem;
      color: #334155;
      font-weight: 500;
    }

    .latency-bar-track {
      flex: 1;
      height: 10px;
      background: #f1f5f9;
      border-radius: 5px;
      overflow: hidden;
    }

    .latency-bar-fill {
      height: 100%;
      border-radius: 5px;
    }

    .bar-retrieval { background: #0284c7; }
    .bar-generation { background: #8b5cf6; }
    .bar-ttft { background: #059669; }
    .bar-p99 { background: #f59e0b; }

    .latency-num {
      width: 95px;
      text-align: right;
      font-size: 0.75rem;
      font-weight: 600;
      color: #0f172a;
    }

    /* Quality distribution */
    .quality-distribution-grid {
      display: grid;
      grid-template-columns: repeat(4, 1fr);
      gap: 0.75rem;
    }

    .quality-box {
      border-radius: 6px;
      padding: 0.85rem 0.5rem;
      text-align: center;
      border: 1px solid transparent;
    }

    .quality-count {
      display: block;
      font-size: 1.5rem;
      font-weight: 700;
      margin-bottom: 0.2rem;
    }

    .quality-tag {
      display: block;
      font-size: 0.65rem;
      font-weight: 700;
      letter-spacing: 0.05em;
    }

    .quality-range {
      display: block;
      font-size: 0.7rem;
      opacity: 0.8;
      margin-top: 0.2rem;
    }

    .q-high { background: #ecfdf5; border-color: #a7f3d0; color: #047857; }
    .q-medium { background: #eff6ff; border-color: #bfdbfe; color: #1d4ed8; }
    .q-low { background: #fef9c3; border-color: #fde047; color: #b45309; }
    .q-insufficient { background: #fef2f2; border-color: #fecaca; color: #b91c1c; }

    /* Benchmark Table */
    .benchmark-section {
      background: #ffffff;
      border: 1px solid #e2e8f0;
      border-radius: 8px;
      padding: 1.25rem;
      box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
    }

    .comparison-bar {
      display: flex;
      align-items: center;
      gap: 1rem;
      margin-bottom: 1.25rem;
      background: #f8fafc;
      padding: 0.75rem 1rem;
      border-radius: 6px;
      border: 1px solid #e2e8f0;
    }

    .compare-group {
      display: flex;
      align-items: center;
      gap: 0.5rem;
    }

    .compare-group label {
      font-size: 0.8rem;
      font-weight: 600;
      color: #475569;
    }

    .select-control {
      padding: 0.35rem 0.65rem;
      border: 1px solid #cbd5e1;
      border-radius: 4px;
      font-size: 0.8rem;
      background: #ffffff;
    }

    .compare-vs {
      font-weight: 700;
      color: #94a3b8;
    }

    .btn-primary {
      background: #0284c7;
      color: #ffffff;
      border: none;
      padding: 0.45rem 0.85rem;
      font-size: 0.8rem;
      font-weight: 600;
      border-radius: 6px;
      cursor: pointer;
    }

    .comparison-result-card {
      background: #f0fdf4;
      border: 1px solid #bbf7d0;
      border-radius: 6px;
      padding: 1rem;
      margin-bottom: 1.25rem;
    }

    .comparison-result-card.regressed {
      background: #fef2f2;
      border-color: #fca5a5;
    }

    .comp-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 0.75rem;
    }

    .comp-verdict {
      font-weight: 700;
      font-size: 0.85rem;
      color: #0f172a;
    }

    .comp-badge {
      font-size: 0.7rem;
      font-weight: 700;
      padding: 0.2rem 0.5rem;
      border-radius: 4px;
    }

    .badge-pass { background: #dcfce7; color: #15803d; }
    .badge-fail { background: #fee2e2; color: #b91c1c; }

    .comp-deltas-grid {
      display: grid;
      grid-template-columns: repeat(4, 1fr);
      gap: 0.75rem;
    }

    .delta-box {
      font-size: 0.8rem;
    }

    .delta-label {
      color: #64748b;
      margin-right: 0.35rem;
    }

    .delta-val {
      font-weight: 700;
      color: #0f172a;
    }

    .text-red { color: #dc2626; }

    .table-responsive {
      overflow-x: auto;
    }

    .benchmark-table {
      width: 100%;
      border-collapse: collapse;
      text-align: left;
      font-size: 0.8rem;
    }

    .benchmark-table th {
      background: #f8fafc;
      color: #475569;
      font-weight: 600;
      padding: 0.65rem 0.75rem;
      border-bottom: 1px solid #e2e8f0;
      white-space: nowrap;
    }

    .benchmark-table td {
      padding: 0.65rem 0.75rem;
      border-bottom: 1px solid #f1f5f9;
      color: #1e293b;
      vertical-align: middle;
    }

    .badge-version {
      background: #e2e8f0;
      color: #334155;
      padding: 0.15rem 0.4rem;
      border-radius: 4px;
      font-size: 0.7rem;
      font-family: monospace;
    }

    .badge-status-pass {
      background: #dcfce7;
      color: #15803d;
      font-size: 0.7rem;
      font-weight: 700;
      padding: 0.15rem 0.4rem;
      border-radius: 4px;
    }

    .empty-cell {
      text-align: center;
      padding: 2rem;
      color: #64748b;
    }
  `]
})
export class QualityComponent implements OnInit {
  metrics = signal<EvaluationMetrics | null>(null);
  benchmarkHistory = signal<BenchmarkRun[]>([]);
  comparison = signal<BenchmarkComparison | null>(null);
  isLoading = signal<boolean>(false);

  selectedBaselineId = '';
  selectedCandidateId = '';

  constructor(private evaluationService: EvaluationService) {}

  ngOnInit(): void {
    this.loadAll();
  }

  loadAll(): void {
    this.isLoading.set(true);
    this.evaluationService.getEvaluationMetrics().subscribe({
      next: (m) => {
        this.metrics.set(m);
      },
      error: () => {}
    });

    this.evaluationService.getBenchmarkHistory().subscribe({
      next: (history) => {
        this.benchmarkHistory.set(history);
        this.isLoading.set(false);
        if (history.length >= 2) {
          this.selectedCandidateId = history[0].id;
          this.selectedBaselineId = history[history.length - 1].id;
        } else if (history.length === 1) {
          this.selectedCandidateId = history[0].id;
          this.selectedBaselineId = history[0].id;
        }
      },
      error: () => {
        this.isLoading.set(false);
      }
    });
  }

  compareRuns(): void {
    if (!this.selectedBaselineId || !this.selectedCandidateId) return;
    this.evaluationService.compareRuns(this.selectedBaselineId, this.selectedCandidateId).subscribe({
      next: (res) => {
        this.comparison.set(res);
      }
    });
  }

  calcBarWidth(val: number | undefined, max: number): number {
    if (!val || val <= 0) return 5;
    return Math.min(100, Math.max(5, Math.round((val / max) * 100)));
  }
}
