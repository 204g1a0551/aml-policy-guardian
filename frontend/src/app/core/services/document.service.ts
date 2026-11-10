import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  DocumentItem,
  IngestionJob,
  PolicyFamilySummary,
  PolicyVersionDetail,
  PolicyComparisonReport,
  SectionDiff,
  HumanValidationRequest,
  ComplianceNotification
} from '../models/document.models';

@Injectable({
  providedIn: 'root'
})
export class DocumentService {
  private readonly baseUrl = `${environment.apiUrl}/documents`;
  private readonly policyUrl = `${environment.apiUrl}/policies`;

  constructor(private http: HttpClient) {}

  getDocuments(status?: string, documentType?: string): Observable<DocumentItem[]> {
    let params = new HttpParams();
    if (status) params = params.set('status', status);
    if (documentType) params = params.set('documentType', documentType);
    return this.http.get<DocumentItem[]>(this.baseUrl, { params });
  }

  getDocument(id: string): Observable<DocumentItem> {
    return this.http.get<DocumentItem>(`${this.baseUrl}/${id}`);
  }

  uploadDocument(
    file: File,
    title: string,
    documentType: string,
    version: string,
    source: string,
    policyCode?: string,
    jurisdiction?: string,
    effectiveDate?: string,
    approvalStatus?: string
  ): Observable<DocumentItem> {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('title', title);
    formData.append('documentType', documentType);
    formData.append('version', version);
    if (source) formData.append('source', source);
    if (policyCode) formData.append('policyCode', policyCode);
    if (jurisdiction) formData.append('jurisdiction', jurisdiction);
    if (effectiveDate) formData.append('effectiveDate', effectiveDate);
    if (approvalStatus) formData.append('approvalStatus', approvalStatus);

    return this.http.post<DocumentItem>(this.baseUrl, formData);
  }

  deleteDocument(id: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }

  // --- Phase 6 Asynchronous Ingestion Job APIs ---

  getIngestionJob(jobId: string): Observable<IngestionJob> {
    return this.http.get<IngestionJob>(`${this.baseUrl}/jobs/${jobId}`);
  }

  getLatestJobForDocument(documentId: string): Observable<IngestionJob> {
    return this.http.get<IngestionJob>(`${this.baseUrl}/${documentId}/job`);
  }

  retryIngestion(documentId: string): Observable<IngestionJob> {
    return this.http.post<IngestionJob>(`${this.baseUrl}/${documentId}/retry`, null);
  }

  // --- Phase 3 Regulatory Versioning APIs ---

  getPolicyFamilies(): Observable<PolicyFamilySummary[]> {
    return this.http.get<PolicyFamilySummary[]>(this.policyUrl);
  }

  getPolicyVersions(policyCode: string): Observable<PolicyVersionDetail[]> {
    return this.http.get<PolicyVersionDetail[]>(`${this.policyUrl}/${encodeURIComponent(policyCode)}/versions`);
  }

  comparePolicies(baseDocumentId: string, targetDocumentId: string): Observable<PolicyComparisonReport> {
    const params = new HttpParams()
      .set('baseDocumentId', baseDocumentId)
      .set('targetDocumentId', targetDocumentId);
    return this.http.get<PolicyComparisonReport>(`${this.policyUrl}/compare`, { params });
  }

  validateDiff(diffId: string, request: HumanValidationRequest): Observable<SectionDiff> {
    return this.http.post<SectionDiff>(`${this.policyUrl}/diff/${diffId}/validate`, request);
  }

  approvePolicyVersion(documentId: string, effectiveDate?: string): Observable<PolicyVersionDetail> {
    let params = new HttpParams();
    if (effectiveDate) {
      params = params.set('effectiveDate', effectiveDate);
    }
    return this.http.post<PolicyVersionDetail>(`${this.policyUrl}/${documentId}/approve`, null, { params });
  }

  getNotifications(unreadOnly = false): Observable<ComplianceNotification[]> {
    const params = new HttpParams().set('unreadOnly', unreadOnly.toString());
    return this.http.get<ComplianceNotification[]>(`${this.policyUrl}/notifications`, { params });
  }

  markNotificationRead(id: string): Observable<void> {
    return this.http.patch<void>(`${this.policyUrl}/notifications/${id}/read`, null);
  }
}
