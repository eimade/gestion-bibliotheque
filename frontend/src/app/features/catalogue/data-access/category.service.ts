import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE } from '../../../core/http/api';
import { Page } from '../../../shared/models/page.model';
import { CategoryResponse } from './catalog.models';

/** Gestion des catégories (US-014). */
@Injectable({ providedIn: 'root' })
export class CategoryService {
  private readonly http = inject(HttpClient);
  private readonly url = `${API_BASE}/categories`;

  list(page: number, size: number): Observable<Page<CategoryResponse>> {
    const params = new HttpParams().set('page', page).set('size', size).set('sort', 'label,asc');
    return this.http.get<Page<CategoryResponse>>(this.url, { params });
  }

  create(label: string): Observable<CategoryResponse> {
    return this.http.post<CategoryResponse>(this.url, { label });
  }

  rename(id: number, label: string, version: number): Observable<CategoryResponse> {
    return this.http.put<CategoryResponse>(`${this.url}/${id}`, { label, version });
  }

  remove(id: number): Observable<void> {
    return this.http.delete<void>(`${this.url}/${id}`);
  }
}
