import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

import { IconComponent } from '../../shared/ui/icon.component';
import { AuthService } from '../auth/auth.service';
import { AppHeaderComponent } from './app-header.component';
import { MEMBER_NAVIGATION } from './navigation';
import { SkipLinkComponent } from './skip-link.component';

/** Gabarit adhérent (UX-socle §2.1) : onglets masqués tant que le mot de passe est temporaire (RG-05). */
@Component({
  selector: 'app-member-layout',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, AppHeaderComponent, IconComponent, SkipLinkComponent],
  template: `
    <app-skip-link />
    <app-header />
    @if (showNavigation()) {
      <nav class="mx-auto max-w-6xl px-4 pt-6 lg:px-8" aria-label="Espace adhérent">
        <ul class="inline-flex max-w-full flex-wrap gap-1 rounded-2xl border border-border bg-surface p-1 shadow-card">
          @for (item of navigation; track item.route) {
            <li>
              <a [routerLink]="item.route" routerLinkActive="!bg-primary !text-white shadow-sm shadow-primary/30"
                 [routerLinkActiveOptions]="{ exact: true }" ariaCurrentWhenActive="page"
                 class="inline-flex items-center gap-2 rounded-xl px-4 py-2 text-sm font-semibold text-slate-600 transition hover:bg-primary-soft hover:text-primary motion-reduce:transition-none"
                 [attr.data-testid]="item.testId">
                @if (item.icon) { <app-icon [name]="item.icon" [size]="17" /> }
                {{ item.label }}
              </a>
            </li>
          }
        </ul>
      </nav>
    }
    <main id="contenu" tabindex="-1" class="mx-auto max-w-6xl px-4 py-8 lg:px-8">
      <router-outlet />
    </main>
  `,
})
export class MemberLayoutComponent {
  private readonly auth = inject(AuthService);
  protected readonly navigation = MEMBER_NAVIGATION;
  protected readonly showNavigation = computed(() => !this.auth.user()?.passwordTemporary);
}
