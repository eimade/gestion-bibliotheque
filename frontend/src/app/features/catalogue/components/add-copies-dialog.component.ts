import { ChangeDetectionStrategy, Component, effect, inject, input, output, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { GENERIC_MESSAGES } from '../../../core/http/messages';
import { PHYSICAL_CONDITION_LABELS, PhysicalCondition } from '../../../shared/models/statuses';
import { todayInParis } from '../../../shared/pipes/date-fr.pipe';
import { AlertComponent } from '../../../shared/ui/alert.component';
import { ButtonComponent } from '../../../shared/ui/button.component';
import { DialogComponent } from '../../../shared/ui/dialog.component';
import { FormFieldComponent, describedBy } from '../../../shared/ui/form-field.component';
import { CATALOG_MESSAGES } from '../data-access/catalog-messages';
import { AddCopiesRequest } from '../data-access/catalog.models';

/** ECR-13 — Ajouter des exemplaires (US-012), présentationnel. */
@Component({
  selector: 'app-add-copies-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, DialogComponent, FormFieldComponent, AlertComponent, ButtonComponent],
  template: `
    <app-dialog title="Ajouter des exemplaires" [open]="open()" testId="add-copies-dialog" (closed)="cancelled.emit()">
      <p class="text-sm">Notice : <strong>{{ bookTitle() }}</strong></p>
      <form [formGroup]="form" (ngSubmit)="submit()" novalidate class="space-y-4">
        <app-form-field label="Nombre d'exemplaires" fieldId="copies-quantity" [required]="true" [error]="quantityError()"
                        errorTestId="add-copies-quantity-error">
          <input id="copies-quantity" type="number" min="1" max="20" formControlName="quantity" class="field-input w-32"
                 data-testid="add-copies-quantity-input" [attr.aria-invalid]="!!quantityError()"
                 [attr.aria-describedby]="describedBy('copies-quantity', !!quantityError())" />
        </app-form-field>
        <app-form-field label="Cote" fieldId="copies-shelfmark" [required]="true" [error]="shelfMarkError()"
                        errorTestId="add-copies-shelfmark-error">
          <input id="copies-shelfmark" formControlName="shelfMark" class="field-input" data-testid="add-copies-shelfmark-input"
                 [attr.aria-invalid]="!!shelfMarkError()" [attr.aria-describedby]="describedBy('copies-shelfmark', !!shelfMarkError())" />
        </app-form-field>
        <fieldset>
          <legend class="text-sm font-medium">État physique <span class="text-danger" aria-hidden="true">*</span></legend>
          <div class="mt-1 flex gap-4 text-sm">
            @for (condition of conditions; track condition) {
              <label class="flex items-center gap-2">
                <input type="radio" formControlName="physicalCondition" [value]="condition"
                       [attr.data-testid]="'add-copies-condition-' + condition.toLowerCase()" />
                {{ conditionLabels[condition] }}
              </label>
            }
          </div>
        </fieldset>
        <app-form-field label="Date d'acquisition" fieldId="copies-date" [required]="true" [error]="dateError()"
                        errorTestId="add-copies-date-error">
          <input id="copies-date" type="date" formControlName="acquisitionDate" class="field-input w-48" [max]="today"
                 data-testid="add-copies-date-input" [attr.aria-invalid]="!!dateError()"
                 [attr.aria-describedby]="describedBy('copies-date', !!dateError())" />
        </app-form-field>
        <app-alert tone="info">Les numéros d'exemplaire sont attribués automatiquement.</app-alert>
        @if (serverError()) {
          <app-alert tone="danger" data-testid="add-copies-server-error">{{ serverError() }}</app-alert>
        }
        <div class="flex justify-end gap-2">
          <button app-button type="button" variant="secondary" data-testid="add-copies-dialog-cancel"
                  (click)="cancelled.emit()">Annuler</button>
          <button app-button type="submit" [loading]="busy()" data-testid="add-copies-dialog-confirm">Ajouter</button>
        </div>
      </form>
    </app-dialog>
  `,
})
export class AddCopiesDialogComponent {
  readonly open = input(false);
  readonly bookTitle = input('');
  readonly busy = input(false);
  readonly serverError = input<string | null>(null);
  readonly submitted = output<AddCopiesRequest>();
  readonly cancelled = output<void>();

  protected readonly describedBy = describedBy;
  protected readonly conditions: readonly PhysicalCondition[] = ['GOOD', 'WORN', 'DAMAGED'];
  protected readonly conditionLabels = PHYSICAL_CONDITION_LABELS;
  protected readonly today = todayInParis();
  private readonly attempted = signal(false);

  protected readonly form = inject(NonNullableFormBuilder).group({
    quantity: [1, [Validators.required, Validators.min(1), Validators.max(20)]],
    shelfMark: ['', [Validators.required, Validators.maxLength(30)]],
    physicalCondition: ['GOOD' as PhysicalCondition, Validators.required],
    acquisitionDate: [this.today, Validators.required],
  });

  constructor() {
    effect(() => {
      if (this.open()) {
        this.form.reset({ quantity: 1, shelfMark: '', physicalCondition: 'GOOD', acquisitionDate: todayInParis() });
        this.attempted.set(false);
      }
    });
  }

  protected quantityError(): string | null {
    const control = this.form.controls.quantity;
    return (control.touched || this.attempted()) && control.invalid ? CATALOG_MESSAGES.quantityRange : null;
  }

  protected shelfMarkError(): string | null {
    const control = this.form.controls.shelfMark;
    if (!(control.touched || this.attempted())) {
      return null;
    }
    if (control.hasError('required') || !control.value.trim()) {
      return GENERIC_MESSAGES.required;
    }
    return control.hasError('maxlength') ? CATALOG_MESSAGES.max(30) : null;
  }

  protected dateError(): string | null {
    const control = this.form.controls.acquisitionDate;
    if (!(control.touched || this.attempted())) {
      return null;
    }
    if (!control.value) {
      return GENERIC_MESSAGES.required;
    }
    return control.value > todayInParis() ? CATALOG_MESSAGES.acquisitionFuture : null;
  }

  protected submit(): void {
    this.attempted.set(true);
    if (this.form.invalid || this.shelfMarkError() || this.dateError()) {
      this.form.markAllAsTouched();
      return;
    }
    const value = this.form.getRawValue();
    this.submitted.emit({ ...value, quantity: Number(value.quantity), shelfMark: value.shelfMark.trim() });
  }
}
