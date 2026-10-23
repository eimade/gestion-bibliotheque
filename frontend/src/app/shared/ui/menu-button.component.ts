import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  inject,
  input,
  output,
  signal,
  viewChild,
  viewChildren,
} from '@angular/core';

export interface MenuItem {
  readonly id: string;
  readonly label: string;
  readonly testId: string;
  readonly disabled?: boolean;
}

/**
 * Bouton « ⋮ » ouvrant un menu d'actions (UX-authentification ECR-05) : rôles ARIA `menu`,
 * flèches haut/bas, `Échap` referme et rend le focus au bouton, clic extérieur referme.
 */
@Component({
  selector: 'app-menu-button',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'relative inline-block', '(document:click)': 'onDocumentClick($event)' },
  template: `
    <button #trigger type="button" [class]="bordered() ? 'inline-flex items-center gap-1.5 rounded-xl border border-border bg-surface px-4 py-2.5 text-sm font-semibold shadow-sm hover:bg-primary-soft hover:text-primary' : 'rounded-lg px-2 py-1 hover:bg-slate-100'"
            aria-haspopup="menu" [attr.aria-expanded]="isOpen()"
            [attr.aria-label]="triggerText() ? null : label()"
            [attr.data-testid]="testId()" (click)="toggle()" (keydown.arrowDown)="openAndFocus($event, 0)">
      @if (triggerText(); as text) {
        <span class="text-sm font-medium">{{ text }}</span> <span aria-hidden="true">▾</span>
      } @else {
        <span aria-hidden="true">⋮</span>
      }
    </button>
    @if (isOpen()) {
      <ul role="menu" class="absolute right-0 z-20 mt-2 min-w-56 rounded-xl border border-border bg-surface p-1 shadow-lift"
          [attr.aria-label]="label()" (keydown)="onMenuKeydown($event)">
        @for (item of items(); track item.id; let index = $index) {
          <li role="none">
            <button #menuItem type="button" role="menuitem" tabindex="-1"
                    class="block w-full rounded-lg px-3 py-2 text-left text-sm text-ink hover:bg-primary-soft hover:text-primary disabled:text-muted"
                    [disabled]="item.disabled" [attr.data-testid]="item.testId" (click)="choose(item)">
              {{ item.label }}
            </button>
          </li>
        }
      </ul>
    }
  `,
})
export class MenuButtonComponent {
  readonly label = input.required<string>();
  /** Texte visible du bouton ; à défaut, « ⋮ » avec `label` comme nom accessible. */
  readonly triggerText = input<string>();
  readonly items = input.required<readonly MenuItem[]>();
  readonly testId = input.required<string>();
  /** Bouton encadré, aligné sur les boutons secondaires. */
  readonly bordered = input(false);
  readonly selected = output<string>();

  protected readonly isOpen = signal(false);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly trigger = viewChild.required<ElementRef<HTMLButtonElement>>('trigger');
  private readonly menuItems = viewChildren<ElementRef<HTMLButtonElement>>('menuItem');

  protected toggle(): void {
    this.isOpen.update((open) => !open);
  }

  protected openAndFocus(event: Event, index: number): void {
    event.preventDefault();
    this.isOpen.set(true);
    setTimeout(() => this.focusItem(index));
  }

  protected choose(item: MenuItem): void {
    this.close();
    this.selected.emit(item.id);
  }

  protected onMenuKeydown(event: KeyboardEvent): void {
    const enabled = this.menuItems().filter((item) => !item.nativeElement.disabled);
    const current = enabled.findIndex((item) => item.nativeElement === document.activeElement);
    if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
      event.preventDefault();
      const step = event.key === 'ArrowDown' ? 1 : -1;
      const next = (current + step + enabled.length) % enabled.length;
      enabled[next]?.nativeElement.focus();
    } else if (event.key === 'Escape') {
      event.preventDefault();
      this.close();
    }
  }

  protected onDocumentClick(event: MouseEvent): void {
    if (this.isOpen() && !this.host.nativeElement.contains(event.target as Node)) {
      this.isOpen.set(false);
    }
  }

  private focusItem(index: number): void {
    const enabled = this.menuItems().filter((item) => !item.nativeElement.disabled);
    enabled[Math.min(index, enabled.length - 1)]?.nativeElement.focus();
  }

  private close(): void {
    this.isOpen.set(false);
    this.trigger().nativeElement.focus();
  }
}
