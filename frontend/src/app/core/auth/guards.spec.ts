import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, UrlTree, provideRouter } from '@angular/router';

import { SessionUser } from '../../features/authentification/data-access/account.models';
import { ADMIN, LIBRARIAN, MEMBER } from '../../testing/fixtures';
import { authGuard } from './auth.guard';
import { AuthService } from './auth.service';
import { guestGuard } from './guest.guard';
import { passwordChangeGuard } from './password-change.guard';
import { roleGuard } from './role.guard';

describe('Guards', () => {
  const user = signal<SessionUser | null>(null);

  beforeEach(() => {
    user.set(null);
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: { user, isAuthenticated: () => user() !== null } },
      ],
    });
  });

  function run(guard: typeof authGuard, data: Record<string, unknown> = {}, url = '/gestion/comptes') {
    const route = { data } as unknown as ActivatedRouteSnapshot;
    const state = { url } as RouterStateSnapshot;
    return TestBed.runInInjectionContext(() => guard(route, state));
  }

  function urlOf(result: unknown): string {
    return TestBed.inject(Router).serializeUrl(result as UrlTree);
  }

  it('authGuard renvoie vers la connexion avec l’adresse demandée', () => {
    expect(urlOf(run(authGuard))).toBe('/connexion?retour=%2Fgestion%2Fcomptes');
  });

  it('authGuard laisse passer un utilisateur connecté', () => {
    user.set(ADMIN);
    expect(run(authGuard)).toBe(true);
  });

  it('roleGuard refuse un rôle non autorisé', () => {
    user.set(LIBRARIAN);
    expect(urlOf(run(roleGuard, { roles: ['ADMIN'] }))).toBe('/acces-refuse');
  });

  it('roleGuard accepte un rôle autorisé', () => {
    user.set(ADMIN);
    expect(run(roleGuard, { roles: ['LIBRARIAN', 'ADMIN'] })).toBe(true);
  });

  it('passwordChangeGuard impose le changement d’un mot de passe temporaire (AC-001-7)', () => {
    user.set({ ...ADMIN, passwordTemporary: true });
    expect(urlOf(run(passwordChangeGuard))).toBe('/compte/mot-de-passe?obligatoire=1');
  });

  it('passwordChangeGuard laisse passer un mot de passe définitif', () => {
    user.set(ADMIN);
    expect(run(passwordChangeGuard)).toBe(true);
  });

  it('guestGuard renvoie un adhérent connecté vers son espace', () => {
    user.set(MEMBER);
    expect(urlOf(run(guestGuard))).toBe('/mon-espace');
  });

  it('guestGuard laisse un visiteur accéder à la connexion', () => {
    expect(run(guestGuard)).toBe(true);
  });
});
