import { ChangeDetectionStrategy, Component, inject, input, output, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { GENERIC_MESSAGES } from '../../../core/http/messages';
import { AlertComponent } from '../../../shared/ui/alert.component';
import { ButtonComponent } from '../../../shared/ui/button.component';
import { FormFieldComponent, describedBy } from '../../../shared/ui/form-field.component';
import {
  matchingPasswordsValidator,
  passwordPolicyValidator,
} from '../../../shared/validators/password-policy.validator';
import { ChangePasswordRequest } from '../data-access/account.models';
import { AUTH_MESSAGES } from '../data-access/auth-messages';
import { PasswordPolicyChecklistComponent } from './password-policy-checklist.component';

export type PasswordServerField = 'current' | 'new' | null;

/** Formulaire de changement de mot de passe (ECR-04), présentationnel. */
@Component({
  selector: 'app-password-form',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, FormFieldComponent, AlertComponent, ButtonComponent, PasswordPolicyChecklistComponent],
  template: `
    <form [formGroup]="form" (ngSubmit)="submit()" novalidate class="space-y-4">
      <app-form-field label="Mot de passe actuel" fieldId="password-current" [required]="true"
                      [error]="currentError()" errorTestId="password-form-current-error">
        <input id="password-current" type="password" formControlName="currentPassword" autocomplete="current-password"
               class="field-input" data-testid="password-form-current-input"
               [attr.aria-invalid]="!!currentError() || serverField() === 'current'"
               [attr.aria-describedby]="describedBy('password-current', !!currentError())" />
      </app-form-field>

      <app-form-field label="Nouveau mot de passe" fieldId="password-new" [required]="true"
                      [error]="newError()" errorTestId="password-form-new-error">
        <input id="password-new" type="password" formControlName="newPassword" autocomplete="new-password"
               class="field-input" data-testid="password-form-new-input"
               [attr.aria-invalid]="!!newError() || serverField() === 'new'"
               [attr.aria-describedby]="describedBy('password-new', !!newError())" />
      </app-form-field>
      <app-password-policy-checklist [password]="newPassword()" />

      <app-form-field label="Confirmer le nouveau mot de passe" fieldId="password-confirm" [required]="true"
                      [error]="confirmError()" errorTestId="password-form-confirm-error">
        <input id="password-confirm" type="password" formControlName="confirmation" autocomplete="new-password"
               class="field-input" data-testid="password-form-confirm-input"
               [attr.aria-invalid]="!!confirmError()"
               [attr.aria-describedby]="describedBy('password-confirm', !!confirmError())" />
      </app-form-field>

      @if (serverError()) {
        <app-alert tone="danger" data-testid="password-form-server-error">{{ serverError() }}</app-alert>
      }

      <div class="flex justify-end gap-2">
        <button app-button type="button" variant="secondary" data-testid="password-form-cancel" (click)="cancelled.emit()">
          {{ forced() ? 'Se déconnecter' : 'Annuler' }}
        </button>
        <button app-button type="submit" [loading]="busy()" data-testid="password-form-submit">Enregistrer</button>
      </div>
    </form>
  `,
})
export class PasswordFormComponent {
  readonly busy = input(false);
  readonly forced = input(false);
  readonly serverError = input<string | null>(null);
  readonly serverField = input<PasswordServerField>(null);
  readonly submitted = output<ChangePasswordRequest>();
  readonly cancelled = output<void>();

  protected readonly describedBy = describedBy;
  private readonly attempted = signal(false);

  protected readonly form = inject(NonNullableFormBuilder).group(
    {
      currentPassword: ['', Validators.required],
      newPassword: ['', [Validators.required, passwordPolicyValidator]],
      confirmation: ['', Validators.required],
    },
    { validators: matchingPasswordsValidator('newPassword', 'confirmation') },
  );
  protected readonly newPassword = toSignal(this.form.controls.newPassword.valueChanges, { initialValue: '' });

  protected currentError(): string | null {
    const control = this.form.controls.currentPassword;
    return this.visible(control.touched) && control.hasError('required') ? GENERIC_MESSAGES.required : null;
  }

  protected newError(): string | null {
    const control = this.form.controls.newPassword;
    if (!this.visible(control.touched)) {
      return null;
    }
    if (control.hasError('required')) {
      return GENERIC_MESSAGES.required;
    }
    return control.hasError('passwordPolicy') ? AUTH_MESSAGES.passwordPolicy : null;
  }

  protected confirmError(): string | null {
    const control = this.form.controls.confirmation;
    if (!this.visible(control.touched)) {
      return null;
    }
    if (control.hasError('required')) {
      return GENERIC_MESSAGES.required;
    }
    return this.form.hasError('passwordMismatch') ? AUTH_MESSAGES.passwordMismatch : null;
  }

  protected submit(): void {
    this.attempted.set(true);
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      const firstInvalid = (['currentPassword', 'newPassword', 'confirmation'] as const).find(
        (name) => this.form.controls[name].invalid,
      );
      const ids = { currentPassword: 'password-current', newPassword: 'password-new', confirmation: 'password-confirm' };
      document.getElementById(ids[firstInvalid ?? 'confirmation'])?.focus();
      return;
    }
    const { currentPassword, newPassword } = this.form.getRawValue();
    this.submitted.emit({ currentPassword, newPassword });
  }

  private visible(touched: boolean): boolean {
    return touched || this.attempted();
  }
}
