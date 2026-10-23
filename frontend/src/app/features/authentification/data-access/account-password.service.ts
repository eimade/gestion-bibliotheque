import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE } from '../../../core/http/api';

/** Attribution d'un mot de passe temporaire par le personnel (US-006). */
@Injectable({ providedIn: 'root' })
export class AccountPasswordService {
  private readonly http = inject(HttpClient);

  reset(accountId: number, newPassword: string): Observable<void> {
    return this.http.post<void>(`${API_BASE}/accounts/${accountId}/password-reset`, { newPassword });
  }
}
