import { ChangeDetectionStrategy, Component, afterNextRender, inject, input, output, signal } from '@angular/core';

import { ApiError } from '../../../core/http/api-error';
import { GENERIC_MESSAGES } from '../../../core/http/messages';
import { ToastService } from '../../../core/notifications/toast.service';
import { formatDateFr } from '../../../shared/pipes/date-fr.pipe';
import { ButtonComponent } from '../../../shared/ui/button.component';
import { DialogComponent } from '../../../shared/ui/dialog.component';
import { AlertComponent } from '../../../shared/ui/alert.component';
import { LOAN_MESSAGES, loanErrorMessage, renewalBlockedLabel } from '../data-access/loan-messages';
import { MemberLoan } from '../data-access/loan.models';
import { LoanService } from '../data-access/loan.service';

/**
 * Prolonger un emprunt (US-027) : bouton si prolongeable, sinon le motif en texte ; confirmation avec la
 * nouvelle date fournie par le serveur. `asMember` = l'adhérent connecté (ECR-22), sinon le guichet (ECR-18).
 */
@Component({
  selector: 'app-renew-loan-action',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ButtonComponent, DialogComponent, AlertComponent],
  template: `
    @if (showTrigger()) {
      @if (loan().renewal.allowed) {
        <button app-button type="button" variant="secondary" data-testid="renew-loan-button" (click)="open.set(true)">
          Prolonger
        </button>
      } @else if (showBlockedReason()) {
        <p class="text-sm text-muted" data-testid="renew-loan-blocked-reason">{{ blockedLabel() }}</p>
      }
    }
    <app-dialog title="Prolonger l’emprunt" [open]="open()" testId="renew-loan-dialog" (closed)="close()">
      <p>
        Prolonger l’emprunt de « {{ loan().title }} » jusqu’au
        <strong data-testid="renew-loan-new-date">{{ newDate() }}</strong> ?
      </p>
      @if (error()) {
        <app-alert tone="danger" data-testid="renew-loan-server-error">{{ error() }}</app-alert>
      }
      <div class="flex justify-end gap-2">
        <button app-button type="button" variant="secondary" data-testid="renew-loan-cancel" (click)="close()">Annuler</button>
        <button app-button type="button" [loading]="busy()" data-testid="renew-loan-confirm" (click)="confirm()">Prolonger</button>
      </div>
    </app-dialog>
  `,
})
export class RenewLoanActionComponent {
  readonly loan = input.required<MemberLoan>();
  readonly asMember = input(false);
  readonly threshold = input(5);
  readonly showBlockedReason = input(true);
  /** `false` : pas de bouton, le dialogue s'ouvre dès l'affichage (menu ⋮ de la fiche adhérent). */
  readonly showTrigger = input(true);
  readonly closed = output<void>();
  readonly renewed = output<MemberLoan>();

  private readonly loans = inject(LoanService);
  private readonly toasts = inject(ToastService);
  protected readonly open = signal(false);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);

  protected blockedLabel(): string {
    return renewalBlockedLabel(this.loan().renewal, this.threshold());
  }

  protected newDate(): string {
    return formatDateFr(this.loan().renewal.newDueDate);
  }

  constructor() {
    afterNextRender(() => {
      if (!this.showTrigger()) {
        this.open.set(true);
      }
    });
  }

  protected close(): void {
    this.open.set(false);
    this.error.set(null);
    this.closed.emit();
  }

  protected confirm(): void {
    this.busy.set(true);
    this.loans.renew(this.loan().id, this.asMember()).subscribe({
      next: (renewed) => {
        this.busy.set(false);
        this.open.set(false);
        this.toasts.success(LOAN_MESSAGES.renewed(renewed.dueDate));
        this.renewed.emit(renewed);
      },
      error: (error: unknown) => {
        this.busy.set(false);
        this.error.set(error instanceof ApiError ? loanErrorMessage(error, this.threshold()) : GENERIC_MESSAGES.unexpected);
      },
    });
  }
}
