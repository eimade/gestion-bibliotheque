import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';

import { ButtonComponent } from './button.component';
import { DialogComponent } from './dialog.component';

/**
 * Confirmation d'une action destructrice ou irréversible (UX-socle §5.3) ; le message rappelle
 * l'objet concerné. `data-testid` : `<testId>-dialog`, `<testId>-confirm`, `<testId>-cancel`.
 */
@Component({
  selector: 'app-confirm-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DialogComponent, ButtonComponent],
  template: `
    <app-dialog [title]="title()" [open]="open()" [testId]="testId() + '-dialog'" (closed)="cancelled.emit()">
      <p class="text-sm">{{ message() }}</p>
      <div class="flex justify-end gap-2">
        <button app-button type="button" variant="secondary" [disabled]="busy()"
                [attr.data-testid]="testId() + '-cancel'" (click)="cancelled.emit()">
          Annuler
        </button>
        <button app-button type="button" [variant]="tone()" [loading]="busy()"
                [attr.data-testid]="testId() + '-confirm'" (click)="confirmed.emit()">
          {{ confirmLabel() }}
        </button>
      </div>
    </app-dialog>
  `,
})
export class ConfirmDialogComponent {
  readonly open = input(false);
  readonly title = input.required<string>();
  readonly message = input.required<string>();
  readonly confirmLabel = input('Confirmer');
  readonly tone = input<'primary' | 'danger'>('danger');
  readonly busy = input(false);
  readonly testId = input.required<string>();

  readonly confirmed = output<void>();
  readonly cancelled = output<void>();
}
