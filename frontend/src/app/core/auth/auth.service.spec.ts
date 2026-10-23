import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { ADMIN } from '../../testing/fixtures';
import { AuthService, homeUrlFor } from './auth.service';

describe('AuthService', () => {
  let service: AuthService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('recharge une session existante au démarrage', async () => {
    const loading = service.loadSession();
    http.expectOne('/api/v1/auth/me').flush(ADMIN);
    await loading;

    expect(service.user()).toEqual(ADMIN);
    expect(service.isAuthenticated()).toBe(true);
  });

  it('considère un 401 au démarrage comme « non connecté »', async () => {
    const loading = service.loadSession();
    http.expectOne('/api/v1/auth/me').flush({ code: 'AUTHENTICATION_REQUIRED' }, { status: 401, statusText: 'Non' });
    await loading;

    expect(service.isAuthenticated()).toBe(false);
  });

  it('mémorise l’utilisateur après la connexion', () => {
    service.login('admin@exemple.fr', 'Admin2026Secret').subscribe();
    const request = http.expectOne('/api/v1/auth/login');
    expect(request.request.body).toEqual({ email: 'admin@exemple.fr', password: 'Admin2026Secret' });
    request.flush(ADMIN);

    expect(service.user()?.accountId).toBe(1);
  });

  it('vide la session à la déconnexion, même en cas d’erreur serveur', () => {
    service.login('a@b.fr', 'x').subscribe();
    http.expectOne('/api/v1/auth/login').flush(ADMIN);

    service.logout().subscribe({ error: () => undefined });
    http.expectOne('/api/v1/auth/logout').flush(null, { status: 500, statusText: 'Erreur' });

    expect(service.user()).toBeNull();
  });

  it('lève le caractère temporaire après un changement de mot de passe', () => {
    service.login('a@b.fr', 'x').subscribe();
    http.expectOne('/api/v1/auth/login').flush({ ...ADMIN, passwordTemporary: true });

    service.changePassword('Ancien2026Secret', 'Nouveau2026Secret').subscribe();
    http.expectOne('/api/v1/auth/password').flush(null, { status: 204, statusText: 'No Content' });

    expect(service.user()?.passwordTemporary).toBe(false);
  });

  it('dirige chaque rôle vers son accueil', () => {
    expect(homeUrlFor(ADMIN)).toBe('/gestion');
    expect(homeUrlFor({ ...ADMIN, role: 'MEMBER' })).toBe('/mon-espace');
  });
});
