import { ApiError } from '../../../core/http/api-error';
import { PAYMENT_METHOD_LABELS, PaymentMethod } from '../../../shared/models/statuses';
import { formatEuro } from '../../../shared/pipes/euro.pipe';
import { commonErrorMessage } from '../../authentification/data-access/auth-messages';

/** Messages exacts d'UX-penalites. */
export const FINE_MESSAGES = {
  selectionRequired: 'Sélectionnez au moins une pénalité.',
  methodRequired: 'Sélectionnez un mode de paiement.',
  partial: 'Le paiement partiel n’est pas autorisé.',
  mismatch: (total: number) => `Le montant encaissé doit être égal au total sélectionné (${formatEuro(total)}).`,
  alreadySettled: 'Une ou plusieurs pénalités ont déjà été réglées. La liste a été actualisée.',
  collected: (amount: number, method: PaymentMethod) =>
    `Paiement de ${formatEuro(amount)} enregistré (${PAYMENT_METHOD_LABELS[method]}).`,
  unblocked: 'L’adhérent peut de nouveau emprunter.',
  none: 'Aucune pénalité impayée.',
  waiveReason: 'Le motif doit contenir entre 3 et 255 caractères.',
  waived: 'Remise accordée.',
  waiveSettled: 'Cette pénalité a déjà été réglée ou remise.',
  provisional: 'provisoire',
} as const;

export function fineErrorMessage(error: ApiError): string {
  switch (error.code) {
    case 'FINE_ALREADY_SETTLED':
      return FINE_MESSAGES.alreadySettled;
    case 'PARTIAL_PAYMENT_NOT_ALLOWED':
      return FINE_MESSAGES.partial;
    case 'PAYMENT_AMOUNT_MISMATCH':
      return error.detail;
    case 'VALIDATION_FAILED':
      return error.problem.fieldErrors?.[0]?.message ?? error.detail;
    default:
      return commonErrorMessage(error);
  }
}
