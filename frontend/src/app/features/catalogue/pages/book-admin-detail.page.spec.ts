import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, TestRequest, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';

import { problemDetailInterceptor } from '../../../core/http/problem-detail.interceptor';
import { ToastService } from '../../../core/notifications/toast.service';
import { BookAdminDetail, CopyView } from '../data-access/catalog.models';
import { BookAdminDetailPage } from './book-admin-detail.page';

function copy(overrides: Partial<CopyView> = {}): CopyView {
  return {
    id: 1, copyNumber: '00000001', shelfMark: 'R DUM', physicalCondition: 'GOOD', acquisitionDate: '2026-03-02',
    status: 'AVAILABLE', version: 0, ...overrides,
  };
}

function book(copies: CopyView[] = [copy()]): BookAdminDetail {
  return {
    id: 7, title: 'Le Comte de Monte-Cristo', authors: [{ id: 1, lastName: 'Dumas', firstName: 'Alexandre' }],
    categories: [{ id: 1, label: 'Roman' }], language: { code: 'fre', label: 'Français' },
    availableCount: copies.filter((c) => c.status === 'AVAILABLE').length, totalCount: copies.length, copies, version: 3,
  };
}

describe('BookAdminDetailPage (ECR-12)', () => {
  let fixture: ComponentFixture<BookAdminDetailPage>;
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
    fixture = TestBed.createComponent(BookAdminDetailPage);
    fixture.componentRef.setInput('bookId', '7');
    await fixture.whenStable();
  });

  afterEach(() => {
    // File d'attente de la notice (ECR-32), chargée avec la fiche.
    http.match((request) => request.url === '/api/v1/books/7/reservations')
      .filter((request) => !request.cancelled)
      .forEach((request) => request.flush([]));
    http.verify();
  });

  const host = () => fixture.nativeElement as HTMLElement;
  const byTestId = (id: string) => host().querySelector<HTMLElement>(`[data-testid="${id}"]`);
  const getRequest = (): TestRequest =>
    http.expectOne((request) => request.method === 'GET' && request.url === '/api/v1/books/7');

  it('affiche la notice et ses exemplaires, suppression désactivée avec la raison', async () => {
    getRequest().flush(book([copy(), copy({ id: 2, copyNumber: '00000002', status: 'WITHDRAWN', withdrawalReason: 'LOST' })]));
    await fixture.whenStable();

    expect(byTestId('book-admin-title')?.textContent).toContain('Le Comte de Monte-Cristo');
    expect(host().querySelectorAll('[data-testid="copy-admin-row"]')).toHaveLength(2);
    expect((byTestId('book-admin-delete-button') as HTMLButtonElement).disabled).toBe(true);
    expect(byTestId('book-admin-delete-reason')?.textContent).toContain('la notice possède des exemplaires');
    expect(host().textContent).toContain('02/03/2026');
  });

  it('affiche « introuvable » sur un 404', async () => {
    getRequest().flush({ code: 'BOOK_NOT_FOUND', status: 404, detail: 'x' }, { status: 404, statusText: 'Not Found' });
    await fixture.whenStable();

    expect(byTestId('book-admin-not-found')?.textContent).toContain('Cette notice est introuvable.');
  });

  it('supprime une notice sans exemplaire puis revient au catalogue de gestion', async () => {
    getRequest().flush(book([]));
    await fixture.whenStable();
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    expect(byTestId('book-admin-empty')).not.toBeNull();

    byTestId('book-admin-delete-button')?.click();
    await fixture.whenStable();
    byTestId('book-delete-confirm')?.click();
    http.expectOne((request) => request.method === 'DELETE' && request.url === '/api/v1/books/7')
      .flush(null, { status: 204, statusText: 'No Content' });
    await fixture.whenStable();

    expect(navigate).toHaveBeenCalledWith('/gestion/catalogue');
  });

  it('ajoute des exemplaires, annonce leurs numéros et recharge la fiche', async () => {
    getRequest().flush(book());
    await fixture.whenStable();
    const success = vi.spyOn(toasts, 'success');

    byTestId('copy-admin-add-button')?.click();
    await fixture.whenStable();
    const shelfMark = byTestId('add-copies-shelfmark-input') as HTMLInputElement;
    shelfMark.value = 'R DUM';
    shelfMark.dispatchEvent(new Event('input'));
    const quantity = byTestId('add-copies-quantity-input') as HTMLInputElement;
    quantity.value = '2';
    quantity.dispatchEvent(new Event('input'));
    byTestId('add-copies-dialog-confirm')?.click();
    await fixture.whenStable();

    const add = http.expectOne((request) => request.method === 'POST' && request.url === '/api/v1/books/7/copies');
    expect(add.request.body).toMatchObject({ quantity: 2, shelfMark: 'R DUM', physicalCondition: 'GOOD' });
    add.flush([copy({ id: 2, copyNumber: '00000002' }), copy({ id: 3, copyNumber: '00000003' })],
      { status: 201, statusText: 'Created' });
    await fixture.whenStable();

    expect(success).toHaveBeenCalledWith('2 exemplaires ajoutés : 00000002, 00000003.');
    getRequest().flush(book());
  });

  it('exige un motif avant de retirer un exemplaire, puis confirme le retrait', async () => {
    getRequest().flush(book());
    await fixture.whenStable();

    byTestId('copy-admin-row-menu')?.click();
    await fixture.whenStable();
    byTestId('copy-admin-withdraw-action')?.click();
    await fixture.whenStable();
    byTestId('copy-edit-tab-withdraw')?.click();
    await fixture.whenStable();

    byTestId('copy-withdraw-submit')?.click();
    await fixture.whenStable();
    expect(byTestId('copy-withdraw-reason-error')?.textContent).toContain('Sélectionnez un motif de retrait.');

    const select = byTestId('copy-withdraw-reason-select') as HTMLSelectElement;
    select.value = 'LOST';
    select.dispatchEvent(new Event('change'));
    byTestId('copy-withdraw-submit')?.click();
    await fixture.whenStable();
    byTestId('copy-withdraw-confirm')?.click();

    const withdraw = http.expectOne((request) => request.url === '/api/v1/copies/00000001/withdrawal');
    expect(withdraw.request.body).toMatchObject({ reason: 'LOST' });
    withdraw.flush(copy({ status: 'WITHDRAWN', withdrawalReason: 'LOST' }));
    await fixture.whenStable();
    getRequest().flush(book());
  });

  it('montre l’erreur du serveur dans le dialogue de l’exemplaire', async () => {
    getRequest().flush(book([copy({ status: 'ON_LOAN', dueDate: '2026-11-01' })]));
    await fixture.whenStable();
    expect(host().textContent).toContain('retour prévu le 01/11/2026');

    byTestId('copy-admin-row-menu')?.click();
    await fixture.whenStable();
    byTestId('copy-admin-edit-action')?.click();
    await fixture.whenStable();
    byTestId('copy-edit-tab-withdraw')?.click();
    await fixture.whenStable();
    expect(byTestId('copy-withdraw-blocked')?.textContent).toContain('l’exemplaire est en circulation');

    byTestId('copy-edit-tab-edit')?.click();
    await fixture.whenStable();
    byTestId('copy-edit-save')?.click();
    http.expectOne((request) => request.method === 'PUT' && request.url === '/api/v1/copies/00000001')
      .flush({ code: 'CONCURRENT_MODIFICATION', status: 409, detail: 'x' }, { status: 409, statusText: 'Conflict' });
    await fixture.whenStable();

    expect(byTestId('copy-edit-server-error')).not.toBeNull();
  });
});
