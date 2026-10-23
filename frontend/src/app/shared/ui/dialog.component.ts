import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  effect,
  input,
  output,
  viewChild,
} from '@angular/core';

let nextDialogId = 1;

/**
 * Boîte de dialogue modale sur l'élément natif `<dialog>` (UX-socle §5.3) : piège du focus,
 * `Échap` (émet `closed`) et retour du focus à l'élément déclencheur sont fournis par le navigateur.
 * Le parent pilote l'ouverture avec `open`.
 */
@Component({
  selector: 'app-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <dialog #dialog
            class="w-full max-w-lg rounded-2xl bg-surface p-0 text-ink shadow-2xl backdrop:bg-indigo-950/50 backdrop:backdrop-blur-sm max-sm:m-0 max-sm:h-full max-sm:max-h-none max-sm:max-w-none"
            [attr.aria-labelledby]="titleId"
            [attr.data-testid]="testId()"
            (cancel)="onCancel($event)">
      <div class="space-y-4 p-6">
        <h2 [id]="titleId" class="text-lg font-bold">{{ title() }}</h2>
        <ng-content />
      </div>
    </dialog>
  `,
})
export class DialogComponent {
  readonly title = input.required<string>();
  readonly open = input(false);
  readonly testId = input<string>();
  readonly closed = output<void>();

  protected readonly titleId = `dialog-title-${nextDialogId++}`;
  private readonly dialog = viewChild.required<ElementRef<HTMLDialogElement>>('dialog');

  constructor() {
    effect(() => {
      const element = this.dialog().nativeElement;
      if (this.open() && !element.open) {
        if (typeof element.showModal === 'function') {
          element.showModal();
        } else {
          element.setAttribute('open', '');
        }
      } else if (!this.open() && element.open) {
        if (typeof element.close === 'function') {
          element.close();
        } else {
          element.removeAttribute('open');
        }
      }
    });
  }

  protected onCancel(event: Event): void {
    event.preventDefault();
    this.closed.emit();
  }
}
