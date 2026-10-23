import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';

import { problem } from '../../testing/fixtures';
import { AuthService } from '../auth/auth.service';
import { ToastService } from '../notifications/toast.service';
import { ApiError } from './api-error';
import { GENERIC_MESSAGES } from './messages';
import { problemDetailInterceptor } from './problem-detail.interceptor';

describe('problemDetailInterceptor', () => {
  let http: HttpClient;
  let controller: HttpTestingController;
  const toasts = { error: vi.fn() };
  const auth = { clear: vi.fn() };
  const router = { navigate: vi.fn(() => Promise.resolve(true)) };

  beforeEach(() => {
    toasts.error.mockClear();
    auth.clear.mockClear();
    router.navigate.mockClear();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([problemDetailInterceptor])),
        provideHttpClientTesting(),
        { provide: ToastService, useValue: toasts },
        { provide: AuthService, useValue: auth },
        { provide: Router, useValue: router },
      ],
    });
    http = TestBed.inject(HttpClient);
    controller = TestBed.inject(HttpTestingController);
  });

  afterEach(() => controller.verify());

  function failWith(url: string, response: ReturnType<typeof problem>): ApiError {
    let caught: unknown;
    http.get(url).subscribe({ error: (error: unknown) => (caught = error) });
    controller.expectOne(url).flush(response.body, { status: response.status, statusText: response.statusText });
    return caught as ApiError;
  }

  it('convertit un ProblemDetail en ApiError avec ses propriétés', () => {
    const error = failWith('/api/v1/auth/login', problem(401, 'ACCOUNT_LOCKED', { lockedUntil: '2026-10-17T10:15:00Z' }));

    expect(error).toBeInstanceOf(ApiError);
    expect(error.code).toBe('ACCOUNT_LOCKED');
    expect(error.property('lockedUntil')).toBe('2026-10-17T10:15:00Z');
  });

  it('un 401 hors sonde de session renvoie vers la connexion', () => {
    failWith('/api/v1/staff-accounts', problem(401, 'AUTHENTICATION_REQUIRED'));

    expect(auth.clear).toHaveBeenCalled();
    expect(router.navigate).toHaveBeenCalledWith(['/connexion'], { queryParams: { expire: 1 } });
  });

  it('un 401 de connexion reste une erreur de formulaire', () => {
    failWith('/api/v1/auth/login', problem(401, 'INVALID_CREDENTIALS'));
    expect(router.navigate).not.toHaveBeenCalled();
  });

  it('une erreur serveur affiche le message générique', () => {
    failWith('/api/v1/staff-accounts', problem(500, 'INTERNAL_ERROR'));
    expect(toasts.error).toHaveBeenCalledWith(GENERIC_MESSAGES.unexpected);
  });

  it('un 403 de droits affiche le message de droits', () => {
    failWith('/api/v1/staff-accounts', problem(403, 'ACCESS_DENIED'));
    expect(toasts.error).toHaveBeenCalledWith(GENERIC_MESSAGES.forbidden);
  });

  it('un refus métier n’affiche aucun message global', () => {
    const error = failWith('/api/v1/staff-accounts', problem(409, 'LAST_ADMIN_REQUIRED'));
    expect(error.code).toBe('LAST_ADMIN_REQUIRED');
    expect(toasts.error).not.toHaveBeenCalled();
  });

  it('un jeton CSRF invalide est rafraîchi puis la requête rejouée une fois', () => {
    let result: unknown;
    http.post('/api/v1/auth/logout', null).subscribe((value) => (result = value));

    controller.expectOne('/api/v1/auth/logout').flush(problem(403, 'CSRF_TOKEN_INVALID').body, {
      status: 403,
      statusText: 'Interdit',
    });
    controller.expectOne('/api/v1/auth/me').flush({});
    controller.expectOne('/api/v1/auth/logout').flush({ ok: true });

    expect(result).toEqual({ ok: true });
  });
});
