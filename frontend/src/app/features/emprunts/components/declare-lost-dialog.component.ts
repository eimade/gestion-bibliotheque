import { ChangeDetectionStrategy, Component, computed, effect, inject, input, output, signal } from '@angular/core';

import { ApiError } from '../../../core/http/api-error';
import { GENERIC_MESSAGES } from '../../../core/http/messages';
import { ToastService } from '../../../core/notifications/toast.service';
import { DateFrPipe } from '../../../shared/pipes/date-fr.pipe';
import { EuroPipe } from '../../../shared/pipes/euro.pipe';
import { AlertComponent } from '../../../shared/ui/alert.component';
import { ButtonComponent } from '../../../shared/ui/button.component';
import { DialogComponent } from '../../../shared/ui/dialog.component';
import { LOAN_MESSAGES, loanErrorMessage, memberLabel } from '../data-access/loan-messages';
import { LossAmounts, MemberBrief } from '../data-access/loan.models';
import { LoanService } from '../data-access/loan.service';

/** Emprunt concerné par la déclaration de perte (ECR-27). */
export interface LossTarget {
  readonly loanId: number;
  readonly title: string;
  readonly copyNumber: string;
  readonly dueDate: string;
  readonly member?: MemberBrief | null;
}

/** ECR-27 — Déclarer un exemplaire perdu (US-029) : montants prévisualisés par le serveur (RG-36, RG-37). */
@Component({
  selector: 'app-declare-lost-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DialogComponent, AlertComponent, ButtonComponent, DateFrPipe, EuroPipe],
  template: `
    <app-dialog title="Déclarer perdu" [open]="!!target()" testId="declare-lost-dialog" (closed)="cancelled.emit()">
      @if (target(); as loan) {
        <p class="font-medium">{{ loan.title }} · {{ loan.copyNumber }}</p>
        @if (loan.member) { <p class="text-sm">Emprunté par {{ borrower() }}</p> }
        @if (preview(); as amounts) {
          <p class="text-sm">À rendre le {{ loan.dueDate | dateFr }}
            @if (amounts.lateDays > 0) { · {{ amounts.lateDays }} jours de retard }</p>
          <dl class="space-y-1 rounded-xl bg-surface-alt p-3 text-sm tabular-nums">
            <div class="flex justify-between"><dt>Retard ({{ amounts.lateDays }} j)</dt>
              <dd data-testid="declare-lost-late-fine">{{ amounts.lateFine | euro }}</dd></div>
            <div class="flex justify-between"><dt>Perte (forfait)</dt>
              <dd data-testid="declare-lost-item-fine">{{ amounts.lostItemFine | euro }}</dd></div>
            <div class="flex justify-between border-t border-border pt-1 font-semibold"><dt>Total</dt>
              <dd data-testid="declare-lost-total">{{ amounts.total | euro }}</dd></div>
          </dl>
          <app-alert tone="warning">L’emprunt sera clos et l’exemplaire marqué « Perdu ».</app-alert>
        } @else if (!error()) {
          <p class="text-sm text-muted" role="status" data-testid="declare-lost-loading">Calcul des pénalités…</p>
        }
        @if (error()) {
          <app-alert tone="danger" data-testid="declare-lost-server-error">{{ error() }}</app-alert>
        }
        <div class="flex justify-end gap-2">
          <button app-button type="button" variant="secondary" data-testid="declare-lost-cancel" (click)="cancelled.emit()">Annuler</button>
          <button app-button type="button" variant="danger" [loading]="busy()" [disabled]="!preview()"
                  data-testid="declare-lost-confirm" (click)="confirm(loan.loanId)">Déclarer perdu</button>
        </div>
      }
    </app-dialog>
  `,
})
export class DeclareLostDialogComponent {
  readonly target = input<LossTarget | null>(null);
  readonly declared = output<LossAmounts>();
  readonly cancelled = output<void>();

  private readonly loans = inject(LoanService);
  private readonly toasts = inject(ToastService);
  protected readonly preview = signal<LossAmounts | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly busy = signal(false);
  protected readonly borrower = computed(() => memberLabel(this.target()?.member));

  constructor() {
    effect(() => {
      const target = this.target();
      this.preview.set(null);
      this.error.set(null);
      if (target) {
        this.loans.lossPreview(target.loanId).subscribe({
          next: (amounts) => this.preview.set(amounts),
          error: (error: unknown) => this.error.set(this.message(error)),
        });
      }
    });
  }

  protected confirm(loanId: number): void {
    this.busy.set(true);
    this.loans.declareLost(loanId).subscribe({
      next: (amounts) => {
        this.busy.set(false);
        this.toasts.success(LOAN_MESSAGES.lossDeclared(amounts.total));
        this.declared.emit(amounts);
      },
      error: (error: unknown) => {
        this.busy.set(false);
        this.error.set(this.message(error));
      },
    });
  }

  private message(error: unknown): string {
    return error instanceof ApiError ? loanErrorMessage(error) : GENERIC_MESSAGES.unexpected;
  }
}
