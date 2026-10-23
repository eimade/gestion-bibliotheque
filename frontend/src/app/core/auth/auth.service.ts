import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, catchError, finalize, firstValueFrom, map, of, tap } from 'rxjs';

import { SessionUser } from '../../features/authentification/data-access/account.models';
import { AuthApiService } from '../../features/authentification/data-access/auth-api.service';

/** État de la session en signal (UX-socle §5.5). */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly api = inject(AuthApiService);
  private readonly current = signal<SessionUser | null>(null);

  readonly user = this.current.asReadonly();
  readonly isAuthenticated = computed(() => this.current() !== null);

  /** Au démarrage : recharge une session existante (un 401 signifie simplement « non connecté »). */
  loadSession(): Promise<void> {
    return firstValueFrom(
      this.api.me().pipe(
        tap((user) => this.current.set(user)),
        map(() => undefined),
        catchError(() => {
          this.current.set(null);
          return of(undefined);
        }),
      ),
    );
  }

  refresh(): Observable<SessionUser> {
    return this.api.me().pipe(tap((user) => this.current.set(user)));
  }

  login(email: string, password: string): Observable<SessionUser> {
    return this.api.login({ email, password }).pipe(tap((user) => this.current.set(user)));
  }

  logout(): Observable<void> {
    return this.api.logout().pipe(finalize(() => this.current.set(null)));
  }

  changePassword(currentPassword: string, newPassword: string): Observable<void> {
    return this.api
      .changePassword({ currentPassword, newPassword })
      .pipe(tap(() => this.current.update((user) => user && { ...user, passwordTemporary: false })));
  }

  /** Session perdue côté serveur (401, inactivité) : l'état local est vidé. */
  clear(): void {
    this.current.set(null);
  }
}

/** Accueil par rôle (UX-socle §2.3). */
export function homeUrlFor(user: SessionUser): string {
  return user.role === 'MEMBER' ? '/mon-espace' : '/gestion';
}
