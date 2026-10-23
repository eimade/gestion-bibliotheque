import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';

import { ApiError } from '../../../core/http/api-error';
import { GENERIC_MESSAGES } from '../../../core/http/messages';
import { dataOf, viewState } from '../../../shared/data/view-state';
import { DEFAULT_PAGE_SIZE } from '../../../shared/models/page.model';
import { DateTimeFrPipe, todayInParis } from '../../../shared/pipes/date-fr.pipe';
import { PageHeaderComponent } from '../../../shared/ui/page-header.component';
import { PaginationComponent } from '../../../shared/ui/pagination.component';
import { EmptyStateComponent, ErrorStateComponent, LoadingStateComponent } from '../../../shared/ui/view-states.component';
import { commonErrorMessage } from '../../authentification/data-access/auth-messages';
import { AUDIT_ACTION_LABELS, AUDIT_OBJECT_LABELS, AuditAction, AuditEntry, AuditQuery } from '../data-access/pilotage.models';
import { PilotageService } from '../data-access/pilotage.service';

/** Date `AAAA-MM-JJ` décalée de `days` jours. */
function shift(date: string, days: number): string {
  const value = new Date(`${date}T00:00:00Z`);
  value.setUTCDate(value.getUTCDate() + days);
  return value.toISOString().slice(0, 10);
}

/** ECR-40 — Journal d'audit (US-044) : lecture seule, 7 derniers jours par défaut, lignes dépliables. */
@Component({
  selector: 'app-audit-log-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DateTimeFrPipe, PageHeaderComponent, PaginationComponent, LoadingStateComponent, EmptyStateComponent,
    ErrorStateComponent],
  template: `
    <app-page-header title="Journal d’audit" />
    <form class="card mb-4 grid gap-3 p-4 md:grid-cols-3 lg:grid-cols-6 lg:items-end"
          (submit)="$event.preventDefault(); apply()">
      <div class="space-y-1 lg:col-span-1">
        <label for="audit-object" class="block text-sm font-medium">Objet</label>
        <input id="audit-object" class="field-input" placeholder="A000123" data-testid="audit-filter-object" [value]="form().object"
               (input)="set('object', $event)" />
      </div>
      <div class="space-y-1">
        <label for="audit-author" class="block text-sm font-medium">Auteur</label>
        <input id="audit-author" class="field-input" data-testid="audit-filter-author" [value]="form().actor" (input)="set('actor', $event)" />
      </div>
      <div class="space-y-1">
        <label for="audit-action" class="block text-sm font-medium">Action</label>
        <select id="audit-action" class="field-input" data-testid="audit-filter-action" (change)="setAction($event)">
          <option value="">Toutes</option>
          @for (action of actions; track action) { <option [value]="action">{{ actionLabels[action] }}</option> }
        </select>
      </div>
      <div class="space-y-1">
        <label for="audit-from" class="block text-sm font-medium">Du</label>
        <input id="audit-from" type="date" class="field-input" data-testid="audit-filter-from" [value]="form().from" (input)="set('from', $event)" />
      </div>
      <div class="space-y-1">
        <label for="audit-to" class="block text-sm font-medium">Au</label>
        <input id="audit-to" type="date" class="field-input" data-testid="audit-filter-to" [value]="form().to" (input)="set('to', $event)" />
      </div>
      <button type="submit" class="rounded-md bg-primary px-4 py-2 text-sm font-medium text-surface hover:bg-primary-hover"
              data-testid="audit-filter-submit">Filtrer</button>
      @if (filterError()) {
        <p class="text-sm text-danger md:col-span-3 lg:col-span-6" data-testid="audit-filter-error">{{ filterError() }}</p>
      }
    </form>

    @switch (state().kind) {
      @case ('loading') { <app-loading-state testId="audit-loading" /> }
      @case ('error') { <app-error-state [message]="errorMessage()" testId="audit-error" (retry)="reload()" /> }
      @case ('success') {
        @if (rows().length === 0) {
          <app-empty-state message="Aucune entrée ne correspond à ces critères." testId="audit-empty" />
        } @else {
          <div class="card overflow-x-auto">
            <table class="w-full text-left text-sm">
              <caption class="sr-only">Journal d’audit, du plus récent au plus ancien</caption>
              <thead class="bg-surface-alt text-xs uppercase tracking-wide text-muted"><tr>
                <th scope="col" class="px-4 py-3 font-semibold" aria-sort="descending">Date-heure ▼</th><th scope="col" class="px-4 py-3 font-semibold">Auteur</th>
                <th scope="col" class="px-4 py-3 font-semibold">Action</th><th scope="col" class="px-4 py-3 font-semibold">Objet</th>
                <th scope="col" class="px-4 py-3 font-semibold"><span class="sr-only">Détail</span></th>
              </tr></thead>
              <tbody>
                @for (entry of rows(); track entry.id) {
                  <tr class="border-t border-border align-top" data-testid="audit-row">
                    <td class="px-4 py-3 tabular-nums">{{ entry.occurredAt | dateTimeFr }}</td>
                    <td class="px-4 py-3">{{ entry.actorLabel }}</td>
                    <td class="px-4 py-3">{{ actionLabels[entry.action] }}</td>
                    <td class="px-4 py-3">{{ objectLabel(entry) }} {{ entry.objectRef }}</td>
                    <td class="px-4 py-3 text-right">
                      @if (entry.details) {
                        <button type="button" class="text-primary underline" data-testid="audit-row-toggle"
                                [attr.aria-expanded]="expanded() === entry.id" [attr.aria-controls]="'audit-detail-' + entry.id"
                                (click)="toggle(entry.id)">Détail</button>
                      }
                    </td>
                  </tr>
                  @if (expanded() === entry.id && entry.details) {
                    <tr [id]="'audit-detail-' + entry.id" data-testid="audit-row-detail">
                      <td colspan="5" class="bg-surface-alt px-3 py-2">
                        <ul class="space-y-1 text-xs">
                          @for (line of detailLines(entry); track $index) { <li class="font-mono">{{ line }}</li> }
                        </ul>
                      </td>
                    </tr>
                  }
                }
              </tbody>
            </table>
          </div>
          <div class="mt-4">
            <app-pagination [page]="query().page" [size]="query().size" [totalPages]="totalPages()" testId="audit-pagination"
                            (pageChange)="changePage($event)" (sizeChange)="changeSize($event)" />
          </div>
        }
      }
    }
  `,
})
export class AuditLogPage {
  private readonly pilotage = inject(PilotageService);
  protected readonly actions = Object.keys(AUDIT_ACTION_LABELS) as AuditAction[];
  protected readonly actionLabels = AUDIT_ACTION_LABELS;
  private readonly today = todayInParis();

  protected readonly form = signal({ object: '', actor: '', action: null as AuditAction | null, from: shift(this.today, -6), to: this.today });
  protected readonly query = signal<AuditQuery>({ ...this.form(), page: 0, size: DEFAULT_PAGE_SIZE });
  protected readonly filterError = signal<string | null>(null);
  protected readonly expanded = signal<number | null>(null);
  private readonly reloadTick = signal(0);
  private readonly request = computed(() => {
    this.reloadTick();
    return { ...this.query() };
  });
  protected readonly state = viewState(this.request, (query) => this.pilotage.audit(query));
  protected readonly rows = computed(() => dataOf(this.state())?.content ?? []);
  protected readonly totalPages = computed(() => dataOf(this.state())?.totalPages ?? 0);
  protected readonly errorMessage = computed(() => {
    const state = this.state();
    return state.kind === 'error' && state.error instanceof ApiError ? commonErrorMessage(state.error) : GENERIC_MESSAGES.unexpected;
  });

  protected set(field: 'object' | 'actor' | 'from' | 'to', event: Event): void {
    const value = (event.target as HTMLInputElement).value;
    this.form.update((form) => ({ ...form, [field]: value }));
  }

  protected setAction(event: Event): void {
    const value = (event.target as HTMLSelectElement).value;
    this.form.update((form) => ({ ...form, action: value ? (value as AuditAction) : null }));
  }

  /** Du ≤ Au, et pas plus de 12 mois en arrière (NFR-08). */
  protected apply(): void {
    const form = this.form();
    if (!form.from || !form.to || form.from > form.to) {
      this.filterError.set('La date de début doit être antérieure ou égale à la date de fin.');
      return;
    }
    const limit = `${Number(this.today.slice(0, 4)) - 1}${this.today.slice(4)}`;
    if (form.from < limit) {
      this.filterError.set('Le journal est conservé 12 mois : choisissez une date plus récente.');
      return;
    }
    this.filterError.set(null);
    this.query.set({ ...form, page: 0, size: this.query().size });
  }

  protected changePage(page: number): void {
    this.query.update((query) => ({ ...query, page }));
  }

  protected changeSize(size: number): void {
    this.query.update((query) => ({ ...query, size, page: 0 }));
  }

  protected toggle(id: number): void {
    this.expanded.update((current) => (current === id ? null : id));
  }

  protected objectLabel(entry: AuditEntry): string {
    return AUDIT_OBJECT_LABELS[entry.objectType] ?? entry.objectType;
  }

  /** Valeurs avant/après : « champ : avant → après », sinon « champ : valeur ». */
  protected detailLines(entry: AuditEntry): string[] {
    return Object.entries(entry.details ?? {}).map(([field, value]) => {
      if (value && typeof value === 'object' && 'before' in value && 'after' in value) {
        const change = value as { before: unknown; after: unknown };
        return `${field} : ${this.text(change.before)} → ${this.text(change.after)}`;
      }
      return `${field} : ${this.text(value)}`;
    });
  }

  protected reload(): void {
    this.reloadTick.update((tick) => tick + 1);
  }

  private text(value: unknown): string {
    if (value === null || value === undefined) {
      return '—';
    }
    return typeof value === 'object' ? JSON.stringify(value) : String(value);
  }
}
