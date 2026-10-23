import { Injectable, inject } from '@angular/core';
import { Router } from '@angular/router';

import { AuthService } from './auth.service';

/**
 * Déconnexion volontaire (US-002) : session invalidée côté serveur, puis retour à l'accueil public,
 * le catalogue (ECR-08).
 */
@Injectable({ providedIn: 'root' })
export class SignOutService {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  signOut(): void {
    const leave = () => void this.router.navigateByUrl('/catalogue');
    this.auth.logout().subscribe({ complete: leave, error: leave });
  }
}
