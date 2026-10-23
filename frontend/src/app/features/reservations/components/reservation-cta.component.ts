import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { AuthService } from '../../../core/auth/auth.service';
import { ApiError } from '../../../core/http/api-error';
import { GENERIC_MESSAGES } from '../../../core/http/messages';
import { ToastService } from '../../../core/notifications/toast.service';
import { dataOf, viewState } from '../../../shared/data/view-state';
import { AlertComponent } from '../../../shared/ui/alert.component';
import { ButtonComponent } from '../../../shared/ui/button.component';
import { DialogComponent } from '../../../shared/ui/dialog.component';
import { RESERVATION_MESSAGES, refusalLabel, reservationErrorMessage } from '../data-access/reservation-messages';
import { ReservationService } from '../data-access/reservation.service';

/**
 * Appel à réserver de la fiche publique (UX-reservations, ECR-09) et dialogue ECR-30 : selon la disponibilité,
 * le profil et la réservation en cours ; l'adhérent ne voit que sa propre position (AC-034-3).
 */
@Component({
  selector: 'app-reservation-cta',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, AlertComponent, ButtonComponent, DialogComponent],
  template: `
    @if (available() > 0) {
      <p class="text-sm" data-testid="book-reserve-available-info">{{ messages.availableInfo }}</p>
    } @else if (!user()) {
      <a routerLink="/connexion" [queryParams]="{ retour: '/catalogue/' + bookId() }" class="text-sm text-primary underline"
         data-testid="book-reserve-login-link">{{ messages.loginToReserve }}</a>
    } @else if (user()!.role === 'MEMBER') {
      @if (situation(); as info) {
        @if (info.current; as current) {
          <p class="text-sm font-medium" data-testid="book-reservation-position">
            {{ current.status === 'READY' && current.pickupDeadline ? messages.ready(current.pickupDeadline)
                : messages.position(current.position ?? 1, current.queueLength) }}
          </p>
        } @else {
          <button app-button type="button" data-testid="book-reserve-button" (click)="open.set(true)">Réserver ce livre</button>
        }
      }
    }

    <app-dialog title="Réserver ce livre" [open]="open()" testId="reserve-dialog" (closed)="open.set(false)">
      @if (situation(); as info) {
        <p class="font-medium">{{ title() }}</p>
        @if (info.preview.reservable) {
          <p>Tous les exemplaires sont empruntés.</p>
          <p data-testid="reserve-position-preview">
            {{ info.preview.position - 1 }} réservation(s) avant vous : vous serez en position {{ info.preview.position }}.
          </p>
          <p class="text-sm">Quand un exemplaire reviendra, il sera mis de côté pour vous pendant {{ info.preview.pickupDays }} jours.
            Suivez son statut dans « Mon espace ».</p>
          <p class="text-sm" data-testid="reserve-quota">Réservations actives : {{ info.preview.activeReservations }} sur {{ info.preview.limit }}</p>
        } @else {
          <ul class="list-disc space-y-1 pl-6 text-sm">
            @for (reason of info.preview.reasons; track reason) {
              <li data-testid="reserve-blocked-reason" [attr.data-reason]="reason">
                {{ label(reason, info.preview.limit, info.preview.memberReasons) }}
              </li>
            }
          </ul>
        }
        @if (error()) {
          <app-alert tone="danger" data-testid="reserve-server-error">{{ error() }}</app-alert>
        }
        <div class="flex justify-end gap-2">
          <button app-button type="button" variant="secondary" data-testid="reserve-dialog-cancel" (click)="open.set(false)">
            {{ info.preview.reservable ? 'Annuler' : 'Fermer' }}
          </button>
          @if (info.preview.reservable) {
            <button app-button type="button" [loading]="busy()" data-testid="reserve-dialog-confirm" (click)="reserve()">Réserver</button>
          }
        </div>
      } @else {
        <p role="status" class="text-sm text-muted" data-testid="reserve-dialog-loading">Chargement…</p>
      }
    </app-dialog>
  `,
})
export class ReservationCtaComponent {
  readonly bookId = input.required<number>();
  readonly title = input.required<string>();
  readonly available = input.required<number>();

  private readonly auth = inject(AuthService);
  private readonly reservations = inject(ReservationService);
  private readonly toasts = inject(ToastService);
  protected readonly messages = RESERVATION_MESSAGES;
  protected readonly user = this.auth.user;
  protected readonly open = signal(false);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);

  private readonly tick = signal(0);
  private readonly request = computed(() => {
    this.tick();
    return this.user()?.role === 'MEMBER' && this.available() === 0 ? { id: this.bookId() } : null;
  });
  private readonly state = viewState(this.request, ({ id }) => this.reservations.myBookReservation(id));
  protected readonly situation = computed(() => dataOf(this.state()));

  protected label(reason: Parameters<typeof refusalLabel>[0], limit: number,
                  memberReasons: Parameters<typeof refusalLabel>[3]): string {
    return refusalLabel(reason, limit, false, memberReasons, 5);
  }

  protected reserve(): void {
    this.busy.set(true);
    this.error.set(null);
    this.reservations.reserveForMe(this.bookId()).subscribe({
      next: (created) => {
        this.busy.set(false);
        this.open.set(false);
        this.toasts.success(RESERVATION_MESSAGES.created(created.position));
        this.tick.update((value) => value + 1);
      },
      error: (error: unknown) => {
        this.busy.set(false);
        this.error.set(error instanceof ApiError ? reservationErrorMessage(error) : GENERIC_MESSAGES.unexpected);
      },
    });
  }
}
