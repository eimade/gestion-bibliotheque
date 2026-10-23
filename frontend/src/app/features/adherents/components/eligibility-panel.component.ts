import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import { eligibilityReasonLabel } from '../data-access/member-messages';
import { Eligibility } from '../data-access/member.models';

/**
 * Éligibilité à l'emprunt (UX-adherents §5, RG-23) : « Peut emprunter N document(s) » ou « Emprunt
 * impossible : » avec tous les motifs, en texte et icône (pas seulement la couleur). Réutilisé par ECR-24.
 */
@Component({
  selector: 'app-eligibility-panel',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'block' },
  template: `
    <div class="rounded-lg border p-4" role="status" data-testid="member-eligibility-panel"
         [attr.data-eligible]="eligibility().eligible"
         [class]="eligibility().eligible ? 'border-success bg-success-soft' : 'border-danger bg-danger-soft'">
      @if (eligibility().eligible) {
        <p class="font-medium text-success" data-testid="member-eligibility-remaining">
          <span aria-hidden="true">✓ </span>Peut emprunter {{ eligibility().remainingLoans }} document(s)
        </p>
      } @else {
        <p class="font-medium text-danger"><span aria-hidden="true">✗ </span>{{ title() }}</p>
        <ul class="mt-1 list-disc pl-6 text-sm">
          @for (reason of labels(); track reason.code) {
            <li data-testid="member-eligibility-reason" [attr.data-reason]="reason.code">{{ reason.label }}</li>
          }
        </ul>
      }
    </div>
  `,
})
export class EligibilityPanelComponent {
  readonly eligibility = input.required<Eligibility>();
  readonly title = input('Emprunt impossible :');

  protected readonly labels = computed(() => this.eligibility().reasons.map((code) => ({
    code,
    label: eligibilityReasonLabel(code, this.eligibility().fineThreshold),
  })));
}
