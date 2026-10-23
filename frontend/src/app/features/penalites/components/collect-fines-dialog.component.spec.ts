import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { problemDetailInterceptor } from '../../../core/http/problem-detail.interceptor';
import { ToastService } from '../../../core/notifications/toast.service';
import { CollectFinesDialogComponent } from './collect-fines-dialog.component';

describe('CollectFinesDialogComponent (ECR-34)', () => {
  let fixture: ComponentFixture<CollectFinesDialogComponent>;
  let http: HttpTestingController;

  const fines = {
    fines: [
      { id: 11, reason: 'LATE_RETURN', lateDays: 5, title: 'L’Étranger', createdOn: '2026-11-12', amount: 1, version: 0 },
      { id: 12, reason: 'LOST_ITEM', title: 'Germinal', createdOn: '2026-11-03', amount: 20, version: 0 },
    ],
    total: 21,
  };

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(withInterceptors([problemDetailInterceptor])), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(CollectFinesDialogComponent);
    fixture.componentRef.setInput('target', { memberNumber: 'A000123', label: 'MARTIN Jean (A000123)' });
    await fixture.whenStable();
    http.expectOne('/api/v1/members/A000123/fines').flush(fines);
    await fixture.whenStable();
  });

  afterEach(() => http.verify());

  const host = () => fixture.nativeElement as HTMLElement;
  const byTestId = (id: string) => host().querySelector<HTMLElement>(`[data-testid="${id}"]`);

  it('coche tout par défaut et recalcule le total à la désélection', async () => {
    expect(byTestId('collect-total')?.textContent?.replace(/\s/g, ' ')).toContain('21,00 €');
    expect((byTestId('collect-amount-input') as HTMLInputElement).value).toBe('21,00');

    (host().querySelectorAll<HTMLInputElement>('[data-testid="collect-fine-checkbox"]')[1]).click();
    await fixture.whenStable();

    expect(byTestId('collect-total')?.textContent?.replace(/\s/g, ' ')).toContain('1,00 €');
    expect((byTestId('collect-amount-input') as HTMLInputElement).value).toBe('1,00');
  });

  it('exige un mode de paiement et refuse un paiement partiel', async () => {
    const amount = byTestId('collect-amount-input') as HTMLInputElement;
    amount.value = '20,00';
    amount.dispatchEvent(new Event('input'));
    byTestId('collect-confirm')?.click();
    await fixture.whenStable();

    expect(byTestId('collect-mode-error')?.textContent).toContain('Sélectionnez un mode de paiement.');
    expect(byTestId('collect-amount-error')?.textContent).toContain('Le paiement partiel n’est pas autorisé.');
  });

  it('encaisse la sélection et annonce le déblocage', async () => {
    const success = vi.spyOn(TestBed.inject(ToastService), 'success');
    const closed = vi.fn();
    fixture.componentInstance.closed.subscribe(closed);
    byTestId('collect-mode-card')?.click();
    byTestId('collect-confirm')?.click();
    const request = http.expectOne('/api/v1/payments');
    expect(request.request.body).toEqual({ memberNumber: 'A000123', fineIds: [11, 12], method: 'CARD', amount: 21 });
    request.flush({ paymentId: 5, amount: 21, method: 'CARD', paidFineIds: [11, 12], unpaidTotal: 0, eligible: true },
      { status: 201, statusText: 'Created' });
    await fixture.whenStable();

    expect(success.mock.calls.map(([message]) => message.replace(/\s/g, ' '))).toEqual([
      'Paiement de 21,00 € enregistré (Carte bancaire).', 'L’adhérent peut de nouveau emprunter.']);
    expect(closed).toHaveBeenCalledWith(true);
  });

  it('pénalité déjà réglée entre-temps : message et liste rechargée', async () => {
    byTestId('collect-mode-cash')?.click();
    byTestId('collect-confirm')?.click();
    http.expectOne('/api/v1/payments').flush({ status: 409, code: 'FINE_ALREADY_SETTLED', detail: 'x' },
      { status: 409, statusText: 'Conflict' });
    await fixture.whenStable();
    http.expectOne('/api/v1/members/A000123/fines').flush({ fines: [fines.fines[1]], total: 20 });
    await fixture.whenStable();

    expect(byTestId('collect-server-error')?.textContent).toContain('Une ou plusieurs pénalités ont déjà été réglées.');
    expect(host().querySelectorAll('[data-testid="collect-fine-row"]')).toHaveLength(1);
  });
});
