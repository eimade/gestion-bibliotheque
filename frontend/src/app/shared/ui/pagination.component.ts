import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';

import { PAGE_SIZES } from '../models/page.model';

/** Pagination serveur (UX-socle §5.3) : « Page x sur y », précédente/suivante, taille 20/50/100. */
@Component({
  selector: 'app-pagination',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <nav class="flex flex-wrap items-center justify-between gap-3 text-sm" aria-label="Pagination">
      <p aria-live="polite">Page {{ page() + 1 }} sur {{ pageCount() }}</p>
      <div class="flex items-center gap-2">
        <button type="button" class="rounded-xl border border-border bg-surface px-3.5 py-1.5 font-medium shadow-sm hover:bg-primary-soft hover:text-primary disabled:opacity-50 disabled:hover:bg-surface disabled:hover:text-ink"
                [disabled]="page() === 0" [attr.data-testid]="testId() + '-prev'"
                (click)="pageChange.emit(page() - 1)">
          <span aria-hidden="true">‹ </span>Précédente
        </button>
        <button type="button" class="rounded-xl border border-border bg-surface px-3.5 py-1.5 font-medium shadow-sm hover:bg-primary-soft hover:text-primary disabled:opacity-50 disabled:hover:bg-surface disabled:hover:text-ink"
                [disabled]="page() + 1 >= pageCount()" [attr.data-testid]="testId() + '-next'"
                (click)="pageChange.emit(page() + 1)">
          Suivante<span aria-hidden="true"> ›</span>
        </button>
        <label class="sr-only" [for]="testId() + '-size'">Éléments par page</label>
        <select class="rounded-xl border border-border bg-surface px-2 py-1.5 shadow-sm" [id]="testId() + '-size'"
                [attr.data-testid]="testId() + '-size'" [value]="size()"
                (change)="onSizeChange($event)">
          @for (option of sizes; track option) {
            <option [value]="option">{{ option }}</option>
          }
        </select>
      </div>
    </nav>
  `,
})
export class PaginationComponent {
  readonly page = input.required<number>();
  readonly size = input.required<number>();
  readonly totalPages = input.required<number>();
  readonly testId = input.required<string>();
  readonly pageChange = output<number>();
  readonly sizeChange = output<number>();

  protected readonly sizes = PAGE_SIZES;
  protected readonly pageCount = computed(() => Math.max(1, this.totalPages()));

  protected onSizeChange(event: Event): void {
    this.sizeChange.emit(Number((event.target as HTMLSelectElement).value));
  }
}
