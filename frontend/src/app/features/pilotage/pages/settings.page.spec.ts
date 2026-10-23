import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { problemDetailInterceptor } from '../../../core/http/problem-detail.interceptor';
import { SettingView } from '../data-access/pilotage.models';
import { SettingsPage } from './settings.page';

describe('SettingsPage (ECR-39)', () => {
  let fixture: ComponentFixture<SettingsPage>;
  let http: HttpTestingController;

  const settings: SettingView[] = [
    { code: 'LOAN_DURATION_DAYS', group: 'LOANS', valueType: 'INTEGER', unit: 'DAYS', value: 21, min: 1, max: 90, version: 0 },
    { code: 'FINE_DAILY_RATE', group: 'FINES', valueType: 'MONEY', unit: 'EUR', value: 0.2, min: 0, max: 5, version: 0,
      updatedAt: '2026-01-02T09:00:00Z', updatedBy: 'LEROY Anne' },
  ];

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(withInterceptors([problemDetailInterceptor])), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(SettingsPage);
    await fixture.whenStable();
    http.expectOne('/api/v1/settings').flush(settings);
    await fixture.whenStable();
  });

  afterEach(() => http.verify());

  const host = () => fixture.nativeElement as HTMLElement;
  const row = (code: string) => host().querySelector<HTMLElement>(`[data-testid="setting-row"][data-row-id="${code}"]`)!;

  async function type(code: string, value: string): Promise<void> {
    const input = row(code).querySelector<HTMLInputElement>('[data-testid="setting-input"]')!;
    input.value = value;
    input.dispatchEvent(new Event('input'));
    await fixture.whenStable();
  }

  it('affiche les groupes, les bornes et la dernière modification', () => {
    expect(host().querySelectorAll('[data-testid="settings-group"]')).toHaveLength(4);
    expect(row('LOAN_DURATION_DAYS').querySelector('[data-testid="setting-bounds"]')?.textContent).toContain('(1 à 90)');
    expect(row('FINE_DAILY_RATE').querySelector('[data-testid="setting-last-modified"]')?.textContent).toContain('LEROY Anne');
  });

  it('refuse une valeur hors bornes ou non entière', async () => {
    await type('LOAN_DURATION_DAYS', '91');
    expect(row('LOAN_DURATION_DAYS').querySelector('[data-testid="setting-error"]')?.textContent)
      .toContain('La valeur doit être comprise entre 1 et 90.');
    await type('LOAN_DURATION_DAYS', '2,5');
    expect(row('LOAN_DURATION_DAYS').querySelector('[data-testid="setting-error"]')?.textContent).toContain('Saisissez un nombre entier.');
    expect(row('LOAN_DURATION_DAYS').querySelector<HTMLButtonElement>('[data-testid="setting-save"]')?.disabled).toBe(true);
  });

  it('confirme puis enregistre une nouvelle durée d’emprunt', async () => {
    await type('LOAN_DURATION_DAYS', '28');
    row('LOAN_DURATION_DAYS').querySelector<HTMLButtonElement>('[data-testid="setting-save"]')!.click();
    await fixture.whenStable();
    expect(host().querySelector('[data-testid="setting-confirm-dialog"]')?.textContent)
      .toContain('Passer « Durée d’emprunt » de 21 jours à 28 jours ?');

    host().querySelector<HTMLButtonElement>('[data-testid="setting-confirm-confirm"]')!.click();
    const request = http.expectOne('/api/v1/settings/LOAN_DURATION_DAYS');
    expect(request.request.body).toEqual({ value: 28, version: 0 });
    request.flush({ ...settings[0], value: 28, version: 1 });
    await fixture.whenStable();
    http.expectOne('/api/v1/settings').flush(settings);
  });
});
