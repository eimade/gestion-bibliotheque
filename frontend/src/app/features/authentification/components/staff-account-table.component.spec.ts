import { ComponentFixture, TestBed } from '@angular/core/testing';

import { staffAccount } from '../../../testing/fixtures';
import { StaffAccountActionEvent, StaffAccountTableComponent } from './staff-account-table.component';

describe('StaffAccountTableComponent (ECR-05)', () => {
  let fixture: ComponentFixture<StaffAccountTableComponent>;
  let host: HTMLElement;

  beforeEach(async () => {
    fixture = TestBed.createComponent(StaffAccountTableComponent);
    fixture.componentRef.setInput('accounts', [
      staffAccount({ id: 1, email: 'admin@exemple.fr', role: 'ADMIN', me: true, lastName: 'Leroy', firstName: 'Anne' }),
      staffAccount({ id: 5 }),
      staffAccount({ id: 6, email: 'parti@exemple.fr', status: 'DISABLED' }),
    ]);
    fixture.componentRef.setInput('sortField', 'lastName');
    fixture.componentRef.setInput('sortDirection', 'asc');
    host = fixture.nativeElement as HTMLElement;
    await fixture.whenStable();
  });

  function row(email: string): HTMLElement {
    return host.querySelector<HTMLElement>(`[data-testid="staff-accounts-row"][data-row-id="${email}"]`)!;
  }

  async function openMenu(email: string): Promise<HTMLElement> {
    row(email).querySelector<HTMLElement>('[data-testid="staff-accounts-row-menu"]')!.click();
    await fixture.whenStable();
    return row(email);
  }

  it('affiche le statut en texte et le tri courant pour les lecteurs d’écran', () => {
    expect(row('marie.dupont@exemple.fr').querySelector('[data-testid="status-badge"]')?.textContent).toContain('Actif');
    expect(host.querySelector('th[aria-sort="ascending"]')?.textContent).toContain('Nom');
  });

  it('interdit l’auto-désactivation avec une explication visible (AC-005-3)', async () => {
    const self = await openMenu('admin@exemple.fr');

    expect(self.querySelector<HTMLButtonElement>('[data-testid="staff-accounts-disable-action"]')?.disabled).toBe(true);
    expect(self.querySelector('[data-testid="staff-accounts-self-disable-reason"]')?.textContent).toContain(
      'Vous ne pouvez pas désactiver votre propre compte.',
    );
  });

  it('propose seulement la réactivation pour un compte désactivé (AC-006-2)', async () => {
    const disabled = await openMenu('parti@exemple.fr');

    expect(disabled.querySelector('[data-testid="staff-accounts-enable-action"]')).not.toBeNull();
    expect(disabled.querySelector('[data-testid="staff-accounts-reset-password-action"]')).toBeNull();
  });

  it('émet l’action choisie', async () => {
    const events: StaffAccountActionEvent[] = [];
    fixture.componentInstance.action.subscribe((event) => events.push(event));
    const other = await openMenu('marie.dupont@exemple.fr');
    other.querySelector<HTMLElement>('[data-testid="staff-accounts-disable-action"]')!.click();

    expect(events).toHaveLength(1);
    expect(events[0].action).toBe('disable');
    expect(events[0].account.id).toBe(5);
  });

  it('émet le champ de tri demandé', () => {
    const fields: string[] = [];
    fixture.componentInstance.sortChange.subscribe((field) => fields.push(field));
    host.querySelectorAll<HTMLButtonElement>('th button')[1].click();

    expect(fields).toEqual(['role']);
  });
});
