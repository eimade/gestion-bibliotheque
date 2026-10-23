import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal } from '@angular/core';
import { Router } from '@angular/router';
import { of } from 'rxjs';

import { ApiError } from '../../../core/http/api-error';
import { GENERIC_MESSAGES } from '../../../core/http/messages';
import { ToastService } from '../../../core/notifications/toast.service';
import { dataOf, viewState } from '../../../shared/data/view-state';
import { MEMBER_STATUS_DISPLAY } from '../../../shared/models/statuses';
import { ConfirmDialogComponent } from '../../../shared/ui/confirm-dialog.component';
import { DialogComponent } from '../../../shared/ui/dialog.component';
import { ButtonComponent } from '../../../shared/ui/button.component';
import { PageHeaderComponent } from '../../../shared/ui/page-header.component';
import { ErrorStateComponent, LoadingStateComponent } from '../../../shared/ui/view-states.component';
import { MemberFormComponent, MemberFormValue } from '../components/member-form.component';
import { MEMBER_MESSAGES, memberErrorMessage } from '../data-access/member-messages';
import { ExistingMember, MemberDetail } from '../data-access/member.models';
import { MemberService } from '../data-access/member.service';

/** ECR-17 — Inscription (`/gestion/adherents/nouveau`) ou modification (`/:memberNumber/modifier`). */
@Component({
  selector: 'app-member-form-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [PageHeaderComponent, MemberFormComponent, LoadingStateComponent, ErrorStateComponent, ConfirmDialogComponent,
    DialogComponent, ButtonComponent],
  template: `
    <app-page-header [title]="memberNumber() ? 'Modifier l’adhérent' : 'Inscrire un adhérent'" />
    @switch (state().kind) {
      @case ('loading') { <app-loading-state testId="member-form-loading" [rows]="8" /> }
      @case ('error') { <app-error-state [message]="loadError()" testId="member-form-error" (retry)="reload()" /> }
      @default {
        <div class="card max-w-3xl p-6">
          <app-member-form [member]="member()" [busy]="busy()" [serverError]="serverError()"
                           [serverEmailError]="emailError()" (submitted)="save($event)" (cancelled)="cancel($event)" />
        </div>
      }
    }
    <app-dialog title="Adhérent existant" [open]="!!duplicate()" testId="member-duplicate-dialog" (closed)="duplicate.set(null)">
      @if (duplicate(); as existing) {
        <p>
          Un adhérent existe déjà avec ces nom, prénom et date de naissance :
          <strong>{{ existing.memberNumber }} — {{ existing.lastName.toUpperCase() }} {{ existing.firstName }}
            ({{ statusLabel(existing) }})</strong>. Voulez-vous vraiment créer une nouvelle inscription ?
        </p>
        <div class="flex flex-wrap justify-end gap-2">
          <button app-button type="button" variant="secondary" data-testid="member-duplicate-view-existing"
                  (click)="viewExisting(existing.memberNumber)">Voir l’adhérent existant</button>
          <button app-button type="button" [loading]="busy()" data-testid="member-duplicate-confirm"
                  (click)="confirmDuplicate()">Inscrire quand même</button>
        </div>
      }
    </app-dialog>
    <app-confirm-dialog [open]="confirmDiscard()" title="Abandonner les modifications"
                        message="Abandonner les modifications non enregistrées ?" confirmLabel="Abandonner"
                        testId="discard-changes" (confirmed)="leave()" (cancelled)="confirmDiscard.set(false)" />
  `,
})
export class MemberFormPage {
  /** Paramètre de route en modification. */
  readonly memberNumber = input<string>();

  private readonly members = inject(MemberService);
  private readonly router = inject(Router);
  private readonly toasts = inject(ToastService);

  private readonly reloadTick = signal(0);
  private readonly request = computed(() => {
    this.reloadTick();
    return { number: this.memberNumber() ?? null };
  });
  protected readonly state = viewState(this.request, ({ number }) =>
    number ? this.members.get(number) : of<MemberDetail | null>(null));
  protected readonly member = computed(() => dataOf(this.state()));
  protected readonly loadError = computed(() => {
    const state = this.state();
    if (state.kind !== 'error' || !(state.error instanceof ApiError)) {
      return GENERIC_MESSAGES.unexpected;
    }
    return state.error.status === 404 ? MEMBER_MESSAGES.notFound(this.memberNumber() ?? '') : memberErrorMessage(state.error);
  });

  protected readonly busy = signal(false);
  protected readonly serverError = signal<string | null>(null);
  protected readonly emailError = signal<string | null>(null);
  protected readonly duplicate = signal<ExistingMember | null>(null);
  protected readonly confirmDiscard = signal(false);
  private pending: MemberFormValue | null = null;

  constructor() {
    // AC-019-4 : un adhérent archivé ne se modifie pas.
    effect(() => {
      const member = this.member();
      if (member?.status === 'ARCHIVED') {
        this.toasts.warning(MEMBER_MESSAGES.archivedNoEdit);
        void this.router.navigate(['/gestion/adherents', member.memberNumber]);
      }
    });
  }

  protected statusLabel(existing: ExistingMember): string {
    return MEMBER_STATUS_DISPLAY[existing.status].label;
  }

  protected save(value: MemberFormValue, confirmDuplicate = false): void {
    this.pending = value;
    this.busy.set(true);
    this.serverError.set(null);
    this.emailError.set(null);
    const current = this.member();
    const { password, ...fields } = value;
    const request$ = current
      ? this.members.update(current.memberNumber, { ...fields, version: current.version })
      : this.members.register({ ...fields, password: password ?? '', confirmDuplicate });
    request$.subscribe({
      next: (saved) => {
        this.busy.set(false);
        this.duplicate.set(null);
        this.toasts.success(current ? MEMBER_MESSAGES.saved : MEMBER_MESSAGES.registered(saved.memberNumber, saved.expiryDate));
        void this.router.navigate(['/gestion/adherents', saved.memberNumber]);
      },
      error: (error: unknown) => {
        this.busy.set(false);
        if (!(error instanceof ApiError)) {
          this.serverError.set(GENERIC_MESSAGES.unexpected);
        } else if (error.code === 'DUPLICATE_MEMBER_SUSPECTED') {
          this.duplicate.set(error.property('existingMember') as ExistingMember);
        } else if (error.code === 'EMAIL_ALREADY_USED') {
          this.emailError.set(MEMBER_MESSAGES.emailUsed);
        } else {
          this.duplicate.set(null);
          this.serverError.set(memberErrorMessage(error));
        }
      },
    });
  }

  protected confirmDuplicate(): void {
    if (this.pending) {
      this.save(this.pending, true);
    }
  }

  protected viewExisting(memberNumber: string): void {
    this.duplicate.set(null);
    void this.router.navigate(['/gestion/adherents', memberNumber]);
  }

  protected cancel(dirty: boolean): void {
    if (dirty) {
      this.confirmDiscard.set(true);
    } else {
      this.leave();
    }
  }

  protected leave(): void {
    this.confirmDiscard.set(false);
    const number = this.member()?.memberNumber;
    void this.router.navigate(number ? ['/gestion/adherents', number] : ['/gestion/adherents']);
  }

  protected reload(): void {
    this.reloadTick.update((tick) => tick + 1);
  }
}
