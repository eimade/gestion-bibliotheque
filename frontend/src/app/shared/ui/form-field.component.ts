import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/**
 * Libellé + champ projeté + aide + message d'erreur (UX-socle §5.3). Le champ projeté porte
 * lui-même `aria-invalid` et `aria-describedby="<fieldId>-error"` (voir {@link describedBy}).
 */
@Component({
  selector: 'app-form-field',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="space-y-1">
      <label [for]="fieldId()" class="block text-sm font-medium text-ink">
        {{ label() }}
        @if (required()) {
          <span class="text-danger" aria-hidden="true">*</span><span class="sr-only">(obligatoire)</span>
        }
      </label>
      <ng-content />
      @if (hint()) {
        <p [id]="fieldId() + '-hint'" class="text-sm text-muted">{{ hint() }}</p>
      }
      @if (error()) {
        <p [id]="fieldId() + '-error'" class="text-sm text-danger" [attr.data-testid]="errorTestId()">
          <span aria-hidden="true">×</span> {{ error() }}
        </p>
      }
    </div>
  `,
})
export class FormFieldComponent {
  readonly label = input.required<string>();
  readonly fieldId = input.required<string>();
  readonly required = input(false);
  readonly hint = input<string>();
  readonly error = input<string | null>(null);
  readonly errorTestId = input<string>();
}

/** Valeur de `aria-describedby` d'un champ selon la présence d'une aide et d'une erreur. */
export function describedBy(fieldId: string, hasError: boolean, hasHint = false): string | null {
  const ids = [hasHint ? `${fieldId}-hint` : null, hasError ? `${fieldId}-error` : null].filter(Boolean);
  return ids.length ? ids.join(' ') : null;
}
