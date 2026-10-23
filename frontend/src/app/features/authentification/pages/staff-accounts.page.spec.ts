import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, TestRequest, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { problemDetailInterceptor } from '../../../core/http/problem-detail.interceptor';
import { ToastService } from '../../../core/notifications/toast.service';
import { staffAccount } from '../../../testing/fixtures';
import { StaffAccountsPage } from './staff-accounts.page';

describe('StaffAccountsPage (ECR-05)', () => {
  let fixture: ComponentFixture<StaffAccountsPage>;
  let http: HttpTestingController;
  let toasts: ToastService;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([problemDetailInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpTestingController);
    toasts = TestBed.inject(ToastService);
    fixture = TestBed.createComponent(StaffAccountsPage);
    await fixture.whenStable();
  });

  afterEach(() => http.verify());

  const host = () => fixture.nativeElement as HTMLElement;
  const byTestId = (id: string) => host().querySelector<HTMLElement>(`[data-testid="${id}"]`);
  const listRequest = (): TestRequest => http.expectOne((request) => request.url === '/api/v1/staff-accounts');

  function page(content = [staffAccount()]) {
    return { content, page: 0, size: 20, totalElements: content.length, totalPages: 1 };
  }

  it('affiche le chargement puis la liste triée par nom', async () => {
    expect(byTestId('staff-accounts-loading')).not.toBeNull();
    const request = listRequest();
    expect(request.request.params.get('sort')).toBe('lastName,asc');
    expect(request.request.params.get('size')).toBe('20');
    request.flush(page());
    await fixture.whenStable();

    expect(host().querySelectorAll('[data-testid="staff-accounts-row"]')).toHaveLength(1);
  });

  it('affiche l’état vide avec le message exact', async () => {
    listRequest().flush(page([]));
    await fixture.whenStable();

    expect(byTestId('staff-accounts-empty')?.textContent).toContain('Aucun compte ne correspond à votre recherche.');
  });

  it('affiche l’erreur et relance la même lecture', async () => {
    listRequest().flush({ code: 'INTERNAL_ERROR' }, { status: 500, statusText: 'Erreur' });
    await fixture.whenStable();
    expect(byTestId('staff-accounts-error')).not.toBeNull();

    byTestId('staff-accounts-error-retry')?.click();
    await fixture.whenStable();
    listRequest().flush(page());
    await fixture.whenStable();

    expect(host().querySelectorAll('[data-testid="staff-accounts-row"]')).toHaveLength(1);
  });

  it('filtre par rôle et revient à la première page', async () => {
    listRequest().flush(page());
    await fixture.whenStable();

    const select = byTestId('staff-accounts-role-filter') as HTMLSelectElement;
    select.value = 'ADMIN';
    select.dispatchEvent(new Event('change'));
    await fixture.whenStable();

    const request = listRequest();
    expect(request.request.params.get('role')).toBe('ADMIN');
    expect(request.request.params.get('page')).toBe('0');
    request.flush(page());
  });

  it('désactive un compte après confirmation et rafraîchit la liste', async () => {
    const success = vi.spyOn(toasts, 'success');
    listRequest().flush(page());
    await fixture.whenStable();

    byTestId('staff-accounts-row-menu')?.click();
    await fixture.whenStable();
    byTestId('staff-accounts-disable-action')?.click();
    await fixture.whenStable();
    expect(byTestId('disable-account-dialog')?.textContent).toContain(
      'Désactiver le compte de Marie DUPONT ? L’agent perdra immédiatement tout accès.',
    );

    byTestId('disable-account-confirm')?.click();
    http.expectOne('/api/v1/staff-accounts/5/deactivation').flush(staffAccount({ status: 'DISABLED' }));
    await fixture.whenStable();

    expect(success).toHaveBeenCalledWith('Compte désactivé.');
    listRequest().flush(page());
  });

  it('signale le refus « dernier administrateur »', async () => {
    const error = vi.spyOn(toasts, 'error');
    listRequest().flush(page([staffAccount({ role: 'ADMIN' })]));
    await fixture.whenStable();

    byTestId('staff-accounts-row-menu')?.click();
    await fixture.whenStable();
    byTestId('staff-accounts-disable-action')?.click();
    await fixture.whenStable();
    byTestId('disable-account-confirm')?.click();
    http.expectOne('/api/v1/staff-accounts/5/deactivation')
      .flush({ code: 'LAST_ADMIN_REQUIRED' }, { status: 409, statusText: 'Conflit' });

    expect(error).toHaveBeenCalledWith('Au moins un administrateur actif est requis.');
  });

  it('affiche « e-mail déjà utilisé » sous le champ lors d’une création refusée', async () => {
    listRequest().flush(page());
    await fixture.whenStable();
    byTestId('staff-accounts-new-button')?.click();
    await fixture.whenStable();

    const fill = (id: string, value: string) => {
      const input = byTestId(id) as HTMLInputElement;
      input.value = value;
      input.dispatchEvent(new Event('input'));
    };
    fill('staff-account-lastname-input', 'Dupont');
    fill('staff-account-firstname-input', 'Marie');
    fill('staff-account-email-input', 'marie.dupont@exemple.fr');
    (byTestId('staff-account-role-librarian') as HTMLInputElement).click();
    fill('staff-account-password-input', 'Initial2026Secret');
    fill('staff-account-confirm-input', 'Initial2026Secret');
    byTestId('staff-account-dialog-confirm')?.click();
    await fixture.whenStable();

    const create = http.expectOne((request) => request.method === 'POST' && request.url === '/api/v1/staff-accounts');
    expect(create.request.body).toEqual({
      lastName: 'Dupont',
      firstName: 'Marie',
      email: 'marie.dupont@exemple.fr',
      role: 'LIBRARIAN',
      initialPassword: 'Initial2026Secret',
    });
    create.flush({ code: 'EMAIL_ALREADY_USED' }, { status: 409, statusText: 'Conflit' });
    await fixture.whenStable();

    expect(byTestId('staff-account-email-error')?.textContent).toContain('Cette adresse e-mail est déjà utilisée.');
  });
});
