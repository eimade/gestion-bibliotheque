import { ChangeDetectionStrategy, Component, computed, inject, input, output, signal } from '@angular/core';

import { ApiError } from '../../../core/http/api-error';
import { GENERIC_MESSAGES } from '../../../core/http/messages';
import { ToastService } from '../../../core/notifications/toast.service';
import { dataOf, viewState } from '../../../shared/data/view-state';
import { LOAN_STATUS_DISPLAY } from '../../../shared/models/statuses';
import { DateFrPipe } from '../../../shared/pipes/date-fr.pipe';
import { AlertComponent } from '../../../shared/ui/alert.component';
import { ButtonComponent } from '../../../shared/ui/button.component';
import { DialogComponent } from '../../../shared/ui/dialog.component';
import { PaginationComponent } from '../../../shared/ui/pagination.component';
import { StatusBadgeComponent } from '../../../shared/ui/status-badge.component';
import { LOAN_MESSAGES, holdLabel, loanErrorMessage, memberLabel } from '../data-access/loan-messages';
import { CopyRelease } from '../data-access/loan.models';
import { LoanService } from '../data-access/loan.service';

/** Exemplaire visé par une action de circulation depuis la fiche notice (ECR-12). */
export interface CopyTarget {
  readonly copyNumber: string;
  readonly title: string;
}

/** ECR-28 — Exemplaire retrouvé (US-030) : remis en rayon, ou mis de côté pour la file (RG-32). */
@Component({
  selector: 'app-copy-found-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DialogComponent, AlertComponent, ButtonComponent],
  template: `
    <app-dialog title="Exemplaire retrouvé" [open]="!!target()" testId="copy-found-dialog" (closed)="cancelled.emit()">
      @if (target(); as copy) {
        <p class="font-medium">{{ copy.title }} · {{ copy.copyNumber }} · Perdu</p>
        <p class="text-sm">L’exemplaire sera remis en circulation. Les pénalités déjà créées restent dues ; une remise peut être
          accordée par un administrateur.</p>
        @if (error()) { <app-alert tone="danger" data-testid="copy-found-server-error">{{ error() }}</app-alert> }
        <div class="flex justify-end gap-2">
          <button app-button type="button" variant="secondary" data-testid="copy-found-cancel" (click)="cancelled.emit()">Annuler</button>
          <button app-button type="button" [loading]="busy()" data-testid="copy-found-confirm" (click)="confirm(copy.copyNumber)">Confirmer</button>
        </div>
      }
    </app-dialog>
  `,
})
export class CopyFoundDialogComponent {
  readonly target = input<CopyTarget | null>(null);
  readonly found = output<CopyRelease>();
  readonly cancelled = output<void>();

  private readonly loans = inject(LoanService);
  private readonly toasts = inject(ToastService);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);

  protected confirm(copyNumber: string): void {
    this.busy.set(true);
    this.error.set(null);
    this.loans.markFound(copyNumber).subscribe({
      next: (release) => {
        this.busy.set(false);
        if (release.hold) {
          this.toasts.warning(`Exemplaire ${holdLabel(release.hold).replace(/^Mettre de côté/, 'mis de côté')}.`);
        } else {
          this.toasts.success(LOAN_MESSAGES.foundShelf(release.shelfMark));
        }
        this.found.emit(release);
      },
      error: (error: unknown) => {
        this.busy.set(false);
        this.error.set(error instanceof ApiError ? loanErrorMessage(error) : GENERIC_MESSAGES.unexpected);
      },
    });
  }
}

/**
 * ECR-29 — Historique de circulation d'un exemplaire (US-031), du plus récent au plus ancien ; l'emprunteur
 * d'un emprunt anonymisé n'apparaît plus (AC-031-2). Présenté en panneau modal.
 */
@Component({
  selector: 'app-copy-history-panel',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DialogComponent, ButtonComponent, PaginationComponent, StatusBadgeComponent, DateFrPipe],
  template: `
    <app-dialog [title]="'Historique — ' + (copyNumber() ?? '')" [open]="!!copyNumber()" testId="copy-history-drawer"
                (closed)="closed.emit()">
      @switch (state().kind) {
        @case ('loading') { <p role="status" class="text-sm text-muted" data-testid="copy-history-loading">Chargement…</p> }
        @case ('error') { <p class="text-sm text-danger" data-testid="copy-history-error">{{ genericError }}</p> }
        @case ('success') {
          @if (rows().length === 0) {
            <p data-testid="copy-history-empty">{{ messages.neverBorrowed }}</p>
          } @else {
            <ul class="divide-y divide-border">
              @for (loan of rows(); track $index) {
                <li class="py-2 text-sm" data-testid="copy-history-row">
                  <p class="tabular-nums">{{ loan.loanDate | dateFr }} → prévu {{ loan.dueDate | dateFr }}
                    @if (loan.returnDate) { · rendu le {{ loan.returnDate | dateFr }} }</p>
                  <p class="flex items-center gap-2">
                    <app-status-badge [display]="statusDisplay[loan.status]" [value]="loan.status" />
                    @if (loan.borrower) {
                      <span data-testid="copy-history-borrower">{{ label(loan.borrower) }}</span>
                    } @else {
                      <span class="text-muted" data-testid="copy-history-anonymized">Emprunteur : données effacées</span>
                    }
                  </p>
                </li>
              }
            </ul>
            <app-pagination [page]="page()" [size]="20" [totalPages]="totalPages()" testId="copy-history-pagination"
                            (pageChange)="page.set($event)" (sizeChange)="page.set(0)" />
          }
        }
      }
      <div class="flex justify-end">
        <button app-button type="button" variant="secondary" (click)="closed.emit()">Fermer</button>
      </div>
    </app-dialog>
  `,
})
export class CopyHistoryPanelComponent {
  readonly copyNumber = input<string | null>(null);
  readonly closed = output<void>();

  private readonly loans = inject(LoanService);
  protected readonly messages = LOAN_MESSAGES;
  protected readonly statusDisplay = LOAN_STATUS_DISPLAY;
  protected readonly label = memberLabel;
  protected readonly genericError = GENERIC_MESSAGES.unexpected;
  protected readonly page = signal(0);
  private readonly request = computed(() => {
    const copyNumber = this.copyNumber();
    return copyNumber ? { copyNumber, page: this.page() } : null;
  });
  protected readonly state = viewState(this.request, ({ copyNumber, page }) => this.loans.copyHistory(copyNumber, page));
  protected readonly rows = computed(() => dataOf(this.state())?.content ?? []);
  protected readonly totalPages = computed(() => dataOf(this.state())?.totalPages ?? 0);
}
