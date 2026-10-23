import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { catchError, map, of, startWith, switchMap } from 'rxjs';

import { ApiError } from '../../../core/http/api-error';
import { GENERIC_MESSAGES } from '../../../core/http/messages';
import { ToastService } from '../../../core/notifications/toast.service';
import { AccountStatus } from '../../../shared/models/statuses';
import { DEFAULT_PAGE_SIZE, Page } from '../../../shared/models/page.model';
import { ButtonComponent } from '../../../shared/ui/button.component';
import { ConfirmDialogComponent } from '../../../shared/ui/confirm-dialog.component';
import { PageHeaderComponent } from '../../../shared/ui/page-header.component';
import { PaginationComponent } from '../../../shared/ui/pagination.component';
import { SearchInputComponent } from '../../../shared/ui/search-input.component';
import {
  EmptyStateComponent,
  ErrorStateComponent,
  LoadingStateComponent,
} from '../../../shared/ui/view-states.component';
import { ResetPasswordDialogComponent } from '../components/reset-password-dialog.component';
import { StaffAccountFormDialogComponent } from '../components/staff-account-form-dialog.component';
import { StaffAccountActionEvent, StaffAccountTableComponent } from '../components/staff-account-table.component';
import {
  CreateStaffAccountRequest,
  StaffAccount,
  StaffAccountQuery,
  StaffAccountSortField,
  StaffRole,
} from '../data-access/account.models';
import { AUTH_MESSAGES, commonErrorMessage, staffAccountErrorMessage } from '../data-access/auth-messages';
import { StaffAccountService } from '../data-access/staff-account.service';

type ViewState =
  | { readonly kind: 'loading' }
  | { readonly kind: 'error'; readonly message: string }
  | { readonly kind: 'success'; readonly page: Page<StaffAccount> };

/** ECR-05 — Comptes du personnel (US-004, US-005, US-006), ADMIN. */
@Component({
  selector: 'app-staff-accounts-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    PageHeaderComponent,
    ButtonComponent,
    SearchInputComponent,
    LoadingStateComponent,
    EmptyStateComponent,
    ErrorStateComponent,
    StaffAccountTableComponent,
    PaginationComponent,
    StaffAccountFormDialogComponent,
    ConfirmDialogComponent,
    ResetPasswordDialogComponent,
  ],
  template: `
    <app-page-header title="Comptes du personnel">
      <button app-button type="button" data-testid="staff-accounts-new-button" (click)="openCreate()">
        + Nouveau compte
      </button>
    </app-page-header>

    <div class="mb-4 grid gap-4 md:grid-cols-[2fr_1fr_1fr]">
      <app-search-input label="Rechercher" placeholder="nom ou e-mail" testId="staff-accounts-search"
                        (search)="updateQuery({ q: $event })" />
      <div class="space-y-1">
        <label for="staff-role-filter" class="block text-sm font-medium">Rôle</label>
        <select id="staff-role-filter" class="field-input" data-testid="staff-accounts-role-filter"
                (change)="onRoleFilter($event)">
          <option value="">Tous</option>
          <option value="LIBRARIAN">Bibliothécaire</option>
          <option value="ADMIN">Administrateur</option>
        </select>
      </div>
      <div class="space-y-1">
        <label for="staff-status-filter" class="block text-sm font-medium">Statut</label>
        <select id="staff-status-filter" class="field-input" data-testid="staff-accounts-status-filter"
                (change)="onStatusFilter($event)">
          <option value="">Tous</option>
          <option value="ACTIVE">Actif</option>
          <option value="LOCKED">Verrouillé</option>
          <option value="DISABLED">Désactivé</option>
        </select>
      </div>
    </div>

    @switch (state().kind) {
      @case ('loading') {
        <app-loading-state testId="staff-accounts-loading" />
      }
      @case ('error') {
        <app-error-state [message]="errorMessage()" testId="staff-accounts-error" (retry)="reload()" />
      }
      @case ('success') {
        @if (accounts().length === 0) {
          <app-empty-state [message]="emptyMessage" testId="staff-accounts-empty" />
        } @else {
          <app-staff-account-table [accounts]="accounts()" [sortField]="query().sortField"
                                   [sortDirection]="query().sortDirection" (sortChange)="sortBy($event)"
                                   (action)="onAction($event)" />
          <div class="mt-4">
            <app-pagination [page]="query().page" [size]="query().size" [totalPages]="totalPages()"
                            testId="staff-accounts-pagination" (pageChange)="updateQuery({ page: $event }, false)"
                            (sizeChange)="updateQuery({ size: $event })" />
          </div>
        }
      }
    }

    <app-staff-account-form-dialog [open]="createOpen()" [busy]="createBusy()" [serverError]="createError()"
                                   [emailTaken]="emailTaken()" (submitted)="create($event)"
                                   (cancelled)="createOpen.set(false)" />

    <app-confirm-dialog [open]="!!toDisable()" title="Désactiver le compte" [message]="disableMessage()"
                        confirmLabel="Désactiver" [busy]="disableBusy()" testId="disable-account"
                        (confirmed)="confirmDisable()" (cancelled)="toDisable.set(null)" />

    <app-reset-password-dialog [open]="!!toReset()" [accountId]="toReset()?.id ?? null"
                               [accountLabel]="resetLabel()" (closed)="toReset.set(null)" />
  `,
})
export class StaffAccountsPage {
  private readonly service = inject(StaffAccountService);
  private readonly toasts = inject(ToastService);

  protected readonly emptyMessage = AUTH_MESSAGES.noStaffAccount;
  protected readonly query = signal<StaffAccountQuery>({
    q: '',
    role: null,
    status: null,
    page: 0,
    size: DEFAULT_PAGE_SIZE,
    sortField: 'lastName',
    sortDirection: 'asc',
  });
  private readonly reloadTick = signal(0);

  protected readonly state = toSignal(
    toObservable(computed(() => ({ query: this.query(), tick: this.reloadTick() }))).pipe(
      switchMap(({ query }) =>
        this.service.list(query).pipe(
          map((page): ViewState => ({ kind: 'success', page })),
          catchError((error: unknown) =>
            of<ViewState>({
              kind: 'error',
              message: error instanceof ApiError ? commonErrorMessage(error) : GENERIC_MESSAGES.unexpected,
            }),
          ),
          startWith<ViewState>({ kind: 'loading' }),
        ),
      ),
    ),
    { initialValue: { kind: 'loading' } as ViewState },
  );

  protected readonly accounts = computed(() => {
    const state = this.state();
    return state.kind === 'success' ? state.page.content : [];
  });
  protected readonly totalPages = computed(() => {
    const state = this.state();
    return state.kind === 'success' ? state.page.totalPages : 0;
  });
  protected readonly errorMessage = computed(() => {
    const state = this.state();
    return state.kind === 'error' ? state.message : '';
  });

  // ECR-06
  protected readonly createOpen = signal(false);
  protected readonly createBusy = signal(false);
  protected readonly createError = signal<string | null>(null);
  protected readonly emailTaken = signal(false);

  // Désactivation (confirmation) et réinitialisation (ECR-07)
  protected readonly toDisable = signal<StaffAccount | null>(null);
  protected readonly disableBusy = signal(false);
  protected readonly toReset = signal<StaffAccount | null>(null);
  protected readonly disableMessage = computed(() => {
    const account = this.toDisable();
    return account
      ? `Désactiver le compte de ${account.firstName} ${account.lastName.toUpperCase()} ? ` +
          'L’agent perdra immédiatement tout accès.'
      : '';
  });
  protected readonly resetLabel = computed(() => {
    const account = this.toReset();
    return account ? `${account.firstName} ${account.lastName.toUpperCase()} (${account.email})` : '';
  });

  /** Tout changement de filtre ou de taille revient à la première page. */
  protected updateQuery(change: Partial<StaffAccountQuery>, resetPage = true): void {
    this.query.update((query) => ({ ...query, ...change, ...(resetPage ? { page: 0 } : {}) }));
  }

  protected onRoleFilter(event: Event): void {
    const value = (event.target as HTMLSelectElement).value;
    this.updateQuery({ role: value ? (value as StaffRole) : null });
  }

  protected onStatusFilter(event: Event): void {
    const value = (event.target as HTMLSelectElement).value;
    this.updateQuery({ status: value ? (value as AccountStatus) : null });
  }

  protected sortBy(field: StaffAccountSortField): void {
    const { sortField, sortDirection } = this.query();
    const direction = sortField === field && sortDirection === 'asc' ? 'desc' : 'asc';
    this.updateQuery({ sortField: field, sortDirection: direction });
  }

  protected reload(): void {
    this.reloadTick.update((tick) => tick + 1);
  }

  protected openCreate(): void {
    this.createError.set(null);
    this.emailTaken.set(false);
    this.createOpen.set(true);
  }

  protected create(request: CreateStaffAccountRequest): void {
    this.createBusy.set(true);
    this.createError.set(null);
    this.emailTaken.set(false);
    this.service.create(request).subscribe({
      next: () => {
        this.createBusy.set(false);
        this.createOpen.set(false);
        this.toasts.success(AUTH_MESSAGES.accountCreated);
        this.reload();
      },
      error: (error: unknown) => {
        this.createBusy.set(false);
        if (error instanceof ApiError && error.code === 'EMAIL_ALREADY_USED') {
          this.emailTaken.set(true);
        } else {
          this.createError.set(error instanceof ApiError ? staffAccountErrorMessage(error) : GENERIC_MESSAGES.unexpected);
        }
      },
    });
  }

  protected onAction({ account, action }: StaffAccountActionEvent): void {
    switch (action) {
      case 'disable':
        this.toDisable.set(account);
        break;
      case 'reset':
        this.toReset.set(account);
        break;
      case 'enable':
        this.service.reactivate(account.id).subscribe({
          next: () => {
            this.toasts.success(AUTH_MESSAGES.accountEnabledToast);
            this.reload();
          },
          error: (error: unknown) => this.showError(error),
        });
        break;
    }
  }

  protected confirmDisable(): void {
    const account = this.toDisable();
    if (!account) {
      return;
    }
    this.disableBusy.set(true);
    this.service.deactivate(account.id).subscribe({
      next: () => {
        this.disableBusy.set(false);
        this.toDisable.set(null);
        this.toasts.success(AUTH_MESSAGES.accountDisabledToast);
        this.reload();
      },
      error: (error: unknown) => {
        this.disableBusy.set(false);
        this.toDisable.set(null);
        this.showError(error);
      },
    });
  }

  /** Les erreurs 0, 5xx et 403 sont déjà signalées par l'intercepteur. */
  private showError(error: unknown): void {
    if (error instanceof ApiError && error.status !== 0 && error.status < 500 && error.code !== 'ACCESS_DENIED') {
      this.toasts.error(staffAccountErrorMessage(error));
    }
  }
}
