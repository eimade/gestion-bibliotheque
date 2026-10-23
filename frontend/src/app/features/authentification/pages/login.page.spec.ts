import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';

import { problemDetailInterceptor } from '../../../core/http/problem-detail.interceptor';
import { ADMIN } from '../../../testing/fixtures';
import { LoginPage } from './login.page';

describe('LoginPage (ECR-03)', () => {
  let fixture: ComponentFixture<LoginPage>;
  let http: HttpTestingController;
  let navigated: string[];

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([problemDetailInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    navigated = [];
    vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockImplementation((url) => {
      navigated.push(String(url));
      return Promise.resolve(true);
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(LoginPage);
    await fixture.whenStable();
  });

  afterEach(() => http.verify());

  const host = () => fixture.nativeElement as HTMLElement;
  const byTestId = (id: string) => host().querySelector<HTMLElement>(`[data-testid="${id}"]`);

  async function submitCredentials(): Promise<void> {
    for (const [id, value] of [['login-email-input', 'admin@exemple.fr'], ['login-password-input', 'Admin2026Secret']]) {
      const input = byTestId(id) as HTMLInputElement;
      input.value = value;
      input.dispatchEvent(new Event('input'));
    }
    byTestId('login-submit')?.click();
    await fixture.whenStable();
  }

  it('affiche le bandeau de session expirée', async () => {
    fixture.componentRef.setInput('expire', '1');
    await fixture.whenStable();

    expect(byTestId('login-session-expired')?.textContent).toContain('Votre session a expiré. Veuillez vous reconnecter.');
  });

  it('dirige vers l’accueil du rôle après la connexion', async () => {
    await submitCredentials();
    http.expectOne('/api/v1/auth/login').flush(ADMIN);

    expect(navigated).toEqual(['/gestion']);
  });

  it('impose le changement d’un mot de passe temporaire (AC-001-7)', async () => {
    fixture.componentRef.setInput('retour', '/gestion/comptes');
    await submitCredentials();
    http.expectOne('/api/v1/auth/login').flush({ ...ADMIN, passwordTemporary: true });

    expect(navigated).toEqual(['/compte/mot-de-passe?obligatoire=1']);
  });

  it('revient à l’adresse demandée si elle est interne', async () => {
    fixture.componentRef.setInput('retour', '/gestion/comptes');
    await submitCredentials();
    http.expectOne('/api/v1/auth/login').flush(ADMIN);

    expect(navigated).toEqual(['/gestion/comptes']);
  });

  it('ignore une adresse de retour externe', async () => {
    fixture.componentRef.setInput('retour', '//pirate.example/');
    await submitCredentials();
    http.expectOne('/api/v1/auth/login').flush(ADMIN);

    expect(navigated).toEqual(['/gestion']);
  });

  it('affiche le message exact d’un refus', async () => {
    await submitCredentials();
    http.expectOne('/api/v1/auth/login').flush({ code: 'ACCOUNT_DISABLED' }, { status: 401, statusText: 'Non' });
    await fixture.whenStable();

    expect(byTestId('login-server-error')?.textContent).toContain('Compte désactivé, contactez la bibliothèque.');
  });

});
