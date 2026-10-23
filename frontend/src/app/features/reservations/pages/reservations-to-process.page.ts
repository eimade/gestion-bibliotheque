import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';

import { dataOf, viewState } from '../../../shared/data/view-state';
import { DEFAULT_PAGE_SIZE } from '../../../shared/models/page.model';
import { RESERVATION_STATUS_DISPLAY } from '../../../shared/models/statuses';
import { DateFrPipe } from '../../../shared/pipes/date-fr.pipe';
import { GENERIC_MESSAGES } from '../../../core/http/messages';
import { PageHeaderComponent } from '../../../shared/ui/page-header.component';
import { PaginationComponent } from '../../../shared/ui/pagination.component';
import { TabItem, TabsComponent } from '../../../shared/ui/tabs.component';
import { ErrorStateComponent, LoadingStateComponent } from '../../../shared/ui/view-states.component';
import { memberLabel } from '../../emprunts/data-access/loan-messages';
import { RESERVATION_MESSAGES, releaseLabel } from '../data-access/reservation-messages';
import { ReleasedCopy } from '../data-access/reservation.models';
import { ReservationService } from '../data-access/reservation.service';

type ProcessTab = 'pretes' | 'a-ranger';

/** ECR-33 — Réservations à traiter (US-035) : prêtes (date limite croissante) et exemplaires à remettre en rayon. */
@Component({
  selector: 'app-reservations-to-process-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, DateFrPipe, PageHeaderComponent, PaginationComponent, TabsComponent, LoadingStateComponent,
    ErrorStateComponent],
  template: `
    <app-page-header title="Réservations à traiter" />
    <app-tabs [tabs]="tabs()" [active]="active()" label="Réservations à traiter" (activeChange)="switchTab($event)" />
    <div class="mt-4" [id]="'panel-' + active()" role="tabpanel" [attr.aria-labelledby]="'tab-' + active()">
      @if (active() === 'pretes') {
        @switch (readyState().kind) {
          @case ('loading') { <app-loading-state testId="to-process-loading" /> }
          @case ('error') { <app-error-state [message]="genericError" testId="to-process-error" (retry)="reload()" /> }
          @case ('success') {
            @if (ready().length === 0) {
              <p class="card p-8 text-center" data-testid="ready-empty">{{ messages.noReady }}</p>
            } @else {
              <div class="card overflow-x-auto">
                <table class="w-full text-left text-sm">
                  <caption class="sr-only">Réservations prêtes, par date limite croissante</caption>
                  <thead class="bg-surface-alt text-xs uppercase tracking-wide text-muted"><tr>
                    <th scope="col" class="px-4 py-3 font-semibold">Titre</th><th scope="col" class="px-4 py-3 font-semibold">N° ex.</th>
                    <th scope="col" class="px-4 py-3 font-semibold">Cote</th><th scope="col" class="px-4 py-3 font-semibold">Adhérent</th>
                    <th scope="col" class="px-4 py-3 font-semibold" aria-sort="ascending">Retrait avant ▲</th>
                  </tr></thead>
                  <tbody>
                    @for (row of ready(); track row.id) {
                      <tr class="border-t border-border" data-testid="ready-row" [attr.data-row-id]="row.copyNumber">
                        <td class="px-4 py-3">{{ row.title }}</td><td class="px-4 py-3 font-mono">{{ row.copyNumber }}</td>
                        <td class="px-4 py-3">{{ row.shelfMark }}</td>
                        <td class="px-4 py-3">
                          @if (row.member) {
                            <a [routerLink]="['/gestion/adherents', row.member.memberNumber]" class="text-primary hover:underline">{{ label(row.member) }}</a>
                            @if (row.notificationFailed) {
                              <p class="text-xs text-warning" data-testid="ready-email-failed">✉ E-mail non envoyé : appeler {{ row.member.phone ?? '—' }}</p>
                            }
                          }
                        </td>
                        <td class="px-4 py-3 tabular-nums" data-testid="ready-deadline">
                          {{ row.pickupDeadline | dateFr }}
                          @if (row.lastDay) {
                            <span class="ml-1 rounded-full bg-warning-soft px-2 py-0.5 text-xs font-medium text-warning" data-testid="ready-last-day">Dernier jour</span>
                          }
                        </td>
                      </tr>
                    }
                  </tbody>
                </table>
              </div>
              <div class="mt-4">
                <app-pagination [page]="page()" [size]="size()" [totalPages]="readyPages()" testId="ready-pagination"
                                (pageChange)="page.set($event)" (sizeChange)="size.set($event); page.set(0)" />
              </div>
            }
          }
        }
      } @else {
        @switch (releasedState().kind) {
          @case ('loading') { <app-loading-state testId="to-process-loading" /> }
          @case ('error') { <app-error-state [message]="genericError" testId="to-process-error" (retry)="reload()" /> }
          @case ('success') {
            @if (released().length === 0) {
              <p class="card p-8 text-center" data-testid="released-empty">{{ messages.noReleased }}</p>
            } @else {
              <div class="card overflow-x-auto">
                <table class="w-full text-left text-sm">
                  <caption class="sr-only">Exemplaires à remettre en rayon</caption>
                  <thead class="bg-surface-alt text-xs uppercase tracking-wide text-muted"><tr>
                    <th scope="col" class="px-4 py-3 font-semibold">Titre</th><th scope="col" class="px-4 py-3 font-semibold">N° ex.</th>
                    <th scope="col" class="px-4 py-3 font-semibold">Cote</th><th scope="col" class="px-4 py-3 font-semibold">Réservation</th>
                    <th scope="col" class="px-4 py-3 font-semibold">Destination</th>
                  </tr></thead>
                  <tbody>
                    @for (row of released(); track row.reservationId) {
                      <tr class="border-t border-border" data-testid="released-row" [attr.data-row-id]="row.copyNumber">
                        <td class="px-4 py-3">{{ row.title }}</td><td class="px-4 py-3 font-mono">{{ row.copyNumber }}</td>
                        <td class="px-4 py-3">{{ row.shelfMark }}</td>
                        <td class="px-4 py-3">{{ statusDisplay[row.closedStatus].label }} le {{ row.closedOn | dateFr }}
                          @if (row.previousMember) { ({{ label(row.previousMember) }}) }</td>
                        <td class="px-4 py-3 font-medium" data-testid="released-destination" [attr.data-destination]="row.destination.toLowerCase()">
                          → {{ destination(row) }}
                        </td>
                      </tr>
                    }
                  </tbody>
                </table>
              </div>
              <div class="mt-4">
                <app-pagination [page]="page()" [size]="size()" [totalPages]="releasedPages()" testId="released-pagination"
                                (pageChange)="page.set($event)" (sizeChange)="size.set($event); page.set(0)" />
              </div>
            }
          }
        }
      }
    </div>
  `,
})
export class ReservationsToProcessPage {
  /** `?onglet=pretes|a-ranger` (liens du tableau de bord). */
  readonly onglet = input<string>();

  private readonly reservations = inject(ReservationService);
  private readonly router = inject(Router);
  protected readonly messages = RESERVATION_MESSAGES;
  protected readonly statusDisplay = RESERVATION_STATUS_DISPLAY;
  protected readonly label = memberLabel;
  protected readonly genericError = GENERIC_MESSAGES.unexpected;
  protected readonly active = computed<ProcessTab>(() => (this.onglet() === 'a-ranger' ? 'a-ranger' : 'pretes'));
  protected readonly page = signal(0);
  protected readonly size = signal(DEFAULT_PAGE_SIZE);
  private readonly reloadTick = signal(0);

  private readonly readyRequest = computed(() => {
    this.reloadTick();
    return { page: this.page(), size: this.size() };
  });
  protected readonly readyState = viewState(this.readyRequest, ({ page, size }) => this.reservations.ready(page, size));
  protected readonly releasedState = viewState(this.readyRequest, ({ page, size }) => this.reservations.released(page, size));
  protected readonly ready = computed(() => dataOf(this.readyState())?.content ?? []);
  protected readonly readyPages = computed(() => dataOf(this.readyState())?.totalPages ?? 0);
  protected readonly released = computed(() => dataOf(this.releasedState())?.content ?? []);
  protected readonly releasedPages = computed(() => dataOf(this.releasedState())?.totalPages ?? 0);
  protected readonly tabs = computed<readonly TabItem[]>(() => [
    { id: 'pretes', label: `Prêtes (${dataOf(this.readyState())?.totalElements ?? 0})`, testId: 'to-process-tab-ready' },
    { id: 'a-ranger', label: `À remettre en rayon (${dataOf(this.releasedState())?.totalElements ?? 0})`,
      testId: 'to-process-tab-released' },
  ]);

  protected destination(row: ReleasedCopy): string {
    return releaseLabel({ copyNumber: row.copyNumber, shelfMark: row.shelfMark, destination: row.destination, hold: row.hold });
  }

  protected switchTab(tab: string): void {
    this.page.set(0);
    void this.router.navigate([], { queryParams: { onglet: tab }, replaceUrl: true });
  }

  protected reload(): void {
    this.reloadTick.update((tick) => tick + 1);
  }
}
