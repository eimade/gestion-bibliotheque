import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { problemDetailInterceptor } from '../../../core/http/problem-detail.interceptor';
import { eligibility, memberDetail } from '../../../testing/fixtures';
import { LoanDeskPage } from './loan-desk.page';

describe('LoanDeskPage (ECR-24)', () => {
  let fixture: ComponentFixture<LoanDeskPage>;
  let http: HttpTestingController;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(withInterceptors([problemDetailInterceptor])), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(LoanDeskPage);
    await fixture.whenStable();
  });

  afterEach(() => http.verify());

  const host = () => fixture.nativeElement as HTMLElement;
  const byTestId = (id: string) => host().querySelector<HTMLElement>(`[data-testid="${id}"]`);

  async function scan(testId: string, value: string): Promise<void> {
    const input = byTestId(testId) as HTMLInputElement;
    input.value = value;
    input.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter' }));
    await fixture.whenStable();
  }

  it('refuse un numéro d’adhérent mal formé sans appeler le serveur', async () => {
    await scan('loan-desk-member-input', '123');

    expect(byTestId('loan-desk-member-error')?.textContent).toContain('Numéro d’adhérent invalide (format attendu : A000123).');
  });

  it('identifie l’adhérent puis enregistre un emprunt et un refus', async () => {
    await scan('loan-desk-member-input', 'a000123');
    http.expectOne('/api/v1/members/A000123').flush(memberDetail());
    await fixture.whenStable();

    expect(byTestId('loan-desk-member-name')?.textContent).toContain('MARTIN Jean');
    expect(byTestId('loan-desk-loan-count')?.textContent).toContain('2 / 5');

    await scan('loan-desk-copy-input', '00001234');
    const request = http.expectOne('/api/v1/loans');
    expect(request.request.body).toEqual({ memberNumber: 'A000123', copyNumber: '00001234' });
    request.flush({ loanId: 9, copyNumber: '00001234', title: 'L’Étranger', dueDate: '2026-11-07',
      reservationFulfilled: false, eligibility: eligibility({ activeLoans: 3, remainingLoans: 2 }) }, { status: 201, statusText: 'Created' });
    await fixture.whenStable();

    await scan('loan-desk-copy-input', '00000999');
    http.expectOne('/api/v1/loans').flush({ status: 409, code: 'COPY_RESERVED_FOR_ANOTHER_MEMBER',
      detail: 'Exemplaire réservé pour un autre adhérent.' }, { status: 409, statusText: 'Conflict' });
    await fixture.whenStable();

    const lines = host().querySelectorAll('[data-testid="loan-desk-line"]');
    expect(lines).toHaveLength(2);
    expect(lines[0].getAttribute('data-result')).toBe('error');
    expect(lines[0].textContent).toContain('Exemplaire réservé pour un autre adhérent.');
    expect(lines[1].textContent).toContain('À rendre le 07/11/2026');
    expect(byTestId('loan-desk-loan-count')?.textContent).toContain('3 / 5');
  });

  it('bloque le scan des exemplaires pour un adhérent non éligible et termine la session', async () => {
    await scan('loan-desk-member-input', 'A000123');
    http.expectOne('/api/v1/members/A000123').flush(memberDetail({
      eligibility: eligibility({ eligible: false, reasons: ['OVERDUE_LOAN', 'UNPAID_FINES_ABOVE_THRESHOLD'] }),
    }));
    await fixture.whenStable();

    expect((byTestId('loan-desk-copy-input') as HTMLInputElement).disabled).toBe(true);
    expect(host().querySelectorAll('[data-testid="member-eligibility-reason"]')).toHaveLength(2);

    byTestId('loan-desk-finish')?.click();
    await fixture.whenStable();
    expect(byTestId('loan-desk-member-input')).not.toBeNull();
  });

  it('affiche l’adhérent inconnu', async () => {
    await scan('loan-desk-member-input', 'A000999');
    http.expectOne('/api/v1/members/A000999').flush({ status: 404, code: 'MEMBER_NOT_FOUND',
      detail: 'Aucun adhérent ne porte le numéro A000999.' }, { status: 404, statusText: 'Not Found' });
    await fixture.whenStable();

    expect(byTestId('loan-desk-member-error')?.textContent).toContain('Aucun adhérent ne porte le numéro A000999.');
  });
});
