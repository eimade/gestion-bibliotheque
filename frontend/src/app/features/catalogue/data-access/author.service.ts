import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE } from '../../../core/http/api';
import { AuthorView } from './catalog.models';

/** Suggestions et création d'auteurs (US-009, AC-009-2). */
@Injectable({ providedIn: 'root' })
export class AuthorService {
  private readonly http = inject(HttpClient);
  private readonly url = `${API_BASE}/authors`;

  suggest(q: string): Observable<readonly AuthorView[]> {
    return this.http.get<readonly AuthorView[]>(this.url, { params: new HttpParams().set('q', q) });
  }

  create(lastName: string, firstName: string | null): Observable<AuthorView> {
    return this.http.post<AuthorView>(this.url, { lastName, firstName });
  }
}
