import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { ApiError } from '../../../core/http/api-error';
import { GENERIC_MESSAGES } from '../../../core/http/messages';
import { dataOf, viewState } from '../../../shared/data/view-state';
import { DEFAULT_PAGE_SIZE } from '../../../shared/models/page.model';
import { EuroPipe } from '../../../shared/pipes/euro.pipe';
import { ButtonComponent } from '../../../shared/ui/button.component';
import { PageHeaderComponent } from '../../../shared/ui/page-header.component';
import { PaginationComponent } from '../../../shared/ui/pagination.component';
import { ErrorStateComponent, LoadingStateComponent } from '../../../shared/ui/view-states.component';
import { memberLabel } from '../../emprunts/data-access/loan-messages';
import { CollectFinesDialogComponent, CollectTarget } from '../components/collect-fines-dialog.component';
import { FINE_MESSAGES, fineErrorMessage } from '../data-access/fine-messages';
import { Debtor, DebtorSort } from '../data-access/fine.models';
import { FineService } from '../data-access/fine.service';

/** ECR-35 — Pénalités impayées (US-040) : débiteurs par total décroissant, « Bloqué » au-delà du seuil (RG-35). */
@Component({
  selector: 'app-unpaid-fines-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, EuroPipe, ButtonComponent, PageHeaderComponent, PaginationComponent, LoadingStateComponent,
    ErrorStateComponent, CollectFinesDialogComponent],
  template: `
    <app-page-header title="Pénalités impayées" />
    @switch (state().kind) {
      @case ('loading') { <app-loading-state testId="unpaid-loading" /> }
      @case ('error') { <app-error-state [message]="errorMessage()" testId="unpaid-error" (retry)="reload()" /> }
      @case ('success') {
        @if (list(); as data) {
          <p class="mb-4 text-sm font-medium" data-testid="unpaid-summary">
            {{ data.memberCount }} adhérent(s) · {{ data.totalAmount | euro }}
          </p>
          @if (data.page.content.length === 0) {
            <p class="card p-8 text-center" data-testid="unpaid-empty">{{ messages.none }}</p>
          } @else {
            <div class="card overflow-x-auto">
              <table class="w-full text-left text-sm">
                <caption class="sr-only">Adhérents ayant des pénalités impayées</caption>
                <thead class="bg-surface-alt text-xs uppercase tracking-wide text-muted"><tr>
                  <th scope="col" class="px-4 py-3 font-semibold">Numéro</th>
                  <th scope="col" class="px-4 py-3 font-semibold" [attr.aria-sort]="sort() === 'name' ? 'ascending' : 'none'">
                    <button type="button" class="font-semibold" (click)="setSort('name')">Adhérent</button></th>
                  <th scope="col" class="px-4 py-3 font-semibold">Contact</th>
                  <th scope="col" class="px-4 py-3 font-semibold" [attr.aria-sort]="sort() === 'count' ? 'descending' : 'none'">
                    <button type="button" class="font-semibold" (click)="setSort('count')">Nb</button></th>
                  <th scope="col" class="px-4 py-3 font-semibold" [attr.aria-sort]="sort() === 'total' ? 'descending' : 'none'">
                    <button type="button" class="font-semibold" (click)="setSort('total')">Total {{ sort() === 'total' ? '▼' : '' }}</button></th>
                  <th scope="col" class="px-4 py-3 font-semibold">Situation</th><th scope="col" class="px-4 py-3 font-semibold"><span class="sr-only">Actions</span></th>
                </tr></thead>
                <tbody>
                  @for (row of data.page.content; track row.memberNumber) {
                    <tr class="border-t border-border" data-testid="unpaid-row" [attr.data-row-id]="row.memberNumber">
                      <td class="px-4 py-3 font-mono">
                        <a [routerLink]="['/gestion/adherents', row.memberNumber]" class="text-primary hover:underline"
                           data-testid="unpaid-member-link">{{ row.memberNumber }}</a>
                      </td>
                      <td class="px-4 py-3">{{ name(row) }}</td>
                      <td class="px-4 py-3 text-xs">{{ row.phone ?? '—' }} · {{ row.email ?? '—' }}</td>
                      <td class="px-4 py-3 tabular-nums" data-testid="unpaid-count">{{ row.fineCount }}</td>
                      <td class="px-4 py-3 text-right tabular-nums font-medium" data-testid="unpaid-total">{{ row.total | euro }}</td>
                      <td class="px-4 py-3">
                        @if (row.blocked) {
                          <span class="rounded-full bg-danger-soft px-2 py-0.5 text-xs font-medium text-danger" data-testid="unpaid-blocked-badge">Bloqué</span>
                        }
                      </td>
                      <td class="px-4 py-3 text-right">
                        <button app-button type="button" variant="secondary" data-testid="unpaid-collect-action" (click)="collect(row)">Encaisser</button>
                      </td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
            <div class="mt-4">
              <app-pagination [page]="page()" [size]="size()" [totalPages]="data.page.totalPages" testId="unpaid-pagination"
                              (pageChange)="page.set($event)" (sizeChange)="size.set($event); page.set(0)" />
            </div>
          }
        }
      }
    }
    <app-collect-fines-dialog [target]="target()" (closed)="onClosed($event)" />
  `,
})
export class UnpaidFinesPage {
  private readonly fines = inject(FineService);
  protected readonly messages = FINE_MESSAGES;
  protected readonly sort = signal<DebtorSort>('total');
  protected readonly page = signal(0);
  protected readonly size = signal(DEFAULT_PAGE_SIZE);
  private readonly reloadTick = signal(0);
  private readonly request = computed(() => {
    this.reloadTick();
    return { sort: this.sort(), page: this.page(), size: this.size() };
  });
  protected readonly state = viewState(this.request, ({ sort, page, size }) => this.fines.debtors(sort, page, size));
  protected readonly list = computed(() => dataOf(this.state()));
  protected readonly errorMessage = computed(() => {
    const state = this.state();
    return state.kind === 'error' && state.error instanceof ApiError ? fineErrorMessage(state.error) : GENERIC_MESSAGES.unexpected;
  });
  protected readonly target = signal<CollectTarget | null>(null);

  protected name(row: Debtor): string {
    return row.lastName ? `${row.lastName.toUpperCase()} ${row.firstName ?? ''}` : '—';
  }

  protected setSort(sort: DebtorSort): void {
    this.sort.set(sort);
    this.page.set(0);
  }

  protected collect(row: Debtor): void {
    this.target.set({ memberNumber: row.memberNumber, label: memberLabel(row) });
  }

  protected onClosed(collected: boolean): void {
    this.target.set(null);
    if (collected) {
      this.reload();
    }
  }

  protected reload(): void {
    this.reloadTick.update((tick) => tick + 1);
  }
}
