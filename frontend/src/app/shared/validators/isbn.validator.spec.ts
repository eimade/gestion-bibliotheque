import { FormControl } from '@angular/forms';

import { isbnValidator, toIsbn13 } from './isbn.validator';

describe('toIsbn13 (RG-07)', () => {
  it('normalise un ISBN-13 avec tirets et espaces', () => {
    expect(toIsbn13('978-2-07-036002-4')).toBe('9782070360024');
    expect(toIsbn13(' 978 2070360024 ')).toBe('9782070360024');
  });

  it('convertit un ISBN-10, y compris avec la clé X', () => {
    expect(toIsbn13('2-07-036002-4')).toBe('9782070360024');
    expect(toIsbn13('080442957X')).toBe('9780804429573');
  });

  it('refuse une clé fausse, une longueur incorrecte ou un X mal placé', () => {
    expect(toIsbn13('9782070360025')).toBeNull();
    expect(toIsbn13('2070360025')).toBeNull();
    expect(toIsbn13('12345')).toBeNull();
    expect(toIsbn13('X804429570')).toBeNull();
    expect(toIsbn13('')).toBeNull();
    expect(toIsbn13(null)).toBeNull();
  });
});

describe('isbnValidator', () => {
  it('accepte un champ vide (ISBN facultatif)', () => {
    expect(isbnValidator(new FormControl(''))).toBeNull();
    expect(isbnValidator(new FormControl('   '))).toBeNull();
  });

  it('signale un ISBN invalide', () => {
    expect(isbnValidator(new FormControl('978-2-07-036002-5'))).toEqual({ isbn: true });
    expect(isbnValidator(new FormControl('978-2-07-036002-4'))).toBeNull();
  });
});
