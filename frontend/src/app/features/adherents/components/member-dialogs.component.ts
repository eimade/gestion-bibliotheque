import { ChangeDetectionStrategy, Component, computed, effect, input, output, signal } from '@angular/core';

import { MEMBER_STATUS_DISPLAY } from '../../../shared/models/statuses';
import { todayInParis } from '../../../shared/pipes/date-fr.pipe';
import { AlertComponent } from '../../../shared/ui/alert.component';
import { ButtonComponent } from '../../../shared/ui/button.component';
import { DialogComponent } from '../../../shared/ui/dialog.component';
import { MEMBER_MESSAGES, archiveReasonLabel } from '../data-access/member-messages';
import { ArchiveCheck, MemberDetail, memberName } from '../data-access/member.models';

/** ECR-19 — Suspendre ou réactiver (US-021), présentationnel : deux modes selon le statut. */
@Component({
  selector: 'app-suspend-member-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DialogComponent, AlertComponent, ButtonComponent],
  template: `
    @if (member(); as current) {
      @if (current.status === 'SUSPENDED') {
        <app-dialog [title]="'Réactiver ' + name()" [open]="open()" testId="reactivate-dialog" (closed)="cancelled.emit()">
          <p>Le statut deviendra : <strong data-testid="reactivate-target-status">{{ targetStatus() }}</strong></p>
          @if (serverError()) { <app-alert tone="danger">{{ serverError() }}</app-alert> }
          <div class="flex justify-end gap-2">
            <button app-button type="button" variant="secondary" (click)="cancelled.emit()">Annuler</button>
            <button app-button type="button" [loading]="busy()" data-testid="reactivate-dialog-confirm"
                    (click)="reactivate.emit()">Réactiver</button>
          </div>
        </app-dialog>
      } @else {
        <app-dialog [title]="'Suspendre ' + name() + ' (' + current.memberNumber + ')'" [open]="open()" testId="suspend-dialog"
                    (closed)="cancelled.emit()">
          <div class="space-y-1">
            <label for="suspend-reason" class="block text-sm font-medium">Motif <span class="text-danger" aria-hidden="true">*</span></label>
            <textarea id="suspend-reason" rows="3" class="field-input" data-testid="suspend-reason-input" [value]="reason()"
                      [attr.aria-invalid]="reasonError() ? true : null" (input)="onReason($event)"></textarea>
            @if (reasonError()) {
              <p class="text-sm text-danger" data-testid="suspend-reason-error">{{ messages.suspendReason }}</p>
            }
          </div>
          <app-alert tone="info">L’adhérent ne pourra plus emprunter, prolonger ni réserver. Ses réservations en cours sont conservées.</app-alert>
          @if (serverError()) { <app-alert tone="danger">{{ serverError() }}</app-alert> }
          <div class="flex justify-end gap-2">
            <button app-button type="button" variant="secondary" data-testid="suspend-dialog-cancel" (click)="cancelled.emit()">Annuler</button>
            <button app-button type="button" variant="danger" [loading]="busy()" data-testid="suspend-dialog-confirm"
                    (click)="confirmSuspension()">Suspendre</button>
          </div>
        </app-dialog>
      }
    }
  `,
})
export class SuspendMemberDialogComponent {
  readonly member = input<MemberDetail | null>(null);
  readonly open = input(false);
  readonly busy = input(false);
  readonly serverError = input<string | null>(null);
  readonly suspend = output<string>();
  readonly reactivate = output<void>();
  readonly cancelled = output<void>();

  protected readonly messages = MEMBER_MESSAGES;
  protected readonly reason = signal('');
  protected readonly reasonError = signal(false);
  protected readonly name = computed(() => {
    const member = this.member();
    return member ? `${member.firstName ?? ''} ${(member.lastName ?? '').toUpperCase()}`.trim() : '';
  });
  /** RG-16 : expiré si l'adhésion est échue. */
  protected readonly targetStatus = computed(() => {
    const member = this.member();
    return member && member.expiryDate < todayInParis() ? MEMBER_STATUS_DISPLAY.EXPIRED.label : MEMBER_STATUS_DISPLAY.ACTIVE.label;
  });

  constructor() {
    effect(() => {
      if (this.open()) {
        this.reason.set('');
        this.reasonError.set(false);
      }
    });
  }

  protected onReason(event: Event): void {
    this.reason.set((event.target as HTMLTextAreaElement).value);
    this.reasonError.set(false);
  }

  protected confirmSuspension(): void {
    const reason = this.reason().trim();
    if (reason.length < 3 || reason.length > 255) {
      this.reasonError.set(true);
      return;
    }
    this.suspend.emit(reason);
  }
}

/** ECR-20 — Archiver (US-022, RG-18) : motifs bloquants, sinon saisie du mot « ARCHIVER ». */
@Component({
  selector: 'app-archive-member-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DialogComponent, AlertComponent, ButtonComponent],
  template: `
    <app-dialog [title]="'Archiver ' + label()" [open]="open()" testId="archive-dialog" (closed)="cancelled.emit()">
      @if (!check()) {
        <p role="status" class="text-sm text-muted" data-testid="archive-dialog-loading">Vérification…</p>
      } @else if (check()!.archivable) {
        <app-alert tone="danger">
          Action irréversible. Le nom, la date de naissance, les coordonnées et le représentant légal seront effacés
          définitivement. Le compte sera désactivé.
        </app-alert>
        <div class="space-y-1">
          <label for="archive-confirm" class="block text-sm font-medium">Pour confirmer, saisissez ARCHIVER</label>
          <input id="archive-confirm" class="field-input" autocomplete="off" data-testid="archive-confirm-input"
                 [value]="typed()" (input)="onTyped($event)" />
        </div>
        @if (serverError()) { <app-alert tone="danger">{{ serverError() }}</app-alert> }
        <div class="flex justify-end gap-2">
          <button app-button type="button" variant="secondary" data-testid="archive-dialog-cancel" (click)="cancelled.emit()">Annuler</button>
          <button app-button type="button" variant="danger" [loading]="busy()" [disabled]="typed() !== 'ARCHIVER'"
                  data-testid="archive-dialog-confirm" (click)="confirmed.emit()">Archiver</button>
        </div>
      } @else {
        <p class="font-medium text-danger"><span aria-hidden="true">✗ </span>Archivage impossible :</p>
        <ul class="list-disc pl-6 text-sm">
          @for (reason of check()!.reasons; track reason) {
            <li data-testid="archive-blocking-reason" [attr.data-reason]="reason">{{ reasonLabel(reason) }}</li>
          }
        </ul>
        <div class="flex justify-end">
          <button app-button type="button" variant="secondary" data-testid="archive-dialog-cancel" (click)="cancelled.emit()">Fermer</button>
        </div>
      }
    </app-dialog>
  `,
})
export class ArchiveMemberDialogComponent {
  readonly label = input('');
  readonly open = input(false);
  readonly check = input<ArchiveCheck | null>(null);
  readonly busy = input(false);
  readonly serverError = input<string | null>(null);
  readonly confirmed = output<void>();
  readonly cancelled = output<void>();

  protected readonly typed = signal('');

  constructor() {
    effect(() => {
      if (this.open()) {
        this.typed.set('');
      }
    });
  }

  protected onTyped(event: Event): void {
    this.typed.set((event.target as HTMLInputElement).value.trim());
  }

  protected reasonLabel(reason: ArchiveCheck['reasons'][number]): string {
    return archiveReasonLabel(reason, this.check()!);
  }
}

/** « Jean MARTIN (A000123) » pour les titres de dialogue. */
export function memberTitle(member: MemberDetail): string {
  return `${memberName(member)} (${member.memberNumber})`;
}
