import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';

/** RG-01 : longueur minimale et types de caractères (le serveur reste juge, 422 PASSWORD_POLICY_VIOLATION). */
export const PASSWORD_MIN_LENGTH = 12;

export interface PasswordRule {
  readonly id: 'length' | 'uppercase' | 'lowercase' | 'digit';
  readonly label: string;
  readonly test: (value: string) => boolean;
}

export const PASSWORD_RULES: readonly PasswordRule[] = [
  { id: 'length', label: `Au moins ${PASSWORD_MIN_LENGTH} caractères`, test: (v) => v.length >= PASSWORD_MIN_LENGTH },
  { id: 'uppercase', label: 'Une majuscule', test: (v) => /\p{Lu}/u.test(v) },
  { id: 'lowercase', label: 'Une minuscule', test: (v) => /\p{Ll}/u.test(v) },
  { id: 'digit', label: 'Un chiffre', test: (v) => /\d/.test(v) },
];

export const passwordPolicyValidator: ValidatorFn = (control: AbstractControl): ValidationErrors | null => {
  const value = typeof control.value === 'string' ? control.value : '';
  if (!value) {
    return null;
  }
  return PASSWORD_RULES.every((rule) => rule.test(value)) ? null : { passwordPolicy: true };
};

/** Validateur de groupe : `confirmation` doit être identique à `password` (erreur `passwordMismatch`). */
export function matchingPasswordsValidator(passwordKey: string, confirmationKey: string): ValidatorFn {
  return (group: AbstractControl): ValidationErrors | null => {
    const password = group.get(passwordKey)?.value;
    const confirmation = group.get(confirmationKey)?.value;
    return confirmation && password !== confirmation ? { passwordMismatch: true } : null;
  };
}
