import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';

import { ApiError } from '../../../core/http/api-error';
import { GENERIC_MESSAGES } from '../../../core/http/messages';
import { ToastService } from '../../../core/notifications/toast.service';
import { dataOf, viewState } from '../../../shared/data/view-state';
import { DEFAULT_PAGE_SIZE } from '../../../shared/models/page.model';
import { ButtonComponent } from '../../../shared/ui/button.component';
import { ConfirmDialogComponent } from '../../../shared/ui/confirm-dialog.component';
import { PageHeaderComponent } from '../../../shared/ui/page-header.component';
import { PaginationComponent } from '../../../shared/ui/pagination.component';
import { EmptyStateComponent, ErrorStateComponent, LoadingStateComponent } from '../../../shared/ui/view-states.component';
import { CATALOG_MESSAGES, catalogErrorMessage } from '../data-access/catalog-messages';
import { CategoryResponse } from '../data-access/catalog.models';
import { CategoryService } from '../data-access/category.service';

const MIN_LABEL = 2;
const MAX_LABEL = 50;

/** ECR-15 — Catégories (US-014, RG-12) : création, renommage en ligne, suppression si inutilisée. */
@Component({
  selector: 'app-categories-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [PageHeaderComponent, ButtonComponent, PaginationComponent, ConfirmDialogComponent, LoadingStateComponent,
    EmptyStateComponent, ErrorStateComponent],
  template: `
    <app-page-header title="Catégories" />
    <form class="mb-6 flex flex-wrap items-end gap-2" (submit)="$event.preventDefault(); create()">
      <div class="space-y-1">
        <label for="new-category" class="block text-sm font-medium">Nouvelle catégorie</label>
        <input id="new-category" class="field-input w-72" data-testid="categories-new-input" [value]="newLabel()"
               [attr.aria-invalid]="newError() ? true : null" [attr.aria-describedby]="newError() ? 'new-category-error' : null"
               (input)="newLabel.set(value($event))" />
      </div>
      <button app-button type="submit" [loading]="busy()" data-testid="categories-add-button">Ajouter</button>
      @if (newError()) {
        <p id="new-category-error" class="w-full text-sm text-danger" data-testid="categories-new-error">{{ newError() }}</p>
      }
    </form>

    @switch (state().kind) {
      @case ('loading') { <app-loading-state testId="categories-loading" /> }
      @case ('error') { <app-error-state [message]="errorMessage()" testId="categories-error" (retry)="reload()" /> }
      @case ('success') {
        @if (rows().length === 0) {
          <app-empty-state [message]="messages.noCategory" testId="categories-empty" />
        } @else {
          <div class="card overflow-x-auto">
            <table class="w-full text-left text-sm">
              <caption class="sr-only">Catégories</caption>
              <thead class="bg-surface-alt text-xs uppercase tracking-wide text-muted">
                <tr>
                  <th scope="col" class="px-4 py-3" aria-sort="ascending">Libellé ▲</th>
                  <th scope="col" class="px-4 py-3">Notices</th>
                  <th scope="col" class="px-4 py-3"><span class="sr-only">Actions</span></th>
                </tr>
              </thead>
              <tbody>
                @for (category of rows(); track category.id) {
                  <tr class="border-t border-border" data-testid="categories-row" [attr.data-row-id]="category.label">
                    <td class="px-4 py-3">
                      @if (editing()?.id === category.id) {
                        <label class="sr-only" [for]="'rename-' + category.id">Nouveau libellé</label>
                        <input [id]="'rename-' + category.id" class="field-input" data-testid="categories-rename-input"
                               [value]="editLabel()" (input)="editLabel.set(value($event))"
                               (keydown.enter)="saveRename(category)" (keydown.escape)="editing.set(null)" />
                        @if (renameError()) {
                          <p class="mt-1 text-sm text-danger" data-testid="categories-rename-error">{{ renameError() }}</p>
                        }
                      } @else {
                        {{ category.label }}
                      }
                    </td>
                    <td class="px-4 py-3 tabular-nums">{{ category.bookCount }}</td>
                    <td class="px-4 py-3 text-right">
                      @if (editing()?.id === category.id) {
                        <button type="button" class="mr-3 text-primary underline" (click)="saveRename(category)">Enregistrer</button>
                        <button type="button" class="underline" (click)="editing.set(null)">Annuler</button>
                      } @else {
                        <button type="button" class="mr-3 text-primary underline" data-testid="categories-rename-action"
                                (click)="startRename(category)">Renommer</button>
                        @if (category.bookCount > 0) {
                          <span class="text-xs text-muted" data-testid="categories-delete-blocked">
                            Utilisée par {{ category.bookCount }} notice{{ category.bookCount > 1 ? 's' : '' }}
                          </span>
                        } @else {
                          <button type="button" class="text-danger underline" data-testid="categories-delete-action"
                                  (click)="toDelete.set(category)">Supprimer</button>
                        }
                      }
                    </td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
          <div class="mt-4">
            <app-pagination [page]="page()" [size]="size()" [totalPages]="totalPages()" testId="categories-pagination"
                            (pageChange)="page.set($event)" (sizeChange)="size.set($event); page.set(0)" />
          </div>
        }
      }
    }
    <app-confirm-dialog [open]="!!toDelete()" title="Supprimer la catégorie"
                        [message]="'Supprimer la catégorie « ' + (toDelete()?.label ?? '') + ' » ?'" confirmLabel="Supprimer"
                        [busy]="busy()" testId="categories-delete" (confirmed)="confirmDelete()" (cancelled)="toDelete.set(null)" />
  `,
})
export class CategoriesPage {
  private readonly service = inject(CategoryService);
  private readonly toasts = inject(ToastService);
  protected readonly messages = CATALOG_MESSAGES;
  protected readonly page = signal(0);
  protected readonly size = signal(DEFAULT_PAGE_SIZE);
  private readonly reloadTick = signal(0);
  private readonly request = computed(() => {
    this.reloadTick();
    return { page: this.page(), size: this.size() };
  });
  protected readonly state = viewState(this.request, ({ page, size }) => this.service.list(page, size));
  protected readonly rows = computed(() => dataOf(this.state())?.content ?? []);
  protected readonly totalPages = computed(() => dataOf(this.state())?.totalPages ?? 0);
  protected readonly errorMessage = computed(() => {
    const state = this.state();
    return state.kind === 'error' && state.error instanceof ApiError ? catalogErrorMessage(state.error) : GENERIC_MESSAGES.unexpected;
  });

  protected readonly busy = signal(false);
  protected readonly newLabel = signal('');
  protected readonly newError = signal<string | null>(null);
  protected readonly editing = signal<CategoryResponse | null>(null);
  protected readonly editLabel = signal('');
  protected readonly renameError = signal<string | null>(null);
  protected readonly toDelete = signal<CategoryResponse | null>(null);

  protected value(event: Event): string {
    return (event.target as HTMLInputElement).value;
  }

  protected create(): void {
    const label = this.newLabel().trim();
    const invalid = this.validate(label);
    this.newError.set(invalid);
    if (invalid) {
      return;
    }
    this.busy.set(true);
    this.service.create(label).subscribe({
      next: () => {
        this.busy.set(false);
        this.newLabel.set('');
        this.toasts.success(CATALOG_MESSAGES.categoryCreated);
        this.reload();
      },
      error: (error: unknown) => {
        this.busy.set(false);
        this.newError.set(this.message(error));
      },
    });
  }

  protected startRename(category: CategoryResponse): void {
    this.editing.set(category);
    this.editLabel.set(category.label);
    this.renameError.set(null);
  }

  protected saveRename(category: CategoryResponse): void {
    const label = this.editLabel().trim();
    const invalid = this.validate(label);
    this.renameError.set(invalid);
    if (invalid) {
      return;
    }
    this.service.rename(category.id, label, category.version).subscribe({
      next: () => {
        this.editing.set(null);
        this.toasts.success(CATALOG_MESSAGES.categoryRenamed);
        this.reload();
      },
      error: (error: unknown) => this.renameError.set(this.message(error)),
    });
  }

  protected confirmDelete(): void {
    const category = this.toDelete();
    if (!category) {
      return;
    }
    this.busy.set(true);
    this.service.remove(category.id).subscribe({
      next: () => {
        this.busy.set(false);
        this.toDelete.set(null);
        this.toasts.success(CATALOG_MESSAGES.categoryDeleted);
        this.reload();
      },
      error: (error: unknown) => {
        this.busy.set(false);
        this.toDelete.set(null);
        this.toasts.error(this.message(error));
      },
    });
  }

  protected reload(): void {
    this.reloadTick.update((tick) => tick + 1);
  }

  private validate(label: string): string | null {
    return label.length < MIN_LABEL || label.length > MAX_LABEL ? CATALOG_MESSAGES.categoryLabelLength : null;
  }

  private message(error: unknown): string {
    return error instanceof ApiError ? catalogErrorMessage(error) : GENERIC_MESSAGES.unexpected;
  }
}
