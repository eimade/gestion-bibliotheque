import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  effect,
  inject,
  input,
  output,
  signal,
  viewChild,
} from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { GENERIC_MESSAGES } from '../../../core/http/messages';
import { AlertComponent } from '../../../shared/ui/alert.component';
import { ButtonComponent } from '../../../shared/ui/button.component';
import { FormFieldComponent, describedBy } from '../../../shared/ui/form-field.component';
import { LoginRequest } from '../data-access/account.models';
import { AUTH_MESSAGES } from '../data-access/auth-messages';

/** Formulaire de connexion (ECR-03), présentationnel. */
@Component({
  selector: 'app-login-form',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, FormFieldComponent, AlertComponent, ButtonComponent],
  template: `
    <form [formGroup]="form" (ngSubmit)="submit()" novalidate class="space-y-4">
      <app-form-field label="Adresse e-mail" fieldId="login-email" [required]="true"
                      [error]="emailError()" errorTestId="login-email-error">
        <input id="login-email" type="email" formControlName="email" autocomplete="username" class="field-input"
               data-testid="login-email-input" [attr.aria-invalid]="!!emailError()"
               [attr.aria-describedby]="describedBy('login-email', !!emailError())" />
      </app-form-field>

      <app-form-field label="Mot de passe" fieldId="login-password" [required]="true"
                      [error]="passwordError()" errorTestId="login-password-error">
        <div class="flex gap-2">
          <input #passwordInput id="login-password" formControlName="password" autocomplete="current-password"
                 class="field-input" data-testid="login-password-input"
                 [type]="passwordVisible() ? 'text' : 'password'" [attr.aria-invalid]="!!passwordError()"
                 [attr.aria-describedby]="describedBy('login-password', !!passwordError())" />
          <button type="button" class="rounded-xl border border-border bg-surface px-3 text-sm font-medium shadow-sm hover:bg-primary-soft hover:text-primary"
                  data-testid="login-password-toggle" [attr.aria-pressed]="passwordVisible()"
                  (click)="passwordVisible.set(!passwordVisible())">
            {{ passwordVisible() ? 'Masquer' : 'Afficher' }}
          </button>
        </div>
      </app-form-field>

      @if (serverError()) {
        <app-alert tone="danger" data-testid="login-server-error">{{ serverError() }}</app-alert>
      }

      <button app-button type="submit" class="w-full" [loading]="busy()" data-testid="login-submit">
        Se connecter
      </button>
    </form>
  `,
})
export class LoginFormComponent {
  readonly busy = input(false);
  readonly serverError = input<string | null>(null);
  readonly submitted = output<LoginRequest>();

  protected readonly describedBy = describedBy;
  protected readonly passwordVisible = signal(false);
  private readonly attempted = signal(false);
  private readonly passwordInput = viewChild.required<ElementRef<HTMLInputElement>>('passwordInput');

  protected readonly form = inject(NonNullableFormBuilder).group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', Validators.required],
  });

  constructor() {
    // Après un refus : mot de passe vidé et focus dessus (UX-authentification ECR-03). Seul le message du
    // serveur est affiché : le champ vidé ne doit pas afficher « obligatoire » avant une nouvelle tentative.
    effect(() => {
      if (this.serverError()) {
        this.form.controls.password.reset();
        this.attempted.set(false);
        this.passwordInput().nativeElement.focus();
      }
    });
  }

  protected emailError(): string | null {
    const control = this.form.controls.email;
    if (!this.shouldShow(control.touched)) {
      return null;
    }
    if (control.hasError('required')) {
      return GENERIC_MESSAGES.required;
    }
    return control.hasError('email') ? AUTH_MESSAGES.emailInvalid : null;
  }

  protected passwordError(): string | null {
    const control = this.form.controls.password;
    return this.shouldShow(control.touched) && control.hasError('required') ? GENERIC_MESSAGES.required : null;
  }

  protected submit(): void {
    this.attempted.set(true);
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      document.getElementById(this.form.controls.email.invalid ? 'login-email' : 'login-password')?.focus();
      return;
    }
    this.submitted.emit(this.form.getRawValue());
  }

  private shouldShow(touched: boolean): boolean {
    return touched || this.attempted();
  }
}
