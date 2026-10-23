import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { AuthService } from '../../../core/auth/auth.service';
import { problemDetailInterceptor } from '../../../core/http/problem-detail.interceptor';
import { ToastService } from '../../../core/notifications/toast.service';
import { SessionUser } from '../../authentification/data-access/account.models';
import { ADMIN, LIBRARIAN, eligibility, memberDetail, memberLoan } from '../../../testing/fixtures';
import { MemberDetailPage } from './member-detail.page';

describe('MemberDetailPage (ECR-18)', () => {
  let fixture: ComponentFixture<MemberDetailPage>;
  let http: HttpTestingController;
  const user = signal<SessionUser | null>(LIBRARIAN);

  async function open(number = 'A000123'): Promise<void> {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(withInterceptors([problemDetailInterceptor])), provideHttpClientTesting(),
        { provide: AuthService, useValue: { user } }],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(MemberDetailPage);
    fixture.componentRef.setInput('memberNumber', number);
    await fixture.whenStable();
  }

  afterEach(() => http.verify());

  const host = () => fixture.nativeElement as HTMLElement;
  const byTestId = (id: string) => host().querySelector<HTMLElement>(`[data-testid="${id}"]`);

  it('affiche la situation complète et l’indication « emprunt impossible » avec ses motifs', async () => {
    await open();
    http.expectOne('/api/v1/members/A000123').flush(memberDetail({
      eligibility: eligibility({ eligible: false, reasons: ['OVERDUE_LOAN'], unpaidTotal: 1.4 }),
    }));
    await fixture.whenStable();
    http.expectOne('/api/v1/members/A000123/loans').flush([
      memberLoan(),
      memberLoan({ id: 2, copyNumber: '00000877', title: 'Germinal', status: 'OVERDUE', overdueDays: 25, provisionalFine: 5,
        renewal: { allowed: false, blockedBy: 'LOAN_OVERDUE', memberReasons: [], newDueDate: '2026-10-13' } }),
    ]);
    await fixture.whenStable();

    expect(byTestId('member-detail-name')?.textContent).toContain('MARTIN Jean');
    expect(byTestId('member-eligibility-panel')?.getAttribute('data-eligible')).toBe('false');
    expect(byTestId('member-eligibility-reason')?.textContent).toContain('Emprunt en retard');
    expect(byTestId('member-detail-new-loan-button')?.getAttribute('aria-disabled')).toBe('true');
    expect(host().querySelectorAll('[data-testid="member-loan-row"]')).toHaveLength(2);
    expect(byTestId('member-loan-overdue-days')?.textContent).toContain('25 j');
    expect(byTestId('member-tab-fines')?.textContent?.replace(/\s/g, ' ')).toContain('Pénalités (1,40 €)');
    expect(byTestId('member-detail-renew-blocked')?.textContent).toContain('Renouvellement possible à partir du 17/09/2027.');
  });

  it('renouvelle l’adhésion après confirmation', async () => {
    await open();
    const toasts = TestBed.inject(ToastService);
    const success = vi.spyOn(toasts, 'success');
    http.expectOne('/api/v1/members/A000123').flush(memberDetail({
      expiresSoon: true, expiryDate: '2026-11-01',
      renewal: { allowed: true, newExpiryDate: '2027-11-01', renewableFrom: '2026-10-02' },
    }));
    await fixture.whenStable();
    http.expectOne('/api/v1/members/A000123/loans').flush([]);
    await fixture.whenStable();
    expect(byTestId('member-detail-renew-alert')).not.toBeNull();
    expect(byTestId('member-loans-empty')?.textContent).toContain('Aucun emprunt en cours.');

    byTestId('member-detail-renew-button')?.click();
    await fixture.whenStable();
    expect(byTestId('member-renew-dialog')?.textContent).toContain('jusqu’au 01/11/2027');
    byTestId('member-renew-confirm')?.click();
    http.expectOne('/api/v1/members/A000123/renewal').flush(memberDetail({ expiryDate: '2027-11-01' }));
    await fixture.whenStable();

    expect(success).toHaveBeenCalledWith('Adhésion renouvelée jusqu’au 01/11/2027.');
    http.expectOne('/api/v1/members/A000123').flush(memberDetail({ expiryDate: '2027-11-01' }));
    await fixture.whenStable();
    http.match('/api/v1/members/A000123/loans').forEach((request) => request.flush([]));
  });

  it('onglet pénalités : la remise n’est proposée qu’à l’administrateur', async () => {
    user.set(ADMIN);
    await open();
    http.expectOne('/api/v1/members/A000123').flush(memberDetail());
    await fixture.whenStable();
    http.expectOne('/api/v1/members/A000123/loans').flush([]);
    await fixture.whenStable();

    byTestId('member-tab-fines')?.click();
    await fixture.whenStable();
    http.expectOne('/api/v1/members/A000123/fines').flush({ fines: [{ id: 4, reason: 'LATE_RETURN', lateDays: 5,
      title: 'Germinal', createdOn: '2026-10-12', amount: 1, version: 0 }], total: 1 });
    await fixture.whenStable();

    expect(byTestId('member-fine-row')?.textContent).toContain('Retard (5 j) · Germinal');
    expect(byTestId('member-fine-waive-action')).not.toBeNull();
    user.set(LIBRARIAN);
  });

  it('adhérent introuvable et adhérent archivé', async () => {
    await open('A000999');
    http.expectOne('/api/v1/members/A000999').flush({ status: 404, code: 'MEMBER_NOT_FOUND', detail: 'x' },
      { status: 404, statusText: 'Not Found' });
    await fixture.whenStable();

    expect(byTestId('member-detail-not-found')?.textContent).toContain('Aucun adhérent ne porte le numéro A000999.');
  });

  it('adhérent archivé : aucune donnée personnelle ni action', async () => {
    await open();
    http.expectOne('/api/v1/members/A000123').flush(memberDetail({ status: 'ARCHIVED', lastName: null, firstName: null }));
    await fixture.whenStable();

    expect(byTestId('member-archived-notice')?.textContent).toContain('Les données personnelles de cet adhérent ont été effacées.');
    expect(byTestId('member-detail-renew-button')).toBeNull();
    expect(byTestId('member-tab-loans')).toBeNull();
  });
});
