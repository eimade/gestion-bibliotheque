import { ChangeDetectionStrategy, Component, HostListener, computed, inject, signal } from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

import { ROLE_LABELS } from '../../shared/models/statuses';
import { IconComponent } from '../../shared/ui/icon.component';
import { AuthService } from '../auth/auth.service';
import { BrandLogoComponent } from './brand-logo.component';
import { NavItem, STAFF_NAVIGATION } from './navigation';
import { SkipLinkComponent } from './skip-link.component';
import { UserMenuComponent } from './user-menu.component';

/**
 * Gabarit du personnel (UX-socle §2.1) : menu latéral sombre fixe en `lg`, tiroir en dessous ; le groupe
 * « Administration » n'est affiché qu'à l'ADMIN ; navigation masquée si mot de passe temporaire (RG-05).
 */
@Component({
  selector: 'app-staff-layout',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [NgTemplateOutlet, RouterOutlet, RouterLink, RouterLinkActive, BrandLogoComponent, IconComponent, SkipLinkComponent, UserMenuComponent],
  template: `
    <app-skip-link />
    <div class="flex min-h-screen">
      @if (showNavigation()) {
        @if (drawerOpen()) {
          <div class="fixed inset-0 z-30 bg-indigo-950/40 backdrop-blur-sm lg:hidden" aria-hidden="true" (click)="drawerOpen.set(false)"></div>
        }
        <nav id="menu-gestion" aria-label="Menu de gestion"
             class="sidebar-scroll fixed inset-y-0 left-0 z-40 w-72 shrink-0 flex-col overflow-y-auto bg-sidebar text-indigo-100 lg:sticky lg:top-0 lg:flex lg:h-screen"
             [class.hidden]="!drawerOpen()" [class.flex]="drawerOpen()">
          <div class="px-5 pb-4 pt-5">
            <a routerLink="/gestion" class="rounded-xl" aria-label="Tableau de bord" (click)="drawerOpen.set(false)">
              <app-brand-logo [inverse]="true" caption="Espace gestion" />
            </a>
          </div>
          <p class="px-6 pb-2 pt-3 text-[0.68rem] font-semibold uppercase tracking-widest text-sidebar-muted">Circulation</p>
          <ul class="space-y-0.5 px-3">
            @for (item of mainItems(); track item.route) {
              <li><ng-container *ngTemplateOutlet="entry; context: { $implicit: item }" /></li>
            }
          </ul>
          @if (adminItems().length) {
            <p class="px-6 pb-2 pt-6 text-[0.68rem] font-semibold uppercase tracking-widest text-sidebar-muted">Administration</p>
            <ul class="space-y-0.5 px-3">
              @for (item of adminItems(); track item.route) {
                <li><ng-container *ngTemplateOutlet="entry; context: { $implicit: item }" /></li>
              }
            </ul>
          }
          <div class="mt-auto p-4">
            <div class="rounded-2xl bg-gradient-to-br from-indigo-500/30 to-teal-500/20 p-4 ring-1 ring-white/10">
              <p class="flex items-center gap-2 text-sm font-semibold text-white"><app-icon name="sparkle" [size]="16" /> Raccourcis</p>
              <p class="mt-1 text-xs leading-relaxed text-indigo-200">
                <kbd class="rounded bg-white/10 px-1">Alt</kbd>+<kbd class="rounded bg-white/10 px-1">E</kbd> emprunt ·
                <kbd class="rounded bg-white/10 px-1">R</kbd> retour · <kbd class="rounded bg-white/10 px-1">A</kbd> adhérents ·
                <kbd class="rounded bg-white/10 px-1">C</kbd> catalogue
              </p>
            </div>
          </div>
        </nav>
      }

      <div class="flex min-w-0 flex-1 flex-col">
        <header class="sticky top-0 z-20 border-b border-slate-200/70 bg-white/85 backdrop-blur-md">
          <div class="flex items-center justify-between gap-4 px-4 py-3 lg:px-8">
            <div class="flex items-center gap-3">
              @if (showNavigation()) {
                <button type="button" class="inline-flex items-center gap-2 rounded-xl border border-border bg-surface px-3 py-2 text-sm font-semibold lg:hidden"
                        [attr.aria-expanded]="drawerOpen()" aria-controls="menu-gestion"
                        data-testid="staff-menu-toggle" (click)="drawerOpen.set(!drawerOpen())">
                  <app-icon name="menu" [size]="18" /> Menu
                </button>
              } @else {
                <app-brand-logo caption="Espace gestion" />
              }
              <p class="hidden text-sm text-muted md:block">{{ today }}</p>
            </div>
            <div class="flex items-center gap-3">
              <span class="hidden rounded-full bg-primary-soft px-3 py-1 text-xs font-semibold text-primary sm:inline" data-testid="staff-role">{{ roleLabel() }}</span>
              <app-user-menu />
            </div>
          </div>
        </header>
        <main id="contenu" tabindex="-1" class="min-w-0 flex-1 px-4 py-8 lg:px-8">
          <router-outlet />
        </main>
      </div>
    </div>

    <ng-template #entry let-item>
      <a [routerLink]="item.route" routerLinkActive="!bg-white/10 !text-white shadow-inner [&>span:first-child]:!bg-primary [&>span:first-child]:!text-white"
         [routerLinkActiveOptions]="{ exact: item.route === '/gestion' }" ariaCurrentWhenActive="page"
         class="group flex items-center gap-3 rounded-xl px-3 py-1.5 text-sm font-medium text-indigo-200 transition hover:bg-sidebar-hover hover:text-white motion-reduce:transition-none"
         [attr.data-testid]="item.testId" [attr.aria-keyshortcuts]="shortcutOf(item)"
         [attr.title]="item.shortcut ? 'Raccourci : ' + shortcutOf(item) : null"
         (click)="drawerOpen.set(false)">
        <span class="flex h-8 w-8 items-center justify-center rounded-lg bg-white/5 text-indigo-300 transition group-hover:text-white motion-reduce:transition-none">
          @if (item.icon) { <app-icon [name]="item.icon" [size]="17" /> }
        </span>
        <span class="flex-1">{{ item.label }}</span>
        @if (item.shortcut) {
          <kbd class="rounded-md bg-white/5 px-1.5 py-0.5 text-[0.65rem] font-semibold text-indigo-300">{{ item.shortcut.toUpperCase() }}</kbd>
        }
      </a>
    </ng-template>
  `,
})
export class StaffLayoutComponent {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  protected readonly drawerOpen = signal(false);
  protected readonly today = new Intl.DateTimeFormat('fr-FR', {
    weekday: 'long', day: 'numeric', month: 'long', year: 'numeric', timeZone: 'Europe/Paris',
  }).format(new Date()).replace(/^./, (first) => first.toUpperCase());

  protected readonly showNavigation = computed(() => !this.auth.user()?.passwordTemporary);
  protected readonly roleLabel = computed(() => {
    const user = this.auth.user();
    return user ? ROLE_LABELS[user.role] : '';
  });
  private readonly visibleItems = computed(() => {
    const role = this.auth.user()?.role;
    return STAFF_NAVIGATION.filter((item) => role !== undefined && item.roles.includes(role));
  });
  protected readonly mainItems = computed(() => this.visibleItems().filter((item) => !item.admin));
  protected readonly adminItems = computed(() => this.visibleItems().filter((item) => item.admin));

  protected shortcutOf(item: NavItem): string | null {
    return item.shortcut ? 'Alt+' + item.shortcut.toUpperCase() : null;
  }

  /** Q-20 : Alt+E (emprunt), Alt+R (retour), Alt+A (adhérents), Alt+C (catalogue). */
  @HostListener('document:keydown', ['$event'])
  protected onShortcut(event: KeyboardEvent): void {
    if (!event.altKey || event.ctrlKey || event.metaKey || !this.showNavigation()) {
      return;
    }
    const item = this.visibleItems().find((candidate) => candidate.shortcut === event.key.toLowerCase());
    if (item) {
      event.preventDefault();
      void this.router.navigateByUrl(item.route);
    }
  }
}
