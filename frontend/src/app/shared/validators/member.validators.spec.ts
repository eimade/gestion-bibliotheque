import { FormControl } from '@angular/forms';

import { formatEuro, parseEuroCents } from '../pipes/euro.pipe';
import { ageOn, pastDateValidator, phoneValidator, postalCodeValidator } from './member.validators';

describe('Validateurs adhérents (Q-24, RG-20)', () => {
  it('accepte un téléphone français ou international, espaces compris', () => {
    expect(phoneValidator(new FormControl('06 12 34 56 78'))).toBeNull();
    expect(phoneValidator(new FormControl('+33612345678'))).toBeNull();
    expect(phoneValidator(new FormControl(''))).toBeNull();
    expect(phoneValidator(new FormControl('12345'))).toEqual({ phone: true });
    expect(phoneValidator(new FormControl('+123'))).toEqual({ phone: true });
  });

  it('exige un code postal de 5 chiffres', () => {
    expect(postalCodeValidator(new FormControl('75011'))).toBeNull();
    expect(postalCodeValidator(new FormControl('750'))).toEqual({ postalCode: true });
  });

  it('refuse une date de naissance qui n’est pas passée', () => {
    expect(pastDateValidator(new FormControl('1990-03-12'))).toBeNull();
    expect(pastDateValidator(new FormControl('2999-01-01'))).toEqual({ notPast: true });
  });

  it('calcule l’âge révolu, 15 ans exactement le jour anniversaire (AC-016-4)', () => {
    expect(ageOn('2011-10-17', '2026-10-17')).toBe(15);
    expect(ageOn('2011-10-18', '2026-10-17')).toBe(14);
    expect(ageOn('2016-10-17', '2026-10-17')).toBe(10);
    expect(ageOn('', '2026-10-17')).toBeNull();
  });
});

describe('Montants en euros (NFR-05)', () => {
  it('formate avec virgule et 2 décimales', () => {
    expect(formatEuro(1.2).replace(/\s/g, ' ')).toBe('1,20 €');
    expect(formatEuro(null).replace(/\s/g, ' ')).toBe('0,00 €');
  });

  it('lit une saisie avec virgule ou point en centimes', () => {
    expect(parseEuroCents('21,00')).toBe(2100);
    expect(parseEuroCents('0.2')).toBe(20);
    expect(parseEuroCents('1,234')).toBeNull();
    expect(parseEuroCents('abc')).toBeNull();
  });
});
