import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AuditLogItem } from '../models/audit.models';

@Injectable({
  providedIn: 'root'
})
export class AuditService {
  private readonly baseUrl = `${environment.apiUrl}/admin/audit`;

  constructor(private http: HttpClient) {}

  getAuditLogs(): Observable<AuditLogItem[]> {
    return this.http.get<AuditLogItem[]>(this.baseUrl);
  }
}
