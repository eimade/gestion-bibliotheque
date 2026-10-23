import { HttpInterceptorFn, HttpResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { tap } from 'rxjs';

import { SessionIdleService } from '../auth/session-idle.service';
import { API_BASE } from './api';

/** Chaque réponse de l'API prolonge la session serveur : on note l'instant (RG-03). */
export const sessionActivityInterceptor: HttpInterceptorFn = (request, next) => {
  if (!request.url.startsWith(API_BASE)) {
    return next(request);
  }
  const idle = inject(SessionIdleService);
  return next(request).pipe(
    tap({
      next: (event) => {
        if (event instanceof HttpResponse) {
          idle.recordActivity();
        }
      },
    }),
  );
};
