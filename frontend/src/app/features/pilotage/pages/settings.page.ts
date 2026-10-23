import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';

import { ApiError } from '../../../core/http/api-error';
import { GENERIC_MESSAGES } from '../../../core/http/messages';
import { ToastService } from '../../../core/notifications/toast.service';
import { dataOf, viewState } from '../../../shared/data/view-state';
import { DateTimeFrPipe } from '../../../shared/pipes/date-fr.pipe';
import { formatEuro, parseEuroCents } from '../../../shared/pipes/euro.pipe';
import { AlertComponent } from '../../../shared/ui/alert.component';
import { ButtonComponent } from '../../../shared/ui/button.component';
import { ConfirmDialogComponent } from '../../../shared/ui/confirm-dialog.component';
import { PageHeaderComponent } from '../../../shared/ui/page-header.component';
import { ErrorStateComponent, LoadingStateComponent } from '../../../shared/ui/view-states.component';
import { commonErrorMessage } from '../../authentification/data-access/auth-messages';
import { SETTING_GROUP_LABELS, SETTING_LABELS, SettingGroup, SettingView, UNIT_LABELS } from '../data-access/pilotage.models';
import { PilotageService } from '../data-access/pilotage.service';

const GROUPS: readonly SettingGroup[] = ['MEMBERSHIP', 'LOANS', 'RESERVATIONS', 'FINES'];

/** ECR-39 — Paramètres des règles de prêt (US-043, RG-40) : une ligne à la fois, avec confirmation. */
@Component({
  selector: 'app-settings-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DateTimeFrPipe, AlertComponent, ButtonComponent, ConfirmDialogComponent, PageHeaderComponent, LoadingStateComponent,
    ErrorStateComponent],
  template: `
    <app-page-header title="Paramètres" />
    <app-alert tone="info" data-testid="settings-info">Une nouvelle valeur s’applique uniquement aux opérations enregistrées après sa modification.</app-alert>
    @switch (state().kind) {
      @case ('loading') { <div class="mt-4"><app-loading-state testId="settings-loading" [rows]="8" /></div> }
      @case ('error') { <div class="mt-4"><app-error-state [message]="genericError" testId="settings-error" (retry)="reload()" /></div> }
      @case ('success') {
        <div class="mt-4 space-y-6">
          @for (group of groups; track group) {
            <fieldset class="card p-4" data-testid="settings-group" [attr.data-group]="group.toLowerCase()">
              <legend class="px-1 font-semibold">{{ groupLabels[group] }}</legend>
              <ul class="divide-y divide-border">
                @for (setting of byGroup(group); track setting.code) {
                  <li class="grid items-center gap-2 py-3 md:grid-cols-[14rem_10rem_1fr_auto]" data-testid="setting-row"
                      [attr.data-row-id]="setting.code">
                    <label [for]="'setting-' + setting.code" class="text-sm font-medium">{{ labels[setting.code] }}</label>
                    <div class="flex items-center gap-2">
                      <input [id]="'setting-' + setting.code" class="field-input w-24 tabular-nums" inputmode="decimal"
                             data-testid="setting-input" [value]="draft(setting)" [attr.aria-invalid]="errorOf(setting) ? true : null"
                             [attr.aria-describedby]="'bounds-' + setting.code" (input)="edit(setting, $event)" />
                      <span class="text-sm text-muted">{{ unitLabels[setting.unit] }}</span>
                    </div>
                    <div class="text-xs text-muted">
                      <p [id]="'bounds-' + setting.code" data-testid="setting-bounds">({{ bound(setting, setting.min) }} à {{ bound(setting, setting.max) }})</p>
                      @if (setting.updatedBy && setting.updatedAt) {
                        <p data-testid="setting-last-modified">modifié le {{ setting.updatedAt | dateTimeFr }} par {{ setting.updatedBy }}</p>
                      }
                      @if (errorOf(setting)) {
                        <p class="text-sm text-danger" data-testid="setting-error">{{ errorOf(setting) }}</p>
                      }
                    </div>
                    <button app-button type="button" variant="secondary" data-testid="setting-save"
                            [disabled]="!changed(setting) || !!localError(setting)" (click)="ask(setting)">Enregistrer</button>
                  </li>
                }
              </ul>
            </fieldset>
          }
        </div>
      }
    }
    <app-confirm-dialog [open]="!!pending()" title="Modifier le paramètre" tone="primary" confirmLabel="Enregistrer"
                        [message]="confirmMessage()" [busy]="busy()" testId="setting-confirm"
                        (confirmed)="save()" (cancelled)="pending.set(null)" />
  `,
})
export class SettingsPage {
  private readonly pilotage = inject(PilotageService);
  private readonly toasts = inject(ToastService);
  protected readonly groups = GROUPS;
  protected readonly groupLabels = SETTING_GROUP_LABELS;
  protected readonly labels = SETTING_LABELS;
  protected readonly unitLabels = UNIT_LABELS;
  protected readonly genericError = GENERIC_MESSAGES.unexpected;
  private readonly tick = signal(0);
  private readonly request = computed(() => ({ tick: this.tick() }));
  protected readonly state = viewState(this.request, () => this.pilotage.settings());
  private readonly settings = computed(() => dataOf(this.state()) ?? []);
  private readonly drafts = signal<Readonly<Record<string, string>>>({});
  private readonly serverErrors = signal<Readonly<Record<string, string>>>({});
  protected readonly pending = signal<{ setting: SettingView; value: number } | null>(null);
  protected readonly busy = signal(false);

  protected readonly confirmMessage = computed(() => {
    const pending = this.pending();
    if (!pending) {
      return '';
    }
    const { setting, value } = pending;
    return `Passer « ${SETTING_LABELS[setting.code]} » de ${this.display(setting, setting.value)} à ${this.display(setting, value)} ? `
      + 'Les opérations déjà enregistrées ne sont pas modifiées.';
  });

  protected byGroup(group: SettingGroup): readonly SettingView[] {
    return this.settings().filter((setting) => setting.group === group);
  }

  protected display(setting: SettingView, value: number): string {
    return setting.valueType === 'MONEY' ? formatEuro(value) : `${value}${setting.unit === 'COUNT' ? '' : ' ' + UNIT_LABELS[setting.unit]}`;
  }

  /** Bornes affichées sans unité (UX-pilotage ECR-39 : « (1 à 36) », « (0,00 à 100,00) »). */
  protected bound(setting: SettingView, value: number): string {
    return setting.valueType === 'MONEY' ? formatEuro(value).replace(/\s?€$/, '') : String(value);
  }

  protected draft(setting: SettingView): string {
    return this.drafts()[setting.code] ?? (setting.valueType === 'MONEY'
      ? formatEuro(setting.value).replace(/\s?€$/, '') : String(setting.value));
  }

  protected edit(setting: SettingView, event: Event): void {
    const value = (event.target as HTMLInputElement).value;
    this.drafts.update((drafts) => ({ ...drafts, [setting.code]: value }));
    this.serverErrors.update((errors) => ({ ...errors, [setting.code]: '' }));
  }

  protected changed(setting: SettingView): boolean {
    const parsed = this.parse(setting);
    return parsed !== null && parsed !== setting.value;
  }

  /** « La valeur doit être comprise entre … et … » / « Saisissez un nombre entier. » / montant invalide (ECR-39). */
  protected localError(setting: SettingView): string | null {
    if (this.drafts()[setting.code] === undefined) {
      return null;
    }
    const parsed = this.parse(setting);
    if (parsed === null) {
      return setting.valueType === 'MONEY' ? 'Saisissez un montant valide (ex. 0,20).' : 'Saisissez un nombre entier.';
    }
    if (parsed < setting.min || parsed > setting.max) {
      return setting.valueType === 'MONEY'
        ? `La valeur doit être comprise entre ${formatEuro(setting.min)} et ${formatEuro(setting.max)}.`
        : `La valeur doit être comprise entre ${setting.min} et ${setting.max}.`;
    }
    return null;
  }

  protected errorOf(setting: SettingView): string | null {
    return this.localError(setting) || this.serverErrors()[setting.code] || null;
  }

  protected ask(setting: SettingView): void {
    const value = this.parse(setting);
    if (value !== null && !this.localError(setting)) {
      this.pending.set({ setting, value });
    }
  }

  protected save(): void {
    const pending = this.pending();
    if (!pending) {
      return;
    }
    this.busy.set(true);
    this.pilotage.updateSetting(pending.setting.code, pending.value, pending.setting.version).subscribe({
      next: () => {
        this.busy.set(false);
        this.pending.set(null);
        this.drafts.update((drafts) => Object.fromEntries(Object.entries(drafts)
          .filter(([code]) => code !== pending.setting.code)));
        this.toasts.success('Paramètre enregistré.');
        this.reload();
      },
      error: (error: unknown) => {
        this.busy.set(false);
        this.pending.set(null);
        const message = error instanceof ApiError ? commonErrorMessage(error) : GENERIC_MESSAGES.unexpected;
        this.serverErrors.update((errors) => ({ ...errors, [pending.setting.code]: message }));
      },
    });
  }

  protected reload(): void {
    this.tick.update((value) => value + 1);
  }

  private parse(setting: SettingView): number | null {
    const text = this.drafts()[setting.code];
    if (text === undefined) {
      return setting.value;
    }
    if (setting.valueType === 'INTEGER') {
      return /^\s*\d+\s*$/.test(text) ? Number(text) : null;
    }
    const cents = parseEuroCents(text);
    return cents === null ? null : cents / 100;
  }
}
