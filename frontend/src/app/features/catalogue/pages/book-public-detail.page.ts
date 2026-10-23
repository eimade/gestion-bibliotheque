import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { ApiError } from '../../../core/http/api-error';
import { GENERIC_MESSAGES } from '../../../core/http/messages';
import { dataOf, viewState } from '../../../shared/data/view-state';
import { COPY_STATUS_DISPLAY } from '../../../shared/models/statuses';
import { DateFrPipe } from '../../../shared/pipes/date-fr.pipe';
import { BookCoverComponent } from '../../../shared/ui/book-cover.component';
import { StatusBadgeComponent } from '../../../shared/ui/status-badge.component';
import { ErrorStateComponent, LoadingStateComponent } from '../../../shared/ui/view-states.component';
import { commonErrorMessage } from '../../authentification/data-access/auth-messages';
import { AvailabilityBadgeComponent } from '../components/availability-badge.component';
import { BookSummaryComponent } from '../components/book-summary.component';
import { CATALOG_MESSAGES } from '../data-access/catalog-messages';
import { CatalogSearchService } from '../data-access/catalog-search.service';
import { AuthorView, authorNames } from '../data-access/catalog.models';
import { ReservationCtaComponent } from '../../reservations/components/reservation-cta.component';

/**
 * ECR-09 — Fiche publique (US-008). Aucun numéro d'exemplaire ni emprunteur (AC-008-1). La réservation
 * (US-032) passe par l'appel à réserver, selon le profil (UX-reservations).
 */
@Component({
  selector: 'app-book-public-detail-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, BookCoverComponent, BookSummaryComponent, AvailabilityBadgeComponent, StatusBadgeComponent, DateFrPipe,
    LoadingStateComponent, ErrorStateComponent, ReservationCtaComponent],
  template: `
    <div class="mx-auto max-w-6xl px-4 py-8 lg:px-8">
      <a routerLink="/catalogue" class="inline-flex items-center gap-1 rounded-lg text-sm font-semibold text-primary hover:underline" data-testid="book-detail-back">
        <span aria-hidden="true">‹ </span>Retour au catalogue
      </a>
      @switch (state().kind) {
        @case ('loading') {
          <div class="mt-4"><app-loading-state testId="book-detail-loading" [rows]="6" /></div>
        }
        @case ('error') {
          @if (notFound()) {
            <div class="card mt-6 p-10 text-center" data-testid="book-detail-not-found">
              <p class="mb-3">{{ messages.bookNotFound }}</p>
              <a routerLink="/catalogue" class="font-semibold text-primary underline">Retour au catalogue</a>
            </div>
          } @else {
            <div class="mt-4"><app-error-state [message]="errorMessage()" testId="book-detail-error" (retry)="retry()" /></div>
          }
        }
        @case ('success') {
          @if (book(); as book) {
            <article class="mt-5 grid gap-8 lg:grid-cols-[18rem_1fr]">
              <div class="space-y-4">
                <div class="hero-gradient rounded-3xl p-8">
                  <app-book-cover [title]="book.title" [author]="authorsOf(book)" [large]="true" class="mx-auto max-w-[13rem]" />
                </div>
                <div class="card p-4 text-center">
                  <app-availability-badge [available]="book.availableCount" [total]="book.totalCount" />
                  <div class="mt-3">
                    <app-reservation-cta [bookId]="book.id" [title]="book.title" [available]="book.availableCount" />
                  </div>
                </div>
              </div>

              <div class="min-w-0 space-y-6">
                <header class="card p-6 sm:p-8">
                  @if (book.categories.length) {
                    <p class="mb-2 flex flex-wrap gap-1.5">
                      @for (category of book.categories; track category.id) {
                        <span class="rounded-full bg-primary-soft px-2.5 py-0.5 text-xs font-semibold text-primary">{{ category.label }}</span>
                      }
                    </p>
                  }
                  <h1 class="text-3xl font-extrabold text-ink sm:text-4xl" data-testid="book-detail-title">{{ book.title }}</h1>
                  <app-book-summary [book]="book" />
                </header>

                <section class="card overflow-hidden">
                  <div class="flex flex-wrap items-center gap-3 border-b border-border px-6 py-4">
                    <h2 class="text-lg font-bold">Exemplaires</h2>
                    <app-availability-badge [available]="book.availableCount" [total]="book.totalCount" />
                  </div>
                  <table class="w-full text-left text-sm">
                    <caption class="sr-only">Exemplaires de {{ book.title }}</caption>
                    <thead class="bg-surface-alt text-xs uppercase tracking-wide text-muted">
                      <tr><th scope="col" class="px-6 py-2.5 font-semibold">Cote</th><th scope="col" class="px-6 py-2.5 font-semibold">Statut</th></tr>
                    </thead>
                    <tbody>
                      @for (copy of book.copies; track $index) {
                        <tr class="border-t border-border" data-testid="book-copy-row" [attr.data-row-id]="copy.shelfMark + '-' + $index">
                          <td class="px-6 py-3 font-medium">{{ copy.shelfMark }}</td>
                          <td class="px-6 py-3" data-testid="book-copy-status">
                            <app-status-badge [display]="statusDisplay[copy.status]" [value]="copy.status" />
                            @if (copy.dueDate) {
                              <span class="ml-2 text-muted" data-testid="book-copy-due-date">
                                retour prévu le {{ copy.dueDate | dateFr }}
                              </span>
                            }
                          </td>
                        </tr>
                      }
                    </tbody>
                  </table>
                  @if (book.availableCount === 0) {
                    <p class="border-t border-border px-6 py-3 text-sm" data-testid="book-none-available-info">{{ messages.noneAvailable }}</p>
                  }
                </section>
              </div>
            </article>
          }
        }
      }
    </div>
  `,
})
export class BookPublicDetailPage {
  /** Paramètre de route `:bookId`. */
  readonly bookId = input.required<string>();

  private readonly catalog = inject(CatalogSearchService);
  protected readonly messages = CATALOG_MESSAGES;
  protected readonly statusDisplay = COPY_STATUS_DISPLAY;
  private readonly reloadTick = signal(0);
  private readonly request = computed(() => {
    this.reloadTick();
    return { id: Number(this.bookId()) };
  });
  protected readonly state = viewState(this.request, ({ id }) => this.catalog.book(id));
  protected readonly book = computed(() => dataOf(this.state()));
  protected readonly notFound = computed(() => {
    const state = this.state();
    return state.kind === 'error' && state.error instanceof ApiError && state.error.status === 404;
  });
  protected readonly errorMessage = computed(() => {
    const state = this.state();
    return state.kind === 'error' && state.error instanceof ApiError
      ? commonErrorMessage(state.error)
      : GENERIC_MESSAGES.unexpected;
  });

  protected authorsOf(book: { authors: readonly AuthorView[] }): string {
    return authorNames(book.authors);
  }

  protected retry(): void {
    this.reloadTick.update((tick) => tick + 1);
  }
}
