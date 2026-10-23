import { Pipe, PipeTransform } from '@angular/core';

const EURO = new Intl.NumberFormat('fr-FR', { minimumFractionDigits: 2, maximumFractionDigits: 2 });

/** Montant en euros avec 2 décimales et virgule : « 1,20 € » (NFR-05). */
export function formatEuro(value: number | null | undefined): string {
  return `${EURO.format(value ?? 0)} €`;
}

/** Saisie décimale en euros (virgule ou point, 2 décimales au plus) → centimes ; `null` si invalide. */
export function parseEuroCents(text: string): number | null {
  const normalized = text.trim().replace(/\s/g, '').replace(',', '.');
  if (!/^\d+(\.\d{1,2})?$/.test(normalized)) {
    return null;
  }
  return Math.round(Number(normalized) * 100);
}

@Pipe({ name: 'euro' })
export class EuroPipe implements PipeTransform {
  transform(value: number | null | undefined): string {
    return formatEuro(value);
  }
}
