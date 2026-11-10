import { describe, it, expect, beforeEach, vi } from 'vitest';
import { of, throwError } from 'rxjs';
import { DocumentService } from '../../core/services/document.service';
import { AuthService } from '../../core/services/auth.service';
import { DocumentsComponent } from './documents.component';
import { DocumentItem, IngestionJob } from '../../core/models/document.models';

describe('DocumentsComponent & Phase 6 Ingestion Jobs', () => {
  let documentService: any;
  let authService: any;
  let component: DocumentsComponent;

  const mockDocs: DocumentItem[] = [
    {
      id: 'doc-1',
      title: 'BSA Compliance Manual',
      filename: 'bsa_manual.pdf',
      documentType: 'POLICY',
      version: 'v1.0',
      source: 'Internal Compliance',
      status: 'READY',
      fileSizeBytes: 2048,
      sha256Checksum: 'hash1',
      mimeType: 'application/pdf',
      uploadedBy: 'user-admin',
      policyCode: 'AML-BSA-001',
      jurisdiction: 'US',
      effectiveDate: '2026-01-01T00:00:00Z',
      approvalStatus: 'APPROVED',
      isEffective: true,
      createdAt: '2026-01-01T00:00:00Z',
      updatedAt: '2026-01-01T00:00:00Z'
    },
    {
      id: 'doc-2',
      title: 'Draft Sanctions SOP',
      filename: 'sanctions_v2.docx',
      documentType: 'PROCEDURE',
      version: 'v2.0',
      source: 'OFAC Notice',
      status: 'FAILED',
      errorMessage: 'OCR Extraction timed out',
      fileSizeBytes: 4096,
      sha256Checksum: 'hash2',
      mimeType: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
      uploadedBy: 'user-admin',
      policyCode: 'AML-SAN-002',
      jurisdiction: 'GLOBAL',
      effectiveDate: '2026-06-01T00:00:00Z',
      approvalStatus: 'DRAFT',
      isEffective: false,
      createdAt: '2026-06-01T00:00:00Z',
      updatedAt: '2026-06-01T00:00:00Z'
    }
  ];

  const mockJob: IngestionJob = {
    id: 'job-123',
    documentId: 'doc-2',
    tenantId: 'default-bank',
    jobType: 'DOCUMENT_INGESTION',
    status: 'QUEUED',
    stage: 'QUEUED',
    progressPct: 5,
    retryCount: 1,
    maxRetries: 3,
    correlationId: 'corr-test',
    createdAt: '2026-06-01T00:00:00Z',
    updatedAt: '2026-06-01T00:00:00Z'
  };

  beforeEach(() => {
    documentService = {
      getDocuments: vi.fn().mockReturnValue(of(mockDocs)),
      getPolicyFamilies: vi.fn().mockReturnValue(of([])),
      getNotifications: vi.fn().mockReturnValue(of([])),
      getIngestionJob: vi.fn().mockReturnValue(of(mockJob)),
      getLatestJobForDocument: vi.fn().mockReturnValue(of(mockJob)),
      retryIngestion: vi.fn().mockReturnValue(of(mockJob)),
      deleteDocument: vi.fn().mockReturnValue(of(void 0))
    };

    authService = {
      isAdmin: vi.fn().mockReturnValue(true),
      isComplianceAnalyst: vi.fn().mockReturnValue(true),
      isAuditor: vi.fn().mockReturnValue(false)
    };

    component = new DocumentsComponent(authService, documentService);
  });

  it('should initialize and load document catalog with status indicators', () => {
    component.ngOnInit();
    expect(documentService.getDocuments).toHaveBeenCalled();
    expect(component.documents().length).toBe(2);
    expect(component.filteredDocuments().length).toBe(2);
    expect(component.filteredDocuments()[0].status).toBe('READY');
    expect(component.filteredDocuments()[1].status).toBe('FAILED');
  });

  it('should filter documents by status and type', () => {
    component.ngOnInit();
    component.selectedStatusFilter = 'FAILED';
    expect(component.filteredDocuments().length).toBe(1);
    expect(component.filteredDocuments()[0].id).toBe('doc-2');

    component.selectedStatusFilter = '';
    component.selectedTypeFilter = 'POLICY';
    expect(component.filteredDocuments().length).toBe(1);
    expect(component.filteredDocuments()[0].id).toBe('doc-1');
  });

  it('should trigger retryIngestion and update alert banners', () => {
    component.ngOnInit();
    component.retryIngestion('doc-2', 'Draft Sanctions SOP');

    expect(documentService.retryIngestion).toHaveBeenCalledWith('doc-2');
    expect(component.actionSuccess()).toContain('Re-ingestion triggered for "Draft Sanctions SOP"');
    expect(component.actionSuccess()).toContain('job-123');
  });

  it('should handle retry error gracefully', () => {
    documentService.retryIngestion.mockReturnValue(throwError(() => ({
      error: { detail: 'Kafka ingestion queue is currently paused' }
    })));

    component.retryIngestion('doc-2', 'Draft Sanctions SOP');
    expect(component.actionError()).toBe('Kafka ingestion queue is currently paused');
  });
});
