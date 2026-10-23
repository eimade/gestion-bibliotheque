import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';

import { Toast, ToastService } from '../../core/notifications/toast.service';

/**
 * Messages globaux (UX-socle §5.3) : succès dans une région `polite`, avertissements et erreurs
 * dans une région `assertive` ; ces derniers se ferment à la main.
 */
@Component({
  selector: 'app-toast-outlet',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="pointer-events-none fixed inset-x-4 bottom-4 z-50 flex flex-col items-end gap-2 sm:left-auto sm:w-96">
      <div aria-live="polite" class="flex w-full flex-col gap-2">
        @for (toast of polite(); track toast.id) {
          <div class="pointer-events-auto rounded-md border-l-4 border-success bg-success-soft p-3 text-sm text-success shadow"
               data-testid="toast-success">
            <span aria-hidden="true">✓ </span>{{ toast.message }}
          </div>
        }
      </div>
      <div aria-live="assertive" class="flex w-full flex-col gap-2">
        @for (toast of assertive(); track toast.id) {
          <div class="pointer-events-auto flex items-start justify-between gap-2 rounded-md border-l-4 p-3 text-sm shadow"
               [class]="toast.kind === 'error' ? 'border-danger bg-danger-soft text-danger' : 'border-warning bg-warning-soft text-warning'"
               [attr.data-testid]="toast.kind === 'error' ? 'toast-error' : 'toast-warning'">
            <span><span aria-hidden="true">{{ toast.kind === 'error' ? '× ' : '! ' }}</span>{{ toast.message }}</span>
            <button type="button" class="font-bold" (click)="toasts.dismiss(toast.id)">
              <span aria-hidden="true">×</span><span class="sr-only">Fermer le message</span>
            </button>
          </div>
        }
      </div>
    </div>
  `,
})
export class ToastOutletComponent {
  protected readonly toasts = inject(ToastService);
  protected readonly polite = computed(() => this.toasts.toasts().filter((toast: Toast) => toast.kind === 'success'));
  protected readonly assertive = computed(() => this.toasts.toasts().filter((toast: Toast) => toast.kind !== 'success'));
}
