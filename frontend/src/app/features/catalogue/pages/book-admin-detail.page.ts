import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';

import { ApiError } from '../../../core/http/api-error';
import { GENERIC_MESSAGES } from '../../../core/http/messages';
import { ToastService } from '../../../core/notifications/toast.service';
import { dataOf, viewState } from '../../../shared/data/view-state';
import { COPY_STATUS_DISPLAY, PHYSICAL_CONDITION_LABELS, WITHDRAWAL_REASON_LABELS, WithdrawalReason } from '../../../shared/models/statuses';
import { DateFrPipe } from '../../../shared/pipes/date-fr.pipe';
import { ButtonComponent } from '../../../shared/ui/button.component';
import { ConfirmDialogComponent } from '../../../shared/ui/confirm-dialog.component';
import { MenuButtonComponent, MenuItem } from '../../../shared/ui/menu-button.component';
import { StatusBadgeComponent } from '../../../shared/ui/status-badge.component';
import { EmptyStateComponent, ErrorStateComponent, LoadingStateComponent } from '../../../shared/ui/view-states.component';
import { AddCopiesDialogComponent } from '../components/add-copies-dialog.component';
import { BookSummaryComponent } from '../components/book-summary.component';
import { CopyEditDialogComponent, CopyUpdate } from '../components/copy-edit-dialog.component';
import { BookService } from '../data-access/book.service';
import { CATALOG_MESSAGES, catalogErrorMessage } from '../data-access/catalog-messages';
import { AddCopiesRequest, CopyView } from '../data-access/catalog.models';
import { CopyService } from '../data-access/copy.service';
import { CopyFoundDialogComponent, CopyHistoryPanelComponent, CopyTarget } from '../../emprunts/components/copy-circulation-dialogs.component';
import { ReservationQueuePanelComponent } from '../../reservations/components/reservation-queue-panel.component';

/**
 * ECR-12 — Fiche notice de gestion (US-008 AC-008-2, US-011, points d'entrée US-012 et US-013).
 * Circulation : historique d'un exemplaire (ECR-29), exemplaire retrouvé (ECR-28), file d'attente et
 * réservation pour un adhérent (ECR-31, ECR-32).
 */
@Component({
  selector: 'app-book-admin-detail-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, BookSummaryComponent, StatusBadgeComponent, MenuButtonComponent, ButtonComponent, DateFrPipe,
    ConfirmDialogComponent, AddCopiesDialogComponent, CopyEditDialogComponent, LoadingStateComponent,
    EmptyStateComponent, ErrorStateComponent, ReservationQueuePanelComponent, CopyFoundDialogComponent,
    CopyHistoryPanelComponent],
  template: `
    <nav class="mb-2 text-sm" aria-label="Fil d'Ariane">
      <a routerLink="/gestion/catalogue" class="text-primary underline">Catalogue</a>
      @if (book(); as book) { <span aria-hidden="true"> › </span><span>{{ book.title }}</span> }
    </nav>
    @switch (state().kind) {
      @case ('loading') {
        <app-loading-state testId="book-admin-loading" [rows]="6" />
      }
      @case ('error') {
        @if (notFound()) {
          <p class="card p-8 text-center" data-testid="book-admin-not-found">{{ messages.noticeNotFound }}</p>
        } @else {
          <app-error-state [message]="errorMessage()" testId="book-admin-error" (retry)="reload()" />
        }
      }
      @case ('success') {
        @if (book(); as book) {
          <div class="space-y-6">
            <section class="card p-6">
              <div class="flex flex-wrap items-start justify-between gap-4">
                <h1 class="text-2xl font-semibold" data-testid="book-admin-title">{{ book.title }}</h1>
                <div class="flex flex-wrap gap-2">
                  <a [routerLink]="['/gestion/catalogue', book.id, 'modifier']" data-testid="book-admin-edit-button"
                     class="inline-flex items-center gap-2 rounded-xl border border-border bg-surface px-4 py-2.5 text-sm font-semibold shadow-sm hover:border-primary/40 hover:bg-primary-soft hover:text-primary">
                    Modifier
                  </a>
                  <button app-button type="button" variant="danger" data-testid="book-admin-delete-button"
                          [disabled]="book.copies.length > 0" [attr.aria-describedby]="book.copies.length ? 'delete-reason' : null"
                          (click)="confirmDelete.set(true)">Supprimer</button>
                </div>
              </div>
              @if (book.copies.length > 0) {
                <p id="delete-reason" class="mt-2 text-right text-xs text-muted" data-testid="book-admin-delete-reason">
                  {{ messages.bookHasCopies }}
                </p>
              }
              <app-book-summary [book]="book" />
            </section>

            <section class="card p-6">
              <div class="mb-4 flex flex-wrap items-center justify-between gap-3">
                <h2 class="text-lg font-bold">Exemplaires ({{ book.copies.length }})</h2>
                <button app-button type="button" data-testid="copy-admin-add-button" (click)="openAdd()">+ Ajouter</button>
              </div>
              @if (book.copies.length === 0) {
                <app-empty-state [message]="messages.noCopy" testId="book-admin-empty" />
              } @else {
                <div class="overflow-x-auto">
                  <table class="w-full text-left text-sm">
                    <caption class="sr-only">Exemplaires de {{ book.title }}</caption>
                    <thead class="bg-surface-alt text-xs uppercase tracking-wide text-muted">
                      <tr>
                        <th scope="col" class="px-4 py-3 font-semibold">N°</th><th scope="col" class="px-4 py-3 font-semibold">Cote</th>
                        <th scope="col" class="px-4 py-3 font-semibold">État</th><th scope="col" class="px-4 py-3 font-semibold">Acquis le</th>
                        <th scope="col" class="px-4 py-3 font-semibold">Statut</th><th scope="col" class="px-4 py-3 font-semibold"><span class="sr-only">Actions</span></th>
                      </tr>
                    </thead>
                    <tbody>
                      @for (copy of book.copies; track copy.id) {
                        <tr class="border-t border-border" data-testid="copy-admin-row" [attr.data-row-id]="copy.copyNumber">
                          <td class="px-4 py-3 tabular-nums">{{ copy.copyNumber }}</td>
                          <td class="px-4 py-3">{{ copy.shelfMark }}</td>
                          <td class="px-4 py-3">{{ conditionLabels[copy.physicalCondition] }}</td>
                          <td class="px-4 py-3 tabular-nums">{{ copy.acquisitionDate | dateFr }}</td>
                          <td class="px-4 py-3">
                            <app-status-badge [display]="statusDisplay[copy.status]" [value]="copy.status" />
                            @if (copy.withdrawalReason) { <span class="ml-1 text-xs text-muted">{{ reasonLabels[copy.withdrawalReason] }}</span> }
                            @if (copy.dueDate) { <span class="ml-1 text-xs text-muted">retour prévu le {{ copy.dueDate | dateFr }}</span> }
                          </td>
                          <td class="px-4 py-3 text-right">
                            <app-menu-button [label]="'Actions pour l’exemplaire ' + copy.copyNumber" [items]="menuFor(copy)"
                                             testId="copy-admin-row-menu" (selected)="openEdit(copy, $event)" />
                          </td>
                        </tr>
                      }
                    </tbody>
                  </table>
                </div>
              }
            </section>

            <app-reservation-queue-panel [bookId]="book.id" [title]="book.title" (changed)="reload()" />
          </div>

          <app-confirm-dialog [open]="confirmDelete()" title="Supprimer la notice"
                              [message]="'Supprimer définitivement la notice « ' + book.title + ' » ?'"
                              confirmLabel="Supprimer" [busy]="busy()" testId="book-delete"
                              (confirmed)="deleteBook(book.id)" (cancelled)="confirmDelete.set(false)" />
          <app-add-copies-dialog [open]="addOpen()" [bookTitle]="book.title" [busy]="busy()" [serverError]="dialogError()"
                                 (submitted)="addCopies(book.id, $event)" (cancelled)="addOpen.set(false)" />
          <app-copy-found-dialog [target]="foundCopy()" (found)="foundCopy.set(null); reload()" (cancelled)="foundCopy.set(null)" />
          <app-copy-history-panel [copyNumber]="historyCopy()" (closed)="historyCopy.set(null)" />
          <app-copy-edit-dialog [open]="!!editedCopy()" [copy]="editedCopy()" [bookTitle]="book.title" [busy]="busy()"
                                [serverError]="dialogError()" (saved)="saveCopy($event)" (withdrawn)="withdrawCopy($event)"
                                (cancelled)="editedCopy.set(null)" />
        }
      }
    }
  `,
})
export class BookAdminDetailPage {
  readonly bookId = input.required<string>();

  private readonly books = inject(BookService);
  private readonly copies = inject(CopyService);
  private readonly toasts = inject(ToastService);
  private readonly router = inject(Router);
  protected readonly messages = CATALOG_MESSAGES;
  protected readonly statusDisplay = COPY_STATUS_DISPLAY;
  protected readonly conditionLabels = PHYSICAL_CONDITION_LABELS;
  protected readonly reasonLabels = WITHDRAWAL_REASON_LABELS;

  private readonly reloadTick = signal(0);
  private readonly request = computed(() => {
    this.reloadTick();
    return { id: Number(this.bookId()) };
  });
  protected readonly state = viewState(this.request, ({ id }) => this.books.get(id));
  protected readonly book = computed(() => dataOf(this.state()));
  protected readonly notFound = computed(() => {
    const state = this.state();
    return state.kind === 'error' && state.error instanceof ApiError && state.error.status === 404;
  });
  protected readonly errorMessage = computed(() => {
    const state = this.state();
    return state.kind === 'error' && state.error instanceof ApiError ? catalogErrorMessage(state.error) : GENERIC_MESSAGES.unexpected;
  });

  protected readonly busy = signal(false);
  protected readonly dialogError = signal<string | null>(null);
  protected readonly confirmDelete = signal(false);
  protected readonly addOpen = signal(false);
  protected readonly editedCopy = signal<CopyView | null>(null);
  protected readonly foundCopy = signal<CopyTarget | null>(null);
  protected readonly historyCopy = signal<string | null>(null);

  protected menuFor(copy: CopyView): readonly MenuItem[] {
    return [
      { id: 'edit', label: 'Modifier', testId: 'copy-admin-edit-action' },
      { id: 'withdraw', label: 'Retirer du fonds', testId: 'copy-admin-withdraw-action', disabled: copy.status === 'WITHDRAWN' },
      { id: 'history', label: 'Historique', testId: 'copy-admin-history-action' },
      ...(copy.status === 'LOST' ? [{ id: 'found', label: 'Exemplaire retrouvé', testId: 'copy-admin-found-action' }] : []),
    ];
  }

  protected openAdd(): void {
    this.dialogError.set(null);
    this.addOpen.set(true);
  }

  protected openEdit(copy: CopyView, action: string): void {
    const book = this.book();
    if (action === 'history') {
      this.historyCopy.set(copy.copyNumber);
    } else if (action === 'found' && book) {
      this.foundCopy.set({ copyNumber: copy.copyNumber, title: book.title });
    } else {
      this.dialogError.set(null);
      this.editedCopy.set(copy);
    }
  }

  protected addCopies(bookId: number, request: AddCopiesRequest): void {
    this.run(this.books.addCopies(bookId, request), (created) => {
      this.addOpen.set(false);
      this.toasts.success(CATALOG_MESSAGES.copiesAdded(created.map((copy) => copy.copyNumber)));
    });
  }

  protected saveCopy(update: CopyUpdate): void {
    const copy = this.editedCopy();
    if (copy) {
      this.run(this.copies.update(copy.copyNumber, update.shelfMark, update.physicalCondition, copy.version), () => {
        this.editedCopy.set(null);
        this.toasts.success(CATALOG_MESSAGES.copyUpdated);
      });
    }
  }

  protected withdrawCopy(reason: WithdrawalReason): void {
    const copy = this.editedCopy();
    if (copy) {
      this.run(this.copies.withdraw(copy.copyNumber, reason), () => {
        this.editedCopy.set(null);
        this.toasts.success(CATALOG_MESSAGES.copyWithdrawnToast);
      });
    }
  }

  protected deleteBook(bookId: number): void {
    this.busy.set(true);
    this.books.remove(bookId).subscribe({
      next: () => {
        this.busy.set(false);
        this.toasts.success(CATALOG_MESSAGES.bookDeleted);
        void this.router.navigateByUrl('/gestion/catalogue');
      },
      error: (error: unknown) => {
        this.busy.set(false);
        this.confirmDelete.set(false);
        if (error instanceof ApiError && error.status > 0 && error.status < 500) {
          this.toasts.error(catalogErrorMessage(error));
        }
      },
    });
  }

  protected reload(): void {
    this.reloadTick.update((tick) => tick + 1);
  }

  /** Exécute une action d'exemplaire puis recharge la fiche ; l'erreur s'affiche dans le dialogue ouvert. */
  private run<T>(action: import('rxjs').Observable<T>, onSuccess: (result: T) => void): void {
    this.busy.set(true);
    this.dialogError.set(null);
    action.subscribe({
      next: (result) => {
        this.busy.set(false);
        onSuccess(result);
        this.reload();
      },
      error: (error: unknown) => {
        this.busy.set(false);
        this.dialogError.set(error instanceof ApiError ? catalogErrorMessage(error) : GENERIC_MESSAGES.unexpected);
      },
    });
  }
}
