import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { AuthService } from './auth.service';

/** RG-05 / AC-001-7 : un mot de passe temporaire impose ECR-04 avant toute autre page. */
export const passwordChangeGuard: CanActivateFn = () => {
  if (!inject(AuthService).user()?.passwordTemporary) {
    return true;
  }
  return inject(Router).createUrlTree(['/compte/mot-de-passe'], { queryParams: { obligatoire: 1 } });
};
