import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';

import { todayInParis } from '../pipes/date-fr.pipe';

/** Q-24 : français (10 chiffres commençant par 0) ou international (+ et 8 à 15 chiffres), espaces tolérés. */
export const PHONE_PATTERN = /^(?:0(?:\s*\d){9}|\+(?:\s*\d){8,15})$/;

/** Téléphone facultatif : vide accepté, sinon format Q-24 (erreur `phone`). */
export const phoneValidator: ValidatorFn = (control: AbstractControl): ValidationErrors | null => {
  const value = typeof control.value === 'string' ? control.value.trim() : '';
  return !value || PHONE_PATTERN.test(value) ? null : { phone: true };
};

/** Code postal français de 5 chiffres (erreur `postalCode`). */
export const postalCodeValidator: ValidatorFn = (control: AbstractControl): ValidationErrors | null => {
  const value = typeof control.value === 'string' ? control.value.trim() : '';
  return !value || /^\d{5}$/.test(value) ? null : { postalCode: true };
};

/** Date `AAAA-MM-JJ` strictement antérieure au jour (heure de Paris) ; erreur `notPast`. */
export const pastDateValidator: ValidatorFn = (control: AbstractControl): ValidationErrors | null => {
  const value = typeof control.value === 'string' ? control.value : '';
  return !value || value < todayInParis() ? null : { notPast: true };
};

/** Âge révolu à la date du jour, à partir d'une date `AAAA-MM-JJ` ; `null` si la date est vide ou invalide. */
export function ageOn(birthDate: string | null | undefined, today: string = todayInParis()): number | null {
  const birth = /^(\d{4})-(\d{2})-(\d{2})$/.exec(birthDate ?? '');
  const now = /^(\d{4})-(\d{2})-(\d{2})$/.exec(today);
  if (!birth || !now) {
    return null;
  }
  let age = Number(now[1]) - Number(birth[1]);
  if (now[2] + now[3] < birth[2] + birth[3]) {
    age--;
  }
  return age;
}
