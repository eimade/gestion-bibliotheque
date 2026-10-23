import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE } from '../../../core/http/api';
import { Page } from '../../../shared/models/page.model';
import {
  AddCopiesRequest,
  AdminSearchQuery,
  BookAdminDetail,
  BookAdminItem,
  BookRequest,
  CopyView,
} from './catalog.models';

/** Notices côté personnel (US-009 → US-012). */
@Injectable({ providedIn: 'root' })
export class BookService {
  private readonly http = inject(HttpClient);
  private readonly url = `${API_BASE}/books`;

  search(query: AdminSearchQuery): Observable<Page<BookAdminItem>> {
    let params = new HttpParams().set('page', query.page).set('size', query.size).set('sort', query.sort);
    if (query.q) {
      params = params.set('q', query.q);
    }
    if (query.categoryId !== null) {
      params = params.set('categoryId', query.categoryId);
    }
    if (query.includeWithdrawn) {
      params = params.set('includeWithdrawn', true);
    }
    return this.http.get<Page<BookAdminItem>>(this.url, { params });
  }

  get(id: number): Observable<BookAdminDetail> {
    return this.http.get<BookAdminDetail>(`${this.url}/${id}`);
  }

  create(body: BookRequest): Observable<BookAdminDetail> {
    return this.http.post<BookAdminDetail>(this.url, body);
  }

  update(id: number, body: BookRequest): Observable<BookAdminDetail> {
    return this.http.put<BookAdminDetail>(`${this.url}/${id}`, body);
  }

  remove(id: number): Observable<void> {
    return this.http.delete<void>(`${this.url}/${id}`);
  }

  addCopies(id: number, body: AddCopiesRequest): Observable<readonly CopyView[]> {
    return this.http.post<readonly CopyView[]>(`${this.url}/${id}/copies`, body);
  }
}
