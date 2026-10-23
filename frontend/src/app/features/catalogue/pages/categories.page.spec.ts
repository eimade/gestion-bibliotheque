import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, TestRequest, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { problemDetailInterceptor } from '../../../core/http/problem-detail.interceptor';
import { ToastService } from '../../../core/notifications/toast.service';
import { CategoryResponse } from '../data-access/catalog.models';
import { CategoriesPage } from './categories.page';

describe('CategoriesPage (ECR-15)', () => {
  let fixture: ComponentFixture<CategoriesPage>;
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
    fixture = TestBed.createComponent(CategoriesPage);
    await fixture.whenStable();
  });

  afterEach(() => http.verify());

  const host = () => fixture.nativeElement as HTMLElement;
  const byTestId = (id: string) => host().querySelector<HTMLElement>(`[data-testid="${id}"]`);
  const listRequest = (): TestRequest =>
    http.expectOne((request) => request.method === 'GET' && request.url === '/api/v1/categories');
  const category = (overrides: Partial<CategoryResponse> = {}): CategoryResponse =>
    ({ id: 1, label: 'Roman', bookCount: 0, version: 0, ...overrides });
  const page = (content: CategoryResponse[]) =>
    ({ content, page: 0, size: 20, totalElements: content.length, totalPages: 1 });

  function type(testId: string, value: string): void {
    const input = byTestId(testId) as HTMLInputElement;
    input.value = value;
    input.dispatchEvent(new Event('input'));
  }

  it('liste les catégories triées par libellé', async () => {
    const request = listRequest();
    expect(request.request.params.get('sort')).toBe('label,asc');
    request.flush(page([category(), category({ id: 2, label: 'Histoire', bookCount: 3 })]));
    await fixture.whenStable();

    expect(host().querySelectorAll('[data-testid="categories-row"]')).toHaveLength(2);
  });

  it('affiche l’état vide', async () => {
    listRequest().flush(page([]));
    await fixture.whenStable();

    expect(byTestId('categories-empty')?.textContent).toContain('Aucune catégorie. Créez la première ci-dessus.');
  });

  it('refuse un libellé trop court sans appeler le serveur', async () => {
    listRequest().flush(page([]));
    await fixture.whenStable();

    type('categories-new-input', 'a');
    byTestId('categories-add-button')?.click();
    await fixture.whenStable();

    expect(byTestId('categories-new-error')?.textContent).toContain('entre 2 et 50 caractères');
  });

  it('crée une catégorie puis recharge la liste', async () => {
    listRequest().flush(page([]));
    await fixture.whenStable();
    const success = vi.spyOn(toasts, 'success');

    type('categories-new-input', '  Poésie ');
    byTestId('categories-add-button')?.click();
    const create = http.expectOne((request) => request.method === 'POST');
    expect(create.request.body).toEqual({ label: 'Poésie' });
    create.flush(category({ id: 9, label: 'Poésie' }), { status: 201, statusText: 'Created' });
    await fixture.whenStable();

    expect(success).toHaveBeenCalledWith('Catégorie créée.');
    listRequest().flush(page([category({ id: 9, label: 'Poésie' })]));
  });

  it('affiche le doublon renvoyé par le serveur sous le champ', async () => {
    listRequest().flush(page([category()]));
    await fixture.whenStable();

    type('categories-new-input', 'roman');
    byTestId('categories-add-button')?.click();
    http.expectOne((request) => request.method === 'POST')
      .flush({ code: 'CATEGORY_ALREADY_EXISTS', status: 409, detail: 'x' }, { status: 409, statusText: 'Conflict' });
    await fixture.whenStable();

    expect(byTestId('categories-new-error')?.textContent).toContain('Cette catégorie existe déjà.');
  });

  it('bloque la suppression d’une catégorie utilisée et renomme avec la version', async () => {
    listRequest().flush(page([category({ bookCount: 2, version: 4 })]));
    await fixture.whenStable();

    expect(byTestId('categories-delete-action')).toBeNull();
    expect(byTestId('categories-delete-blocked')?.textContent).toContain('Utilisée par 2 notices');

    byTestId('categories-rename-action')?.click();
    await fixture.whenStable();
    type('categories-rename-input', 'Romans');
    (byTestId('categories-rename-input') as HTMLInputElement)
      .dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter' }));
    const rename = http.expectOne((request) => request.method === 'PUT' && request.url === '/api/v1/categories/1');
    expect(rename.request.body).toEqual({ label: 'Romans', version: 4 });
    rename.flush(category({ label: 'Romans', bookCount: 2, version: 5 }));
    await fixture.whenStable();
    listRequest().flush(page([category({ label: 'Romans', bookCount: 2, version: 5 })]));
  });

  it('supprime une catégorie inutilisée après confirmation', async () => {
    listRequest().flush(page([category()]));
    await fixture.whenStable();

    byTestId('categories-delete-action')?.click();
    await fixture.whenStable();
    byTestId('categories-delete-confirm')?.click();
    http.expectOne((request) => request.method === 'DELETE' && request.url === '/api/v1/categories/1')
      .flush(null, { status: 204, statusText: 'No Content' });
    await fixture.whenStable();
    listRequest().flush(page([]));
    await fixture.whenStable();

    expect(byTestId('categories-empty')).not.toBeNull();
  });
});
