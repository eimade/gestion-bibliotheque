import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { AuthService, homeUrlFor } from './auth.service';

/** Sur `/connexion` : un utilisateur déjà connecté est renvoyé vers l'accueil de son rôle. */
export const guestGuard: CanActivateFn = () => {
  const user = inject(AuthService).user();
  return user ? inject(Router).createUrlTree([homeUrlFor(user)]) : true;
};
