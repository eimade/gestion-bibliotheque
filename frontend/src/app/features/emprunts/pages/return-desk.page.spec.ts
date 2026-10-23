import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { problemDetailInterceptor } from '../../../core/http/problem-detail.interceptor';
import { ReturnDeskPage } from './return-desk.page';

describe('ReturnDeskPage (ECR-25)', () => {
  let fixture: ComponentFixture<ReturnDeskPage>;
  let http: HttpTestingController;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(withInterceptors([problemDetailInterceptor])), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ReturnDeskPage);
    await fixture.whenStable();
  });

  afterEach(() => http.verify());

  const host = () => fixture.nativeElement as HTMLElement;

  async function scan(value: string, response: object, status = 200): Promise<void> {
    const input = host().querySelector<HTMLInputElement>('[data-testid="return-desk-copy-input"]')!;
    input.value = value;
    input.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter' }));
    http.expectOne('/api/v1/returns').flush(response, { status, statusText: status === 200 ? 'OK' : 'Conflict' });
    await fixture.whenStable();
  }

  const member = { memberNumber: 'A000123', lastName: 'Martin', firstName: 'Jean' };

  it('garde la mise de côté en tête, affiche la pénalité et la remise en rayon', async () => {
    await scan('00001234', { copyNumber: '00001234', title: 'L’Étranger', shelfMark: 'R CAM', member, lateDays: 0,
      destination: 'SHELF' });
    await scan('00004567', { copyNumber: '00004567', title: 'Nana', shelfMark: 'R ZOL', member, lateDays: 0, destination: 'HOLD',
      hold: { memberNumber: 'A000789', lastName: 'Bernard', firstName: 'Luc', pickupDeadline: '2026-10-20' } });
    await scan('00000877', { copyNumber: '00000877', title: 'Germinal', shelfMark: 'R ZOL', member, lateDays: 5,
      fine: { id: 4, amount: 1 }, destination: 'SHELF' });
    await scan('00007777', { status: 409, code: 'NO_ACTIVE_LOAN_FOR_COPY', detail: 'Aucun emprunt en cours pour cet exemplaire.' }, 409);

    const lines = [...host().querySelectorAll('[data-testid="return-desk-line"]')];
    expect(lines.map((line) => line.getAttribute('data-result'))).toEqual(['hold', 'error', 'late', 'shelf']);
    expect(lines[0].textContent).toContain('METTRE DE CÔTÉ pour BERNARD Luc (A000789), à retirer avant le 20/10/2026');
    expect(lines[2].textContent).toContain('Retour en retard de 5 jour(s)');
    expect(lines[3].textContent).toContain('Remettre en rayon : R CAM');
    expect(host().querySelector('[data-testid="return-desk-count"]')?.textContent).toContain('3');

    host().querySelector<HTMLButtonElement>('[data-testid="return-desk-line-done"]')!.click();
    await fixture.whenStable();
    const reordered = [...host().querySelectorAll('[data-testid="return-desk-line"]')];
    expect(reordered[0].getAttribute('data-result')).toBe('error');
  });
});
