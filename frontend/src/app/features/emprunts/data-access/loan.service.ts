import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE } from '../../../core/http/api';
import { Page } from '../../../shared/models/page.model';
import {
  CopyLoanHistoryItem,
  CopyRelease,
  HistoryLoan,
  LoanResult,
  LossAmounts,
  MemberLoan,
  OverdueLoan,
  OverdueSort,
  ReturnResult,
} from './loan.models';

/** Emprunts (US-025 → US-031) : guichet, retards, pertes, historique, prolongation. */
@Injectable({ providedIn: 'root' })
export class LoanService {
  private readonly http = inject(HttpClient);

  borrow(memberNumber: string, copyNumber: string): Observable<LoanResult> {
    return this.http.post<LoanResult>(`${API_BASE}/loans`, { memberNumber, copyNumber });
  }

  giveBack(copyNumber: string): Observable<ReturnResult> {
    return this.http.post<ReturnResult>(`${API_BASE}/returns`, { copyNumber });
  }

  /** Prolongation au guichet (AC-027-7) ou par l'adhérent connecté (US-027). */
  renew(loanId: number, asMember: boolean): Observable<MemberLoan> {
    const url = asMember ? `${API_BASE}/me/loans/${loanId}/renewal` : `${API_BASE}/loans/${loanId}/renewal`;
    return this.http.post<MemberLoan>(url, {});
  }

  overdue(minDays: number, sort: OverdueSort, page: number, size: number): Observable<Page<OverdueLoan>> {
    const params = new HttpParams().set('minDays', minDays).set('sort', sort).set('page', page).set('size', size);
    return this.http.get<Page<OverdueLoan>>(`${API_BASE}/loans/overdue`, { params });
  }

  lossPreview(loanId: number): Observable<LossAmounts> {
    return this.http.get<LossAmounts>(`${API_BASE}/loans/${loanId}/loss-preview`);
  }

  declareLost(loanId: number): Observable<LossAmounts> {
    return this.http.post<LossAmounts>(`${API_BASE}/loans/${loanId}/loss`, {});
  }

  markFound(copyNumber: string): Observable<CopyRelease> {
    return this.http.post<CopyRelease>(`${API_BASE}/copies/${copyNumber}/found`, {});
  }

  copyHistory(copyNumber: string, page: number): Observable<Page<CopyLoanHistoryItem>> {
    return this.http.get<Page<CopyLoanHistoryItem>>(`${API_BASE}/copies/${copyNumber}/loans`, {
      params: new HttpParams().set('page', page).set('size', 20),
    });
  }

  memberLoans(memberNumber: string | null): Observable<readonly MemberLoan[]> {
    return this.http.get<readonly MemberLoan[]>(memberNumber ? `${API_BASE}/members/${memberNumber}/loans` : `${API_BASE}/me/loans`);
  }

  history(memberNumber: string | null, page: number): Observable<Page<HistoryLoan>> {
    const url = memberNumber ? `${API_BASE}/members/${memberNumber}/history` : `${API_BASE}/me/history`;
    return this.http.get<Page<HistoryLoan>>(url, { params: new HttpParams().set('page', page).set('size', 20) });
  }
}
