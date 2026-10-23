import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';

import { ACCOUNT_STATUS_DISPLAY, ROLE_LABELS } from '../../../shared/models/statuses';
import { MenuButtonComponent, MenuItem } from '../../../shared/ui/menu-button.component';
import { StatusBadgeComponent } from '../../../shared/ui/status-badge.component';
import { SortDirection, StaffAccount, StaffAccountSortField } from '../data-access/account.models';
import { AUTH_MESSAGES } from '../data-access/auth-messages';

export type StaffAccountAction = 'disable' | 'enable' | 'reset';

export interface StaffAccountActionEvent {
  readonly account: StaffAccount;
  readonly action: StaffAccountAction;
}

/** Tableau des comptes du personnel (ECR-05), présentationnel. */
@Component({
  selector: 'app-staff-account-table',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MenuButtonComponent, StatusBadgeComponent],
  template: `
    <div class="card overflow-x-auto">
      <table class="w-full text-left text-sm">
        <caption class="sr-only">Comptes du personnel</caption>
        <thead class="bg-surface-alt text-xs uppercase tracking-wide text-muted">
          <tr>
            <th scope="col" class="px-4 py-3" [attr.aria-sort]="ariaSort('lastName')">
              <button type="button" class="font-semibold" (click)="sortChange.emit('lastName')">
                Nom {{ sortIcon('lastName') }}
              </button>
            </th>
            <th scope="col" class="px-4 py-3 font-semibold">E-mail</th>
            <th scope="col" class="px-4 py-3" [attr.aria-sort]="ariaSort('role')">
              <button type="button" class="font-semibold" (click)="sortChange.emit('role')">Rôle {{ sortIcon('role') }}</button>
            </th>
            <th scope="col" class="px-4 py-3" [attr.aria-sort]="ariaSort('status')">
              <button type="button" class="font-semibold" (click)="sortChange.emit('status')">
                Statut {{ sortIcon('status') }}
              </button>
            </th>
            <th scope="col" class="px-4 py-3"><span class="sr-only">Actions</span></th>
          </tr>
        </thead>
        <tbody>
          @for (account of accounts(); track account.id) {
            <tr class="border-t border-border" data-testid="staff-accounts-row" [attr.data-row-id]="account.email">
              <td class="px-4 py-3">
                {{ account.lastName.toUpperCase() }} {{ account.firstName }}
                @if (account.me) {
                  <span class="text-muted">(vous)</span>
                  <p class="text-xs text-muted" data-testid="staff-accounts-self-disable-reason">{{ selfDeactivation }}</p>
                }
              </td>
              <td class="px-4 py-3">{{ account.email }}</td>
              <td class="px-4 py-3">{{ roleLabels[account.role] }}</td>
              <td class="px-4 py-3">
                <app-status-badge [display]="statusDisplay[account.status]" [value]="account.status" />
              </td>
              <td class="px-4 py-3 text-right">
                <app-menu-button [label]="'Actions pour ' + account.firstName + ' ' + account.lastName"
                                 [items]="menuFor(account)" testId="staff-accounts-row-menu"
                                 (selected)="emitAction(account, $event)" />
              </td>
            </tr>
          }
        </tbody>
      </table>
    </div>
  `,
})
export class StaffAccountTableComponent {
  readonly accounts = input.required<readonly StaffAccount[]>();
  readonly sortField = input.required<StaffAccountSortField>();
  readonly sortDirection = input.required<SortDirection>();
  readonly sortChange = output<StaffAccountSortField>();
  readonly action = output<StaffAccountActionEvent>();

  protected readonly roleLabels = ROLE_LABELS;
  protected readonly statusDisplay = ACCOUNT_STATUS_DISPLAY;
  protected readonly selfDeactivation = AUTH_MESSAGES.selfDeactivation;

  protected ariaSort(field: StaffAccountSortField): 'ascending' | 'descending' | 'none' {
    if (this.sortField() !== field) {
      return 'none';
    }
    return this.sortDirection() === 'asc' ? 'ascending' : 'descending';
  }

  protected sortIcon(field: StaffAccountSortField): string {
    if (this.sortField() !== field) {
      return '';
    }
    return this.sortDirection() === 'asc' ? '▲' : '▼';
  }

  /** AC-006-2 : pas de réinitialisation pour un compte désactivé ; AC-005-3 : pas d'auto-désactivation. */
  protected menuFor(account: StaffAccount): readonly MenuItem[] {
    if (account.status === 'DISABLED') {
      return [{ id: 'enable', label: 'Réactiver', testId: 'staff-accounts-enable-action' }];
    }
    return [
      { id: 'disable', label: 'Désactiver', testId: 'staff-accounts-disable-action', disabled: account.me },
      { id: 'reset', label: 'Réinitialiser le mot de passe', testId: 'staff-accounts-reset-password-action' },
    ];
  }

  protected emitAction(account: StaffAccount, id: string): void {
    this.action.emit({ account, action: id as StaffAccountAction });
  }
}
