import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { AuthService } from '../../../core/auth/auth.service';
import { problemDetailInterceptor } from '../../../core/http/problem-detail.interceptor';
import { SessionUser } from '../../authentification/data-access/account.models';
import { MEMBER } from '../../../testing/fixtures';
import { ReservationCtaComponent } from './reservation-cta.component';

describe('ReservationCtaComponent (ECR-09, ECR-30)', () => {
  let fixture: ComponentFixture<ReservationCtaComponent>;
  let http: HttpTestingController;
  const user = signal<SessionUser | null>(null);

  async function render(available: number, who: SessionUser | null): Promise<void> {
    user.set(who);
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(withInterceptors([problemDetailInterceptor])), provideHttpClientTesting(),
        { provide: AuthService, useValue: { user } }],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ReservationCtaComponent);
    fixture.componentRef.setInput('bookId', 4);
    fixture.componentRef.setInput('title', 'Bel-Ami');
    fixture.componentRef.setInput('available', available);
    await fixture.whenStable();
  }

  afterEach(() => http.verify());

  const host = () => fixture.nativeElement as HTMLElement;
  const byTestId = (id: string) => host().querySelector<HTMLElement>(`[data-testid="${id}"]`);
  const preview = { reservable: true, reasons: [], memberReasons: [], position: 2, activeReservations: 1, limit: 3, pickupDays: 3 };

  it('exemplaire disponible : simple information', async () => {
    await render(1, MEMBER);

    expect(byTestId('book-reserve-available-info')?.textContent).toContain('Un exemplaire est disponible en rayon.');
  });

  it('visiteur : lien de connexion', async () => {
    await render(0, null);

    expect(byTestId('book-reserve-login-link')?.textContent).toContain('Connectez-vous pour réserver');
  });

  it('adhérent : réserve et voit sa position', async () => {
    await render(0, MEMBER);
    http.expectOne('/api/v1/me/books/4/reservation').flush({ preview });
    await fixture.whenStable();

    byTestId('book-reserve-button')?.click();
    await fixture.whenStable();
    expect(byTestId('reserve-position-preview')?.textContent).toContain('vous serez en position 2');
    expect(byTestId('reserve-quota')?.textContent).toContain('1 sur 3');

    byTestId('reserve-dialog-confirm')?.click();
    http.expectOne('/api/v1/me/reservations').flush({ id: 7, status: 'PENDING', position: 2, queueLength: 2 },
      { status: 201, statusText: 'Created' });
    await fixture.whenStable();
    http.expectOne('/api/v1/me/books/4/reservation').flush({
      current: { id: 7, status: 'PENDING', position: 2, queueLength: 2 }, preview: { ...preview, reservable: false,
        reasons: ['RESERVATION_ALREADY_EXISTS'] } });
    await fixture.whenStable();

    expect(byTestId('book-reservation-position')?.textContent).toContain('Vous êtes en position 2 sur 2.');
  });

  it('adhérent non éligible : motifs à la place du bouton Réserver', async () => {
    await render(0, MEMBER);
    http.expectOne('/api/v1/me/books/4/reservation').flush({ preview: { ...preview, reservable: false,
      reasons: ['MEMBER_NOT_ELIGIBLE'], memberReasons: ['MEMBER_SUSPENDED'] } });
    await fixture.whenStable();

    byTestId('book-reserve-button')?.click();
    await fixture.whenStable();

    expect(byTestId('reserve-blocked-reason')?.textContent).toContain('Réservation impossible : Adhérent suspendu.');
    expect(byTestId('reserve-dialog-confirm')).toBeNull();
  });
});
