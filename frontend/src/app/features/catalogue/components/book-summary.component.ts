import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import { AuthorView, CategoryOption, LanguageOption, authorNames } from '../data-access/catalog.models';

/** Champs descriptifs communs aux fiches publique et de gestion. */
export interface BookSummaryData {
  readonly isbn?: string | null;
  readonly subtitle?: string | null;
  readonly authors: readonly AuthorView[];
  readonly categories: readonly CategoryOption[];
  readonly publisher?: string | null;
  readonly publicationYear?: number | null;
  readonly language: LanguageOption;
  readonly pageCount?: number | null;
  readonly summary?: string | null;
}

/** Bloc d'informations d'une notice (ECR-09, ECR-12), sous forme de liste de définitions. */
@Component({
  selector: 'app-book-summary',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (book().subtitle) {
      <p class="text-lg text-muted">{{ book().subtitle }}</p>
    }
    <p class="mt-1 text-lg" data-testid="book-detail-authors">{{ authors() }}</p>
    <dl class="mt-4 grid grid-cols-1 gap-x-6 gap-y-2 text-sm sm:grid-cols-2">
      @for (item of items(); track item.label) {
        <div class="flex gap-2">
          <dt class="font-medium text-muted">{{ item.label }}</dt>
          <dd [attr.data-testid]="item.testId">{{ item.value }}</dd>
        </div>
      }
    </dl>
    @if (book().summary) {
      <h2 class="mt-6 text-base font-semibold">Résumé</h2>
      <p class="mt-1 whitespace-pre-line text-sm" data-testid="book-detail-summary">{{ book().summary }}</p>
    }
  `,
})
export class BookSummaryComponent {
  readonly book = input.required<BookSummaryData>();

  protected readonly authors = computed(() => authorNames(this.book().authors));
  protected readonly items = computed(() => {
    const book = this.book();
    const rows: { label: string; value: string; testId?: string }[] = [];
    if (book.publisher) rows.push({ label: 'Éditeur', value: book.publisher });
    if (book.publicationYear) rows.push({ label: 'Année', value: String(book.publicationYear) });
    rows.push({ label: 'Langue', value: book.language.label });
    if (book.pageCount) rows.push({ label: 'Pages', value: book.pageCount.toLocaleString('fr-FR') });
    rows.push({ label: 'Catégories', value: book.categories.map((c) => c.label).join(', ') });
    if (book.isbn) rows.push({ label: 'ISBN', value: book.isbn, testId: 'book-detail-isbn' });
    return rows;
  });
}
