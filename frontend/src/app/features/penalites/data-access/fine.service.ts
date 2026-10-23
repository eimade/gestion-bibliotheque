import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE } from '../../../core/http/api';
import { DebtorList, DebtorSort, MemberFines, PaymentRequest, PaymentResult, WaiverResult } from './fine.models';

/** Pénalités (US-038 → US-040) ; `memberNumber` absent = adhérent connecté (US-023). */
@Injectable({ providedIn: 'root' })
export class FineService {
  private readonly http = inject(HttpClient);

  unpaid(memberNumber: string | null): Observable<MemberFines> {
    return this.http.get<MemberFines>(memberNumber ? `${API_BASE}/members/${memberNumber}/fines` : `${API_BASE}/me/fines`);
  }

  collect(body: PaymentRequest): Observable<PaymentResult> {
    return this.http.post<PaymentResult>(`${API_BASE}/payments`, body);
  }

  waive(fineId: number, reason: string): Observable<WaiverResult> {
    return this.http.post<WaiverResult>(`${API_BASE}/fines/${fineId}/waiver`, { reason });
  }

  debtors(sort: DebtorSort, page: number, size: number): Observable<DebtorList> {
    return this.http.get<DebtorList>(`${API_BASE}/fines/debtors`, {
      params: new HttpParams().set('sort', sort).set('page', page).set('size', size),
    });
  }
}
