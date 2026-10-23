import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE } from '../../../core/http/api';
import { ChangePasswordRequest, LoginRequest, SessionUser } from './account.models';

/** Session de l'utilisateur (ARCH-authentification §3) ; utilisé par `core/auth/AuthService`. */
@Injectable({ providedIn: 'root' })
export class AuthApiService {
  private readonly http = inject(HttpClient);

  me(): Observable<SessionUser> {
    return this.http.get<SessionUser>(`${API_BASE}/auth/me`);
  }

  login(body: LoginRequest): Observable<SessionUser> {
    return this.http.post<SessionUser>(`${API_BASE}/auth/login`, body);
  }

  logout(): Observable<void> {
    return this.http.post<void>(`${API_BASE}/auth/logout`, null);
  }

  changePassword(body: ChangePasswordRequest): Observable<void> {
    return this.http.post<void>(`${API_BASE}/auth/password`, body);
  }
}
