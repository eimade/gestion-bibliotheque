import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE } from '../../../core/http/api';
import { PhysicalCondition, WithdrawalReason } from '../../../shared/models/statuses';
import { CopyView } from './catalog.models';

/** Exemplaires, identifiés par leur numéro (US-013). */
@Injectable({ providedIn: 'root' })
export class CopyService {
  private readonly http = inject(HttpClient);
  private readonly url = `${API_BASE}/copies`;

  update(copyNumber: string, shelfMark: string, physicalCondition: PhysicalCondition, version: number): Observable<CopyView> {
    return this.http.put<CopyView>(`${this.url}/${copyNumber}`, { shelfMark, physicalCondition, version });
  }

  withdraw(copyNumber: string, reason: WithdrawalReason): Observable<CopyView> {
    return this.http.post<CopyView>(`${this.url}/${copyNumber}/withdrawal`, { reason });
  }
}
