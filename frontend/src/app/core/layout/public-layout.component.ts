import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

import { AppHeaderComponent } from './app-header.component';
import { BrandLogoComponent } from './brand-logo.component';
import { SkipLinkComponent } from './skip-link.component';

/**
 * Gabarit public (UX-socle §2.1), mobile d'abord. Le contenu occupe toute la largeur : chaque page
 * pose son propre conteneur (`max-w-6xl`), ce qui permet au catalogue d'afficher un bandeau pleine largeur.
 */
@Component({
  selector: 'app-public-layout',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'flex min-h-screen flex-col' },
  imports: [RouterOutlet, AppHeaderComponent, BrandLogoComponent, SkipLinkComponent],
  template: `
    <app-skip-link />
    <app-header />
    <main id="contenu" tabindex="-1" class="flex-1">
      <router-outlet />
    </main>
    <footer class="border-t border-border bg-surface">
      <div class="mx-auto flex max-w-6xl flex-col items-center justify-between gap-3 px-4 py-6 text-sm text-muted sm:flex-row lg:px-8">
        <app-brand-logo />
        <p>Catalogue en ligne · Emprunts · Réservations</p>
      </div>
    </footer>
  `,
})
export class PublicLayoutComponent {}
