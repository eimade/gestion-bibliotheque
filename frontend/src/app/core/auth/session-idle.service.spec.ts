import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of, throwError } from 'rxjs';

import { SessionUser } from '../../features/authentification/data-access/account.models';
import { ADMIN } from '../../testing/fixtures';
import { AuthService } from './auth.service';
import { IDLE_CHECK_INTERVAL_MS, SESSION_TIMEOUT_MS, SessionIdleService, WARNING_BEFORE_MS } from './session-idle.service';

describe('SessionIdleService (RG-03, Q-19)', () => {
  const user = signal<SessionUser | null>(ADMIN);
  const auth = {
    user,
    isAuthenticated: () => user() !== null,
    clear: vi.fn(() => user.set(null)),
    refresh: vi.fn(),
  };
  const router = { navigate: vi.fn(() => Promise.resolve(true)) };
  let service: SessionIdleService;

  beforeEach(() => {
    vi.useFakeTimers();
    user.set(ADMIN);
    auth.clear.mockClear();
    router.navigate.mockClear();
    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: auth },
        { provide: Router, useValue: router },
      ],
    });
    service = TestBed.inject(SessionIdleService);
    service.recordActivity();
  });

  afterEach(() => vi.useRealTimers());

  it('n’avertit pas avant 28 minutes d’inactivité', () => {
    vi.advanceTimersByTime(SESSION_TIMEOUT_MS - WARNING_BEFORE_MS - IDLE_CHECK_INTERVAL_MS);
    expect(service.warningVisible()).toBe(false);
  });

  it('avertit 2 minutes avant l’expiration', () => {
    vi.advanceTimersByTime(SESSION_TIMEOUT_MS - WARNING_BEFORE_MS + IDLE_CHECK_INTERVAL_MS);
    expect(service.warningVisible()).toBe(true);
  });

  it('déconnecte localement et renvoie vers la connexion à 30 minutes', () => {
    vi.advanceTimersByTime(SESSION_TIMEOUT_MS + IDLE_CHECK_INTERVAL_MS);

    expect(auth.clear).toHaveBeenCalled();
    expect(router.navigate).toHaveBeenCalledWith(['/connexion'], { queryParams: { expire: 1 } });
    expect(service.warningVisible()).toBe(false);
  });

  it('une réponse du serveur repousse l’expiration', () => {
    vi.advanceTimersByTime(SESSION_TIMEOUT_MS - WARNING_BEFORE_MS + IDLE_CHECK_INTERVAL_MS);
    service.recordActivity();

    expect(service.warningVisible()).toBe(false);
    vi.advanceTimersByTime(SESSION_TIMEOUT_MS - WARNING_BEFORE_MS - IDLE_CHECK_INTERVAL_MS);
    expect(auth.clear).not.toHaveBeenCalled();
  });

  it('« Rester connecté » interroge le serveur ; un échec expire la session', () => {
    auth.refresh.mockReturnValueOnce(of(ADMIN));
    service.stayConnected();
    expect(auth.clear).not.toHaveBeenCalled();

    auth.refresh.mockReturnValueOnce(throwError(() => new Error('401')));
    service.stayConnected();
    expect(auth.clear).toHaveBeenCalled();
  });

  it('ne fait rien sans utilisateur connecté', () => {
    user.set(null);
    vi.advanceTimersByTime(SESSION_TIMEOUT_MS * 2);
    expect(router.navigate).not.toHaveBeenCalled();
  });
});
