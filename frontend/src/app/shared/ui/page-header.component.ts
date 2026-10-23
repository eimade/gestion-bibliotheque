import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/** Titre de page (`h1` unique) et zone d'actions projetée (UX-socle §5.3). */
@Component({
  selector: 'app-page-header',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="mb-6 flex flex-wrap items-end justify-between gap-4">
      <div>
        @if (eyebrow()) { <p class="text-xs font-semibold uppercase tracking-wider text-primary">{{ eyebrow() }}</p> }
        <h1 class="text-2xl font-bold text-ink sm:text-3xl" [attr.data-testid]="testId()">{{ title() }}</h1>
        @if (subtitle()) { <p class="mt-1 text-sm text-muted">{{ subtitle() }}</p> }
      </div>
      <div class="flex flex-wrap gap-2"><ng-content /></div>
    </div>
  `,
})
export class PageHeaderComponent {
  readonly title = input.required<string>();
  readonly testId = input<string>();
  readonly eyebrow = input<string>();
  readonly subtitle = input<string>();
}
