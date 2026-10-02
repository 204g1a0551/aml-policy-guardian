export type DocumentStatus = 'UPLOADED' | 'PROCESSING' | 'READY' | 'FAILED' | 'DELETED';
export type DocumentType = 'POLICY' | 'PROCEDURE' | 'GUIDELINE' | 'TRAINING' | 'REGULATORY' | 'OTHER';

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
  createdAt: string;
  updatedAt: string;
}
