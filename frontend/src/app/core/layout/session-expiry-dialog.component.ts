import { ChangeDetectionStrategy, Component, inject } from '@angular/core';

import { ButtonComponent } from '../../shared/ui/button.component';
import { DialogComponent } from '../../shared/ui/dialog.component';
import { SessionIdleService } from '../auth/session-idle.service';
import { SignOutService } from '../auth/sign-out.service';

/** Avertissement d'inactivité 2 minutes avant la déconnexion (RG-03, Q-19, WCAG 2.2.1). */
@Component({
  selector: 'app-session-expiry-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DialogComponent, ButtonComponent],
  template: `
    <app-dialog title="Votre session va expirer" [open]="idle.warningVisible()" testId="session-expiry-dialog"
                (closed)="idle.stayConnected()">
      <p class="text-sm">Sans action de votre part, vous serez déconnecté dans 2 minutes.</p>
      <div class="flex justify-end gap-2">
        <button app-button type="button" variant="secondary" data-testid="session-expiry-logout"
                (click)="signOut.signOut()">
          Se déconnecter
        </button>
        <button app-button type="button" data-testid="session-expiry-stay" (click)="idle.stayConnected()">
          Rester connecté
        </button>
      </div>
    </app-dialog>
  `,
})
export class SessionExpiryDialogComponent {
  protected readonly idle = inject(SessionIdleService);
  protected readonly signOut = inject(SignOutService);
}
