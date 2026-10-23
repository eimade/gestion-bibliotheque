import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { Role } from '../../shared/models/statuses';
import { AuthService } from './auth.service';

/**
 * Exige l'un des rôles déclarés dans `data.roles` de la route ; sinon page « Accès refusé » (ECR-02).
 * Protection d'interface seulement : le serveur reste juge des droits (NFR-06).
 */
export const roleGuard: CanActivateFn = (route) => {
  const roles = (route.data['roles'] ?? []) as readonly Role[];
  const user = inject(AuthService).user();
  if (user && roles.includes(user.role)) {
    return true;
  }
  return inject(Router).createUrlTree(['/acces-refuse']);
};
