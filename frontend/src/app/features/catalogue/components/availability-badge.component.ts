import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/** Disponibilité en texte (RG-11, WCAG 1.4.1) : « N disponible(s) sur M » ou « Aucun exemplaire disponible ». */
@Component({
  selector: 'app-availability-badge',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (available() > 0) {
      <span class="inline-flex items-center gap-1.5 rounded-lg bg-success-soft px-2.5 py-0.5 text-xs font-semibold text-success"
            [attr.data-testid]="testId()">
        <span aria-hidden="true" class="h-1.5 w-1.5 rounded-full bg-current"></span>{{ available() }} disponible{{ available() > 1 ? 's' : '' }} sur {{ total() }}
      </span>
    } @else {
      <span class="inline-flex items-center gap-1.5 rounded-lg bg-slate-100 px-2.5 py-0.5 text-xs font-semibold text-slate-600"
            [attr.data-testid]="testId()">
        <span aria-hidden="true" class="h-1.5 w-1.5 rounded-full border border-current"></span>Aucun exemplaire disponible
      </span>
    }
  `,
})
export class AvailabilityBadgeComponent {
  readonly available = input.required<number>();
  readonly total = input.required<number>();
  readonly testId = input<string>();
}
