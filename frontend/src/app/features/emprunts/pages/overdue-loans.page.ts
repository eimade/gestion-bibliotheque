import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';

import { ApiError } from '../../../core/http/api-error';
import { GENERIC_MESSAGES } from '../../../core/http/messages';
import { dataOf, viewState } from '../../../shared/data/view-state';
import { DEFAULT_PAGE_SIZE } from '../../../shared/models/page.model';
import { DateFrPipe } from '../../../shared/pipes/date-fr.pipe';
import { EuroPipe } from '../../../shared/pipes/euro.pipe';
import { MenuButtonComponent, MenuItem } from '../../../shared/ui/menu-button.component';
import { PageHeaderComponent } from '../../../shared/ui/page-header.component';
import { PaginationComponent } from '../../../shared/ui/pagination.component';
import { ErrorStateComponent, LoadingStateComponent } from '../../../shared/ui/view-states.component';
import { DeclareLostDialogComponent, LossTarget } from '../components/declare-lost-dialog.component';
import { LOAN_MESSAGES, loanErrorMessage, memberLabel } from '../data-access/loan-messages';
import { OverdueLoan, OverdueSort } from '../data-access/loan.models';
import { LoanService } from '../data-access/loan.service';

/** ECR-26 — Emprunts en retard (US-028) : retard décroissant par défaut, filtre « au moins N jours ». */
@Component({
  selector: 'app-overdue-loans-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, DateFrPipe, EuroPipe, MenuButtonComponent, PageHeaderComponent, PaginationComponent,
    LoadingStateComponent, ErrorStateComponent, DeclareLostDialogComponent],
  template: `
    <app-page-header [title]="'Emprunts en retard (' + total() + ')'" testId="overdue-count" />
    <form class="mb-4 flex flex-wrap items-end gap-2" (submit)="$event.preventDefault(); apply()">
      <div class="space-y-1">
        <label for="overdue-min-days" class="block text-sm font-medium">Au moins … jours de retard</label>
        <input id="overdue-min-days" type="number" min="1" class="field-input w-28" data-testid="overdue-min-days-input"
               [value]="minDaysText()" [attr.aria-invalid]="minDaysError() ? true : null" (input)="onMinDays($event)" />
      </div>
      <button type="submit" class="rounded-xl border border-border bg-surface px-4 py-2.5 text-sm font-semibold shadow-sm hover:bg-primary-soft hover:text-primary"
              data-testid="overdue-apply-filter">Appliquer</button>
      @if (minDaysError()) {
        <p class="w-full text-sm text-danger" data-testid="overdue-min-days-error">{{ messages.minDays }}</p>
      }
    </form>
    @switch (state().kind) {
      @case ('loading') { <app-loading-state testId="overdue-loading" /> }
      @case ('error') { <app-error-state [message]="errorMessage()" testId="overdue-error" (retry)="reload()" /> }
      @case ('success') {
        @if (rows().length === 0) {
          <p class="card p-8 text-center" data-testid="overdue-empty">
            {{ minDays() > 1 ? messages.noOverdueFiltered(minDays()) : messages.noOverdue }}
          </p>
        } @else {
          <div class="card overflow-x-auto">
            <table class="w-full text-left text-sm">
              <caption class="sr-only">Emprunts en retard</caption>
              <thead class="bg-surface-alt text-xs uppercase tracking-wide text-muted"><tr>
                <th scope="col" class="px-4 py-3 font-semibold">Titre</th><th scope="col" class="px-4 py-3 font-semibold">N° ex.</th>
                <th scope="col" class="px-4 py-3 font-semibold" [attr.aria-sort]="sort() === 'memberName' ? 'ascending' : 'none'">
                  <button type="button" class="font-semibold" (click)="setSort('memberName')">Adhérent / contact</button></th>
                <th scope="col" class="px-4 py-3 font-semibold" [attr.aria-sort]="sort() === 'dueDate' ? 'descending' : 'none'">
                  <button type="button" class="font-semibold" (click)="setSort('dueDate')">À rendre</button></th>
                <th scope="col" class="px-4 py-3 font-semibold" [attr.aria-sort]="sort() === 'overdueDays' ? 'descending' : 'none'">
                  <button type="button" class="font-semibold" (click)="setSort('overdueDays')">Retard {{ sort() === 'overdueDays' ? '▼' : '' }}</button></th>
                <th scope="col" class="px-4 py-3 font-semibold">Pén. prov.</th><th scope="col" class="px-4 py-3 font-semibold"><span class="sr-only">Actions</span></th>
              </tr></thead>
              <tbody>
                @for (loan of rows(); track loan.loanId) {
                  <tr class="border-t border-border align-top" data-testid="overdue-row" [attr.data-row-id]="loan.copyNumber">
                    <td class="px-4 py-3">{{ loan.title }}</td>
                    <td class="px-4 py-3 font-mono">{{ loan.copyNumber }}</td>
                    <td class="px-4 py-3">
                      @if (loan.member; as member) {
                        <a [routerLink]="['/gestion/adherents', member.memberNumber]" class="text-primary hover:underline"
                           data-testid="overdue-member-link">{{ label(loan) }}</a>
                        <p class="text-xs text-muted">{{ member.phone ?? '—' }} · {{ member.email ?? '—' }}</p>
                      }
                    </td>
                    <td class="px-4 py-3 tabular-nums">{{ loan.dueDate | dateFr }}</td>
                    <td class="px-4 py-3 tabular-nums" data-testid="overdue-days">{{ loan.overdueDays }} j</td>
                    <td class="px-4 py-3 tabular-nums" data-testid="overdue-provisional-fine">{{ loan.provisionalFine | euro }}</td>
                    <td class="px-4 py-3 text-right">
                      <app-menu-button [label]="'Actions pour l’emprunt ' + loan.copyNumber" [items]="menu" testId="overdue-row-menu"
                                       (selected)="onAction(loan, $event)" />
                    </td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
          <div class="mt-4">
            <app-pagination [page]="page()" [size]="size()" [totalPages]="totalPages()" testId="overdue-pagination"
                            (pageChange)="page.set($event)" (sizeChange)="size.set($event); page.set(0)" />
          </div>
        }
      }
    }
    <app-declare-lost-dialog [target]="lossTarget()" (declared)="lossTarget.set(null); reload()" (cancelled)="lossTarget.set(null)" />
  `,
})
export class OverdueLoansPage {
  private readonly loans = inject(LoanService);
  private readonly router = inject(Router);
  protected readonly messages = LOAN_MESSAGES;
  protected readonly menu: readonly MenuItem[] = [
    { id: 'member', label: 'Voir la fiche adhérent', testId: 'overdue-member-action' },
    { id: 'lost', label: 'Déclarer perdu', testId: 'overdue-declare-lost-action' },
  ];
  protected readonly minDays = signal(1);
  protected readonly minDaysText = signal('1');
  protected readonly minDaysError = signal(false);
  protected readonly sort = signal<OverdueSort>('overdueDays');
  protected readonly page = signal(0);
  protected readonly size = signal(DEFAULT_PAGE_SIZE);
  private readonly reloadTick = signal(0);
  private readonly request = computed(() => {
    this.reloadTick();
    return { minDays: this.minDays(), sort: this.sort(), page: this.page(), size: this.size() };
  });
  protected readonly state = viewState(this.request, (query) => this.loans.overdue(query.minDays, query.sort, query.page, query.size));
  protected readonly rows = computed(() => dataOf(this.state())?.content ?? []);
  protected readonly total = computed(() => dataOf(this.state())?.totalElements ?? 0);
  protected readonly totalPages = computed(() => dataOf(this.state())?.totalPages ?? 0);
  protected readonly errorMessage = computed(() => {
    const state = this.state();
    return state.kind === 'error' && state.error instanceof ApiError ? loanErrorMessage(state.error) : GENERIC_MESSAGES.unexpected;
  });
  protected readonly lossTarget = signal<LossTarget | null>(null);

  protected label(loan: OverdueLoan): string {
    return memberLabel(loan.member);
  }

  protected onMinDays(event: Event): void {
    this.minDaysText.set((event.target as HTMLInputElement).value);
    this.minDaysError.set(false);
  }

  protected apply(): void {
    const value = Number(this.minDaysText());
    if (!Number.isInteger(value) || value < 1) {
      this.minDaysError.set(true);
      return;
    }
    this.minDays.set(value);
    this.page.set(0);
  }

  protected setSort(sort: OverdueSort): void {
    this.sort.set(sort);
    this.page.set(0);
  }

  protected onAction(loan: OverdueLoan, action: string): void {
    if (action === 'member' && loan.member) {
      void this.router.navigate(['/gestion/adherents', loan.member.memberNumber]);
    } else if (action === 'lost') {
      this.lossTarget.set({ loanId: loan.loanId, title: loan.title, copyNumber: loan.copyNumber, dueDate: loan.dueDate,
        member: loan.member });
    }
  }

  protected reload(): void {
    this.reloadTick.update((tick) => tick + 1);
  }
}
