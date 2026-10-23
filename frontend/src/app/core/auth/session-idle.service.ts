import { DestroyRef, Injectable, inject, signal } from '@angular/core';
import { Router } from '@angular/router';

import { AuthService } from './auth.service';

/** RG-03 : la session serveur expire après 30 minutes sans requête. */
export const SESSION_TIMEOUT_MS = 30 * 60 * 1000;
/** Q-19 (WCAG 2.2.1) : avertissement 2 minutes avant l'expiration. */
export const WARNING_BEFORE_MS = 2 * 60 * 1000;
export const IDLE_CHECK_INTERVAL_MS = 15 * 1000;

/**
 * Suit la dernière requête vue par le serveur, affiche l'avertissement d'inactivité et déconnecte
 * localement à l'expiration (UX-socle §5.5).
 */
@Injectable({ providedIn: 'root' })
export class SessionIdleService {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private lastServerActivity = Date.now();

  readonly warningVisible = signal(false);

  constructor() {
    const timer = setInterval(() => this.check(), IDLE_CHECK_INTERVAL_MS);
    inject(DestroyRef).onDestroy(() => clearInterval(timer));
  }

  /** Appelé par l'intercepteur à chaque réponse du serveur. */
  recordActivity(): void {
    this.lastServerActivity = Date.now();
    this.warningVisible.set(false);
  }

  check(): void {
    if (!this.auth.isAuthenticated()) {
      this.warningVisible.set(false);
      return;
    }
    const idle = Date.now() - this.lastServerActivity;
    if (idle >= SESSION_TIMEOUT_MS) {
      this.expire();
    } else if (idle >= SESSION_TIMEOUT_MS - WARNING_BEFORE_MS) {
      this.warningVisible.set(true);
    }
  }

  /** « Rester connecté » : une requête suffit à prolonger la session serveur. */
  stayConnected(): void {
    this.auth.refresh().subscribe({ error: () => this.expire() });
  }

  private expire(): void {
    this.warningVisible.set(false);
    this.auth.clear();
    void this.router.navigate(['/connexion'], { queryParams: { expire: 1 } });
  }
}
