import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';

import { ApiError } from '../../../core/http/api-error';
import { GENERIC_MESSAGES } from '../../../core/http/messages';
import { dataOf, viewState } from '../../../shared/data/view-state';
import { DEFAULT_PAGE_SIZE } from '../../../shared/models/page.model';
import { PageHeaderComponent } from '../../../shared/ui/page-header.component';
import { PaginationComponent } from '../../../shared/ui/pagination.component';
import { SearchInputComponent } from '../../../shared/ui/search-input.component';
import { EmptyStateComponent, ErrorStateComponent, LoadingStateComponent } from '../../../shared/ui/view-states.component';
import { commonErrorMessage } from '../../authentification/data-access/auth-messages';
import { BookService } from '../data-access/book.service';
import { CATALOG_MESSAGES } from '../data-access/catalog-messages';
import { CatalogSearchService } from '../data-access/catalog-search.service';
import { AdminSearchQuery, authorNames } from '../data-access/catalog.models';

type SortField = 'title' | 'publicationYear';

/** ECR-10 — Catalogue de gestion (US-007 AC-007-7, point d'entrée US-009). */
@Component({
  selector: 'app-catalog-admin-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, PageHeaderComponent, SearchInputComponent, PaginationComponent, LoadingStateComponent,
    EmptyStateComponent, ErrorStateComponent],
  template: `
    <app-page-header title="Catalogue">
      <a routerLink="/gestion/catalogue/nouvelle" data-testid="catalog-admin-new-button"
         class="inline-flex items-center rounded-md bg-primary px-4 py-2 text-sm font-medium text-surface hover:bg-primary-hover">
        + Nouvelle notice
      </a>
    </app-page-header>

    <div class="mb-4 grid gap-4 md:grid-cols-[2fr_1fr_auto] md:items-end">
      <app-search-input label="Titre, auteur ou ISBN" testId="catalog-admin-search" (search)="update({ q: $event })" />
      <div class="space-y-1">
        <label for="catalog-admin-category" class="block text-sm font-medium">Catégorie</label>
        <select id="catalog-admin-category" class="field-input" data-testid="catalog-admin-category-filter"
                (change)="onCategory($event)">
          <option value="">Toutes</option>
          @for (category of categories(); track category.id) {
            <option [value]="category.id">{{ category.label }}</option>
          }
        </select>
      </div>
      <label class="flex items-center gap-2 pb-2 text-sm">
        <input type="checkbox" data-testid="catalog-admin-include-withdrawn" (change)="onWithdrawn($event)" />
        Inclure les notices retirées
      </label>
    </div>

    @switch (state().kind) {
      @case ('loading') {
        <app-loading-state testId="catalog-admin-loading" />
      }
      @case ('error') {
        <app-error-state [message]="errorMessage()" testId="catalog-admin-error" (retry)="reload()" />
      }
      @case ('success') {
        @if (rows().length === 0) {
          <app-empty-state [message]="messages.noAdminResult" testId="catalog-admin-empty" />
        } @else {
          <div class="card overflow-x-auto">
            <table class="w-full text-left text-sm">
              <caption class="sr-only">Notices du catalogue</caption>
              <thead class="bg-surface-alt text-xs uppercase tracking-wide text-muted">
                <tr>
                  <th scope="col" class="px-4 py-3" [attr.aria-sort]="ariaSort('title')">
                    <button type="button" class="font-semibold" (click)="sortBy('title')">Titre {{ sortIcon('title') }}</button>
                  </th>
                  <th scope="col" class="px-4 py-3 font-semibold">Auteur(s)</th>
                  <th scope="col" class="px-4 py-3 font-semibold">ISBN</th>
                  <th scope="col" class="px-4 py-3" [attr.aria-sort]="ariaSort('publicationYear')">
                    <button type="button" class="font-semibold" (click)="sortBy('publicationYear')">
                      Année {{ sortIcon('publicationYear') }}
                    </button>
                  </th>
                  <th scope="col" class="px-4 py-3 font-semibold">Exemplaires</th>
                </tr>
              </thead>
              <tbody>
                @for (book of rows(); track book.id) {
                  <tr class="border-t border-border" data-testid="catalog-admin-row" [attr.data-row-id]="book.id">
                    <td class="px-4 py-3">
                      <a [routerLink]="['/gestion/catalogue', book.id]" class="font-medium text-primary hover:underline">{{ book.title }}</a>
                    </td>
                    <td class="px-4 py-3">{{ names(book.authors) }}</td>
                    <td class="px-4 py-3 tabular-nums">{{ book.isbn ?? '—' }}</td>
                    <td class="px-4 py-3 tabular-nums">{{ book.publicationYear ?? '—' }}</td>
                    <td class="px-4 py-3 tabular-nums">
                      @if (book.withdrawn) {
                        <span class="rounded-full bg-surface-alt px-2 py-0.5 text-xs text-muted" data-testid="catalog-admin-withdrawn-badge">Retirée</span>
                      } @else {
                        {{ book.availableCount }} / {{ book.totalCount }}
                        <span class="sr-only">disponible(s) sur {{ book.totalCount }}</span>
                      }
                    </td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
          <div class="mt-4">
            <app-pagination [page]="query().page" [size]="query().size" [totalPages]="totalPages()"
                            testId="catalog-admin-pagination" (pageChange)="update({ page: $event }, false)"
                            (sizeChange)="update({ size: $event })" />
          </div>
        }
      }
    }
  `,
})
export class CatalogAdminPage {
  private readonly books = inject(BookService);
  protected readonly messages = CATALOG_MESSAGES;
  protected readonly names = authorNames;
  protected readonly categories = toSignal(inject(CatalogSearchService).categories(), { initialValue: [] });

  protected readonly query = signal<AdminSearchQuery>({
    q: '', categoryId: null, includeWithdrawn: false, sort: 'title,asc', page: 0, size: DEFAULT_PAGE_SIZE,
  });
  private readonly reloadTick = signal(0);
  private readonly request = computed(() => {
    this.reloadTick();
    return { ...this.query() };
  });
  protected readonly state = viewState(this.request, (query) => this.books.search(query));
  protected readonly rows = computed(() => dataOf(this.state())?.content ?? []);
  protected readonly totalPages = computed(() => dataOf(this.state())?.totalPages ?? 0);
  protected readonly errorMessage = computed(() => {
    const state = this.state();
    return state.kind === 'error' && state.error instanceof ApiError ? commonErrorMessage(state.error) : GENERIC_MESSAGES.unexpected;
  });

  protected update(change: Partial<AdminSearchQuery>, resetPage = true): void {
    this.query.update((query) => ({ ...query, ...change, ...(resetPage ? { page: 0 } : {}) }));
  }

  protected onCategory(event: Event): void {
    const value = (event.target as HTMLSelectElement).value;
    this.update({ categoryId: value ? Number(value) : null });
  }

  protected onWithdrawn(event: Event): void {
    this.update({ includeWithdrawn: (event.target as HTMLInputElement).checked });
  }

  protected sortBy(field: SortField): void {
    const [current, direction] = this.query().sort.split(',');
    const next = current === field && direction === 'asc' ? 'desc' : 'asc';
    this.update({ sort: `${field},${next}` as AdminSearchQuery['sort'] });
  }

  protected ariaSort(field: SortField): 'ascending' | 'descending' | 'none' {
    const [current, direction] = this.query().sort.split(',');
    return current !== field ? 'none' : direction === 'asc' ? 'ascending' : 'descending';
  }

  protected sortIcon(field: SortField): string {
    const sort = this.ariaSort(field);
    return sort === 'none' ? '' : sort === 'ascending' ? '▲' : '▼';
  }

  protected reload(): void {
    this.reloadTick.update((tick) => tick + 1);
  }
}
