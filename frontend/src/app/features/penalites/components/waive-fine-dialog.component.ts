import { ChangeDetectionStrategy, Component, effect, inject, input, output, signal } from '@angular/core';

import { ApiError } from '../../../core/http/api-error';
import { GENERIC_MESSAGES } from '../../../core/http/messages';
import { ToastService } from '../../../core/notifications/toast.service';
import { FINE_REASON_LABELS } from '../../../shared/models/statuses';
import { EuroPipe } from '../../../shared/pipes/euro.pipe';
import { AlertComponent } from '../../../shared/ui/alert.component';
import { ButtonComponent } from '../../../shared/ui/button.component';
import { DialogComponent } from '../../../shared/ui/dialog.component';
import { FINE_MESSAGES, fineErrorMessage } from '../data-access/fine-messages';
import { FineView, WaiverResult } from '../data-access/fine.models';
import { FineService } from '../data-access/fine.service';

/** ECR-36 — Remise gracieuse (US-039, ADMIN seulement : le bouton n'est pas proposé au bibliothécaire). */
@Component({
  selector: 'app-waive-fine-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DialogComponent, AlertComponent, ButtonComponent, EuroPipe],
  template: `
    <app-dialog title="Accorder une remise" [open]="!!fine()" testId="waive-dialog" (closed)="cancelled.emit()">
      @if (fine(); as current) {
        <p data-testid="waive-fine-summary">{{ reasonLabels[current.reason] }}{{ current.title ? ' · ' + current.title : '' }}
          · {{ current.amount | euro }}</p>
        <p class="text-sm">{{ memberLabel() }}</p>
        <div class="space-y-1">
          <label for="waive-reason" class="block text-sm font-medium">Motif <span class="text-danger" aria-hidden="true">*</span></label>
          <textarea id="waive-reason" rows="3" class="field-input" data-testid="waive-reason-input" [value]="reason()"
                    [attr.aria-invalid]="reasonError() ? true : null" (input)="onReason($event)"></textarea>
          @if (reasonError()) {
            <p class="text-sm text-danger" data-testid="waive-reason-error">{{ messages.waiveReason }}</p>
          }
        </div>
        <app-alert tone="warning">La remise est définitive.</app-alert>
        @if (serverError()) {
          <app-alert tone="danger" data-testid="waive-server-error">{{ serverError() }}</app-alert>
        }
        <div class="flex justify-end gap-2">
          <button app-button type="button" variant="secondary" data-testid="waive-cancel" (click)="cancelled.emit()">Annuler</button>
          <button app-button type="button" [loading]="busy()" data-testid="waive-confirm" (click)="confirm(current.id)">
            Accorder la remise
          </button>
        </div>
      }
    </app-dialog>
  `,
})
export class WaiveFineDialogComponent {
  readonly fine = input<FineView | null>(null);
  readonly memberLabel = input('');
  readonly waived = output<WaiverResult>();
  readonly cancelled = output<void>();

  private readonly service = inject(FineService);
  private readonly toasts = inject(ToastService);
  protected readonly messages = FINE_MESSAGES;
  protected readonly reasonLabels = FINE_REASON_LABELS;
  protected readonly reason = signal('');
  protected readonly reasonError = signal(false);
  protected readonly serverError = signal<string | null>(null);
  protected readonly busy = signal(false);

  constructor() {
    effect(() => {
      if (this.fine()) {
        this.reason.set('');
        this.reasonError.set(false);
        this.serverError.set(null);
      }
    });
  }

  protected onReason(event: Event): void {
    this.reason.set((event.target as HTMLTextAreaElement).value);
    this.reasonError.set(false);
  }

  protected confirm(fineId: number): void {
    const reason = this.reason().trim();
    if (reason.length < 3 || reason.length > 255) {
      this.reasonError.set(true);
      return;
    }
    this.busy.set(true);
    this.service.waive(fineId, reason).subscribe({
      next: (result) => {
        this.busy.set(false);
        this.toasts.success(FINE_MESSAGES.waived);
        this.waived.emit(result);
      },
      error: (error: unknown) => {
        this.busy.set(false);
        this.serverError.set(error instanceof ApiError && error.code === 'FINE_ALREADY_SETTLED'
          ? FINE_MESSAGES.waiveSettled
          : error instanceof ApiError ? fineErrorMessage(error) : GENERIC_MESSAGES.unexpected);
      },
    });
  }
}
