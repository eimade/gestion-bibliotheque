import { ChangeDetectionStrategy, Component } from '@angular/core';

/** WCAG 2.4.1 : premier élément focalisable, mène au contenu principal (`#contenu`). */
@Component({
  selector: 'app-skip-link',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <a href="#contenu"
       class="sr-only focus:not-sr-only focus:absolute focus:left-4 focus:top-4 focus:z-50 focus:rounded-md focus:bg-surface focus:px-4 focus:py-2 focus:shadow">
      Aller au contenu
    </a>
  `,
})
export class SkipLinkComponent {}
