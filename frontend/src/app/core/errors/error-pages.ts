import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';

import { AuthService, homeUrlFor } from '../auth/auth.service';

function homeLink(auth: AuthService): string {
  const user = auth.user();
  return user ? homeUrlFor(user) : '/catalogue';
}

/** ECR-01 — Page introuvable. */
@Component({
  selector: 'app-not-found-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink],
  template: `
    <section class="card mx-4 my-16 max-w-lg sm:mx-auto space-y-4 p-10 text-center">
      <p class="text-6xl font-extrabold text-primary/20" aria-hidden="true">{{ code }}</p>
      <h1 class="text-2xl font-bold" data-testid="not-found-title">Page introuvable</h1>
      <p>La page demandée n'existe pas ou a été déplacée.</p>
      <a [routerLink]="home()" class="inline-flex rounded-xl bg-primary px-4 py-2.5 text-sm font-semibold text-white hover:bg-primary-hover" data-testid="not-found-home-link">
        Retour à l'accueil
      </a>
    </section>
  `,
})
export class NotFoundPage {
  private readonly auth = inject(AuthService);
  protected readonly home = computed(() => homeLink(this.auth));
  protected readonly code = 404;
}

/** ECR-02 — Accès refusé. */
@Component({
  selector: 'app-forbidden-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink],
  template: `
    <section class="card mx-4 my-16 max-w-lg sm:mx-auto space-y-4 p-10 text-center">
      <p class="text-6xl font-extrabold text-primary/20" aria-hidden="true">{{ code }}</p>
      <h1 class="text-2xl font-bold" data-testid="forbidden-title">Accès refusé</h1>
      <p>Vous n'avez pas les droits nécessaires pour accéder à cette page.</p>
      <a [routerLink]="home()" class="inline-flex rounded-xl bg-primary px-4 py-2.5 text-sm font-semibold text-white hover:bg-primary-hover" data-testid="forbidden-home-link">
        Retour à l'accueil
      </a>
    </section>
  `,
})
export class ForbiddenPage {
  private readonly auth = inject(AuthService);
  protected readonly home = computed(() => homeLink(this.auth));
  protected readonly code = 403;
}
