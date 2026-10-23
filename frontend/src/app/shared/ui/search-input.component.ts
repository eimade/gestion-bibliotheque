import { ChangeDetectionStrategy, Component, DestroyRef, inject, input, linkedSignal, output, signal } from '@angular/core';

import { GENERIC_MESSAGES } from '../../core/http/messages';

export const SEARCH_DEBOUNCE_MS = 300;

/**
 * Champ de recherche (UX-socle §5.3) : déclenche sur `Entrée`, le bouton ou après 300 ms sans
 * frappe ; une saisie vide efface le filtre, une saisie trop courte affiche une erreur.
 * `data-testid` : `<testId>-input`, `<testId>-submit`, `<testId>-error`.
 */
@Component({
  selector: 'app-search-input',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="space-y-1">
      <label [for]="testId()" class="block text-sm font-medium" [class.sr-only]="hero()">{{ label() }}</label>
      <div class="flex gap-2" [class]="hero() ? 'rounded-2xl bg-white p-2 shadow-lift ring-1 ring-indigo-100' : ''">
        <input [id]="testId()" type="search" class="field-input" [class]="hero() ? '!border-transparent !py-3 !text-base !shadow-none focus:!ring-0' : ''" [placeholder]="placeholder()"
               [value]="value()" [attr.aria-invalid]="error() ? true : null"
               [attr.aria-describedby]="error() ? testId() + '-error' : null"
               [attr.data-testid]="testId() + '-input'"
               (input)="onInput($event)" (keydown.enter)="submit()" />
        <button type="button" class="shrink-0 rounded-xl px-4 text-sm font-semibold transition motion-reduce:transition-none"
                [class]="hero() ? 'bg-primary px-4 text-white sm:px-6 shadow-sm shadow-primary/30 hover:bg-primary-hover' : 'border border-border bg-surface shadow-sm hover:bg-primary-soft hover:text-primary'"
                [attr.data-testid]="testId() + '-submit'" (click)="submit()">
          Rechercher
        </button>
      </div>
      @if (error()) {
        <p [id]="testId() + '-error'" class="text-sm text-danger" [attr.data-testid]="testId() + '-error'">
          {{ error() }}
        </p>
      }
    </div>
  `,
})
export class SearchInputComponent {
  readonly label = input.required<string>();
  readonly placeholder = input('');
  readonly minLength = input(2);
  readonly testId = input.required<string>();
  /** Valeur affichée au départ (ex. mot-clé repris de l'URL). */
  readonly initialValue = input('');
  /** Grand champ du bandeau d'accueil (libellé masqué visuellement mais lu). */
  readonly hero = input(false);
  readonly search = output<string>();

  protected readonly value = linkedSignal(() => this.initialValue());
  protected readonly error = signal<string | null>(null);
  private timer: ReturnType<typeof setTimeout> | undefined;

  constructor() {
    inject(DestroyRef).onDestroy(() => clearTimeout(this.timer));
  }

  protected onInput(event: Event): void {
    this.value.set((event.target as HTMLInputElement).value);
    clearTimeout(this.timer);
    this.timer = setTimeout(() => this.submit(), SEARCH_DEBOUNCE_MS);
  }

  protected submit(): void {
    clearTimeout(this.timer);
    const term = this.value().trim();
    if (term.length > 0 && term.length < this.minLength()) {
      this.error.set(GENERIC_MESSAGES.minSearchLength(this.minLength()));
      return;
    }
    this.error.set(null);
    this.search.emit(term);
  }
}
