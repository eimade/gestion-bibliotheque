import { ChangeDetectionStrategy, Component, inject, input, signal } from '@angular/core';
import { Router } from '@angular/router';

import { AuthService, homeUrlFor } from '../../../core/auth/auth.service';
import { ApiError } from '../../../core/http/api-error';
import { GENERIC_MESSAGES } from '../../../core/http/messages';
import { AlertComponent } from '../../../shared/ui/alert.component';
import { IconComponent } from '../../../shared/ui/icon.component';
import { LibraryHeroArtComponent } from '../../catalogue/components/library-hero-art.component';
import { LoginFormComponent } from '../components/login-form.component';
import { LoginRequest, SessionUser } from '../data-access/account.models';
import { loginErrorMessage } from '../data-access/auth-messages';

/** ECR-03 — Connexion (US-001). */
@Component({
  selector: 'app-login-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [LoginFormComponent, AlertComponent, IconComponent, LibraryHeroArtComponent],
  template: `
    <div class="mx-auto max-w-6xl px-4 py-10 lg:px-8 lg:py-16">
      <div class="card grid overflow-hidden lg:grid-cols-2">
        <div class="hero-gradient relative hidden flex-col justify-between overflow-hidden p-10 lg:flex">
          <div class="pointer-events-none absolute -right-20 -top-20 h-64 w-64 rounded-full bg-white/30 blur-2xl" aria-hidden="true"></div>
          <div class="relative">
            <p class="text-xs font-semibold uppercase tracking-widest text-primary">Bienvenue</p>
            <p class="mt-3 text-3xl font-extrabold leading-tight text-indigo-950">Votre bibliothèque,<br /><span class="text-accent">partout avec vous.</span></p>
            <ul class="mt-6 space-y-3 text-sm text-indigo-900/80">
              <li class="flex items-center gap-3"><span class="flex h-8 w-8 items-center justify-center rounded-lg bg-white/70 text-primary"><app-icon name="book" [size]="16" /></span>Suivez vos emprunts et leurs échéances</li>
              <li class="flex items-center gap-3"><span class="flex h-8 w-8 items-center justify-center rounded-lg bg-white/70 text-brand"><app-icon name="bookmark" [size]="16" /></span>Réservez les livres déjà empruntés</li>
              <li class="flex items-center gap-3"><span class="flex h-8 w-8 items-center justify-center rounded-lg bg-white/70 text-accent"><app-icon name="calendar" [size]="16" /></span>Prolongez en un clic</li>
            </ul>
          </div>
          <app-library-hero-art class="relative mt-8 w-full max-w-sm self-center" />
        </div>

        <section class="space-y-6 p-6 sm:p-10 lg:p-12">
          <div>
            <span class="flex h-12 w-12 items-center justify-center rounded-2xl bg-primary-soft text-primary"><app-icon name="lock" [size]="22" /></span>
            <h1 class="mt-4 text-3xl font-bold text-ink">Se connecter</h1>
            <p class="mt-1 text-sm text-muted">Adhérents et personnel : utilisez l’adresse e-mail enregistrée à la bibliothèque.</p>
          </div>
          @if (expire()) {
            <app-alert tone="warning" data-testid="login-session-expired">{{ sessionExpired }}</app-alert>
          }
          <app-login-form class="block" [busy]="busy()" [serverError]="serverError()" (submitted)="login($event)" />
          <p class="rounded-xl bg-surface-alt p-3 text-sm text-muted">Mot de passe oublié ? Adressez-vous à l'accueil de la bibliothèque.</p>
        </section>
      </div>
    </div>
  `,
})
export class LoginPage {
  /** Paramètres d'URL liés par le routeur : `?expire=1`, `?retour=/chemin`. */
  readonly expire = input<string>();
  readonly retour = input<string>();

  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  protected readonly busy = signal(false);
  protected readonly serverError = signal<string | null>(null);
  protected readonly sessionExpired = GENERIC_MESSAGES.sessionExpired;

  protected login(credentials: LoginRequest): void {
    this.busy.set(true);
    this.serverError.set(null);
    this.auth.login(credentials.email, credentials.password).subscribe({
      next: (user) => {
        this.busy.set(false);
        void this.router.navigateByUrl(this.destination(user));
      },
      error: (error: unknown) => {
        this.busy.set(false);
        this.serverError.set(error instanceof ApiError ? loginErrorMessage(error) : GENERIC_MESSAGES.unexpected);
      },
    });
  }

  /** AC-001-7 : mot de passe temporaire d'abord ; sinon l'adresse demandée si elle est interne, ou l'accueil du rôle. */
  private destination(user: SessionUser): string {
    if (user.passwordTemporary) {
      return '/compte/mot-de-passe?obligatoire=1';
    }
    const requested = this.retour();
    const isInternal = !!requested && requested.startsWith('/') && !requested.startsWith('//')
      && !requested.startsWith('/connexion');
    return isInternal ? requested : homeUrlFor(user);
  }
}
