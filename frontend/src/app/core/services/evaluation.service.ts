import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  EvaluationMetrics,
  BenchmarkRun,
  RegressionAlert,
  BenchmarkComparison
} from '../models/evaluation.models';

@Injectable({
  providedIn: 'root'
})
export class EvaluationService {
  private readonly baseUrl = `${environment.apiUrl}/admin/evaluation`;

  constructor(private http: HttpClient) {}

  getEvaluationMetrics(): Observable<EvaluationMetrics> {
    return this.http.get<EvaluationMetrics>(`${this.baseUrl}/metrics`);
  }

  getBenchmarkHistory(): Observable<BenchmarkRun[]> {
    return this.http.get<BenchmarkRun[]>(`${this.baseUrl}/history`);
  }

  getRegressionAlerts(): Observable<RegressionAlert[]> {
    return this.http.get<RegressionAlert[]>(`${this.baseUrl}/alerts`);
  }

  compareRuns(baselineId: string, candidateId: string): Observable<BenchmarkComparison> {
    const params = new HttpParams()
      .set('baselineId', baselineId)
      .set('candidateId', candidateId);
    return this.http.get<BenchmarkComparison>(`${this.baseUrl}/compare`, { params });
  }
}
