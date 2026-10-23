import { ChangeDetectionStrategy, Component, effect, inject, input, output, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { GENERIC_MESSAGES } from '../../../core/http/messages';
import { AlertComponent } from '../../../shared/ui/alert.component';
import { ButtonComponent } from '../../../shared/ui/button.component';
import { DialogComponent } from '../../../shared/ui/dialog.component';
import { FormFieldComponent, describedBy } from '../../../shared/ui/form-field.component';
import {
  matchingPasswordsValidator,
  passwordPolicyValidator,
} from '../../../shared/validators/password-policy.validator';
import { CreateStaffAccountRequest, StaffRole } from '../data-access/account.models';
import { AUTH_MESSAGES } from '../data-access/auth-messages';
import { PasswordPolicyChecklistComponent } from './password-policy-checklist.component';

/** ECR-06 — Dialogue « Nouveau compte du personnel », présentationnel. */
@Component({
  selector: 'app-staff-account-form-dialog',
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
    <app-dialog title="Nouveau compte du personnel" [open]="open()" testId="staff-account-dialog" (closed)="cancelled.emit()">
      <form [formGroup]="form" (ngSubmit)="submit()" novalidate class="space-y-4">
        <div class="grid gap-4 sm:grid-cols-2">
          <app-form-field label="Nom" fieldId="staff-lastname" [required]="true" [error]="textError('lastName')">
            <input id="staff-lastname" formControlName="lastName" autocomplete="family-name" class="field-input"
                   data-testid="staff-account-lastname-input" [attr.aria-invalid]="!!textError('lastName')"
                   [attr.aria-describedby]="describedBy('staff-lastname', !!textError('lastName'))" />
          </app-form-field>
          <app-form-field label="Prénom" fieldId="staff-firstname" [required]="true" [error]="textError('firstName')">
            <input id="staff-firstname" formControlName="firstName" autocomplete="given-name" class="field-input"
                   data-testid="staff-account-firstname-input" [attr.aria-invalid]="!!textError('firstName')"
                   [attr.aria-describedby]="describedBy('staff-firstname', !!textError('firstName'))" />
          </app-form-field>
        </div>

        <app-form-field label="Adresse e-mail" fieldId="staff-email" [required]="true" [error]="emailError()"
                        errorTestId="staff-account-email-error">
          <input id="staff-email" type="email" formControlName="email" autocomplete="off" class="field-input"
                 data-testid="staff-account-email-input" [attr.aria-invalid]="!!emailError()"
                 [attr.aria-describedby]="describedBy('staff-email', !!emailError())" />
        </app-form-field>

        <fieldset class="space-y-1">
          <legend class="text-sm font-medium">Rôle <span class="text-danger" aria-hidden="true">*</span></legend>
          <div class="flex gap-6 text-sm">
            <label class="flex items-center gap-2">
              <input type="radio" formControlName="role" value="LIBRARIAN" data-testid="staff-account-role-librarian" />
              Bibliothécaire
            </label>
            <label class="flex items-center gap-2">
              <input type="radio" formControlName="role" value="ADMIN" data-testid="staff-account-role-admin" />
              Administrateur
            </label>
          </div>
          @if (roleError()) {
            <p class="text-sm text-danger">{{ roleError() }}</p>
          }
        </fieldset>

        <app-form-field label="Mot de passe initial" fieldId="staff-password" [required]="true"
                        [error]="passwordError()" errorTestId="staff-account-password-error">
          <input id="staff-password" type="password" formControlName="initialPassword" autocomplete="new-password"
                 class="field-input" data-testid="staff-account-password-input" [attr.aria-invalid]="!!passwordError()"
                 [attr.aria-describedby]="describedBy('staff-password', !!passwordError())" />
        </app-form-field>
        <app-password-policy-checklist [password]="initialPassword()" />

        <app-form-field label="Confirmer" fieldId="staff-confirm" [required]="true" [error]="confirmError()"
                        errorTestId="staff-account-confirm-error">
          <input id="staff-confirm" type="password" formControlName="confirmation" autocomplete="new-password"
                 class="field-input" data-testid="staff-account-confirm-input" [attr.aria-invalid]="!!confirmError()"
                 [attr.aria-describedby]="describedBy('staff-confirm', !!confirmError())" />
        </app-form-field>

        <app-alert tone="info">L'agent devra changer ce mot de passe à sa première connexion.</app-alert>
        @if (serverError()) {
          <app-alert tone="danger" data-testid="staff-account-server-error">{{ serverError() }}</app-alert>
        }

        <div class="flex justify-end gap-2">
          <button app-button type="button" variant="secondary" data-testid="staff-account-dialog-cancel"
                  (click)="cancelled.emit()">Annuler</button>
          <button app-button type="submit" [loading]="busy()" data-testid="staff-account-dialog-confirm">
            Créer le compte
          </button>
        </div>
      </form>
    </app-dialog>
  `,
})
export class StaffAccountFormDialogComponent {
  readonly open = input(false);
  readonly busy = input(false);
  readonly serverError = input<string | null>(null);
  /** EMAIL_ALREADY_USED : message affiché sous le champ e-mail. */
  readonly emailTaken = input(false);
  readonly submitted = output<CreateStaffAccountRequest>();
  readonly cancelled = output<void>();

  protected readonly describedBy = describedBy;
  private readonly attempted = signal(false);

  protected readonly form = inject(NonNullableFormBuilder).group(
    {
      lastName: ['', [Validators.required, Validators.maxLength(100)]],
      firstName: ['', [Validators.required, Validators.maxLength(100)]],
      email: ['', [Validators.required, Validators.email]],
      role: ['' as StaffRole | '', Validators.required],
      initialPassword: ['', [Validators.required, passwordPolicyValidator]],
      confirmation: ['', Validators.required],
    },
    { validators: matchingPasswordsValidator('initialPassword', 'confirmation') },
  );
  protected readonly initialPassword = toSignal(this.form.controls.initialPassword.valueChanges, { initialValue: '' });

  constructor() {
    effect(() => {
      if (this.open()) {
        this.form.reset();
        this.attempted.set(false);
      }
    });
  }

  protected textError(name: 'lastName' | 'firstName'): string | null {
    const control = this.form.controls[name];
    if (!this.visible(control.touched)) {
      return null;
    }
    if (control.hasError('required')) {
      return GENERIC_MESSAGES.required;
    }
    return control.hasError('maxlength') ? AUTH_MESSAGES.maxLength100 : null;
  }

  protected emailError(): string | null {
    if (this.emailTaken()) {
      return AUTH_MESSAGES.emailAlreadyUsed;
    }
    const control = this.form.controls.email;
    if (!this.visible(control.touched)) {
      return null;
    }
    if (control.hasError('required')) {
      return GENERIC_MESSAGES.required;
    }
    return control.hasError('email') ? AUTH_MESSAGES.emailInvalid : null;
  }

  protected roleError(): string | null {
    return this.attempted() && this.form.controls.role.invalid ? AUTH_MESSAGES.roleRequired : null;
  }

  protected passwordError(): string | null {
    const control = this.form.controls.initialPassword;
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
      return;
    }
    const { lastName, firstName, email, role, initialPassword } = this.form.getRawValue();
    this.submitted.emit({ lastName, firstName, email, role: role as StaffRole, initialPassword });
  }

  private visible(touched: boolean): boolean {
    return touched || this.attempted();
  }
}
