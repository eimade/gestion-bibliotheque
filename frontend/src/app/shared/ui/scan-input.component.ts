import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  afterNextRender,
  effect,
  input,
  output,
  signal,
  viewChild,
} from '@angular/core';

/**
 * Champ optimisé pour le lecteur de code-barres (UX-socle §5.3) : focus automatique, validation sur `Entrée`,
 * vidé et refocalisé après un scan valide. Un format invalide affiche `invalidMessage` sans émettre.
 * `data-testid` : `<testId>-input`, `<testId>-error`.
 */
@Component({
  selector: 'app-scan-input',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="space-y-1">
      <label [for]="testId()" class="block text-sm font-medium">{{ label() }}</label>
      <input #field [id]="testId()" class="field-input font-mono" autocomplete="off" inputmode="text"
             [attr.data-testid]="testId() + '-input'" [disabled]="disabled()" [attr.placeholder]="placeholder()"
             [attr.aria-invalid]="error() ? true : null" [attr.aria-describedby]="error() ? testId() + '-error' : null"
             (input)="error.set(null)" (keydown.enter)="submit($event)" (keydown.escape)="escaped.emit()" />
      @if (error()) {
        <p [id]="testId() + '-error'" class="text-sm text-danger" role="alert" [attr.data-testid]="testId() + '-error'">
          {{ error() }}
        </p>
      }
    </div>
  `,
})
export class ScanInputComponent {
  readonly label = input.required<string>();
  readonly pattern = input.required<RegExp>();
  readonly invalidMessage = input.required<string>();
  readonly testId = input.required<string>();
  readonly placeholder = input<string>();
  readonly disabled = input(false);
  /** Incrémenté par le parent pour redonner le focus (ex. après un refus serveur). */
  readonly focusTick = input(0);
  readonly scanned = output<string>();
  readonly escaped = output<void>();

  protected readonly error = signal<string | null>(null);
  private readonly field = viewChild.required<ElementRef<HTMLInputElement>>('field');

  constructor() {
    afterNextRender(() => this.focus());
    effect(() => {
      this.focusTick();
      if (!this.disabled()) {
        queueMicrotask(() => this.focus());
      }
    });
  }

  focus(): void {
    this.field().nativeElement.focus();
  }

  protected submit(event: Event): void {
    event.preventDefault();
    const element = this.field().nativeElement;
    const value = element.value.trim().toUpperCase();
    if (!value) {
      return;
    }
    if (!this.pattern().test(value)) {
      this.error.set(this.invalidMessage());
      return;
    }
    element.value = '';
    this.scanned.emit(value);
  }
}
