import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/** Logo de l'application : pile de livres stylisée + nom. `inverse` pour les fonds sombres. */
@Component({
  selector: 'app-brand-logo',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'inline-flex items-center gap-2.5' },
  template: `
    <span class="brand-gradient flex h-9 w-9 shrink-0 items-center justify-center rounded-xl shadow-md shadow-indigo-500/30" aria-hidden="true">
      <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="white" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round">
        <path d="M4 4.5h3.5v15H4z" /><path d="M9.5 4.5H13v15H9.5z" /><path d="m15 5.6 3.2-.8 3 13.6-3.2.8z" />
      </svg>
    </span>
    <span class="flex flex-col leading-none">
      <span class="text-base font-bold tracking-tight" [class]="inverse() ? 'text-white' : 'text-ink'">Bibliothèque</span>
      @if (caption()) {
        <span class="mt-1 text-[0.68rem] font-semibold uppercase tracking-widest"
              [class]="inverse() ? 'text-sidebar-muted' : 'text-primary'">{{ caption() }}</span>
      }
    </span>
  `,
})
export class BrandLogoComponent {
  readonly inverse = input(false);
  readonly caption = input<string>();
}
