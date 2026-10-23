import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { Router } from '@angular/router';
import { of } from 'rxjs';

import { ApiError } from '../../../core/http/api-error';
import { GENERIC_MESSAGES } from '../../../core/http/messages';
import { ToastService } from '../../../core/notifications/toast.service';
import { dataOf, viewState } from '../../../shared/data/view-state';
import { ConfirmDialogComponent } from '../../../shared/ui/confirm-dialog.component';
import { PageHeaderComponent } from '../../../shared/ui/page-header.component';
import { ErrorStateComponent, LoadingStateComponent } from '../../../shared/ui/view-states.component';
import { BookFormComponent } from '../components/book-form.component';
import { BookService } from '../data-access/book.service';
import { CATALOG_MESSAGES, catalogErrorMessage } from '../data-access/catalog-messages';
import { CatalogSearchService } from '../data-access/catalog-search.service';
import { BookAdminDetail, BookRequest } from '../data-access/catalog.models';

/** ECR-11 — Création (`/gestion/catalogue/nouvelle`) ou modification (`/:bookId/modifier`) d'une notice. */
@Component({
  selector: 'app-book-form-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [PageHeaderComponent, BookFormComponent, LoadingStateComponent, ErrorStateComponent, ConfirmDialogComponent],
  template: `
    <app-page-header [title]="bookId() ? 'Modifier la notice' : 'Nouvelle notice'" />
    @switch (state().kind) {
      @case ('loading') {
        <app-loading-state testId="book-form-loading" [rows]="8" />
      }
      @case ('error') {
        <app-error-state [message]="loadError()" testId="book-form-error" (retry)="reload()" />
      }
      @default {
        <div class="card max-w-3xl p-6">
          <app-book-form [book]="book()" [languages]="languages()" [categories]="categories()" [busy]="busy()"
                         [serverError]="serverError()" [serverIsbnError]="isbnError()" [conflict]="conflict()"
                         (submitted)="save($event)" (cancelled)="cancel($event)" (reloadRequested)="reload()" />
        </div>
      }
    }
    <app-confirm-dialog [open]="confirmDiscard()" title="Abandonner les modifications" [message]="messages.discardChanges"
                        confirmLabel="Abandonner" testId="discard-changes" (confirmed)="leave()"
                        (cancelled)="confirmDiscard.set(false)" />
  `,
})
export class BookFormPage {
  /** Paramètre de route en modification. */
  readonly bookId = input<string>();

  private readonly books = inject(BookService);
  private readonly router = inject(Router);
  private readonly toasts = inject(ToastService);
  private readonly catalog = inject(CatalogSearchService);
  protected readonly messages = CATALOG_MESSAGES;
  protected readonly languages = toSignal(this.catalog.languages(), { initialValue: [] });
  protected readonly categories = toSignal(this.catalog.categories(), { initialValue: [] });

  private readonly reloadTick = signal(0);
  private readonly request = computed(() => {
    this.reloadTick();
    return { id: this.bookId() ? Number(this.bookId()) : null };
  });
  protected readonly state = viewState(this.request, ({ id }) =>
    id === null ? of<BookAdminDetail | null>(null) : this.books.get(id));
  protected readonly book = computed(() => dataOf(this.state()));
  protected readonly loadError = computed(() => {
    const state = this.state();
    if (state.kind !== 'error' || !(state.error instanceof ApiError)) {
      return GENERIC_MESSAGES.unexpected;
    }
    return state.error.status === 404 ? CATALOG_MESSAGES.noticeNotFound : catalogErrorMessage(state.error);
  });

  protected readonly busy = signal(false);
  protected readonly serverError = signal<string | null>(null);
  protected readonly isbnError = signal<string | null>(null);
  protected readonly conflict = signal(false);
  protected readonly confirmDiscard = signal(false);

  protected save(request: BookRequest): void {
    this.busy.set(true);
    this.serverError.set(null);
    this.isbnError.set(null);
    this.conflict.set(false);
    const id = this.book()?.id;
    (id ? this.books.update(id, request) : this.books.create(request)).subscribe({
      next: (saved) => {
        this.busy.set(false);
        this.toasts.success(CATALOG_MESSAGES.bookSaved);
        void this.router.navigate(['/gestion/catalogue', saved.id]);
      },
      error: (error: unknown) => {
        this.busy.set(false);
        if (!(error instanceof ApiError)) {
          this.serverError.set(GENERIC_MESSAGES.unexpected);
        } else if (error.code === 'CONCURRENT_MODIFICATION') {
          this.conflict.set(true);
        } else if (error.code === 'ISBN_INVALID' || error.code === 'ISBN_ALREADY_EXISTS') {
          this.isbnError.set(catalogErrorMessage(error));
        } else if (error.status !== 0 && error.status < 500) {
          this.serverError.set(catalogErrorMessage(error));
        }
      },
    });
  }

  protected cancel(dirty: boolean): void {
    if (dirty) {
      this.confirmDiscard.set(true);
    } else {
      this.leave();
    }
  }

  protected leave(): void {
    this.confirmDiscard.set(false);
    const id = this.book()?.id;
    void this.router.navigate(id ? ['/gestion/catalogue', id] : ['/gestion/catalogue']);
  }

  protected reload(): void {
    this.conflict.set(false);
    this.reloadTick.update((tick) => tick + 1);
  }
}
