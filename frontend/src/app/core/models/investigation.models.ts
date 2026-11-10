export interface AmlTransaction {
  id: string;
  transactionReference: string;
  transactionDate: string;
  amount: number;
  currency: string;
  transactionType: string;
  counterpartyName: string;
  counterpartyCountry: string;
  counterpartyAccount?: string;
  narrative?: string;
  suspiciousIndicator: boolean;
}

export interface AmlAlert {
  id: string;
  alertReference: string;
  scenarioCode: string;
  scenarioTitle: string;
  customerId: string;
  customerName: string;
  customerRiskLevel: string;
  accountNumber: string;
  totalAlertAmount: number;
  currency: string;
  severity: string;
  status: string;
  triggeredAt: string;
  tenantId: string;
  createdAt: string;
  transactions?: AmlTransaction[];
  investigationId?: string;
  investigationStatus?: string;
}

export interface LinkedPolicy {
  policyCode: string;
  documentTitle: string;
  section: string;
  snippet: string;
  relevanceScore: number;
}

export interface InvestigationTimeline {
  id: string;
  action: string;
  performedBy: string;
  notes: string;
  previousStatus: string;
  newStatus: string;
  recordedAt: string;
}

export interface AmlInvestigationDetail {
  id: string;
  investigationReference: string;
  alert: AmlAlert;
  assignedAnalyst?: string;
  reviewer?: string;
  status: string;
  aiSummary?: string;
  policyRelevanceExplanation?: string;
  sarNarrativeDraft?: string;
  decisionRecommendation?: string;
  analystDecision?: string;
  analystNotes?: string;
  reviewerDecision?: string;
  reviewerNotes?: string;
  escalationReason?: string;
  linkedPolicies: LinkedPolicy[];
  linkedAuditHashes: string[];
  evidenceQualityScore: number;
  evidenceLevel: string;
  tenantId: string;
  completedAt?: string;
  createdAt: string;
  updatedAt: string;
  timeline: InvestigationTimeline[];
}

export interface InvestigationSummary {
  id: string;
  investigationReference: string;
  alertId: string;
  alertReference: string;
  scenarioCode: string;
  scenarioTitle: string;
  customerName: string;
  severity: string;
  totalAlertAmount: number;
  status: string;
  assignedAnalyst?: string;
  decisionRecommendation?: string;
  analystDecision?: string;
  reviewerDecision?: string;
  evidenceQualityScore: number;
  evidenceLevel: string;
  createdAt: string;
  updatedAt: string;
}

export interface InvestigationExport {
  investigationId: string;
  investigationReference: string;
  alertReference: string;
  customerName: string;
  status: string;
  exportedBy: string;
  exportedAt: string;
  reportFormat: string;
  markdownContent: string;
  legalDisclaimer: string;
}

export interface CreateAlertRequest {
  scenarioCode: string;
  scenarioTitle: string;
  customerId: string;
  customerName: string;
  customerRiskLevel: string;
  accountNumber: string;
  totalAlertAmount: number;
  currency: string;
  severity: string;
}

export interface UpdateInvestigationDraftRequest {
  sarNarrativeDraft: string;
  analystNotes?: string;
}

export interface AnalystDecisionRequest {
  decision: 'ACCEPTED' | 'EDITED' | 'REJECTED' | 'REGENERATE_REQUESTED';
  updatedDraft?: string;
  notes?: string;
}

export interface ReviewerDecisionRequest {
  decision: 'APPROVED' | 'RETURNED_FOR_REVISION' | 'ESCALATED_TO_MLRO' | 'REJECTED';
  notes?: string;
  escalationReason?: string;
}
