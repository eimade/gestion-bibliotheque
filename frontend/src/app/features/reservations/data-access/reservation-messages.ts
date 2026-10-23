import { ApiError } from '../../../core/http/api-error';
import { formatDateFr } from '../../../shared/pipes/date-fr.pipe';
import { commonErrorMessage } from '../../authentification/data-access/auth-messages';
import { eligibilityReasonLabel } from '../../adherents/data-access/member-messages';
import { EligibilityReason } from '../../adherents/data-access/member.models';
import { memberLabel } from '../../emprunts/data-access/loan-messages';
import { CopyRelease } from '../../emprunts/data-access/loan.models';
import { ReservationRefusal } from './reservation.models';

/** Messages exacts d'UX-reservations. */
export const RESERVATION_MESSAGES = {
  created: (position: number) => `Réservation enregistrée : vous êtes en position ${position}.`,
  createdFor: (name: string, position: number) => `Réservation enregistrée pour ${name} : position ${position}.`,
  cancelled: 'Réservation annulée.',
  closed: 'Cette réservation est déjà close.',
  queueEmpty: 'Aucune réservation en attente pour ce livre.',
  noReady: 'Aucune réservation prête.',
  noReleased: 'Aucun exemplaire à remettre en rayon.',
  availableInfo: 'Un exemplaire est disponible en rayon.',
  loginToReserve: 'Connectez-vous pour réserver',
  position: (position: number, total: number) => `Vous êtes en position ${position} sur ${total}.`,
  ready: (deadline: string) => `Votre réservation est prête : à retirer avant le ${formatDateFr(deadline)}.`,
} as const;

/**
 * Motif de refus d'une réservation (UX-reservations ECR-30) ; `forStaff` remplace « Vous avez déjà… » par
 * « Cet adhérent a déjà… » (ECR-31).
 */
export function refusalLabel(reason: ReservationRefusal, limit: number, forStaff: boolean,
                             memberReasons: readonly EligibilityReason[], threshold: number): string {
  const subject = forStaff ? 'Cet adhérent a' : 'Vous avez';
  switch (reason) {
    case 'COPY_AVAILABLE_ON_SHELF':
      return 'Un exemplaire est disponible en rayon : vous pouvez l’emprunter directement.';
    case 'NO_COPY_IN_CIRCULATION':
      return 'Document indisponible : aucun exemplaire ne peut être réservé.';
    case 'RESERVATION_LIMIT_REACHED':
      return `Nombre maximal de réservations atteint (${limit}).`;
    case 'RESERVATION_ALREADY_EXISTS':
      return `${subject} déjà une réservation en cours pour ce livre.`;
    case 'BOOK_ALREADY_BORROWED':
      return `${subject} déjà ce livre en cours d’emprunt.`;
    case 'MEMBER_NOT_ELIGIBLE':
      return 'Réservation impossible : '
        + memberReasons.map((item) => eligibilityReasonLabel(item, threshold)).join(', ') + '.';
  }
}

/** Destination d'un exemplaire libéré (ECR-33, ECR-28) : rayon ou mise de côté. */
export function releaseLabel(release: CopyRelease): string {
  return release.destination === 'HOLD' && release.hold
    ? `Mettre de côté pour ${memberLabel(release.hold)}, à retirer avant le ${formatDateFr(release.hold.pickupDeadline)}`
    : `Remettre en rayon : ${release.shelfMark}`;
}

export function reservationErrorMessage(error: ApiError, forStaff = false): string {
  switch (error.code) {
    case 'RESERVATION_CLOSED':
      return RESERVATION_MESSAGES.closed;
    case 'COPY_AVAILABLE_ON_SHELF':
    case 'NO_COPY_IN_CIRCULATION':
    case 'RESERVATION_LIMIT_REACHED':
    case 'RESERVATION_ALREADY_EXISTS':
    case 'BOOK_ALREADY_BORROWED':
      return refusalLabel(error.code, Number(error.property('limit') ?? 0), forStaff, [], 5);
    case 'MEMBER_NOT_ELIGIBLE':
      return refusalLabel('MEMBER_NOT_ELIGIBLE', 0, forStaff,
        (error.problem.reasons ?? []) as readonly EligibilityReason[], 5);
    case 'MEMBER_NOT_FOUND':
      return error.detail;
    default:
      return commonErrorMessage(error);
  }
}
