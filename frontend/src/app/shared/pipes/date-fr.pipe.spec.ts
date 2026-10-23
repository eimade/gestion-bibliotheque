import { DateFrPipe, todayInParis } from './date-fr.pipe';

describe('DateFrPipe (NFR-05)', () => {
  const pipe = new DateFrPipe();

  it('reformate une date métier sans conversion de fuseau', () => {
    expect(pipe.transform('2026-01-01')).toBe('01/01/2026');
    expect(pipe.transform('2026-12-31T23:30:00Z')).toBe('31/12/2026');
  });

  it('rend une chaîne vide pour une valeur absente et laisse passer un format inconnu', () => {
    expect(pipe.transform(null)).toBe('');
    expect(pipe.transform(undefined)).toBe('');
    expect(pipe.transform('demain')).toBe('demain');
  });
});

describe('todayInParis (NFR-11)', () => {
  it('donne la date de Paris, même quand UTC est encore la veille', () => {
    expect(todayInParis(new Date('2026-06-30T22:30:00Z'))).toBe('2026-07-01');
    expect(todayInParis(new Date('2026-01-15T10:00:00Z'))).toBe('2026-01-15');
  });
});
