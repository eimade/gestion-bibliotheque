import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import { StatusDisplay, Tone } from '../models/statuses';

const TONES: Record<Tone, string> = {
  success: 'bg-success-soft text-success',
  warning: 'bg-warning-soft text-warning',
  danger: 'bg-danger-soft text-danger',
  info: 'bg-info-soft text-info',
  muted: 'bg-surface-alt text-muted',
};

/** Badge de statut : toujours un libellé texte, jamais la couleur seule (WCAG 1.4.1). */
@Component({
  selector: 'app-status-badge',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <span class="inline-flex items-center gap-1 rounded-full px-2.5 py-0.5 text-xs font-semibold"
          [class]="toneClass()" data-testid="status-badge" [attr.data-status]="value()">
      <span aria-hidden="true" class="h-1.5 w-1.5 rounded-full bg-current"></span>{{ display().label }}
    </span>
  `,
})
export class StatusBadgeComponent {
  readonly display = input.required<StatusDisplay>();
  readonly value = input.required<string>();

  protected readonly toneClass = computed(() => TONES[this.display().tone]);
}
