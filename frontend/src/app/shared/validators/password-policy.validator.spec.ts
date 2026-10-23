import { FormControl, FormGroup } from '@angular/forms';

import { matchingPasswordsValidator, passwordPolicyValidator } from './password-policy.validator';

describe('passwordPolicyValidator (RG-01)', () => {
  const check = (value: string) => passwordPolicyValidator(new FormControl(value));

  it('accepte 12 caractères avec majuscule, minuscule et chiffre', () => {
    expect(check('Abcdefghijk1')).toBeNull();
  });

  it.each([
    ['11 caractères (limite − 1)', 'Abcdefghij1'],
    ['sans majuscule', 'abcdefghijk1'],
    ['sans minuscule', 'ABCDEFGHIJK1'],
    ['sans chiffre', 'Abcdefghijkl'],
  ])('refuse un mot de passe %s', (_label, value) => {
    expect(check(value)).toEqual({ passwordPolicy: true });
  });

  it('laisse la saisie vide au validateur « obligatoire »', () => {
    expect(check('')).toBeNull();
  });
});

describe('matchingPasswordsValidator', () => {
  const group = (password: string, confirmation: string) =>
    new FormGroup(
      { password: new FormControl(password), confirmation: new FormControl(confirmation) },
      { validators: matchingPasswordsValidator('password', 'confirmation') },
    );

  it('signale une confirmation différente', () => {
    expect(group('Abcdefghijk1', 'Abcdefghijk2').errors).toEqual({ passwordMismatch: true });
  });

  it('accepte une confirmation identique', () => {
    expect(group('Abcdefghijk1', 'Abcdefghijk1').errors).toBeNull();
  });
});
