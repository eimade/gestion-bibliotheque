import { ChangeDetectionStrategy, Component, inject } from '@angular/core';

import { AuthService } from '../auth/auth.service';
import { MemberLayoutComponent } from './member-layout.component';
import { StaffLayoutComponent } from './staff-layout.component';

/** Pages communes à tous les rôles (ECR-04) : gabarit adhérent ou personnel selon le rôle (UX-authentification §2). */
@Component({
  selector: 'app-account-layout',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MemberLayoutComponent, StaffLayoutComponent],
  template: `
    @if (auth.user()?.role === 'MEMBER') {
      <app-member-layout />
    } @else {
      <app-staff-layout />
    }
  `,
})
export class AccountLayoutComponent {
  protected readonly auth = inject(AuthService);
}
