import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { RouterLink } from '@angular/router';

import { IconComponent, IconName } from './icon.component';

export type StatTone = 'primary' | 'brand' | 'accent' | 'danger' | 'warning' | 'success' | 'info';

const TONES: Record<StatTone, string> = {
  primary: 'bg-primary-soft text-primary',
  brand: 'bg-brand-soft text-brand',
  accent: 'bg-accent-soft text-accent',
  danger: 'bg-danger-soft text-danger',
  warning: 'bg-warning-soft text-warning',
  success: 'bg-success-soft text-success',
  info: 'bg-info-soft text-info',
};

/**
 * Indicateur chiffré (UX-socle §5.3) : lien dont le nom inclut le libellé et la valeur quand `link` est
 * fourni, simple bloc sinon (UX-pilotage §6). Icône et teinte facultatives, purement décoratives.
 */
@Component({
  selector: 'app-stat-card',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, NgTemplateOutlet, IconComponent],
  template: `
    @if (link(); as target) {
      <a [routerLink]="target" [queryParams]="queryParams()" [attr.data-testid]="testId()"
         class="card group block h-full p-5 transition hover:-translate-y-0.5 hover:border-primary/30 hover:shadow-lift motion-reduce:transition-none motion-reduce:hover:translate-y-0"
         [attr.aria-label]="label() + ' : ' + value()">
        <ng-container *ngTemplateOutlet="body" />
      </a>
    } @else {
      <div class="card h-full p-5" [attr.data-testid]="testId()">
        <ng-container *ngTemplateOutlet="body" />
      </div>
    }
    <ng-template #body>
      <div class="flex items-start justify-between gap-3">
        <p class="text-sm font-medium text-muted">{{ label() }}</p>
        @if (icon(); as name) {
          <span class="flex h-10 w-10 items-center justify-center rounded-xl" [class]="toneClass()">
            <app-icon [name]="name" [size]="20" />
          </span>
        }
      </div>
      <p class="mt-2 flex items-baseline justify-between text-3xl font-bold tabular-nums text-ink">
        <span data-testid="stat-value">{{ value() }}</span>
        @if (link()) {
          <span class="text-primary opacity-60 transition group-hover:translate-x-0.5 group-hover:opacity-100 motion-reduce:transition-none">
            <app-icon name="chevron-right" [size]="18" />
          </span>
        }
      </p>
      @if (hint()) { <p class="mt-1 text-xs text-muted" [attr.data-testid]="hintTestId()">{{ hint() }}</p> }
    </ng-template>
  `,
})
export class StatCardComponent {
  readonly label = input.required<string>();
  readonly value = input.required<string | number>();
  readonly hint = input<string>();
  readonly hintTestId = input<string>();
  readonly link = input<string>();
  readonly queryParams = input<Record<string, string>>();
  readonly testId = input<string>();
  readonly icon = input<IconName>();
  readonly tone = input<StatTone>('primary');

  protected readonly toneClass = computed(() => TONES[this.tone()]);
}
