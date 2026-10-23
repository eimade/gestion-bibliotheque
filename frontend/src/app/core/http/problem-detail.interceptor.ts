import {
  HttpBackend,
  HttpClient,
  HttpContextToken,
  HttpErrorResponse,
  HttpInterceptorFn,
  HttpXsrfTokenExtractor,
} from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, of, switchMap, throwError } from 'rxjs';

import { AuthService } from '../auth/auth.service';
import { ToastService } from '../notifications/toast.service';
import { API_BASE } from './api';
import { ApiError } from './api-error';
import { GENERIC_MESSAGES } from './messages';

/** Marque une requête déjà rejouée après un jeton CSRF invalide (une seule tentative). */
const CSRF_RETRIED = new HttpContextToken<boolean>(() => false);

/** Requêtes dont un 401 est une réponse normale et non une session expirée. */
const SESSION_PROBES = [`${API_BASE}/auth/me`, `${API_BASE}/auth/login`];

/**
 * Traduit toute erreur HTTP en {@link ApiError} (ADR-003) et gère les cas transverses :
 * réseau / 5xx / 403 → message global ; 401 → retour à la connexion ; CSRF invalide → jeton
 * rafraîchi puis requête rejouée une fois (ARCH-authentification §3).
 */
export const problemDetailInterceptor: HttpInterceptorFn = (request, next) => {
  const toasts = inject(ToastService);
  const auth = inject(AuthService);
  const router = inject(Router);
  const rawHttp = new HttpClient(inject(HttpBackend));
  const xsrf = inject(HttpXsrfTokenExtractor);

  return next(request).pipe(
    catchError((response: unknown) => {
      if (!(response instanceof HttpErrorResponse)) {
        return throwError(() => response);
      }
      const error = ApiError.fromResponse(response);

      if (error.code === 'CSRF_TOKEN_INVALID' && !request.context.get(CSRF_RETRIED)) {
        return rawHttp.get(`${API_BASE}/auth/me`).pipe(
          catchError(() => of(null)),
          switchMap(() =>
            next(
              request.clone({
                setHeaders: { 'X-XSRF-TOKEN': xsrf.getToken() ?? '' },
                context: request.context.set(CSRF_RETRIED, true),
              }),
            ),
          ),
          catchError((retryResponse: unknown) =>
            throwError(() =>
              retryResponse instanceof HttpErrorResponse ? ApiError.fromResponse(retryResponse) : retryResponse,
            ),
          ),
        );
      }

      if (error.status === 0) {
        toasts.error(GENERIC_MESSAGES.network);
      } else if (error.status >= 500) {
        toasts.error(GENERIC_MESSAGES.unexpected);
      } else if (error.status === 401 && !SESSION_PROBES.includes(request.url)) {
        auth.clear();
        void router.navigate(['/connexion'], { queryParams: { expire: 1 } });
      } else if (error.status === 403 && error.code === 'ACCESS_DENIED') {
        toasts.error(GENERIC_MESSAGES.forbidden);
      }
      return throwError(() => error);
    }),
  );
};
