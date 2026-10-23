import { ChangeDetectionStrategy, Component, inject, input, output, signal } from '@angular/core';

import { ApiError } from '../../../core/http/api-error';
import { GENERIC_MESSAGES } from '../../../core/http/messages';
import { ToastService } from '../../../core/notifications/toast.service';
import { AlertComponent } from '../../../shared/ui/alert.component';
import { ButtonComponent } from '../../../shared/ui/button.component';
import { DialogComponent } from '../../../shared/ui/dialog.component';
import { RESERVATION_MESSAGES, releaseLabel, reservationErrorMessage } from '../data-access/reservation-messages';
import { ReservationCancelled } from '../data-access/reservation.models';
import { ReservationService } from '../data-access/reservation.service';

/**
 * Annuler une réservation (US-033) : confirmation, avertissement pour une réservation prête ; côté personnel,
 * la destination de l'exemplaire libéré est annoncée en avertissement persistant.
 */
@Component({
  selector: 'app-cancel-reservation-action',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ButtonComponent, DialogComponent, AlertComponent],
  template: `
    <button app-button type="button" variant="secondary" data-testid="cancel-reservation-button" (click)="open.set(true)">
      Annuler
    </button>
    <app-dialog title="Annuler la réservation" [open]="open()" testId="cancel-reservation-dialog" (closed)="close()">
      <p>Annuler la réservation de « {{ title() }} » ?</p>
      @if (ready()) {
        <app-alert tone="warning">L’exemplaire mis de côté sera proposé à l’adhérent suivant.</app-alert>
      }
      @if (error()) {
        <app-alert tone="danger" data-testid="cancel-reservation-server-error">{{ error() }}</app-alert>
      }
      <div class="flex justify-end gap-2">
        <button app-button type="button" variant="secondary" data-testid="cancel-reservation-cancel" (click)="close()">Retour</button>
        <button app-button type="button" variant="danger" [loading]="busy()" data-testid="cancel-reservation-confirm"
                (click)="confirm()">Annuler la réservation</button>
      </div>
    </app-dialog>
  `,
})
export class CancelReservationActionComponent {
  readonly reservationId = input.required<number>();
  readonly title = input.required<string>();
  readonly ready = input(false);
  readonly asMember = input(false);
  readonly cancelled = output<ReservationCancelled>();

  private readonly reservations = inject(ReservationService);
  private readonly toasts = inject(ToastService);
  protected readonly open = signal(false);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);

  protected close(): void {
    this.open.set(false);
    this.error.set(null);
  }

  protected confirm(): void {
    this.busy.set(true);
    this.reservations.cancel(this.reservationId(), this.asMember()).subscribe({
      next: (result) => {
        this.busy.set(false);
        this.open.set(false);
        this.toasts.success(RESERVATION_MESSAGES.cancelled);
        if (!this.asMember() && result.release) {
          this.toasts.warning(releaseLabel(result.release));
        }
        this.cancelled.emit(result);
      },
      error: (error: unknown) => {
        this.busy.set(false);
        this.error.set(error instanceof ApiError ? reservationErrorMessage(error) : GENERIC_MESSAGES.unexpected);
      },
    });
  }
}
