import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';

import { ApiError } from '../../../core/http/api-error';
import { GENERIC_MESSAGES } from '../../../core/http/messages';
import { dataOf, viewState } from '../../../shared/data/view-state';
import { DEFAULT_PAGE_SIZE } from '../../../shared/models/page.model';
import { MEMBER_STATUS_DISPLAY, MemberStatus } from '../../../shared/models/statuses';
import { DateFrPipe } from '../../../shared/pipes/date-fr.pipe';
import { PageHeaderComponent } from '../../../shared/ui/page-header.component';
import { PaginationComponent } from '../../../shared/ui/pagination.component';
import { StatusBadgeComponent } from '../../../shared/ui/status-badge.component';
import { EmptyStateComponent, ErrorStateComponent, LoadingStateComponent } from '../../../shared/ui/view-states.component';
import { MEMBER_MESSAGES, memberErrorMessage } from '../data-access/member-messages';
import { MEMBER_NUMBER_PATTERN, MemberSearchQuery, MemberSort, memberName } from '../data-access/member.models';
import { MemberService } from '../data-access/member.service';

/** ECR-16 — Recherche d'adhérents (US-017) ; un numéro saisi ou scanné ouvre directement la fiche (AC-017-1). */
@Component({
  selector: 'app-member-search-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, PageHeaderComponent, PaginationComponent, StatusBadgeComponent, DateFrPipe, LoadingStateComponent,
    EmptyStateComponent, ErrorStateComponent],
  template: `
    <app-page-header title="Adhérents">
      <div class="flex flex-wrap gap-2">
        <a routerLink="/gestion/adherents/a-archiver" data-testid="member-to-archive-link"
           class="inline-flex items-center gap-2 rounded-xl border border-border bg-surface px-4 py-2.5 text-sm font-semibold shadow-sm hover:border-primary/40 hover:bg-primary-soft hover:text-primary">
          Adhérents à archiver
        </a>
        <a routerLink="/gestion/adherents/nouveau" data-testid="member-new-button"
           class="inline-flex items-center rounded-md bg-primary px-4 py-2 text-sm font-medium text-surface hover:bg-primary-hover">
          + Inscrire
        </a>
      </div>
    </app-page-header>

    <form class="mb-4 grid gap-4 md:grid-cols-[2fr_1fr] md:items-end" (submit)="$event.preventDefault(); search()">
      <div class="space-y-1">
        <label for="member-search" class="block text-sm font-medium">Numéro, nom ou e-mail</label>
        <div class="flex gap-2">
          <input id="member-search" type="search" class="field-input" data-testid="member-search-input" [value]="text()"
                 placeholder="A000123" [attr.aria-invalid]="textError() ? true : null" (input)="onText($event)" />
          <button type="submit" class="rounded-xl border border-border bg-surface px-4 text-sm font-semibold shadow-sm hover:bg-primary-soft hover:text-primary"
                  data-testid="member-search-submit">Rechercher</button>
        </div>
        @if (textError()) {
          <p class="text-sm text-danger" data-testid="member-search-error">{{ textError() }}</p>
        }
      </div>
      <div class="space-y-1">
        <label for="member-status" class="block text-sm font-medium">Statut</label>
        <select id="member-status" class="field-input" data-testid="member-status-filter" (change)="onStatus($event)">
          <option value="">Tous sauf archivés</option>
          <option value="ACTIVE">Actif</option>
          <option value="EXPIRED">Expiré</option>
          <option value="SUSPENDED">Suspendu</option>
        </select>
      </div>
    </form>

    @switch (state().kind) {
      @case ('loading') { <app-loading-state testId="member-search-loading" /> }
      @case ('error') { <app-error-state [message]="errorMessage()" testId="member-search-error-state" (retry)="reload()" /> }
      @case ('success') {
        @if (rows().length === 0) {
          <app-empty-state [message]="messages.noResult" testId="member-search-empty" />
        } @else {
          <div class="card overflow-x-auto">
            <table class="w-full text-left text-sm">
              <caption class="sr-only">Adhérents</caption>
              <thead class="bg-surface-alt text-xs uppercase tracking-wide text-muted">
                <tr>
                  <th scope="col" class="px-4 py-3" [attr.aria-sort]="ariaSort('memberNumber,asc')">
                    <button type="button" class="font-semibold" (click)="sortBy('memberNumber,asc')">Numéro {{ icon('memberNumber,asc') }}</button>
                  </th>
                  <th scope="col" class="px-4 py-3" [attr.aria-sort]="ariaSort('lastName,asc')">
                    <button type="button" class="font-semibold" (click)="sortBy('lastName,asc')">Nom {{ icon('lastName,asc') }}</button>
                  </th>
                  <th scope="col" class="px-4 py-3 font-semibold">Naissance</th>
                  <th scope="col" class="px-4 py-3 font-semibold">Statut</th>
                  <th scope="col" class="px-4 py-3" [attr.aria-sort]="ariaSort('expiryDate,asc')">
                    <button type="button" class="font-semibold" (click)="sortBy('expiryDate,asc')">Expire le {{ icon('expiryDate,asc') }}</button>
                  </th>
                </tr>
              </thead>
              <tbody>
                @for (member of rows(); track member.memberNumber) {
                  <tr class="border-t border-border hover:bg-surface-alt" data-testid="member-row" [attr.data-row-id]="member.memberNumber">
                    <td class="px-4 py-3 font-mono">
                      <a [routerLink]="['/gestion/adherents', member.memberNumber]" class="text-primary hover:underline">{{ member.memberNumber }}</a>
                    </td>
                    <td class="px-4 py-3">{{ name(member) }}</td>
                    <td class="px-4 py-3 tabular-nums">{{ member.birthDate | dateFr }}</td>
                    <td class="px-4 py-3"><app-status-badge [display]="statusDisplay[member.status]" [value]="member.status" /></td>
                    <td class="px-4 py-3 tabular-nums">{{ member.expiryDate | dateFr }}</td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
          <div class="mt-4">
            <app-pagination [page]="query().page" [size]="query().size" [totalPages]="totalPages()" testId="member-pagination"
                            (pageChange)="update({ page: $event }, false)" (sizeChange)="update({ size: $event })" />
          </div>
        }
      }
    }
  `,
})
export class MemberSearchPage {
  private readonly members = inject(MemberService);
  private readonly router = inject(Router);
  protected readonly messages = MEMBER_MESSAGES;
  protected readonly statusDisplay = MEMBER_STATUS_DISPLAY;
  protected readonly name = memberName;

  protected readonly text = signal('');
  protected readonly textError = signal<string | null>(null);
  protected readonly query = signal<MemberSearchQuery>({
    q: '', status: null, sort: 'lastName,asc', page: 0, size: DEFAULT_PAGE_SIZE,
  });
  private readonly reloadTick = signal(0);
  private readonly request = computed(() => {
    this.reloadTick();
    return { ...this.query() };
  });
  protected readonly state = viewState(this.request, (query) => this.members.search(query));
  protected readonly rows = computed(() => dataOf(this.state())?.content ?? []);
  protected readonly totalPages = computed(() => dataOf(this.state())?.totalPages ?? 0);
  protected readonly errorMessage = computed(() => {
    const state = this.state();
    return state.kind === 'error' && state.error instanceof ApiError ? memberErrorMessage(state.error) : GENERIC_MESSAGES.unexpected;
  });

  protected onText(event: Event): void {
    this.text.set((event.target as HTMLInputElement).value);
    this.textError.set(null);
  }

  /** AC-017-1 : un numéro exact ouvre la fiche ; sinon recherche partielle d'au moins 2 caractères. */
  protected search(): void {
    const value = this.text().trim();
    if (MEMBER_NUMBER_PATTERN.test(value.toUpperCase())) {
      void this.router.navigate(['/gestion/adherents', value.toUpperCase()]);
      return;
    }
    if (value.length === 1) {
      this.textError.set(MEMBER_MESSAGES.minSearch);
      return;
    }
    this.update({ q: value });
  }

  protected onStatus(event: Event): void {
    const value = (event.target as HTMLSelectElement).value;
    this.update({ status: value ? (value as MemberStatus) : null });
  }

  protected update(change: Partial<MemberSearchQuery>, resetPage = true): void {
    this.query.update((query) => ({ ...query, ...change, ...(resetPage ? { page: 0 } : {}) }));
  }

  protected sortBy(sort: MemberSort): void {
    this.update({ sort });
  }

  protected ariaSort(sort: MemberSort): 'ascending' | 'none' {
    return this.query().sort === sort ? 'ascending' : 'none';
  }

  protected icon(sort: MemberSort): string {
    return this.query().sort === sort ? '▲' : '';
  }

  protected reload(): void {
    this.reloadTick.update((tick) => tick + 1);
  }
}
