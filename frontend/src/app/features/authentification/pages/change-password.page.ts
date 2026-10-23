import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { Router } from '@angular/router';

import { AuthService, homeUrlFor } from '../../../core/auth/auth.service';
import { SignOutService } from '../../../core/auth/sign-out.service';
import { ApiError } from '../../../core/http/api-error';
import { GENERIC_MESSAGES } from '../../../core/http/messages';
import { ToastService } from '../../../core/notifications/toast.service';
import { AlertComponent } from '../../../shared/ui/alert.component';
import { PageHeaderComponent } from '../../../shared/ui/page-header.component';
import { PasswordFormComponent, PasswordServerField } from '../components/password-form.component';
import { ChangePasswordRequest } from '../data-access/account.models';
import { AUTH_MESSAGES, passwordErrorMessage } from '../data-access/auth-messages';

/** ECR-04 — Changer mon mot de passe (US-003, AC-001-7). */
@Component({
  selector: 'app-change-password-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [PasswordFormComponent, AlertComponent, PageHeaderComponent],
  template: `
    <div class="mx-auto max-w-xl space-y-6">
      <app-page-header title="Changer mon mot de passe" />
      @if (forced()) {
        <app-alert tone="info" data-testid="password-forced-banner">{{ forcedMessage }}</app-alert>
      }
      <div class="card p-6">
        <app-password-form [busy]="busy()" [forced]="forced()" [serverError]="serverError()"
                           [serverField]="serverField()" (submitted)="change($event)" (cancelled)="cancel()" />
      </div>
    </div>
  `,
})
export class ChangePasswordPage {
  /** `?obligatoire=1` posé par le guard ou après la connexion. */
  readonly obligatoire = input<string>();

  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly toasts = inject(ToastService);
  private readonly signOut = inject(SignOutService);

  protected readonly forced = computed(() => !!this.obligatoire() || !!this.auth.user()?.passwordTemporary);
  protected readonly busy = signal(false);
  protected readonly serverError = signal<string | null>(null);
  protected readonly serverField = signal<PasswordServerField>(null);
  protected readonly forcedMessage = AUTH_MESSAGES.forcedChange;

  protected change(request: ChangePasswordRequest): void {
    const wasForced = this.forced();
    this.busy.set(true);
    this.serverError.set(null);
    this.serverField.set(null);
    this.auth.changePassword(request.currentPassword, request.newPassword).subscribe({
      next: () => {
        this.busy.set(false);
        this.toasts.success(AUTH_MESSAGES.passwordChanged);
        const user = this.auth.user();
        if (wasForced || !user || window.history.length <= 1) {
          void this.router.navigateByUrl(user ? homeUrlFor(user) : '/connexion');
        } else {
          window.history.back();
        }
      },
      error: (error: unknown) => {
        this.busy.set(false);
        if (error instanceof ApiError) {
          this.serverError.set(passwordErrorMessage(error));
          this.serverField.set(error.code === 'CURRENT_PASSWORD_INCORRECT' ? 'current' : 'new');
        } else {
          this.serverError.set(GENERIC_MESSAGES.unexpected);
        }
      },
    });
  }

  /** En mode obligatoire, le seul autre choix est la déconnexion (UX-authentification ECR-04). */
  protected cancel(): void {
    if (this.forced()) {
      this.signOut.signOut();
      return;
    }
    const user = this.auth.user();
    if (window.history.length > 1) {
      window.history.back();
    } else if (user) {
      void this.router.navigateByUrl(homeUrlFor(user));
    }
  }
}
