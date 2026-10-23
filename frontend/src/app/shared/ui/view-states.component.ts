import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';

import { ButtonComponent } from './button.component';
import { IconComponent } from './icon.component';

/** État « chargement » : squelettes à la place du contenu, annoncés aux lecteurs d'écran (UX-socle §5.7). */
@Component({
  selector: 'app-loading-state',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div role="status" class="space-y-2" [attr.data-testid]="testId()">
      <span class="sr-only">Chargement…</span>
      @for (row of rowList(); track row) {
        <div class="h-12 animate-pulse rounded-xl bg-slate-200/70 motion-reduce:animate-none" aria-hidden="true"></div>
      }
    </div>
  `,
})
export class LoadingStateComponent {
  readonly rows = input(5);
  readonly testId = input<string>();
  protected readonly rowList = computed(() => Array.from({ length: this.rows() }, (_, index) => index));
}

/** État « vide » avec le message propre à l'écran. */
@Component({
  selector: 'app-empty-state',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent],
  template: `
    <div class="rounded-2xl border-2 border-dashed border-indigo-100 bg-surface p-10 text-center text-muted"
         [attr.data-testid]="testId()">
      <span class="mx-auto mb-3 flex h-12 w-12 items-center justify-center rounded-2xl bg-primary-soft text-primary"><app-icon name="books" [size]="24" /></span>
      <p>{{ message() }}</p>
      <ng-content />
    </div>
  `,
})
export class EmptyStateComponent {
  readonly message = input.required<string>();
  readonly testId = input<string>();
}

/** État « erreur » avec « Réessayer » qui relance la même lecture ; `data-testid` du bouton : `<testId>-retry`. */
@Component({
  selector: 'app-error-state',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ButtonComponent],
  template: `
    <div role="alert" class="rounded-2xl border border-danger/20 bg-danger-soft p-6 text-center text-danger"
         [attr.data-testid]="testId()">
      <p class="mb-3">{{ message() }}</p>
      <button app-button type="button" variant="secondary" [attr.data-testid]="testId() + '-retry'"
              (click)="retry.emit()">
        Réessayer
      </button>
    </div>
  `,
})
export class ErrorStateComponent {
  readonly message = input.required<string>();
  readonly testId = input.required<string>();
  readonly retry = output<void>();
}
