import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { DocumentItem } from '../models/document.models';

@Injectable({
  providedIn: 'root'
})
export class DocumentService {
  private readonly baseUrl = `${environment.apiUrl}/documents`;

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

  uploadDocument(file: File, title: string, documentType: string, version: string, source: string): Observable<DocumentItem> {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('title', title);
    formData.append('documentType', documentType);
    formData.append('version', version);
    if (source) formData.append('source', source);

    return this.http.post<DocumentItem>(this.baseUrl, formData);
  }

  deleteDocument(id: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }
}
