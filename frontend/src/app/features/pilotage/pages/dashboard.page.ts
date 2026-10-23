import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { GENERIC_MESSAGES } from '../../../core/http/messages';
import { dataOf, viewState } from '../../../shared/data/view-state';
import { IconComponent } from '../../../shared/ui/icon.component';
import { StatCardComponent } from '../../../shared/ui/stat-card.component';
import { ErrorStateComponent, LoadingStateComponent } from '../../../shared/ui/view-states.component';
import { PilotageService } from '../data-access/pilotage.service';

const LONG_DATE = new Intl.DateTimeFormat('fr-FR', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric', timeZone: 'UTC' });
const TIME = new Intl.DateTimeFormat('fr-FR', { hour: '2-digit', minute: '2-digit', timeZone: 'Europe/Paris' });

/** ECR-37 — Tableau de bord du jour (US-041), accueil du personnel ; « Actualiser » relance la lecture. */
@Component({
  selector: 'app-dashboard-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, IconComponent, StatCardComponent, LoadingStateComponent, ErrorStateComponent],
  template: `
    <section class="brand-gradient relative mb-6 overflow-hidden rounded-3xl p-6 text-white shadow-lift sm:p-8">
      <div class="pointer-events-none absolute -right-10 -top-16 h-56 w-56 rounded-full bg-white/10" aria-hidden="true"></div>
      <div class="pointer-events-none absolute -bottom-20 right-40 h-48 w-48 rounded-full bg-white/10" aria-hidden="true"></div>
      <div class="relative flex flex-wrap items-start justify-between gap-4">
        <div>
          <p class="text-xs font-semibold uppercase tracking-widest text-indigo-100">Activité de la bibliothèque</p>
          <h1 class="mt-2 text-2xl font-bold sm:text-3xl" data-testid="dashboard-date">Tableau de bord — {{ dateLabel() }}</h1>
          <p class="mt-1 text-sm text-indigo-100">Vue d’ensemble du jour : guichet, retards et réservations.</p>
        </div>
        <button type="button" data-testid="dashboard-refresh" (click)="refresh()"
                class="inline-flex items-center gap-2 rounded-xl bg-white/15 px-4 py-2.5 text-sm font-semibold text-white ring-1 ring-white/30 backdrop-blur transition hover:bg-white/25 motion-reduce:transition-none">
          <app-icon name="clock" [size]="16" /> Actualiser
        </button>
      </div>
      <div class="relative mt-6 grid gap-3 sm:grid-cols-2 lg:max-w-2xl">
        <a routerLink="/gestion/guichet/emprunt" data-testid="dashboard-desk-loan-link" title="Raccourci : Alt+E"
           class="group flex items-center gap-3 rounded-2xl bg-white p-4 text-ink shadow-md transition hover:-translate-y-0.5 motion-reduce:transition-none motion-reduce:hover:translate-y-0">
          <span class="flex h-11 w-11 items-center justify-center rounded-xl bg-primary-soft text-primary"><app-icon name="arrow-up-right" [size]="22" /></span>
          <span class="flex-1">
            <span class="block font-semibold">Guichet — Emprunt</span>
            <span class="block text-xs text-muted">Raccourci Alt+E</span>
          </span>
          <app-icon name="chevron-right" [size]="18" class="text-primary" />
        </a>
        <a routerLink="/gestion/guichet/retour" data-testid="dashboard-desk-return-link" title="Raccourci : Alt+R"
           class="group flex items-center gap-3 rounded-2xl bg-white p-4 text-ink shadow-md transition hover:-translate-y-0.5 motion-reduce:transition-none motion-reduce:hover:translate-y-0">
          <span class="flex h-11 w-11 items-center justify-center rounded-xl bg-brand-soft text-brand"><app-icon name="arrow-down-left" [size]="22" /></span>
          <span class="flex-1">
            <span class="block font-semibold">Guichet — Retour</span>
            <span class="block text-xs text-muted">Raccourci Alt+R</span>
          </span>
          <app-icon name="chevron-right" [size]="18" class="text-brand" />
        </a>
      </div>
    </section>

    @switch (state().kind) {
      @case ('loading') { <app-loading-state testId="dashboard-loading" [rows]="5" /> }
      @case ('error') { <app-error-state [message]="genericError" testId="dashboard-error" (retry)="refresh()" /> }
      @case ('success') {
        @if (data(); as d) {
          <h2 class="mb-3 text-lg font-bold">Aujourd’hui</h2>
          <div class="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
            <app-stat-card label="Emprunts du jour" [value]="d.loansToday" icon="arrow-up-right" tone="primary" testId="dashboard-stat-loans-today" />
            <app-stat-card label="Retours du jour" [value]="d.returnsToday" icon="arrow-down-left" tone="brand" testId="dashboard-stat-returns-today" />
            <app-stat-card label="Retards en cours" [value]="d.overdue" icon="clock" tone="danger" link="/gestion/retards" testId="dashboard-stat-overdue" />
            <app-stat-card label="Réservations prêtes" [value]="d.readyReservations" icon="bookmark" tone="accent" link="/gestion/reservations"
                           [queryParams]="{ onglet: 'pretes' }" [hint]="'dont ' + d.readyLastDay + ' dernier jour'"
                           hintTestId="dashboard-stat-ready-last-day" testId="dashboard-stat-ready" />
            <app-stat-card label="Exemplaires à remettre en rayon" [value]="d.toShelve" icon="books" tone="info" link="/gestion/reservations"
                           [queryParams]="{ onglet: 'a-ranger' }" testId="dashboard-stat-to-shelve" />
            <div class="card flex flex-col justify-between gap-3 p-5">
              <p class="text-sm font-medium text-muted">Accès rapides</p>
              <div class="flex flex-wrap gap-2">
                <a routerLink="/gestion/adherents" class="inline-flex items-center gap-1.5 rounded-lg bg-surface-alt px-3 py-1.5 text-sm font-semibold hover:bg-primary-soft hover:text-primary"><app-icon name="users" [size]="15" /> Adhérents</a>
                <a routerLink="/gestion/catalogue" class="inline-flex items-center gap-1.5 rounded-lg bg-surface-alt px-3 py-1.5 text-sm font-semibold hover:bg-primary-soft hover:text-primary"><app-icon name="books" [size]="15" /> Catalogue</a>
                <a routerLink="/gestion/penalites" class="inline-flex items-center gap-1.5 rounded-lg bg-surface-alt px-3 py-1.5 text-sm font-semibold hover:bg-primary-soft hover:text-primary"><app-icon name="coins" [size]="15" /> Pénalités</a>
              </div>
            </div>
          </div>
          <p class="mt-4 text-sm text-muted" data-testid="dashboard-updated-at">Mis à jour à {{ time(d.computedAt) }}</p>
        }
      }
    }
  `,
})
export class DashboardPage {
  private readonly pilotage = inject(PilotageService);
  protected readonly genericError = GENERIC_MESSAGES.unexpected;
  private readonly tick = signal(0);
  private readonly request = computed(() => ({ tick: this.tick() }));
  protected readonly state = viewState(this.request, () => this.pilotage.dashboard());
  protected readonly data = computed(() => dataOf(this.state()));
  protected readonly dateLabel = computed(() => {
    const date = this.data()?.date;
    return date ? LONG_DATE.format(new Date(`${date}T00:00:00Z`)) : '';
  });

  protected time(instant: string): string {
    return TIME.format(new Date(instant));
  }

  protected refresh(): void {
    this.tick.update((value) => value + 1);
  }
}
