import { ChangeDetectionStrategy, Component, computed, effect, inject, input, output, signal } from '@angular/core';

import { ApiError } from '../../../core/http/api-error';
import { GENERIC_MESSAGES } from '../../../core/http/messages';
import { ToastService } from '../../../core/notifications/toast.service';
import { FINE_REASON_LABELS, PAYMENT_METHOD_LABELS, PaymentMethod } from '../../../shared/models/statuses';
import { DateFrPipe } from '../../../shared/pipes/date-fr.pipe';
import { EuroPipe, formatEuro, parseEuroCents } from '../../../shared/pipes/euro.pipe';
import { AlertComponent } from '../../../shared/ui/alert.component';
import { ButtonComponent } from '../../../shared/ui/button.component';
import { DialogComponent } from '../../../shared/ui/dialog.component';
import { FINE_MESSAGES, fineErrorMessage } from '../data-access/fine-messages';
import { FineView, PaymentResult, toCents } from '../data-access/fine.models';
import { FineService } from '../data-access/fine.service';

/** Adhérent à encaisser (ECR-34 ouvert depuis ECR-18, ECR-25 ou ECR-35). */
export interface CollectTarget {
  readonly memberNumber: string;
  readonly label: string;
}

/**
 * ECR-34 — Encaisser des pénalités (US-038) : toutes cochées par défaut, montant pré-rempli avec le total
 * sélectionné ; un paiement partiel ou différent est refusé avant l'envoi (RG-38).
 */
@Component({
  selector: 'app-collect-fines-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DialogComponent, AlertComponent, ButtonComponent, DateFrPipe, EuroPipe],
  template: `
    <app-dialog [title]="'Encaisser — ' + (target()?.label ?? '')" [open]="!!target()" testId="collect-dialog"
                (closed)="closed.emit(false)">
      @if (loading()) {
        <p role="status" class="text-sm text-muted" data-testid="collect-loading">Chargement des pénalités…</p>
      } @else if (loadError()) {
        <app-alert tone="danger" data-testid="collect-error">{{ loadError() }}</app-alert>
      } @else if (fines().length === 0) {
        <p data-testid="collect-empty">{{ messages.none }}</p>
        <div class="flex justify-end">
          <button app-button type="button" variant="secondary" (click)="closed.emit(false)">Fermer</button>
        </div>
      } @else {
        <form class="space-y-4" (submit)="$event.preventDefault(); submit()" novalidate>
          <fieldset>
            <legend class="mb-2 text-sm font-medium">Pénalités à encaisser</legend>
            <ul class="space-y-1">
              @for (fine of fines(); track fine.id) {
                <li data-testid="collect-fine-row" [attr.data-row-id]="fine.id">
                  <label class="flex items-center justify-between gap-3 text-sm">
                    <span class="flex items-center gap-2">
                      <input type="checkbox" data-testid="collect-fine-checkbox" [checked]="selected().has(fine.id)"
                             (change)="toggle(fine.id)" />
                      {{ describe(fine) }} · {{ fine.createdOn | dateFr }}
                    </span>
                    <span class="tabular-nums">{{ fine.amount | euro }}</span>
                  </label>
                </li>
              }
            </ul>
            @if (selectionError()) {
              <p class="mt-1 text-sm text-danger" data-testid="collect-selection-error">{{ messages.selectionRequired }}</p>
            }
          </fieldset>
          <p class="flex justify-between border-t border-border pt-2 font-semibold" aria-live="polite">
            <span>Total sélectionné</span><span class="tabular-nums" data-testid="collect-total">{{ total() | euro }}</span>
          </p>
          <fieldset>
            <legend class="text-sm font-medium">Mode de paiement <span class="text-danger" aria-hidden="true">*</span></legend>
            <div class="mt-1 flex flex-wrap gap-4 text-sm">
              @for (option of methods; track option) {
                <label class="flex items-center gap-2">
                  <input type="radio" name="collect-method" [value]="option" [checked]="method() === option"
                         [attr.data-testid]="'collect-mode-' + option.toLowerCase()" (change)="setMethod(option)" />
                  {{ methodLabels[option] }}
                </label>
              }
            </div>
            @if (methodError()) {
              <p class="mt-1 text-sm text-danger" data-testid="collect-mode-error">{{ messages.methodRequired }}</p>
            }
          </fieldset>
          <div class="space-y-1">
            <label for="collect-amount" class="block text-sm font-medium">Montant encaissé (€) <span class="text-danger" aria-hidden="true">*</span></label>
            <input id="collect-amount" class="field-input w-40 tabular-nums" inputmode="decimal" data-testid="collect-amount-input"
                   [value]="amountText()" [attr.aria-invalid]="amountError() ? true : null" (input)="onAmount($event)" />
            @if (amountError()) {
              <p class="text-sm text-danger" data-testid="collect-amount-error">{{ amountError() }}</p>
            }
          </div>
          @if (serverError()) {
            <app-alert tone="danger" data-testid="collect-server-error">{{ serverError() }}</app-alert>
          }
          <div class="flex justify-end gap-2">
            <button app-button type="button" variant="secondary" data-testid="collect-cancel" (click)="closed.emit(false)">Annuler</button>
            <button app-button type="submit" [loading]="busy()" data-testid="collect-confirm">Enregistrer {{ total() | euro }}</button>
          </div>
        </form>
      }
    </app-dialog>
  `,
})
export class CollectFinesDialogComponent {
  readonly target = input<CollectTarget | null>(null);
  /** `true` si un encaissement a été enregistré. */
  readonly closed = output<boolean>();
  readonly collected = output<PaymentResult>();

  private readonly service = inject(FineService);
  private readonly toasts = inject(ToastService);
  protected readonly messages = FINE_MESSAGES;
  protected readonly methods: readonly PaymentMethod[] = ['CASH', 'CARD', 'CHECK'];
  protected readonly methodLabels = PAYMENT_METHOD_LABELS;

  protected readonly fines = signal<readonly FineView[]>([]);
  protected readonly selected = signal<ReadonlySet<number>>(new Set());
  protected readonly method = signal<PaymentMethod | null>(null);
  protected readonly amountText = signal('');
  protected readonly loading = signal(false);
  protected readonly loadError = signal<string | null>(null);
  protected readonly serverError = signal<string | null>(null);
  protected readonly busy = signal(false);
  private readonly attempted = signal(false);

  protected readonly total = computed(() => this.fines()
    .filter((fine) => this.selected().has(fine.id))
    .reduce((sum, fine) => sum + toCents(fine.amount), 0) / 100);
  protected readonly selectionError = computed(() => this.attempted() && this.selected().size === 0);
  protected readonly methodError = computed(() => this.attempted() && !this.method());
  protected readonly amountError = computed(() => {
    if (!this.attempted()) {
      return null;
    }
    const cents = parseEuroCents(this.amountText());
    if (cents === null) {
      return FINE_MESSAGES.partial;
    }
    const expected = toCents(this.total());
    if (cents < expected) {
      return FINE_MESSAGES.partial;
    }
    return cents > expected ? FINE_MESSAGES.mismatch(this.total()) : null;
  });

  constructor() {
    effect(() => {
      const target = this.target();
      if (target) {
        this.load(target.memberNumber);
      }
    });
  }

  protected describe(fine: FineView): string {
    const reason = fine.reason === 'LATE_RETURN' && fine.lateDays
      ? `${FINE_REASON_LABELS.LATE_RETURN} (${fine.lateDays} j)`
      : FINE_REASON_LABELS[fine.reason];
    return fine.title ? `${reason} · ${fine.title}` : reason;
  }

  protected toggle(id: number): void {
    const next = new Set(this.selected());
    if (next.has(id)) {
      next.delete(id);
    } else {
      next.add(id);
    }
    this.selected.set(next);
    this.amountText.set(amountInput(this.total()));
  }

  protected setMethod(method: PaymentMethod): void {
    this.method.set(method);
  }

  protected onAmount(event: Event): void {
    this.amountText.set((event.target as HTMLInputElement).value);
  }

  protected submit(): void {
    this.attempted.set(true);
    const target = this.target();
    const method = this.method();
    if (!target || !method || this.selected().size === 0 || this.amountError()) {
      return;
    }
    this.busy.set(true);
    this.serverError.set(null);
    this.service.collect({
      memberNumber: target.memberNumber,
      fineIds: [...this.selected()],
      method,
      amount: (parseEuroCents(this.amountText()) ?? 0) / 100,
    }).subscribe({
      next: (result) => {
        this.busy.set(false);
        this.toasts.success(FINE_MESSAGES.collected(result.amount, result.method));
        if (result.eligible) {
          this.toasts.success(FINE_MESSAGES.unblocked);
        }
        this.collected.emit(result);
        this.closed.emit(true);
      },
      error: (error: unknown) => {
        this.busy.set(false);
        this.serverError.set(error instanceof ApiError ? fineErrorMessage(error) : GENERIC_MESSAGES.unexpected);
        if (error instanceof ApiError && error.code === 'FINE_ALREADY_SETTLED') {
          this.load(target.memberNumber, false);
        }
      },
    });
  }

  private load(memberNumber: string, resetForm = true): void {
    this.loading.set(true);
    this.loadError.set(null);
    if (resetForm) {
      this.serverError.set(null);
      this.method.set(null);
      this.attempted.set(false);
    }
    this.service.unpaid(memberNumber).subscribe({
      next: (result) => {
        this.loading.set(false);
        this.fines.set(result.fines);
        this.selected.set(new Set(result.fines.map((fine) => fine.id)));
        this.amountText.set(amountInput(this.total()));
      },
      error: (error: unknown) => {
        this.loading.set(false);
        this.loadError.set(error instanceof ApiError ? fineErrorMessage(error) : GENERIC_MESSAGES.unexpected);
      },
    });
  }
}

/** « 21,00 » pour le champ de saisie (virgule décimale). */
function amountInput(total: number): string {
  return formatEuro(total).replace(/\s?€$/, '').replace(/\s/g, '');
}
