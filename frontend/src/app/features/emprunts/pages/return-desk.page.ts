import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';

import { ApiError } from '../../../core/http/api-error';
import { GENERIC_MESSAGES } from '../../../core/http/messages';
import { EuroPipe } from '../../../shared/pipes/euro.pipe';
import { ButtonComponent } from '../../../shared/ui/button.component';
import { PageHeaderComponent } from '../../../shared/ui/page-header.component';
import { ScanInputComponent } from '../../../shared/ui/scan-input.component';
import { CollectFinesDialogComponent, CollectTarget } from '../../penalites/components/collect-fines-dialog.component';
import { LOAN_MESSAGES, holdInstruction, loanErrorMessage, memberLabel } from '../data-access/loan-messages';
import { COPY_NUMBER_PATTERN, ReturnResult } from '../data-access/loan.models';
import { LoanService } from '../data-access/loan.service';

interface ReturnLine {
  readonly key: number;
  readonly copyNumber: string;
  readonly kind: 'pending' | 'shelf' | 'hold' | 'late' | 'error';
  readonly result?: ReturnResult;
  readonly message?: string;
  readonly done?: boolean;
}

/**
 * ECR-25 — Guichet retour (US-026, US-037) : mise de côté en premier et en majuscules, persistante jusqu'à
 * « Fait » ; pénalité de retard affichée avec « Encaisser » (ECR-34).
 */
@Component({
  selector: 'app-return-desk-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [EuroPipe, ButtonComponent, PageHeaderComponent, ScanInputComponent, CollectFinesDialogComponent],
  template: `
    <app-page-header title="Guichet — Retour" />
    <div class="max-w-3xl space-y-4">
      <app-scan-input label="N° d’exemplaire" testId="return-desk-copy" placeholder="00001234" [pattern]="copyPattern"
                      [invalidMessage]="messages.copyInvalid" [focusTick]="focus()" (scanned)="giveBack($event)" />
      <ul class="space-y-2">
        @for (line of ordered(); track line.key) {
          <li class="rounded-md p-3 text-sm" data-testid="return-desk-line" [attr.data-row-id]="line.copyNumber"
              [attr.data-result]="line.kind" [attr.aria-busy]="line.kind === 'pending'" [class]="tone(line)"
              [attr.aria-live]="line.kind === 'hold' || line.kind === 'late' || line.kind === 'error' ? 'assertive' : 'polite'">
            @switch (line.kind) {
              @case ('pending') { Enregistrement… {{ line.copyNumber }} }
              @case ('error') {
                <p data-testid="return-desk-line-message"><span aria-hidden="true">✗ </span>{{ line.copyNumber }} — {{ line.message }}</p>
              }
              @default {
                @if (line.result; as result) {
                  <p class="font-medium">
                    <span aria-hidden="true">{{ line.kind === 'hold' ? '⚑ ' : line.kind === 'late' ? '! ' : '✓ ' }}</span>
                    {{ result.title }} · {{ result.copyNumber }} — {{ member(result) }}
                  </p>
                  @if (result.hold) {
                    <p class="mt-1 font-semibold uppercase" data-testid="return-desk-line-hold-member">{{ instruction(result) }}</p>
                  }
                  @if (result.lateDays > 0 && result.fine) {
                    <p class="mt-1" data-testid="return-desk-line-late-days">
                      Retour en retard de {{ result.lateDays }} jour(s). Pénalité créée :
                      <span data-testid="return-desk-line-fine-amount">{{ result.fine.amount | euro }}</span>
                    </p>
                  }
                  @if (!result.hold) {
                    <p class="mt-1" data-testid="return-desk-line-instruction">Remettre en rayon : {{ result.shelfMark }}</p>
                  }
                  <div class="mt-2 flex gap-2">
                    @if (result.fine && result.member) {
                      <button app-button type="button" variant="secondary" data-testid="return-desk-line-collect"
                              (click)="collect(result)">Encaisser</button>
                    }
                    @if (line.kind === 'hold' && !line.done) {
                      <button app-button type="button" variant="ghost" data-testid="return-desk-line-done" (click)="markDone(line.key)">Fait</button>
                    }
                  </div>
                }
              }
            }
          </li>
        } @empty {
          <li class="text-sm text-muted" data-testid="return-desk-empty">{{ messages.returnEmpty }}</li>
        }
      </ul>
      <div class="flex items-center justify-between text-sm">
        <span data-testid="return-desk-count">Retours de la session : {{ count() }}</span>
        <button app-button type="button" variant="secondary" data-testid="return-desk-clear" (click)="lines.set([])">Vider la liste</button>
      </div>
    </div>
    <app-collect-fines-dialog [target]="collectTarget()" (closed)="collectTarget.set(null); focus.set(focus() + 1)" />
  `,
})
export class ReturnDeskPage {
  private readonly loans = inject(LoanService);
  protected readonly messages = LOAN_MESSAGES;
  protected readonly copyPattern = COPY_NUMBER_PATTERN;
  protected readonly lines = signal<readonly ReturnLine[]>([]);
  protected readonly focus = signal(0);
  protected readonly collectTarget = signal<CollectTarget | null>(null);
  private nextKey = 1;

  /** Les mises de côté non traitées restent en tête de liste (UX-emprunts ECR-25). */
  protected readonly ordered = computed(() => {
    const lines = this.lines();
    return [...lines.filter((line) => line.kind === 'hold' && !line.done),
      ...lines.filter((line) => !(line.kind === 'hold' && !line.done))];
  });
  protected readonly count = computed(() => this.lines().filter((line) => line.result).length);

  protected giveBack(copyNumber: string): void {
    const key = this.nextKey++;
    this.lines.update((lines) => [{ key, copyNumber, kind: 'pending' }, ...lines]);
    this.loans.giveBack(copyNumber).subscribe({
      next: (result) => {
        const kind = result.hold ? 'hold' : result.fine ? 'late' : 'shelf';
        this.replace(key, { key, copyNumber, kind, result });
        this.focus.update((tick) => tick + 1);
      },
      error: (error: unknown) => {
        this.replace(key, { key, copyNumber, kind: 'error',
          message: error instanceof ApiError ? loanErrorMessage(error) : GENERIC_MESSAGES.unexpected });
        this.focus.update((tick) => tick + 1);
      },
    });
  }

  protected tone(line: ReturnLine): string {
    switch (line.kind) {
      case 'hold':
        return 'bg-warning-soft';
      case 'late':
      case 'error':
        return 'bg-danger-soft';
      case 'shelf':
        return 'bg-success-soft';
      default:
        return 'bg-surface-alt';
    }
  }

  protected member(result: ReturnResult): string {
    return memberLabel(result.member);
  }

  protected instruction(result: ReturnResult): string {
    return result.hold ? holdInstruction(result.hold) : '';
  }

  protected collect(result: ReturnResult): void {
    if (result.member) {
      this.collectTarget.set({ memberNumber: result.member.memberNumber, label: memberLabel(result.member) });
    }
  }

  protected markDone(key: number): void {
    this.lines.update((lines) => lines.map((line) => (line.key === key ? { ...line, done: true } : line)));
  }

  private replace(key: number, line: ReturnLine): void {
    this.lines.update((lines) => lines.map((current) => (current.key === key ? line : current)));
  }
}
