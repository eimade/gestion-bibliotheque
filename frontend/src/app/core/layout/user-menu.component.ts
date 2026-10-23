import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { Router } from '@angular/router';

import { MenuButtonComponent, MenuItem } from '../../shared/ui/menu-button.component';
import { AuthService } from '../auth/auth.service';
import { SignOutService } from '../auth/sign-out.service';

/** Menu de l'utilisateur connecté : changer le mot de passe (US-003), se déconnecter (US-002). */
@Component({
  selector: 'app-user-menu',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MenuButtonComponent],
  template: `
    @if (auth.user(); as user) {
      <span class="flex items-center gap-2">
      <span class="brand-gradient flex h-9 w-9 items-center justify-center rounded-full text-xs font-bold text-white shadow-sm" aria-hidden="true">{{ user.firstName.charAt(0) + user.lastName.charAt(0) }}</span>
      <app-menu-button label="Menu utilisateur" [triggerText]="user.firstName + ' ' + user.lastName"
                       [items]="items()" testId="user-menu" (selected)="onSelect($event)" />
      </span>
    }
  `,
})
export class UserMenuComponent {
  protected readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly signOut = inject(SignOutService);

  protected readonly items = computed<readonly MenuItem[]>(() => [
    {
      id: 'password',
      label: 'Changer mon mot de passe',
      testId: 'user-menu-password',
      disabled: this.auth.user()?.passwordTemporary,
    },
    { id: 'logout', label: 'Se déconnecter', testId: 'user-menu-logout' },
  ]);

  protected onSelect(id: string): void {
    if (id === 'password') {
      void this.router.navigateByUrl('/compte/mot-de-passe');
    } else {
      this.signOut.signOut();
    }
  }
}
