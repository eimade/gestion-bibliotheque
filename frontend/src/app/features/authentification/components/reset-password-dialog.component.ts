import { ChangeDetectionStrategy, Component, effect, inject, input, output, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { ApiError } from '../../../core/http/api-error';
import { GENERIC_MESSAGES } from '../../../core/http/messages';
import { ToastService } from '../../../core/notifications/toast.service';
import { AlertComponent } from '../../../shared/ui/alert.component';
import { ButtonComponent } from '../../../shared/ui/button.component';
import { DialogComponent } from '../../../shared/ui/dialog.component';
import { FormFieldComponent, describedBy } from '../../../shared/ui/form-field.component';
import {
  matchingPasswordsValidator,
  passwordPolicyValidator,
} from '../../../shared/validators/password-policy.validator';
import { AccountPasswordService } from '../data-access/account-password.service';
import { AUTH_MESSAGES, passwordErrorMessage } from '../data-access/auth-messages';
import { PasswordPolicyChecklistComponent } from './password-policy-checklist.component';

/**
 * ECR-07 — Dialogue « Réinitialiser le mot de passe » (US-006). Composant autonome, réutilisé par la
 * fiche adhérent (ECR-18) : il appelle lui-même l'API et émet `closed` (vrai si réinitialisé).
 */
@Component({
  selector: 'app-reset-password-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    DialogComponent,
    FormFieldComponent,
    AlertComponent,
    ButtonComponent,
    PasswordPolicyChecklistComponent,
  ],
  template: `
    <app-dialog title="Réinitialiser le mot de passe" [open]="open()" testId="reset-password-dialog"
                (closed)="closed.emit(false)">
      <p class="text-sm">Compte : <strong data-testid="reset-password-account-label">{{ accountLabel() }}</strong></p>
      <form [formGroup]="form" (ngSubmit)="submit()" novalidate class="space-y-4">
        <app-form-field label="Nouveau mot de passe temporaire" fieldId="reset-password" [required]="true"
                        [error]="passwordError()" errorTestId="reset-password-error">
          <input id="reset-password" type="password" formControlName="newPassword" autocomplete="new-password"
                 class="field-input" data-testid="reset-password-input" [attr.aria-invalid]="!!passwordError()"
                 [attr.aria-describedby]="describedBy('reset-password', !!passwordError())" />
        </app-form-field>
        <app-password-policy-checklist [password]="newPassword()" />
        <app-form-field label="Confirmer" fieldId="reset-confirm" [required]="true" [error]="confirmError()"
                        errorTestId="reset-password-confirm-error">
          <input id="reset-confirm" type="password" formControlName="confirmation" autocomplete="new-password"
                 class="field-input" data-testid="reset-password-confirm-input" [attr.aria-invalid]="!!confirmError()"
                 [attr.aria-describedby]="describedBy('reset-confirm', !!confirmError())" />
        </app-form-field>
        <app-alert tone="info">
          Le compte sera débloqué. Le mot de passe devra être changé à la prochaine connexion.
        </app-alert>
        @if (serverError()) {
          <app-alert tone="danger" data-testid="reset-password-server-error">{{ serverError() }}</app-alert>
        }
        <div class="flex justify-end gap-2">
          <button app-button type="button" variant="secondary" data-testid="reset-password-dialog-cancel"
                  (click)="closed.emit(false)">Annuler</button>
          <button app-button type="submit" [loading]="busy()" data-testid="reset-password-dialog-confirm">
            Réinitialiser
          </button>
        </div>
      </form>
    </app-dialog>
  `,
})
export class ResetPasswordDialogComponent {
  readonly open = input(false);
  readonly accountId = input.required<number | null>();
  readonly accountLabel = input('');
  readonly closed = output<boolean>();

  private readonly passwords = inject(AccountPasswordService);
  private readonly toasts = inject(ToastService);
  protected readonly describedBy = describedBy;
  protected readonly busy = signal(false);
  protected readonly serverError = signal<string | null>(null);
  private readonly attempted = signal(false);

  protected readonly form = inject(NonNullableFormBuilder).group(
    {
      newPassword: ['', [Validators.required, passwordPolicyValidator]],
      confirmation: ['', Validators.required],
    },
    { validators: matchingPasswordsValidator('newPassword', 'confirmation') },
  );
  protected readonly newPassword = toSignal(this.form.controls.newPassword.valueChanges, { initialValue: '' });

  constructor() {
    effect(() => {
      if (this.open()) {
        this.form.reset();
        this.attempted.set(false);
        this.serverError.set(null);
      }
    });
  }

  protected passwordError(): string | null {
    const control = this.form.controls.newPassword;
    if (!(control.touched || this.attempted())) {
      return null;
    }
    if (control.hasError('required')) {
      return GENERIC_MESSAGES.required;
    }
    return control.hasError('passwordPolicy') ? AUTH_MESSAGES.passwordPolicy : null;
  }

  protected confirmError(): string | null {
    const control = this.form.controls.confirmation;
    if (!(control.touched || this.attempted())) {
      return null;
    }
    if (control.hasError('required')) {
      return GENERIC_MESSAGES.required;
    }
    return this.form.hasError('passwordMismatch') ? AUTH_MESSAGES.passwordMismatch : null;
  }

  protected submit(): void {
    this.attempted.set(true);
    const accountId = this.accountId();
    if (this.form.invalid || accountId === null) {
      this.form.markAllAsTouched();
      return;
    }
    this.busy.set(true);
    this.serverError.set(null);
    this.passwords.reset(accountId, this.form.getRawValue().newPassword).subscribe({
      next: () => {
        this.busy.set(false);
        this.toasts.success(AUTH_MESSAGES.passwordReset);
        this.closed.emit(true);
      },
      error: (error: unknown) => {
        this.busy.set(false);
        this.serverError.set(error instanceof ApiError ? passwordErrorMessage(error) : GENERIC_MESSAGES.unexpected);
      },
    });
  }
}
