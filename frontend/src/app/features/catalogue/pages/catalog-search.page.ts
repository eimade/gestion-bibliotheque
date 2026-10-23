import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { Router } from '@angular/router';

import { ApiError } from '../../../core/http/api-error';
import { GENERIC_MESSAGES } from '../../../core/http/messages';
import { dataOf, viewState } from '../../../shared/data/view-state';
import { DEFAULT_PAGE_SIZE } from '../../../shared/models/page.model';
import { IconComponent } from '../../../shared/ui/icon.component';
import { PaginationComponent } from '../../../shared/ui/pagination.component';
import { SearchInputComponent } from '../../../shared/ui/search-input.component';
import { EmptyStateComponent, ErrorStateComponent, LoadingStateComponent } from '../../../shared/ui/view-states.component';
import { commonErrorMessage } from '../../authentification/data-access/auth-messages';
import { BookResultCardComponent } from '../components/book-result-card.component';
import { LibraryHeroArtComponent } from '../components/library-hero-art.component';
import { CATALOG_MESSAGES } from '../data-access/catalog-messages';
import { CatalogSearchService } from '../data-access/catalog-search.service';
import { PublicSearchQuery, PublicSort } from '../data-access/catalog.models';

const SORTS: readonly PublicSort[] = ['relevance', 'title,asc', 'publicationYear,desc'];

/**
 * ECR-08 — Recherche publique (US-007). L'URL porte l'état (`q`, `categorie`, `langue`, `disponible`,
 * `tri`, `page`, `taille`) : un résultat se partage et le bouton « Retour » fonctionne.
 */
@Component({
  selector: 'app-catalog-search-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [SearchInputComponent, BookResultCardComponent, LibraryHeroArtComponent, IconComponent, PaginationComponent, LoadingStateComponent,
    EmptyStateComponent, ErrorStateComponent],
  template: `
    <section class="hero-gradient relative overflow-hidden">
      <div class="pointer-events-none absolute -left-24 -top-24 h-72 w-72 rounded-full bg-white/30 blur-2xl" aria-hidden="true"></div>
      <div class="pointer-events-none absolute -right-16 top-10 h-64 w-64 rounded-full bg-sky-300/30 blur-2xl" aria-hidden="true"></div>
      <div class="relative mx-auto grid max-w-6xl items-center gap-8 px-4 pb-24 pt-10 lg:grid-cols-[1.15fr_1fr] lg:px-8 lg:pb-28 lg:pt-14">
        <div>
          <p class="inline-flex items-center gap-2 rounded-full bg-white/70 px-3 py-1 text-xs font-semibold uppercase tracking-wider text-primary shadow-sm">
            <app-icon name="sparkle" [size]="14" /> Médiathèque en ligne
          </p>
          <h1 class="mt-4 text-4xl font-extrabold leading-[1.1] text-indigo-950 sm:text-5xl">
            Rechercher un livre,<br /><span class="text-accent">trouver votre prochaine lecture</span>
          </h1>
          <p class="mt-4 max-w-xl text-base text-indigo-900/75">
            Parcourez le catalogue, vérifiez la disponibilité des exemplaires en temps réel et réservez depuis votre espace.
          </p>
          <div class="mt-6 max-w-xl">
            <app-search-input label="Titre, auteur ou ISBN" placeholder="Titre, auteur ou ISBN — ex. Monte-Cristo, Zola…"
                              testId="catalog-search" [hero]="true"
                              [initialValue]="q() ?? ''" (search)="navigate({ q: $event || null })" />
          </div>
          @if (categories().length) {
            <ul class="mt-5 flex flex-wrap gap-2" aria-label="Catégories">
              @for (category of categories().slice(0, 6); track category.id) {
                <li>
                  <button type="button" (click)="navigate({ categorie: '' + category.id })"
                          [attr.aria-pressed]="categorie() === '' + category.id"
                          class="rounded-full border border-white/70 px-3.5 py-1.5 text-sm font-medium backdrop-blur transition motion-reduce:transition-none"
                          [class]="categorie() === '' + category.id ? 'bg-primary text-white' : 'bg-white/60 text-indigo-900 hover:bg-white hover:text-primary'">
                    {{ category.label }}
                  </button>
                </li>
              }
            </ul>
          }
        </div>
        <app-library-hero-art class="mx-auto hidden w-full max-w-md lg:block" />
      </div>
      <svg class="absolute inset-x-0 bottom-0 h-16 w-full text-slate-50 lg:h-24" viewBox="0 0 1440 120" preserveAspectRatio="none" aria-hidden="true">
        <path fill="currentColor" fill-opacity="0.5" d="M0 64c160 40 320 56 480 40s320-72 480-72 320 48 480 64v104H0z" />
        <path fill="currentColor" d="M0 88c200 24 360 32 540 8s340-64 520-56 260 40 380 48v32H0z" />
      </svg>
    </section>

    <div class="mx-auto max-w-6xl px-4 pb-16 lg:px-8">
      <div class="grid gap-6 lg:grid-cols-[15rem_1fr]">
        <aside class="card h-fit space-y-4 p-5 lg:sticky lg:top-24" aria-label="Filtres">
          <p class="flex items-center gap-2 text-sm font-bold text-ink"><app-icon name="settings" [size]="16" /> Affiner</p>
          <div class="space-y-1.5">
            <label for="catalog-category" class="block text-xs font-semibold uppercase tracking-wide text-muted">Catégorie</label>
            <select id="catalog-category" class="field-input" data-testid="catalog-filter-category"
                    (change)="onSelect('categorie', $event)">
              <option value="" [selected]="!categorie()">Toutes</option>
              @for (category of categories(); track category.id) {
                <option [value]="category.id" [selected]="categorie() === '' + category.id">{{ category.label }}</option>
              }
            </select>
          </div>
          <div class="space-y-1.5">
            <label for="catalog-language" class="block text-xs font-semibold uppercase tracking-wide text-muted">Langue</label>
            <select id="catalog-language" class="field-input" data-testid="catalog-filter-language"
                    (change)="onSelect('langue', $event)">
              <option value="" [selected]="!langue()">Toutes</option>
              @for (language of languages(); track language.code) {
                <option [value]="language.code" [selected]="langue() === language.code">{{ language.label }}</option>
              }
            </select>
          </div>
          <label class="flex cursor-pointer items-center gap-2.5 rounded-xl bg-success-soft px-3 py-2.5 text-sm font-medium text-success">
            <input type="checkbox" class="h-4 w-4 rounded accent-emerald-700" data-testid="catalog-filter-available"
                   [checked]="disponible() === '1'" (change)="onAvailable($event)" />
            Disponibles uniquement
          </label>
        </aside>

        <section class="min-w-0 space-y-5" aria-label="Résultats">
          @switch (state().kind) {
            @case ('idle') {
              <div class="card flex flex-col items-center gap-3 p-10 text-center">
                <span class="flex h-14 w-14 items-center justify-center rounded-2xl bg-primary-soft text-primary"><app-icon name="search" [size]="26" /></span>
                <p class="text-lg font-semibold text-ink">Que voulez-vous lire aujourd’hui ?</p>
                <p class="text-muted" data-testid="catalog-help">{{ messages.help }}</p>
              </div>
            }
            @case ('loading') {
              <app-loading-state testId="catalog-loading" [rows]="4" />
            }
            @case ('error') {
              <app-error-state [message]="errorMessage()" testId="catalog-error" (retry)="retry()" />
            }
            @case ('success') {
              <div class="flex flex-wrap items-center justify-between gap-3">
                <p aria-live="polite" class="text-sm font-semibold text-ink" data-testid="catalog-result-count">
                  {{ total() }} résultat{{ total() > 1 ? 's' : '' }}
                </p>
                <div class="flex items-center gap-2 text-sm">
                  <label for="catalog-sort" class="text-muted">Trier par</label>
                  <select id="catalog-sort" class="rounded-xl border border-border bg-surface px-3 py-1.5 font-medium shadow-sm"
                          data-testid="catalog-sort" (change)="onSelect('tri', $event)">
                    @for (option of sortOptions(); track option.value) {
                      <option [value]="option.value" [selected]="query()?.sort === option.value">{{ option.label }}</option>
                    }
                  </select>
                </div>
              </div>
              @if (results().length === 0) {
                <app-empty-state [message]="messages.noResult" testId="catalog-empty">
                  @if (hasFilters()) {
                    <button type="button" class="mt-3 text-sm font-semibold text-primary underline" data-testid="catalog-clear-filters"
                            (click)="clearFilters()">Effacer les filtres</button>
                  }
                </app-empty-state>
              } @else {
                <ul class="grid grid-cols-2 gap-x-3 gap-y-4 sm:grid-cols-3 xl:grid-cols-4">
                  @for (book of results(); track book.id) {
                    <li><app-book-result-card [book]="book" /></li>
                  }
                </ul>
                <app-pagination [page]="query()?.page ?? 0" [size]="query()?.size ?? defaultSize" [totalPages]="totalPages()"
                                testId="catalog-pagination" (pageChange)="navigate({ page: $event || null }, false)"
                                (sizeChange)="navigate({ taille: $event })" />
              }
            }
          }
        </section>
      </div>
    </div>
  `,
})
export class CatalogSearchPage {
  // Paramètres d'URL liés par le routeur
  readonly q = input<string>();
  readonly categorie = input<string>();
  readonly langue = input<string>();
  readonly disponible = input<string>();
  readonly tri = input<string>();
  readonly page = input<string>();
  readonly taille = input<string>();

  private readonly catalog = inject(CatalogSearchService);
  private readonly router = inject(Router);
  protected readonly messages = CATALOG_MESSAGES;
  protected readonly defaultSize = DEFAULT_PAGE_SIZE;
  protected readonly categories = toSignal(this.catalog.categories(), { initialValue: [] });
  protected readonly languages = toSignal(this.catalog.languages(), { initialValue: [] });

  /** Rien n'est chargé tant qu'aucun mot-clé ni filtre n'est choisi (UX ECR-08 « état initial »). */
  protected readonly query = computed<PublicSearchQuery | null>(() => {
    const q = (this.q() ?? '').trim();
    const categoryId = this.categorie() ? Number(this.categorie()) : null;
    const language = this.langue() || null;
    const available = this.disponible() === '1';
    if (!q && categoryId === null && !language && !available) {
      return null;
    }
    const sort = SORTS.includes(this.tri() as PublicSort) ? (this.tri() as PublicSort) : q ? 'relevance' : 'title,asc';
    return {
      q,
      categoryId,
      language,
      available,
      sort: sort === 'relevance' && !q ? 'title,asc' : sort,
      page: Math.max(0, Number(this.page() ?? 0) || 0),
      size: Number(this.taille()) || DEFAULT_PAGE_SIZE,
    };
  });

  /** « Réessayer » relance la même lecture : un nouvel objet de requête est émis. */
  private readonly reloadTick = signal(0);
  private readonly request = computed(() => {
    this.reloadTick();
    const query = this.query();
    return query ? { ...query } : null;
  });
  protected readonly state = viewState(this.request, (query) => this.catalog.search(query));
  protected readonly results = computed(() => dataOf(this.state())?.content ?? []);
  protected readonly total = computed(() => dataOf(this.state())?.totalElements ?? 0);
  protected readonly totalPages = computed(() => dataOf(this.state())?.totalPages ?? 0);
  protected readonly hasFilters = computed(() => !!(this.categorie() || this.langue() || this.disponible()));
  protected readonly errorMessage = computed(() => {
    const state = this.state();
    return state.kind === 'error' && state.error instanceof ApiError
      ? commonErrorMessage(state.error)
      : GENERIC_MESSAGES.unexpected;
  });
  protected readonly sortOptions = computed(() => [
    ...(this.q() ? [{ value: 'relevance', label: 'Pertinence' }] : []),
    { value: 'title,asc', label: 'Titre A→Z' },
    { value: 'publicationYear,desc', label: 'Année (récent d’abord)' },
  ]);

  /** Met à jour l'URL ; un changement de critère revient à la première page. */
  protected navigate(params: Record<string, string | number | null>, resetPage = true): void {
    void this.router.navigate([], {
      queryParams: { ...params, ...(resetPage ? { page: null } : {}) },
      queryParamsHandling: 'merge',
    });
  }

  protected onSelect(param: 'categorie' | 'langue' | 'tri', event: Event): void {
    const value = (event.target as HTMLSelectElement).value;
    this.navigate({ [param]: value || null });
  }

  protected onAvailable(event: Event): void {
    this.navigate({ disponible: (event.target as HTMLInputElement).checked ? '1' : null });
  }

  protected clearFilters(): void {
    this.navigate({ categorie: null, langue: null, disponible: null });
  }

  protected retry(): void {
    this.reloadTick.update((tick) => tick + 1);
  }
}
