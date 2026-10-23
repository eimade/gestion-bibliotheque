import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

export type AlertTone = 'info' | 'warning' | 'danger' | 'success';

const TONES: Record<AlertTone, { box: string; icon: string }> = {
  info: { box: 'border-info/20 bg-info-soft text-info', icon: 'i' },
  warning: { box: 'border-warning/20 bg-warning-soft text-warning', icon: '!' },
  danger: { box: 'border-danger/20 bg-danger-soft text-danger', icon: '×' },
  success: { box: 'border-success/20 bg-success-soft text-success', icon: '✓' },
};

/** Bandeau persistant (UX-socle §5.3) ; une alerte `danger` est annoncée immédiatement (`role=alert`). */
@Component({
  selector: 'app-alert',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { '[attr.role]': "tone() === 'danger' ? 'alert' : 'status'" },
  template: `
    <div class="flex gap-3 rounded-xl border p-3.5 text-sm" [class]="palette().box">
      <span class="flex h-5 w-5 shrink-0 items-center justify-center rounded-full bg-white/70 text-xs font-bold" aria-hidden="true">{{ palette().icon }}</span>
      <div>
        @if (title()) {
          <p class="font-semibold">{{ title() }}</p>
        }
        <ng-content />
      </div>
    </div>
  `,
})
export class AlertComponent {
  readonly tone = input<AlertTone>('info');
  readonly title = input<string>();

  protected readonly palette = computed(() => TONES[this.tone()]);
}
