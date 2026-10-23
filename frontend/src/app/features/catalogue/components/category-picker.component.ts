import { ChangeDetectionStrategy, Component, input, model } from '@angular/core';

import { CategoryOption } from '../data-access/catalog.models';

/**
 * Sélection multiple des catégories (ECR-11) en cases à cocher : accessible au clavier sans composant
 * tiers ; les catégories sont peu nombreuses (écart Q-39 avec l'autocomplétion prévue par l'UX).
 */
@Component({
  selector: 'app-category-picker',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fieldset data-testid="book-form-categories-picker">
      <legend class="text-sm font-medium">Catégories <span class="text-danger" aria-hidden="true">*</span>
        <span class="sr-only">(obligatoire)</span></legend>
      <div class="mt-2 grid max-h-48 grid-cols-2 gap-2 overflow-y-auto rounded-xl border border-border p-3 sm:grid-cols-3"
           [attr.aria-describedby]="invalid() ? 'book-form-categories-error' : null">
        @for (category of options(); track category.id) {
          <label class="flex items-center gap-2 text-sm">
            <input type="checkbox" [checked]="selected().includes(category.id)" data-testid="book-form-category-option"
                   [attr.data-category]="category.label" (change)="toggle(category.id)" />
            {{ category.label }}
          </label>
        }
      </div>
      @if (invalid()) {
        <p id="book-form-categories-error" class="mt-1 text-sm text-danger" data-testid="book-form-categories-error">
          <span aria-hidden="true">×</span> Sélectionnez au moins une catégorie.
        </p>
      }
    </fieldset>
  `,
})
export class CategoryPickerComponent {
  readonly options = input.required<readonly CategoryOption[]>();
  readonly selected = model<readonly number[]>([]);
  readonly invalid = input(false);

  protected toggle(id: number): void {
    this.selected.update((ids) => (ids.includes(id) ? ids.filter((value) => value !== id) : [...ids, id]));
  }
}
