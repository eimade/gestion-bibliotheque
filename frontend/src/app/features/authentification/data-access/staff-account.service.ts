import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE } from '../../../core/http/api';
import { Page } from '../../../shared/models/page.model';
import { CreateStaffAccountRequest, StaffAccount, StaffAccountQuery } from './account.models';

/** Comptes du personnel (US-004, US-005), réservés à l'administrateur. */
@Injectable({ providedIn: 'root' })
export class StaffAccountService {
  private readonly http = inject(HttpClient);
  private readonly url = `${API_BASE}/staff-accounts`;

  list(query: StaffAccountQuery): Observable<Page<StaffAccount>> {
    let params = new HttpParams()
      .set('page', query.page)
      .set('size', query.size)
      .set('sort', `${query.sortField},${query.sortDirection}`);
    if (query.q) {
      params = params.set('q', query.q);
    }
    if (query.role) {
      params = params.set('role', query.role);
    }
    if (query.status) {
      params = params.set('status', query.status);
    }
    return this.http.get<Page<StaffAccount>>(this.url, { params });
  }

  create(body: CreateStaffAccountRequest): Observable<StaffAccount> {
    return this.http.post<StaffAccount>(this.url, body);
  }

  deactivate(id: number): Observable<StaffAccount> {
    return this.http.post<StaffAccount>(`${this.url}/${id}/deactivation`, null);
  }

  reactivate(id: number): Observable<StaffAccount> {
    return this.http.post<StaffAccount>(`${this.url}/${id}/reactivation`, null);
  }
}
