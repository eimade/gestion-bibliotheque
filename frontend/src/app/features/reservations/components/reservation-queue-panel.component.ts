import { ChangeDetectionStrategy, Component, computed, inject, input, output, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { ApiError } from '../../../core/http/api-error';
import { GENERIC_MESSAGES } from '../../../core/http/messages';
import { ToastService } from '../../../core/notifications/toast.service';
import { dataOf, viewState } from '../../../shared/data/view-state';
import { MEMBER_STATUS_DISPLAY, RESERVATION_STATUS_DISPLAY } from '../../../shared/models/statuses';
import { DateFrPipe, DateTimeFrPipe } from '../../../shared/pipes/date-fr.pipe';
import { AlertComponent } from '../../../shared/ui/alert.component';
import { ButtonComponent } from '../../../shared/ui/button.component';
import { DialogComponent } from '../../../shared/ui/dialog.component';
import { ScanInputComponent } from '../../../shared/ui/scan-input.component';
import { StatusBadgeComponent } from '../../../shared/ui/status-badge.component';
import { EmptyStateComponent, ErrorStateComponent, LoadingStateComponent } from '../../../shared/ui/view-states.component';
import { MEMBER_NUMBER_PATTERN, memberName } from '../../adherents/data-access/member.models';
import { LOAN_MESSAGES, memberLabel } from '../../emprunts/data-access/loan-messages';
import { RESERVATION_MESSAGES, refusalLabel, reservationErrorMessage } from '../data-access/reservation-messages';
import { ReservationPreview } from '../data-access/reservation.models';
import { ReservationService } from '../data-access/reservation.service';
import { CancelReservationActionComponent } from './cancel-reservation-action.component';

/**
 * ECR-32 — File d'attente d'une notice (US-034) et ECR-31 — réserver pour un adhérent (AC-032-8), intégrés à la
 * fiche notice de gestion (ECR-12).
 */
@Component({
  selector: 'app-reservation-queue-panel',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'block' },
  imports: [RouterLink, DateFrPipe, DateTimeFrPipe, AlertComponent, ButtonComponent, DialogComponent, ScanInputComponent,
    StatusBadgeComponent, LoadingStateComponent, EmptyStateComponent, ErrorStateComponent, CancelReservationActionComponent],
  template: `
    <section class="card p-6" data-testid="queue-panel">
      <div class="mb-4 flex flex-wrap items-center justify-between gap-3">
        <h2 class="text-lg font-bold">File d’attente ({{ entries().length }})</h2>
        <button app-button type="button" variant="secondary" data-testid="queue-reserve-for-member" (click)="openReserve()">
          Réserver pour un adhérent
        </button>
      </div>
      @switch (state().kind) {
        @case ('loading') { <app-loading-state testId="queue-loading" [rows]="3" /> }
        @case ('error') { <app-error-state [message]="genericError" testId="queue-error" (retry)="reload()" /> }
        @case ('success') {
          @if (entries().length === 0) {
            <app-empty-state [message]="messages.queueEmpty" testId="queue-empty" />
          } @else {
            <table class="w-full text-left text-sm">
              <caption class="sr-only">File d’attente de {{ title() }}</caption>
              <thead class="bg-surface-alt text-xs uppercase tracking-wide text-muted"><tr>
                <th scope="col" class="px-4 py-3 font-semibold">#</th><th scope="col" class="px-4 py-3 font-semibold">Adhérent</th>
                <th scope="col" class="px-4 py-3 font-semibold">Réservé le</th><th scope="col" class="px-4 py-3 font-semibold">Statut</th>
                <th scope="col" class="px-4 py-3 font-semibold"><span class="sr-only">Actions</span></th>
              </tr></thead>
              <tbody>
                @for (entry of entries(); track entry.id) {
                  <tr class="border-t border-border" data-testid="queue-row" [attr.data-row-id]="entry.member?.memberNumber"
                      [attr.data-status]="entry.status">
                    <td class="px-4 py-3" data-testid="queue-position">{{ entry.position ?? '—' }}</td>
                    <td class="px-4 py-3">
                      @if (entry.member) {
                        <a [routerLink]="['/gestion/adherents', entry.member.memberNumber]" class="text-primary hover:underline">{{ label(entry.member) }}</a>
                      }
                    </td>
                    <td class="px-4 py-3 tabular-nums">{{ entry.reservedAt | dateTimeFr }}</td>
                    <td class="px-4 py-3">
                      <app-status-badge [display]="statusDisplay[entry.status]" [value]="entry.status" />
                      @if (entry.status === 'READY' && entry.pickupDeadline) {
                        <span class="ml-1 text-xs" data-testid="queue-ready-deadline">jusqu’au {{ entry.pickupDeadline | dateFr }} · ex. {{ entry.copyNumber }}</span>
                      }
                    </td>
                    <td class="px-4 py-3 text-right" data-testid="queue-cancel-action">
                      <app-cancel-reservation-action [reservationId]="entry.id" [title]="title()" [ready]="entry.status === 'READY'"
                                                     (cancelled)="changed.emit(); reload()" />
                    </td>
                  </tr>
                }
              </tbody>
            </table>
          }
        }
      }
    </section>

    <app-dialog [title]="'Réserver « ' + title() + ' » pour un adhérent'" [open]="reserveOpen()" testId="reserve-for-member-dialog"
                (closed)="reserveOpen.set(false)">
      <app-scan-input label="Carte adhérent" testId="reserve-for-member" placeholder="A000123" [pattern]="memberPattern"
                      [invalidMessage]="loanMessages.memberInvalid" (scanned)="checkMember($event)" />
      @if (lookupError()) { <p class="text-sm text-danger" role="alert" data-testid="reserve-for-member-error">{{ lookupError() }}</p> }
      @if (preview(); as info) {
        <div class="space-y-2" data-testid="reserve-for-member-card">
          @if (info.member; as member) {
            <p>{{ name(member) }} · <span class="font-mono">{{ member.memberNumber }}</span> ({{ memberStatus[member.status].label }})</p>
          }
          @if (info.reservable) {
            <p class="text-success"><span aria-hidden="true">✓ </span>Réservation possible · position prévue : {{ info.position }}</p>
          } @else {
            <ul class="list-disc pl-6 text-sm">
              @for (reason of info.reasons; track reason) {
                <li data-testid="reserve-for-member-reason">{{ refusal(reason, info) }}</li>
              }
            </ul>
          }
          <p class="text-sm">Réservations actives : {{ info.activeReservations }} sur {{ info.limit }}</p>
        </div>
      }
      @if (reserveError()) { <app-alert tone="danger">{{ reserveError() }}</app-alert> }
      <div class="flex justify-end gap-2">
        <button app-button type="button" variant="secondary" data-testid="reserve-for-member-cancel" (click)="reserveOpen.set(false)">Annuler</button>
        <button app-button type="button" [loading]="busy()" [disabled]="!preview()?.reservable"
                data-testid="reserve-for-member-confirm" (click)="reserve()">Réserver</button>
      </div>
    </app-dialog>
  `,
})
export class ReservationQueuePanelComponent {
  readonly bookId = input.required<number>();
  readonly title = input.required<string>();
  readonly changed = output<void>();

  private readonly reservations = inject(ReservationService);
  private readonly toasts = inject(ToastService);
  protected readonly messages = RESERVATION_MESSAGES;
  protected readonly loanMessages = LOAN_MESSAGES;
  protected readonly statusDisplay = RESERVATION_STATUS_DISPLAY;
  protected readonly memberStatus = MEMBER_STATUS_DISPLAY;
  protected readonly memberPattern = MEMBER_NUMBER_PATTERN;
  protected readonly label = memberLabel;
  protected readonly name = memberName;
  protected readonly genericError = GENERIC_MESSAGES.unexpected;

  private readonly tick = signal(0);
  private readonly request = computed(() => {
    this.tick();
    return { id: this.bookId() };
  });
  protected readonly state = viewState(this.request, ({ id }) => this.reservations.queue(id));
  protected readonly entries = computed(() => dataOf(this.state()) ?? []);

  protected readonly reserveOpen = signal(false);
  protected readonly preview = signal<ReservationPreview | null>(null);
  protected readonly lookupError = signal<string | null>(null);
  protected readonly reserveError = signal<string | null>(null);
  protected readonly busy = signal(false);

  protected openReserve(): void {
    this.preview.set(null);
    this.lookupError.set(null);
    this.reserveError.set(null);
    this.reserveOpen.set(true);
  }

  protected checkMember(memberNumber: string): void {
    this.lookupError.set(null);
    this.reservations.previewForMember(this.bookId(), memberNumber).subscribe({
      next: (preview) => this.preview.set(preview),
      error: (error: unknown) => {
        this.preview.set(null);
        this.lookupError.set(error instanceof ApiError ? reservationErrorMessage(error, true) : GENERIC_MESSAGES.unexpected);
      },
    });
  }

  protected refusal(reason: ReservationPreview['reasons'][number], info: ReservationPreview): string {
    return refusalLabel(reason, info.limit, true, info.memberReasons, 5);
  }

  protected reserve(): void {
    const member = this.preview()?.member;
    if (!member) {
      return;
    }
    this.busy.set(true);
    this.reserveError.set(null);
    this.reservations.reserveForMember(this.bookId(), member.memberNumber).subscribe({
      next: (created) => {
        this.busy.set(false);
        this.reserveOpen.set(false);
        this.toasts.success(RESERVATION_MESSAGES.createdFor(memberLabel(member), created.position));
        this.changed.emit();
        this.reload();
      },
      error: (error: unknown) => {
        this.busy.set(false);
        this.reserveError.set(error instanceof ApiError ? reservationErrorMessage(error, true) : GENERIC_MESSAGES.unexpected);
      },
    });
  }

  protected reload(): void {
    this.tick.update((value) => value + 1);
  }
}
