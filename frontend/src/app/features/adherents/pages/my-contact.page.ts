import { ChangeDetectionStrategy, Component, computed, effect, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';

import { ApiError } from '../../../core/http/api-error';
import { GENERIC_MESSAGES } from '../../../core/http/messages';
import { ToastService } from '../../../core/notifications/toast.service';
import { dataOf, viewState } from '../../../shared/data/view-state';
import { DateFrPipe } from '../../../shared/pipes/date-fr.pipe';
import { AlertComponent } from '../../../shared/ui/alert.component';
import { ButtonComponent } from '../../../shared/ui/button.component';
import { FormFieldComponent } from '../../../shared/ui/form-field.component';
import { PageHeaderComponent } from '../../../shared/ui/page-header.component';
import { ErrorStateComponent, LoadingStateComponent } from '../../../shared/ui/view-states.component';
import { phoneValidator, postalCodeValidator } from '../../../shared/validators/member.validators';
import { MEMBER_MESSAGES, memberErrorMessage } from '../data-access/member-messages';
import { MemberSpaceService } from '../data-access/member.service';

/** ECR-23 — Mes coordonnées (US-024) : téléphone et adresse ; nom, naissance et e-mail en lecture seule (AC-024-2). */
@Component({
  selector: 'app-my-contact-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, DateFrPipe, AlertComponent, ButtonComponent, FormFieldComponent, PageHeaderComponent,
    LoadingStateComponent, ErrorStateComponent],
  template: `
    <app-page-header title="Mes coordonnées" />
    @switch (state().kind) {
      @case ('loading') { <app-loading-state testId="my-contact-loading" [rows]="5" /> }
      @case ('error') { <app-error-state [message]="genericError" testId="my-contact-error" (retry)="reload()" /> }
      @case ('success') {
        @if (me(); as member) {
          <div class="card max-w-xl space-y-4 p-6">
            <div class="space-y-1 text-sm" data-testid="my-contact-readonly">
              <p>Nom : <strong>{{ member.lastName.toUpperCase() }} {{ member.firstName }}</strong> · Né(e) le {{ member.birthDate | dateFr }}</p>
              <p>E-mail : {{ member.email }}</p>
              <p class="text-muted">Pour modifier ces informations, adressez-vous à l’accueil de la bibliothèque.</p>
            </div>
            <form [formGroup]="form" (ngSubmit)="save(member.version)" novalidate class="space-y-4">
              <app-form-field label="Téléphone" fieldId="my-phone" [error]="phoneError()" errorTestId="my-contact-phone-error">
                <input id="my-phone" type="tel" formControlName="phone" class="field-input" autocomplete="tel"
                       data-testid="my-contact-phone-input" [attr.aria-invalid]="!!phoneError()" />
              </app-form-field>
              <app-form-field label="Adresse" fieldId="my-street" [required]="true" [error]="requiredError('street')">
                <input id="my-street" formControlName="street" class="field-input" autocomplete="street-address"
                       data-testid="my-contact-street-input" />
              </app-form-field>
              <div class="grid gap-4 sm:grid-cols-[10rem_1fr]">
                <app-form-field label="Code postal" fieldId="my-postcode" [required]="true" [error]="postalError()"
                                errorTestId="my-contact-postcode-error">
                  <input id="my-postcode" formControlName="postalCode" class="field-input" autocomplete="postal-code"
                         inputmode="numeric" data-testid="my-contact-postcode-input" [attr.aria-invalid]="!!postalError()" />
                </app-form-field>
                <app-form-field label="Ville" fieldId="my-city" [required]="true" [error]="requiredError('city')">
                  <input id="my-city" formControlName="city" class="field-input" autocomplete="address-level2"
                         data-testid="my-contact-city-input" />
                </app-form-field>
              </div>
              @if (serverError()) {
                <app-alert tone="danger" data-testid="my-contact-server-error">{{ serverError() }}</app-alert>
              }
              <div class="flex justify-end gap-2">
                <button app-button type="button" variant="secondary" data-testid="my-contact-cancel" (click)="leave()">Annuler</button>
                <button app-button type="submit" [loading]="busy()" data-testid="my-contact-submit">Enregistrer</button>
              </div>
            </form>
          </div>
        }
      }
    }
  `,
})
export class MyContactPage {
  private readonly space = inject(MemberSpaceService);
  private readonly toasts = inject(ToastService);
  private readonly router = inject(Router);
  protected readonly genericError = GENERIC_MESSAGES.unexpected;
  private readonly reloadTick = signal(0);
  private readonly request = computed(() => ({ tick: this.reloadTick() }));
  protected readonly state = viewState(this.request, () => this.space.me());
  protected readonly me = computed(() => dataOf(this.state()));
  protected readonly busy = signal(false);
  protected readonly serverError = signal<string | null>(null);
  private readonly attempted = signal(false);

  protected readonly form = inject(NonNullableFormBuilder).group({
    phone: ['', phoneValidator],
    street: ['', [Validators.required, Validators.maxLength(255)]],
    postalCode: ['', [Validators.required, postalCodeValidator]],
    city: ['', [Validators.required, Validators.maxLength(100)]],
  });

  constructor() {
    effect(() => {
      const member = this.me();
      if (member) {
        this.form.reset({ phone: member.phone ?? '', street: member.street, postalCode: member.postalCode, city: member.city });
      }
    });
  }

  protected phoneError(): string | null {
    const control = this.form.controls.phone;
    return (control.touched || this.attempted()) && control.invalid ? MEMBER_MESSAGES.phoneInvalid : null;
  }

  protected postalError(): string | null {
    const control = this.form.controls.postalCode;
    if (!(control.touched || this.attempted()) || control.valid) {
      return null;
    }
    return control.hasError('required') ? MEMBER_MESSAGES.required : MEMBER_MESSAGES.postalCode;
  }

  protected requiredError(field: 'street' | 'city'): string | null {
    const control = this.form.controls[field];
    return (control.touched || this.attempted()) && control.invalid ? MEMBER_MESSAGES.required : null;
  }

  protected save(version: number): void {
    this.attempted.set(true);
    this.form.markAllAsTouched();
    if (this.form.invalid) {
      return;
    }
    const value = this.form.getRawValue();
    this.busy.set(true);
    this.serverError.set(null);
    this.space.updateContact({ phone: value.phone.trim() || null, street: value.street.trim(),
      postalCode: value.postalCode.trim(), city: value.city.trim(), version }).subscribe({
      next: () => {
        this.busy.set(false);
        this.toasts.success(MEMBER_MESSAGES.contactSaved);
        this.reload();
      },
      error: (error: unknown) => {
        this.busy.set(false);
        this.serverError.set(error instanceof ApiError ? memberErrorMessage(error) : GENERIC_MESSAGES.unexpected);
      },
    });
  }

  protected leave(): void {
    void this.router.navigateByUrl('/mon-espace');
  }

  protected reload(): void {
    this.reloadTick.update((tick) => tick + 1);
  }
}
