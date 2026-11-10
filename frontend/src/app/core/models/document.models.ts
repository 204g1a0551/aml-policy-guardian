export type DocumentStatus = 'UPLOADED' | 'PROCESSING' | 'READY' | 'FAILED' | 'DELETED';
export type DocumentType = 'POLICY' | 'PROCEDURE' | 'GUIDELINE' | 'TRAINING' | 'REGULATORY' | 'OTHER';

export type IngestionJobStatus = 'QUEUED' | 'PROCESSING' | 'COMPLETED' | 'FAILED' | 'CANCELLED';
export type IngestionJobStage = 'QUEUED' | 'TEXT_EXTRACTION' | 'CHUNKING' | 'EMBEDDING_GENERATION' | 'VECTOR_STORAGE' | 'COMPLETED' | 'FAILED';

export interface IngestionJob {
  id: string;
  documentId: string;
  tenantId: string;
  jobType: string;
  status: IngestionJobStatus;
  stage: IngestionJobStage;
  progressPct: number;
  retryCount: number;
  maxRetries: number;
  errorMessage?: string;
  correlationId?: string;
  createdAt: string;
  updatedAt: string;
  completedAt?: string;
}

export interface DocumentItem {
  id: string;
  title: string;
  filename: string;
  documentType: DocumentType;
  version: string;
  source: string;
  status: DocumentStatus;
  fileSizeBytes: number;
  sha256Checksum: string;
  mimeType: string;
  errorMessage?: string;
  uploadedBy: string;
  policyCode?: string;
  jurisdiction?: string;
  effectiveDate?: string;
  approvalStatus?: string;
  approvedBy?: string;
  approvedAt?: string;
  isEffective?: boolean;
  supersededBy?: string;
  supersededAt?: string;
  createdAt: string;
  updatedAt: string;
}

export interface PolicyFamilySummary {
  policyCode: string;
  jurisdiction: string;
  latestVersion: string;
  activeEffectiveVersion?: string;
  totalVersions: number;
  hasPendingApproval: boolean;
}

export interface PolicyVersionDetail {
  id: string;
  title: string;
  policyCode: string;
  version: string;
  jurisdiction: string;
  source: string;
  effectiveDate?: string;
  approvalStatus: string;
  approvedBy?: string;
  approvedAt?: string;
  isEffective: boolean;
  supersededBy?: string;
  sha256Checksum: string;
  createdAt: string;
}

export type ChangeClassification = 'ADDITION' | 'DELETION' | 'MODIFIED_OBLIGATION' | 'UNCHANGED';
export type HumanValidationStatus = 'PENDING' | 'VALIDATED' | 'REJECTED';

export interface SectionDiff {
  id: string;
  sectionNumber: string;
  sectionTitle: string;
  changeClassification: ChangeClassification;
  baseContent?: string;
  targetContent?: string;
  requiresHumanValidation: boolean;
  humanValidationStatus: HumanValidationStatus;
  validationNotes?: string;
  validatedBy?: string;
  validatedAt?: string;
  affectedChunkCount: number;
  affectedChunkIds: string[];
}

export interface PolicyComparisonReport {
  policyCode: string;
  baseDocumentId: string;
  baseVersion: string;
  targetDocumentId: string;
  targetVersion: string;
  totalSections: number;
  additions: number;
  deletions: number;
  modifiedObligations: number;
  unchanged: number;
  allHumanValidated: boolean;
  diffs: SectionDiff[];
}

export interface HumanValidationRequest {
  status: 'VALIDATED' | 'REJECTED';
  notes?: string;
}

export interface ComplianceNotification {
  id: string;
  recipientRole: string;
  title: string;
  message: string;
  policyCode?: string;
  documentId?: string;
  notificationType: string;
  read: boolean;
  createdAt: string;
}
