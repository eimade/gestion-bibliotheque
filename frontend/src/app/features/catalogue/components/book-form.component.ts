import { ChangeDetectionStrategy, Component, computed, effect, inject, input, output, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { GENERIC_MESSAGES } from '../../../core/http/messages';
import { AlertComponent } from '../../../shared/ui/alert.component';
import { ButtonComponent } from '../../../shared/ui/button.component';
import { FormFieldComponent, describedBy } from '../../../shared/ui/form-field.component';
import { isbnValidator } from '../../../shared/validators/isbn.validator';
import { CATALOG_MESSAGES } from '../data-access/catalog-messages';
import { AuthorView, BookAdminDetail, BookRequest, CategoryOption, LanguageOption } from '../data-access/catalog.models';
import { AuthorPickerComponent } from './author-picker.component';
import { CategoryPickerComponent } from './category-picker.component';

export const MIN_YEAR = 1450;

/** ECR-11 — Formulaire de notice, présentationnel (création et modification). */
@Component({
  selector: 'app-book-form',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, FormFieldComponent, AlertComponent, ButtonComponent, AuthorPickerComponent,
    CategoryPickerComponent],
  template: `
    <form [formGroup]="form" (ngSubmit)="submit()" novalidate class="space-y-5">
      <app-form-field label="ISBN" fieldId="book-isbn" hint="ISBN-10 ou ISBN-13, tirets acceptés." [error]="isbnError()"
                      errorTestId="book-form-isbn-error">
        <input id="book-isbn" formControlName="isbn" class="field-input" data-testid="book-form-isbn-input"
               [attr.aria-invalid]="!!isbnError()" [attr.aria-describedby]="describedBy('book-isbn', !!isbnError(), true)" />
      </app-form-field>

      <app-form-field label="Titre" fieldId="book-title" [required]="true" [error]="textError('title', 255)"
                      errorTestId="book-form-title-error">
        <input id="book-title" formControlName="title" class="field-input" data-testid="book-form-title-input"
               [attr.aria-invalid]="!!textError('title', 255)"
               [attr.aria-describedby]="describedBy('book-title', !!textError('title', 255))" />
      </app-form-field>

      <app-form-field label="Sous-titre" fieldId="book-subtitle" [error]="textError('subtitle', 255)">
        <input id="book-subtitle" formControlName="subtitle" class="field-input" data-testid="book-form-subtitle-input" />
      </app-form-field>

      <app-author-picker [(authors)]="authors" [invalid]="showErrors() && authors().length === 0" />
      <app-category-picker [options]="categories()" [(selected)]="categoryIds"
                           [invalid]="showErrors() && categoryIds().length === 0" />

      <div class="grid gap-4 sm:grid-cols-3">
        <app-form-field label="Éditeur" fieldId="book-publisher" [error]="textError('publisher', 150)">
          <input id="book-publisher" formControlName="publisher" class="field-input" data-testid="book-form-publisher-input" />
        </app-form-field>
        <app-form-field label="Année" fieldId="book-year" [error]="yearError()" errorTestId="book-form-year-error">
          <input id="book-year" type="number" inputmode="numeric" formControlName="publicationYear" class="field-input"
                 data-testid="book-form-year-input" [attr.aria-invalid]="!!yearError()"
                 [attr.aria-describedby]="describedBy('book-year', !!yearError())" />
        </app-form-field>
        <app-form-field label="Langue" fieldId="book-language" [required]="true">
          <select id="book-language" formControlName="languageCode" class="field-input" data-testid="book-form-language-select">
            @for (language of languages(); track language.code) {
              <option [value]="language.code">{{ language.label }}</option>
            }
          </select>
        </app-form-field>
      </div>

      <app-form-field label="Nombre de pages" fieldId="book-pages" [error]="pagesError()">
        <input id="book-pages" type="number" inputmode="numeric" formControlName="pageCount" class="field-input sm:w-40"
               data-testid="book-form-pages-input" [attr.aria-invalid]="!!pagesError()" />
      </app-form-field>

      <app-form-field label="Résumé" fieldId="book-summary" [error]="summaryError()">
        <textarea id="book-summary" formControlName="summary" rows="5" class="field-input"
                  data-testid="book-form-summary-input" aria-describedby="book-summary-counter"></textarea>
        <p id="book-summary-counter" class="text-right text-xs text-muted" data-testid="book-form-summary-counter">
          {{ summaryLength() }}/4000
        </p>
      </app-form-field>

      @if (conflict()) {
        <app-alert tone="danger" data-testid="book-form-conflict">
          {{ concurrentMessage }}
          <button type="button" class="ml-2 underline" data-testid="book-form-conflict-reload" (click)="reloadRequested.emit()">
            Recharger
          </button>
        </app-alert>
      } @else if (serverError()) {
        <app-alert tone="danger" data-testid="book-form-server-error">{{ serverError() }}</app-alert>
      }

      <div class="flex justify-end gap-2">
        <button app-button type="button" variant="secondary" data-testid="book-form-cancel" (click)="cancel()">Annuler</button>
        <button app-button type="submit" [loading]="busy()" data-testid="book-form-submit">Enregistrer</button>
      </div>
    </form>
  `,
})
export class BookFormComponent {
  /** Notice à modifier ; absente en création. */
  readonly book = input<BookAdminDetail | null>(null);
  readonly languages = input.required<readonly LanguageOption[]>();
  readonly categories = input.required<readonly CategoryOption[]>();
  readonly busy = input(false);
  readonly serverError = input<string | null>(null);
  /** Erreur d'ISBN renvoyée par le serveur (invalide, déjà présent). */
  readonly serverIsbnError = input<string | null>(null);
  readonly conflict = input(false);
  readonly submitted = output<BookRequest>();
  /** `true` si le formulaire a été modifié (confirmation d'abandon). */
  readonly cancelled = output<boolean>();
  readonly reloadRequested = output<void>();

  protected readonly describedBy = describedBy;
  protected readonly concurrentMessage = GENERIC_MESSAGES.concurrentModification;
  protected readonly maxYear = new Date().getFullYear();
  protected readonly authors = signal<readonly AuthorView[]>([]);
  protected readonly categoryIds = signal<readonly number[]>([]);
  protected readonly showErrors = signal(false);
  private readonly referencesChanged = signal(false);

  protected readonly form = inject(NonNullableFormBuilder).group({
    isbn: ['', isbnValidator],
    title: ['', [Validators.required, Validators.maxLength(255)]],
    subtitle: ['', Validators.maxLength(255)],
    publisher: ['', Validators.maxLength(150)],
    publicationYear: ['', [Validators.min(MIN_YEAR), Validators.max(this.maxYear), Validators.pattern(/^\d*$/)]],
    languageCode: ['fre', Validators.required],
    pageCount: ['', [Validators.min(1), Validators.pattern(/^\d*$/)]],
    summary: ['', Validators.maxLength(4000)],
  });
  private readonly summaryValue = toSignal(this.form.controls.summary.valueChanges, { initialValue: '' });
  protected readonly summaryLength = computed(() => this.summaryValue().length);

  constructor() {
    effect(() => {
      const book = this.book();
      if (book) {
        this.form.reset({
          isbn: book.isbn ?? '',
          title: book.title,
          subtitle: book.subtitle ?? '',
          publisher: book.publisher ?? '',
          publicationYear: book.publicationYear ? String(book.publicationYear) : '',
          languageCode: book.language.code,
          pageCount: book.pageCount ? String(book.pageCount) : '',
          summary: book.summary ?? '',
        });
        this.authors.set(book.authors);
        this.categoryIds.set(book.categories.map((category) => category.id));
        this.referencesChanged.set(false);
      }
    });
    effect(() => {
      this.authors();
      this.categoryIds();
      if (this.form.touched || this.form.dirty) {
        this.referencesChanged.set(true);
      }
    });
  }

  protected isbnError(): string | null {
    if (this.serverIsbnError()) {
      return this.serverIsbnError();
    }
    const control = this.form.controls.isbn;
    return (control.touched || this.showErrors()) && control.hasError('isbn') ? CATALOG_MESSAGES.isbnInvalid : null;
  }

  protected textError(name: 'title' | 'subtitle' | 'publisher', max: number): string | null {
    const control = this.form.controls[name];
    if (!(control.touched || this.showErrors())) {
      return null;
    }
    if (control.hasError('required')) {
      return GENERIC_MESSAGES.required;
    }
    return control.hasError('maxlength') ? CATALOG_MESSAGES.max(max) : null;
  }

  protected yearError(): string | null {
    const control = this.form.controls.publicationYear;
    return (control.touched || this.showErrors()) && control.invalid ? CATALOG_MESSAGES.yearRange(this.maxYear) : null;
  }

  protected pagesError(): string | null {
    const control = this.form.controls.pageCount;
    return (control.touched || this.showErrors()) && control.invalid ? CATALOG_MESSAGES.positiveInteger : null;
  }

  protected summaryError(): string | null {
    return this.form.controls.summary.hasError('maxlength') ? CATALOG_MESSAGES.summaryMax : null;
  }

  protected submit(): void {
    this.showErrors.set(true);
    if (this.form.invalid || this.authors().length === 0 || this.categoryIds().length === 0) {
      this.form.markAllAsTouched();
      return;
    }
    const value = this.form.getRawValue();
    this.submitted.emit({
      isbn: value.isbn.trim() || null,
      title: value.title.trim(),
      subtitle: value.subtitle.trim() || null,
      authorIds: this.authors().map((author) => author.id),
      categoryIds: [...this.categoryIds()],
      publisher: value.publisher.trim() || null,
      publicationYear: value.publicationYear ? Number(value.publicationYear) : null,
      languageCode: value.languageCode,
      pageCount: value.pageCount ? Number(value.pageCount) : null,
      summary: value.summary.trim() || null,
      version: this.book()?.version ?? null,
    });
  }

  protected cancel(): void {
    this.cancelled.emit(this.form.dirty || this.referencesChanged());
  }
}
