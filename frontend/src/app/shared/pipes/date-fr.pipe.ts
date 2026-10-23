import { Pipe, PipeTransform } from '@angular/core';

/**
 * Date métier `AAAA-MM-JJ` → `JJ/MM/AAAA` (NFR-05). Une date métier n'a pas de fuseau : elle est
 * reformatée sans conversion, pour ne jamais décaler d'un jour.
 */
export function formatDateFr(value: string | null | undefined): string {
  if (!value) {
    return '';
  }
  const match = /^(\d{4})-(\d{2})-(\d{2})/.exec(value);
  return match ? `${match[3]}/${match[2]}/${match[1]}` : value;
}

@Pipe({ name: 'dateFr' })
export class DateFrPipe implements PipeTransform {
  transform(value: string | null | undefined): string {
    return formatDateFr(value);
  }
}

const DATE_TIME = new Intl.DateTimeFormat('fr-FR', {
  day: '2-digit',
  month: '2-digit',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
  timeZone: 'Europe/Paris',
});

/** Instant ISO → « JJ/MM/AAAA HH:MM » à l'heure de Paris (NFR-11). */
@Pipe({ name: 'dateTimeFr' })
export class DateTimeFrPipe implements PipeTransform {
  transform(value: string | null | undefined): string {
    if (!value) {
      return '';
    }
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? value : DATE_TIME.format(date);
  }
}

/** Date du jour à l'heure de Paris, au format `AAAA-MM-JJ` (NFR-11). */
export function todayInParis(now: Date = new Date()): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'Europe/Paris' }).format(now);
}

/** Nombre de jours calendaires de `from` à `to` (dates `AAAA-MM-JJ`). */
export function daysBetween(from: string, to: string): number {
  return Math.round((Date.parse(`${to}T00:00:00Z`) - Date.parse(`${from}T00:00:00Z`)) / 86_400_000);
}
