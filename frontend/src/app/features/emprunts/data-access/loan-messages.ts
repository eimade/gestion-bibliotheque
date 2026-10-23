import { ApiError } from '../../../core/http/api-error';
import { formatDateFr } from '../../../shared/pipes/date-fr.pipe';
import { formatEuro } from '../../../shared/pipes/euro.pipe';
import { commonErrorMessage } from '../../authentification/data-access/auth-messages';
import { eligibilityReasonLabel } from '../../adherents/data-access/member-messages';
import { EligibilityReason } from '../../adherents/data-access/member.models';
import { Hold, LoanRenewal, MemberBrief } from './loan.models';

/** Messages exacts d'UX-emprunts. */
export const LOAN_MESSAGES = {
  memberInvalid: 'Numéro d’adhérent invalide (format attendu : A000123).',
  copyInvalid: 'Numéro d’exemplaire invalide (8 chiffres attendus).',
  noScan: 'Aucun exemplaire scanné.',
  returnEmpty: 'Scannez un exemplaire pour enregistrer son retour.',
  quotaReached: 'Nombre maximal d’emprunts atteint.',
  noOverdue: 'Aucun emprunt en retard.',
  noOverdueFiltered: (days: number) => `Aucun emprunt avec au moins ${days} jours de retard.`,
  minDays: 'Saisissez un nombre entier supérieur ou égal à 1.',
  lossDeclared: (total: number) => `Perte déclarée. Pénalités créées : ${formatEuro(total)}.`,
  loanClosed: 'Cet emprunt n’est plus en cours.',
  notLost: 'Cet exemplaire n’est pas déclaré perdu.',
  foundShelf: (shelfMark: string) => `Exemplaire remis en circulation : remettre en rayon (${shelfMark}).`,
  neverBorrowed: 'Cet exemplaire n’a jamais été emprunté.',
  renewed: (date: string) => `Emprunt prolongé jusqu’au ${formatDateFr(date)}.`,
} as const;

/** « NOM Prénom (A000123) ». */
export function memberLabel(member: MemberBrief | null | undefined): string {
  if (!member) {
    return '';
  }
  const name = member.lastName ? `${member.lastName.toUpperCase()} ${member.firstName ?? ''}`.trim() : 'Adhérent';
  return `${name} (${member.memberNumber})`;
}

/** « METTRE DE CÔTÉ pour NOM Prénom (A000789), à retirer avant le JJ/MM/AAAA » (UX-emprunts ECR-25). */
export function holdInstruction(hold: Hold): string {
  return `METTRE DE CÔTÉ pour ${memberLabel(hold)}, à retirer avant le ${formatDateFr(hold.pickupDeadline)}`;
}

/** Variante d'ECR-33 et d'ECR-28 : « Mettre de côté pour … ». */
export function holdLabel(hold: Hold): string {
  return `Mettre de côté pour ${memberLabel(hold)}, à retirer avant le ${formatDateFr(hold.pickupDeadline)}`;
}

/** Motif affiché à la place du bouton « Prolonger » (UX-emprunts, action transverse). */
export function renewalBlockedLabel(renewal: LoanRenewal, threshold: number): string {
  switch (renewal.blockedBy) {
    case 'RENEWAL_LIMIT_REACHED':
      return 'Prolongation impossible : nombre maximal de prolongations atteint.';
    case 'RENEWAL_BLOCKED_BY_RESERVATION':
      return 'Prolongation impossible : document réservé par un autre adhérent.';
    case 'LOAN_OVERDUE':
      return 'Prolongation impossible : emprunt en retard.';
    default:
      return 'Prolongation impossible : ' + renewal.memberReasons
        .map((reason) => eligibilityReasonLabel(reason, threshold).toLowerCase()).join(', ') + '.';
  }
}

/** « Emprunt impossible : » + motifs (refus d'éligibilité au guichet). */
export function notEligibleMessage(prefix: string, reasons: readonly EligibilityReason[], threshold: number): string {
  return `${prefix} ${reasons.map((reason) => eligibilityReasonLabel(reason, threshold)).join(', ')}.`;
}

export function loanErrorMessage(error: ApiError, threshold = 5): string {
  switch (error.code) {
    case 'MEMBER_NOT_FOUND':
      return error.detail;
    case 'MEMBER_NOT_ELIGIBLE': {
      const reasons = (error.problem.reasons ?? []) as readonly EligibilityReason[];
      return reasons.includes('LOAN_LIMIT_REACHED') && reasons.length === 1
        ? LOAN_MESSAGES.quotaReached
        : notEligibleMessage(error.detail.replace(/\.$/, ' :'), reasons, threshold);
    }
    case 'COPY_NOT_FOUND':
      return 'Exemplaire inconnu.';
    case 'COPY_NOT_AVAILABLE':
    case 'COPY_RESERVED_FOR_ANOTHER_MEMBER':
    case 'NO_ACTIVE_LOAN_FOR_COPY':
    case 'RENEWAL_LIMIT_REACHED':
    case 'RENEWAL_BLOCKED_BY_RESERVATION':
    case 'LOAN_OVERDUE':
      return error.detail;
    case 'LOAN_NOT_ACTIVE':
      return LOAN_MESSAGES.loanClosed;
    case 'COPY_NOT_LOST':
      return LOAN_MESSAGES.notLost;
    default:
      return commonErrorMessage(error);
  }
}
