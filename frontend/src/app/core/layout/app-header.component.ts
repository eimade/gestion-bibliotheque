import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';

import { IconComponent } from '../../shared/ui/icon.component';
import { AuthService, homeUrlFor } from '../auth/auth.service';
import { BrandLogoComponent } from './brand-logo.component';
import { UserMenuComponent } from './user-menu.component';

/** En-tête des gabarits public et adhérent (UX-socle §2.1) : collant et translucide. */
@Component({
  selector: 'app-header',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, RouterLinkActive, BrandLogoComponent, IconComponent, UserMenuComponent],
  template: `
    <header class="sticky top-0 z-30 border-b border-slate-200/70 bg-white/85 backdrop-blur-md">
      <div class="mx-auto flex max-w-6xl items-center justify-between gap-4 px-4 py-3 lg:px-8">
        <a [routerLink]="homeLink()" aria-label="Bibliothèque — accueil" class="rounded-xl">
          <app-brand-logo caption="Médiathèque" />
        </a>
        <div class="flex items-center gap-2 sm:gap-3">
          <a routerLink="/catalogue" routerLinkActive="!text-primary" [routerLinkActiveOptions]="{ exact: true }"
             class="hidden items-center gap-1.5 rounded-lg px-3 py-2 text-sm font-semibold text-slate-600 hover:text-primary sm:inline-flex">
            <app-icon name="search" [size]="16" /> Catalogue
          </a>
          @if (auth.isAuthenticated()) {
            <app-user-menu />
          } @else {
            <a routerLink="/connexion" data-testid="header-login-link"
               class="inline-flex items-center gap-2 rounded-xl bg-primary px-4 py-2 text-sm font-semibold text-white shadow-sm shadow-primary/30 transition hover:bg-primary-hover motion-reduce:transition-none">
              <app-icon name="user" [size]="16" /> Se connecter
            </a>
          }
        </div>
      </div>
    </header>
  `,
})
export class AppHeaderComponent {
  protected readonly auth = inject(AuthService);
  protected readonly homeLink = computed(() => {
    const user = this.auth.user();
    return user ? homeUrlFor(user) : '/catalogue';
  });
}
