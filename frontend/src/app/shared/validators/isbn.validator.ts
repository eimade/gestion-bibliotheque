import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';

/**
 * RG-07 : ISBN-10 ou ISBN-13 à clé valide, tirets et espaces tolérés ; renvoie l'ISBN-13 normalisé
 * ou `null`. Même algorithme que le serveur (`catalog.service.Isbn`), qui reste juge.
 */
export function toIsbn13(raw: string | null | undefined): string | null {
  if (!raw) {
    return null;
  }
  const compact = raw.replace(/[\s-]/g, '').toUpperCase();
  if (/^97[89]\d{10}$/.test(compact) && isbn13CheckDigit(compact.slice(0, 12)) === compact[12]) {
    return compact;
  }
  if (/^\d{9}[\dX]$/.test(compact) && isValidIsbn10(compact)) {
    const body = `978${compact.slice(0, 9)}`;
    return body + isbn13CheckDigit(body);
  }
  return null;
}

/** Champ facultatif : vide accepté, sinon un ISBN valide (erreur `isbn`). */
export const isbnValidator: ValidatorFn = (control: AbstractControl): ValidationErrors | null => {
  const value = typeof control.value === 'string' ? control.value.trim() : '';
  return !value || toIsbn13(value) ? null : { isbn: true };
};

function isbn13CheckDigit(twelveDigits: string): string {
  let sum = 0;
  for (let i = 0; i < 12; i++) {
    const digit = Number(twelveDigits[i]);
    sum += i % 2 === 0 ? digit : digit * 3;
  }
  return String((10 - (sum % 10)) % 10);
}

function isValidIsbn10(isbn10: string): boolean {
  let sum = 0;
  for (let i = 0; i < 10; i++) {
    const char = isbn10[i];
    if (char === 'X' && i !== 9) {
      return false;
    }
    sum += (char === 'X' ? 10 : Number(char)) * (10 - i);
  }
  return sum % 11 === 0;
}
