import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { ApiError } from '../../../core/http/api-error';
import { GENERIC_MESSAGES } from '../../../core/http/messages';
import { ToastService } from '../../../core/notifications/toast.service';
import { dataOf, viewState } from '../../../shared/data/view-state';
import { DEFAULT_PAGE_SIZE } from '../../../shared/models/page.model';
import { DateFrPipe } from '../../../shared/pipes/date-fr.pipe';
import { ButtonComponent } from '../../../shared/ui/button.component';
import { PageHeaderComponent } from '../../../shared/ui/page-header.component';
import { PaginationComponent } from '../../../shared/ui/pagination.component';
import { EmptyStateComponent, ErrorStateComponent, LoadingStateComponent } from '../../../shared/ui/view-states.component';
import { ArchiveMemberDialogComponent } from '../components/member-dialogs.component';
import { MEMBER_MESSAGES, archiveReasonLabel, memberErrorMessage } from '../data-access/member-messages';
import { MemberToArchive, memberName } from '../data-access/member.models';
import { MemberService } from '../data-access/member.service';

/** ECR-21 — Adhérents expirés depuis plus de 3 ans (US-022 AC-022-2, RG-19), avec leur archivabilité. */
@Component({
  selector: 'app-members-to-archive-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, DateFrPipe, ButtonComponent, PageHeaderComponent, PaginationComponent, LoadingStateComponent,
    EmptyStateComponent, ErrorStateComponent, ArchiveMemberDialogComponent],
  template: `
    <app-page-header title="Adhérents à archiver" />
    <p class="mb-4 text-sm text-muted">Adhérents dont l’adhésion a expiré depuis plus de 3 ans.</p>
    @switch (state().kind) {
      @case ('loading') { <app-loading-state testId="to-archive-loading" /> }
      @case ('error') { <app-error-state [message]="errorMessage()" testId="to-archive-error" (retry)="reload()" /> }
      @case ('success') {
        @if (rows().length === 0) {
          <app-empty-state [message]="messages.noneToArchive" testId="to-archive-empty" />
        } @else {
          <div class="card overflow-x-auto">
            <table class="w-full text-left text-sm">
              <caption class="sr-only">Adhérents à archiver</caption>
              <thead class="bg-surface-alt text-xs uppercase tracking-wide text-muted"><tr>
                <th scope="col" class="px-4 py-3">Numéro</th><th scope="col" class="px-4 py-3">Nom</th>
                <th scope="col" class="px-4 py-3" aria-sort="ascending">Expiré le ▲</th>
                <th scope="col" class="px-4 py-3">Archivable</th><th scope="col" class="px-4 py-3"><span class="sr-only">Actions</span></th>
              </tr></thead>
              <tbody>
                @for (row of rows(); track row.memberNumber) {
                  <tr class="border-t border-border" data-testid="to-archive-row" [attr.data-row-id]="row.memberNumber">
                    <td class="px-4 py-3 font-mono">
                      <a [routerLink]="['/gestion/adherents', row.memberNumber]" class="text-primary hover:underline">{{ row.memberNumber }}</a>
                    </td>
                    <td class="px-4 py-3">{{ name(row) }}</td>
                    <td class="px-4 py-3 tabular-nums">{{ row.expiryDate | dateFr }}</td>
                    <td class="px-4 py-3" data-testid="to-archive-archivable" [attr.data-archivable]="row.archivable">
                      @if (row.archivable) { Oui } @else {
                        Non : <span data-testid="to-archive-reason">{{ reasons(row) }}</span>
                      }
                    </td>
                    <td class="px-4 py-3 text-right">
                      @if (row.archivable) {
                        <button app-button type="button" variant="danger" data-testid="to-archive-action" (click)="target.set(row)">Archiver</button>
                      }
                    </td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
          <div class="mt-4">
            <app-pagination [page]="page()" [size]="size()" [totalPages]="totalPages()" testId="to-archive-pagination"
                            (pageChange)="page.set($event)" (sizeChange)="size.set($event); page.set(0)" />
          </div>
        }
      }
    }
    <app-archive-member-dialog [label]="target() ? name(target()!) + ' (' + target()!.memberNumber + ')' : ''" [open]="!!target()"
                               [check]="target()" [busy]="busy()" [serverError]="dialogError()" (confirmed)="archive()"
                               (cancelled)="target.set(null)" />
  `,
})
export class MembersToArchivePage {
  private readonly members = inject(MemberService);
  private readonly toasts = inject(ToastService);
  protected readonly messages = MEMBER_MESSAGES;
  protected readonly name = memberName;
  protected readonly page = signal(0);
  protected readonly size = signal(DEFAULT_PAGE_SIZE);
  private readonly reloadTick = signal(0);
  private readonly request = computed(() => {
    this.reloadTick();
    return { page: this.page(), size: this.size() };
  });
  protected readonly state = viewState(this.request, ({ page, size }) => this.members.toArchive(page, size));
  protected readonly rows = computed(() => dataOf(this.state())?.content ?? []);
  protected readonly totalPages = computed(() => dataOf(this.state())?.totalPages ?? 0);
  protected readonly errorMessage = computed(() => {
    const state = this.state();
    return state.kind === 'error' && state.error instanceof ApiError ? memberErrorMessage(state.error) : GENERIC_MESSAGES.unexpected;
  });
  protected readonly target = signal<MemberToArchive | null>(null);
  protected readonly busy = signal(false);
  protected readonly dialogError = signal<string | null>(null);

  protected reasons(row: MemberToArchive): string {
    return row.reasons.map((reason) => archiveReasonLabel(reason, row).toLowerCase()).join(', ');
  }

  protected archive(): void {
    const row = this.target();
    if (!row) {
      return;
    }
    this.busy.set(true);
    this.dialogError.set(null);
    this.members.archive(row.memberNumber).subscribe({
      next: () => {
        this.busy.set(false);
        this.target.set(null);
        this.toasts.success(MEMBER_MESSAGES.archived);
        this.reload();
      },
      error: (error: unknown) => {
        this.busy.set(false);
        this.dialogError.set(error instanceof ApiError ? memberErrorMessage(error) : GENERIC_MESSAGES.unexpected);
      },
    });
  }

  protected reload(): void {
    this.reloadTick.update((tick) => tick + 1);
  }
}
