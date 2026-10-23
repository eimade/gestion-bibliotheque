import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Observable } from 'rxjs';

import { AuthService } from '../../../core/auth/auth.service';
import { ApiError } from '../../../core/http/api-error';
import { GENERIC_MESSAGES } from '../../../core/http/messages';
import { ToastService } from '../../../core/notifications/toast.service';
import { dataOf, viewState } from '../../../shared/data/view-state';
import {
  FINE_REASON_LABELS,
  LOAN_STATUS_DISPLAY,
  MEMBER_STATUS_DISPLAY,
  RESERVATION_STATUS_DISPLAY,
} from '../../../shared/models/statuses';
import { DateFrPipe } from '../../../shared/pipes/date-fr.pipe';
import { EuroPipe, formatEuro } from '../../../shared/pipes/euro.pipe';
import { AlertComponent } from '../../../shared/ui/alert.component';
import { ButtonComponent } from '../../../shared/ui/button.component';
import { ConfirmDialogComponent } from '../../../shared/ui/confirm-dialog.component';
import { MenuButtonComponent, MenuItem } from '../../../shared/ui/menu-button.component';
import { PaginationComponent } from '../../../shared/ui/pagination.component';
import { StatusBadgeComponent } from '../../../shared/ui/status-badge.component';
import { TabItem, TabsComponent } from '../../../shared/ui/tabs.component';
import { EmptyStateComponent, ErrorStateComponent, LoadingStateComponent } from '../../../shared/ui/view-states.component';
import { ResetPasswordDialogComponent } from '../../authentification/components/reset-password-dialog.component';
import { DeclareLostDialogComponent, LossTarget } from '../../emprunts/components/declare-lost-dialog.component';
import { RenewLoanActionComponent } from '../../emprunts/components/renew-loan-action.component';
import { MemberLoan } from '../../emprunts/data-access/loan.models';
import { LoanService } from '../../emprunts/data-access/loan.service';
import { CollectFinesDialogComponent, CollectTarget } from '../../penalites/components/collect-fines-dialog.component';
import { WaiveFineDialogComponent } from '../../penalites/components/waive-fine-dialog.component';
import { FineView } from '../../penalites/data-access/fine.models';
import { FineService } from '../../penalites/data-access/fine.service';
import { CancelReservationActionComponent } from '../../reservations/components/cancel-reservation-action.component';
import { ReservationService } from '../../reservations/data-access/reservation.service';
import { EligibilityPanelComponent } from '../components/eligibility-panel.component';
import { ArchiveMemberDialogComponent, SuspendMemberDialogComponent } from '../components/member-dialogs.component';
import { MEMBER_MESSAGES, memberErrorMessage } from '../data-access/member-messages';
import { ArchiveCheck, MemberDetail, memberName } from '../data-access/member.models';
import { MemberService } from '../data-access/member.service';

type Tab = 'loans' | 'reservations' | 'fines' | 'history' | 'contact';

/**
 * ECR-18 — Fiche adhérent (US-018, US-020) et points d'entrée des actions de circulation : emprunt, prolongation,
 * perte, annulation de réservation, encaissement et remise (ADMIN).
 */
@Component({
  selector: 'app-member-detail-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, DateFrPipe, EuroPipe, AlertComponent, ButtonComponent, ConfirmDialogComponent, MenuButtonComponent,
    PaginationComponent, StatusBadgeComponent, TabsComponent, LoadingStateComponent, EmptyStateComponent,
    ErrorStateComponent, EligibilityPanelComponent, SuspendMemberDialogComponent, ArchiveMemberDialogComponent,
    ResetPasswordDialogComponent, RenewLoanActionComponent, DeclareLostDialogComponent, CancelReservationActionComponent,
    CollectFinesDialogComponent, WaiveFineDialogComponent],
  template: `
    <nav class="mb-2 text-sm" aria-label="Fil d'Ariane">
      <a routerLink="/gestion/adherents" class="font-semibold text-primary hover:underline">Adhérents</a>
      <span aria-hidden="true"> › </span><span>{{ memberNumber() }}</span>
    </nav>
    @switch (state().kind) {
      @case ('loading') { <app-loading-state testId="member-detail-loading" [rows]="8" /> }
      @case ('error') {
        @if (notFound()) {
          <p class="card p-8 text-center" data-testid="member-detail-not-found">
            {{ messages.notFound(memberNumber()) }}
          </p>
        } @else {
          <app-error-state [message]="errorMessage()" testId="member-detail-error" (retry)="reload()" />
        }
      }
      @case ('success') {
        @if (member(); as m) {
          <section class="card space-y-4 p-6">
            <div class="flex flex-wrap items-center gap-3">
              <span class="brand-gradient flex h-12 w-12 items-center justify-center rounded-2xl text-base font-bold text-white" aria-hidden="true">{{ (m.lastName ?? '').charAt(0) + (m.firstName ?? '').charAt(0) }}</span>
              <h1 class="text-2xl font-bold" data-testid="member-detail-name">{{ name(m) }}</h1>
              <span class="rounded-lg bg-surface-alt px-2 py-0.5 font-mono text-sm text-muted" data-testid="member-detail-number">{{ m.memberNumber }}</span>
              <span data-testid="member-detail-status"><app-status-badge [display]="statusDisplay[m.status]" [value]="m.status" /></span>
              @if (m.status !== 'ARCHIVED') {
                <span class="text-sm" data-testid="member-detail-expiry">Adhésion jusqu’au {{ m.expiryDate | dateFr }}</span>
              }
            </div>

            @if (m.status === 'ARCHIVED') {
              <app-alert tone="info" data-testid="member-archived-notice">{{ messages.archivedNotice }}</app-alert>
            } @else {
              <div class="flex flex-wrap items-start gap-2">
                <a [routerLink]="m.eligibility.eligible ? '/gestion/guichet/emprunt' : null" [queryParams]="{ adherent: m.memberNumber }"
                   data-testid="member-detail-new-loan-button" [attr.aria-disabled]="!m.eligibility.eligible"
                   [class]="m.eligibility.eligible ? 'bg-primary text-surface hover:bg-primary-hover' : 'cursor-not-allowed bg-surface-alt text-muted'"
                   class="inline-flex items-center rounded-xl px-4 py-2.5 text-sm font-semibold shadow-sm">Nouvel emprunt</a>
                @if (m.status === 'ACTIVE' || m.status === 'EXPIRED') {
                  <div>
                    <button app-button type="button" variant="secondary" [disabled]="!m.renewal.allowed"
                            data-testid="member-detail-renew-button" (click)="renewOpen.set(true)">Renouveler</button>
                    @if (m.renewal.blockedBy === 'RENEWAL_TOO_EARLY' && m.renewal.renewableFrom) {
                      <p class="mt-1 text-xs text-muted" data-testid="member-detail-renew-blocked">
                        {{ messages.renewFrom(m.renewal.renewableFrom) }}
                      </p>
                    }
                  </div>
                }
                <a [routerLink]="['/gestion/adherents', m.memberNumber, 'modifier']" data-testid="member-detail-edit-button"
                   class="inline-flex items-center gap-2 rounded-xl border border-border bg-surface px-4 py-2.5 text-sm font-semibold shadow-sm hover:border-primary/40 hover:bg-primary-soft hover:text-primary">
                  Modifier
                </a>
                <app-menu-button label="Plus d’actions" triggerText="Plus" [items]="moreItems()" testId="member-detail-more-menu" [bordered]="true"
                                 (selected)="onMore($event)" />
              </div>

              @if (m.expiresSoon) {
                <app-alert tone="warning" data-testid="member-detail-renew-alert">
                  L’adhésion expire le {{ m.expiryDate | dateFr }}.
                  @if (m.renewal.allowed) {
                    <button type="button" class="ml-2 font-medium underline" (click)="renewOpen.set(true)">Renouveler</button>
                  }
                </app-alert>
              }
              @if (m.status === 'SUSPENDED' && m.suspensionReason) {
                <app-alert tone="danger">Suspendu : {{ m.suspensionReason }}</app-alert>
              }
              <app-eligibility-panel [eligibility]="m.eligibility" />
              <p class="text-sm text-muted">Emprunts en cours : {{ m.eligibility.activeLoans }} / {{ m.eligibility.loanLimit }}</p>

              <app-tabs [tabs]="tabs()" [active]="tab()" label="Situation de l’adhérent" (activeChange)="selectTab($event)" />
              <div [id]="'panel-' + tab()" role="tabpanel" [attr.aria-labelledby]="'tab-' + tab()">
                @switch (tab()) {
                  @case ('loans') {
                    @switch (loansState().kind) {
                      @case ('loading') { <app-loading-state [rows]="3" /> }
                      @case ('error') { <app-error-state [message]="genericError" testId="member-loans-error" (retry)="refreshTabs()" /> }
                      @case ('success') {
                        @if (loans().length === 0) {
                          <app-empty-state [message]="messages.noLoan" testId="member-loans-empty" />
                        } @else {
                          <div class="overflow-x-auto">
                            <table class="w-full text-left text-sm">
                              <caption class="sr-only">Emprunts en cours</caption>
                              <thead class="bg-surface-alt text-xs uppercase tracking-wide text-muted"><tr>
                                <th scope="col" class="px-4 py-3 font-semibold">Titre</th><th scope="col" class="px-4 py-3 font-semibold">N° ex.</th>
                                <th scope="col" class="px-4 py-3 font-semibold">Emprunté</th><th scope="col" class="px-4 py-3 font-semibold">Retour</th>
                                <th scope="col" class="px-4 py-3 font-semibold">Statut</th><th scope="col" class="px-4 py-3 font-semibold"><span class="sr-only">Actions</span></th>
                              </tr></thead>
                              <tbody>
                                @for (loan of loans(); track loan.id) {
                                  <tr class="border-t border-border align-top" data-testid="member-loan-row" [attr.data-row-id]="loan.copyNumber">
                                    <td class="px-4 py-3">{{ loan.title }}</td>
                                    <td class="px-4 py-3 font-mono">{{ loan.copyNumber }}</td>
                                    <td class="px-4 py-3 tabular-nums">{{ loan.loanDate | dateFr }}</td>
                                    <td class="px-4 py-3 tabular-nums">{{ loan.dueDate | dateFr }}</td>
                                    <td class="px-4 py-3">
                                      <app-status-badge [display]="loanStatus[loan.status]" [value]="loan.status" />
                                      @if (loan.status === 'OVERDUE') {
                                        <p class="mt-1 text-xs">
                                          <span data-testid="member-loan-overdue-days">{{ loan.overdueDays }} j</span> ·
                                          <span data-testid="member-loan-provisional-fine">{{ loan.provisionalFine | euro }}</span>
                                          <span class="text-warning"> provisoire</span>
                                        </p>
                                      }
                                    </td>
                                    <td class="px-4 py-3 text-right">
                                      <app-menu-button [label]="'Actions pour l’emprunt ' + loan.copyNumber" [items]="loanMenu(loan)"
                                                       testId="member-loan-menu" (selected)="onLoanAction(loan, $event)" />
                                    </td>
                                  </tr>
                                }
                              </tbody>
                            </table>
                          </div>
                        }
                      }
                    }
                  }
                  @case ('reservations') {
                    @switch (reservationsState().kind) {
                      @case ('loading') { <app-loading-state [rows]="2" /> }
                      @case ('error') { <app-error-state [message]="genericError" testId="member-reservations-error" (retry)="refreshTabs()" /> }
                      @case ('success') {
                        @if (reservations().length === 0) {
                          <app-empty-state [message]="messages.noReservation" testId="member-reservations-empty" />
                        } @else {
                          <ul class="divide-y divide-border">
                            @for (reservation of reservations(); track reservation.id) {
                              <li class="flex flex-wrap items-center justify-between gap-3 py-3" data-testid="member-reservation-row">
                                <div>
                                  <p class="font-medium">{{ reservation.title }}</p>
                                  <p class="text-sm">
                                    <app-status-badge [display]="reservationStatus[reservation.status]" [value]="reservation.status" />
                                    @if (reservation.status === 'PENDING') {
                                      <span class="ml-2">Position {{ reservation.position }} sur {{ reservation.queueLength }}</span>
                                    } @else if (reservation.pickupDeadline) {
                                      <span class="ml-2">À retirer avant le {{ reservation.pickupDeadline | dateFr }}
                                        · ex. {{ reservation.copyNumber }}</span>
                                    }
                                  </p>
                                </div>
                                <app-cancel-reservation-action [reservationId]="reservation.id" [title]="reservation.title"
                                                               [ready]="reservation.status === 'READY'" (cancelled)="refreshAll()" />
                              </li>
                            }
                          </ul>
                        }
                      }
                    }
                  }
                  @case ('fines') {
                    @switch (finesState().kind) {
                      @case ('loading') { <app-loading-state [rows]="2" /> }
                      @case ('error') { <app-error-state [message]="genericError" testId="member-fines-error" (retry)="refreshTabs()" /> }
                      @case ('success') {
                        @if (fines().length === 0) {
                          <app-empty-state [message]="messages.noFine" testId="member-fines-empty" />
                        } @else {
                          <div class="mb-3 flex flex-wrap items-center justify-between gap-3">
                            <p class="font-semibold">Total impayé : <span class="tabular-nums" data-testid="member-fines-total">{{ finesTotal() | euro }}</span></p>
                            <button app-button type="button" data-testid="member-fines-collect" (click)="openCollect(m)">Encaisser</button>
                          </div>
                          <ul class="divide-y divide-border">
                            @for (fine of fines(); track fine.id) {
                              <li class="flex flex-wrap items-center justify-between gap-3 py-2 text-sm" data-testid="member-fine-row">
                                <span>{{ fineLabel(fine) }} · {{ fine.createdOn | dateFr }}</span>
                                <span class="flex items-center gap-3">
                                  <span class="tabular-nums font-medium">{{ fine.amount | euro }}</span>
                                  @if (isAdmin()) {
                                    <button type="button" class="text-primary underline" data-testid="member-fine-waive-action"
                                            (click)="waiveTarget.set(fine)">Accorder une remise</button>
                                  }
                                </span>
                              </li>
                            }
                          </ul>
                        }
                      }
                    }
                  }
                  @case ('history') {
                    @switch (historyState().kind) {
                      @case ('loading') { <app-loading-state [rows]="3" /> }
                      @case ('error') { <app-error-state [message]="genericError" testId="member-history-error" (retry)="refreshTabs()" /> }
                      @case ('success') {
                        @if (history().length === 0) {
                          <app-empty-state [message]="messages.noHistory" testId="member-history-empty" />
                        } @else {
                          <table class="w-full text-left text-sm">
                            <caption class="sr-only">Emprunts rendus au cours des 4 derniers mois</caption>
                            <thead class="bg-surface-alt text-xs uppercase tracking-wide text-muted"><tr>
                              <th scope="col" class="px-4 py-3 font-semibold">Titre</th><th scope="col" class="px-4 py-3 font-semibold">N° ex.</th>
                              <th scope="col" class="px-4 py-3 font-semibold">Emprunté</th><th scope="col" class="px-4 py-3 font-semibold">Rendu</th>
                            </tr></thead>
                            <tbody>
                              @for (loan of history(); track loan.id) {
                                <tr class="border-t border-border" data-testid="member-history-row">
                                  <td class="px-4 py-3">{{ loan.title }}</td><td class="px-4 py-3 font-mono">{{ loan.copyNumber }}</td>
                                  <td class="px-4 py-3 tabular-nums">{{ loan.loanDate | dateFr }}</td>
                                  <td class="px-4 py-3 tabular-nums">{{ loan.returnDate | dateFr }}</td>
                                </tr>
                              }
                            </tbody>
                          </table>
                          <div class="mt-3">
                            <app-pagination [page]="historyPage()" [size]="20" [totalPages]="historyPages()" testId="member-history-pagination"
                                            (pageChange)="historyPage.set($event)" (sizeChange)="historyPage.set(0)" />
                          </div>
                        }
                      }
                    }
                  }
                  @case ('contact') {
                    <dl class="grid gap-x-6 gap-y-2 text-sm sm:grid-cols-[12rem_1fr]">
                      <dt class="text-muted">Date de naissance</dt><dd>{{ m.birthDate | dateFr }}</dd>
                      <dt class="text-muted">E-mail</dt><dd>{{ m.email ?? '—' }}</dd>
                      <dt class="text-muted">Téléphone</dt><dd>{{ m.phone ?? '—' }}</dd>
                      <dt class="text-muted">Adresse</dt><dd>{{ m.street }}, {{ m.postalCode }} {{ m.city }}</dd>
                      @if (m.guardian; as guardian) {
                        <dt class="text-muted">Représentant légal</dt>
                        <dd>{{ guardian.lastName.toUpperCase() }} {{ guardian.firstName }} · {{ guardian.phone }}</dd>
                      }
                      <dt class="text-muted">Inscrit le</dt><dd>{{ m.registrationDate | dateFr }}</dd>
                    </dl>
                  }
                }
              </div>
            }
          </section>

          <app-confirm-dialog [open]="renewOpen()" title="Renouveler l’adhésion" tone="primary" confirmLabel="Renouveler"
                              [message]="'Renouveler l’adhésion de ' + name(m) + ' jusqu’au ' + (m.renewal.newExpiryDate | dateFr) + ' ?'"
                              [busy]="busy()" testId="member-renew" (confirmed)="renew(m)" (cancelled)="renewOpen.set(false)" />
          <app-suspend-member-dialog [member]="m" [open]="suspendOpen()" [busy]="busy()" [serverError]="dialogError()"
                                     (suspend)="suspend(m, $event)" (reactivate)="reactivate(m)" (cancelled)="suspendOpen.set(false)" />
          <app-archive-member-dialog [label]="name(m) + ' (' + m.memberNumber + ')'" [open]="archiveOpen()" [check]="archiveCheck()"
                                     [busy]="busy()" [serverError]="dialogError()" (confirmed)="archive(m)"
                                     (cancelled)="archiveOpen.set(false)" />
          <app-reset-password-dialog [open]="resetOpen()" [accountId]="m.accountId ?? null" [accountLabel]="name(m)"
                                     (closed)="resetOpen.set(false)" />
          @if (renewTarget(); as loan) {
            <app-renew-loan-action [loan]="loan" [showTrigger]="false" [threshold]="m.eligibility.fineThreshold"
                                   (renewed)="renewTarget.set(null); refreshAll()" (closed)="renewTarget.set(null)" />
          }
          <app-declare-lost-dialog [target]="lossTarget()" (declared)="lossTarget.set(null); refreshAll()"
                                   (cancelled)="lossTarget.set(null)" />
          <app-collect-fines-dialog [target]="collectTarget()" (closed)="onCollectClosed($event)" />
          <app-waive-fine-dialog [fine]="waiveTarget()" [memberLabel]="name(m) + ' (' + m.memberNumber + ')'"
                                 (waived)="waiveTarget.set(null); refreshAll()" (cancelled)="waiveTarget.set(null)" />
        }
      }
    }
  `,
})
export class MemberDetailPage {
  readonly memberNumber = input.required<string>();

  private readonly members = inject(MemberService);
  private readonly loanService = inject(LoanService);
  private readonly reservationService = inject(ReservationService);
  private readonly fineService = inject(FineService);
  private readonly toasts = inject(ToastService);
  private readonly auth = inject(AuthService);
  protected readonly messages = MEMBER_MESSAGES;
  protected readonly statusDisplay = MEMBER_STATUS_DISPLAY;
  protected readonly loanStatus = LOAN_STATUS_DISPLAY;
  protected readonly reservationStatus = RESERVATION_STATUS_DISPLAY;
  protected readonly name = memberName;
  protected readonly genericError = GENERIC_MESSAGES.unexpected;
  protected readonly isAdmin = computed(() => this.auth.user()?.role === 'ADMIN');

  private readonly reloadTick = signal(0);
  private readonly tabsTick = signal(0);
  private readonly request = computed(() => {
    this.reloadTick();
    return { number: this.memberNumber() };
  });
  protected readonly state = viewState(this.request, ({ number }) => this.members.get(number));
  protected readonly member = computed(() => dataOf(this.state()));
  protected readonly notFound = computed(() => {
    const state = this.state();
    return state.kind === 'error' && state.error instanceof ApiError && state.error.status === 404;
  });
  protected readonly errorMessage = computed(() => {
    const state = this.state();
    return state.kind === 'error' && state.error instanceof ApiError ? memberErrorMessage(state.error) : GENERIC_MESSAGES.unexpected;
  });

  protected readonly tab = signal<Tab>('loans');
  protected readonly historyPage = signal(0);
  private tabRequest<T>(tab: Tab, load: (number: string) => Observable<T>) {
    return viewState(computed(() => {
      this.tabsTick();
      const member = this.member();
      return this.tab() === tab && member && member.status !== 'ARCHIVED' ? { number: member.memberNumber, page: this.historyPage() } : null;
    }), ({ number }) => load(number));
  }
  protected readonly loansState = this.tabRequest('loans', (number) => this.loanService.memberLoans(number));
  protected readonly reservationsState = this.tabRequest('reservations', (number) => this.reservationService.memberReservations(number));
  protected readonly finesState = this.tabRequest('fines', (number) => this.fineService.unpaid(number));
  protected readonly historyState = this.tabRequest('history', (number) => this.loanService.history(number, this.historyPage()));
  protected readonly loans = computed(() => dataOf(this.loansState()) ?? []);
  protected readonly reservations = computed(() => dataOf(this.reservationsState()) ?? []);
  protected readonly fines = computed(() => dataOf(this.finesState())?.fines ?? []);
  protected readonly finesTotal = computed(() => dataOf(this.finesState())?.total ?? 0);
  protected readonly history = computed(() => dataOf(this.historyState())?.content ?? []);
  protected readonly historyPages = computed(() => dataOf(this.historyState())?.totalPages ?? 0);

  protected readonly tabs = computed<readonly TabItem[]>(() => {
    const member = this.member();
    return [
      { id: 'loans', label: `Emprunts (${member?.eligibility.activeLoans ?? 0})`, testId: 'member-tab-loans' },
      { id: 'reservations', label: `Réservations (${member?.activeReservations ?? 0})`, testId: 'member-tab-reservations' },
      { id: 'fines', label: `Pénalités (${formatEuro(member?.eligibility.unpaidTotal ?? 0)})`, testId: 'member-tab-fines' },
      { id: 'history', label: 'Historique', testId: 'member-tab-history' },
      { id: 'contact', label: 'Coordonnées', testId: 'member-tab-contact' },
    ];
  });

  protected readonly moreItems = computed<readonly MenuItem[]>(() => {
    const member = this.member();
    return [
      member?.status === 'SUSPENDED'
        ? { id: 'reactivate', label: 'Réactiver', testId: 'member-detail-reactivate-action' }
        : { id: 'suspend', label: 'Suspendre', testId: 'member-detail-suspend-action' },
      { id: 'reset', label: 'Réinitialiser le mot de passe', testId: 'member-detail-reset-password-action',
        disabled: !member?.accountId || member.accountStatus === 'DISABLED' },
      { id: 'archive', label: 'Archiver', testId: 'member-detail-archive-action' },
    ];
  });

  protected readonly busy = signal(false);
  protected readonly dialogError = signal<string | null>(null);
  protected readonly renewOpen = signal(false);
  protected readonly suspendOpen = signal(false);
  protected readonly archiveOpen = signal(false);
  protected readonly archiveCheck = signal<ArchiveCheck | null>(null);
  protected readonly resetOpen = signal(false);
  protected readonly renewTarget = signal<MemberLoan | null>(null);
  protected readonly lossTarget = signal<LossTarget | null>(null);
  protected readonly collectTarget = signal<CollectTarget | null>(null);
  protected readonly waiveTarget = signal<FineView | null>(null);

  protected selectTab(tab: string): void {
    this.tab.set(tab as Tab);
  }

  protected loanMenu(loan: MemberLoan): readonly MenuItem[] {
    return [
      { id: 'renew', label: 'Prolonger', testId: 'member-loan-renew-action', disabled: !loan.renewal.allowed },
      { id: 'lost', label: 'Déclarer perdu', testId: 'member-loan-lost-action' },
    ];
  }

  protected onLoanAction(loan: MemberLoan, action: string): void {
    const member = this.member();
    if (action === 'renew') {
      this.renewTarget.set(loan);
    } else if (member) {
      this.lossTarget.set({ loanId: loan.id, title: loan.title, copyNumber: loan.copyNumber, dueDate: loan.dueDate,
        member: { memberNumber: member.memberNumber, lastName: member.lastName, firstName: member.firstName } });
    }
  }

  protected fineLabel(fine: FineView): string {
    const reason = fine.reason === 'LATE_RETURN' && fine.lateDays
      ? `${FINE_REASON_LABELS.LATE_RETURN} (${fine.lateDays} j)` : FINE_REASON_LABELS[fine.reason];
    return fine.title ? `${reason} · ${fine.title}` : reason;
  }

  protected openCollect(member: MemberDetail): void {
    this.collectTarget.set({ memberNumber: member.memberNumber, label: `${memberName(member)} (${member.memberNumber})` });
  }

  protected onCollectClosed(collected: boolean): void {
    this.collectTarget.set(null);
    if (collected) {
      this.refreshAll();
    }
  }

  protected onMore(action: string): void {
    const member = this.member();
    if (!member) {
      return;
    }
    this.dialogError.set(null);
    switch (action) {
      case 'suspend':
      case 'reactivate':
        this.suspendOpen.set(true);
        break;
      case 'reset':
        this.resetOpen.set(true);
        break;
      case 'archive':
        this.archiveCheck.set(null);
        this.archiveOpen.set(true);
        this.members.archiveCheck(member.memberNumber).subscribe({
          next: (check) => this.archiveCheck.set(check),
          error: (error: unknown) => this.dialogError.set(this.message(error)),
        });
        break;
    }
  }

  protected renew(member: MemberDetail): void {
    this.run(this.members.renew(member.memberNumber), (updated) => {
      this.renewOpen.set(false);
      this.toasts.success(MEMBER_MESSAGES.renewed(updated.expiryDate));
    }, () => this.renewOpen.set(false));
  }

  protected suspend(member: MemberDetail, reason: string): void {
    this.run(this.members.suspend(member.memberNumber, reason), () => {
      this.suspendOpen.set(false);
      this.toasts.success(MEMBER_MESSAGES.suspended);
    });
  }

  protected reactivate(member: MemberDetail): void {
    this.run(this.members.reactivate(member.memberNumber), (updated) => {
      this.suspendOpen.set(false);
      this.toasts.success(MEMBER_MESSAGES.reactivated(MEMBER_STATUS_DISPLAY[updated.status].label));
    });
  }

  protected archive(member: MemberDetail): void {
    this.run(this.members.archive(member.memberNumber), () => {
      this.archiveOpen.set(false);
      this.toasts.success(MEMBER_MESSAGES.archived);
    });
  }

  protected reload(): void {
    this.reloadTick.update((tick) => tick + 1);
  }

  protected refreshTabs(): void {
    this.tabsTick.update((tick) => tick + 1);
  }

  /** Après une action de circulation : la fiche (éligibilité, compteurs) et l'onglet sont relus. */
  protected refreshAll(): void {
    this.reload();
    this.refreshTabs();
  }

  private run(action: Observable<MemberDetail>, onSuccess: (member: MemberDetail) => void, onError?: () => void): void {
    this.busy.set(true);
    this.dialogError.set(null);
    action.subscribe({
      next: (member) => {
        this.busy.set(false);
        onSuccess(member);
        this.refreshAll();
      },
      error: (error: unknown) => {
        this.busy.set(false);
        const message = this.message(error);
        this.dialogError.set(message);
        if (onError) {
          onError();
          this.toasts.error(message);
        }
      },
    });
  }

  private message(error: unknown): string {
    return error instanceof ApiError ? memberErrorMessage(error) : GENERIC_MESSAGES.unexpected;
  }
}
