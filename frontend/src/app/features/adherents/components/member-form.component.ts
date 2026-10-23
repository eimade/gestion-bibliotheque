import { ChangeDetectionStrategy, Component, computed, effect, inject, input, output, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { DateFrPipe } from '../../../shared/pipes/date-fr.pipe';
import { AlertComponent } from '../../../shared/ui/alert.component';
import { ButtonComponent } from '../../../shared/ui/button.component';
import { FormFieldComponent, describedBy } from '../../../shared/ui/form-field.component';
import { ageOn, pastDateValidator, phoneValidator, postalCodeValidator } from '../../../shared/validators/member.validators';
import { matchingPasswordsValidator, passwordPolicyValidator } from '../../../shared/validators/password-policy.validator';
import { PasswordPolicyChecklistComponent } from '../../authentification/components/password-policy-checklist.component';
import { MEMBER_MESSAGES } from '../data-access/member-messages';
import { MemberDetail, MemberRequest } from '../data-access/member.models';

/** Données émises : fiche + mot de passe initial (inscription seulement). */
export interface MemberFormValue extends MemberRequest {
  readonly password: string | null;
}

/** RG-20 : en dessous de cet âge, le représentant légal est obligatoire. */
const GUARDIAN_AGE = 15;

/**
 * ECR-17 — Formulaire adhérent (présentationnel) : sections Identité, Représentant légal (affichée sous
 * 15 ans, annoncée en `aria-live`), Coordonnées, Compte (inscription uniquement).
 */
@Component({
  selector: 'app-member-form',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, FormFieldComponent, ButtonComponent, AlertComponent, PasswordPolicyChecklistComponent,
    DateFrPipe],
  template: `
    <form [formGroup]="form" (ngSubmit)="submit()" novalidate class="space-y-6">
      @if (member(); as current) {
        <p class="text-sm" data-testid="member-form-number-readonly">
          Numéro <strong>{{ current.memberNumber }}</strong> · inscrit le {{ current.registrationDate | dateFr }}
        </p>
      }
      <fieldset class="space-y-4 rounded-2xl border border-border bg-surface-alt/60 p-5">
        <legend class="px-1 font-semibold">Identité</legend>
        <div class="grid gap-4 sm:grid-cols-2">
          <app-form-field label="Nom" fieldId="member-lastname" [required]="true" [error]="error('lastName')"
                          errorTestId="member-form-lastname-error">
            <input id="member-lastname" formControlName="lastName" class="field-input" autocomplete="family-name"
                   data-testid="member-form-lastname-input" [attr.aria-invalid]="!!error('lastName')"
                   [attr.aria-describedby]="describedBy('member-lastname', !!error('lastName'))" />
          </app-form-field>
          <app-form-field label="Prénom" fieldId="member-firstname" [required]="true" [error]="error('firstName')"
                          errorTestId="member-form-firstname-error">
            <input id="member-firstname" formControlName="firstName" class="field-input" autocomplete="given-name"
                   data-testid="member-form-firstname-input" [attr.aria-invalid]="!!error('firstName')"
                   [attr.aria-describedby]="describedBy('member-firstname', !!error('firstName'))" />
          </app-form-field>
        </div>
        <app-form-field label="Date de naissance" fieldId="member-birthdate" [required]="true" [error]="error('birthDate')"
                        errorTestId="member-form-birthdate-error">
          <div class="flex items-center gap-3">
            <input id="member-birthdate" type="date" formControlName="birthDate" class="field-input w-48" autocomplete="bday"
                   data-testid="member-form-birthdate-input" [attr.aria-invalid]="!!error('birthDate')"
                   [attr.aria-describedby]="describedBy('member-birthdate', !!error('birthDate'))" />
            @if (age() !== null) {
              <span class="text-sm text-muted" data-testid="member-form-age">→ {{ age() }} ans</span>
            }
          </div>
        </app-form-field>
      </fieldset>

      <div aria-live="polite">
        @if (guardianRequired() || hasGuardian()) {
          <fieldset class="space-y-4 rounded-2xl border border-border bg-surface-alt/60 p-5" formGroupName="guardian"
                    data-testid="member-form-guardian-section">
            <legend class="px-1 font-semibold">
              Représentant légal {{ guardianRequired() ? '(obligatoire : moins de 15 ans)' : '' }}
            </legend>
            <div class="grid gap-4 sm:grid-cols-3">
              <app-form-field label="Nom" fieldId="guardian-lastname" [required]="guardianRequired()">
                <input id="guardian-lastname" formControlName="lastName" class="field-input"
                       data-testid="member-form-guardian-lastname-input" />
              </app-form-field>
              <app-form-field label="Prénom" fieldId="guardian-firstname" [required]="guardianRequired()">
                <input id="guardian-firstname" formControlName="firstName" class="field-input"
                       data-testid="member-form-guardian-firstname-input" />
              </app-form-field>
              <app-form-field label="Téléphone" fieldId="guardian-phone" [required]="guardianRequired()">
                <input id="guardian-phone" type="tel" formControlName="phone" class="field-input"
                       data-testid="member-form-guardian-phone-input" />
              </app-form-field>
            </div>
            @if (guardianError()) {
              <p class="text-sm text-danger" data-testid="member-form-guardian-error">{{ guardianError() }}</p>
            }
          </fieldset>
        }
      </div>

      <fieldset class="space-y-4 rounded-2xl border border-border bg-surface-alt/60 p-5">
        <legend class="px-1 font-semibold">Coordonnées</legend>
        <div class="grid gap-4 sm:grid-cols-2">
          <app-form-field label="E-mail" fieldId="member-email" [required]="true" [error]="emailError()"
                          errorTestId="member-form-email-error">
            <input id="member-email" type="email" formControlName="email" class="field-input" autocomplete="email"
                   data-testid="member-form-email-input" [attr.aria-invalid]="!!emailError()"
                   [attr.aria-describedby]="describedBy('member-email', !!emailError())" />
          </app-form-field>
          <app-form-field label="Téléphone" fieldId="member-phone" [error]="error('phone')" errorTestId="member-form-phone-error">
            <input id="member-phone" type="tel" formControlName="phone" class="field-input" autocomplete="tel"
                   data-testid="member-form-phone-input" [attr.aria-invalid]="!!error('phone')"
                   [attr.aria-describedby]="describedBy('member-phone', !!error('phone'))" />
          </app-form-field>
        </div>
        <app-form-field label="Adresse" fieldId="member-street" [required]="true" [error]="error('street')">
          <input id="member-street" formControlName="street" class="field-input" autocomplete="street-address"
                 data-testid="member-form-street-input" [attr.aria-invalid]="!!error('street')" />
        </app-form-field>
        <div class="grid gap-4 sm:grid-cols-[10rem_1fr]">
          <app-form-field label="Code postal" fieldId="member-postcode" [required]="true" [error]="error('postalCode')"
                          errorTestId="member-form-postcode-error">
            <input id="member-postcode" formControlName="postalCode" class="field-input" autocomplete="postal-code"
                   inputmode="numeric" data-testid="member-form-postcode-input" [attr.aria-invalid]="!!error('postalCode')"
                   [attr.aria-describedby]="describedBy('member-postcode', !!error('postalCode'))" />
          </app-form-field>
          <app-form-field label="Ville" fieldId="member-city" [required]="true" [error]="error('city')">
            <input id="member-city" formControlName="city" class="field-input" autocomplete="address-level2"
                   data-testid="member-form-city-input" [attr.aria-invalid]="!!error('city')" />
          </app-form-field>
        </div>
      </fieldset>

      @if (!member()) {
        <fieldset class="space-y-4 rounded-2xl border border-border bg-surface-alt/60 p-5">
          <legend class="px-1 font-semibold">Compte de l’adhérent</legend>
          <app-form-field label="Mot de passe initial" fieldId="member-password" [required]="true" [error]="error('password')"
                          errorTestId="member-form-password-error">
            <input id="member-password" type="password" formControlName="password" class="field-input"
                   autocomplete="new-password" data-testid="member-form-password-input"
                   [attr.aria-invalid]="!!error('password')" />
          </app-form-field>
          <app-password-policy-checklist [password]="passwordValue()" />
          <app-form-field label="Confirmer" fieldId="member-confirm" [required]="true" [error]="confirmError()"
                          errorTestId="member-form-confirm-error">
            <input id="member-confirm" type="password" formControlName="confirmation" class="field-input"
                   autocomplete="new-password" data-testid="member-form-confirm-input" [attr.aria-invalid]="!!confirmError()" />
          </app-form-field>
          <app-alert tone="info">À communiquer à l’adhérent ; il devra le changer à sa première connexion.</app-alert>
        </fieldset>
      }

      @if (serverError()) {
        <app-alert tone="danger" data-testid="member-form-server-error">{{ serverError() }}</app-alert>
      }
      <div class="flex justify-end gap-2">
        <button app-button type="button" variant="secondary" data-testid="member-form-cancel"
                (click)="cancelled.emit(form.dirty)">Annuler</button>
        <button app-button type="submit" [loading]="busy()" data-testid="member-form-submit">
          {{ member() ? 'Enregistrer' : 'Inscrire' }}
        </button>
      </div>
    </form>
  `,
})
export class MemberFormComponent {
  readonly member = input<MemberDetail | null>(null);
  readonly busy = input(false);
  readonly serverError = input<string | null>(null);
  readonly serverEmailError = input<string | null>(null);
  readonly submitted = output<MemberFormValue>();
  readonly cancelled = output<boolean>();

  protected readonly describedBy = describedBy;
  private readonly attempted = signal(false);
  private readonly fb = inject(NonNullableFormBuilder);

  protected readonly form = this.fb.group({
    lastName: ['', [Validators.required, Validators.maxLength(100)]],
    firstName: ['', [Validators.required, Validators.maxLength(100)]],
    birthDate: ['', [Validators.required, pastDateValidator]],
    guardian: this.fb.group({
      lastName: ['', Validators.maxLength(100)],
      firstName: ['', Validators.maxLength(100)],
      phone: ['', phoneValidator],
    }),
    email: ['', [Validators.required, Validators.email, Validators.maxLength(254)]],
    phone: ['', phoneValidator],
    street: ['', [Validators.required, Validators.maxLength(255)]],
    postalCode: ['', [Validators.required, postalCodeValidator]],
    city: ['', [Validators.required, Validators.maxLength(100)]],
    password: ['', passwordPolicyValidator],
    confirmation: [''],
  }, { validators: matchingPasswordsValidator('password', 'confirmation') });

  private readonly values = toSignal(this.form.valueChanges, { initialValue: this.form.getRawValue() });
  protected readonly age = computed(() => ageOn(this.values().birthDate));
  protected readonly guardianRequired = computed(() => {
    const age = this.age();
    return age !== null && age >= 0 && age < GUARDIAN_AGE;
  });
  protected readonly hasGuardian = computed(() => !!this.values().guardian?.lastName);
  protected readonly passwordValue = computed(() => this.values().password ?? '');

  constructor() {
    effect(() => {
      const member = this.member();
      if (member) {
        this.form.reset({
          lastName: member.lastName ?? '', firstName: member.firstName ?? '', birthDate: member.birthDate ?? '',
          guardian: { lastName: member.guardian?.lastName ?? '', firstName: member.guardian?.firstName ?? '',
            phone: member.guardian?.phone ?? '' },
          email: member.email ?? '', phone: member.phone ?? '', street: member.street ?? '',
          postalCode: member.postalCode, city: member.city ?? '', password: '', confirmation: '',
        });
      }
    });
  }

  protected error(field: 'lastName' | 'firstName' | 'birthDate' | 'phone' | 'street' | 'postalCode' | 'city' | 'password'): string | null {
    const control = this.form.controls[field];
    if (!(control.touched || this.attempted()) || control.valid) {
      if (field === 'password' && !this.member() && this.attempted() && !control.value) {
        return MEMBER_MESSAGES.required;
      }
      return null;
    }
    if (control.hasError('required')) {
      return MEMBER_MESSAGES.required;
    }
    switch (field) {
      case 'birthDate':
        return MEMBER_MESSAGES.birthDateFuture;
      case 'phone':
        return MEMBER_MESSAGES.phoneInvalid;
      case 'postalCode':
        return MEMBER_MESSAGES.postalCode;
      case 'password':
        return MEMBER_MESSAGES.passwordPolicy;
      default:
        return MEMBER_MESSAGES.max100;
    }
  }

  protected emailError(): string | null {
    const control = this.form.controls.email;
    if ((control.touched || this.attempted()) && control.invalid) {
      return control.hasError('required') ? MEMBER_MESSAGES.required : MEMBER_MESSAGES.emailInvalid;
    }
    return this.serverEmailError();
  }

  protected confirmError(): string | null {
    if (this.member() || !(this.form.controls.confirmation.touched || this.attempted())) {
      return null;
    }
    if (!this.form.controls.confirmation.value) {
      return MEMBER_MESSAGES.required;
    }
    return this.form.hasError('passwordMismatch') ? MEMBER_MESSAGES.passwordMismatch : null;
  }

  /** RG-20 : nom, prénom et téléphone du représentant légal, tous trois obligatoires sous 15 ans. */
  protected guardianError(): string | null {
    if (!this.attempted()) {
      return null;
    }
    const guardian = this.form.controls.guardian;
    const { lastName, firstName, phone } = guardian.getRawValue();
    const anyFilled = !!(lastName.trim() || firstName.trim() || phone.trim());
    if ((this.guardianRequired() || anyFilled) && !(lastName.trim() && firstName.trim() && phone.trim())) {
      return MEMBER_MESSAGES.guardianRequired;
    }
    return guardian.controls.phone.invalid ? MEMBER_MESSAGES.phoneInvalid : null;
  }

  protected submit(): void {
    this.attempted.set(true);
    this.form.markAllAsTouched();
    const creating = !this.member();
    const passwordMissing = creating && !this.form.controls.password.value;
    if (this.form.invalid || passwordMissing || this.guardianError() || (creating && this.confirmError())) {
      return;
    }
    const value = this.form.getRawValue();
    const guardian = value.guardian.lastName.trim()
      ? { lastName: value.guardian.lastName.trim(), firstName: value.guardian.firstName.trim(), phone: value.guardian.phone.trim() }
      : null;
    this.submitted.emit({
      lastName: value.lastName.trim(), firstName: value.firstName.trim(), birthDate: value.birthDate, guardian,
      email: value.email.trim(), phone: value.phone.trim() || null, street: value.street.trim(),
      postalCode: value.postalCode.trim(), city: value.city.trim(), password: creating ? value.password : null,
    });
  }
}
