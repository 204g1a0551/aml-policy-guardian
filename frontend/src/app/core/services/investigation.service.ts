import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  AmlAlert,
  AmlInvestigationDetail,
  InvestigationSummary,
  InvestigationExport,
  CreateAlertRequest,
  UpdateInvestigationDraftRequest,
  AnalystDecisionRequest,
  ReviewerDecisionRequest
} from '../models/investigation.models';

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

@Injectable({
  providedIn: 'root'
})
export class InvestigationService {
  private http = inject(HttpClient);
  private apiUrl = `${environment.apiUrl}/investigations`;

  // Alert Endpoints
  getAlerts(status?: string, severity?: string, scenarioCode?: string, query?: string, page = 0, size = 20): Observable<PageResponse<AmlAlert>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (status) params = params.set('status', status);
    if (severity) params = params.set('severity', severity);
    if (scenarioCode) params = params.set('scenarioCode', scenarioCode);
    if (query) params = params.set('query', query);
    return this.http.get<PageResponse<AmlAlert>>(`${this.apiUrl}/alerts`, { params });
  }

  getAlert(id: string): Observable<AmlAlert> {
    return this.http.get<AmlAlert>(`${this.apiUrl}/alerts/${id}`);
  }

  generateSyntheticAlert(req: CreateAlertRequest): Observable<AmlAlert> {
    return this.http.post<AmlAlert>(`${this.apiUrl}/alerts/generate`, req);
  }

  // Investigation Workflow Endpoints
  getInvestigations(status?: string, assignedAnalyst?: string, query?: string, page = 0, size = 20): Observable<PageResponse<InvestigationSummary>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (status) params = params.set('status', status);
    if (assignedAnalyst) params = params.set('assignedAnalyst', assignedAnalyst);
    if (query) params = params.set('query', query);
    return this.http.get<PageResponse<InvestigationSummary>>(this.apiUrl, { params });
  }

  getInvestigation(id: string): Observable<AmlInvestigationDetail> {
    return this.http.get<AmlInvestigationDetail>(`${this.apiUrl}/${id}`);
  }

  analyzeAlert(alertId: string): Observable<AmlInvestigationDetail> {
    return this.http.post<AmlInvestigationDetail>(`${this.apiUrl}/alerts/${alertId}/analyze`, {});
  }

  updateDraft(id: string, req: UpdateInvestigationDraftRequest): Observable<AmlInvestigationDetail> {
    return this.http.put<AmlInvestigationDetail>(`${this.apiUrl}/${id}/draft`, req);
  }

  submitAnalystDecision(id: string, req: AnalystDecisionRequest): Observable<AmlInvestigationDetail> {
    return this.http.post<AmlInvestigationDetail>(`${this.apiUrl}/${id}/analyst-decision`, req);
  }

  submitReviewerDecision(id: string, req: ReviewerDecisionRequest): Observable<AmlInvestigationDetail> {
    return this.http.post<AmlInvestigationDetail>(`${this.apiUrl}/${id}/reviewer-decision`, req);
  }

  exportReport(id: string): Observable<InvestigationExport> {
    return this.http.get<InvestigationExport>(`${this.apiUrl}/${id}/export`);
  }
}
