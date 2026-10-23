import { registerLocaleData } from '@angular/common';
import { provideHttpClient, withInterceptors, withXsrfConfiguration } from '@angular/common/http';
import localeFr from '@angular/common/locales/fr';
import {
  ApplicationConfig,
  LOCALE_ID,
  inject,
  provideAppInitializer,
  provideBrowserGlobalErrorListeners,
} from '@angular/core';
import { provideRouter, withComponentInputBinding } from '@angular/router';

import { routes } from './app.routes';
import { AuthService } from './core/auth/auth.service';
import { problemDetailInterceptor } from './core/http/problem-detail.interceptor';
import { sessionActivityInterceptor } from './core/http/session-activity.interceptor';

registerLocaleData(localeFr);

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes, withComponentInputBinding()),
    // ADR-004 : cookie XSRF-TOKEN renvoyé dans l'en-tête X-XSRF-TOKEN (valeurs par défaut d'Angular, explicitées)
    provideHttpClient(
      withXsrfConfiguration({ cookieName: 'XSRF-TOKEN', headerName: 'X-XSRF-TOKEN' }),
      withInterceptors([sessionActivityInterceptor, problemDetailInterceptor]),
    ),
    { provide: LOCALE_ID, useValue: 'fr-FR' },
    // Recharge la session existante avant le premier rendu (et obtient le cookie CSRF)
    provideAppInitializer(() => inject(AuthService).loadSession()),
  ],
};
