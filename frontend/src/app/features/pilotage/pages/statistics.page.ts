import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';

import { ApiError } from '../../../core/http/api-error';
import { GENERIC_MESSAGES } from '../../../core/http/messages';
import { ToastService } from '../../../core/notifications/toast.service';
import { dataOf, viewState } from '../../../shared/data/view-state';
import { todayInParis } from '../../../shared/pipes/date-fr.pipe';
import { EuroPipe } from '../../../shared/pipes/euro.pipe';
import { ButtonComponent } from '../../../shared/ui/button.component';
import { PageHeaderComponent } from '../../../shared/ui/page-header.component';
import { StatCardComponent } from '../../../shared/ui/stat-card.component';
import { ErrorStateComponent, LoadingStateComponent } from '../../../shared/ui/view-states.component';
import { commonErrorMessage } from '../../authentification/data-access/auth-messages';
import { PilotageService } from '../data-access/pilotage.service';

const MONTHS = ['janvier', 'février', 'mars', 'avril', 'mai', 'juin', 'juillet', 'août', 'septembre', 'octobre', 'novembre', 'décembre'];
const RATE = new Intl.NumberFormat('fr-FR', { minimumFractionDigits: 2, maximumFractionDigits: 2 });

/** ECR-38 — Statistiques mensuelles (US-042, RG-41) en tableaux, export tableur sans donnée nominative. */
@Component({
  selector: 'app-statistics-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [EuroPipe, ButtonComponent, PageHeaderComponent, StatCardComponent, LoadingStateComponent, ErrorStateComponent],
  template: `
    <app-page-header title="Statistiques">
      <button app-button type="button" variant="secondary" [loading]="exporting()" data-testid="stats-export-button"
              (click)="export()">Exporter (tableur)</button>
    </app-page-header>
    <fieldset class="mb-6 flex flex-wrap items-end gap-3" data-testid="stats-month-picker">
      <legend class="sr-only">Mois</legend>
      <div class="space-y-1">
        <label for="stats-month" class="block text-sm font-medium">Mois</label>
        <select id="stats-month" class="field-input" (change)="onMonth($event)">
          @for (name of monthNames; track $index) {
            <option [value]="$index + 1" [selected]="$index + 1 === month()">{{ name }}</option>
          }
        </select>
      </div>
      <div class="space-y-1">
        <label for="stats-year" class="block text-sm font-medium">Année</label>
        <select id="stats-year" class="field-input" (change)="onYear($event)">
          @for (y of years; track y) { <option [value]="y" [selected]="y === year()">{{ y }}</option> }
        </select>
      </div>
      @if (monthError()) {
        <p class="w-full text-sm text-danger" data-testid="stats-month-error">Sélectionnez un mois passé ou le mois en cours.</p>
      }
    </fieldset>

    @switch (state().kind) {
      @case ('idle') { }
      @case ('loading') { <app-loading-state testId="stats-loading" [rows]="6" /> }
      @case ('error') { <app-error-state [message]="errorMessage()" testId="stats-error" (retry)="reload()" /> }
      @case ('success') {
        @if (stats(); as s) {
          <div class="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
            <app-stat-card label="Emprunts" [value]="s.loans" icon="arrow-up-right" tone="primary" testId="stats-card-loans" />
            <app-stat-card label="Retours" [value]="s.returns" icon="arrow-down-left" tone="brand" testId="stats-card-returns" />
            <app-stat-card label="Prolongations" [value]="s.renewals" icon="calendar" tone="info" testId="stats-card-renewals" />
            <app-stat-card label="Réservations" [value]="s.reservations" icon="bookmark" tone="accent" testId="stats-card-reservations" />
            <app-stat-card label="Nouveaux adhérents" [value]="s.newMembers" icon="user" tone="success" testId="stats-card-new-members" />
            <app-stat-card label="Adhérents ayant emprunté" [value]="s.activeBorrowers" icon="users" tone="primary" testId="stats-card-active-borrowers" />
          </div>
          <div class="mt-6 grid gap-6 lg:grid-cols-2">
            <section class="card p-4">
              <table class="w-full text-left text-sm">
                <caption class="mb-2 text-left font-semibold">Les 10 livres les plus empruntés</caption>
                <thead class="bg-surface-alt text-xs uppercase tracking-wide text-muted"><tr><th scope="col" class="px-4 py-3 font-semibold">#</th><th scope="col" class="px-4 py-3 font-semibold">Titre</th>
                  <th scope="col" class="px-4 py-3 font-semibold text-right">Emprunts</th></tr></thead>
                <tbody>
                  @for (book of s.topBooks; track book.bookId; let i = $index) {
                    <tr class="border-t border-border" data-testid="stats-top-books-row">
                      <td class="px-4 py-3">{{ i + 1 }}</td>
                      <td class="px-4 py-3">{{ book.title }}<span class="block text-xs text-muted">{{ book.authors }}</span></td>
                      <td class="px-4 py-3 text-right tabular-nums">{{ book.loans }}</td>
                    </tr>
                  } @empty {
                    <tr><td colspan="3" class="px-3 py-4 text-muted" data-testid="stats-empty">Aucun emprunt sur cette période.</td></tr>
                  }
                </tbody>
              </table>
            </section>
            <section class="card p-4">
              <table class="w-full text-left text-sm">
                <caption class="mb-2 text-left font-semibold">Rotation par catégorie</caption>
                <thead class="bg-surface-alt text-xs uppercase tracking-wide text-muted"><tr><th scope="col" class="px-4 py-3 font-semibold">Catégorie</th>
                  <th scope="col" class="px-4 py-3 font-semibold text-right">Emprunts</th><th scope="col" class="px-4 py-3 font-semibold text-right">Exemplaires</th>
                  <th scope="col" class="px-4 py-3 font-semibold text-right">Taux</th></tr></thead>
                <tbody>
                  @for (row of s.rotation; track row.category) {
                    <tr class="border-t border-border" data-testid="stats-rotation-row" [attr.data-row-id]="row.category">
                      <td class="px-4 py-3">{{ row.category }}</td>
                      <td class="px-4 py-3 text-right tabular-nums">{{ row.loans }}</td>
                      <td class="px-4 py-3 text-right tabular-nums">{{ row.copies }}</td>
                      <td class="px-4 py-3 text-right tabular-nums">{{ rate(row.rate) }}</td>
                    </tr>
                  }
                </tbody>
              </table>
            </section>
          </div>
          <section class="card mt-6 p-4">
            <h2 class="mb-2 font-semibold">Encaissements</h2>
            <p class="text-sm">
              Espèces <span class="tabular-nums" data-testid="stats-collections-cash">{{ s.collections.cash | euro }}</span> ·
              Carte <span class="tabular-nums" data-testid="stats-collections-card">{{ s.collections.card | euro }}</span> ·
              Chèque <span class="tabular-nums" data-testid="stats-collections-check">{{ s.collections.check | euro }}</span> ·
              Remises <span class="tabular-nums" data-testid="stats-waived-total">{{ s.collections.waived | euro }}</span>
            </p>
          </section>
        }
      }
    }
  `,
})
export class StatisticsPage {
  private readonly pilotage = inject(PilotageService);
  private readonly toasts = inject(ToastService);
  protected readonly monthNames = MONTHS;
  private readonly current = todayInParis();
  protected readonly years = Array.from({ length: 5 }, (_, i) => Number(this.current.slice(0, 4)) - i);
  protected readonly year = signal(Number(this.current.slice(0, 4)));
  protected readonly month = signal(Number(this.current.slice(5, 7)));
  protected readonly exporting = signal(false);
  private readonly tick = signal(0);

  protected readonly monthKey = computed(() => `${this.year()}-${String(this.month()).padStart(2, '0')}`);
  /** AC-042-4 : pas de mois futur. */
  protected readonly monthError = computed(() => this.monthKey() > this.current.slice(0, 7));
  private readonly request = computed(() => {
    this.tick();
    return this.monthError() ? null : { month: this.monthKey() };
  });
  protected readonly state = viewState(this.request, ({ month }) => this.pilotage.statistics(month));
  protected readonly stats = computed(() => dataOf(this.state()));
  protected readonly errorMessage = computed(() => {
    const state = this.state();
    return state.kind === 'error' && state.error instanceof ApiError ? commonErrorMessage(state.error) : GENERIC_MESSAGES.unexpected;
  });

  protected rate(value: number): string {
    return RATE.format(value);
  }

  protected onMonth(event: Event): void {
    this.month.set(Number((event.target as HTMLSelectElement).value));
  }

  protected onYear(event: Event): void {
    this.year.set(Number((event.target as HTMLSelectElement).value));
  }

  protected export(): void {
    if (this.monthError()) {
      return;
    }
    const month = this.monthKey();
    this.exporting.set(true);
    this.pilotage.exportStatistics(month).subscribe({
      next: (blob) => {
        this.exporting.set(false);
        const url = URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = `statistiques-${month}.csv`;
        link.click();
        URL.revokeObjectURL(url);
      },
      error: () => {
        this.exporting.set(false);
        this.toasts.error('L’export a échoué. Réessayez.');
      },
    });
  }

  protected reload(): void {
    this.tick.update((value) => value + 1);
  }
}
