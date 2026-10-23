import { ChangeDetectionStrategy, Component, DestroyRef, computed, effect, inject, input, signal } from '@angular/core';

import { PASSWORD_RULES } from '../../../shared/validators/password-policy.validator';

/** Annonce aux lecteurs d'écran après une pause de saisie, pas à chaque frappe (UX-authentification §6). */
export const CHECKLIST_ANNOUNCE_DELAY_MS = 600;

/** Liste vivante des critères RG-01 (ECR-04, ECR-06, ECR-07, ECR-17). */
@Component({
  selector: 'app-password-policy-checklist',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <ul class="grid grid-cols-1 gap-1 text-sm sm:grid-cols-2" aria-label="Règles du mot de passe">
      @for (rule of rules(); track rule.id) {
        <li data-testid="password-policy-item" [attr.data-rule]="rule.id" [attr.data-valid]="rule.valid"
            [class]="rule.valid ? 'text-success' : 'text-muted'">
          <span aria-hidden="true">{{ rule.valid ? '✓' : '✗' }}</span>
          {{ rule.label }}<span class="sr-only"> : {{ rule.valid ? 'respecté' : 'non respecté' }}</span>
        </li>
      }
    </ul>
    <p class="sr-only" aria-live="polite">{{ announcement() }}</p>
  `,
})
export class PasswordPolicyChecklistComponent {
  readonly password = input('');

  protected readonly rules = computed(() =>
    PASSWORD_RULES.map((rule) => ({ id: rule.id, label: rule.label, valid: rule.test(this.password()) })),
  );
  protected readonly announcement = signal('');

  constructor() {
    let timer: ReturnType<typeof setTimeout> | undefined;
    inject(DestroyRef).onDestroy(() => clearTimeout(timer));
    effect(() => {
      const rules = this.rules();
      clearTimeout(timer);
      if (!this.password()) {
        return;
      }
      timer = setTimeout(() => {
        const met = rules.filter((rule) => rule.valid).length;
        this.announcement.set(`${met} règle(s) sur ${rules.length} respectée(s).`);
      }, CHECKLIST_ANNOUNCE_DELAY_MS);
    });
  }
}
