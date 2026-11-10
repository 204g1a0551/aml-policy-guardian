export interface PerformanceSnapshot {
  totalRequests: number;
  successfulResponses: number;
  refusals: number;
  modelFailures: number;
  successRatePercentage: number;
  refusalRatePercentage: number;
  errorRatePercentage: number;
  p50TotalLatencyMs: number;
  p95TotalLatencyMs: number;
  p99TotalLatencyMs: number;
  p50RetrievalLatencyMs: number;
  p95RetrievalLatencyMs: number;
  p50GenerationLatencyMs: number;
  p95GenerationLatencyMs: number;
  p50TimeToFirstTokenMs: number;
  p95TimeToFirstTokenMs: number;
  totalPromptTokens: number;
  totalCompletionTokens: number;
  totalEstimatedCostUsd: number;
  highQualityCount: number;
  mediumQualityCount: number;
  lowQualityCount: number;
  insufficientQualityCount: number;
}

export interface RegressionAlert {
  id: string;
  alertType: string;
  severity: 'CRITICAL' | 'WARNING' | 'INFO';
  metricName: string;
  currentValue: number;
  threshold: number;
  message: string;
  triggeredAt: string;
}

export interface EvaluationMetrics {
  performanceSnapshot: PerformanceSnapshot;
  systemHealth: 'OPTIMAL' | 'DEGRADED' | 'CRITICAL';
  regressionAlertsActive: boolean;
  activeAlerts: RegressionAlert[];
  generatedAt: string;
}

export interface BenchmarkRun {
  id: string;
  benchmarkVersion: string;
  totalInquiries: number;
  passedInquiries: number;
  passRatePercentage: number;
  groundednessRate: number;
  retrievalRecallRate: number;
  citationCorrectnessRate: number;
  refusalQualityRate: number;
  meanLatencyMs: number;
  p50LatencyMs: number;
  p95LatencyMs: number;
  timestamp: string;
  triggeredBy: string;
  status: string;
}

export interface BenchmarkComparison {
  baselineId: string;
  candidateId: string;
  passRateDelta: number;
  groundednessDelta: number;
  recallDelta: number;
  citationDelta: number;
  refusalDelta: number;
  p95LatencyDeltaMs: number;
  isRegression: boolean;
  summaryVerdict: string;
}
