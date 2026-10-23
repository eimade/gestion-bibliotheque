import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';

export interface TabItem {
  readonly id: string;
  readonly label: string;
  readonly testId: string;
}

/** Onglets accessibles (UX-socle §5.3) : `role=tablist`, flèches gauche/droite, onglet actif seul focalisable. */
@Component({
  selector: 'app-tabs',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div role="tablist" class="inline-flex max-w-full flex-wrap gap-1 rounded-xl bg-slate-100 p-1" [attr.aria-label]="label()">
      @for (tab of tabs(); track tab.id; let index = $index) {
        <button type="button" role="tab" [id]="'tab-' + tab.id" [attr.aria-selected]="tab.id === active()"
                [attr.aria-controls]="'panel-' + tab.id" [tabIndex]="tab.id === active() ? 0 : -1"
                [attr.data-testid]="tab.testId"
                class="rounded-lg px-3.5 py-1.5 text-sm font-semibold transition motion-reduce:transition-none"
                [class]="tab.id === active() ? 'bg-surface text-primary shadow-sm' : 'text-muted hover:text-ink'"
                (click)="activeChange.emit(tab.id)" (keydown)="onKey($event, index)">
          {{ tab.label }}
        </button>
      }
    </div>
  `,
})
export class TabsComponent {
  readonly tabs = input.required<readonly TabItem[]>();
  readonly active = input.required<string>();
  readonly label = input('Onglets');
  readonly activeChange = output<string>();

  protected onKey(event: KeyboardEvent, index: number): void {
    const step = event.key === 'ArrowRight' ? 1 : event.key === 'ArrowLeft' ? -1 : 0;
    if (!step) {
      return;
    }
    event.preventDefault();
    const tabs = this.tabs();
    const next = tabs[(index + step + tabs.length) % tabs.length];
    this.activeChange.emit(next.id);
    queueMicrotask(() => document.getElementById('tab-' + next.id)?.focus());
  }
}
