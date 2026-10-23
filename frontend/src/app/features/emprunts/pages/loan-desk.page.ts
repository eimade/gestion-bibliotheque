import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { RouterLink } from '@angular/router';

import { ApiError } from '../../../core/http/api-error';
import { GENERIC_MESSAGES } from '../../../core/http/messages';
import { MEMBER_STATUS_DISPLAY } from '../../../shared/models/statuses';
import { DateFrPipe } from '../../../shared/pipes/date-fr.pipe';
import { ButtonComponent } from '../../../shared/ui/button.component';
import { PageHeaderComponent } from '../../../shared/ui/page-header.component';
import { ScanInputComponent } from '../../../shared/ui/scan-input.component';
import { StatusBadgeComponent } from '../../../shared/ui/status-badge.component';
import { EligibilityPanelComponent } from '../../adherents/components/eligibility-panel.component';
import { memberErrorMessage } from '../../adherents/data-access/member-messages';
import { Eligibility, MEMBER_NUMBER_PATTERN, MemberDetail, memberName } from '../../adherents/data-access/member.models';
import { MemberService } from '../../adherents/data-access/member.service';
import { LOAN_MESSAGES, loanErrorMessage } from '../data-access/loan-messages';
import { COPY_NUMBER_PATTERN } from '../data-access/loan.models';
import { LoanService } from '../data-access/loan.service';

interface DeskLine {
  readonly key: number;
  readonly copyNumber: string;
  readonly result: 'pending' | 'success' | 'error';
  readonly title?: string;
  readonly dueDate?: string;
  readonly message?: string;
  readonly reservationFulfilled?: boolean;
}

/**
 * ECR-24 — Guichet emprunt (US-025) : carte adhérent puis exemplaires, au clavier et au lecteur ; chaque scan
 * ajoute une ligne en tête (succès ou refus) annoncée en `aria-live`. `Échap` ou « Terminer » vide la session.
 */
@Component({
  selector: 'app-loan-desk-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, DateFrPipe, ButtonComponent, PageHeaderComponent, ScanInputComponent, StatusBadgeComponent,
    EligibilityPanelComponent],
  template: `
    <app-page-header title="Guichet — Emprunt">
      <button app-button type="button" variant="secondary" data-testid="loan-desk-finish" (click)="finish()">Terminer</button>
    </app-page-header>
    <div class="grid gap-6 lg:grid-cols-2">
      <section class="card space-y-4 p-6" aria-labelledby="desk-member-title">
        <h2 id="desk-member-title" class="font-semibold">1. Adhérent</h2>
        @if (!member()) {
          <app-scan-input label="Carte adhérent" testId="loan-desk-member" placeholder="A000123" [pattern]="memberPattern"
                          [invalidMessage]="messages.memberInvalid" [focusTick]="memberFocus()" (scanned)="identify($event)" />
          @if (memberLoading()) {
            <p role="status" class="text-sm text-muted" data-testid="loan-desk-member-loading">Recherche de l’adhérent…</p>
          }
          @if (memberError()) {
            <p class="text-sm text-danger" role="alert" data-testid="loan-desk-member-error">{{ memberError() }}</p>
          }
        } @else {
          @if (member(); as m) {
            <div class="space-y-3" data-testid="loan-desk-member-card">
              <p class="flex flex-wrap items-center gap-2">
                <strong data-testid="loan-desk-member-name">{{ name(m) }}</strong> · <span class="font-mono">{{ m.memberNumber }}</span>
                <app-status-badge [display]="statusDisplay[m.status]" [value]="m.status" />
              </p>
              <p class="text-sm">Adhésion jusqu’au {{ m.expiryDate | dateFr }}</p>
              <app-eligibility-panel [eligibility]="eligibility()!" />
              <p class="text-sm" data-testid="loan-desk-loan-count">Emprunts en cours : {{ eligibility()!.activeLoans }} / {{ eligibility()!.loanLimit }}</p>
              <a [routerLink]="['/gestion/adherents', m.memberNumber]" data-testid="loan-desk-view-member"
                 class="inline-block text-sm text-primary underline">Voir la fiche</a>
            </div>
          }
        }
      </section>

      <section class="card space-y-4 p-6" aria-labelledby="desk-copies-title">
        <h2 id="desk-copies-title" class="font-semibold">2. Exemplaires</h2>
        <app-scan-input label="N° d’exemplaire" testId="loan-desk-copy" placeholder="00001234" [pattern]="copyPattern"
                        [invalidMessage]="messages.copyInvalid" [disabled]="!canScanCopies()" [focusTick]="copyFocus()"
                        (scanned)="borrow($event)" (escaped)="finish()" />
        <ul class="space-y-2" aria-live="polite">
          @for (line of lines(); track line.key) {
            <li class="rounded-md p-3 text-sm" data-testid="loan-desk-line" [attr.data-row-id]="line.copyNumber"
                [attr.data-result]="line.result" [attr.aria-busy]="line.result === 'pending'"
                [class]="line.result === 'success' ? 'bg-success-soft' : line.result === 'error' ? 'bg-danger-soft' : 'bg-surface-alt'">
              @switch (line.result) {
                @case ('pending') { Enregistrement… {{ line.copyNumber }} }
                @case ('success') {
                  <p><span aria-hidden="true">✓ </span><strong>{{ line.title }}</strong> · {{ line.copyNumber }}</p>
                  <p data-testid="loan-desk-line-due-date">À rendre le {{ line.dueDate | dateFr }}</p>
                  @if (line.reservationFulfilled) {
                    <p class="text-muted" data-testid="loan-desk-line-reservation-fulfilled">(réservation retirée)</p>
                  }
                }
                @case ('error') {
                  <p data-testid="loan-desk-line-message"><span aria-hidden="true">✗ </span>{{ line.copyNumber }} — {{ line.message }}</p>
                }
              }
            </li>
          } @empty {
            <li class="text-sm text-muted" data-testid="loan-desk-empty">{{ messages.noScan }}</li>
          }
        </ul>
      </section>
    </div>
  `,
})
export class LoanDeskPage {
  /** `?adherent=A000123` : adhérent déjà identifié (depuis ECR-18). */
  readonly adherent = input<string>();

  private readonly members = inject(MemberService);
  private readonly loans = inject(LoanService);
  protected readonly messages = LOAN_MESSAGES;
  protected readonly memberPattern = MEMBER_NUMBER_PATTERN;
  protected readonly copyPattern = COPY_NUMBER_PATTERN;
  protected readonly statusDisplay = MEMBER_STATUS_DISPLAY;
  protected readonly name = memberName;

  protected readonly member = signal<MemberDetail | null>(null);
  protected readonly eligibility = signal<Eligibility | null>(null);
  protected readonly memberLoading = signal(false);
  protected readonly memberError = signal<string | null>(null);
  protected readonly lines = signal<readonly DeskLine[]>([]);
  protected readonly memberFocus = signal(0);
  protected readonly copyFocus = signal(0);
  protected readonly canScanCopies = computed(() => !!this.member() && !!this.eligibility()?.eligible);
  private nextKey = 1;

  constructor() {
    effect(() => {
      const number = this.adherent();
      if (number && MEMBER_NUMBER_PATTERN.test(number)) {
        untracked(() => this.identify(number));
      }
    });
  }

  protected identify(memberNumber: string): void {
    this.memberLoading.set(true);
    this.memberError.set(null);
    this.members.get(memberNumber).subscribe({
      next: (member) => {
        this.memberLoading.set(false);
        this.member.set(member);
        this.eligibility.set(member.eligibility);
        this.copyFocus.update((tick) => tick + 1);
      },
      error: (error: unknown) => {
        this.memberLoading.set(false);
        this.memberError.set(error instanceof ApiError ? memberErrorMessage(error) : GENERIC_MESSAGES.unexpected);
        this.memberFocus.update((tick) => tick + 1);
      },
    });
  }

  protected borrow(copyNumber: string): void {
    const member = this.member();
    if (!member) {
      return;
    }
    const key = this.nextKey++;
    this.lines.update((lines) => [{ key, copyNumber, result: 'pending' }, ...lines]);
    this.loans.borrow(member.memberNumber, copyNumber).subscribe({
      next: (result) => {
        this.replace(key, { key, copyNumber, result: 'success', title: result.title, dueDate: result.dueDate,
          reservationFulfilled: result.reservationFulfilled });
        this.eligibility.set(result.eligibility);
        this.copyFocus.update((tick) => tick + 1);
      },
      error: (error: unknown) => {
        const threshold = this.eligibility()?.fineThreshold ?? 5;
        this.replace(key, { key, copyNumber, result: 'error',
          message: error instanceof ApiError ? loanErrorMessage(error, threshold) : GENERIC_MESSAGES.unexpected });
        this.copyFocus.update((tick) => tick + 1);
      },
    });
  }

  /** Fin de session : l'écran revient à la carte adhérent (UX-emprunts ECR-24, étape 5). */
  protected finish(): void {
    this.member.set(null);
    this.eligibility.set(null);
    this.lines.set([]);
    this.memberError.set(null);
    this.memberFocus.update((tick) => tick + 1);
  }

  private replace(key: number, line: DeskLine): void {
    this.lines.update((lines) => lines.map((current) => (current.key === key ? line : current)));
  }
}
