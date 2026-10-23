import { ChangeDetectionStrategy, Component, computed, effect, inject, input, output, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { GENERIC_MESSAGES } from '../../../core/http/messages';
import {
  PHYSICAL_CONDITION_LABELS,
  PhysicalCondition,
  WITHDRAWAL_REASON_LABELS,
  WithdrawalReason,
} from '../../../shared/models/statuses';
import { AlertComponent } from '../../../shared/ui/alert.component';
import { ButtonComponent } from '../../../shared/ui/button.component';
import { DialogComponent } from '../../../shared/ui/dialog.component';
import { FormFieldComponent } from '../../../shared/ui/form-field.component';
import { CATALOG_MESSAGES } from '../data-access/catalog-messages';
import { CopyView } from '../data-access/catalog.models';

export interface CopyUpdate {
  readonly shelfMark: string;
  readonly physicalCondition: PhysicalCondition;
}

/**
 * ECR-14 — Modifier ou retirer un exemplaire (US-013), présentationnel. Le retrait demande une
 * confirmation explicite (action irréversible).
 */
@Component({
  selector: 'app-copy-edit-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, DialogComponent, FormFieldComponent, AlertComponent, ButtonComponent],
  template: `
    <app-dialog [title]="'Exemplaire ' + (copy()?.copyNumber ?? '')" [open]="open()" testId="copy-edit-dialog"
                (closed)="cancelled.emit()">
      @if (copy(); as copy) {
        @if (copy.status === 'WITHDRAWN') {
          <app-alert tone="info" data-testid="copy-edit-readonly">{{ messages.copyWithdrawn }}</app-alert>
          <div class="flex justify-end"><button app-button type="button" variant="secondary" (click)="cancelled.emit()">Fermer</button></div>
        } @else {
          <div role="tablist" class="flex gap-2 border-b border-border" aria-label="Action sur l'exemplaire">
            <button type="button" role="tab" [attr.aria-selected]="tab() === 'edit'" data-testid="copy-edit-tab-edit"
                    class="border-b-2 px-3 py-2 text-sm font-medium"
                    [class]="tab() === 'edit' ? 'border-primary text-primary' : 'border-transparent'"
                    (click)="tab.set('edit')">Modifier</button>
            <button type="button" role="tab" [attr.aria-selected]="tab() === 'withdraw'" data-testid="copy-edit-tab-withdraw"
                    class="border-b-2 px-3 py-2 text-sm font-medium"
                    [class]="tab() === 'withdraw' ? 'border-primary text-primary' : 'border-transparent'"
                    (click)="tab.set('withdraw')">Retirer du fonds</button>
          </div>

          @if (tab() === 'edit') {
            <form [formGroup]="form" (ngSubmit)="save()" novalidate class="space-y-4" role="tabpanel">
              <app-form-field label="Cote" fieldId="copy-shelfmark" [required]="true" [error]="shelfMarkError()"
                              errorTestId="copy-edit-shelfmark-error">
                <input id="copy-shelfmark" formControlName="shelfMark" class="field-input" data-testid="copy-edit-shelfmark-input" />
              </app-form-field>
              <fieldset>
                <legend class="text-sm font-medium">État physique</legend>
                <div class="mt-1 flex gap-4 text-sm">
                  @for (condition of conditions; track condition) {
                    <label class="flex items-center gap-2">
                      <input type="radio" formControlName="physicalCondition" [value]="condition"
                             [attr.data-testid]="'copy-edit-condition-' + condition.toLowerCase()" />
                      {{ conditionLabels[condition] }}
                    </label>
                  }
                </div>
              </fieldset>
              @if (serverError()) {
                <app-alert tone="danger" data-testid="copy-edit-server-error">{{ serverError() }}</app-alert>
              }
              <div class="flex justify-end gap-2">
                <button app-button type="button" variant="secondary" (click)="cancelled.emit()">Annuler</button>
                <button app-button type="submit" [loading]="busy()" data-testid="copy-edit-save">Enregistrer</button>
              </div>
            </form>
          } @else {
            <div class="space-y-4" role="tabpanel">
              @if (inCirculation()) {
                <app-alert tone="warning" data-testid="copy-withdraw-blocked">{{ messages.copyInCirculation }}</app-alert>
              } @else if (confirming()) {
                <app-alert tone="danger" data-testid="copy-withdraw-confirm-dialog">
                  Retirer définitivement l'exemplaire {{ copy.copyNumber }} ({{ bookTitle() }}) du fonds ?
                </app-alert>
                @if (serverError()) {
                  <app-alert tone="danger" data-testid="copy-edit-server-error">{{ serverError() }}</app-alert>
                }
                <div class="flex justify-end gap-2">
                  <button app-button type="button" variant="secondary" (click)="confirming.set(false)">Annuler</button>
                  <button app-button type="button" variant="danger" [loading]="busy()" data-testid="copy-withdraw-confirm"
                          (click)="withdrawn.emit(reason()!)">Retirer définitivement</button>
                </div>
              } @else {
                <div class="space-y-1">
                  <label for="withdraw-reason" class="block text-sm font-medium">Motif <span class="text-danger" aria-hidden="true">*</span></label>
                  <select id="withdraw-reason" class="field-input" data-testid="copy-withdraw-reason-select"
                          [attr.aria-invalid]="reasonError() ? true : null" (change)="onReason($event)">
                    <option value="">Choisir…</option>
                    @for (reason of reasons; track reason) {
                      <option [value]="reason">{{ reasonLabels[reason] }}</option>
                    }
                  </select>
                  @if (reasonError()) {
                    <p class="text-sm text-danger" data-testid="copy-withdraw-reason-error">{{ messages.withdrawReasonRequired }}</p>
                  }
                </div>
                <app-alert tone="warning">Le retrait est définitif. L'exemplaire restera dans l'historique.</app-alert>
                <div class="flex justify-end gap-2">
                  <button app-button type="button" variant="secondary" (click)="cancelled.emit()">Annuler</button>
                  <button app-button type="button" variant="danger" data-testid="copy-withdraw-submit" (click)="askConfirmation()">
                    Retirer
                  </button>
                </div>
              }
            </div>
          }
        }
      }
    </app-dialog>
  `,
})
export class CopyEditDialogComponent {
  readonly open = input(false);
  readonly copy = input<CopyView | null>(null);
  readonly bookTitle = input('');
  readonly busy = input(false);
  readonly serverError = input<string | null>(null);
  readonly saved = output<CopyUpdate>();
  readonly withdrawn = output<WithdrawalReason>();
  readonly cancelled = output<void>();

  protected readonly messages = CATALOG_MESSAGES;
  protected readonly conditions: readonly PhysicalCondition[] = ['GOOD', 'WORN', 'DAMAGED'];
  protected readonly conditionLabels = PHYSICAL_CONDITION_LABELS;
  protected readonly reasons: readonly WithdrawalReason[] = ['DAMAGED', 'LOST', 'WEEDED', 'OTHER'];
  protected readonly reasonLabels = WITHDRAWAL_REASON_LABELS;
  protected readonly tab = signal<'edit' | 'withdraw'>('edit');
  protected readonly reason = signal<WithdrawalReason | null>(null);
  protected readonly reasonError = signal(false);
  protected readonly confirming = signal(false);
  protected readonly inCirculation = computed(() => ['ON_LOAN', 'RESERVED'].includes(this.copy()?.status ?? ''));

  protected readonly form = inject(NonNullableFormBuilder).group({
    shelfMark: ['', [Validators.required, Validators.maxLength(30)]],
    physicalCondition: ['GOOD' as PhysicalCondition, Validators.required],
  });

  constructor() {
    effect(() => {
      const copy = this.copy();
      if (this.open() && copy) {
        this.form.reset({ shelfMark: copy.shelfMark, physicalCondition: copy.physicalCondition });
        this.tab.set('edit');
        this.reason.set(null);
        this.reasonError.set(false);
        this.confirming.set(false);
      }
    });
  }

  protected shelfMarkError(): string | null {
    const control = this.form.controls.shelfMark;
    if (!control.touched) {
      return null;
    }
    if (!control.value.trim()) {
      return GENERIC_MESSAGES.required;
    }
    return control.hasError('maxlength') ? CATALOG_MESSAGES.max(30) : null;
  }

  protected save(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid || this.shelfMarkError()) {
      return;
    }
    const value = this.form.getRawValue();
    this.saved.emit({ shelfMark: value.shelfMark.trim(), physicalCondition: value.physicalCondition });
  }

  protected onReason(event: Event): void {
    const value = (event.target as HTMLSelectElement).value;
    this.reason.set(value ? (value as WithdrawalReason) : null);
    this.reasonError.set(false);
  }

  protected askConfirmation(): void {
    if (!this.reason()) {
      this.reasonError.set(true);
      return;
    }
    this.confirming.set(true);
  }
}
