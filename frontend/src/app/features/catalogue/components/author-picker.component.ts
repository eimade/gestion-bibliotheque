import { ChangeDetectionStrategy, Component, DestroyRef, inject, input, model, signal } from '@angular/core';

import { ApiError } from '../../../core/http/api-error';
import { GENERIC_MESSAGES } from '../../../core/http/messages';
import { AuthorService } from '../data-access/author.service';
import { AuthorView, authorName } from '../data-access/catalog.models';

export const AUTHOR_SEARCH_DEBOUNCE_MS = 300;

/**
 * Sélection ordonnée des auteurs d'une notice (ECR-11) : suggestions (`role=combobox`), création à la
 * volée (AC-009-2), réordonnancement par boutons ↑ ↓ annoncé aux lecteurs d'écran.
 */
@Component({
  selector: 'app-author-picker',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fieldset class="space-y-2" data-testid="book-form-authors-picker">
      <legend class="text-sm font-medium">Auteur(s) <span class="text-danger" aria-hidden="true">*</span>
        <span class="sr-only">(obligatoire)</span></legend>
      @if (authors().length) {
        <ol class="flex flex-wrap gap-2">
          @for (author of authors(); track author.id; let i = $index, last = $last) {
            <li class="flex items-center gap-1 rounded-full bg-primary-soft px-3 py-1 text-sm" data-testid="book-form-author-chip">
              <span>{{ name(author) }}</span>
              <button type="button" class="px-1" [disabled]="i === 0" data-testid="book-form-author-up"
                      [attr.aria-label]="'Monter ' + name(author)" (click)="move(i, -1)"><span aria-hidden="true">↑</span></button>
              <button type="button" class="px-1" [disabled]="last" data-testid="book-form-author-down"
                      [attr.aria-label]="'Descendre ' + name(author)" (click)="move(i, 1)"><span aria-hidden="true">↓</span></button>
              <button type="button" class="px-1" [attr.aria-label]="'Retirer ' + name(author)" (click)="remove(i)">
                <span aria-hidden="true">×</span>
              </button>
            </li>
          }
        </ol>
      }
      <div class="relative">
        <label for="author-search" class="sr-only">Ajouter un auteur</label>
        <input id="author-search" class="field-input" placeholder="Ajouter un auteur…" autocomplete="off"
               role="combobox" aria-autocomplete="list" aria-controls="author-options"
               [attr.aria-expanded]="open()" [attr.aria-invalid]="invalid() || null"
               [attr.aria-describedby]="invalid() ? 'book-form-authors-error' : null"
               [value]="term()" (input)="onInput($event)" (keydown.escape)="open.set(false)" />
        @if (open()) {
          <ul id="author-options" role="listbox" class="absolute z-10 mt-1 w-full rounded-xl border border-border bg-surface p-1 shadow-lift">
            @for (option of suggestions(); track option.id) {
              <li role="option" aria-selected="false">
                <button type="button" class="block w-full px-3 py-2 text-left text-sm hover:bg-surface-alt"
                        data-testid="book-form-author-option" (click)="add(option)">{{ name(option) }}</button>
              </li>
            }
            <li role="option" aria-selected="false">
              <button type="button" class="block w-full px-3 py-2 text-left text-sm font-medium text-primary hover:bg-surface-alt"
                      data-testid="book-form-author-create-option" (click)="startCreate()">
                Créer l'auteur « {{ term() }} »
              </button>
            </li>
          </ul>
        }
      </div>
      @if (creating()) {
        <div class="grid gap-2 rounded-xl border border-border p-3 sm:grid-cols-[1fr_1fr_auto]" data-testid="book-form-author-create">
          <div>
            <label for="new-author-last" class="block text-xs font-medium">Nom *</label>
            <input id="new-author-last" class="field-input" data-testid="book-form-new-author-lastname"
                   [value]="newLastName()" (input)="newLastName.set(value($event))" />
          </div>
          <div>
            <label for="new-author-first" class="block text-xs font-medium">Prénom</label>
            <input id="new-author-first" class="field-input" data-testid="book-form-new-author-firstname"
                   [value]="newFirstName()" (input)="newFirstName.set(value($event))" />
          </div>
          <div class="flex items-end gap-2">
            <button type="button" class="rounded-md bg-primary px-3 py-2 text-sm text-surface"
                    data-testid="book-form-new-author-save" (click)="create()">Créer</button>
            <button type="button" class="rounded-xl border border-border bg-surface px-3 py-2 text-sm font-medium shadow-sm hover:bg-primary-soft hover:text-primary" (click)="creating.set(false)">Annuler</button>
          </div>
          @if (createError()) {
            <p class="text-sm text-danger sm:col-span-3">{{ createError() }}</p>
          }
        </div>
      }
      @if (invalid()) {
        <p id="book-form-authors-error" class="text-sm text-danger" data-testid="book-form-authors-error">
          <span aria-hidden="true">×</span> Ajoutez au moins un auteur.
        </p>
      }
      <p class="sr-only" aria-live="polite">{{ announcement() }}</p>
    </fieldset>
  `,
})
export class AuthorPickerComponent {
  readonly authors = model<readonly AuthorView[]>([]);
  readonly invalid = input(false);

  private readonly service = inject(AuthorService);
  protected readonly name = authorName;
  protected readonly term = signal('');
  protected readonly suggestions = signal<readonly AuthorView[]>([]);
  protected readonly open = signal(false);
  protected readonly creating = signal(false);
  protected readonly newLastName = signal('');
  protected readonly newFirstName = signal('');
  protected readonly createError = signal<string | null>(null);
  protected readonly announcement = signal('');
  private timer: ReturnType<typeof setTimeout> | undefined;

  constructor() {
    inject(DestroyRef).onDestroy(() => clearTimeout(this.timer));
  }

  protected value(event: Event): string {
    return (event.target as HTMLInputElement).value;
  }

  protected onInput(event: Event): void {
    const term = this.value(event);
    this.term.set(term);
    clearTimeout(this.timer);
    if (term.trim().length < 2) {
      this.open.set(false);
      return;
    }
    this.timer = setTimeout(() => {
      this.service.suggest(term.trim()).subscribe({
        next: (authors) => {
          this.suggestions.set(authors.filter((a) => !this.authors().some((selected) => selected.id === a.id)));
          this.open.set(true);
        },
        error: () => this.open.set(false),
      });
    }, AUTHOR_SEARCH_DEBOUNCE_MS);
  }

  protected add(author: AuthorView): void {
    this.authors.update((list) => [...list, author]);
    this.term.set('');
    this.open.set(false);
    this.announcement.set(`${authorName(author)} ajouté.`);
  }

  protected startCreate(): void {
    this.newLastName.set(this.term().trim());
    this.newFirstName.set('');
    this.createError.set(null);
    this.creating.set(true);
    this.open.set(false);
  }

  protected create(): void {
    const lastName = this.newLastName().trim();
    if (!lastName) {
      this.createError.set(GENERIC_MESSAGES.required);
      return;
    }
    this.service.create(lastName, this.newFirstName().trim() || null).subscribe({
      next: (author) => {
        this.creating.set(false);
        this.add(author);
      },
      error: (error: unknown) =>
        this.createError.set(error instanceof ApiError ? error.detail : GENERIC_MESSAGES.unexpected),
    });
  }

  protected move(index: number, step: number): void {
    const list = [...this.authors()];
    const [author] = list.splice(index, 1);
    list.splice(index + step, 0, author);
    this.authors.set(list);
    this.announcement.set(`${authorName(author)} en position ${index + step + 1}.`);
  }

  protected remove(index: number): void {
    const removed = this.authors()[index];
    this.authors.update((list) => list.filter((_, i) => i !== index));
    this.announcement.set(`${authorName(removed)} retiré.`);
  }
}
