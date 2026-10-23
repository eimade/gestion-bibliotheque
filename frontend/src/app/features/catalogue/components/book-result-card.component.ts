import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { RouterLink } from '@angular/router';

import { BookCoverComponent } from '../../../shared/ui/book-cover.component';
import { BookSearchItem, authorNames } from '../data-access/catalog.models';
import { AvailabilityBadgeComponent } from './availability-badge.component';

/**
 * Résultat de recherche publique (ECR-08), présenté en vignette avec couverture : le lien est le titre
 * (WCAG 2.4.4) et couvre toute la carte grâce à un pseudo-élément.
 */
@Component({
  selector: 'app-book-result-card',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'block h-full' },
  imports: [RouterLink, AvailabilityBadgeComponent, BookCoverComponent],
  template: `
    <article class="group relative flex h-full flex-col rounded-2xl p-2 transition hover:bg-white hover:shadow-lift motion-reduce:transition-none"
             data-testid="catalog-result-row" [attr.data-row-id]="book().id">
      <app-book-cover [title]="book().title" [author]="authors()"
                      class="transition duration-300 group-hover:-translate-y-1 motion-reduce:transition-none motion-reduce:group-hover:translate-y-0" />
      <div class="flex flex-1 flex-col px-1 pb-1 pt-3">
        <h2 class="line-clamp-2 text-sm font-semibold leading-snug text-ink">
          <a [routerLink]="['/catalogue', book().id]" data-testid="catalog-result-title"
             class="after:absolute after:inset-0 after:rounded-2xl hover:text-primary focus-visible:after:ring-2 focus-visible:after:ring-focus">
            {{ book().title }}
          </a>
        </h2>
        @if (book().subtitle) {
          <p class="line-clamp-1 text-xs text-muted">{{ book().subtitle }}</p>
        }
        <p class="mt-0.5 line-clamp-1 text-xs text-slate-600">{{ details() }}</p>
        <div class="mt-auto pt-2.5">
          <app-availability-badge [available]="book().availableCount" [total]="book().totalCount"
                                  testId="catalog-result-availability" />
        </div>
      </div>
    </article>
  `,
})
export class BookResultCardComponent {
  readonly book = input.required<BookSearchItem>();

  protected readonly authors = computed(() => authorNames(this.book().authors));
  protected readonly details = computed(() => {
    const book = this.book();
    return [this.authors(), book.publicationYear, book.categories.map((c) => c.label).join(', ')]
      .filter((part) => part !== null && part !== undefined && part !== '')
      .join(' · ');
  });
}
