import { ComponentFixture, TestBed } from '@angular/core/testing';

import { LoginRequest } from '../data-access/account.models';
import { LoginFormComponent } from './login-form.component';

describe('LoginFormComponent (ECR-03)', () => {
  let fixture: ComponentFixture<LoginFormComponent>;
  let host: HTMLElement;

  beforeEach(async () => {
    fixture = TestBed.createComponent(LoginFormComponent);
    host = fixture.nativeElement as HTMLElement;
    await fixture.whenStable();
  });

  const byTestId = (id: string) => host.querySelector<HTMLElement>(`[data-testid="${id}"]`);

  function type(testId: string, value: string): void {
    const input = byTestId(testId) as HTMLInputElement;
    input.value = value;
    input.dispatchEvent(new Event('input'));
  }

  async function submit(): Promise<void> {
    byTestId('login-submit')?.click();
    await fixture.whenStable();
  }

  it('affiche les messages exacts quand les champs sont vides', async () => {
    await submit();

    expect(byTestId('login-email-error')?.textContent).toContain('Ce champ est obligatoire.');
    expect(byTestId('login-password-error')?.textContent).toContain('Ce champ est obligatoire.');
  });

  it('refuse un e-mail mal formé', async () => {
    type('login-email-input', 'pas-un-email');
    type('login-password-input', 'x');
    await submit();

    expect(byTestId('login-email-error')?.textContent).toContain('Saisissez une adresse e-mail valide.');
  });

  it('émet les identifiants d’un formulaire valide', async () => {
    const emitted: LoginRequest[] = [];
    fixture.componentInstance.submitted.subscribe((value) => emitted.push(value));
    type('login-email-input', 'admin@exemple.fr');
    type('login-password-input', 'Admin2026Secret');
    await submit();

    expect(emitted).toEqual([{ email: 'admin@exemple.fr', password: 'Admin2026Secret' }]);
  });

  it('affiche ou masque le mot de passe', async () => {
    byTestId('login-password-toggle')?.click();
    await fixture.whenStable();

    expect((byTestId('login-password-input') as HTMLInputElement).type).toBe('text');
    expect(byTestId('login-password-toggle')?.getAttribute('aria-pressed')).toBe('true');
  });

  it('vide le mot de passe et affiche l’erreur du serveur après un refus', async () => {
    type('login-password-input', 'Mauvais2026x');
    fixture.componentRef.setInput('serverError', 'Identifiants incorrects.');
    await fixture.whenStable();

    expect(byTestId('login-server-error')?.textContent).toContain('Identifiants incorrects.');
    expect((byTestId('login-password-input') as HTMLInputElement).value).toBe('');
  });

  it('n’affiche pas « obligatoire » sur le mot de passe vidé après un refus', async () => {
    type('login-email-input', 'admin@exemple.fr');
    type('login-password-input', 'Mauvais2026x');
    await submit();
    fixture.componentRef.setInput('serverError', 'Identifiants incorrects.');
    await fixture.whenStable();

    expect(byTestId('login-password-error')).toBeNull();
    expect(document.activeElement).toBe(byTestId('login-password-input'));
  });
});
