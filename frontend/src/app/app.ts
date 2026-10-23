import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';

import { SessionIdleService } from './core/auth/session-idle.service';
import { SessionExpiryDialogComponent } from './core/layout/session-expiry-dialog.component';
import { ToastOutletComponent } from './shared/ui/toast-outlet.component';

@Component({
  selector: 'app-root',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterOutlet, ToastOutletComponent, SessionExpiryDialogComponent],
  template: `
    <router-outlet />
    <app-toast-outlet />
    <app-session-expiry-dialog />
  `,
})
export class App {
  /** Démarre la surveillance d'inactivité (RG-03) dès le lancement. */
  private readonly idle = inject(SessionIdleService);
}
