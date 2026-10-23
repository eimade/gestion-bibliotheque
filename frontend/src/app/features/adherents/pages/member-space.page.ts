import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { FINE_REASON_LABELS, LOAN_STATUS_DISPLAY, RESERVATION_STATUS_DISPLAY } from '../../../shared/models/statuses';
import { dataOf, viewState } from '../../../shared/data/view-state';
import { DateFrPipe, daysBetween, todayInParis } from '../../../shared/pipes/date-fr.pipe';
import { EuroPipe } from '../../../shared/pipes/euro.pipe';
import { AlertComponent } from '../../../shared/ui/alert.component';
import { BookCoverComponent } from '../../../shared/ui/book-cover.component';
import { IconComponent } from '../../../shared/ui/icon.component';
import { StatusBadgeComponent } from '../../../shared/ui/status-badge.component';
import { ErrorStateComponent, LoadingStateComponent } from '../../../shared/ui/view-states.component';
import { RenewLoanActionComponent } from '../../emprunts/components/renew-loan-action.component';
import { MemberLoan } from '../../emprunts/data-access/loan.models';
import { LoanService } from '../../emprunts/data-access/loan.service';
import { FineView } from '../../penalites/data-access/fine.models';
import { FineService } from '../../penalites/data-access/fine.service';
import { CancelReservationActionComponent } from '../../reservations/components/cancel-reservation-action.component';
import { ReservationService } from '../../reservations/data-access/reservation.service';
import { MEMBER_MESSAGES } from '../data-access/member-messages';
import { MemberSpaceService } from '../data-access/member.service';

/**
 * ECR-22 — Mon espace (US-023) : uniquement les données de l'adhérent connecté (AC-023-4), en cartes sur une
 * colonne en mobile ; prolongation (US-027) et annulation de réservation (US-033).
 */
@Component({
  selector: 'app-member-space-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, DateFrPipe, EuroPipe, AlertComponent, BookCoverComponent, IconComponent, StatusBadgeComponent, LoadingStateComponent, ErrorStateComponent,
    RenewLoanActionComponent, CancelReservationActionComponent],
  template: `
    @switch (meState().kind) {
      @case ('loading') { <app-loading-state testId="my-space-loading" [rows]="6" /> }
      @case ('error') { <app-error-state [message]="genericError" testId="my-space-error" (retry)="refresh()" /> }
      @case ('success') {
        @if (me(); as member) {
          <section class="hero-gradient relative overflow-hidden rounded-3xl p-6 sm:p-8">
            <div class="pointer-events-none absolute -right-12 -top-12 h-48 w-48 rounded-full bg-white/30 blur-xl" aria-hidden="true"></div>
            <div class="relative flex flex-wrap items-center justify-between gap-6">
              <div>
                <p class="text-xs font-semibold uppercase tracking-widest text-primary">Mon espace lecteur</p>
                <h1 class="mt-2 text-3xl font-extrabold text-indigo-950" data-testid="my-space-greeting">Bonjour {{ member.firstName }}</h1>
                <p class="mt-2 inline-flex items-center gap-2 rounded-full bg-white/70 px-3 py-1 text-sm font-medium text-indigo-900" data-testid="my-space-expiry">
                  <app-icon name="calendar" [size]="15" /> Adhésion valable jusqu’au {{ member.expiryDate | dateFr }}
                </p>
              </div>
              <dl class="grid grid-cols-3 gap-3 text-center">
                <div class="rounded-2xl bg-white/80 px-4 py-3 shadow-sm">
                  <dt class="text-xs font-medium text-muted">Emprunts</dt>
                  <dd class="text-2xl font-bold text-primary tabular-nums">{{ loans().length }}</dd>
                </div>
                <div class="rounded-2xl bg-white/80 px-4 py-3 shadow-sm">
                  <dt class="text-xs font-medium text-muted">Réservations</dt>
                  <dd class="text-2xl font-bold text-brand tabular-nums">{{ reservations().length }}</dd>
                </div>
                <div class="rounded-2xl bg-white/80 px-4 py-3 shadow-sm">
                  <dt class="text-xs font-medium text-muted">À régler</dt>
                  <dd class="text-2xl font-bold text-accent tabular-nums">{{ finesTotal() | euro }}</dd>
                </div>
              </dl>
            </div>
          </section>
          @if (member.status === 'EXPIRED') {
            <app-alert tone="warning" data-testid="my-space-expired-alert" class="mt-4 block">{{ messages.expiredInvite }}</app-alert>
          }
          @if (allEmpty()) {
            <div class="card mt-6 flex flex-col items-center gap-3 p-8 text-center" data-testid="my-space-empty">
              <span class="flex h-12 w-12 items-center justify-center rounded-2xl bg-primary-soft text-primary"><app-icon name="books" [size]="24" /></span>
              <p>{{ messages.nothingInProgress }}</p>
              <a routerLink="/catalogue" class="inline-flex rounded-xl bg-primary px-4 py-2.5 text-sm font-semibold text-white hover:bg-primary-hover">Rechercher un livre</a>
            </div>
          }
          <div class="mt-8 grid gap-8 lg:grid-cols-[1.25fr_1fr]">
            <section class="space-y-3" data-testid="my-loans-section" aria-labelledby="my-loans-title">
              <h2 id="my-loans-title" class="flex items-center gap-2 text-lg font-bold">
                <span class="flex h-8 w-8 items-center justify-center rounded-lg bg-primary-soft text-primary"><app-icon name="book" [size]="17" /></span>
                Mes emprunts ({{ loans().length }})
              </h2>
              @if (loansState().kind === 'loading') { <app-loading-state [rows]="2" /> }
              @if (loansState().kind === 'error') { <app-error-state [message]="genericError" testId="my-loans-error" (retry)="refresh()" /> }
              @if (loansState().kind === 'success' && loans().length === 0) { <p class="text-sm text-muted">{{ messages.noLoan }}</p> }
              @for (loan of loans(); track loan.id) {
                <article class="card flex gap-4 p-4" data-testid="my-loan-card" [attr.data-row-id]="loan.copyNumber"
                         [class.!border-danger/40]="loan.status === 'OVERDUE'">
                  <app-book-cover [title]="loan.title" class="w-20 shrink-0 sm:w-24" />
                  <div class="min-w-0 flex-1 space-y-1">
                    <div class="flex items-start justify-between gap-2">
                      <h3 class="font-semibold leading-snug">{{ loan.title }}</h3>
                      @if (loan.status === 'OVERDUE') { <app-status-badge [display]="loanStatus.OVERDUE" value="OVERDUE" /> }
                    </div>
                    <p class="text-sm text-muted">Emprunté le {{ loan.loanDate | dateFr }}</p>
                    <p class="text-sm">
                      À rendre le <span class="font-semibold" data-testid="my-loan-due-date">{{ loan.dueDate | dateFr }}</span> ·
                      <span data-testid="my-loan-due-label" [class]="dueTone(loan)">{{ dueLabel(loan) }}</span>
                    </p>
                    @if (loan.status === 'OVERDUE') {
                      <p class="text-sm text-danger" data-testid="my-loan-provisional-fine">
                        Pénalité provisoire : {{ loan.provisionalFine | euro }}
                      </p>
                    }
                    <div class="pt-2">
                      <app-renew-loan-action [loan]="loan" [asMember]="true" (renewed)="refresh()" />
                    </div>
                  </div>
                </article>
              }
            </section>

            <div class="space-y-8">
              <section class="space-y-3" data-testid="my-reservations-section" aria-labelledby="my-reservations-title">
                <h2 id="my-reservations-title" class="flex items-center gap-2 text-lg font-bold">
                  <span class="flex h-8 w-8 items-center justify-center rounded-lg bg-brand-soft text-brand"><app-icon name="bookmark" [size]="17" /></span>
                  Mes réservations ({{ reservations().length }})
                </h2>
                @if (reservationsState().kind === 'success' && reservations().length === 0) {
                  <p class="text-sm text-muted">{{ messages.myNoReservation }}</p>
                }
                @for (reservation of reservations(); track reservation.id) {
                  <article class="card flex items-center gap-4 p-4" data-testid="my-reservation-row">
                    <app-book-cover [title]="reservation.title" class="w-14 shrink-0" />
                    <div class="min-w-0 flex-1">
                      <h3 class="font-semibold leading-snug">{{ reservation.title }}</h3>
                      <p class="mt-1 text-sm">
                        <app-status-badge [display]="reservationStatus[reservation.status]" [value]="reservation.status" />
                        @if (reservation.status === 'PENDING') {
                          <span class="ml-2 text-muted" data-testid="my-reservation-position">Position {{ reservation.position }} sur {{ reservation.queueLength }}</span>
                        } @else if (reservation.pickupDeadline) {
                          <span class="ml-2 text-muted" data-testid="my-reservation-deadline">À retirer avant le {{ reservation.pickupDeadline | dateFr }}</span>
                        }
                      </p>
                      <div class="mt-2">
                        <app-cancel-reservation-action [reservationId]="reservation.id" [title]="reservation.title"
                                                       [ready]="reservation.status === 'READY'" [asMember]="true" (cancelled)="refresh()" />
                      </div>
                    </div>
                  </article>
                }
              </section>

              <section class="space-y-3" data-testid="my-fines-section" aria-labelledby="my-fines-title">
                <h2 id="my-fines-title" class="flex flex-wrap items-center gap-2 text-lg font-bold">
                  <span class="flex h-8 w-8 items-center justify-center rounded-lg bg-accent-soft text-accent"><app-icon name="coins" [size]="17" /></span>
                  Mes pénalités · Total à régler : <span class="tabular-nums" data-testid="my-fines-total">{{ finesTotal() | euro }}</span>
                </h2>
                @if (finesState().kind === 'success' && fines().length === 0) {
                  <p class="text-sm text-muted">{{ messages.myNoFine }}</p>
                } @else {
                  <ul class="card divide-y divide-border">
                    @for (fine of fines(); track fine.id) {
                      <li class="flex justify-between gap-3 px-4 py-3 text-sm" data-testid="my-fine-row">
                        <span>{{ fineLabel(fine) }}</span><span class="font-semibold tabular-nums">{{ fine.amount | euro }}</span>
                      </li>
                    }
                  </ul>
                  @if (fines().length) { <p class="text-sm text-muted">{{ messages.payAtDesk }}</p> }
                }
              </section>
            </div>
          </div>

          <section class="card mt-8 p-5">
            <button type="button" class="flex w-full items-center justify-between gap-2 font-bold" [attr.aria-expanded]="historyOpen()" aria-controls="my-history"
                    data-testid="my-history-toggle" (click)="historyOpen.set(!historyOpen())">
              <span class="flex items-center gap-2"><app-icon name="clock" [size]="18" /> Historique (4 derniers mois)</span>
              <span aria-hidden="true">{{ historyOpen() ? '▾' : '▸' }}</span>
            </button>
            @if (historyOpen()) {
              <div id="my-history" class="mt-4">
                @if (history().length === 0) {
                  <p class="text-sm text-muted">{{ messages.noHistory }}</p>
                } @else {
                  <ul class="divide-y divide-border">
                    @for (loan of history(); track loan.id) {
                      <li class="py-2.5 text-sm" data-testid="my-history-row">
                        <span class="font-medium">{{ loan.title }}</span> · emprunté le {{ loan.loanDate | dateFr }} · rendu le {{ loan.returnDate | dateFr }}
                      </li>
                    }
                  </ul>
                }
              </div>
            }
          </section>
        }
      }
    }
  `,
})
export class MemberSpacePage {
  private readonly space = inject(MemberSpaceService);
  private readonly loanService = inject(LoanService);
  private readonly reservationService = inject(ReservationService);
  private readonly fineService = inject(FineService);
  protected readonly messages = MEMBER_MESSAGES;
  protected readonly loanStatus = LOAN_STATUS_DISPLAY;
  protected readonly reservationStatus = RESERVATION_STATUS_DISPLAY;
  protected readonly genericError = 'Impossible de charger cette section. Réessayez.';

  private readonly tick = signal(0);
  private readonly request = computed(() => ({ tick: this.tick() }));
  protected readonly meState = viewState(this.request, () => this.space.me());
  protected readonly loansState = viewState(this.request, () => this.loanService.memberLoans(null));
  protected readonly reservationsState = viewState(this.request, () => this.reservationService.memberReservations(null));
  protected readonly finesState = viewState(this.request, () => this.fineService.unpaid(null));
  protected readonly historyOpen = signal(false);
  private readonly historyRequest = computed(() => (this.historyOpen() ? { tick: this.tick() } : null));
  protected readonly historyState = viewState(this.historyRequest, () => this.loanService.history(null, 0));

  protected readonly me = computed(() => dataOf(this.meState()));
  protected readonly loans = computed(() => dataOf(this.loansState()) ?? []);
  protected readonly reservations = computed(() => dataOf(this.reservationsState()) ?? []);
  protected readonly fines = computed(() => dataOf(this.finesState())?.fines ?? []);
  protected readonly finesTotal = computed(() => dataOf(this.finesState())?.total ?? 0);
  protected readonly history = computed(() => dataOf(this.historyState())?.content ?? []);
  protected readonly allEmpty = computed(() => this.loansState().kind === 'success' && this.reservationsState().kind === 'success'
    && this.finesState().kind === 'success' && !this.loans().length && !this.reservations().length && !this.fines().length);

  /** « dans N jours », « demain », « aujourd'hui », « N jour(s) de retard » (UX-adherents ECR-22). */
  protected dueLabel(loan: MemberLoan): string {
    const days = daysBetween(todayInParis(), loan.dueDate);
    if (days < 0) {
      return `${-days} jour${days < -1 ? 's' : ''} de retard`;
    }
    return days === 0 ? 'aujourd’hui' : days === 1 ? 'demain' : `dans ${days} jours`;
  }

  protected dueTone(loan: MemberLoan): string {
    const days = daysBetween(todayInParis(), loan.dueDate);
    return days < 0 ? 'font-medium text-danger' : days <= 3 ? 'font-medium text-warning' : '';
  }

  protected fineLabel(fine: FineView): string {
    const reason = fine.reason === 'LATE_RETURN' && fine.lateDays
      ? `${FINE_REASON_LABELS.LATE_RETURN} (${fine.lateDays} j)` : FINE_REASON_LABELS[fine.reason];
    return fine.title ? `${reason} · ${fine.title}` : reason;
  }

  protected refresh(): void {
    this.tick.update((value) => value + 1);
  }
}
